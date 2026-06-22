package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.projectflow.dto.V2ProjectDtos.ProjectProfileResponse;
import com.projectflow.support.AppException;

@Service
public class ProjectZipScanService {
    private static final int MAX_ZIP_ENTRIES = 220;
    private static final int MAX_FILE_SNIPPET_CHARS = 6_000;
    private static final int MAX_INDEXED_SNIPPET_CHARS = 160_000;

    private final ProjectZipUploadGuard projectZipUploadGuard;

    public ProjectZipScanService(ProjectZipUploadGuard projectZipUploadGuard) {
        this.projectZipUploadGuard = projectZipUploadGuard;
    }

    public ZipProjectScan scan(MultipartFile file) {
        projectZipUploadGuard.assertUploadBudget(file.getSize());
        try {
            return scan(file, StandardCharsets.UTF_8);
        } catch (AppException utf8Exception) {
            if (!"ZIP_READ_FAILED".equals(utf8Exception.getCode())) {
                throw utf8Exception;
            }
            return scan(file, Charset.forName("GBK"));
        }
    }

    private ZipProjectScan scan(MultipartFile file, Charset charset) {
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream(), charset)) {
            StringBuilder tree = new StringBuilder("# Project zip summary\n\n## Directory tree\n");
            StringBuilder keyFiles = new StringBuilder("\n## Key files\n");
            StringBuilder fileSnippets = new StringBuilder("\n## File snippets\n");
            List<String> moduleStructure = new ArrayList<>();
            Set<String> techStack = new LinkedHashSet<>();
            Map<String, String> keyFileContent = new LinkedHashMap<>();
            String rootName = "";
            boolean hasReadme = false;
            boolean hasTests = false;
            boolean hasStartScript = false;
            boolean hasDeployConfig = false;
            boolean hasSource = false;
            int count = 0;
            long indexedBytes = 0;
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null && count < MAX_ZIP_ENTRIES) {
                String name = entry.getName().replace("\\", "/");
                if (entry.isDirectory() || shouldSkipZipEntry(name)) {
                    continue;
                }
                if (rootName.isBlank()) {
                    rootName = firstPathSegment(name);
                }

                String relativeName = stripRoot(name, rootName);
                count++;
                tree.append("- ").append(relativeName).append("\n");
                moduleStructure.add(relativeName);

                String lowerRelativeName = relativeName.toLowerCase();
                hasReadme = hasReadme || lowerRelativeName.endsWith("readme.md");
                hasTests = hasTests || isTestPath(lowerRelativeName);
                hasStartScript = hasStartScript || lowerRelativeName.startsWith("start-") || lowerRelativeName.endsWith(".bat") || lowerRelativeName.contains("package.json");
                hasDeployConfig = hasDeployConfig || lowerRelativeName.endsWith("docker-compose.yml") || lowerRelativeName.contains("docker/");
                hasSource = hasSource || isSourceCodePath(lowerRelativeName);

                if (!isSensitivePath(relativeName) && (isKeyZipFile(relativeName) || isIndexableTextFile(relativeName))) {
                    String content = readSafeZipText(zipInputStream);
                    indexedBytes = projectZipUploadGuard.assertReadBudget(indexedBytes, content.getBytes(StandardCharsets.UTF_8).length);
                    if (isKeyZipFile(relativeName)) {
                        keyFileContent.put(relativeName, content);
                        keyFiles.append("\n### ").append(relativeName).append("\n");
                        keyFiles.append(content).append("\n");
                        detectTechStack(relativeName, content, techStack);
                    } else if (fileSnippets.length() < MAX_INDEXED_SNIPPET_CHARS) {
                        fileSnippets.append("\n### ").append(relativeName).append("\n");
                        fileSnippets.append(content).append("\n");
                    }
                }
            }

            ProjectProfileResponse profile = buildProjectProfile(
                rootName,
                keyFileContent,
                new ArrayList<>(techStack),
                moduleStructure,
                hasReadme,
                hasTests,
                hasStartScript,
                hasDeployConfig,
                hasSource
            );
            return new ZipProjectScan(tree.append(keyFiles).append(fileSnippets).toString(), profile);
        } catch (IOException exception) {
            throw new AppException("ZIP_READ_FAILED", "项目 zip 无法读取；如果是中文路径或旧压缩工具生成的 zip，ProjectFlow 会自动尝试 GBK 编码。若仍失败，请重新压缩为标准 zip。", HttpStatus.BAD_REQUEST);
        }
    }

    private ProjectProfileResponse buildProjectProfile(
        String rootName,
        Map<String, String> keyFileContent,
        List<String> techStack,
        List<String> moduleStructure,
        boolean hasReadme,
        boolean hasTests,
        boolean hasStartScript,
        boolean hasDeployConfig,
        boolean hasSource
    ) {
        String inferredName = inferProjectName(rootName, keyFileContent);
        boolean looksEmptyShell = !hasSource || !hasReadme || moduleStructure.size() < 4;
        String currentStage = hasTests && hasDeployConfig ? "工程化完善中" : "项目导入梳理";
        String mostImportantGap = inferMostImportantGap(hasReadme, hasTests, hasStartScript, hasDeployConfig, looksEmptyShell);
        String summary = "%s 已完成完整项目 zip 导入，识别到 %d 个结构条目。".formatted(inferredName, moduleStructure.size());
        return new ProjectProfileResponse(
            inferredName,
            summary,
            techStack,
            moduleStructure.stream().limit(80).toList(),
            currentStage,
            hasReadme,
            hasTests,
            hasStartScript,
            hasDeployConfig,
            looksEmptyShell,
            mostImportantGap
        );
    }

    private String inferProjectName(String rootName, Map<String, String> keyFileContent) {
        if (rootName != null && !rootName.isBlank()) {
            return rootName;
        }
        for (Map.Entry<String, String> entry : keyFileContent.entrySet()) {
            if (entry.getKey().endsWith("package.json")) {
                String name = extractJsonString(entry.getValue(), "name");
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        for (Map.Entry<String, String> entry : keyFileContent.entrySet()) {
            if (entry.getKey().endsWith("pom.xml")) {
                String artifactId = extractXmlTag(entry.getValue(), "artifactId");
                if (!artifactId.isBlank()) {
                    return artifactId;
                }
            }
        }
        for (Map.Entry<String, String> entry : keyFileContent.entrySet()) {
            if (entry.getKey().toLowerCase().endsWith("readme.md")) {
                String heading = entry.getValue().lines()
                    .filter(line -> line.startsWith("# "))
                    .map(line -> line.substring(2).trim())
                    .findFirst()
                    .orElse("");
                if (!heading.isBlank()) {
                    return heading;
                }
            }
        }
        return "Imported Project " + LocalDate.now();
    }

    private String inferMostImportantGap(boolean hasReadme, boolean hasTests, boolean hasStartScript, boolean hasDeployConfig, boolean looksEmptyShell) {
        if (looksEmptyShell) {
            return "补齐项目核心源码和结构证据";
        }
        if (!hasReadme) {
            return "补齐 README 项目说明";
        }
        if (!hasTests) {
            return "补齐关键测试路径";
        }
        if (!hasStartScript) {
            return "补齐本地启动脚本";
        }
        if (!hasDeployConfig) {
            return "补齐部署配置说明";
        }
        return "确认项目画像并规划下一轮开发";
    }

    private void detectTechStack(String relativeName, String content, Set<String> techStack) {
        String lowerName = relativeName.toLowerCase();
        String lowerContent = content.toLowerCase();
        if (lowerName.endsWith("package.json")) {
            techStack.add("Node.js");
            if (lowerContent.contains("\"next\"")) {
                techStack.add("Next.js");
            }
            if (lowerContent.contains("\"react\"")) {
                techStack.add("React");
            }
            if (lowerContent.contains("\"vue\"")) {
                techStack.add("Vue");
            }
            if (lowerContent.contains("\"vite\"")) {
                techStack.add("Vite");
            }
            if (lowerContent.contains("\"express\"")) {
                techStack.add("Express");
            }
            if (lowerContent.contains("\"nestjs\"") || lowerContent.contains("@nestjs/")) {
                techStack.add("NestJS");
            }
        }
        if (lowerName.endsWith("pom.xml")) {
            if (lowerContent.contains("spring-boot")) {
                techStack.add("Spring Boot");
            }
            techStack.add("Java");
        }
        if (lowerName.endsWith("build.gradle") || lowerName.endsWith("build.gradle.kts")) {
            if (lowerContent.contains("springframework.boot") || lowerContent.contains("spring-boot")) {
                techStack.add("Spring Boot");
            }
            techStack.add("Java");
            techStack.add("Gradle");
        }
        if (lowerName.endsWith("pyproject.toml") || lowerName.endsWith("requirements.txt")) {
            techStack.add("Python");
            if (lowerContent.contains("fastapi")) {
                techStack.add("FastAPI");
            }
            if (lowerContent.contains("django")) {
                techStack.add("Django");
            }
            if (lowerContent.contains("flask")) {
                techStack.add("Flask");
            }
        }
        if (lowerName.endsWith("go.mod")) {
            techStack.add("Go");
            if (lowerContent.contains("gin-gonic") || lowerContent.contains("gin ")) {
                techStack.add("Gin");
            }
        }
        if (lowerName.endsWith("cargo.toml")) {
            techStack.add("Rust");
        }
        if (lowerName.endsWith("composer.json")) {
            techStack.add("PHP");
            if (lowerContent.contains("laravel")) {
                techStack.add("Laravel");
            }
        }
        if (lowerName.endsWith(".csproj")) {
            techStack.add(".NET");
        }
        if (lowerName.endsWith("docker-compose.yml")) {
            techStack.add("Docker Compose");
            if (lowerContent.contains("postgres")) {
                techStack.add("PostgreSQL");
            }
            if (lowerContent.contains("redis")) {
                techStack.add("Redis");
            }
        }
    }

    private String extractJsonString(String content, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = content.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = content.indexOf(':', keyIndex);
        int firstQuote = content.indexOf('"', colonIndex + 1);
        int secondQuote = content.indexOf('"', firstQuote + 1);
        if (colonIndex < 0 || firstQuote < 0 || secondQuote < 0) {
            return "";
        }
        return content.substring(firstQuote + 1, secondQuote).trim();
    }

    private String extractXmlTag(String content, String tagName) {
        String open = "<" + tagName + ">";
        String close = "</" + tagName + ">";
        int openIndex = content.indexOf(open);
        int closeIndex = content.indexOf(close, openIndex + open.length());
        if (openIndex < 0 || closeIndex < 0) {
            return "";
        }
        return content.substring(openIndex + open.length(), closeIndex).trim();
    }

    private boolean shouldSkipZipEntry(String name) {
        String lower = name.toLowerCase();
        return isProjectNoisePath(lower)
            || lower.contains("/.git/")
            || lower.contains("/node_modules/")
            || lower.contains("/target/")
            || lower.contains("/dist/")
            || lower.contains("/.next/")
            || lower.contains("/build/")
            || lower.contains("/logs/")
            || lower.contains("/coverage/")
            || lower.contains("/.turbo/")
            || lower.contains("/.venv/")
            || lower.contains("/venv/")
            || lower.contains("/__pycache__/")
            || lower.contains("/.pytest_cache/")
            || lower.contains("/.mypy_cache/")
            || lower.contains("/.ruff_cache/")
            || lower.contains("/vendor/")
            || lower.endsWith(".log")
            || lower.contains(".next-dev.")
            || lower.endsWith(".env")
            || lower.endsWith(".png")
            || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg")
            || lower.endsWith(".gif")
            || lower.endsWith(".mp4")
            || lower.endsWith(".zip")
            || lower.endsWith(".jar");
    }

    private boolean isProjectNoisePath(String path) {
        String lower = path.toLowerCase();
        return lower.startsWith(".codex-run/")
            || lower.contains("/.codex-run/")
            || lower.contains("/old-git-")
            || lower.startsWith(".git/")
            || lower.contains("/.git/")
            || lower.startsWith("node_modules/")
            || lower.contains("/node_modules/")
            || lower.startsWith(".venv/")
            || lower.contains("/.venv/")
            || lower.startsWith("venv/")
            || lower.contains("/venv/")
            || lower.contains("/__pycache__/")
            || lower.contains("/.pytest_cache/")
            || lower.contains("/.mypy_cache/")
            || lower.contains("/.ruff_cache/")
            || lower.contains("/coverage/")
            || lower.contains("/dist/")
            || lower.contains("/build/")
            || lower.contains("/target/")
            || lower.contains("/.next/")
            || lower.contains("/.turbo/");
    }

    private boolean isIndexableTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".java")
            || lower.endsWith(".kt")
            || lower.endsWith(".js")
            || lower.endsWith(".jsx")
            || lower.endsWith(".ts")
            || lower.endsWith(".tsx")
            || lower.endsWith(".vue")
            || lower.endsWith(".py")
            || lower.endsWith(".go")
            || lower.endsWith(".rs")
            || lower.endsWith(".php")
            || lower.endsWith(".cs")
            || lower.endsWith(".rb")
            || lower.endsWith(".sql")
            || lower.endsWith(".graphql")
            || lower.endsWith(".properties")
            || lower.endsWith(".yaml")
            || lower.endsWith(".yml")
            || lower.endsWith(".xml")
            || lower.endsWith(".md")
            || lower.endsWith(".txt")
            || lower.endsWith(".css")
            || lower.endsWith(".scss")
            || lower.endsWith(".html");
    }

    private String readSafeZipText(ZipInputStream zipInputStream) throws IOException {
        int byteLimit = MAX_FILE_SNIPPET_CHARS * 4;
        byte[] bytes = zipInputStream.readNBytes(byteLimit);
        String content = new String(bytes, StandardCharsets.UTF_8);
        return sanitizeIndexedContent(truncate(content, MAX_FILE_SNIPPET_CHARS));
    }

    private String sanitizeIndexedContent(String content) {
        return content
            .replaceAll("(?im)^([\\w.-]*(?:api[_-]?key|secret|password|token)[\\w.-]*\\s*[:=]\\s*).+$", "$1[REDACTED]")
            .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\"']+", "$1[REDACTED]")
            .replaceAll("(?s)-----BEGIN [^-]+ PRIVATE KEY-----.*?-----END [^-]+ PRIVATE KEY-----", "[REDACTED PRIVATE KEY]");
    }

    private boolean isKeyZipFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith("readme.md")
            || lower.endsWith("package.json")
            || lower.endsWith("pom.xml")
            || lower.endsWith("build.gradle")
            || lower.endsWith("build.gradle.kts")
            || lower.endsWith("settings.gradle")
            || lower.endsWith("gradle.properties")
            || lower.endsWith("pyproject.toml")
            || lower.endsWith("requirements.txt")
            || lower.endsWith("poetry.lock")
            || lower.endsWith("go.mod")
            || lower.endsWith("cargo.toml")
            || lower.endsWith("composer.json")
            || lower.endsWith(".csproj")
            || lower.endsWith("docker-compose.yml")
            || lower.endsWith("tsconfig.json")
            || lower.endsWith("jsconfig.json")
            || lower.endsWith("next.config.ts")
            || lower.endsWith("next.config.js")
            || lower.endsWith("vite.config.ts")
            || lower.endsWith("vite.config.js")
            || lower.endsWith(".env.example")
            || lower.startsWith("docs/");
    }

    private boolean isTestPath(String lowerPath) {
        return lowerPath.startsWith("test/")
            || lowerPath.startsWith("tests/")
            || lowerPath.startsWith("spec/")
            || lowerPath.contains("/test/")
            || lowerPath.contains("/tests/")
            || lowerPath.contains("/spec/")
            || lowerPath.contains("/__tests__/")
            || lowerPath.contains(".test.")
            || lowerPath.contains(".spec.")
            || lowerPath.endsWith("_test.py")
            || lowerPath.endsWith("test_main.py");
    }

    private boolean isSourceCodePath(String lowerPath) {
        return lowerPath.contains("/src/")
            || lowerPath.startsWith("src/")
            || lowerPath.contains("/app/")
            || lowerPath.endsWith(".java")
            || lowerPath.endsWith(".kt")
            || lowerPath.endsWith(".ts")
            || lowerPath.endsWith(".tsx")
            || lowerPath.endsWith(".js")
            || lowerPath.endsWith(".jsx")
            || lowerPath.endsWith(".vue")
            || lowerPath.endsWith(".py")
            || lowerPath.endsWith(".go")
            || lowerPath.endsWith(".rs")
            || lowerPath.endsWith(".php")
            || lowerPath.endsWith(".cs")
            || lowerPath.endsWith(".rb");
    }

    private boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();
        return (lower.contains(".env") && !lower.endsWith(".env.example"))
            || lower.endsWith(".pem")
            || lower.endsWith(".key")
            || lower.contains("secret");
    }

    private String firstPathSegment(String path) {
        int slashIndex = path.indexOf('/');
        return slashIndex < 0 ? "" : path.substring(0, slashIndex);
    }

    private String stripRoot(String path, String rootName) {
        if (rootName == null || rootName.isBlank()) {
            return path;
        }
        String prefix = rootName + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ZipProjectScan(
        String content,
        ProjectProfileResponse profile
    ) {
    }
}
