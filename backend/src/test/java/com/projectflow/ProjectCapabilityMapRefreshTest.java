package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ModelGatewayService;
import com.projectflow.service.ModelOutputAdapter;
import com.projectflow.service.ProjectCapabilityMapService;
import com.projectflow.service.ProjectCapabilityQueryService;

@SpringBootTest
@ActiveProfiles("test")
class ProjectCapabilityMapRefreshTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectCapabilityRepository capabilityRepository;
    @Autowired ProjectCapabilityEvolutionRepository evolutionRepository;
    @Autowired AiProviderRepository providerRepository;
    @Autowired ProjectCapabilityMapService mapService;
    @Autowired ProjectCapabilityQueryService queryService;
    @Autowired ModelOutputAdapter outputAdapter;
    @MockitoBean ModelGatewayService modelGateway;

    @Test
    void bootstrapIncrementalNoChangeAndFailurePreserveStableMap() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId);
        provider(userId);
        ProjectFact first = fact(project.getId(), "模型失败保护", "2026-01-01T00:00:00Z");
        when(modelGateway.callStructured(any(), any(), any())).thenReturn(response("""
            {"operations":[{"type":"NEW_CAPABILITY","temporaryKey":"TMP-1","canonicalName":"模型调用可靠性治理","summary":"统一保护模型调用结果。","problemSolved":"模型失败不能覆盖旧结果","longTermValue":"持续保持模型入口可靠","productAreas":["模型"],"factIds":["%s"],"evolutionTitle":"形成模型可靠性治理","evolutionSummary":"建立失败保护"}],"noCapabilityChangeFactIds":[],"attentionFacts":[]}
            """.formatted(first.getId())));

        runRefresh(userId, project.getId());

        var bootstrap = queryService.overview(userId, project.getId());
        ProjectCapability capability = capabilityRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()).get(0);
        UUID stableId = capability.getId();
        assertThat(bootstrap.mapStatus()).isEqualTo("READY");
        assertThat(bootstrap.coveredFactCount()).isEqualTo(1);
        assertThat(capability.getCurrentVersion()).isEqualTo(1);

        ProjectFact second = fact(project.getId(), "统一模型恢复", "2026-03-01T00:00:00Z");
        when(modelGateway.callStructured(any(), any(), any())).thenReturn(response("""
            {"operations":[{"type":"ENHANCE_CAPABILITY","capabilityId":"%s","canonicalName":"模型调用可靠性治理","summary":"增加结构修复和失败恢复。","problemSolved":"模型失败不能覆盖旧结果","longTermValue":"持续保持模型入口可靠","productAreas":["模型"],"factIds":["%s"],"evolutionTitle":"增强模型恢复","evolutionSummary":"加入结构修复"}],"noCapabilityChangeFactIds":[],"attentionFacts":[]}
            """.formatted(stableId, second.getId())));
        runRefresh(userId, project.getId());

        ProjectCapability enhanced = capabilityRepository.findById(stableId).orElseThrow();
        assertThat(enhanced.getCurrentVersion()).isEqualTo(2);
        assertThat(enhanced.getSourceFactCount()).isEqualTo(2);
        assertThat(evolutionRepository.countByCapabilityId(stableId)).isEqualTo(2);

        ProjectFact maintenance = fact(project.getId(), "更新维护文档", "2026-03-02T00:00:00Z");
        when(modelGateway.callStructured(any(), any(), any())).thenReturn(response("""
            {"operations":[],"noCapabilityChangeFactIds":["%s"],"attentionFacts":[]}
            """.formatted(maintenance.getId())));
        runRefresh(userId, project.getId());
        var noChange = queryService.overview(userId, project.getId());
        assertThat(noChange.coveredFactCount()).isEqualTo(3);
        assertThat(noChange.noCapabilityChangeFactCount()).isEqualTo(1);
        assertThat(capabilityRepository.findById(stableId).orElseThrow().getSourceFactCount()).isEqualTo(2);

        fact(project.getId(), "新增模型证据", "2026-03-03T00:00:00Z");
        mapService.markDirty(project.getId(), false);
        String failedScope = mapService.nextDirtyScope(project.getId());
        when(modelGateway.callStructured(any(), any(), any())).thenThrow(new IllegalStateException("controlled failure"));
        assertThatThrownBy(() -> mapService.refresh(userId, project.getId(), UUID.randomUUID(), failedScope))
            .isInstanceOf(IllegalStateException.class);
        var failed = queryService.overview(userId, project.getId());
        assertThat(failed.mapStatus()).isEqualTo("READY_STALE");
        assertThat(failed.stale()).isTrue();
        assertThat(capabilityRepository.findById(stableId)).isPresent();
        assertThat(factRepository.countByProjectId(project.getId())).isEqualTo(4);
    }

    @Test
    void highRiskMergeBecomesAttentionWithoutDeletingCapabilities() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId);
        provider(userId);
        ProjectCapability one = capability(project.getId(), "本地启动可靠性", "不同问题一");
        ProjectCapability two = capability(project.getId(), "模型启动可靠性", "不同问题二");
        ProjectFact fact = fact(project.getId(), "相似名称核对", "2026-04-01T00:00:00Z");
        when(modelGateway.callStructured(any(), any(), any())).thenReturn(response("""
            {"operations":[{"type":"MERGE_CAPABILITY","capabilityId":"%s","mergeIntoCapabilityId":"%s","factIds":["%s"],"evolutionTitle":"尝试合并","evolutionSummary":"名称相似"}],"noCapabilityChangeFactIds":[],"attentionFacts":[]}
            """.formatted(one.getId(), two.getId(), fact.getId())));

        runRefresh(userId, project.getId());

        assertThat(capabilityRepository.findById(one.getId()).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        assertThat(capabilityRepository.findById(two.getId()).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        var overview = queryService.overview(userId, project.getId());
        assertThat(overview.attentionCount()).isEqualTo(1);
        assertThat(overview.coveredFactCount()).isEqualTo(1);
    }

    @Test
    void retriesOneBoundedCoverageRepairWhenProviderOmitsFacts() throws Exception {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId);
        provider(userId);
        ProjectFact first = fact(project.getId(), "能力覆盖修复", "2026-05-01T00:00:00Z");
        ProjectFact second = fact(project.getId(), "说明文字更新", "2026-05-02T00:00:00Z");
        when(modelGateway.callStructured(any(), any(), any())).thenReturn(
            response("""
                {"operations":[{"type":"NEW_CAPABILITY","temporaryKey":"TMP-REPAIR","canonicalName":"模型覆盖修复","summary":"修复遗漏分类。","problemSolved":"Provider 遗漏 fact","longTermValue":"保持完整覆盖","productAreas":["模型"],"factIds":["%s"],"evolutionTitle":"形成覆盖修复","evolutionSummary":"完整分类"}],"noCapabilityChangeFactIds":[],"attentionFacts":[]}
                """.formatted(first.getId())),
            response("""
                {"operations":[{"type":"NEW_CAPABILITY","temporaryKey":"TMP-REPAIR","canonicalName":"模型覆盖修复","summary":"修复遗漏分类。","problemSolved":"Provider 遗漏 fact","longTermValue":"保持完整覆盖","productAreas":["模型"],"factIds":["%s"],"evolutionTitle":"形成覆盖修复","evolutionSummary":"完整分类"}],"noCapabilityChangeFactIds":["%s"],"attentionFacts":[]}
                """.formatted(first.getId(), second.getId()))
        );

        runRefresh(userId, project.getId());

        assertThat(queryService.overview(userId, project.getId()).coveredFactCount()).isEqualTo(2);
        assertThat(queryService.overview(userId, project.getId()).noCapabilityChangeFactCount()).isEqualTo(1);
        verify(modelGateway, times(2)).callStructured(any(), any(), any());
    }

    private void runRefresh(UUID userId, UUID projectId) throws Exception {
        mapService.markDirty(projectId, false);
        String scope = mapService.nextDirtyScope(projectId);
        assertThat(scope).isNotBlank();
        UUID jobId = UUID.randomUUID();
        mapService.markQueued(scope, jobId);
        mapService.refresh(userId, projectId, jobId, scope);
    }

    private ModelGatewayService.StructuredModelResponse response(String content) throws Exception {
        return new ModelGatewayService.StructuredModelResponse(content, outputAdapter.parse(content));
    }

    private ProjectSpace project(UUID userId) {
        ProjectSpace project = new ProjectSpace(userId);
        project.update("能力刷新 " + UUID.randomUUID(), "test", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        return projectRepository.saveAndFlush(project);
    }

    private void provider(UUID userId) {
        AiProvider provider = new AiProvider(userId);
        provider.update("fixed", "http://127.0.0.1", "test-key", "fixed", AiProviderType.OPENAI_COMPATIBLE, 0.1, 8000, true, List.of());
        providerRepository.saveAndFlush(provider);
    }

    private ProjectFact fact(UUID projectId, String title, String time) {
        Instant occurred = Instant.parse(time);
        ProjectFact fact = new ProjectFact(projectId, UUID.randomUUID(), UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        fact.updateContent(
            title, title + " 已完成", List.of(title), "用户可以看到结果", occurred, occurred,
            List.of(UUID.randomUUID().toString().replace("-", "")), List.of(), List.of(), List.of("src/changed.java"),
            List.of("commit:evidence", "file:src/changed.java"), "MODEL", "PASS", EvidenceConfidence.HIGH,
            ProjectFactRecordStatus.RECORDED, ""
        );
        fact.assignTimeline(occurred, time.substring(0, 10), "2026-W01", time.substring(0, 7));
        return factRepository.saveAndFlush(fact);
    }

    private ProjectCapability capability(UUID projectId, String name, String problem) {
        String identity = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        ProjectCapability capability = new ProjectCapability(projectId, identity, identity);
        capability.initialize(name, name, problem, "长期价值", List.of(name), Instant.parse("2026-01-01T00:00:00Z"), "MODEL", "fixed", "fixed", UUID.randomUUID());
        return capabilityRepository.saveAndFlush(capability);
    }
}
