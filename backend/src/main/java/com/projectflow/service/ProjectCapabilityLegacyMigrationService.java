package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.CapabilityCardStatus;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityAttention;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityEvolutionType;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.repository.ProjectCapabilityAttentionRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectSedimentRepository;

@Service
public class ProjectCapabilityLegacyMigrationService {
    private final ProjectCapabilityCardRepository cardRepository;
    private final ProjectCapabilityRepository capabilityRepository;
    private final ProjectCapabilityEvolutionRepository evolutionRepository;
    private final ProjectCapabilityFactRepository relationRepository;
    private final ProjectCapabilityAttentionRepository attentionRepository;
    private final ProjectSedimentRepository sedimentRepository;
    private final ProjectFactRepository factRepository;

    public ProjectCapabilityLegacyMigrationService(
        ProjectCapabilityCardRepository cardRepository,
        ProjectCapabilityRepository capabilityRepository,
        ProjectCapabilityEvolutionRepository evolutionRepository,
        ProjectCapabilityFactRepository relationRepository,
        ProjectCapabilityAttentionRepository attentionRepository,
        ProjectSedimentRepository sedimentRepository,
        ProjectFactRepository factRepository
    ) {
        this.cardRepository = cardRepository;
        this.capabilityRepository = capabilityRepository;
        this.evolutionRepository = evolutionRepository;
        this.relationRepository = relationRepository;
        this.attentionRepository = attentionRepository;
        this.sedimentRepository = sedimentRepository;
        this.factRepository = factRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(240)
    @Transactional
    public void migrateConfirmedCards() {
        cardRepository.findByStatusOrderByCreatedAtAsc(CapabilityCardStatus.CONFIRMED).forEach(card -> {
            if (capabilityRepository.findByProjectIdAndLegacyCardId(card.getProjectId(), card.getId()).isPresent()) return;
            String identity = sha256(card.getProjectId() + "\nLEGACY_CARD\n" + card.getId());
            ProjectCapability capability = new ProjectCapability(card.getProjectId(), identity, sha256(identity + "\nV1"));
            capability.initialize(
                card.getName(), card.getSummary(), card.getProblemSolved(), card.getSummary(),
                card.getFeatureEntry().isBlank() ? List.of() : List.of(card.getFeatureEntry()),
                card.getCreatedAt(), "LEGACY_SEED", card.getModelProvider(), "", card.getAnalysisJobId()
            );
            capability.markLegacy(card.getId());
            capability.updateExpressions(card.getReadmeExpression(), card.getResumeExpression(), card.getInterviewExpression());
            capability = capabilityRepository.save(capability);

            List<ProjectFact> facts = traceFacts(card.getProjectId(), card.getSourceRefs());
            if (facts.isEmpty()) {
                String reason = "旧版已确认能力卡片未能追溯到 ProjectFact，已保留兼容资产但不作为事实原生能力证据";
                String attentionFingerprint = sha256(card.getProjectId() + "\nLEGACY_GAP\n" + card.getId());
                if (attentionRepository.findByProjectIdAndAttentionFingerprint(card.getProjectId(), attentionFingerprint).isEmpty()) {
                    attentionRepository.save(new ProjectCapabilityAttention(
                        card.getProjectId(), "LEGACY_EVIDENCE_GAP", reason, null, capability.getId(), null,
                        attentionFingerprint, card.getAnalysisJobId()
                    ));
                }
                capability.updateStatistics(0, 0, 0, 0, 0, 0, ProjectCapabilityMaturity.FORMING, reason, card.getCreatedAt(), card.getCreatedAt());
                capabilityRepository.save(capability);
                return;
            }

            String evolutionFingerprint = sha256(card.getProjectId() + "\nLEGACY_EVOLUTION\n" + card.getId());
            UUID migratedCapabilityId = capability.getId();
            ProjectCapabilityEvolution evolution = evolutionRepository.findByProjectIdAndOperationFingerprint(
                card.getProjectId(), evolutionFingerprint
            ).orElseGet(() -> {
                ProjectCapabilityEvolution created = new ProjectCapabilityEvolution(
                    card.getProjectId(), migratedCapabilityId, ProjectCapabilityEvolutionType.NEW_CAPABILITY, 0, 1,
                    "迁移旧版已确认能力", card.getSummary(), latest(facts), evolutionFingerprint
                );
                created.attachSourceStats(
                    facts.size(), (int) facts.stream().map(ProjectFact::getBatchId).filter(java.util.Objects::nonNull).distinct().count(),
                    facts.stream().map(ProjectFact::getTimelineMonthKey).filter(value -> !value.isBlank()).distinct().sorted().toList(),
                    card.getAnalysisJobId(), card.getModelProvider(), ""
                );
                return evolutionRepository.save(created);
            });
            for (ProjectFact fact : facts) {
                if (!relationRepository.existsByCapabilityIdAndFactId(capability.getId(), fact.getId())) {
                    relationRepository.save(new ProjectCapabilityFact(
                        card.getProjectId(), capability.getId(), fact.getId(), ProjectCapabilityRelationRole.FORMATION, evolution.getId()
                    ));
                }
            }
            int batches = (int) facts.stream().map(ProjectFact::getBatchId).filter(java.util.Objects::nonNull).distinct().count();
            int commits = (int) facts.stream().flatMap(fact -> fact.getCommitRefs().stream()).distinct().count();
            int evidence = facts.stream().mapToInt(ProjectFact::getEvidenceCount).sum();
            int attention = (int) facts.stream().filter(fact -> fact.getRecordStatus() == com.projectflow.entity.ProjectFactRecordStatus.NEEDS_ATTENTION).count();
            ProjectCapabilityMaturity maturity = ProjectCapabilityMapService.maturity(
                facts.size(), batches, commits, evidence, attention, 1, earliest(facts), latest(facts)
            );
            capability.updateStatistics(
                facts.size(), batches, commits, evidence, attention, 1, maturity,
                "由旧版已确认卡片及 " + facts.size() + " 条可追溯 ProjectFact 迁移形成", earliest(facts), latest(facts)
            );
            capabilityRepository.save(capability);
        });
    }

    private List<ProjectFact> traceFacts(UUID projectId, List<String> sourceRefs) {
        LinkedHashSet<UUID> factIds = new LinkedHashSet<>();
        for (String source : sourceRefs == null ? List.<String>of() : sourceRefs) {
            if (source == null) continue;
            UUID direct = parseUuid(source.startsWith("fact:") ? source.substring(5) : "");
            if (direct != null) factRepository.findByIdAndProjectId(direct, projectId).ifPresent(fact -> factIds.add(fact.getId()));
            UUID sedimentId = parseUuid(source.startsWith("sediment:") ? source.substring(9) : "");
            ProjectSediment sediment = sedimentId == null ? null : sedimentRepository.findById(sedimentId).orElse(null);
            if (sediment == null || !projectId.equals(sediment.getProjectId())) continue;
            for (String segmentRef : sediment.getSourceSegmentIds()) {
                UUID segmentId = parseUuid(segmentRef.contains(":") ? segmentRef.substring(segmentRef.lastIndexOf(':') + 1) : segmentRef);
                if (segmentId != null) factRepository.findFirstByProjectIdAndSourceSegmentId(projectId, segmentId)
                    .ifPresent(fact -> factIds.add(fact.getId()));
            }
        }
        return factRepository.findAllById(factIds).stream().filter(fact -> projectId.equals(fact.getProjectId())).toList();
    }

    private static UUID parseUuid(String value) {
        try { return UUID.fromString(value == null ? "" : value.trim()); }
        catch (RuntimeException exception) { return null; }
    }
    private static Instant earliest(List<ProjectFact> facts) {
        return facts.stream().map(fact -> fact.getTimelineEventAt() != null ? fact.getTimelineEventAt() : fact.getOccurredFrom())
            .filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(Instant.now());
    }
    private static Instant latest(List<ProjectFact> facts) {
        return facts.stream().map(fact -> fact.getTimelineEventAt() != null ? fact.getTimelineEventAt() : fact.getOccurredTo())
            .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.now());
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
