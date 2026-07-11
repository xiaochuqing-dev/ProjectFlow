package com.projectflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * - 模型返回可解析的结构化结果 -> 即使有质量问题也保留，按 segment 打状态（需复核/需中文修正/需补证据）。
 * - 只有以下情况才回退本地规则：模型未配置、调用失败、完全未返回、返回内容不是可解析 JSON、
 *   引用的证据完全不可用导致无任何可用 segment。
 *
 * V3.3.4: 失败提示人话化。不再笼统说"模型归并失败，已使用增强本地摘要"。
 * 按原因拆分：未配置 / 调用失败 / 返回格式无效 / 证据引用无效，统一表述为"本地事实摘要"。
 */
@Service
public class ModelSegmentEnricher {
    private static final int MAX_PROMPT_ATOMS = 80;
    // V3.3.4 小阶段修复：prompt 体积防护。超过此字符数（约 15K tokens）时截断 atom 列表。
    private static final int PROMPT_CHAR_BUDGET = 45_000;
    // prompt 里每个 atom 最多展示的文件路径数，避免单个大 commit 撑爆 prompt。
    private static final int PROMPT_FILES_PER_ATOM = 15;

    private final AiProviderRepository providerRepository;
    private final ModelGatewayService modelGatewayService;
    private final SegmentEvidenceValidator evidenceValidator;
    private final SegmentQualityGate qualityGate;
    private final ObjectMapper objectMapper;
    private final ModelOutputAdapter outputAdapter;

    @Autowired
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator,
        SegmentQualityGate qualityGate,
        ObjectMapper objectMapper,
        ModelOutputAdapter outputAdapter
    ) {
        this.providerRepository = providerRepository;
        this.modelGatewayService = modelGatewayService;
        this.evidenceValidator = evidenceValidator;
        this.qualityGate = qualityGate;
        this.objectMapper = objectMapper;
        this.outputAdapter = outputAdapter;
    }

    // 兼容旧测试构造器：不传 ObjectMapper 时仍可工作（quality gate 标记器模式）。
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator
    ) {
        this(providerRepository, modelGatewayService, evidenceValidator, new SegmentQualityGate(), new ObjectMapper(), new ModelOutputAdapter(new ObjectMapper()));
    }

    // 兼容旧测试构造器。
    public ModelSegmentEnricher(
        AiProviderRepository providerRepository,
        ModelGatewayService modelGatewayService,
        SegmentEvidenceValidator evidenceValidator,
        SegmentQualityGate qualityGate
    ) {
        this(providerRepository, modelGatewayService, evidenceValidator, qualityGate, new ObjectMapper(), new ModelOutputAdapter(new ObjectMapper()));
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
            // V3.3.4: 模型未配置 -> 明确告知用户原因，不再笼统说"增强本地摘要"。
            return new EnrichmentResult(fallback, "LOCAL_RULE", "NOT_CONFIGURED", "", "当前未配置模型，本次仅读取本地 Git 与 Agent result 事实，展示本地事实摘要。", List.of(), "");
        }
        if (atoms.isEmpty()) {
            return new EnrichmentResult(fallback, "LOCAL_RULE", "NO_CHANGES", provider.getName(), "没有可供模型分析的新变化。", List.of(), "");
        }
        Exception lastFailure = null;
        boolean schemaUnrecognized = false;
        boolean evidenceRejected = false;
        ModelGatewayService.ModelCallDiagnostics modelDiagnostics = null;
        try {
            ModelGatewayService.StructuredModelResponse response = modelGatewayService.callStructured(
                provider, prompt(atoms, fallback, false, snapshot), ModelTaskType.DEVELOPMENT_SEGMENT_MERGE
            );
            modelDiagnostics = response.diagnostics();
            List<SegmentDraft> validated = parse(response.parsed().root(), atoms);
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
            List<String> resultWarnings = new ArrayList<>(qualityWarnings);
            if (response.parsed().partial()) resultWarnings.add("模型输出被截断，已保留 " + response.parsed().recoveredItems() + " 个完整条目。");
            if (response.diagnostics().compactRetryAttempted()) {
                resultWarnings.add(response.diagnostics().compactRetrySucceeded()
                    ? "模型输出疑似截断，紧凑重试已成功。"
                    : "紧凑重试未得到完整结构，当前结果来自已恢复的完整条目。");
            }
            String reason = resultWarnings.isEmpty() ? ""
                : "模型已返回结果，其中 " + resultWarnings.size() + " 项需要注意：" + String.join("；", resultWarnings);
            String modelStatus = resultWarnings.isEmpty() ? "SUCCESS" : "SUCCESS_WITH_WARNINGS";
            return new EnrichmentResult(retained, "MODEL", modelStatus, provider.getName(), reason, resultWarnings, summary, modelDiagnostics);
        } catch (IllegalArgumentException exception) {
            lastFailure = exception;
            String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
            if (message.contains("segments must be an array") || message.contains("is required") || message.contains("must be an array")) {
                schemaUnrecognized = true;
            }
        } catch (Exception exception) {
            lastFailure = exception;
            if (exception instanceof ModelGatewayService.ModelResponseFormatException responseFailure) {
                modelDiagnostics = responseFailure.diagnostics();
            }
        }
        // V3.3.3: 只有模型完全不可用才回退本地规则。
        // V3.3.4: 拆分失败原因，让用户看懂 DeepSeek 调用失败 / 返回格式无效 / 证据引用无效 的区别。
        String status = schemaUnrecognized ? ModelFailureClassifier.SCHEMA_UNRECOGNIZED
            : evidenceRejected ? "EVIDENCE_REJECTED"
            : modelFailureStatus(lastFailure);
        String reason = ModelFailureClassifier.humanReason(status, provider.getName());
        return new EnrichmentResult(fallback, "LOCAL_RULE", status, provider.getName(), reason, List.of(), "", modelDiagnostics);
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
            List<JsonNode> segments = outputAdapter.items(json, "segments", "developmentSegments", "cards", "items", "results");
            if (segments.isEmpty()) throw new IllegalArgumentException("segments must be an array");
            Map<String, ChangeAtom> sourceMap = new java.util.LinkedHashMap<>();
            for (int index = 0; index < atoms.size(); index++) sourceMap.put("S" + (index + 1), atoms.get(index));
            List<SegmentDraft> validated = new ArrayList<>();
            for (JsonNode item : segments) {
                if (!item.isObject()) continue;
                List<String> atomIds = outputAdapter.strings(item, "includedAtomIds", "atomIds");
                List<String> sourceIndexes = outputAdapter.strings(item, "sourceIndexes", "source_indexes", "sources");
                if (atomIds.isEmpty() && !sourceIndexes.isEmpty()) {
                    atomIds = sourceIndexes.stream().map(sourceMap::get).filter(java.util.Objects::nonNull).map(ChangeAtom::id).distinct().toList();
                }
                String rawTitle = outputAdapter.text(item, "", "segmentTitle", "segment_title", "title", "name");
                String rawSummary = outputAdapter.text(item, "", "plainSummary", "plain_summary", "summary", "description");
                if ((rawTitle.isBlank() && rawSummary.isBlank()) || atomIds.isEmpty()) continue;
                SegmentDraft candidate = new SegmentDraft(
                    DisplayContentSanitizer.sanitizeTitle(rawTitle.isBlank() ? rawSummary : rawTitle),
                    DisplayContentSanitizer.sanitizeSummary(rawSummary.isBlank() ? rawTitle : rawSummary),
                    atomIds,
                    DisplayContentSanitizer.sanitizeChanges(outputAdapter.strings(item, "mainChanges", "changes")),
                    DisplayContentSanitizer.sanitizeUserVisibleValue(outputAdapter.text(item, rawSummary, "userVisibleValue", "value")),
                    outputAdapter.strings(item, "evidenceRefs"),
                    outputAdapter.strings(item, "affectedFiles", "files"),
                    confidence(outputAdapter.text(item, "LOW", "confidence"))
                );
                evidenceValidator.validate(candidate, atoms).ifPresent(validated::add);
            }
            if (validated.isEmpty()) {
                throw new IllegalArgumentException("model segments did not pass evidence validation");
            }
            return validated.stream().limit(8).toList();
    }

    private AiProvider configuredProvider(UUID userId) {
        return providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(AiProvider::isDefaultEnabled)
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
                if (scope.evidenceGap()) {
                    facts.append(" 存在证据缺口");
                    if (!scope.evidenceGapReason().isBlank()) facts.append("（").append(scope.evidenceGapReason()).append("）");
                    facts.append("。");
                }
            }
            facts.append("\n\n");
        }
        // 原子事实。V3.3.4 小阶段修复：每个 atom 行瘦身 + 体积防护。
        if (atoms.size() <= MAX_PROMPT_ATOMS) {
            int included = 0;
            for (int index = 0; index < atoms.size(); index++) {
                ChangeAtom atom = atoms.get(index);
                String atomLine = compactAtomLine(atom, "S" + (index + 1));
                if (facts.length() + atomLine.length() > PROMPT_CHAR_BUDGET) {
                    // 超出预算：停止追加，避免 prompt 过大导致模型调用失败。
                    facts.append("(已截断，共 ").append(atoms.size()).append(" 条原子变化，前 ").append(included)
                        .append(" 条已纳入分析)\n");
                    break;
                }
                facts.append(atomLine);
                included++;
            }
        } else {
            for (int index = 0; index < Math.min(atoms.size(), MAX_PROMPT_ATOMS); index++) {
                facts.append(compactAtomLine(atoms.get(index), "S" + (index + 1)));
            }
            facts.append("(输入较多，仅列出前 ").append(MAX_PROMPT_ATOMS).append(" 个来源)\n");
        }
        return """
            你是 ProjectFlow V3.3.3 的开发推进段归并器。你不是在 GitHub 和本地 Git 之间二选一，而是在基于多来源证据判断当前真实开发状态。
            只依据给定事实返回 JSON：
            {"segments":[{"segmentTitle":"","plainSummary":"","sourceIndexes":["S1"],"mainChanges":[],"userVisibleValue":"","affectedFiles":[],"confidence":"HIGH|MEDIUM|LOW","needsUserReview":true}]}
            来源只填写给定的 S 编号，不要复制内部 atom ID、提交哈希或 evidenceRefs。不能发明来源或文件；文档和测试若服务于同一功能，应与功能归为一段；最多返回 8 段。
            标题和摘要必须描述实际开发结果，禁止目录名加"开发推进"或仅报告数量。mainChanges 必须为 3 到 6 条具体变化。
            needsUserReview 必须为 true，模型不能替用户确认项目事实。
            用户可见主内容（segmentTitle、plainSummary、mainChanges、userVisibleValue）必须使用简体中文人话；英文 commit message、文件路径、类名、接口名只能出现在证据细节里，不能成为主标题或摘要。
            如果变化未提交，请在摘要中说明"未提交工作区变化"；如果本地领先远程，请提示未推送；如果远程领先，请提示先同步本地；如果分叉，请标记当前分析可能不完整。
            如果证据不完整，要说不完整；不要假设 GitHub 一定最新，也不要假设本地 commit 一定完整。

            %s

            事实：
            """.formatted(retry ? "上一次输出未通过质量门槛，请改写为具体结果、简体中文人话，并避免重复标题。" : "") + facts;
    }

    // V3.3.4 小阶段修复：生成紧凑的 atom 行。
    // files 截断到 PROMPT_FILES_PER_ATOM 个（完整列表仍在 atom 对象里供 validator 校验）；
    // evidence 只发 commit:hash（逐个 file: 路径不进 prompt，validator 用完整 evidenceRefs 校验）；
    // diffHints 已去掉冗余的 commit=subject。
    private String compactAtomLine(ChangeAtom atom, String sourceIndex) {
        List<String> allFiles = atom.files();
        String fileList = allFiles.size() <= PROMPT_FILES_PER_ATOM
            ? String.join(",", allFiles)
            : String.join(",", allFiles.stream().limit(PROMPT_FILES_PER_ATOM).toList()) + " +" + (allFiles.size() - PROMPT_FILES_PER_ATOM);
        // evidence 里只保留 commit: 前缀的引用，不发逐个 file: 路径（已在 files= 里列过）。
        String evidence = atom.evidenceRefs().stream()
            .filter(ref -> ref.startsWith("commit:") || ref.startsWith("agent-result:"))
            .reduce((a, b) -> a + "," + b).orElse("");
        StringBuilder line = new StringBuilder();
        line.append(sourceIndex).append(" | ").append(atom.title())
            .append(" | files=").append(fileList)
            .append(" | source=").append(atom.sourceType());
        if (!evidence.isEmpty()) line.append(" | evidence=").append(evidence);
        if (!atom.diffHints().isEmpty()) line.append(" | diffHints=").append(String.join(";", atom.diffHints()));
        line.append('\n');
        return line.toString();
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

    // V3.3.4 小阶段修复：使用统一分类器，区分超时 / 鉴权 / 限流 / 5xx / 网络 / 未知。
    private String modelFailureStatus(Exception failure) {
        return ModelFailureClassifier.classifyException(failure);
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
        String modelSummary,
        ModelGatewayService.ModelCallDiagnostics modelDiagnostics
    ) {
        public EnrichmentResult(
            List<SegmentDraft> segments, String mode, String modelStatus, String providerName, String fallbackReason,
            List<String> qualityWarnings, String modelSummary
        ) {
            this(segments, mode, modelStatus, providerName, fallbackReason, qualityWarnings, modelSummary, null);
        }

        // 兼容旧构造：无 qualityWarnings/modelSummary。
        public EnrichmentResult(List<SegmentDraft> segments, String mode, String modelStatus, String providerName, String fallbackReason) {
            this(segments, mode, modelStatus, providerName, fallbackReason, List.of(), "", null);
        }
    }
}
