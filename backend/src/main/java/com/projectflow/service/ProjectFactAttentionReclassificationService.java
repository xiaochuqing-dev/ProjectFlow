package com.projectflow.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.repository.ProjectFactRepository;

@Service
public class ProjectFactAttentionReclassificationService {
    static final String FALLBACK_TIME_REASON = "无法从提交或 Agent result 确定发生时间，已回退到分析批次时间";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectFactAttentionReclassificationService.class);
    private final ProjectFactRepository factRepository;

    public ProjectFactAttentionReclassificationService(ProjectFactRepository factRepository) {
        this.factRepository = factRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(180)
    @Transactional
    public void reclassifyOnStartup() {
        ReclassificationResult result = reclassify(factRepository.findAll());
        if (result.changed() > 0) {
            LOGGER.info(
                "ProjectFact attention reclassification completed: before={}, recorded={}, retainedAttention={}, changed={}",
                result.beforeAttention(), result.recorded(), result.retainedAttention(), result.changed()
            );
        }
    }

    ReclassificationResult reclassify(List<ProjectFact> facts) {
        int before = 0;
        int changed = 0;
        int recorded = 0;
        int retained = 0;
        for (ProjectFact fact : facts) {
            if (fact.getRecordStatus() != ProjectFactRecordStatus.NEEDS_ATTENTION) continue;
            before++;
            if (canRecover(fact)) {
                fact.reclassify(ProjectFactRecordStatus.RECORDED, "");
                factRepository.save(fact);
                changed++;
                recorded++;
            } else {
                retained++;
            }
        }
        return new ReclassificationResult(before, recorded, retained, changed);
    }

    static boolean canRecover(ProjectFact fact) {
        if (fact == null || fact.getSourceSegmentId() == null || fact.getBatchId() == null) return false;
        if (!"PASS".equalsIgnoreCase(fact.getQualityStatus())) return false;
        boolean primaryEvidence = fact.getCommitCount() > 0 || fact.getAgentResultCount() > 0;
        boolean supportingEvidence = fact.getAffectedFileCount() > 0 && fact.getEvidenceCount() > 0;
        if (!primaryEvidence || !supportingEvidence) return false;
        String remainingReason = fact.getAttentionReason().replace(FALLBACK_TIME_REASON, "")
            .replace("；", "").replace(";", "").trim();
        return remainingReason.isBlank();
    }

    record ReclassificationResult(int beforeAttention, int recorded, int retainedAttention, int changed) {
    }
}
