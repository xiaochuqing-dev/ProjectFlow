package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactAgentResultRef;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectSedimentRepository;

@Service
public class ProjectFactMigrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectFactMigrationService.class);
    private static final Pattern GIT_SHA = Pattern.compile("(?i)^[0-9a-f]{7,64}$");

    private final ChangeBatchRepository batchRepository;
    private final DevelopmentSegmentRepository segmentRepository;
    private final ProjectSedimentRepository sedimentRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactCommitRefRepository commitRefRepository;
    private final ProjectFactAgentResultRefRepository agentResultRefRepository;
    private final ProjectFactIngestionService ingestionService;
    private final TransactionTemplate transactionTemplate;

    public ProjectFactMigrationService(
        ChangeBatchRepository batchRepository,
        DevelopmentSegmentRepository segmentRepository,
        ProjectSedimentRepository sedimentRepository,
        ProjectFactRepository factRepository,
        ProjectFactCommitRefRepository commitRefRepository,
        ProjectFactAgentResultRefRepository agentResultRefRepository,
        ProjectFactIngestionService ingestionService,
        PlatformTransactionManager transactionManager
    ) {
        this.batchRepository = batchRepository;
        this.segmentRepository = segmentRepository;
        this.sedimentRepository = sedimentRepository;
        this.factRepository = factRepository;
        this.commitRefRepository = commitRefRepository;
        this.agentResultRefRepository = agentResultRefRepository;
        this.ingestionService = ingestionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void migrateOnStartup() {
        migrateSegments();
        for (ProjectSediment sediment : sedimentRepository.findAll()) {
            try {
                transactionTemplate.executeWithoutResult(status -> migrateSediment(sediment.getId()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Legacy sediment fact migration failed: sedimentId={}", sediment.getId(), exception);
            }
        }
    }

    private void migrateSegments() {
        Map<UUID, List<ChangeBatch>> byProject = new LinkedHashMap<>();
        for (ChangeBatch batch : batchRepository.findAll()) {
            if ("HISTORY_BACKFILL".equals(batch.getScanType())) continue;
            if (segmentRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId()).isEmpty()) continue;
            byProject.computeIfAbsent(batch.getProjectId(), ignored -> new ArrayList<>()).add(batch);
        }
        byProject.forEach((projectId, batches) -> {
            batches.sort(Comparator.comparing(ChangeBatch::getScanStartedAt));
            boolean complete = true;
            for (ChangeBatch batch : batches) {
                try {
                    ingestionService.ingestBatch(projectId, batch.getId(), ProjectFactOrigin.LEGACY_SEGMENT_MIGRATION, false);
                } catch (RuntimeException exception) {
                    complete = false;
                    LOGGER.warn("Legacy segment fact migration failed: batchId={}", batch.getId(), exception);
                }
            }
            if (complete && !batches.isEmpty()) {
                ChangeBatch latest = batches.get(batches.size() - 1);
                ingestionService.ingestBatch(projectId, latest.getId(), ProjectFactOrigin.LEGACY_SEGMENT_MIGRATION, true);
            }
        });
    }

    private void migrateSediment(UUID sedimentId) {
        ProjectSediment sediment = sedimentRepository.findById(sedimentId).orElse(null);
        if (sediment == null) return;
        boolean linked = false;
        for (String source : sediment.getSourceSegmentIds()) {
            UUID segmentId = parseUuid(source);
            if (segmentId == null) continue;
            ProjectFact fact = factRepository.findFirstByProjectIdAndSourceSegmentId(sediment.getProjectId(), segmentId).orElse(null);
            if (fact != null) {
                fact.linkLegacySediment(sediment.getId());
                factRepository.save(fact);
                linked = true;
            }
        }
        if (linked) return;

        String fingerprint = legacyFingerprint(sediment);
        ProjectFact existing = factRepository.findByProjectIdAndFactFingerprint(sediment.getProjectId(), fingerprint).orElse(null);
        if (existing != null) {
            existing.linkLegacySediment(sediment.getId());
            factRepository.save(existing);
            return;
        }
        UUID batchId = sediment.getSourceBatchIds().stream().map(this::parseUuid).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        List<String> evidence = sediment.getEvidenceRefs();
        boolean reliableEvidence = evidence.stream().anyMatch(this::isReliableEvidence);
        ProjectFact fact = new ProjectFact(
            sediment.getProjectId(), batchId, null, ProjectFactOrigin.LEGACY_SEDIMENT_MIGRATION, fingerprint
        );
        List<String> commits = evidence.stream().filter(value -> value != null && value.startsWith("commit:"))
            .map(value -> value.substring(7)).toList();
        List<String> agents = evidence.stream().filter(value -> value != null && value.startsWith("agent-result:"))
            .toList();
        String attention = reliableEvidence ? "" : "旧版沉淀缺少可验证提交、文件或 Agent result 证据";
        fact.updateContent(
            sediment.getTitle(), sediment.getSummary(),
            sediment.getProblemSolved().isBlank() ? List.of() : List.of(sediment.getProblemSolved()),
            sediment.getProblemSolved(), sediment.getCreatedAt(), sediment.getCreatedAt(), commits, List.of(), agents,
            sediment.getAffectedFiles(), evidence, sediment.getContentSource(), sediment.getQualityStatus(),
            reliableEvidence ? EvidenceConfidence.MEDIUM : EvidenceConfidence.LOW,
            reliableEvidence ? ProjectFactRecordStatus.RECORDED : ProjectFactRecordStatus.NEEDS_ATTENTION,
            attention
        );
        fact.linkLegacySediment(sediment.getId());
        fact = factRepository.save(fact);
        for (String commit : commits) {
            String normalized = normalizeCommit(commit);
            if (!normalized.isBlank() && !commitRefRepository.existsByFactIdAndCommitSha(fact.getId(), normalized)) {
                commitRefRepository.save(new ProjectFactCommitRef(fact.getProjectId(), fact.getId(), normalized));
            }
        }
        for (String agent : agents) {
            if (!agentResultRefRepository.existsByFactIdAndAgentResultRef(fact.getId(), agent)) {
                agentResultRefRepository.save(new ProjectFactAgentResultRef(fact.getProjectId(), fact.getId(), agent));
            }
        }
    }

    private boolean isReliableEvidence(String value) {
        if (value == null || value.isBlank()) return false;
        if (value.startsWith("file:") || value.startsWith("agent-result:")) return true;
        return value.startsWith("commit:") && !normalizeCommit(value.substring(7)).isBlank();
    }

    private String legacyFingerprint(ProjectSediment sediment) {
        Set<String> evidence = new LinkedHashSet<>(sediment.getEvidenceRefs());
        String input = sediment.getProjectId() + "\nLEGACY_SEDIMENT\n" + sediment.getId() + "\n"
            + evidence.stream().filter(java.util.Objects::nonNull).map(String::trim).sorted()
                .reduce((left, right) -> left + "|" + right).orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        String candidate = value.contains(":") ? value.substring(value.lastIndexOf(':') + 1) : value;
        try {
            return UUID.fromString(candidate.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizeCommit(String value) {
        String normalized = value == null ? "" : value.trim();
        return GIT_SHA.matcher(normalized).matches() ? normalized.toLowerCase(Locale.ROOT) : "";
    }
}
