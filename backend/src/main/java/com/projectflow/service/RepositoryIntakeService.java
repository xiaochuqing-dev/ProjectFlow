package com.projectflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectUnderstandingDtos.GitEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;

@Service
public class RepositoryIntakeService {
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
        ".git", ".hg", ".svn", "node_modules", "vendor", "target", "build", "dist", "out",
        ".next", ".nuxt", ".gradle", ".idea", ".venv", "venv", "__pycache__", "coverage",
        ".pytest_cache", ".mypy_cache", ".ruff_cache", ".turbo"
    );
    private static final Set<String> MANIFEST_NAMES = Set.of(
        "package.json", "pnpm-workspace.yaml", "yarn.lock", "package-lock.json", "pnpm-lock.yaml",
        "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "pyproject.toml", "requirements.txt", "poetry.lock", "go.mod", "go.work", "cargo.toml",
        "composer.json", "gemfile", "mix.exs", "deno.json", "deno.jsonc", "docker-compose.yml",
        "docker-compose.yaml", "compose.yml", "compose.yaml"
    );
    private static final Pattern MAVEN_MODULE = Pattern.compile("<module>\\s*([^<]+?)\\s*</module>");

    private final LocalCommandExecutor commandExecutor;
    private final SccCodeMetricsAdapter sccAdapter;
    private final ObjectMapper objectMapper;

    @Value("${projectflow.understanding.max-files:250000}")
    private int maxFiles;

    @Value("${projectflow.understanding.max-file-details:5000}")
    private int maxFileDetails;

    @Value("${projectflow.understanding.max-file-read-bytes:8388608}")
    private long maxFileReadBytes;

    @Value("${projectflow.understanding.max-total-read-bytes:536870912}")
    private long maxTotalReadBytes;

    @Value("${projectflow.understanding.small-loc:20000}")
    private long smallLoc;

    @Value("${projectflow.understanding.medium-loc:100000}")
    private long mediumLoc;

    @Value("${projectflow.understanding.large-loc:500000}")
    private long largeLoc;

    public RepositoryIntakeService(
        LocalCommandExecutor commandExecutor,
        SccCodeMetricsAdapter sccAdapter,
        ObjectMapper objectMapper
    ) {
        this.commandExecutor = commandExecutor;
        this.sccAdapter = sccAdapter;
        this.objectMapper = objectMapper;
    }

    public ScanResult scan(Path root) {
        ModelCancellationContext.throwIfCancelled();
        ScanAccumulator accumulator = new ScanAccumulator(digest());
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (directory.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String relative = normalize(root.relativize(directory));
                    String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (".git".equals(name)) {
                        if (relative.indexOf('/') >= 0) {
                            accumulator.nestedRepositories++;
                        }
                        accumulator.ignoredDirectories++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (SKIPPED_DIRECTORIES.contains(name)) {
                        accumulator.ignoredDirectories++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                        accumulator.ignoredDirectories++;
                        accumulator.warn("符号链接未跟随，避免扫描逃出已绑定项目目录。");
                        return FileVisitResult.CONTINUE;
                    }
                    if (accumulator.fileCount >= maxFiles || accumulator.contentBytesRead >= maxTotalReadBytes) {
                        accumulator.truncated = true;
                        return FileVisitResult.TERMINATE;
                    }
                    inspectFile(root, file, attributes, accumulator);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    accumulator.unreadableFiles++;
                    accumulator.warn("部分文件无法读取，已降低目录覆盖率。");
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            accumulator.warn("目录扫描未完整完成：" + safeMessage(exception));
            accumulator.truncated = true;
        }

        GitEvidenceResponse git = inspectGit(root);
        SccCodeMetricsAdapter.CodeMetrics scc = sccAdapter.inspect(root);
        long loc = scc.available() ? scc.codeLines() : accumulator.estimatedLoc;
        Map<String, Long> languages = scc.available()
            ? sortCounts(scc.languageLines())
            : sortCounts(accumulator.languageLines);
        String metricsSource = scc.available() ? "SCC" : "BUILTIN_EXTENSION_SCAN";
        finishInventoryDigest(accumulator.digest, accumulator.fileCount, accumulator.truncated);
        String contentHash = hex(accumulator.digest.digest());
        String scale = scale(loc, accumulator.sourceFileCount, accumulator.monorepo);
        String classification = classification(accumulator.fileCount, accumulator.sourceFileCount, git.available(), scale);
        double scanCoverage = coverage(accumulator);
        double generatedRatio = ratio(
            accumulator.generatedFiles,
            accumulator.fileCount
        );
        double binaryRatio = ratio(accumulator.binaryFiles, accumulator.fileCount);
        String sourceRevision = git.available() && !git.head().isBlank()
            ? "git:" + git.head() + ":" + contentHash.substring(0, 12)
            : "filesystem:" + contentHash;
        if (accumulator.truncated) {
            accumulator.warn("目录超过扫描安全上限，结构索引只覆盖有界范围。");
        }
        if (accumulator.partialContentFiles > 0) {
            accumulator.warn("部分大文件只读取了有界前缀，代码行与内容指纹覆盖率已相应降低。");
        }
        if (accumulator.ignoredDirectories > 0) {
            accumulator.warn("generated/vendor/build 目录已按安全策略跳过，不会进入模型上下文。");
        }
        if (!git.available() && accumulator.sourceFileCount > 0) {
            accumulator.warn("未检测到 Git；可以理解当前结构，但历史演进不可用。");
        }

        RepositoryIntakeResponse intake = new RepositoryIntakeResponse(
            classification,
            scale,
            true,
            accumulator.fileCount,
            accumulator.sourceFileCount,
            accumulator.totalBytes,
            loc,
            languages,
            List.copyOf(accumulator.manifests),
            git,
            accumulator.nestedRepositories,
            accumulator.monorepo,
            generatedRatio,
            binaryRatio,
            scanCoverage,
            accumulator.truncated,
            metricsSource,
            sourceRevision,
            contentHash,
            List.copyOf(accumulator.warnings)
        );
        return new ScanResult(
            intake,
            List.copyOf(accumulator.fileDetails),
            accumulator.fileCount > accumulator.fileDetails.size(),
            Map.copyOf(accumulator.inventorySignatures),
            accumulator.moduleSignals(),
            immutableSignals(accumulator.engineeringSignals),
            List.copyOf(accumulator.workspaceSignals),
            List.copyOf(accumulator.entryCandidates)
        );
    }

    /**
     * Fast unchanged check based on bounded relative-path metadata. It does not
     * open file contents and therefore stays cheap enough for repeat refreshes.
     */
    public String inventoryFingerprint(Path root) {
        MessageDigest inventoryDigest = digest();
        long[] count = { 0 };
        boolean[] truncated = { false };
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (directory.equals(root)) return FileVisitResult.CONTINUE;
                    String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
                    return SKIPPED_DIRECTORIES.contains(name)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (count[0] >= maxFiles) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    updateInventoryDigest(inventoryDigest, normalize(root.relativize(file)), attributes);
                    count[0]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    inventoryDigest.update(normalize(root.relativize(file)).getBytes(StandardCharsets.UTF_8));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            inventoryDigest.update(("scan-error:" + safeMessage(exception)).getBytes(StandardCharsets.UTF_8));
        }
        finishInventoryDigest(inventoryDigest, count[0], truncated[0]);
        return hex(inventoryDigest.digest());
    }

    private void inspectFile(Path root, Path file, BasicFileAttributes attributes, ScanAccumulator accumulator) {
        String relative = normalize(root.relativize(file));
        String lower = relative.toLowerCase(Locale.ROOT);
        boolean sensitive = isSensitive(lower);
        boolean generated = isGenerated(lower);
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean manifest = MANIFEST_NAMES.contains(fileName)
            || fileName.endsWith(".csproj") || fileName.endsWith(".sln");
        String language = language(relative);
        boolean source = language != null;
        long remainingBudget = Math.max(0, maxTotalReadBytes - accumulator.contentBytesRead);
        FileInspection inspection = inspectContent(
            file,
            attributes.size(),
            source,
            manifest,
            sensitive,
            Math.min(maxFileReadBytes, remainingBudget)
        );
        accumulator.contentBytesRead += inspection.bytesRead();
        if (inspection.partial()) accumulator.partialContentFiles++;

        accumulator.fileCount++;
        accumulator.inventorySignatures.put(relative, inventorySignature(attributes));
        accumulator.totalBytes += Math.max(0, attributes.size());
        if (inspection.binary()) {
            accumulator.binaryFiles++;
        }
        if (generated) {
            accumulator.generatedFiles++;
        }
        if (source && !inspection.binary() && !sensitive && !generated) {
            accumulator.sourceFileCount++;
            accumulator.estimatedLoc += inspection.lines();
            accumulator.languageLines.merge(language, inspection.lines(), Long::sum);
        }
        updateInventoryDigest(accumulator.digest, relative, attributes);

        String module = modulePath(relative);
        ModuleAccumulator moduleAccumulator = accumulator.modules.computeIfAbsent(module, ignored -> new ModuleAccumulator());
        moduleAccumulator.fileCount++;
        if (source && !inspection.binary() && !sensitive && !generated) {
            moduleAccumulator.sourceFileCount++;
            moduleAccumulator.loc += inspection.lines();
            moduleAccumulator.languages.merge(language, inspection.lines(), Long::sum);
        }
        if (moduleAccumulator.evidencePaths.size() < 5 && !sensitive) {
            moduleAccumulator.evidencePaths.add(relative);
        }

        if (manifest && !sensitive && accumulator.manifests.size() < 200) {
            accumulator.manifests.add(relative);
            inspectWorkspaceManifest(file, relative, accumulator);
        }
        collectEngineeringSignals(relative, lower, manifest, accumulator);
        if (looksLikeEntryPoint(relative, lower) && !sensitive && accumulator.entryCandidates.size() < 200) {
            accumulator.entryCandidates.add(relative);
        }
        if (!sensitive && accumulator.fileDetails.size() < maxFileDetails) {
            accumulator.fileDetails.add(new ScannedFile(
                relative,
                language == null ? "" : language,
                inspection.lines(),
                attributes.size(),
                generated,
                inspection.binary(),
                manifest,
                isKeyFile(lower, manifest)
            ));
        }
    }

    private FileInspection inspectContent(
        Path file,
        long size,
        boolean source,
        boolean manifest,
        boolean sensitive,
        long readLimit
    ) {
        if (sensitive) {
            return new FileInspection(false, 0, "sensitive-metadata-only", 0, false);
        }
        if (readLimit <= 0) return new FileInspection(false, 0, "read-budget-exhausted", 0, true);
        MessageDigest fileDigest = digest();
        long lines = 0;
        long bytesRead = 0;
        boolean binary = false;
        boolean sawContent = false;
        boolean endedWithNewline = false;
        boolean fullRead = source || manifest || size <= 1_048_576;
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            boolean first = true;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (first) {
                    binary = containsNul(buffer, read);
                    first = false;
                }
                fileDigest.update(buffer, 0, read);
                bytesRead += read;
                sawContent = true;
                endedWithNewline = buffer[read - 1] == '\n';
                if (source && !binary) {
                    for (int index = 0; index < read; index++) {
                        if (buffer[index] == '\n') {
                            lines++;
                        }
                    }
                }
                if (binary || (!fullRead && bytesRead >= 8192)) {
                    break;
                }
                if (bytesRead >= readLimit) {
                    break;
                }
            }
        } catch (IOException exception) {
            return new FileInspection(false, 0, "unreadable", bytesRead, true);
        }
        if (source && sawContent && !binary && !endedWithNewline) {
            lines++;
        }
        return new FileInspection(binary, lines, hex(fileDigest.digest()), bytesRead, bytesRead < size);
    }

    private void inspectWorkspaceManifest(Path file, String relative, ScanAccumulator accumulator) {
        try {
            if (Files.size(file) > 262_144) {
                return;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if ("package.json".equals(lowerName)) {
                JsonNode json = objectMapper.readTree(text);
                JsonNode workspaces = json.path("workspaces");
                if (workspaces.isArray()) {
                    workspaces.forEach(item -> addWorkspaceSignal(relative, item.asText(""), accumulator));
                } else if (workspaces.isObject() && workspaces.path("packages").isArray()) {
                    workspaces.path("packages").forEach(item -> addWorkspaceSignal(relative, item.asText(""), accumulator));
                }
            } else if ("pom.xml".equals(lowerName)) {
                Matcher matcher = MAVEN_MODULE.matcher(text);
                while (matcher.find()) {
                    addWorkspaceSignal(relative, matcher.group(1), accumulator);
                }
            } else if (lowerName.contains("workspace")
                || lowerName.startsWith("settings.gradle")
                || ("cargo.toml".equals(lowerName) && text.contains("[workspace]"))
                || "go.work".equals(lowerName)) {
                addWorkspaceSignal(relative, "workspace-declaration", accumulator);
            }
        } catch (Exception ignored) {
            accumulator.warn("部分 workspace manifest 无法解析，已保留文件级证据。");
        }
    }

    private void addWorkspaceSignal(String manifest, String value, ScanAccumulator accumulator) {
        String clean = value == null ? "" : value.trim().replace('\\', '/');
        if (clean.isBlank()) {
            return;
        }
        accumulator.monorepo = true;
        if (accumulator.workspaceSignals.size() < 100) {
            accumulator.workspaceSignals.add(manifest + " -> " + clean);
        }
    }

    private GitEvidenceResponse inspectGit(Path root) {
        LocalCommandExecutor.CommandResult inside = commandExecutor.execute(
            root,
            List.of("git", "rev-parse", "--is-inside-work-tree"),
            Duration.ofSeconds(5)
        );
        if (inside.timedOut() || inside.exitCode() != 0 || !inside.output().trim().equalsIgnoreCase("true")) {
            return new GitEvidenceResponse(false, "", "", 0, "UNAVAILABLE", 0);
        }
        String head = output(root, List.of("git", "rev-parse", "HEAD"), 5);
        String branch = output(root, List.of("git", "branch", "--show-current"), 5);
        String commitCountText = output(root, List.of("git", "rev-list", "--count", "HEAD"), 8);
        long commitCount;
        try {
            commitCount = Long.parseLong(commitCountText.trim());
        } catch (NumberFormatException ignored) {
            commitCount = 0;
        }
        String status = output(root, List.of("git", "status", "--porcelain=v1", "--untracked-files=no"), 8);
        String submodules = output(root, List.of("git", "submodule", "status"), 8);
        int submoduleCount = submodules.isBlank() ? 0 : (int) submodules.lines().filter(line -> !line.isBlank()).count();
        return new GitEvidenceResponse(
            true,
            branch.isBlank() ? "DETACHED" : branch.trim(),
            head.trim(),
            commitCount,
            status.isBlank() ? "CLEAN" : "DIRTY",
            submoduleCount
        );
    }

    private String output(Path root, List<String> command, int timeoutSeconds) {
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(root, command, Duration.ofSeconds(timeoutSeconds));
        return result.timedOut() || result.exitCode() != 0 ? "" : result.output().trim();
    }

    private void collectEngineeringSignals(
        String relative,
        String lower,
        boolean manifest,
        ScanAccumulator accumulator
    ) {
        if (isTestFile(lower)) {
            addSignal(accumulator, "tests", relative);
        }
        if (lower.startsWith(".github/workflows/") || lower.contains("/.github/workflows/")
            || lower.endsWith(".gitlab-ci.yml") || lower.endsWith("jenkinsfile")) {
            addSignal(accumulator, "ci", relative);
        }
        if (lower.contains("dockerfile") || lower.contains("docker-compose") || lower.endsWith("compose.yml")
            || lower.endsWith("compose.yaml") || lower.contains("kubernetes") || lower.contains("/k8s/")) {
            addSignal(accumulator, "deployment", relative);
        }
        if (lower.contains("/migration") || lower.contains("/migrations/") || lower.contains("flyway")
            || lower.contains("liquibase")) {
            addSignal(accumulator, "migrations", relative);
        }
        if (lower.contains("eslint") || lower.contains("prettier") || lower.contains("checkstyle")
            || lower.contains("spotbugs") || lower.contains("ruff") || lower.contains("mypy")) {
            addSignal(accumulator, "quality", relative);
        }
        if (manifest) {
            addSignal(accumulator, "build", relative);
        }
        if (lower.contains("changelog") || lower.contains("release") || lower.contains("/tags/")) {
            addSignal(accumulator, "release", relative);
        }
    }

    private void addSignal(ScanAccumulator accumulator, String kind, String relative) {
        List<String> values = accumulator.engineeringSignals.computeIfAbsent(kind, ignored -> new ArrayList<>());
        if (values.size() < 30 && !isSensitive(relative.toLowerCase(Locale.ROOT))) {
            values.add(relative);
        }
    }

    private static String classification(long files, long sources, boolean git, String scale) {
        if (files == 0) {
            return "EMPTY";
        }
        if (sources == 0) {
            return "UNKNOWN_NON_CODE";
        }
        if (!git) {
            return "CODE_NO_GIT";
        }
        return "MONOREPO".equals(scale) || "HUGE".equals(scale) ? "HUGE_MONOREPO" : scale;
    }

    private String scale(long loc, long sourceFiles, boolean monorepo) {
        if (sourceFiles == 0) {
            return "NONE";
        }
        if (monorepo) {
            return "MONOREPO";
        }
        if (loc <= smallLoc) {
            return "SMALL";
        }
        if (loc <= mediumLoc) {
            return "MEDIUM";
        }
        if (loc <= largeLoc) {
            return "LARGE";
        }
        return "HUGE";
    }

    private static double coverage(ScanAccumulator accumulator) {
        double denominator = accumulator.fileCount + accumulator.unreadableFiles + accumulator.ignoredDirectories
            + accumulator.partialContentFiles;
        double value = denominator == 0 ? 1.0 : accumulator.fileCount / denominator;
        if (accumulator.truncated) {
            value = Math.min(value, 0.8);
        }
        return round(value);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0 : round((double) numerator / denominator);
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 1000.0) / 1000.0;
    }

    private static Map<String, Long> sortCounts(Map<String, Long> values) {
        Map<String, Long> sorted = new LinkedHashMap<>();
        values.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
            .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(sorted);
    }

    private static Map<String, List<String>> immutableSignals(Map<String, List<String>> values) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((key, list) -> result.put(key, List.copyOf(list)));
        return Map.copyOf(result);
    }

    private static String language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "Kotlin";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) return "JavaScript";
        if (lower.endsWith(".py") || lower.endsWith(".pyi")) return "Python";
        if (lower.endsWith(".go")) return "Go";
        if (lower.endsWith(".rs")) return "Rust";
        if (lower.endsWith(".c") || lower.endsWith(".h")) return "C";
        if (lower.endsWith(".cc") || lower.endsWith(".cpp") || lower.endsWith(".cxx") || lower.endsWith(".hpp")) return "C++";
        if (lower.endsWith(".cs")) return "C#";
        if (lower.endsWith(".rb")) return "Ruby";
        if (lower.endsWith(".php")) return "PHP";
        if (lower.endsWith(".swift")) return "Swift";
        if (lower.endsWith(".scala")) return "Scala";
        if (lower.endsWith(".dart")) return "Dart";
        if (lower.endsWith(".ex") || lower.endsWith(".exs")) return "Elixir";
        if (lower.endsWith(".vue")) return "Vue";
        if (lower.endsWith(".svelte")) return "Svelte";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "Shell";
        if (lower.endsWith(".ps1")) return "PowerShell";
        return null;
    }

    private static boolean isGenerated(String lower) {
        return lower.contains("/generated/") || lower.contains("/generated-sources/")
            || lower.contains("/gen/") || lower.endsWith(".min.js") || lower.endsWith(".min.css")
            || lower.endsWith(".designer.cs") || lower.endsWith(".g.cs");
    }

    private static boolean isSensitive(String lower) {
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        return (fileName.startsWith(".env") && !fileName.equals(".env.example"))
            || lower.endsWith(".pem") || lower.endsWith(".key") || lower.endsWith(".p12")
            || lower.contains("credentials") || lower.contains("/secrets/") || lower.contains("secret.");
    }

    private static boolean isKeyFile(String lower, boolean manifest) {
        return manifest || lower.endsWith("readme.md") || lower.endsWith("readme.mdx")
            || lower.endsWith("dockerfile") || lower.contains(".github/workflows/")
            || lower.endsWith("application.yml") || lower.endsWith("application.yaml")
            || lower.endsWith("application.properties");
    }

    private static boolean isTestFile(String lower) {
        return lower.startsWith("test/") || lower.startsWith("tests/") || lower.contains("/test/")
            || lower.contains("/tests/") || lower.contains("/__tests__/") || lower.contains(".test.")
            || lower.contains(".spec.") || lower.endsWith("test.java") || lower.endsWith("_test.py")
            || lower.endsWith("_test.go");
    }

    private static boolean looksLikeEntryPoint(String relative, String lower) {
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        return name.equals("main.java") || name.endsWith("application.java") || name.equals("main.kt")
            || name.equals("main.py") || name.equals("main.go") || name.equals("main.rs")
            || name.equals("index.ts") || name.equals("index.js") || name.equals("app.tsx")
            || name.equals("app.jsx") || name.equals("program.cs");
    }

    private static String modulePath(String relative) {
        int slash = relative.indexOf('/');
        return slash < 0 ? "." : relative.substring(0, slash);
    }

    private static boolean containsNul(byte[] bytes, int length) {
        for (int index = 0; index < length; index++) {
            if (bytes[index] == 0) {
                return true;
            }
        }
        return false;
    }

    private static void updateInventoryDigest(
        MessageDigest digest,
        String relative,
        BasicFileAttributes attributes
    ) {
        String value = relative + "\0" + inventorySignature(attributes);
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String inventorySignature(BasicFileAttributes attributes) {
        return attributes.size() + ":" + attributes.lastModifiedTime().toMillis();
    }

    private static void finishInventoryDigest(MessageDigest digest, long count, boolean truncated) {
        digest.update((truncated ? "truncated:" : "complete:").getBytes(StandardCharsets.UTF_8));
        digest.update(Long.toString(count).getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record ScanResult(
        RepositoryIntakeResponse intake,
        List<ScannedFile> fileDetails,
        boolean fileDetailsTruncated,
        Map<String, String> inventorySignatures,
        Map<String, ModuleSignal> modules,
        Map<String, List<String>> engineeringSignals,
        List<String> workspaceSignals,
        List<String> entryCandidates
    ) {
    }

    public record ScannedFile(
        String path,
        String language,
        long lines,
        long bytes,
        boolean generated,
        boolean binary,
        boolean manifest,
        boolean keyFile
    ) {
    }

    public record ModuleSignal(
        long fileCount,
        long sourceFileCount,
        long estimatedLoc,
        Map<String, Long> languages,
        List<String> evidencePaths
    ) {
    }

    private record FileInspection(boolean binary, long lines, String contentHash, long bytesRead, boolean partial) {
    }

    private static final class ModuleAccumulator {
        long fileCount;
        long sourceFileCount;
        long loc;
        final Map<String, Long> languages = new HashMap<>();
        final List<String> evidencePaths = new ArrayList<>();
    }

    private static final class ScanAccumulator {
        final MessageDigest digest;
        long fileCount;
        long sourceFileCount;
        long totalBytes;
        long estimatedLoc;
        long generatedFiles;
        long binaryFiles;
        long unreadableFiles;
        long ignoredDirectories;
        long contentBytesRead;
        long partialContentFiles;
        int nestedRepositories;
        boolean monorepo;
        boolean truncated;
        final Map<String, Long> languageLines = new HashMap<>();
        final List<String> manifests = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<ScannedFile> fileDetails = new ArrayList<>();
        final Map<String, String> inventorySignatures = new LinkedHashMap<>();
        final Map<String, ModuleAccumulator> modules = new LinkedHashMap<>();
        final Map<String, List<String>> engineeringSignals = new LinkedHashMap<>();
        final Set<String> workspaceSignals = new LinkedHashSet<>();
        final List<String> entryCandidates = new ArrayList<>();

        ScanAccumulator(MessageDigest digest) {
            this.digest = digest;
        }

        void warn(String warning) {
            if (warnings.size() < 20 && !warnings.contains(warning)) {
                warnings.add(warning);
            }
        }

        Map<String, ModuleSignal> moduleSignals() {
            Map<String, ModuleSignal> result = new LinkedHashMap<>();
            modules.forEach((path, value) -> result.put(path, new ModuleSignal(
                value.fileCount,
                value.sourceFileCount,
                value.loc,
                sortCounts(value.languages),
                List.copyOf(value.evidencePaths)
            )));
            return Map.copyOf(result);
        }
    }
}
