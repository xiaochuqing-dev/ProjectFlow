package com.projectflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.CapabilityCardStatus;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectCapabilityCardRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ProjectCapabilityService;

@ExtendWith(MockitoExtension.class)
class ProjectCapabilityServiceTest {
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectSedimentRepository sedimentRepository;
    @Mock private ProjectCapabilityCardRepository cardRepository;
    @Mock private AiProviderRepository providerRepository;
    @Mock private ModelGatewayService modelGatewayService;
    @Mock private ProjectAnalysisJobRepository jobRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ModelOutputAdapter outputAdapter;

    @BeforeEach
    void prepareTransactionManager() {
        outputAdapter = new ModelOutputAdapter(objectMapper);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void modelFailureKeepsExistingCandidates() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = prepareInput(userId);
        when(modelGatewayService.callStructured(any(), any(), any(com.projectflow.service.ModelTaskType.class))).thenThrow(new IOException("bad json"));
        ProjectCapabilityService service = service();

        assertThatThrownBy(() -> service.analyzeWithOutcome(userId, projectId, null))
            .isInstanceOf(ProjectCapabilityService.CapabilityAnalysisException.class);

        verify(cardRepository, never()).deleteByProjectIdAndStatus(any(), any());
    }

    @Test
    void successfulReplacementNeverDeletesConfirmedCards() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = prepareInput(userId);
        String content = "{\"cards\":[{\"title\":\"扫描范围恢复\",\"summary\":\"从确认点恢复待整理变化。\",\"sources\":[\"S1\"]}]}";
        when(modelGatewayService.callStructured(any(), any(), any(com.projectflow.service.ModelTaskType.class))).thenReturn(
            new ModelGatewayService.StructuredModelResponse(content, outputAdapter.parse(content))
        );
        when(cardRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectCapabilityService service = service();

        UUID jobId = UUID.randomUUID();
        var outcome = service.analyzeWithOutcome(userId, projectId, jobId);

        verify(cardRepository).deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.CANDIDATE);
        verify(cardRepository).deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.NEEDS_EVIDENCE);
        verify(cardRepository, never()).deleteByProjectIdAndStatus(projectId, CapabilityCardStatus.CONFIRMED);
        assertThat(outcome.cards()).allSatisfy(card -> {
            assertThat(card.analysisJobId()).isEqualTo(jobId);
            assertThat(card.legacyResult()).isFalse();
        });
    }

    private UUID prepareInput(UUID userId) {
        ProjectSpace project = new ProjectSpace(userId);
        UUID projectId = project.getId();
        ProjectSediment sediment = new ProjectSediment(projectId);
        sediment.updateCore(
            "扫描范围恢复", "从确认点读取变化。", "减少重复扫描。", "CAPABILITY",
            List.of(UUID.randomUUID().toString()), List.of("commit:abc", "file:backend/Scan.java")
        );
        sediment.recordConfirmation(UUID.randomUUID(), List.of("backend/Scan.java"), "MODEL_RESULT", "PASS");
        AiProvider provider = new AiProvider(userId);
        provider.update("DeepSeek", "https://api.example.com", "test-key", "model", AiProviderType.DEEPSEEK, 0.2, 4000, true, List.of("analysis"));
        when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
        when(sedimentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of(sediment));
        lenient().when(sedimentRepository.findAllById(any())).thenReturn(List.of(sediment));
        when(providerRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider));
        return projectId;
    }

    private ProjectCapabilityService service() {
        return new ProjectCapabilityService(
            projectRepository, sedimentRepository, cardRepository, providerRepository, modelGatewayService,
            outputAdapter, jobRepository, transactionManager
        );
    }
}
