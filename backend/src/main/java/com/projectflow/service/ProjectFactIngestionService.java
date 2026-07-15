package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactAgentResultRef;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactCursor;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCursorRepository;
import com.projectflow.repository.ProjectFactRepository;

@Service
public class ProjectFactIngestionService {
    private static final Pattern GIT_SHA = Pattern.compile("(?i)^[0-9a-f]{7,64}$");

    private final ChangeBatchRepository batchRepository;
    private final DevelopmentSegmentRepository segmentRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactCommitRefRepository commitRefRepository;
    private final ProjectFactAgentResultRefRepository agentResultRefRepository;
    private final ProjectFactCursorRepository cursorRepository;

    public ProjectFactIngestionService(
        ChangeBatchRepository batchRepository,
        DevelopmentSegmentRepository segmentRepository,
        ProjectFactRepository factRepository,
        ProjectFactCommitRefRepository commitRefRepository,
        ProjectFactAgentResultRefRepository agentResultRefRepository,
        ProjectFactCursorRepository cursorRepository
    ) {
        this.batchRepository = batchRepository;
        this.segmentRepository = segmentRepository;
        this.factRepository = factRepository;
        this.commitRefRepository = commitRefRepository;
        this.agentResultRefRepository = agentResultRefRepository;
        this.cursorRepository = cursorRepository;
    }

    /**
     * Converts one persisted analysis batch into durable facts. The batch row is
     * locked so retries and concurrent reusable-batch paths serialize on both H2
     * and PostgreSQL. Fact persistence, batch completion, and incremental cursor
     * advancement share one transaction.
     */
    @Transactional
    public IngestionResult ingestBatch(
        UUID projectId,
        UUID batchId,
        ProjectFactOrigin origin,
        boolean advanceIncrementalCursor
    ) {
        ChangeBatch batch = batchRepository.findLockedById(batchId)
            .filter(value -> value.getProjectId().equals(projectId))
            .orElseThrow(() -> new IllegalArgumentException("Change batch was not found for project"));
        List<DevelopmentSegment> segments = segmentRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (segments.isEmpty()) {
            throw new IllegalStateException("Cannot complete fact ingestion for a batch without development segments");
        }
        for (DevelopmentSegment segment : segments) {
            ingestSegment(batch, segment, origin);
        }
        factRepository.flush();

        List<ProjectFact> facts = factRepository.findByBatchIdOrderByOccurredFromAscCreatedAtAsc(batchId);
        int attention = (int) facts.stream()
            .filter(fact -> fact.getRecordStatus() == ProjectFactRecordStatus.NEEDS_ATTENTION)
            .count();
        Instant occurredFrom = facts.stream()
            .map(ProjectFact::getOccurredFrom)
            .filter(java.util.Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
        Instant occurredTo = facts.stream()
            .map(ProjectFact::getOccurredTo)
            .filter(java.util.Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(occurredFrom);
        batch.markFactsRecorded(facts.size(), attention, occurredFrom, occurredTo);
        batchRepository.save(batch);

        if (advanceIncrementalCursor && !batch.getHeadCommitSha().isBlank()) {
            ProjectFactCursor cursor = cursorRepository.findLockedByProjectId(projectId)
                .orElseGet(() -> new ProjectFactCursor(projectId));
            cursor.advance(batch.getHeadCommitSha(), Instant.now(), batch.getBranchName(), batch.getId());
            cursorRepository.save(cursor);
        }
        return new IngestionResult(batch.getId(), facts.size(), attention, occurredFrom, occurredTo);
    }

    private ProjectFact ingestSegment(ChangeBatch batch, DevelopmentSegment segment, ProjectFactOrigin origin) {
        String fingerprint = fingerprint(
            segment.getProjectId(), segment.getBatchId(), segment.getId(), segment.getIncludedCommitRefs(),
            segment.getIncludedAgentResultRefs(), segment.getEvidenceRefs()
        );
        ProjectFact existing = factRepository.findByProjectIdAndFactFingerprint(segment.getProjectId(), fingerprint)
            .orElse(null);
        if (existing != null) {
            ensureCommitRefs(existing);
            ensureAgentResultRefs(existing);
            return existing;
        }

        Classification classification = classify(segment);
        Instant occurredFrom = segment.getOccurredFrom();
        Instant occurredTo = segment.getOccurredTo();
        List<String> reasons = new ArrayList<>(classification.reasons());
        if (occurredFrom == null) {
            occurredFrom = batch.getScanStartedAt();
            occurredTo = occurredFrom;
            reasons.add("无法从提交或 Agent result 确定发生时间，已回退到分析批次时间");
        }
        ProjectFactRecordStatus status = reasons.isEmpty()
            ? ProjectFactRecordStatus.RECORDED
            : ProjectFactRecordStatus.NEEDS_ATTENTION;
        ProjectFact fact = new ProjectFact(segment.getProjectId(), segment.getBatchId(), segment.getId(), origin, fingerprint);
        fact.updateContent(
            segment.getTitle(), segment.getPlainSummary(), segment.getMainChanges(), segment.getUserVisibleValue(),
            occurredFrom, occurredTo, segment.getIncludedCommitRefs(), segment.getCommitUrls(),
            segment.getIncludedAgentResultRefs(), segment.getAffectedFiles(), segment.getEvidenceRefs(),
            sourceMode(segment), segment.getQualityStatus(), segment.getConfidence(), status,
            String.join("；", new LinkedHashSet<>(reasons))
        );
        fact = factRepository.save(fact);
        ensureCommitRefs(fact);
        ensureAgentResultRefs(fact);
        return fact;
    }

    private Classification classify(DevelopmentSegment segment) {
        List<String> reasons = new ArrayList<>();
        if (segment.getTitle().isBlank() || segment.getPlainSummary().isBlank()) {
            reasons.add("标题或摘要不完整");
        }
        List<String> evidence = segment.getEvidenceRefs().stream()
            .filter(this::isRecognizedEvidence)
            .toList();
        if (evidence.isEmpty()) {
            reasons.add("没有有效证据引用，不能作为普通已记录事实");
        }
        boolean hasCodeEvidence = evidence.stream().anyMatch(value -> value.startsWith("commit:") || value.startsWith("file:"));
        boolean agentOnly = !segment.getIncludedAgentResultRefs().isEmpty()
            && segment.getIncludedCommitRefs().isEmpty()
            && !hasCodeEvidence;
        if (agentOnly) {
            reasons.add("只有 Agent result，缺少提交或代码变化证据");
        }
        if (!"PASS".equals(segment.getQualityStatus())) {
            reasons.add(segment.getQualityReason().isBlank()
                ? "分析质量状态为 " + segment.getQualityStatus()
                : segment.getQualityReason());
        }
        return new Classification(reasons);
    }

    private boolean isRecognizedEvidence(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim();
        if (normalized.startsWith("commit:")) return normalized.length() > "commit:".length();
        if (normalized.startsWith("file:")) return normalized.length() > "file:".length();
        if (normalized.startsWith("agent-result:")) return normalized.length() > "agent-result:".length();
        return false;
    }

    private String sourceMode(DevelopmentSegment segment) {
        String mode = segment.getGenerationMode();
        String fallback = segment.getFallbackReason().toLowerCase(Locale.ROOT);
        if ("MODEL".equals(mode) && (fallback.contains("部分") || fallback.contains("截断") || fallback.contains("恢复"))) {
            return "MODEL_PARTIAL_RESULT";
        }
        if (!segment.getIncludedAgentResultRefs().isEmpty() && segment.getIncludedCommitRefs().isEmpty()) {
            return "AGENT_RESULT";
        }
        return mode;
    }

    private void ensureCommitRefs(ProjectFact fact) {
        Set<String> commits = new LinkedHashSet<>();
        fact.getCommitRefs().stream().map(ProjectFactIngestionService::normalizeCommit).filter(value -> !value.isBlank()).forEach(commits::add);
        fact.getEvidenceRefs().stream()
            .filter(value -> value != null && value.startsWith("commit:"))
            .map(value -> normalizeCommit(value.substring("commit:".length())))
            .filter(value -> !value.isBlank())
            .forEach(commits::add);
        for (String commit : commits) {
            if (!commitRefRepository.existsByFactIdAndCommitSha(fact.getId(), commit)) {
                commitRefRepository.save(new ProjectFactCommitRef(fact.getProjectId(), fact.getId(), commit));
            }
        }
    }

    private void ensureAgentResultRefs(ProjectFact fact) {
        for (String value : fact.getAgentResultRefs()) {
            String ref = value == null ? "" : value.trim();
            if (ref.isBlank()) continue;
            if (!agentResultRefRepository.existsByFactIdAndAgentResultRef(fact.getId(), ref)) {
                agentResultRefRepository.save(new ProjectFactAgentResultRef(fact.getProjectId(), fact.getId(), ref));
            }
        }
    }

    public static String fingerprint(
        UUID projectId,
        UUID batchId,
        UUID sourceSegmentId,
        List<String> commitRefs,
        List<String> agentResultRefs,
        List<String> evidenceRefs
    ) {
        String canonical = String.join("\n",
            projectId == null ? "" : projectId.toString(),
            batchId == null ? "" : batchId.toString(),
            sourceSegmentId == null ? "" : sourceSegmentId.toString(),
            canonicalList(commitRefs),
            canonicalList(agentResultRefs),
            canonicalList(evidenceRefs)
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalList(List<String> values) {
        if (values == null) return "";
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
            .distinct().sorted().reduce((left, right) -> left + "|" + right).orElse("");
    }

    private static String normalizeCommit(String value) {
        String normalized = value == null ? "" : value.trim();
        return GIT_SHA.matcher(normalized).matches() ? normalized.toLowerCase(Locale.ROOT) : "";
    }

    public record IngestionResult(UUID batchId, int factCount, int attentionCount, Instant occurredFrom, Instant occurredTo) {
    }

    private record Classification(List<String> reasons) {
    }
}
