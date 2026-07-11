package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V2ProjectDtos.CapabilityInterpretRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.AgentBridgeRequest;
import com.projectflow.service.ProjectAgentBridgeService;
import com.projectflow.service.ProjectAnalysisService;
import com.projectflow.service.ProjectMemoryService;
import com.projectflow.service.WorkSessionScanService;

class LongTransactionBoundaryTest {
    @Test
    void externalModelGitAndFileWorkDoesNotHoldMethodTransactions() throws Exception {
        assertThat(WorkSessionScanService.class.getMethod("scan", UUID.class, UUID.class, UUID.class)
            .getAnnotation(Transactional.class)).isNull();
        assertThat(ProjectAnalysisService.class.getMethod("runProjectAnalysis", UUID.class, UUID.class)
            .getAnnotation(Transactional.class)).isNull();
        assertThat(ProjectAnalysisService.class.getMethod("analyzeProjectFile", UUID.class, UUID.class, ProjectFileAnalysisRequest.class)
            .getAnnotation(Transactional.class)).isNull();
        assertThat(ProjectMemoryService.class.getMethod("interpretCapability", UUID.class, UUID.class, CapabilityInterpretRequest.class)
            .getAnnotation(Transactional.class)).isNull();
        assertThat(ProjectAgentBridgeService.class.getMethod("scanAgentResults", UUID.class, UUID.class, AgentBridgeRequest.class)
            .getAnnotation(Transactional.class)).isNull();
    }
}
