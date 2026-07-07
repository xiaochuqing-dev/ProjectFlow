package com.projectflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.service.DevelopmentSegmentationService.ChangeAtom;
import com.projectflow.service.DevelopmentSegmentationService.SegmentDraft;
import com.projectflow.service.SegmentQualityGate.QualityResult;

/**
 * V3.3.3: 模型结果保留优先。质量门槛改为"标记器"，不再把整批模型结果替换成本地摘要。
 *
 * 保留策略：
 * - 模型返回可解析的结构化结果 → 即使有质量问题也保留，按 segment 打状态（需复核/需中文修正/需补证据）。
 * - 只有以下情况才回退本地规则：模型未配置、调用失败、完全未返回、返回内容不是可解析 JSON、
 *   引用的证据完全不可用导致无任何可用 segment。
 */
@Service
public class ModelSegmentEnricher {
    private static final int MAX_PROMPT_ATOMS = 80;
    private static final int MAX_OUTPUT_TOKENS = 8_000;

    private final AiProviderRepository providerRepository;
    private final ModelGatewayService modelGatewayService;
    private final SegmentEvidenceValidator evidenceValidator;
    private final SegmentQualityGate qualityGate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator,
        SegmentQualityGate qualityGate,
        ObjectMapper objectMapper
    ) {
        this.providerRepository = providerRepository;
        this.modelGatewayService = modelGatewayService;
        this.evidenceValidator = evidenceValidator;
        this.qualityGate = qualityGate;
        this.objectMapper = objectMapper;
    }

    // 兼容旧测试构造器：不传 ObjectMapper 时仍可工作（quality gate 标记器模式）。
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator
    ) {
        this(providerRepository, modelGatewayService, evidenceValidator, new SegmentQualityGate(), new ObjectMapper());
    }

    // 兼容旧测试构造器。
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator,
        SegmentQualityGate qualityGate
    ) {
        this(providerRepository, modelGatewayService, evidenceValidator, qualityGate, new ObjectMapper());
    }

    public List<SegmentDraft> enrich(
        UUID userId,
        List<ChangeAtom> atoms,
        List<SegmentDraft> fallback,
        List<String> warnings
    ) {
        EnrichmentResult result = enrichWithDiagnostics(userId, atoms, fallback);
        if (!result.fallbackReason().isBlank()) {
            warnings.add(result.fallbackReason());
        }
        return result.segments();
    }

    public EnrichmentResult enrichWithDiagnostics(UUID userId, List<ChangeAtom> atoms, List<SegmentDraft> fallback) {
        return enrichWithDiagnostics(userId, atoms, fallback, null);
    }

    // V3.3.3: 接收分析输入快照，把多来源证据整理进 prompt。
    public EnrichmentResult enrichWithDiagnostics(UUID userId, List<ChangeAtom> atoms, List<SegmentDraft> fallback, AnalysisInputSnapshot snapshot) {
        AiProvider provider = configuredProvider(userId);
        if (provider == null) {
            return new EnrichmentResult(fallback, "LOCAL_RULE", "NOT_CONFIGURED", "", "未配置可用模型，已使用增强本地摘要。", List.of(), "");
        }
        if (atoms.isEmpty()) {
            return new EnrichmentResult(fallback, "LOCAL_RULE", "NO_CHANGES", provider.getName(), "没有可供模型分析的新变化。", List.of(), "");
        }
        Exception lastFailure = null;
        boolean jsonParseFailed = false;
        boolean evidenceRejected = false;
        try {
            JsonNode json = modelGatewayService.callJson(provider, prompt(atoms, fallback, false, snapshot), MAX_OUTPUT_TOKENS);
            List<SegmentDraft> validated = parse(json, atoms);
            // V3.3.3: 质量门槛改为标记器。不再因质量问题丢弃整批模型结果。
            // 每个 segment 单独评估，保留可用内容，标记状态。
            List<SegmentDraft> retained = new ArrayList<>();
            List<String> qualityWarnings = new ArrayList<>();
            List<String> titles = new ArrayList<>();
            for (SegmentDraft candidate : validated) {
                QualityResult quality = qualityGate.evaluate(candidate, titles);
                titles.add(candidate.title());
                if (qualityGate.needsReviewFlag(quality) && !quality.status().equals("NEEDS_EVIDENCE")) {
                    qualityWarnings.add(quality.reason());
                }
                retained.add(candidate);
            }
            if (retained.isEmpty()) {
                evidenceRejected = true;
                throw new IllegalArgumentException("no model segments survived evidence validation");
            }
            String summary = buildModelSummary(snapshot, retained.size());
            String reason = qualityWarnings.isEmpty() ? "" : "模型已返回结果，其中 " + qualityWarnings.size() + " 条需人工复核。";
            return new EnrichmentResult(retained, "MODEL", "SUCCESS", provider.getName(), reason, qualityWarnings, summary);
        } catch (IllegalArgumentException exception) {
            lastFailure = exception;
            String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
            if (message.contains("segments must be an array") || message.contains("is required") || message.contains("must be an array")) {
                jsonParseFailed = true;
            }
        } catch (Exception exception) {
            lastFailure = exception;
        }
        // V3.3.3: 只有模型完全不可用才回退本地规则。
        String status = jsonParseFailed ? "JSON_PARSE_FAILED"
            : evidenceRejected ? "EVIDENCE_REJECTED"
            : modelFailureStatus(lastFailure);
        String reason = jsonParseFailed ? "模型返回内容无法解析为结构化结果，已使用增强本地摘要。"
            : evidenceRejected ? "模型引用的证据完全不可用，已使用增强本地摘要。"
            : "模型归并失败，已使用增强本地摘要。";
        return new EnrichmentResult(fallback, "LOCAL_RULE", status, provider.getName(), reason, List.of(), "");
    }

    private String buildModelSummary(AnalysisInputSnapshot snapshot, int segmentCount) {
        if (snapshot == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("模型基于多来源证据归并 ").append(segmentCount).append(" 段。");
        var scope = snapshot.scanScope();
        if (scope != null) {
            sb.append("输入：").append(scope.inputCommitCount()).append(" 提交 / ")
              .append(scope.inputFileCount()).append(" 文件 / ").append(scope.inputAgentResultCount()).append(" Agent result。");
            if (scope.includesUncommitted()) sb.append("含未提交变化。");
            if (scope.githubParticipated()) sb.append("GitHub 参与。"); else sb.append("GitHub 未参与。");
        }
        return sb.toString();
    }

    private List<SegmentDraft> parse(JsonNode json, List<ChangeAtom> atoms) {
            JsonNode segments = json.path("segments");
            if (!segments.isArray()) {
                throw new IllegalArgumentException("segments must be an array");
            }
            List<SegmentDraft> validated = new ArrayList<>();
            for (JsonNode item : segments) {
                if (!item.path("needsUserReview").asBoolean(false)) {
                    throw new IllegalArgumentException("model segments must require user review");
                }
                SegmentDraft candidate = new SegmentDraft(
                    requiredText(item, "segmentTitle"),
                    requiredText(item, "plainSummary"),
                    textArray(item, "includedAtomIds"),
                    textArray(item, "mainChanges"),
                    requiredText(item, "userVisibleValue"),
                    textArray(item, "evidenceRefs"),
                    textArray(item, "affectedFiles"),
                    confidence(item.path("confidence").asText())
                );
                evidenceValidator.validate(candidate, atoms).ifPresent(validated::add);
            }
            if (validated.isEmpty() || validated.size() > 8) {
                throw new IllegalArgumentException("model segments did not pass evidence validation");
            }
            return validated;
    }

    private AiProvider configuredProvider(UUID userId) {
        return providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .findFirst()
            .orElse(null);
    }

    // V3.3.3: 把多来源证据整理进 prompt，明确告诉模型"你在基于多来源证据判断真实开发状态"。
    private String prompt(List<ChangeAtom> atoms, List<SegmentDraft> fallback, boolean retry, AnalysisInputSnapshot snapshot) {
        StringBuilder facts = new StringBuilder();
        // 证据快照说明：让模型理解当前开发状态的多来源事实。
        if (snapshot != null) {
            facts.append("【分析输入快照】\n");
            appendGitFacts(facts, snapshot.git());
            appendWorktreeFacts(facts, snapshot.worktree());
            appendGitHubFacts(facts, snapshot.github());
            appendAgentResultFacts(facts, snapshot.agentResults());
            facts.append("【扫描范围】");
            var scope = snapshot.scanScope();
            if (scope != null) {
                facts.append("输入 ").append(scope.inputCommitCount()).append(" 提交 / ")
                    .append(scope.inputFileCount()).append(" 文件 / ").append(scope.inputAgentResultCount())
                    .append(" Agent result；");
                facts.append(scope.includesUncommitted() ? "含未提交变化；" : "无未提交变化；");
                facts.append(scope.githubParticipated() ? "GitHub 参与；" : "GitHub 未参与；");
                facts.append(scope.modelParticipated() ? "模型参与。" : "模型未参与。");
                if (scope.evidenceGap()) facts.append(" 存在证据缺口。");
            }
            facts.append("\n\n");
        }
        // 原子事实。
        if (atoms.size() <= MAX_PROMPT_ATOMS) {
            for (ChangeAtom atom : atoms) {
                facts.append("ATOM ").append(atom.id()).append(" | ").append(atom.title()).append(" | files=")
                    .append(String.join(",", atom.files())).append(" | evidence=")
                    .append(String.join(",", atom.evidenceRefs())).append(" | source=").append(atom.sourceType())
                    .append(" | diffHints=").append(String.join(";", atom.diffHints())).append('\n');
            }
        } else {
            for (SegmentDraft segment : fallback) {
                facts.append("RULE_GROUP ").append(segment.title()).append(" | atoms=")
                    .append(String.join(",", segment.includedAtomIds())).append(" | evidence=")
                    .append(String.join(",", segment.evidenceRefs())).append('\n');
            }
        }
        return """
            你是 ProjectFlow V3.3.3 的开发推进段归并器。你不是在 GitHub 和本地 Git 之间二选一，而是在基于多来源证据判断当前真实开发状态。
            只依据给定事实返回严格 JSON：
            {"segments":[{"segmentTitle":"","plainSummary":"","includedAtomIds":[],"mainChanges":[],"userVisibleValue":"","evidenceRefs":[],"affectedFiles":[],"confidence":"HIGH|MEDIUM|LOW","needsUserReview":true}]}
            不能发明 atom、commit 或文件；文档和测试若服务于同一功能，应与功能归为一段；最多返回 8 段。
            标题和摘要必须描述实际开发结果，禁止目录名加"开发推进"或仅报告数量。mainChanges 必须为 3 到 6 条具体变化。
            needsUserReview 必须为 true，模型不能替用户确认项目事实。
            用户可见主内容（segmentTitle、plainSummary、mainChanges、userVisibleValue）必须使用简体中文人话；英文 commit message、文件路径、类名、接口名只能出现在证据细节里，不能成为主标题或摘要。
            如果变化未提交，请在摘要中说明"未提交工作区变化"；如果本地领先远程，请提示未推送；如果远程领先，请提示先同步本地；如果分叉，请标记当前分析可能不完整。
            如果证据不完整，要说不完整；不要假设 GitHub 一定最新，也不要假设本地 commit 一定完整。

            %s

            事实：
            """.formatted(retry ? "上一次输出未通过质量门槛，请改写为具体结果、简体中文人话，并避免重复标题。" : "") + facts;
    }

    private void appendGitFacts(StringBuilder sb, AnalysisInputSnapshot.GitFacts git) {
        if (git == null) return;
        sb.append("【本地 Git】分支 ").append(safe(git.branch())).append("；HEAD ").append(shortHash(git.headCommit()));
        sb.append(git.firstScan() ? "；首次扫描。" : "；从确认点读取。");
        sb.append(" 提交数：").append(git.commitCount()).append("。");
        if (!git.commitMessages().isEmpty()) {
            sb.append(" 提交线索：");
            sb.append(String.join("；", git.commitMessages().stream().limit(6).toList()));
            sb.append("。");
        }
        sb.append("\n");
    }

    private void appendWorktreeFacts(StringBuilder sb, AnalysisInputSnapshot.WorktreeFacts worktree) {
        if (worktree == null) return;
        sb.append("【工作区】");
        sb.append(worktree.worktreeDirty() ? "存在未提交变化" : "无未提交变化");
        sb.append("；unstaged=").append(worktree.hasUnstaged() ? "有" : "无");
        sb.append("，staged=").append(worktree.hasStaged() ? "有" : "无");
        sb.append("，untracked=").append(worktree.hasUntracked() ? "有" : "无");
        sb.append("。");
        if (worktree.possiblyUnfinished()) sb.append(" 可能是未完成开发，请在摘要中标注。");
        sb.append("\n");
    }

    private void appendGitHubFacts(StringBuilder sb, AnalysisInputSnapshot.GitHubFacts github) {
        if (github == null) return;
        sb.append("【GitHub】");
        if (!github.installed()) {
            sb.append("未安装 GitHub CLI。");
        } else if (!github.authenticated()) {
            sb.append("已安装但未登录。");
        } else if (!github.detected()) {
            sb.append("已登录但无 remote。");
        } else {
            sb.append("已接入 ").append(safe(github.repo())).append("；");
            sb.append("upstream=").append(safe(github.upstream())).append("；");
            sb.append("localAhead=").append(github.localAhead()).append("，remoteAhead=").append(github.remoteAhead());
            sb.append("，relation=").append(safe(github.relation())).append("。");
        }
        if (!github.githubParticipated()) sb.append(" 本次 GitHub 未参与分析。");
        sb.append("\n");
    }

    private void appendAgentResultFacts(StringBuilder sb, AnalysisInputSnapshot.AgentResultFacts agent) {
        if (agent == null) return;
        sb.append("【Agent result】读取 ").append(agent.count()).append(" 条。");
        if (agent.count() > 0) {
            if (agent.onlyAgentResultsWithoutCode()) sb.append(" 只有 Agent result 缺少代码变化，请标注证据缺口。");
            if (agent.overlapsWithGitDiff()) sb.append(" 与 Git diff 指向同一批文件，应合并分析。");
            if (!agent.taskGoals().isEmpty()) {
                sb.append(" 任务线索：").append(String.join("；", agent.taskGoals().stream().limit(4).toList())).append("。");
            }
        }
        sb.append("\n");
    }

    public String configurationKey(UUID userId) {
        AiProvider provider = configuredProvider(userId);
        return provider == null ? "none" : provider.getId() + ":" + provider.getModelName();
    }

    private String modelFailureStatus(Exception failure) {
        if (failure == null) return "CALL_FAILED";
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        if (message.contains("json") || message.contains("array")) return "JSON_PARSE_FAILED";
        if (message.contains("evidence")) return "EVIDENCE_REJECTED";
        return "CALL_FAILED";
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private List<String> textArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        });
        return values;
    }

    private EvidenceConfidence confidence(String value) {
        try {
            return EvidenceConfidence.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return EvidenceConfidence.LOW;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String shortHash(String hash) { return hash == null || hash.length() <= 12 ? safe(hash) : hash.substring(0, 12); }

    public record EnrichmentResult(
        List<SegmentDraft> segments,
        String mode,
        String modelStatus,
        String providerName,
        String fallbackReason,
        List<String> qualityWarnings,
        String modelSummary
    ) {
        // 兼容旧构造：无 qualityWarnings/modelSummary。
        public EnrichmentResult(List<SegmentDraft> segments, String mode, String modelStatus, String providerName, String fallbackReason) {
            this(segments, mode, modelStatus, providerName, fallbackReason, List.of(), "");
        }
    }
}
