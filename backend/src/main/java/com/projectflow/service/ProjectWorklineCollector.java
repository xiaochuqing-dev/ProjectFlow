package com.projectflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Replaceable branch observations collected only inside the existing explicit History job. */
public final class ProjectWorklineCollector {
    static final int BRANCH_LIMIT = 300;
    static final int DETAIL_LIMIT = 60;
    private static final Pattern REPO = Pattern.compile("(?:https://github\\.com/|git@github\\.com:)([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?/?");
    private static final Pattern SHA = Pattern.compile("[a-fA-F0-9]{40,64}");
    private final LocalCommandExecutor commands;
    private final SensitiveContentRedactor redactor;
    private final ObjectMapper mapper;

    public ProjectWorklineCollector(LocalCommandExecutor commands, SensitiveContentRedactor redactor, ObjectMapper mapper) {
        this.commands = commands; this.redactor = redactor; this.mapper = mapper;
    }

    public record Source(String kind, String label, String reference, String observedAt) {}
    public record PullRequest(int number, String title, String excerpt, String state, boolean draft,
                              String base, String head, String headSha, String updatedAt, String mergedAt,
                              String url, List<String> issues, String checks) {}
    public record Workline(String id, String branch, String head, String purpose, String classification,
                           String state, String mergeState, String mergeBasis, String lastActivity,
                           String firstSampleActivity, Integer ahead, Integer behind, String mergeBase,
                           String dependsOn, boolean localAvailable, boolean remoteHeadDiffers,
                           List<String> changedFiles, List<String> commitSubjects, List<String> authors,
                           PullRequest pullRequest, List<Source> sources, List<String> limitations) {}
    public record Snapshot(String version, String observedAt, String githubObservedAt, String githubStatus,
                           String defaultBranch, String defaultBranchSource, int branchCount, int detailedCount,
                           boolean truncated, List<Workline> items, List<String> limitations) {
        public static Snapshot empty() {
            return new Snapshot("v1", "", "", "NOT_READ", "", "UNKNOWN", 0, 0, false, List.of(),
                List.of("尚未读取开发工作线；请更新项目状态。"));
        }
    }
    public record Page(String observedAt, String githubObservedAt, String githubStatus, String defaultBranch,
                       int branchCount, boolean truncated, boolean stale, Map<String, Long> groups,
                       List<Workline> items, int page, int totalPages, int totalElements, List<String> limitations) {}
    private record Ref(String name, String sha, String at, boolean local) {}

    public Snapshot collect(Path root, String configuredRepo) {
        if (root == null) return Snapshot.empty();
        Session s = new Session(root);
        String now = Instant.now().toString();
        LinkedHashMap<String, Ref> refs = new LinkedHashMap<>();
        // Full ref names are data. Only validated hashes enter revision commands below.
        String output = s.run("git", "for-each-ref", "--count=601", "--sort=-committerdate",
            "--format=%(refname)%09%(objectname)%09%(committerdate:iso-strict)", "refs/heads", "refs/remotes/origin");
        for (String line : output.split("\n")) {
            String[] fields = line.trim().split("\t", -1);
            if (fields.length != 3 || !SHA.matcher(fields[1]).matches()) continue;
            boolean local = fields[0].startsWith("refs/heads/");
            String name = fields[0].replaceFirst("^refs/(?:heads/|remotes/origin/)", "");
            if (name.equals("HEAD") || name.length() > 500) continue;
            Ref ref = new Ref(name, fields[1], fields[2], local);
            if (!refs.containsKey(name) || local) refs.put(name, ref);
        }
        boolean truncated = refs.size() > BRANCH_LIMIT || output.length() >= 99_000;
        List<String> limits = new ArrayList<>();
        String defaultBranch = s.run("git", "symbolic-ref", "--quiet", "refs/remotes/origin/HEAD")
            .trim().replaceFirst("^refs/remotes/origin/", "");
        String defaultSource = defaultBranch.isBlank() ? "UNKNOWN" : "LOCAL_REMOTE_HEAD";
        String repo = repository(configuredRepo);
        if (repo.isBlank()) repo = repository(s.run("git", "remote", "get-url", "origin").trim());
        List<PullRequest> prs = new ArrayList<>();
        Map<String, String> remoteHeads = new HashMap<>();
        String githubAt = "", githubStatus = repo.isBlank() ? "NO_REMOTE" : "UNAVAILABLE";
        if (!repo.isBlank()) {
            JsonNode repoInfo = s.json("gh", "api", "repos/" + repo);
            if (repoInfo.hasNonNull("default_branch")) {
                defaultBranch = repoInfo.path("default_branch").asText(); defaultSource = "GITHUB";
                githubStatus = "AVAILABLE"; githubAt = now;
                for (int page = 1; page <= 3; page++) {
                    JsonNode branches = s.json("gh", "api", "repos/" + repo + "/branches?per_page=100&page=" + page);
                    if (!branches.isArray()) { githubStatus = "PARTIAL"; limits.add("GitHub 分支目录未完整读取。"); break; }
                    for (JsonNode branch : branches) {
                        String name = branch.path("name").asText(), sha = branch.path("commit").path("sha").asText();
                        if (name.length() > 500 || !SHA.matcher(sha).matches()) continue;
                        remoteHeads.put(name, sha);
                        refs.putIfAbsent(name, new Ref(name, sha, "", false));
                    }
                    if (branches.size() < 100) break;
                    if (page == 3) truncated = true;
                }
                JsonNode pullRequests = s.json("gh", "pr", "list", "--repo", repo, "--state", "all", "--limit", "100",
                    "--json", "number,title,state,isDraft,baseRefName,headRefName,headRefOid,updatedAt,mergedAt");
                if (!pullRequests.isArray()) { githubStatus = "PARTIAL"; limits.add("GitHub PR 未读取；不能据此判断不存在 PR。"); }
                else {
                    int enriched = 0;
                    for (JsonNode pr : pullRequests) {
                        if ("OPEN".equals(pr.path("state").asText()) && enriched++ < 8) {
                            JsonNode extra = s.json("gh", "pr", "view", String.valueOf(pr.path("number").asInt()), "--repo", repo,
                                "--json", "body,closingIssuesReferences,statusCheckRollup");
                            if (pr.isObject() && extra.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) pr).setAll((com.fasterxml.jackson.databind.node.ObjectNode) extra);
                        }
                        prs.add(pr(pr, repo));
                    }
                    if (pullRequests.size() == 100) limits.add("仅读取最近 100 个 PR；未找到关联不代表不存在历史 PR。");
                }
            } else limits.add("GitHub 当前不可用，保留本地 Git 观察；PR 合并与审查状态未知。");
        }
        if (defaultBranch.isBlank()) {
            limits.add("无法确认默认分支；没有把当前检出分支猜作主线。");
        }
        final String main = defaultBranch;
        final List<PullRequest> allPrs = prs;
        List<Ref> ordered = refs.values().stream().sorted(Comparator
            .comparingInt((Ref ref) -> ref.name().equals(main) ? 0 : associated(allPrs, ref.name(), ref.sha()) != null ? 1 : 2)
            .thenComparing(Ref::at, Comparator.reverseOrder()).thenComparing(Ref::name)).limit(BRANCH_LIMIT).toList();
        truncated |= refs.size() > BRANCH_LIMIT;
        Ref mainRef = refs.get(main);
        String mainSha = remoteHeads.getOrDefault(main, mainRef == null ? "" : mainRef.sha());
        List<Workline> worklines = new ArrayList<>();
        int detailed = 0;
        for (Ref ref : ordered) {
            ModelCancellationContext.throwIfCancelled();
            boolean available = !s.run("git", "cat-file", "-t", ref.sha()).trim().isEmpty();
            boolean detail = available && detailed < DETAIL_LIMIT && s.remaining();
            if (detail) detailed++;
            List<String> itemLimits = new ArrayList<>();
            List<String> subjects = new ArrayList<>(), authors = new ArrayList<>(), dates = new ArrayList<>(), files = new ArrayList<>();
            String mergeBase = ""; Integer ahead = null, behind = null;
            boolean comparable = detail && SHA.matcher(mainSha).matches();
            if (comparable) {
                String base = s.run("git", "merge-base", mainSha, ref.sha()).trim();
                if (SHA.matcher(base).matches()) {
                    mergeBase = base;
                    String[] counts = s.run("git", "rev-list", "--left-right", "--count", mainSha + "..." + ref.sha()).trim().split("\\s+");
                    if (counts.length == 2 && counts[0].matches("\\d+") && counts[1].matches("\\d+")) {
                        behind = Integer.parseInt(counts[0]); ahead = Integer.parseInt(counts[1]);
                    }
                    String changed = s.run("git", "-c", "core.quotepath=false", "diff", "--no-ext-diff", "--no-textconv", "--name-only", base, ref.sha(), "--");
                    files = changed.lines().filter(value -> !value.isBlank()).limit(40).map(value -> safe(value, 220)).toList();
                    if (changed.lines().count() > 40) itemLimits.add("仅列出前 40 个变更文件。");
                } else itemLimits.add("无法确定共同祖先；可能缺少本地对象或存在重写历史。");
            }
            String last = ref.at();
            if (detail) {
                String range = !mergeBase.isBlank() && !ref.name().equals(main) && !mergeBase.equals(ref.sha())
                    ? mergeBase + ".." + ref.sha() : ref.sha();
                for (String line : s.run("git", "log", "-8", "--format=%cI%x09%an%x09%s", range, "--").split("\n")) {
                    String[] f = line.trim().split("\t", 3);
                    if (f.length == 3) { dates.add(f[0]); authors.add(safe(f[1], 80)); subjects.add(safe(f[2], 240)); }
                }
                if (last.isBlank() && !dates.isEmpty()) last = dates.get(0);
            } else itemLimits.add("未深读此分支；受本地对象、60 条详细读取或总时限约束。");
            PullRequest pr = associated(prs, ref.name(), ref.sha());
            boolean merged = pr != null && "MERGED".equals(pr.state()) && ref.sha().equals(pr.headSha());
            boolean open = pr != null && "OPEN".equals(pr.state());
            String mergeState = merged ? "MERGED" : ahead != null && ahead == 0 ? "CONTAINED"
                : open ? "OPEN_PR" : "UNKNOWN";
            String mergeBasis = merged ? "GITHUB_PR_MERGED_HEAD" : "CONTAINED".equals(mergeState) ? "GIT_ANCESTRY" : open ? "GITHUB_OPEN_PR" : "UNKNOWN";
            if (!merged && ahead != null && ahead > 0 && !open) itemLimits.add("Git 提交不同不证明工作未合入；squash、rebase 或 cherry-pick 状态尚不确定。");
            if (pr != null && "MERGED".equals(pr.state()) && !merged) itemLimits.add("关联 PR 已合并，但分支 HEAD 不同；后续工作是否合入未知。");
            String dependency = open && !pr.base().equals(main) ? safe(pr.base(), 500) : "";
            String activity = pr != null && pr.updatedAt().compareTo(last) > 0 ? pr.updatedAt() : last;
            boolean stale = isOld(activity);
            String state = ref.name().equals(main) ? "MAIN" : merged || "CONTAINED".equals(mergeState) ? "HISTORY"
                : !dependency.isBlank() ? "DEPENDENT" : open ? pr.draft() ? "DEVELOPING" : "REVIEW"
                : stale ? "INACTIVE" : "UNKNOWN";
            String classification = pr != null ? "DECLARED" : !files.isEmpty() || !subjects.isEmpty() ? "INFERRED" : "UNKNOWN";
            String purpose = pr != null ? pr.title() : !files.isEmpty() ? "已观察到变更涉及 " + areas(files)
                : !subjects.isEmpty() ? "已读取最近 " + subjects.size() + " 条提交，用途暂无明确声明" : "用途暂无明确记录";
            List<Source> sources = new ArrayList<>();
            if (available) sources.add(new Source("LOCAL_GIT", "本地 Git HEAD 与提交样本", ref.sha(), now));
            if (!files.isEmpty()) sources.add(new Source("LOCAL_DIFF", "相对共同祖先的文件变化", mergeBase + ".." + ref.sha(), now));
            if (remoteHeads.containsKey(ref.name())) sources.add(new Source("GITHUB_BRANCH", "GitHub 分支 HEAD", remoteHeads.get(ref.name()), githubAt));
            if (pr != null) sources.add(new Source("GITHUB_PR", "PR #" + pr.number() + " 中的声明及状态", pr.url(), githubAt));
            worklines.add(new Workline(ProjectHistorySourceCollector.sha256(ref.name()).substring(0, 20), safe(ref.name(), 500),
                ref.sha(), purpose, classification, state, mergeState, mergeBasis, activity,
                dates.isEmpty() ? "" : dates.get(dates.size() - 1), ahead, behind, mergeBase, dependency, available,
                remoteHeads.containsKey(ref.name()) && !remoteHeads.get(ref.name()).equals(ref.sha()),
                files, subjects, authors.stream().distinct().toList(), pr, sources, itemLimits));
        }
        if (truncated) limits.add("分支目录达到 300 条安全上限；超出部分未展示。");
        if (s.expired) limits.add("工作线读取达到 60 秒总时限；未读取项保留未知。");
        limits.add("活动时间取本地提交或 PR 更新；提交样本首日不是分支创建日期。没有关联 PR 不代表未合并。");
        return new Snapshot("v1", now, githubAt, githubStatus, safe(main, 500), defaultSource,
            refs.size(), detailed, truncated, List.copyOf(worklines), limits);
    }

    static PullRequest associated(List<PullRequest> prs, String branch, String sha) {
        return prs.stream().filter(pr -> branch.equals(pr.head())).sorted(Comparator
            .comparingInt((PullRequest pr) -> "OPEN".equals(pr.state()) ? 0 : sha.equals(pr.headSha()) ? 1 : 2)
            .thenComparing(PullRequest::updatedAt, Comparator.reverseOrder())).findFirst().orElse(null);
    }

    private PullRequest pr(JsonNode pr, String repo) {
        int number = pr.path("number").asInt();
        List<String> issues = new ArrayList<>();
        for (JsonNode issue : pr.path("closingIssuesReferences")) {
            if (issues.size() == 10) break;
            issues.add("#" + issue.path("number").asInt() + " " + safe(issue.path("title").asText(), 120));
        }
        Set<String> checks = new LinkedHashSet<>();
        for (JsonNode check : pr.path("statusCheckRollup")) {
            String value = check.path("conclusion").asText(check.path("state").asText());
            if (value.isBlank()) value = check.path("status").asText("UNKNOWN");
            checks.add(value);
        }
        return new PullRequest(number, safe(pr.path("title").asText(), 300), safe(pr.path("body").asText(), 500),
            pr.path("state").asText(), pr.path("isDraft").asBoolean(), safe(pr.path("baseRefName").asText(), 500),
            safe(pr.path("headRefName").asText(), 500), pr.path("headRefOid").asText(), pr.path("updatedAt").asText(),
            pr.path("mergedAt").asText(), "https://github.com/" + repo + "/pull/" + number, issues, String.join(", ", checks));
    }
    private String safe(String value, int limit) {
        String text = redactor.redactOutboundText(value).replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }
    private static String repository(String value) {
        var match = REPO.matcher(value == null ? "" : value.trim());
        return match.matches() ? match.group(1) : "";
    }
    private static String areas(List<String> files) {
        return files.stream().map(file -> file.contains("/") ? file.substring(0, file.indexOf('/')) : file)
            .distinct().limit(4).reduce((a, b) -> a + "、" + b).orElse("已读取文件");
    }
    private static boolean isOld(String at) {
        try { return Instant.parse(at).isBefore(Instant.now().minus(Duration.ofDays(90))); }
        catch (Exception ignored) { return false; }
    }
    private final class Session {
        final Path root; final long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos(); boolean expired;
        Session(Path root) { this.root = root; }
        boolean remaining() { return System.nanoTime() < deadline; }
        String run(String... args) {
            ModelCancellationContext.throwIfCancelled();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) { expired = true; return ""; }
            var result = commands.execute(root, List.of(args), Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(5).toNanos())));
            ModelCancellationContext.throwIfCancelled();
            return result != null && result.exitCode() == 0 && !result.timedOut() ? result.output() : "";
        }
        JsonNode json(String... args) {
            try { JsonNode value = mapper.readTree(run(args)); return value == null ? mapper.createObjectNode() : value; }
            catch (Exception ignored) { return mapper.createObjectNode(); }
        }
    }
}
