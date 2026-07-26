package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.projectflow.dto.ProjectUnderstandingDtos.AnalysisToolEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.service.ProjectEvidenceDiscoveryService.PromptEvidence;

@Component
public class BoundedLocalAnalysisCapabilityProvider implements AnalysisCapabilityProvider {
    private static final Set<String> SUPPORTED = Set.of(
        "DOC_READER", "GIT_HISTORY", "GIT_TAG", "WORKTREE", "MANIFEST", "AGENT_RESULT"
    );
    private static final Set<String> DOCUMENT_CATEGORIES = Set.of(
        "DOC", "README", "ADR", "PRODUCT_CONTEXT", "AGENT_CONTEXT", "CHANGELOG", "UNKNOWN_DOCUMENT"
    );
    private static final Pattern COMMIT_PERIOD = Pattern.compile("@@COMMIT@@(\\d{4}-\\d{2})\\|[0-9a-fA-F]+");

    private final LocalCommandExecutor commandExecutor;
    private final SensitiveContentRedactor redactor;

    public BoundedLocalAnalysisCapabilityProvider(
        LocalCommandExecutor commandExecutor,
        SensitiveContentRedactor redactor
    ) {
        this.commandExecutor = commandExecutor;
        this.redactor = redactor;
    }

    @Override
    public boolean supports(String capability) {
        return SUPPORTED.contains(normalized(capability));
    }

    @Override
    public CapabilityResult execute(CapabilityRequest request) {
        ModelCancellationContext.throwIfCancelled();
        return switch (normalized(request.capability())) {
            case "DOC_READER" -> deepRead(request, DOCUMENT_CATEGORIES);
            case "MANIFEST" -> deepRead(request, Set.of("MANIFEST", "BUILD"));
            case "AGENT_RESULT" -> deepRead(request, Set.of("AGENT_RESULT"));
            case "GIT_HISTORY" -> gitHistory(request);
            case "GIT_TAG" -> gitTags(request);
            case "WORKTREE" -> worktree(request);
            default -> new CapabilityResult("UNAVAILABLE", List.of(), List.of(), 0, 0, "未注册本地 Provider");
        };
    }

    private CapabilityResult deepRead(CapabilityRequest request, Set<String> categories) {
        Map<String, ProjectEvidenceSourceResponse> byId = new LinkedHashMap<>();
        request.sourceMap().sources().stream()
            .filter(source -> categories.contains(source.category()))
            .forEach(source -> byId.put(source.id(), source));
        List<ProjectEvidenceSourceResponse> targets = new ArrayList<>();
        if ("DOC_READER".equals(normalized(request.capability()))) {
            for (String evidenceId : request.plan().deepReadTargets()) {
                ProjectEvidenceSourceResponse source = byId.get(evidenceId);
                if (source != null && !targets.contains(source)) targets.add(source);
            }
        } else {
            byId.values().stream()
                .sorted(Comparator.comparing(ProjectEvidenceSourceResponse::importance).reversed())
                .forEach(targets::add);
        }

        List<AnalysisToolEvidenceResponse> evidence = new ArrayList<>();
        List<PromptEvidence> promptEvidence = new ArrayList<>();
        int consumed = 0;
        int selected = 0;
        for (ProjectEvidenceSourceResponse source : targets) {
            if (selected >= request.budget().maxItems() || consumed >= request.budget().maxTotalChars()) break;
            if (!request.allowedEvidenceIds().contains(source.id())) continue;
            String relative = safeRelative(source.locator());
            if (relative.isBlank() || redactor.isSensitivePath(relative)) continue;
            Path target = request.projectRoot().resolve(relative).normalize();
            if (!target.startsWith(request.projectRoot().normalize()) || !Files.isRegularFile(target)) continue;
            int remaining = Math.min(
                request.budget().maxCharsPerItem(),
                request.budget().maxTotalChars() - consumed
            );
            String content = readBoundedText(target, remaining);
            if (content.isBlank()) continue;
            String id = toolEvidenceId(request.capability(), source.id());
            String summary = boundedSummary(content, 800);
            evidence.add(new AnalysisToolEvidenceResponse(
                id,
                normalized(request.capability()),
                "TOOL_RESULT",
                "TARGETED_DEEP_READ",
                summary,
                List.of(source.id())
            ));
            promptEvidence.add(new PromptEvidence(
                id,
                "TOOL_RESULT",
                normalized(request.capability()) + "_RESULT",
                relative,
                summary,
                content
            ));
            consumed += content.length();
            selected++;
        }
        String status = evidence.isEmpty() ? "NO_EVIDENCE" : "SUCCEEDED";
        String message = evidence.isEmpty()
            ? "没有符合 allow-list、路径和内容边界的可深读来源"
            : "读取 " + evidence.size() + " 个有界来源，原始完整文档未持久化";
        return new CapabilityResult(
            status,
            List.copyOf(evidence),
            List.copyOf(promptEvidence),
            selected,
            consumed,
            message
        );
    }

    private CapabilityResult gitHistory(CapabilityRequest request) {
        String output = command(
            request,
            List.of(
                "git", "log", "--max-count=240", "--date=format:%Y-%m",
                "--format=@@COMMIT@@%ad|%h", "HEAD"
            )
        );
        if (output.isBlank()) return unavailable("Git 历史命令失败或没有输出");
        Map<String, Integer> periods = new LinkedHashMap<>();
        int commits = 0;
        for (String raw : output.lines().toList()) {
            String line = raw.strip();
            if (line.isBlank()) continue;
            var commitPeriod = COMMIT_PERIOD.matcher(line);
            if (commitPeriod.matches()) {
                periods.merge(commitPeriod.group(1), 1, Integer::sum);
                commits++;
            }
        }
        String summary = "有界读取最近 " + commits + " 次提交元数据；周期 "
            + joinTop(periods, 12) + "。未读取文件内容或 patch，未逐提交调用模型。";
        return gitEvidence(request, "GIT_HISTORY_METADATA", summary, commits);
    }

    private CapabilityResult gitTags(CapabilityRequest request) {
        String output = command(
            request,
            List.of(
                "git", "for-each-ref", "--count=200", "--sort=-creatordate",
                "--format=%(refname:short)|%(creatordate:iso-strict)|%(objecttype)", "refs/tags"
            )
        );
        if (output.isBlank()) return unavailable("没有 Tag，或 Tag 元数据命令失败");
        List<String> anchors = output.lines()
            .map(String::strip)
            .filter(line -> !line.isBlank())
            .map(redactor::redact)
            .limit(40)
            .toList();
        String summary = "读取 " + output.lines().filter(line -> !line.isBlank()).limit(200).count()
            + " 个本地 Tag 元数据锚点；样例 " + String.join("；", anchors.stream().limit(12).toList()) + "。";
        return gitEvidence(request, "GIT_TAG_METADATA", summary, anchors.size());
    }

    private CapabilityResult worktree(CapabilityRequest request) {
        String output = command(
            request,
            List.of("git", "status", "--porcelain=v1", "--untracked-files=normal")
        );
        if (output.isBlank()) {
            return gitEvidence(request, "WORKTREE_STATUS", "当前工作树没有 staged、unstaged 或 untracked 路径。", 0);
        }
        Map<String, Integer> states = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>();
        for (String raw : output.lines().limit(500).toList()) {
            if (raw.length() < 3) continue;
            String state = raw.substring(0, 2).strip();
            states.merge(state.isBlank() ? "UNKNOWN" : state, 1, Integer::sum);
            String relative = safeRelative(raw.substring(3).replace("\"", ""));
            if (!relative.isBlank() && !redactor.isSensitivePath(relative) && paths.size() < 40) {
                paths.add(relative);
            }
        }
        String summary = "工作树变化状态 " + joinTop(states, 12) + "；有界路径 "
            + String.join("、", paths.stream().limit(20).toList()) + "。未读取 diff，也未写入 ProjectFact。";
        return gitEvidence(request, "WORKTREE_STATUS", summary, paths.size());
    }

    private CapabilityResult gitEvidence(
        CapabilityRequest request,
        String sourceType,
        String rawSummary,
        int selectedItems
    ) {
        String summary = redactor.redact(rawSummary);
        String id = toolEvidenceId(request.capability(), request.intake().sourceRevision());
        AnalysisToolEvidenceResponse evidence = new AnalysisToolEvidenceResponse(
            id,
            normalized(request.capability()),
            "TOOL_RESULT",
            sourceType,
            summary,
            request.allowedEvidenceIds().contains("git:summary") ? List.of("git:summary") : List.of("intake:scan")
        );
        PromptEvidence prompt = new PromptEvidence(
            id,
            "TOOL_RESULT",
            sourceType,
            "",
            summary,
            summary
        );
        return new CapabilityResult(
            "SUCCEEDED",
            List.of(evidence),
            List.of(prompt),
            selectedItems,
            summary.length(),
            "固定参数 Git 元数据执行成功"
        );
    }

    private CapabilityResult unavailable(String message) {
        return new CapabilityResult("UNAVAILABLE", List.of(), List.of(), 0, 0, message);
    }

    private String command(CapabilityRequest request, List<String> command) {
        Duration timeout = Duration.ofMillis(Math.max(500, request.budget().timeoutMs()));
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(request.projectRoot(), command, timeout);
        if (result.timedOut() || result.exitCode() != 0) return "";
        String output = redactor.redact(result.output());
        return output.length() <= request.budget().maxTotalChars()
            ? output
            : output.substring(0, request.budget().maxTotalChars());
    }

    private String readBoundedText(Path target, int limit) {
        if (limit <= 0) return "";
        try (var input = Files.newInputStream(target)) {
            byte[] bytes = input.readNBytes(Math.max(256, limit * 2));
            if (containsNul(bytes)) return "";
            String text = new String(bytes, StandardCharsets.UTF_8);
            String redacted = redactor.redact(text);
            return redacted.length() <= limit ? redacted : redacted.substring(0, limit);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return true;
        return false;
    }

    private static String boundedSummary(String value, int limit) {
        String summary = value.lines()
            .map(String::strip)
            .filter(line -> !line.isBlank())
            .limit(12)
            .reduce((left, right) -> left + " " + right)
            .orElse("");
        return summary.length() <= limit ? summary : summary.substring(0, limit);
    }

    private static String safeRelative(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("../")) return "";
        return normalized;
    }

    private static String joinTop(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .limit(limit)
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + "、" + right)
            .orElse("无");
    }

    private static String normalized(String capability) {
        return capability == null ? "" : capability.strip().toUpperCase(Locale.ROOT);
    }

    private static String toolEvidenceId(String capability, String source) {
        try {
            String input = normalized(capability) + ":" + source;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return "tool:" + normalized(capability).toLowerCase(Locale.ROOT) + ":"
                + HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }
}
