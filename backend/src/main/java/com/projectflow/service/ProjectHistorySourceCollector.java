package com.projectflow.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Scope;
import com.projectflow.entity.ProjectHistoryEvent.SourceType;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

/** Bounded, fixed-command discovery for source-backed project-history events. */
@Component
public class ProjectHistorySourceCollector {
    static final int MAX_COMMITS = 5_000;
    static final int COMMIT_PAGE_SIZE = 25;
    static final int MAX_EVENTS = 20_000;
    static final int MAX_FILE_EVENTS_PER_COMMIT = 500;
    static final int MAX_AGENT_RESULTS = 200;
    static final int MAX_CURRENT_FILES = 5_000;
    private static final int MAX_PROJECTION_SCAN_ENTRIES = 5_000;
    private static final int MAX_PROJECTION_MANIFESTS = 20;
    private static final int MAX_PROJECTION_MANIFEST_BYTES = 256_000;
    private static final int MAX_PROJECTION_SCAN_DEPTH = 6;
    private static final int MAX_GITHUB_ITEMS = 40;
    private static final int MAX_GITHUB_RATIONALE_CHARS = 600;
    private static final int MAX_LIST_ITEMS = 100;
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration OPTIONAL_SOURCE_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern PR_REFERENCE = Pattern.compile("(?i)(?:pull request|\\bpr)\\s*#(\\d+)");
    private static final Pattern NUMBER_REFERENCE = Pattern.compile("(?<![A-Za-z0-9])#(\\d+)");
    private static final Pattern GITHUB_REPOSITORY = Pattern.compile(
        "(?i)^(?:https://github\\.com/|git@github\\.com:|ssh://git@github\\.com/)([^/]+/[^/]+?)(?:\\.git)?/?$"
    );
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Set<String> GENERIC_NAMES = Set.of(
        "index", "page", "route", "layout", "readme", "config", "configuration", "main", "app", "application"
    );
    private static final Set<Transition> BULK_AREA_TRANSITIONS = Set.of(
        Transition.CREATED, Transition.MODIFIED, Transition.UNKNOWN_TRANSITION
    );
    private static final Set<String> NOISE_SEGMENTS = Set.of(
        ".git", "node_modules", "vendor", "dist", "build", "target", ".next", "coverage", "generated", "out",
        ".m2-cache", ".m2-flat", ".cp-flat"
    );
    private static final Set<String> PROJECTFLOW_METADATA_NAMES = Set.of(
        ".projectflow", ".projectflow-manifest.json", ".projectflow-manifest.backup.json", ".projectflow-conflicts.json"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "md", "mdx", "txt", "rst", "adoc", "doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx", "csv", "tsv",
        "json", "yaml", "yml", "toml", "xml", "drawio", "fig", "sketch", "png", "jpg", "jpeg", "gif", "svg",
        "mp3", "wav", "mp4", "mov"
    );
    private static final List<String> TYPE_SUFFIXES = List.of(
        "integrationtest", "controlleradvice", "repository", "controller", "configuration", "service",
        "component", "provider", "adapter", "scheduler", "migration", "response", "request", "entity",
        "record", "runner", "handler", "client", "factory", "mapper", "listener", "gateway", "support",
        "utils", "util", "tests", "test", "spec", "dto"
    );

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectFactRepository factRepository;
    private final LocalProjectPathGuard pathGuard;
    private final LocalCommandExecutor commandExecutor;
    private final SensitiveContentRedactor redactor;
    private final ObjectMapper objectMapper;

    public ProjectHistorySourceCollector(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectFactRepository factRepository,
        LocalProjectPathGuard pathGuard,
        LocalCommandExecutor commandExecutor,
        SensitiveContentRedactor redactor,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.factRepository = factRepository;
        this.pathGuard = pathGuard;
        this.commandExecutor = commandExecutor;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
    }

    public CollectionOutcome collect(UUID userId, UUID projectId) {
        ProjectSpace project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        List<String> limitations = new ArrayList<>();
        CollectionState collectionState = new CollectionState();
        LinkedHashMap<String, CollectedEvent> events = new LinkedHashMap<>();
        ProjectMemory memory = memoryRepository.findByProjectId(projectId).orElse(null);
        Path root = resolveRoot(memory, limitations);
        if (root != null) discoverProjectionRoots(root, projectId, collectionState, limitations);
        String projectRevision = "project:" + instant(project.getUpdatedAt());
        boolean gitAvailable = false;
        boolean shallowGitHistory = false;
        boolean gitCommitCountKnown = false;
        int totalGitCommits = 0;
        int readGitCommits = 0;

        if (root != null && Files.exists(root.resolve(".git"))) {
            String head = command(root, limitations, collectionState, "git", "rev-parse", "--verify", "HEAD").trim();
            if (isCommitSha(head)) {
                gitAvailable = true;
                projectRevision = head;
                String shallowText = command(
                    root, limitations, collectionState, "git", "rev-parse", "--is-shallow-repository"
                ).trim();
                shallowGitHistory = "true".equalsIgnoreCase(shallowText);
                if (shallowGitHistory) {
                    collectionState.complete = false;
                    limitations.add("Git 仓库是浅克隆；当前提交总数只代表已取得的历史窗口，不能视为完整历史。 ");
                }
                String countText = command(
                    root, limitations, collectionState, "git", "rev-list", "--all", "--count"
                ).trim();
                gitCommitCountKnown = countText.matches("\\d+");
                totalGitCommits = gitCommitCountKnown ? Math.max(0, integer(countText)) : 0;
                if (!gitCommitCountKnown) {
                    collectionState.complete = false;
                    limitations.add("Git 提交总数无法确认；本次结果不得标记为完整历史。 ");
                }
                GitCollection git = collectGit(
                    project, root, projectRevision, totalGitCommits, gitCommitCountKnown,
                    events, limitations, collectionState
                );
                readGitCommits = git.commitCount();
                collectTags(project, root, projectRevision, events, limitations, collectionState);
                collectGitHubCollaboration(project, root, projectRevision, events, limitations);
                collectWorktree(project, root, projectRevision, events, limitations, collectionState);
            } else {
                collectionState.complete = false;
                limitations.add("已绑定目录没有可读取的 Git HEAD；历程按当前材料和项目事实降级。 ");
            }
        } else if (root != null) {
            limitations.add("项目目录没有 Git 元数据；只能提供 current-state-only 与已有事实覆盖。 ");
        } else {
            collectionState.complete = false;
            limitations.add("项目尚未绑定可读取的本地目录；只能使用数据库内已有事实。 ");
        }

        if (root != null && !gitAvailable) {
            collectFilesystemCurrent(project, root, projectRevision, events, limitations, collectionState);
        }
        if (root != null) collectAgentResults(projectId, root, projectRevision, events, limitations, collectionState);
        collectProjectFacts(projectId, projectRevision, events, limitations, collectionState);

        List<CollectedEvent> ordered = events.values().stream()
            .sorted(sourceEventOrder(events.values()))
            .limit(MAX_EVENTS)
            .toList();
        if (events.size() > MAX_EVENTS) {
            collectionState.complete = false;
            limitations.add("来源事件超过单次安全上限 " + MAX_EVENTS + "；超出部分未进入本次快照，覆盖状态为 INCOMPLETE。 ");
        }
        String fingerprint = fingerprint(ordered);
        boolean sourceScanComplete = collectionState.complete && events.size() <= MAX_EVENTS;
        boolean complete = sourceScanComplete
            && gitAvailable
            && gitCommitCountKnown
            && totalGitCommits <= readGitCommits
            ;
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        ordered.forEach(event -> sourceCounts.merge(event.sourceType().name(), 1, Integer::sum));
        return new CollectionOutcome(
            project, root, projectRevision, fingerprint, ordered, complete, sourceScanComplete, gitAvailable,
            shallowGitHistory, gitCommitCountKnown, totalGitCommits, readGitCommits,
            Map.copyOf(sourceCounts), compact(limitations, 30)
        );
    }

    private Path resolveRoot(ProjectMemory memory, List<String> limitations) {
        if (memory == null || memory.getLocalProjectPath() == null || memory.getLocalProjectPath().isBlank()) return null;
        try {
            return pathGuard.requireProjectDirectory(memory.getLocalProjectPath()).path();
        } catch (AppException exception) {
            limitations.add("已绑定项目目录当前不可读取；未持久化机器绝对路径。 ");
            return null;
        }
    }

    private void discoverProjectionRoots(
        Path root,
        UUID projectId,
        CollectionState collectionState,
        List<String> limitations
    ) {
        int[] visited = {0};
        int[] manifests = {0};
        boolean[] truncated = {false};
        try {
            Files.walkFileTree(root, Set.of(), MAX_PROJECTION_SCAN_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (visited[0]++ >= MAX_PROJECTION_SCAN_ENTRIES) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    if (directory.equals(root)) return FileVisitResult.CONTINUE;
                    String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (NOISE_SEGMENTS.contains(name) || PROJECTFLOW_METADATA_NAMES.contains(name)
                        || Files.isSymbolicLink(directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (visited[0]++ >= MAX_PROJECTION_SCAN_ENTRIES) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    if (!file.getFileName().toString().equalsIgnoreCase(".projectflow-manifest.json")
                        || Files.isSymbolicLink(file) || attributes.size() > MAX_PROJECTION_MANIFEST_BYTES) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (manifests[0]++ >= MAX_PROJECTION_MANIFESTS) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    try {
                        JsonNode manifest = objectMapper.readTree(Files.readAllBytes(file));
                        if (!projectId.toString().equals(manifest.path("projectId").asText())
                            || !manifest.path("files").isObject()) {
                            return FileVisitResult.CONTINUE;
                        }
                        String relativeRoot = safeRelativePath(root.relativize(file.getParent()).toString());
                        if (!relativeRoot.isBlank()) collectionState.projectionRoots.add(relativeRoot);
                    } catch (IOException ignored) {
                        // Invalid or concurrently replaced manifests are not trusted as exclusion boundaries.
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            limitations.add("Obsidian 投影管理根无法完整识别；已知 ProjectFlow 元数据仍按 raw-only 处理。 ");
        }
        if (truncated[0]) {
            limitations.add("Obsidian 投影管理根发现达到有界上限；未确认目录不会被自动当成受管投影。 ");
        }
    }

    private GitCollection collectGit(
        ProjectSpace project,
        Path root,
        String projectRevision,
        int totalCommits,
        boolean totalCountKnown,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        Set<String> removedPaths = new LinkedHashSet<>();
        List<CommitDraft> discovered = new ArrayList<>();
        int boundedTotal = totalCountKnown ? Math.min(totalCommits, MAX_COMMITS) : MAX_COMMITS;
        for (int offset = 0; offset < boundedTotal && events.size() < MAX_EVENTS; offset += COMMIT_PAGE_SIZE) {
            ModelCancellationContext.throwIfCancelled();
            String output = command(
                root,
                limitations,
                collectionState,
                "git", "log", "--all", "--topo-order", "--date=iso-strict",
                "--pretty=format:__PF_COMMIT__%x09%H%x09%P%x09%aI%x09%an%x09%s%x09%D",
                "--name-status", "--find-renames=50%",
                "--max-count=" + Math.min(COMMIT_PAGE_SIZE, boundedTotal - offset), "--skip=" + offset, "--"
            );
            if (output.isBlank()) break;
            List<CommitDraft> commits = parseCommits(output, project.getCreatedAt(), limitations);
            if (commits.isEmpty()) break;
            if (commits.stream().anyMatch(commit -> commit.changes().size() >= MAX_FILE_EVENTS_PER_COMMIT)) {
                collectionState.complete = false;
            }
            discovered.addAll(commits);
            if (commits.size() < Math.min(COMMIT_PAGE_SIZE, boundedTotal - offset)) break;
        }
        java.util.Collections.reverse(discovered);
        for (CommitDraft commit : discovered) {
            addCommitEvents(project, projectRevision, commit, removedPaths, events, limitations, collectionState);
            if (events.size() >= MAX_EVENTS) break;
        }
        if (totalCountKnown && discovered.size() < Math.min(totalCommits, MAX_COMMITS)) {
            collectionState.complete = false;
            limitations.add("Git 历史分页未达到已确认的提交总数；本次覆盖状态为 INCOMPLETE。 ");
        }
        if (totalCountKnown && totalCommits > MAX_COMMITS) {
            collectionState.complete = false;
            limitations.add("Git 提交超过单次安全上限 " + MAX_COMMITS + "；需要后续有界续扫，不能把当前结果视为完整历史。 ");
        }
        return new GitCollection(discovered.size());
    }

    private List<CommitDraft> parseCommits(String output, Instant fallback, List<String> limitations) {
        List<CommitDraft> result = new ArrayList<>();
        CommitDraft current = null;
        for (String raw : output.split("\\R", -1)) {
            String line = raw.stripTrailing();
            if (line.startsWith("__PF_COMMIT__\t")) {
                if (current != null) result.add(current);
                String[] parts = line.split("\\t", 7);
                current = parts.length >= 6
                    ? new CommitDraft(
                        parts[1], list(parts[2].split(" +"), 8), parseInstant(parts[3], fallback),
                        safeLabel(parts[4], 160), safeLabel(parts[5], 500), parts.length >= 7 ? safeLabel(parts[6], 500) : "",
                        new ArrayList<>()
                    )
                    : null;
                if (current == null) limitations.add("一个 Git 提交头无法解析，已跳过该条来源元数据。 ");
                continue;
            }
            if (current == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 3);
            if (parts.length < 2) continue;
            String status = parts[0].trim();
            String first = safeRelativePath(parts[1]);
            String second = parts.length >= 3 ? safeRelativePath(parts[2]) : "";
            if (first.isBlank() && second.isBlank()) continue;
            if (current.changes().size() < MAX_FILE_EVENTS_PER_COMMIT) {
                current.changes().add(new FileChange(status, first, second));
            }
        }
        if (current != null) result.add(current);
        return result;
    }

    private void addCommitEvents(
        ProjectSpace project,
        String projectRevision,
        CommitDraft commit,
        Set<String> removedPaths,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        if (!isCommitSha(commit.sha())) return;
        List<FileChange> changes = commit.changes();
        List<String> paths = changes.stream().flatMap(change -> Stream.of(change.first(), change.second()))
            .filter(value -> !value.isBlank()).distinct().limit(MAX_LIST_ITEMS).toList();
        List<String> semanticPaths = paths.stream()
            .filter(path -> !redactor.isSensitivePath(path) && !noisePath(path)
                && !projectflowMetadataPath(path, collectionState))
            .toList();
        boolean bulkSemanticChange = semanticPaths.size() >= 20;
        List<String> subjects = bulkSemanticChange
            ? bulkSubjects(semanticPaths, commit.subject())
            : semanticPaths.stream().map(ProjectHistorySourceCollector::historySubjectKey)
                .filter(value -> !value.isBlank()).distinct().limit(20).toList();
        if (subjects.isEmpty() && !paths.isEmpty()) {
            List<String> rawOnlySubjects = new ArrayList<>();
            if (paths.stream().anyMatch(redactor::isSensitivePath)) rawOnlySubjects.add("sensitive-material");
            if (paths.stream().anyMatch(ProjectHistorySourceCollector::noisePath)) rawOnlySubjects.add("dependency-metadata");
            if (paths.stream().anyMatch(path -> projectflowMetadataPath(path, collectionState))) {
                rawOnlySubjects.add("projectflow-metadata");
            }
            subjects = List.copyOf(rawOnlySubjects);
        }
        Transition commitTransition = commit.parents().size() > 1
            ? Transition.MERGED
            : explicitCommitTransition(commit.subject());
        Category commitCategory = commit.parents().size() > 1 ? Category.MERGE : Category.COMMIT;
        List<String> relations = commit.parents().stream().map(parent -> "parent:" + parent).toList();
        List<String> eventLimitations = new ArrayList<>();
        if (commit.subject().isBlank() || genericCommitSubject(commit.subject())) {
            eventLimitations.add("Commit message 缺少可验证的变化语义；仅作为来源元数据。 ");
        }
        if (changes.size() >= MAX_FILE_EVENTS_PER_COMMIT) {
            eventLimitations.add("该提交的文件变化达到单提交上限，覆盖可能不完整。 ");
        }
        add(events, event(
            project.getId(), SourceType.GIT, commit.sha(), commit.sha(), projectRevision, commit.occurredAt(),
            commit.author(), Scope.HISTORICAL, commitCategory, commitTransition,
            commit.subject().isBlank() ? "无标题 Git 提交 " + shortSha(commit.sha()) : commit.subject(),
            paths, subjects, List.of("commit:" + commit.sha()), relations, Authority.SOURCE_BACKED,
            ProjectFactEpistemicStatus.OBSERVED, Map.of("source", "git", "fileChangeCount", changes.size()),
            eventLimitations, gitLink(project.getRepoUrl(), "commit", commit.sha())
        ));

        Map<String, List<FileChange>> bySubject = new LinkedHashMap<>();
        changes.forEach(change -> bySubject.computeIfAbsent(
            historySubjectKey(change.second().isBlank() ? change.first() : change.second()), ignored -> new ArrayList<>()
        ).add(change));
        Set<FileChange> splitAdds = splitAdds(changes);
        Set<FileChange> mergeAdds = mergeAdds(changes);
        Set<FileChange> replacementAdds = replacementAdds(changes);
        for (FileChange change : changes) {
            List<String> affected = Stream.of(change.first(), change.second()).filter(value -> !value.isBlank()).distinct().toList();
            String currentPath = change.second().isBlank() ? change.first() : change.second();
            Transition transition = fileTransition(change, removedPaths);
            List<String> changeRelations = new ArrayList<>();
            changeRelations.add("commit:" + commit.sha());
            if (!change.second().isBlank()) changeRelations.add("previous-path:" + change.first());
            if (splitAdds.contains(change)) transition = Transition.SPLIT;
            if (mergeAdds.contains(change)) transition = Transition.MERGED;
            if (replacementAdds.contains(change)) transition = Transition.REPLACED;
            boolean sensitive = affected.stream().anyMatch(redactor::isSensitivePath);
            boolean noise = affected.stream().anyMatch(ProjectHistorySourceCollector::noisePath);
            boolean projectflowMetadata = affected.stream().anyMatch(path -> projectflowMetadataPath(path, collectionState));
            List<String> fileLimitations = new ArrayList<>();
            if (sensitive) fileLimitations.add("敏感路径只保存元数据，未读取内容。 ");
            if (noise) fileLimitations.add("生成、依赖或构建输出只保留原始事件，不进入语义摘要输入。 ");
            if (projectflowMetadata) {
                fileLimitations.add("ProjectFlow 管理元数据只保留原始事件，避免投影或 Agent 桥内容反馈进入语义历程。 ");
            }
            List<String> fileSubjects = sensitive ? List.of("sensitive-material")
                : projectflowMetadata ? List.of("projectflow-metadata")
                : noise ? List.of("dependency-metadata")
                : bulkSemanticChange && BULK_AREA_TRANSITIONS.contains(transition)
                    ? affected.stream().map(ProjectHistorySourceCollector::historyAreaKey).distinct().limit(8).toList()
                : fileSubjects(affected, transition, changes);
            add(events, event(
                project.getId(), SourceType.GIT,
                commit.sha() + ":" + change.status() + ":" + String.join("->", affected), commit.sha(), projectRevision,
                commit.occurredAt(), commit.author(), Scope.HISTORICAL, Category.FILE_CHANGE, transition,
                fileLabel(transition, currentPath), affected,
                fileSubjects, List.of("commit:" + commit.sha(), "file:" + currentPath),
                changeRelations, Authority.SOURCE_BACKED, ProjectFactEpistemicStatus.OBSERVED,
                Map.of("source", "git-name-status", "metadataOnly", sensitive || noise || projectflowMetadata), fileLimitations, ""
            ));
        }
        if (!semanticPaths.isEmpty() || paths.isEmpty()) {
            addExplicitReferences(project, projectRevision, commit, events);
        }
        if (bySubject.size() >= 3 && genericCommitSubject(commit.subject())) {
            limitations.add("检测到一个语义不足且跨多个项目要素的提交；变化故事按项目要素拆分，未直接复述 Commit message。 ");
        }
    }

    private Set<FileChange> splitAdds(List<FileChange> changes) {
        List<FileChange> deleted = changes.stream().filter(change -> change.status().startsWith("D")).toList();
        List<FileChange> added = changes.stream().filter(change -> change.status().startsWith("A")).toList();
        if (deleted.size() != 1 || added.size() < 2) return Set.of();
        String source = historySubjectKey(deleted.get(0).first());
        return added.stream().allMatch(change -> relatedSubject(source, historySubjectKey(change.first())))
            ? new LinkedHashSet<>(added) : Set.of();
    }

    private Set<FileChange> mergeAdds(List<FileChange> changes) {
        List<FileChange> deleted = changes.stream().filter(change -> change.status().startsWith("D")).toList();
        List<FileChange> added = changes.stream().filter(change -> change.status().startsWith("A")).toList();
        if (deleted.size() < 2 || added.size() != 1) return Set.of();
        String target = historySubjectKey(added.get(0).first());
        return deleted.stream().allMatch(change -> relatedSubject(target, historySubjectKey(change.first())))
            ? Set.of(added.get(0)) : Set.of();
    }

    private Set<FileChange> replacementAdds(List<FileChange> changes) {
        List<FileChange> deleted = changes.stream().filter(change -> change.status().startsWith("D")).toList();
        List<FileChange> added = changes.stream().filter(change -> change.status().startsWith("A")).toList();
        if (deleted.size() != 1 || added.size() != 1) return Set.of();
        return relatedSubject(historySubjectKey(deleted.get(0).first()), historySubjectKey(added.get(0).first()))
            ? Set.of(added.get(0)) : Set.of();
    }

    private static List<String> fileSubjects(List<String> affected, Transition transition, List<FileChange> changes) {
        LinkedHashSet<String> subjects = affected.stream().map(ProjectHistorySourceCollector::historySubjectKey)
            .filter(value -> !value.isBlank()).collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (Set.of(Transition.SPLIT, Transition.MERGED, Transition.REPLACED).contains(transition)) {
            changes.stream().filter(change -> change.status().toUpperCase(Locale.ROOT).startsWith("D"))
                .map(change -> historySubjectKey(change.first())).filter(value -> !value.isBlank()).forEach(subjects::add);
        }
        return subjects.stream().limit(20).toList();
    }

    private void addExplicitReferences(
        ProjectSpace project,
        String projectRevision,
        CommitDraft commit,
        Map<String, CollectedEvent> events
    ) {
        Matcher pr = PR_REFERENCE.matcher(commit.subject());
        Set<String> prNumbers = new LinkedHashSet<>();
        while (pr.find() && prNumbers.size() < 10) prNumbers.add(pr.group(1));
        for (String number : prNumbers) {
            add(events, event(
                project.getId(), SourceType.GITHUB, "pull-request:" + number, commit.sha(), projectRevision,
                commit.occurredAt(), commit.author(), Scope.HISTORICAL, Category.PULL_REQUEST, Transition.MERGED,
                "合并 Pull Request #" + number, List.of(), List.of("pull-request-" + number),
                List.of("commit:" + commit.sha()), List.of("merged-by:" + commit.sha()), Authority.SOURCE_BACKED,
                ProjectFactEpistemicStatus.OBSERVED, Map.of("source", "explicit-commit-reference"), List.of(),
                gitLink(project.getRepoUrl(), "pull", number)
            ));
        }
        Matcher reference = NUMBER_REFERENCE.matcher(commit.subject());
        Set<String> issues = new LinkedHashSet<>();
        while (reference.find() && issues.size() < 10) {
            String number = reference.group(1);
            if (!prNumbers.contains(number)) issues.add(number);
        }
        for (String number : issues) {
            add(events, event(
                project.getId(), SourceType.GITHUB, "issue-reference:" + number + ":" + commit.sha(), commit.sha(), projectRevision,
                commit.occurredAt(), commit.author(), Scope.HISTORICAL, Category.ISSUE, Transition.MODIFIED,
                "提交引用 Issue #" + number, List.of(), List.of("issue-" + number),
                List.of("commit:" + commit.sha()), List.of("referenced-by:" + commit.sha()), Authority.SOURCE_BACKED,
                ProjectFactEpistemicStatus.OBSERVED, Map.of("source", "explicit-commit-reference"),
                List.of("只确认提交文本存在引用，不推断 Issue 状态或变更原因。 "),
                gitLink(project.getRepoUrl(), "issues", number)
            ));
        }
    }

    private void collectTags(
        ProjectSpace project,
        Path root,
        String projectRevision,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        String output = command(
            root, limitations, collectionState, "git", "for-each-ref", "refs/tags", "--sort=creatordate",
            "--format=__PF_TAG__%09%(refname:short)%09%(objectname)%09%(creatordate:iso-strict)%09%(subject)"
        );
        for (String line : output.split("\\R")) {
            if (!line.startsWith("__PF_TAG__\t")) continue;
            String[] parts = line.split("\\t", 5);
            if (parts.length < 4) continue;
            String tag = safeLabel(parts[1], 300);
            String revision = safeLabel(parts[2], 180);
            Instant occurred = parseInstant(parts[3], project.getCreatedAt());
            String subject = parts.length >= 5 ? safeLabel(parts[4], 500) : "";
            add(events, event(
                project.getId(), SourceType.GIT, "tag:" + tag, revision, projectRevision, occurred, "",
                Scope.HISTORICAL, Category.TAG, Transition.CREATED,
                subject.isBlank() ? "创建 Tag " + tag : "创建 Tag " + tag + "：" + subject,
                List.of(), List.of("tag-" + normalizeStableKey(tag)), List.of("tag:" + tag),
                List.of("points-to:" + revision), Authority.SOURCE_BACKED, ProjectFactEpistemicStatus.OBSERVED,
                Map.of("source", "git-tag-metadata"), List.of(), gitLink(project.getRepoUrl(), "releases/tag", tag)
            ));
        }
    }

    private void collectGitHubCollaboration(
        ProjectSpace project,
        Path root,
        String projectRevision,
        Map<String, CollectedEvent> events,
        List<String> limitations
    ) {
        String repository = githubRepository(project.getRepoUrl());
        if (repository.isBlank()) {
            LocalCommandExecutor.CommandResult remote = optionalCommand(root, "git", "remote", "get-url", "origin");
            if (remote.exitCode() == 0 && !remote.timedOut()) repository = githubRepository(remote.output().trim());
        }
        if (repository.isBlank()) return;

        LocalCommandExecutor.CommandResult auth = optionalCommand(root, "gh", "auth", "status");
        if (auth.exitCode() != 0 || auth.timedOut()) {
            limitations.add("GitHub 协作元数据未参与本次历程；本地 Git 历史仍可完整读取。 ");
            return;
        }

        LocalCommandExecutor.CommandResult pullRequests = optionalCommand(
            root, "gh", "pr", "list", "--repo", repository, "--state", "all", "--limit",
            Integer.toString(MAX_GITHUB_ITEMS), "--json",
            "number,title,body,createdAt,updatedAt,closedAt,mergedAt,author,url,mergeCommit"
        );
        if (pullRequests.exitCode() == 0 && !pullRequests.timedOut()) {
            collectPullRequests(project, projectRevision, events, pullRequests.output(), limitations);
        } else {
            limitations.add("GitHub Pull Request 元数据当前不可读取；未影响本地来源事件。 ");
        }

        LocalCommandExecutor.CommandResult issues = optionalCommand(
            root, "gh", "issue", "list", "--repo", repository, "--state", "all", "--limit",
            Integer.toString(MAX_GITHUB_ITEMS), "--json",
            "number,title,body,createdAt,updatedAt,closedAt,author,url,state"
        );
        if (issues.exitCode() == 0 && !issues.timedOut()) {
            collectIssues(project, projectRevision, events, issues.output(), limitations);
        } else {
            limitations.add("GitHub Issue 元数据当前不可读取；未影响本地来源事件。 ");
        }
    }

    private void collectPullRequests(
        ProjectSpace project,
        String projectRevision,
        Map<String, CollectedEvent> events,
        String output,
        List<String> limitations
    ) {
        JsonNode values = jsonArray(output, "Pull Request", limitations);
        if (values == null) return;
        for (JsonNode value : values) {
            String number = value.path("number").asText("").trim();
            if (!number.matches("\\d+")) continue;
            String mergeRevision = safeLabel(value.path("mergeCommit").path("oid").asText(""), 180);
            Instant occurredAt = firstInstant(
                value.path("mergedAt").asText(""), value.path("closedAt").asText(""),
                value.path("updatedAt").asText(""), value.path("createdAt").asText(""), project.getCreatedAt()
            );
            List<String> subjects = subjectsForRevision(events, mergeRevision);
            if (subjects.isEmpty()) subjects = List.of("pull-request-" + number);
            List<String> paths = pathsForRevision(events, mergeRevision);
            String title = safeOutbound(value.path("title").asText(""), 300);
            String rationale = rationale(value.path("body").asText(""));
            String label = "Pull Request #" + number + (title.isBlank() ? "" : "：" + title)
                + (rationale.isBlank() ? "" : "；说明：" + rationale);
            List<String> evidence = new ArrayList<>(List.of("github-pr:" + number));
            List<String> relations = new ArrayList<>();
            if (isCommitSha(mergeRevision)) {
                evidence.add("commit:" + mergeRevision);
                relations.add("merged-by:" + mergeRevision);
            }
            add(events, event(
                project.getId(), SourceType.GITHUB, "pull-request:" + number,
                mergeRevision.isBlank() ? value.path("updatedAt").asText("") : mergeRevision,
                projectRevision, occurredAt, safeOutbound(value.path("author").path("login").asText(""), 160),
                Scope.HISTORICAL, Category.PULL_REQUEST,
                value.path("mergedAt").asText("").isBlank() ? Transition.MODIFIED : Transition.MERGED,
                label, paths, subjects, evidence, relations, Authority.DECLARED, ProjectFactEpistemicStatus.DECLARED,
                Map.of("source", "github-cli", "bodyExcerptIncluded", !rationale.isBlank()),
                List.of("PR 标题和有界正文摘要只作为 DECLARED 原因证据，不证明实现或验证结果。 "),
                safeGitHubLink(value.path("url").asText(""))
            ));
        }
    }

    private void collectIssues(
        ProjectSpace project,
        String projectRevision,
        Map<String, CollectedEvent> events,
        String output,
        List<String> limitations
    ) {
        JsonNode values = jsonArray(output, "Issue", limitations);
        if (values == null) return;
        for (JsonNode value : values) {
            String number = value.path("number").asText("").trim();
            if (!number.matches("\\d+")) continue;
            Instant occurredAt = firstInstant(
                value.path("closedAt").asText(""), value.path("updatedAt").asText(""),
                value.path("createdAt").asText(""), "", project.getCreatedAt()
            );
            String title = safeOutbound(value.path("title").asText(""), 300);
            String rationale = rationale(value.path("body").asText(""));
            String label = "Issue #" + number + (title.isBlank() ? "" : "：" + title)
                + (rationale.isBlank() ? "" : "；说明：" + rationale);
            add(events, event(
                project.getId(), SourceType.GITHUB, "issue:" + number,
                value.path("updatedAt").asText(""), projectRevision, occurredAt,
                safeOutbound(value.path("author").path("login").asText(""), 160), Scope.HISTORICAL,
                Category.ISSUE, Transition.MODIFIED, label, List.of(), List.of("issue-" + number),
                List.of("github-issue:" + number), List.of(), Authority.DECLARED,
                ProjectFactEpistemicStatus.DECLARED,
                Map.of("source", "github-cli", "bodyExcerptIncluded", !rationale.isBlank()),
                List.of("Issue 标题和有界正文摘要只作为 DECLARED 原因证据，不证明实现或验证结果。 "),
                safeGitHubLink(value.path("url").asText(""))
            ));
        }
    }

    private JsonNode jsonArray(String output, String source, List<String> limitations) {
        if (output == null || output.isBlank()) return objectMapper.createArrayNode();
        try {
            JsonNode root = objectMapper.readTree(output);
            if (root.isArray()) return root;
        } catch (IOException ignored) {
            // The fixed command may hit its output bound; partial JSON is never trusted.
        }
        limitations.add("GitHub " + source + " 元数据格式无法完整校验，已忽略该可选来源。 ");
        return null;
    }

    private List<String> subjectsForRevision(Map<String, CollectedEvent> events, String revision) {
        if (!isCommitSha(revision)) return List.of();
        return events.values().stream()
            .filter(item -> item.sourceType() == SourceType.GIT && revision.equals(item.sourceRevision()))
            .flatMap(item -> item.subjectKeys().stream())
            .filter(value -> !value.isBlank())
            .distinct().limit(20).toList();
    }

    private List<String> pathsForRevision(Map<String, CollectedEvent> events, String revision) {
        if (!isCommitSha(revision)) return List.of();
        return events.values().stream()
            .filter(item -> item.sourceType() == SourceType.GIT && revision.equals(item.sourceRevision()))
            .flatMap(item -> item.affectedPaths().stream())
            .filter(value -> !value.isBlank())
            .distinct().limit(MAX_LIST_ITEMS).toList();
    }

    private String rationale(String body) {
        if (body == null || body.isBlank()) return "";
        for (String paragraph : body.replace('\r', '\n').split("\\n\\s*\\n")) {
            String compact = paragraph.replaceAll("\\s+", " ").trim();
            if (!compact.isBlank()) return safeOutbound(compact, MAX_GITHUB_RATIONALE_CHARS);
        }
        return "";
    }

    private String safeOutbound(String value, int maxChars) {
        return safeLabel(redactor.redactOutboundText(value), maxChars);
    }

    private static String githubRepository(String value) {
        if (value == null || value.isBlank()) return "";
        Matcher matcher = GITHUB_REPOSITORY.matcher(value.trim());
        return matcher.matches() ? matcher.group(1) : "";
    }

    private static String safeGitHubLink(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                && "github.com".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null ? uri.toString() : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static Instant firstInstant(
        String first,
        String second,
        String third,
        String fourth,
        Instant fallback
    ) {
        for (String value : new String[] { first, second, third, fourth }) {
            if (value == null || value.isBlank()) continue;
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                // Continue to the next bounded timestamp candidate.
            }
        }
        return fallback == null ? Instant.EPOCH : fallback;
    }

    private LocalCommandExecutor.CommandResult optionalCommand(Path root, String... command) {
        return commandExecutor.execute(root, List.of(command), OPTIONAL_SOURCE_TIMEOUT);
    }

    private void collectWorktree(
        ProjectSpace project,
        Path root,
        String projectRevision,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        String output = command(
            root, limitations, collectionState, "git", "status", "--porcelain=v1", "--untracked-files=all"
        );
        if (output.isBlank()) return;
        for (String raw : output.split("\\R")) {
            if (raw.length() < 4) continue;
            String status = raw.substring(0, 2);
            String rawPath = raw.substring(3).trim();
            String path = safeRelativePath(rawPath.contains(" -> ") ? rawPath.substring(rawPath.indexOf(" -> ") + 4) : rawPath);
            if (path.isBlank()) continue;
            Transition transition = status.contains("?") || status.contains("A") ? Transition.CREATED
                : status.contains("D") ? Transition.REMOVED
                : status.contains("R") ? Transition.MOVED
                : Transition.MODIFIED;
            List<String> eventLimitations = new ArrayList<>();
            boolean sensitive = redactor.isSensitivePath(path);
            boolean noise = noisePath(path);
            boolean projectflowMetadata = projectflowMetadataPath(path, collectionState);
            if (sensitive) eventLimitations.add("敏感工作区路径只保存元数据，未读取内容。 ");
            if (noise) eventLimitations.add("生成、依赖或构建输出只保留原始事件，不进入语义摘要输入。 ");
            if (projectflowMetadata) {
                eventLimitations.add("ProjectFlow 管理元数据只保留原始事件，避免投影或 Agent 桥内容反馈进入语义历程。 ");
            }
            String subject = sensitive ? "sensitive-material"
                : projectflowMetadata ? "projectflow-metadata"
                : noise ? "dependency-metadata"
                : historySubjectKey(path);
            String revision = worktreeRevision(root, status, path);
            add(events, event(
                project.getId(), SourceType.FILESYSTEM, "worktree:" + status + ":" + path, revision, projectRevision,
                fileModifiedAt(root, path, project.getUpdatedAt()), "", Scope.CURRENT, Category.FILE_CHANGE, transition,
                "工作区" + transitionLabel(transition) + " " + path, List.of(path), List.of(subject),
                List.of("worktree:" + revision, "file:" + path), List.of(), Authority.SOURCE_BACKED,
                ProjectFactEpistemicStatus.OBSERVED, Map.of("source", "git-status", "metadataOnly", true),
                eventLimitations, ""
            ));
        }
    }

    private void collectAgentResults(
        UUID projectId,
        Path root,
        String projectRevision,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        Path resultRoot = root.resolve(".projectflow/agent-results").toAbsolutePath().normalize();
        if (!Files.isDirectory(resultRoot)) return;
        try (Stream<Path> paths = Files.walk(resultRoot, 3)) {
            List<Path> resultFiles = paths.filter(Files::isRegularFile)
                .filter(item -> !Files.isSymbolicLink(item))
                .filter(item -> item.getFileName().toString().equals("result.json"))
                .sorted().limit(MAX_AGENT_RESULTS + 1L).toList();
            if (resultFiles.size() > MAX_AGENT_RESULTS) {
                collectionState.complete = false;
                limitations.add("Agent Result 超过单次安全上限 " + MAX_AGENT_RESULTS + "；未读取部分保留为覆盖缺口。 ");
            }
            for (Path path : resultFiles.stream().limit(MAX_AGENT_RESULTS).toList()) {
                ModelCancellationContext.throwIfCancelled();
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(resultRoot) || Files.size(normalized) > 1_000_000) continue;
                byte[] bytes = Files.readAllBytes(normalized);
                JsonNode rootNode = objectMapper.readTree(bytes);
                String relative = safeRelativePath(root.relativize(normalized).toString());
                List<String> keyFiles = jsonStrings(rootNode.path("keyFiles"), 60).stream()
                    .map(ProjectHistorySourceCollector::safeRelativePath).filter(value -> !value.isBlank()).toList();
                List<String> changes = jsonStrings(rootNode.path("actualChanges"), 8).stream()
                    .map(value -> safeLabel(value, 500)).filter(value -> !value.isBlank()).toList();
                String goal = safeLabel(rootNode.path("taskGoal").asText(""), 500);
                String label = changes.isEmpty() ? goal : changes.get(0);
                if (label.isBlank()) label = "Agent 工作结果 " + relative;
                List<String> evidence = new ArrayList<>();
                evidence.add("agent-result:" + relative);
                keyFiles.stream().limit(40).forEach(file -> evidence.add("file:" + file));
                add(events, event(
                    projectId, SourceType.AGENT_RESULT, relative, sha256(bytes), projectRevision,
                    Files.getLastModifiedTime(normalized).toInstant(), "Agent", Scope.HISTORICAL,
                    Category.AGENT_RESULT, Transition.MODIFIED, label, keyFiles,
                    keyFiles.stream().map(ProjectHistorySourceCollector::historySubjectKey).distinct().limit(20).toList(),
                    evidence, List.of(), Authority.PROCESS_EVIDENCE, ProjectFactEpistemicStatus.PROCESS_EVIDENCE,
                    Map.of("source", "agent-result", "claimOnly", true),
                    List.of("Agent 完成或测试声明属于过程证据，未经独立验证不能升级为强事实。 "), ""
                ));
            }
        } catch (IOException exception) {
            collectionState.complete = false;
            limitations.add("Agent Result 目录无法完整读取；本次继续使用其他来源。 ");
        }
    }

    private void collectProjectFacts(
        UUID projectId,
        String projectRevision,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        int pageNumber = 0;
        Page<ProjectFact> page;
        do {
            page = factRepository.findByProjectIdOrderByOccurredFromAscCreatedAtAsc(projectId, PageRequest.of(pageNumber++, 200));
            for (ProjectFact fact : page.getContent()) {
                ModelCancellationContext.throwIfCancelled();
                Instant occurred = first(fact.getOccurredTo(), fact.getOccurredFrom(), fact.getCreatedAt());
                Category category = fact.getEpistemicStatus() == ProjectFactEpistemicStatus.DECLARED
                    ? Category.USER_DECLARATION : Category.PROJECT_FACT;
                Authority authority = fact.getRecordStatus() == ProjectFactRecordStatus.RECORDED
                    && fact.getEpistemicStatus().isStrongFact() ? Authority.FACTUAL_SOURCE
                    : fact.getEpistemicStatus() == ProjectFactEpistemicStatus.DECLARED ? Authority.DECLARED
                    : fact.getEpistemicStatus() == ProjectFactEpistemicStatus.PROCESS_EVIDENCE ? Authority.PROCESS_EVIDENCE
                    : Authority.UNKNOWN;
                List<String> paths = fact.getAffectedFiles().stream().map(ProjectHistorySourceCollector::safeRelativePath)
                    .filter(value -> !value.isBlank()).limit(MAX_LIST_ITEMS).toList();
                List<String> evidence = new ArrayList<>();
                evidence.add("fact:" + fact.getId());
                fact.getEvidenceRefs().stream().map(value -> safeLabel(value, 500)).filter(value -> !value.isBlank())
                    .limit(80).forEach(evidence::add);
                List<String> relations = fact.getCommitRefs().stream().limit(40).map(value -> "derived-from:commit:" + value).toList();
                List<String> factLimitations = new ArrayList<>(fact.getLimitations().stream().map(value -> safeLabel(value, 500)).toList());
                if (authority != Authority.FACTUAL_SOURCE) {
                    factLimitations.add("该记录未进入强事实权威路径，历程只保留其原始认知状态。 ");
                }
                add(events, event(
                    projectId, SourceType.PROJECT_FACT, fact.getId().toString(),
                    fact.getRevision().isBlank() ? instant(fact.getUpdatedAt()) : fact.getRevision(), projectRevision,
                    occurred, fact.getCreatedBy(), Scope.HISTORICAL, category, transitionFromFact(fact),
                    fact.getTitle().isBlank() ? fact.getSummary() : fact.getTitle(), paths,
                    subjects(paths, fact.getTitle()), evidence, relations, authority, fact.getEpistemicStatus(),
                    Map.of("source", "project-fact", "recordStatus", fact.getRecordStatus().name()),
                    compact(factLimitations, 30), "/projects/" + projectId + "/facts/" + fact.getId()
                ));
            }
        } while (page.hasNext() && events.size() < MAX_EVENTS);
        if (page.hasNext()) {
            collectionState.complete = false;
            limitations.add("项目事实数量达到来源事件上限；未读取部分已作为覆盖缺口记录。 ");
        }
    }

    private void collectFilesystemCurrent(
        ProjectSpace project,
        Path root,
        String projectRevision,
        Map<String, CollectedEvent> events,
        List<String> limitations,
        CollectionState collectionState
    ) {
        int[] discovered = {0};
        int[] skippedDirectories = {0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (directory.equals(root)) return FileVisitResult.CONTINUE;
                    String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
                    String relative = safeRelativePath(root.relativize(directory).toString());
                    if (NOISE_SEGMENTS.contains(name) || PROJECTFLOW_METADATA_NAMES.contains(name)
                        || projectflowMetadataPath(relative, collectionState) || Files.isSymbolicLink(directory)) {
                        skippedDirectories[0]++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    ModelCancellationContext.throwIfCancelled();
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                    if (discovered[0] >= MAX_CURRENT_FILES || events.size() >= MAX_EVENTS) {
                        collectionState.complete = false;
                        return FileVisitResult.TERMINATE;
                    }
                    String relative = safeRelativePath(root.relativize(file).toString());
                    if (relative.isBlank()) return FileVisitResult.CONTINUE;
                    discovered[0]++;
                    boolean sensitive = redactor.isSensitivePath(relative);
                    boolean noise = noisePath(relative);
                    boolean document = documentPath(relative) && !noise;
                    String revision = sha256(
                        relative + "|" + attributes.size() + "|" + attributes.lastModifiedTime().toMillis()
                    );
                    String subject = sensitive ? "sensitive-material"
                        : noise ? "dependency-metadata"
                        : historyAreaKey(relative);
                    List<String> fileLimitations = new ArrayList<>();
                    fileLimitations.add("无版本历史；文件修改时间仅作为当前元数据，不能证明实际变更原因或完整时间线。 ");
                    if (sensitive) fileLimitations.add("敏感路径只保存元数据，未读取内容。 ");
                    if (noise) fileLimitations.add("生成、依赖或构建输出只保留原始事件，不进入语义摘要输入。 ");
                    add(events, event(
                        project.getId(), document ? SourceType.DOCUMENT : SourceType.FILESYSTEM,
                        "current-file:" + relative, revision, projectRevision,
                        attributes.lastModifiedTime().toInstant(), "", Scope.CURRENT,
                        document ? Category.DOCUMENT_VERSION : Category.FILE_CHANGE,
                        Transition.UNKNOWN_TRANSITION,
                        sensitive ? "检测到敏感材料元数据" : "当前材料版本：" + relative,
                        List.of(relative), List.of(subject),
                        List.of("file:" + relative, "filesystem-metadata:" + revision), List.of(),
                        Authority.SOURCE_BACKED, ProjectFactEpistemicStatus.OBSERVED,
                        Map.of(
                            "source", "filesystem-metadata",
                            "metadataOnly", true,
                            "currentStateOnly", true,
                            "bytes", attributes.size()
                        ),
                        fileLimitations, ""
                    ));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    collectionState.complete = false;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            collectionState.complete = false;
            limitations.add("当前材料目录无法完整读取；未读取范围已保留为覆盖缺口。 ");
        }
        if (discovered[0] >= MAX_CURRENT_FILES || events.size() >= MAX_EVENTS) {
            limitations.add("当前材料超过单次安全上限 " + MAX_CURRENT_FILES + "；本次只保留有界文件元数据。 ");
        }
        if (skippedDirectories[0] > 0) {
            limitations.add("generated、vendor、依赖、构建目录和 ProjectFlow 管理元数据未进入 current-state 语义事件。 ");
        }
    }

    private CollectedEvent event(
        UUID projectId,
        SourceType sourceType,
        String sourceIdentity,
        String sourceRevision,
        String projectRevision,
        Instant occurredAt,
        String actorLabel,
        Scope scope,
        Category category,
        Transition transition,
        String safeSourceLabel,
        List<String> affectedPaths,
        List<String> subjectKeys,
        List<String> evidenceRefs,
        List<String> relationRefs,
        Authority authority,
        ProjectFactEpistemicStatus epistemicStatus,
        Map<String, Object> coverage,
        List<String> limitations,
        String deepLink
    ) {
        String identity = safeLabel(sourceIdentity, 500);
        String revision = safeLabel(sourceRevision, 180);
        String stableKey = sha256(projectId + "|" + sourceType + "|" + identity + "|" + revision);
        List<String> paths = compact(affectedPaths.stream().map(ProjectHistorySourceCollector::safeRelativePath)
            .filter(value -> !value.isBlank()).toList(), MAX_LIST_ITEMS);
        List<String> subjects = compact(subjectKeys.stream().map(ProjectHistorySourceCollector::normalizeStableKey)
            .filter(value -> !value.isBlank()).toList(), 30);
        List<String> evidence = compact(evidenceRefs.stream().map(value -> safeLabel(value, 500))
            .filter(value -> !value.isBlank()).toList(), MAX_LIST_ITEMS);
        List<String> relations = compact(relationRefs.stream().map(value -> safeLabel(value, 500))
            .filter(value -> !value.isBlank()).toList(), MAX_LIST_ITEMS);
        List<String> safeLimitations = compact(limitations.stream().map(value -> safeLabel(value, 500))
            .filter(value -> !value.isBlank()).toList(), 30);
        String link = safeDeepLink(deepLink);
        Instant safeOccurred = occurredAt == null ? Instant.EPOCH : occurredAt;
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", sourceType.name());
        payload.put("sourceIdentity", identity);
        payload.put("sourceRevision", revision);
        payload.put("occurredAt", safeOccurred.toString());
        payload.put("category", category.name());
        payload.put("transition", transition.name());
        payload.put("label", safeLabel(safeSourceLabel, 1_000));
        payload.put("paths", paths);
        payload.put("subjects", subjects);
        payload.put("evidence", evidence);
        payload.put("relations", relations);
        payload.put("authority", authority.name());
        payload.put("epistemicStatus", epistemicStatus.name());
        payload.put("coverage", coverage);
        payload.put("limitations", safeLimitations);
        payload.put("deepLink", link);
        String payloadHash = sha256(json(payload));
        return new CollectedEvent(
            stableKey, sourceType, identity, revision, safeLabel(projectRevision, 180), safeOccurred, safeOccurred,
            safeLabel(actorLabel, 160), scope, category, transition, safeLabel(safeSourceLabel, 1_000), paths, subjects,
            evidence, relations, authority, epistemicStatus, Map.copyOf(coverage), safeLimitations, link, payloadHash
        );
    }

    private void add(Map<String, CollectedEvent> events, CollectedEvent event) {
        if (event != null && events.size() < MAX_EVENTS * 2) events.put(event.stableEventKey(), event);
    }

    private String command(
        Path root,
        List<String> limitations,
        CollectionState collectionState,
        String... command
    ) {
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(root, List.of(command), GIT_TIMEOUT);
        if (result.timedOut()) {
            collectionState.complete = false;
            limitations.add("一个固定 Git 命令达到 30 秒超时；该来源范围未静默标记为完成。 ");
            return "";
        }
        if (result.exitCode() != 0) {
            collectionState.complete = false;
            limitations.add("一个固定 Git 元数据命令执行失败；未保存命令输出。 ");
            return "";
        }
        if (result.output().length() >= 100_000) {
            collectionState.complete = false;
            limitations.add("一个固定 Git 命令达到输出上限；仅保留完整解析到的元数据。 ");
        }
        return result.output();
    }

    private String fingerprint(List<CollectedEvent> events) {
        StringBuilder input = new StringBuilder("project-history-source-v1\n");
        events.stream().sorted(Comparator.comparing(CollectedEvent::stableEventKey)).forEach(event ->
            input.append(event.stableEventKey()).append(':').append(event.payloadHash()).append('\n')
        );
        return sha256(input.toString());
    }

    private static Comparator<CollectedEvent> sourceEventOrder(Collection<CollectedEvent> events) {
        Map<String, Integer> gitRevisionOrder = new LinkedHashMap<>();
        for (CollectedEvent event : events) {
            if (event.sourceType() == SourceType.GIT
                && Set.of(Category.COMMIT, Category.MERGE).contains(event.category())
                && !event.sourceRevision().isBlank()) {
                gitRevisionOrder.putIfAbsent(event.sourceRevision(), gitRevisionOrder.size());
            }
        }
        return Comparator.comparing(CollectedEvent::occurredAt)
            .thenComparingInt(event -> gitRevisionOrder.getOrDefault(event.sourceRevision(), Integer.MAX_VALUE))
            .thenComparing(CollectedEvent::sourceRevision)
            .thenComparingInt(event -> eventCategoryOrder(event.category()))
            .thenComparing(event -> event.sourceType().name())
            .thenComparing(CollectedEvent::sourceIdentity)
            .thenComparing(event -> event.transition().name())
            .thenComparing(event -> String.join("\u0000", event.affectedPaths()))
            .thenComparing(CollectedEvent::stableEventKey);
    }

    static int eventCategoryOrder(Category category) {
        return switch (category) {
            case COMMIT, MERGE -> 0;
            case FILE_CHANGE, DOCUMENT_VERSION -> 1;
            case PULL_REQUEST, ISSUE, AGENT_RESULT, VALIDATION, USER_DECLARATION, PROJECT_FACT, EXTERNAL -> 2;
            case TAG -> 3;
        };
    }

    private static Transition fileTransition(FileChange change, Set<String> removedPaths) {
        String status = change.status().toUpperCase(Locale.ROOT);
        String first = change.first();
        String second = change.second();
        if (status.startsWith("R")) {
            removedPaths.remove(first);
            removedPaths.remove(second);
            return sameParent(first, second) ? Transition.RENAMED : Transition.MOVED;
        }
        if (status.startsWith("C")) return Transition.CREATED;
        if (status.startsWith("D")) {
            removedPaths.add(first);
            return Transition.REMOVED;
        }
        if (status.startsWith("A") || status.equals("??")) {
            if (removedPaths.remove(first)) return Transition.RESTORED;
            return Transition.CREATED;
        }
        if (status.startsWith("T")) return Transition.REPLACED;
        return Transition.MODIFIED;
    }

    private static Transition explicitCommitTransition(String subject) {
        String lower = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        if (lower.startsWith("revert ") || lower.contains(" rollback") || lower.contains("撤销")) return Transition.REVERTED;
        if (lower.contains("reapply") || lower.contains("re-apply") || lower.contains("重新实现") || lower.contains("恢复实现")) {
            return Transition.REAPPLIED;
        }
        return Transition.MODIFIED;
    }

    private static Transition transitionFromFact(ProjectFact fact) {
        String value = (fact.getTitle() + " " + fact.getSummary()).toLowerCase(Locale.ROOT);
        if (value.contains("恢复") || value.contains("restore")) return Transition.RESTORED;
        if (value.contains("删除") || value.contains("移除") || value.contains("remove") || value.contains("delete")) return Transition.REMOVED;
        if (value.contains("新增") || value.contains("引入") || value.contains("create") || value.contains("add")) return Transition.CREATED;
        if (value.contains("替换") || value.contains("replace")) return Transition.REPLACED;
        return Transition.MODIFIED;
    }

    static String historySubjectKey(String path) {
        String safe = safeRelativePath(path);
        if (safe.isBlank()) return "unknown-subject";
        String[] segments = safe.split("/");
        String file = segments[segments.length - 1];
        int dot = file.indexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        base = CAMEL_BOUNDARY.matcher(base).replaceAll("$1-$2").toLowerCase(Locale.ROOT);
        base = base.replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        for (String suffix : TYPE_SUFFIXES) {
            String normalizedSuffix = suffix.replaceAll("[^a-z0-9]", "");
            String flattened = base.replace("-", "");
            if (flattened.endsWith(normalizedSuffix) && flattened.length() > normalizedSuffix.length() + 2) {
                int keep = flattened.length() - normalizedSuffix.length();
                base = CAMEL_BOUNDARY.matcher(file.substring(0, Math.min(file.length(), keep))).replaceAll("$1-$2")
                    .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
                break;
            }
        }
        if (base.startsWith("project-history")) return "project-history";
        if (GENERIC_NAMES.contains(base) && segments.length >= 2) {
            base = normalizeStableKey(segments[segments.length - 2]);
        }
        if (base.isBlank()) {
            base = segments.length >= 2 ? normalizeStableKey(segments[segments.length - 2]) : "project-content";
        }
        return base.length() > 100 ? base.substring(0, 100) : base;
    }

    private static List<String> bulkSubjects(List<String> paths, String commitSubject) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!genericCommitSubject(commitSubject)) result.add("change-" + subjectFromText(commitSubject));
        paths.stream().map(ProjectHistorySourceCollector::historyAreaKey).forEach(result::add);
        return result.stream().filter(value -> !value.isBlank()).limit(8).toList();
    }

    static String historyAreaKey(String path) {
        String safe = safeRelativePath(path);
        if (safe.isBlank()) return "project-content";
        String[] segments = safe.split("/");
        if (segments.length < 2) return historySubjectKey(safe);
        String area = normalizeStableKey(segments[0]);
        return area.isBlank() ? historySubjectKey(safe) : "project-area-" + area;
    }

    private static List<String> subjects(List<String> paths, String title) {
        List<String> values = new ArrayList<>();
        paths.stream().map(ProjectHistorySourceCollector::historySubjectKey).forEach(values::add);
        if (values.isEmpty()) values.add(subjectFromText(title));
        return values.stream().filter(value -> !value.isBlank()).distinct().limit(20).toList();
    }

    static String subjectFromText(String value) {
        String safe = normalizeKey(value);
        if (safe.isBlank()) return "project-change";
        String[] tokens = safe.split("-");
        return String.join("-", List.of(tokens).subList(0, Math.min(tokens.length, 6)));
    }

    static String subjectLabel(String key) {
        if (key != null && key.startsWith("project-area-")) {
            String area = key.substring("project-area-".length());
            return switch (area) {
                case "backend" -> "后端区域";
                case "frontend" -> "前端区域";
                case "docs" -> "文档区域";
                case "integrations" -> "集成区域";
                case "scripts" -> "工具脚本区域";
                case "tests", "test" -> "测试区域";
                case "src" -> "源码区域";
                case "github" -> "CI 与协作配置";
                default -> area.replace('-', ' ') + " 区域";
            };
        }
        if (key != null && key.startsWith("change-")) {
            String value = key.substring("change-".length()).replace('-', ' ').trim();
            return value.isBlank() ? "集中变化" : value;
        }
        String normalized = key == null ? "" : key.replace('-', ' ').trim();
        return normalized.isBlank() || normalized.equals("unknown subject") ? "项目内容" : normalized;
    }

    private static boolean relatedSubject(String left, String right) {
        return left.equals(right) || left.startsWith(right + "-") || right.startsWith(left + "-");
    }

    private static boolean genericCommitSubject(String subject) {
        String normalized = normalizeKey(subject);
        return normalized.isBlank() || Set.of("fix", "update", "change", "changes", "调整", "修改", "修复").contains(normalized);
    }

    private static String fileLabel(Transition transition, String path) {
        return transitionLabel(transition) + " " + path;
    }

    private static String transitionLabel(Transition transition) {
        return switch (transition) {
            case CREATED -> "新增";
            case MODIFIED -> "修改";
            case REMOVED -> "删除";
            case RESTORED -> "恢复";
            case RENAMED -> "重命名";
            case MOVED -> "移动";
            case REPLACED -> "替换";
            case SPLIT -> "拆分";
            case MERGED -> "合并";
            case REVERTED -> "撤销";
            case REAPPLIED -> "重新实现";
            default -> "变更";
        };
    }

    private String safeLabel(String value, int max) {
        String safe = redactor.redactOutboundText(value == null ? "" : value).replace('\u0000', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, max - 1) + "…";
    }

    private static String safeRelativePath(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('\\', '/').trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.startsWith("/") || normalized.startsWith("//") || normalized.matches("(?i)^[a-z]:/.*")) return "";
        if (Stream.of(normalized.split("/")).anyMatch(part -> part.equals(".."))) return "";
        if (normalized.length() > 1_000) normalized = normalized.substring(0, 1_000);
        return normalized;
    }

    private static boolean noisePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("package-lock.json") || lower.endsWith("pnpm-lock.yaml") || lower.endsWith("yarn.lock")) return true;
        return Stream.of(lower.split("/")).anyMatch(NOISE_SEGMENTS::contains);
    }

    private static boolean projectflowMetadataPath(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return Stream.of(normalized.split("/")).anyMatch(PROJECTFLOW_METADATA_NAMES::contains);
    }

    private static boolean projectflowMetadataPath(String path, CollectionState collectionState) {
        if (projectflowMetadataPath(path)) return true;
        if (path == null || path.isBlank() || collectionState.projectionRoots.isEmpty()) return false;
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return collectionState.projectionRoots.stream().anyMatch(root -> {
            String normalizedRoot = root.toLowerCase(Locale.ROOT);
            return normalized.equals(normalizedRoot) || normalized.startsWith(normalizedRoot + "/");
        });
    }

    private static boolean documentPath(String path) {
        if (path == null || path.isBlank()) return false;
        String name = path.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        return DOCUMENT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean sameParent(String left, String right) {
        int leftSlash = left.lastIndexOf('/');
        int rightSlash = right.lastIndexOf('/');
        return left.substring(0, Math.max(0, leftSlash)).equals(right.substring(0, Math.max(0, rightSlash)));
    }

    private static String normalizeKey(String value) {
        if (value == null) return "";
        String withBoundaries = CAMEL_BOUNDARY.matcher(value).replaceAll("$1-$2");
        return withBoundaries.toLowerCase(Locale.ROOT)
            .replaceAll("(?i)\\b(feat|fix|docs|chore|refactor|test|build|ci|perf)(\\([^)]*\\))?!?:?", " ")
            .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String normalizeStableKey(String value) {
        if (value == null) return "";
        return CAMEL_BOUNDARY.matcher(value).replaceAll("$1-$2").toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
    }

    static String safeDeepLink(String value) {
        if (value == null || value.isBlank()) return "";
        String safe = value.trim();
        if (safe.startsWith("/projects/") || safe.startsWith("obsidian://")) return safe.length() <= 1_000 ? safe : "";
        try {
            URI uri = URI.create(safe);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) return "";
            return safe.length() <= 1_000 ? safe : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String gitLink(String repoUrl, String kind, String identity) {
        if (repoUrl == null || repoUrl.isBlank()) return "";
        String base = repoUrl.trim().replaceAll("\\.git$", "");
        String candidate = base + "/" + kind + "/" + identity;
        return safeDeepLink(candidate);
    }

    private static Instant fileModifiedAt(Path root, String relative, Instant fallback) {
        try {
            Path target = root.resolve(relative).toAbsolutePath().normalize();
            if (!target.startsWith(root.toAbsolutePath().normalize()) || !Files.exists(target)) return first(fallback, Instant.EPOCH);
            return Files.getLastModifiedTime(target).toInstant();
        } catch (IOException exception) {
            return first(fallback, Instant.EPOCH);
        }
    }

    private static String worktreeRevision(Path root, String status, String relative) {
        try {
            Path target = root.resolve(relative).toAbsolutePath().normalize();
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!target.startsWith(normalizedRoot) || !Files.exists(target)) {
                return sha256(status + "|" + relative + "|missing");
            }
            return sha256(status + "|" + relative + "|" + Files.size(target) + "|"
                + Files.getLastModifiedTime(target).toMillis());
        } catch (IOException exception) {
            return sha256(status + "|" + relative + "|unreadable");
        }
    }

    private static Instant parseInstant(String value, Instant fallback) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return first(fallback, Instant.EPOCH);
        }
    }

    private static Instant first(Instant... values) {
        for (Instant value : values) if (value != null) return value;
        return Instant.EPOCH;
    }

    private static String instant(Instant value) { return value == null ? "unknown" : value.toString(); }
    private static int integer(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException exception) { return 0; } }
    private static boolean isCommitSha(String value) { return value != null && value.matches("(?i)[0-9a-f]{40,64}"); }
    private static String shortSha(String value) { return value == null ? "" : value.substring(0, Math.min(8, value.length())); }

    private static <T> List<T> compact(List<T> values, int limit) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).distinct().limit(limit).toList();
    }

    private static List<String> list(String[] values, int limit) {
        return Stream.of(values).map(String::trim).filter(value -> !value.isBlank()).limit(limit).toList();
    }

    private static List<String> jsonStrings(JsonNode node, int limit) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(value -> { if (value.isTextual() && result.size() < limit) result.add(value.asText()); });
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    static String sha256(String value) {
        return sha256(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CollectedEvent(
        String stableEventKey,
        SourceType sourceType,
        String sourceIdentity,
        String sourceRevision,
        String projectRevision,
        Instant occurredAt,
        Instant effectiveAt,
        String actorLabel,
        Scope scope,
        Category category,
        Transition transition,
        String safeSourceLabel,
        List<String> affectedPaths,
        List<String> subjectKeys,
        List<String> evidenceRefs,
        List<String> relationRefs,
        Authority authority,
        ProjectFactEpistemicStatus epistemicStatus,
        Map<String, Object> coverage,
        List<String> limitations,
        String rawSourceDeepLink,
        String payloadHash
    ) {
    }

    public record CollectionOutcome(
        ProjectSpace project,
        Path projectRoot,
        String projectRevision,
        String sourceFingerprint,
        List<CollectedEvent> events,
        boolean complete,
        boolean sourceScanComplete,
        boolean gitAvailable,
        boolean shallowGitHistory,
        boolean gitCommitCountKnown,
        int totalGitCommits,
        int readGitCommits,
        Map<String, Integer> sourceCounts,
        List<String> limitations
    ) {
    }

    private record GitCollection(int commitCount) {
    }

    private record CommitDraft(
        String sha,
        List<String> parents,
        Instant occurredAt,
        String author,
        String subject,
        String decorations,
        List<FileChange> changes
    ) {
    }

    private record FileChange(String status, String first, String second) {
    }

    private static final class CollectionState {
        private boolean complete = true;
        private final Set<String> projectionRoots = new LinkedHashSet<>();
    }
}
