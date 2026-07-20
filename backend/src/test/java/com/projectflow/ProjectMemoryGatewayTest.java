package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityEvolutionType;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactAgentResultRef;
import com.projectflow.entity.ProjectFactCommitRef;
import com.projectflow.entity.ProjectFactFileRef;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.ProjectTimelineThemeFact;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactFileRefRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryReadAuditRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeFactRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.service.ProjectFactMigrationService;
import com.projectflow.service.ProjectMemoryGatewayService;
import com.projectflow.service.TimelinePeriodResolver;
import com.projectflow.support.AppException;

@SpringBootTest(properties = {
    "projectflow.auth.required=true",
    "projectflow.timeline.zone=Asia/Shanghai"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectMemoryGatewayTest {
    private static final Instant OCCURRED = Instant.parse("2026-07-17T09:30:00Z");
    private static final Instant ANALYZED = Instant.parse("2026-08-20T11:00:00Z");

    @Autowired ProjectRepository projectRepository;
    @Autowired ChangeBatchRepository batchRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectFactCommitRefRepository commitRepository;
    @Autowired ProjectFactFileRefRepository fileRepository;
    @Autowired ProjectFactAgentResultRefRepository agentRepository;
    @Autowired ProjectCapabilityRepository capabilityRepository;
    @Autowired ProjectCapabilityEvolutionRepository evolutionRepository;
    @Autowired ProjectCapabilityFactRepository capabilityFactRepository;
    @Autowired ProjectCapabilityMapStateRepository capabilityStateRepository;
    @Autowired ProjectTimelineSummaryRepository summaryRepository;
    @Autowired ProjectTimelineThemeRepository themeRepository;
    @Autowired ProjectTimelineThemeFactRepository themeFactRepository;
    @Autowired ProjectMemoryReadAuditRepository auditRepository;
    @Autowired ProjectSedimentRepository sedimentRepository;
    @Autowired ProjectMemoryGatewayService gateway;
    @Autowired ProjectFactMigrationService migrationService;
    @Autowired TimelinePeriodResolver resolver;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    Identity owner;
    ProjectSpace project;
    ProjectSpace otherProject;
    ChangeBatch batch;
    ProjectFact fact;
    ProjectCapability capability;
    ProjectCapabilityEvolution evolution;

    @BeforeEach
    void setUp() throws Exception {
        owner = register();
        project = project(owner.userId(), "Project Memory Gateway");
        otherProject = project(UUID.randomUUID(), "Other owner project");
        batch = batch(project.getId(), OCCURRED, ANALYZED);
        fact = fact(project.getId(), batch.getId(), OCCURRED, "FactCursor 解决重复分析边界");
        commitRepository.save(new ProjectFactCommitRef(project.getId(), fact.getId(), "1717171717171717171717171717171717171717"));
        fileRepository.save(new ProjectFactFileRef(project.getId(), fact.getId(), "C:/private/work/Secret.java"));
        agentRepository.save(new ProjectFactAgentResultRef(project.getId(), fact.getId(), "C:/private/results/result.json"));

        capability = capability(project.getId());
        evolution = new ProjectCapabilityEvolution(
            project.getId(), capability.getId(), ProjectCapabilityEvolutionType.NEW_CAPABILITY, 0, 1,
            "FactCursor 能力形成", "通过真实事实建立稳定增量分析边界。", OCCURRED,
            UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        );
        evolution.attachSourceStats(1, 1, List.of("2026-07"), null, "fixed", "fixed");
        evolution = evolutionRepository.saveAndFlush(evolution);
        capabilityFactRepository.saveAndFlush(new ProjectCapabilityFact(
            project.getId(), capability.getId(), fact.getId(), ProjectCapabilityRelationRole.FORMATION, evolution.getId()
        ));
        capability.updateStatistics(1, 1, 1, 2, 0, 1,
            com.projectflow.entity.ProjectCapabilityMaturity.FORMED,
            "已有独立事实和提交证据。", OCCURRED, OCCURRED);
        capabilityRepository.saveAndFlush(capability);
        ProjectCapabilityMapState state = new ProjectCapabilityMapState(project.getId());
        state.complete(1, 1, 1, 0, 0, "test-fingerprint", OCCURRED, null);
        state.markFailed("TEST_FAILURE", "保留上次成功能力地图", null);
        capabilityStateRepository.saveAndFlush(state);

        var range = resolver.resolve(TimelineGranularity.MONTH, "2026-07");
        ProjectTimelineSummary summary = new ProjectTimelineSummary(
            project.getId(), TimelineGranularity.MONTH, "2026-07",
            range.startInclusive(), range.endExclusive(), resolver.zoneId()
        );
        summary.markDirty(1, "july", fact.getUpdatedAt());
        summary.complete("七月形成了稳定的事实游标边界。", 1, "fixed", "fixed", null);
        summary.markFailed("TEST_FAILURE", "模拟摘要刷新失败", null);
        summary = summaryRepository.saveAndFlush(summary);
        ProjectTimelineTheme theme = themeRepository.saveAndFlush(new ProjectTimelineTheme(
            summary.getId(), project.getId(), "FactCursor 形成背景", "真实事实支持的七月主题。", 0
        ));
        themeFactRepository.saveAndFlush(new ProjectTimelineThemeFact(project.getId(), theme.getId(), fact.getId()));
    }

    @Test
    void snapshotAndTimelinePreserveOccurrenceTimeWhenAnalysisIsLater() {
        var snapshot = gateway.snapshot(owner.userId(), project.getId());
        assertThat(snapshot.latestFactAt()).isEqualTo(OCCURRED);
        assertThat(snapshot.health().latestRealChangeAt()).isEqualTo(OCCURRED);
        assertThat(snapshot.health().latestAnalysisAt()).isEqualTo(ANALYZED);
        assertThat(snapshot.recentChanges().items()).singleElement()
            .satisfies(item -> assertThat(item.time().eventAt()).isEqualTo(OCCURRED));
        assertThat(snapshot.representativeCapabilities()).singleElement()
            .satisfies(item -> assertThat(item.stale()).isTrue());

        var july = gateway.timeline(owner.userId(), project.getId(), "MONTH", "2026-07", null, null, 0, 20, "compact");
        assertThat(july.period().facts().items()).singleElement();
        assertThat(july.period().summary().status()).isEqualTo("FAILED");
        assertThat(july.period().summary().summary()).contains("七月");
        assertThat(july.period().facts().items().get(0).time().eventAt()).isEqualTo(OCCURRED);
    }

    @Test
    void unifiedSearchKeepsEntityTypesAndSourceDerivedLayersDistinct() {
        var search = gateway.search(owner.userId(), project.getId(), "FactCursor", null, null, null, 0, 20, "compact");
        assertThat(search.items()).extracting(item -> item.entityType())
            .contains("FACT", "TIMELINE_THEME", "CAPABILITY", "EVOLUTION");
        assertThat(search.items().stream().filter(item -> item.entityType().equals("FACT")).findFirst().orElseThrow().truthLayer())
            .isEqualTo("FACTUAL_SOURCE");
        assertThat(search.items().stream().filter(item -> item.entityType().equals("TIMELINE_THEME")).findFirst().orElseThrow().truthLayer())
            .startsWith("DERIVED_FROM_FACTS");
        assertThat(search.items()).allSatisfy(item -> assertThat(item.relevanceReason()).startsWith("匹配"));
    }

    @Test
    void traceIsBoundedAndDoesNotExposeAbsolutePathsOrInternalFingerprints() {
        var trace = gateway.traceFact(owner.userId(), project.getId(), fact.getId(), "compact");
        assertThat(trace.files()).containsExactly("[路径已隐藏]/Secret.java");
        assertThat(trace.agentResults()).containsExactly("[路径已隐藏]/result.json");
        assertThat(trace.files()).noneMatch(value -> value.contains("C:/private"));
        assertThat(objectMapper.valueToTree(trace).toString()).doesNotContain("factFingerprint", "rawResponse", "reasoning");
    }

    @Test
    void apiIsPagedOwnedAndStoresPrivacySafeAuditOnly() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/project-memory/search")
                .header("Authorization", "Bearer " + owner.token())
                .header("X-ProjectFlow-Caller", "hermes-local-test")
                .queryParam("query", "FactCursor")
                .queryParam("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.hasMore").value(true));

        var audits = auditRepository.findTop50ByProjectIdOrderByOccurredAtDesc(project.getId());
        assertThat(audits).isNotEmpty();
        var audit = audits.get(0);
        assertThat(audit.getOperationName()).isEqualTo("search_project_memory");
        assertThat(audit.getQueryLength()).isEqualTo("factcursor".length());
        assertThat(audit.getQueryHash()).hasSize(64);
        assertThat(objectMapper.valueToTree(audit).toString()).doesNotContain("FactCursor", "hermes-local-test");

        assertThatThrownBy(() -> gateway.snapshot(UUID.randomUUID(), project.getId()))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> gateway.traceFact(owner.userId(), otherProject.getId(), fact.getId(), "compact"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void legacySedimentUsesOwnedSourceBatchOccurrenceInsteadOfLateRecordTime() {
        ProjectSediment sediment = new ProjectSediment(project.getId());
        sediment.updateCore(
            "旧沉淀时间回归", "有来源批次的旧事实", "保留真实发生时间", "PROJECT_CAPABILITY",
            List.of(), List.of("file:src/legacy-time.java")
        );
        sediment.recordConfirmation(batch.getId(), List.of("src/legacy-time.java"), "LEGACY", "PASS");
        sediment = sedimentRepository.saveAndFlush(sediment);

        migrationService.migrateOnStartup();

        UUID sedimentId = sediment.getId();
        ProjectFact migrated = factRepository.findAll().stream()
            .filter(item -> sedimentId.equals(item.getLegacySedimentId())).findFirst().orElseThrow();
        assertThat(migrated.getOccurredFrom()).isEqualTo(OCCURRED);
        assertThat(migrated.getTimelineMonthKey()).isEqualTo("2026-07");
    }

    private Identity register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        JsonNode data = objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"memory-" + unique.substring(0, 8) + "\",\"email\":\"" + unique
                    + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        return new Identity(data.path("accessToken").asText(), UUID.fromString(data.at("/user/id").asText()));
    }

    private ProjectSpace project(UUID userId, String name) {
        ProjectSpace value = new ProjectSpace(userId);
        value.update(name, "自动维护长期项目记忆", ProjectStatus.BUILDING,
            List.of("Spring Boot", "Python"), "", LocalDate.of(2026, 1, 1), null);
        return projectRepository.saveAndFlush(value);
    }

    private ChangeBatch batch(UUID projectId, Instant occurredAt, Instant analyzedAt) {
        ChangeBatch value = new ChangeBatch(
            projectId, "1616161616161616161616161616161616161616",
            "2020202020202020202020202020202020202020", "master", false
        );
        value.complete(1, 1, 1, List.of());
        value.markFactsRecorded(1, 0, occurredAt, occurredAt);
        ReflectionTestUtils.setField(value, "scanStartedAt", analyzedAt.minusSeconds(300));
        ReflectionTestUtils.setField(value, "scanFinishedAt", analyzedAt);
        return batchRepository.saveAndFlush(value);
    }

    private ProjectFact fact(UUID projectId, UUID batchId, Instant occurredAt, String title) {
        ProjectFact value = new ProjectFact(
            projectId, batchId, UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN,
            UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        );
        value.updateContent(
            title, "事实游标使用已成功记录的提交建立稳定增量边界。", List.of("新增 FactCursor"),
            "重复读取和重试不会制造重复事实", occurredAt, occurredAt,
            List.of("1717171717171717171717171717171717171717"), List.of(),
            List.of("agent-result:fact-cursor"), List.of("src/FactCursor.java"),
            List.of("commit:1717171717171717171717171717171717171717"), "MODEL", "PASS",
            EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        var assignment = resolver.assign(value);
        value.assignTimeline(assignment.eventAt(), assignment.dayKey(), assignment.weekKey(), assignment.monthKey());
        ReflectionTestUtils.setField(value, "createdAt", ANALYZED);
        ReflectionTestUtils.setField(value, "updatedAt", ANALYZED);
        return factRepository.saveAndFlush(value);
    }

    private ProjectCapability capability(UUID projectId) {
        ProjectCapability value = new ProjectCapability(
            projectId, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
            UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        );
        value.initialize(
            "稳定增量事实游标", "按成功事实边界继续分析。", "避免重复分析和事实重复", "形成稳定项目记忆主链",
            List.of("项目记忆"), OCCURRED, "MODEL", "fixed", "fixed", null
        );
        value.addAlias("FactCursor");
        value.updateExpressions("可靠增量事实记录", "实现幂等事实游标", "解释事实边界设计");
        return capabilityRepository.saveAndFlush(value);
    }

    private record Identity(String token, UUID userId) {
    }
}
