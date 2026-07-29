package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactRecordStatus;

@Component
public class StrongFactPromotionGuard {
    private static final Pattern INFERENCE = Pattern.compile(
        "(?i)(可能|推测|推断|或许|似乎|大概|maybe|likely|possibly|appears? to|suggests?)"
    );
    private static final Pattern HISTORICAL_REASON = Pattern.compile(
        "(?i)(为了|因为|出于|以便|之所以|当初.*原因|because|in order to|designed to)"
    );
    private static final Pattern DEPRECATION = Pattern.compile(
        "(?i)(已废弃|已经废弃|弃用|不再使用|deprecated|obsolete|superseded)"
    );
    private static final Pattern TECHNICAL_DEBT = Pattern.compile(
        "(?i)(技术债|尚未解决|未解决的?问题|TODO|FIXME|technical debt|known limitation)"
    );
    private static final Set<String> REASON_SOURCES = Set.of(
        "ADR", "ISSUE", "PULL_REQUEST", "COMMIT_BODY", "USER_DECLARATION",
        "DESIGN_DOCUMENT", "AGENT_DECISION"
    );
    private static final Set<String> DEPRECATION_SOURCES = Set.of(
        "DEPRECATION_MARKER", "MIGRATION_DOCUMENT", "FILE_DELETION", "ISSUE",
        "PULL_REQUEST", "USER_DECLARATION"
    );
    private static final Set<String> DEBT_SOURCES = Set.of(
        "TODO_MARKER", "FIXME_MARKER", "OPEN_ISSUE", "TEST_FAILURE",
        "RISK_DOCUMENT", "KNOWN_LIMITATION", "VERIFIABLE_CODE_GAP", "USER_DECLARATION"
    );

    public Decision classify(DevelopmentSegment segment) {
        List<String> reasons = new ArrayList<>();
        if (segment.getTitle().isBlank() || segment.getPlainSummary().isBlank()) {
            reasons.add("标题或摘要不完整");
        }
        List<EvidenceRef> evidence = segment.getEvidenceRefs().stream()
            .map(StrongFactPromotionGuard::parseEvidence)
            .filter(java.util.Objects::nonNull)
            .toList();
        if (evidence.isEmpty()) reasons.add("没有有效证据引用，不能进入强事实层");
        int invalidEvidence = segment.getEvidenceRefs().size() - evidence.size();
        if (invalidEvidence > 0) reasons.add("存在 " + invalidEvidence + " 个未知 Evidence 引用");

        Set<String> sourceTypes = new LinkedHashSet<>();
        evidence.forEach(item -> sourceTypes.add(item.sourceType()));
        boolean engineering = sourceTypes.stream().anyMatch(Set.of(
            "LOCAL_GIT_COMMIT", "PROJECT_FILE", "TEST_EXECUTION", "CI_RUN",
            "FILE_DELETION", "VERIFIABLE_CODE_GAP"
        )::contains);
        boolean agentOnly = !sourceTypes.isEmpty()
            && sourceTypes.stream().allMatch(value -> value.equals("AGENT_RESULT") || value.equals("AGENT_DECISION"));
        String statement = String.join(" ", List.of(
            segment.getTitle(), segment.getPlainSummary(),
            String.join(" ", segment.getMainChanges()), segment.getUserVisibleValue()
        )).strip();

        ProjectFactEpistemicStatus epistemic = ProjectFactEpistemicStatus.OBSERVED;
        if (agentOnly || (!segment.getIncludedAgentResultRefs().isEmpty() && !engineering)) {
            epistemic = ProjectFactEpistemicStatus.PROCESS_EVIDENCE;
            reasons.add("Agent Result 是过程证据，缺少独立工程验证");
        } else if (INFERENCE.matcher(statement).find()) {
            epistemic = ProjectFactEpistemicStatus.INFERRED;
            reasons.add("包含推断表达，不能升级为强事实");
        } else if (!engineering && !sourceTypes.isEmpty()) {
            epistemic = ProjectFactEpistemicStatus.DECLARED;
            reasons.add("来源是声明材料，尚未经过工程验证");
        }

        if (HISTORICAL_REASON.matcher(statement).find() && disjoint(sourceTypes, REASON_SOURCES)) {
            epistemic = ProjectFactEpistemicStatus.INFERRED;
            reasons.add("历史设计原因缺少 ADR、Issue、PR、commit body、设计文档或用户说明");
        }
        if (DEPRECATION.matcher(statement).find() && disjoint(sourceTypes, DEPRECATION_SOURCES)) {
            epistemic = ProjectFactEpistemicStatus.INFERRED;
            reasons.add("废弃结论缺少 deprecated、替代、删除、迁移或关闭原因证据");
        }
        if (TECHNICAL_DEBT.matcher(statement).find() && disjoint(sourceTypes, DEBT_SOURCES)) {
            epistemic = ProjectFactEpistemicStatus.INFERRED;
            reasons.add("技术债结论缺少 TODO/FIXME、Issue、失败测试、风险或已知限制证据");
        }
        if (!"PASS".equals(segment.getQualityStatus())) {
            if (epistemic.isStrongFact()) epistemic = ProjectFactEpistemicStatus.UNKNOWN;
            reasons.add(segment.getQualityReason().isBlank()
                ? "分析质量状态为 " + segment.getQualityStatus()
                : segment.getQualityReason());
        }
        if (evidence.isEmpty()) epistemic = ProjectFactEpistemicStatus.UNKNOWN;

        boolean independentlyVerified = engineering
            && sourceTypes.stream().anyMatch(Set.of("TEST_EXECUTION", "CI_RUN")::contains)
            && sourceTypes.stream().anyMatch(Set.of("LOCAL_GIT_COMMIT", "PROJECT_FILE")::contains);
        if (reasons.isEmpty() && independentlyVerified) epistemic = ProjectFactEpistemicStatus.VERIFIED;
        ProjectFactRecordStatus recordStatus = reasons.isEmpty() && epistemic.isStrongFact()
            ? ProjectFactRecordStatus.RECORDED
            : ProjectFactRecordStatus.NEEDS_ATTENTION;
        return new Decision(
            epistemic,
            recordStatus,
            List.copyOf(new LinkedHashSet<>(reasons)),
            List.copyOf(sourceTypes),
            recordStatus == ProjectFactRecordStatus.RECORDED ? "VALIDATED" : "PENDING_VALIDATION"
        );
    }

    private static EvidenceRef parseEvidence(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) return null;
        String prefix = value.substring(0, separator).toLowerCase(Locale.ROOT);
        String sourceType = switch (prefix) {
            case "commit" -> "LOCAL_GIT_COMMIT";
            case "file" -> "PROJECT_FILE";
            case "agent-result" -> "AGENT_RESULT";
            case "agent-decision" -> "AGENT_DECISION";
            case "test-result" -> "TEST_EXECUTION";
            case "ci" -> "CI_RUN";
            case "adr" -> "ADR";
            case "issue" -> "ISSUE";
            case "open-issue" -> "OPEN_ISSUE";
            case "pr" -> "PULL_REQUEST";
            case "commit-body" -> "COMMIT_BODY";
            case "user" -> "USER_DECLARATION";
            case "design-doc" -> "DESIGN_DOCUMENT";
            case "deprecated" -> "DEPRECATION_MARKER";
            case "migration" -> "MIGRATION_DOCUMENT";
            case "file-deleted" -> "FILE_DELETION";
            case "todo" -> "TODO_MARKER";
            case "fixme" -> "FIXME_MARKER";
            case "test-failure" -> "TEST_FAILURE";
            case "risk-doc" -> "RISK_DOCUMENT";
            case "known-limit" -> "KNOWN_LIMITATION";
            case "code-gap" -> "VERIFIABLE_CODE_GAP";
            default -> null;
        };
        return sourceType == null ? null : new EvidenceRef(value, sourceType);
    }

    private static boolean disjoint(Set<String> actual, Set<String> required) {
        return actual.stream().noneMatch(required::contains);
    }

    public record Decision(
        ProjectFactEpistemicStatus epistemicStatus,
        ProjectFactRecordStatus recordStatus,
        List<String> reasons,
        List<String> sourceTypes,
        String validationStatus
    ) {
    }

    private record EvidenceRef(String id, String sourceType) {
    }
}
