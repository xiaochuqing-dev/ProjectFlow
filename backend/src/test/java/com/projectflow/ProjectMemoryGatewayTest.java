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
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCoverage;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewContent;
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
import com.projectflow.entity.ProjectHistorySnapshot;
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
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactFileRefRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectMemoryReadAuditRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeFactRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.service.ProjectFactMigrationService;
import com.projectflow.service.ProjectAgentHistoryService;
import com.projectflow.service.ProjectHistoryCorrectionService;
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
    @Autowired ProjectHistorySnapshotRepository historySnapshotRepository;
    @Autowired ProjectAgentCandidateRepository candidateRepository;
    @Autowired ProjectSedimentRepository sedimentRepository;
    @Autowired ProjectMemoryGatewayService gateway;
    @Autowired ProjectAgentHistoryService agentHistory;
    @Autowired ProjectHistoryCorrectionService historyCorrectionService;
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
    String historyStoryId;

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

        historyStoryId = "story-fact-cursor";
        ChangeStory historyStory = new ChangeStory(
            historyStoryId, "fact-cursor", "新增事实游标并形成稳定增量边界", "事实游标避免重试制造重复事实。",
            "此前没有稳定的增量事实边界。", "来源记录显示新增并验证了事实游标。", "事实游标已经成为增量读取边界。",
            List.of("项目记忆"), "", List.of(), "随后继续用于统一项目记忆读取。", List.of(), List.of("变更原因保持 UNKNOWN。"),
            OCCURRED, OCCURRED, 1, 1, "ENGINEERING_GROUPING", "DETERMINISTIC",
            "FULL_WITHIN_DISCOVERED_SOURCES", List.of(), List.of(UUID.randomUUID()), List.of("fact:" + fact.getId())
        );
        HistoryChapter historyChapter = new HistoryChapter(
            "chapter-fact-cursor", "2026-07：事实游标形成", "这一时间区间形成了稳定的事实游标边界。",
            OCCURRED, OCCURRED, List.of("EARLIEST_DISCOVERED_EVENT"), List.of(historyStoryId), 1, 1,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        EvolutionThread historyThread = new EvolutionThread(
            "thread-fact-cursor", "fact-cursor", "事实游标", "PROJECT_SUBJECT", List.of(historyStoryId),
            List.of("CREATED"), historyStory.afterState(), List.of(), List.of(), historyStory.unknowns(), 1, capability.getId()
        );
        HistoryCoverage historyCoverage = new HistoryCoverage(
            true, "CURRENT", 1, 1, 0, 0, java.util.Map.of("PROJECT_FACT", 1), List.of(), List.of()
        );
        HistoryOverviewContent historyOverview = new HistoryOverviewContent(
            "最早可确认事实游标尚未存在。", "最近可确认事实游标已经形成稳定增量边界。",
            List.of(new com.projectflow.dto.ProjectHistoryDtos.HistoryChapterSummary(
                historyChapter.id(), historyChapter.title(), historyChapter.summary(), historyChapter.from(), historyChapter.to(),
                historyChapter.storyCount(), historyChapter.rawEventCount(), historyChapter.authority()
            )),
            List.of(historyStory.humanTitle()), List.of(), historyStory.unknowns()
        );
        ProjectHistorySnapshot historySnapshot = new ProjectHistorySnapshot(project.getId());
        historySnapshot.complete(
            "git:test", "history-fingerprint", 1, OCCURRED, OCCURRED,
            "project-history-v1", "project-history-synthesis-v1",
            objectMapper.writeValueAsString(historyOverview), objectMapper.writeValueAsString(List.of(historyChapter)),
            objectMapper.writeValueAsString(List.of(historyStory)), objectMapper.writeValueAsString(List.of(historyThread)),
            objectMapper.writeValueAsString(historyCoverage), "{}", UUID.randomUUID(), false
        );
        historySnapshotRepository.saveAndFlush(historySnapshot);
    }

    @Test
    void snapshotAndTimelinePreserveOccurrenceTimeWhenAnalysisIsLater() {
        var snapshot = gateway.snapshot(owner.userId(), project.getId());
        assertThat(snapshot.latestFactAt()).isEqualTo(OCCURRED);
        assertThat(snapshot.health().latestRealChangeAt()).isEqualTo(OCCURRED);
        assertThat(snapshot.health().latestAnalysisAt()).isEqualTo(ANALYZED);
        assertThat(snapshot.health().projectHistoryStatus()).isEqualTo("READY");
        assertThat(snapshot.projectHistory().overview().currentState()).contains("事实游标");
        assertThat(snapshot.recentChanges().items()).singleElement()
            .satisfies(item -> assertThat(item.time().eventAt()).isEqualTo(OCCURRED));
        assertThat(snapshot.representativeCapabilities()).singleElement()
            .satisfies(item -> assertThat(item.stale()).isTrue());
        String brief = gateway.brief(owner.userId(), project.getId(), 6_000).contextText();
        assertThat(brief).contains("项目历程：READY", "当前状态：最近可确认事实游标");
        assertThat(brief.indexOf("项目历程：")).isLessThan(brief.indexOf("主要能力："));

        var july = gateway.timeline(owner.userId(), project.getId(), "MONTH", "2026-07", null, null, 0, 20, "compact");
        assertThat(july.period().facts().items()).singleElement();
        assertThat(july.period().summary().status()).isEqualTo("FAILED");
        assertThat(july.period().summary().summary()).contains("七月");
        assertThat(july.period().summary().epistemicStatus()).isEqualTo("INFERRED");
        assertThat(july.period().summary().authority()).isEqualTo("NON_AUTHORITATIVE");
        assertThat(july.period().facts().items().get(0).time().eventAt()).isEqualTo(OCCURRED);
    }

    @Test
    void exposesWarningsForEveryNonReadyProjectHistoryState() {
        ProjectHistorySnapshot ready = historySnapshotRepository.findByProjectId(project.getId()).orElseThrow();
        historySnapshotRepository.deleteAll();
        ProjectHistorySnapshot notInitialized = historySnapshotRepository.saveAndFlush(new ProjectHistorySnapshot(project.getId()));
        assertThat(gateway.snapshot(owner.userId(), project.getId()).health().warnings())
            .contains("项目历程尚未显式刷新");

        notInitialized.begin(UUID.randomUUID(), false);
        historySnapshotRepository.saveAndFlush(notInitialized);
        assertThat(gateway.snapshot(owner.userId(), project.getId()).health().warnings())
            .contains("项目历程正在刷新，当前读取结果可能尚未生成");

        historySnapshotRepository.deleteAll();
        ProjectHistorySnapshot stale = new ProjectHistorySnapshot(project.getId());
        stale.complete(
            ready.getProjectRevision(), ready.getSourceEventFingerprint(), ready.getSourceEventCount(),
            ready.getEarliestEventAt(), ready.getLatestEventAt(), ready.getStrategyVersion(), ready.getPromptVersion(),
            ready.getOverviewJson(), ready.getChaptersJson(), ready.getStoriesJson(), ready.getThreadsJson(),
            ready.getCoverageJson(), ready.getDiagnosticsJson(), UUID.randomUUID(), false
        );
        stale.begin(UUID.randomUUID(), true);
        historySnapshotRepository.saveAndFlush(stale);
        assertThat(gateway.snapshot(owner.userId(), project.getId()).health().warnings())
            .contains("项目历程来源已变化，当前返回上次成功快照");

        stale.fail("HISTORY_FAILED", "refresh failed", "{}", UUID.randomUUID());
        historySnapshotRepository.saveAndFlush(stale);
        assertThat(gateway.snapshot(owner.userId(), project.getId()).health().warnings())
            .contains("项目历程存在来源缺口、失败保留或模型降级，当前结果仍可追溯");

        historySnapshotRepository.deleteAll();
        ProjectHistorySnapshot failed = new ProjectHistorySnapshot(project.getId());
        failed.fail("HISTORY_FAILED", "refresh failed", "{}", UUID.randomUUID());
        historySnapshotRepository.saveAndFlush(failed);
        assertThat(gateway.snapshot(owner.userId(), project.getId()).health().warnings())
            .contains("项目历程刷新失败且没有可用成功快照");
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

        mockMvc.perform(get("/api/projects/" + project.getId() + "/project-memory/history/stories")
                .header("Authorization", "Bearer " + owner.token())
                .header("X-ProjectFlow-Caller", "hermes-local-test")
                .queryParam("subject", "fact-cursor")
                .queryParam("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(historyStoryId));

        var audits = auditRepository.findTop50ByProjectIdOrderByOccurredAtDesc(project.getId());
        assertThat(audits).isNotEmpty();
        var audit = audits.stream().filter(item -> item.getOperationName().equals("search_project_memory"))
            .findFirst().orElseThrow();
        assertThat(audit.getOperationName()).isEqualTo("search_project_memory");
        assertThat(audit.getQueryLength()).isEqualTo("factcursor".length());
        assertThat(audit.getQueryHash()).hasSize(64);
        assertThat(objectMapper.valueToTree(audit).toString()).doesNotContain("FactCursor", "hermes-local-test");
        assertThat(audits).anySatisfy(item -> assertThat(item.getOperationName()).isEqualTo("list_project_change_stories"));

        assertThatThrownBy(() -> gateway.snapshot(UUID.randomUUID(), project.getId()))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> gateway.historyOverview(UUID.randomUUID(), project.getId()))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> gateway.traceFact(owner.userId(), otherProject.getId(), fact.getId(), "compact"))
            .isInstanceOf(AppException.class);
    }

    @Test
    void agentCandidateCannotWriteStrongFactOrReferenceAnotherProject() throws Exception {
        long factsBefore = factRepository
            .findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(project.getId()).size();

        mockMvc.perform(post("/api/projects/" + project.getId() + "/agent-candidates")
                .header("Authorization", "Bearer " + owner.token())
                .header("X-ProjectFlow-Caller", "agent-contract-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "candidateType":"ASSERTION",
                      "assertion":"Agent 认为事务边界需要复核",
                      "epistemicStatus":"INFERRED",
                      "evidenceRefs":["fact:%s"],
                      "currentness":"CURRENT",
                      "sourceRevision":"test-revision"
                    }
                    """.formatted(fact.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.epistemicStatus").value("INFERRED"))
            .andExpect(jsonPath("$.data.validationStatus").value("PENDING_ENGINEERING_VALIDATION"));

        mockMvc.perform(post("/api/projects/" + project.getId() + "/agent-candidates")
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "candidateType":"ASSERTION",
                      "assertion":"Agent 不能直接声明强事实",
                      "epistemicStatus":"VERIFIED",
                      "evidenceRefs":["fact:%s"]
                    }
                    """.formatted(fact.getId())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/" + project.getId() + "/agent-candidates")
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "candidateType":"ASSERTION",
                      "assertion":"未知项目证据必须被拒绝",
                      "epistemicStatus":"UNKNOWN",
                      "evidenceRefs":["fact:%s"]
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());

        assertThat(candidateRepository.findTop100ByProjectIdOrderByCreatedAtDesc(project.getId()))
            .singleElement();
        assertThat(factRepository.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(project.getId()))
            .hasSize((int) factsBefore);
    }

    @Test
    void projectCatalogAndContextPackageStayOwnedBoundedAndAudited() throws Exception {
        var catalog = agentHistory.catalog(owner.userId());
        assertThat(catalog.items()).extracting(item -> item.projectId())
            .containsExactly(project.getId())
            .doesNotContain(otherProject.getId());

        var context = agentHistory.contextPackage(owner.userId(), project.getId(), 2_000);
        assertThat(context.packageVersion()).isEqualTo("projectflow-agent-context-v2");
        assertThat(context.actualCharacters()).isLessThanOrEqualTo(context.sizeBudget());
        assertThat(context.currentStrongFacts()).singleElement()
            .satisfies(item -> assertThat(item.epistemicStatus()).isEqualTo("OBSERVED"));
        assertThat(objectMapper.valueToTree(context).toString())
            .doesNotContain("C:/private", "rawResponse", "reasoning", "Authorization");

        mockMvc.perform(get("/api/projects/" + project.getId() + "/project-memory/context-package")
                .header("Authorization", "Bearer " + owner.token())
                .header("X-ProjectFlow-Caller", "agent-context-test")
                .queryParam("sizeBudget", "2000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.packageVersion").value("projectflow-agent-context-v2"));

        mockMvc.perform(get("/api/projects/" + otherProject.getId() + "/project-memory/context-package")
                .header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isNotFound());

        assertThat(auditRepository.findTop50ByProjectIdOrderByOccurredAtDesc(project.getId()))
            .anySatisfy(audit -> assertThat(audit.getOperationName()).isEqualTo("get_project_context_package"));
    }

    @Test
    void gatewayAndAgentContextExposeTheSameCorrectedPresentationWithoutInternalEnums() {
        String revision = gateway.historyCorrections(owner.userId(), project.getId()).presentationRevision();
        historyCorrectionService.create(owner.userId(), project.getId(), new HistoryCorrectionRequest(
            "RENAME_STORY", "STORY", historyStoryId, List.of(), "让事实游标避免重复记录", "", "", "",
            revision, ""
        ));

        var gatewayStories = gateway.historyStories(
            owner.userId(), project.getId(), null, false, null, null, 0, 20
        );
        assertThat(gatewayStories.items()).singleElement().satisfies(story -> {
            assertThat(story.humanTitle()).isEqualTo("让事实游标避免重复记录");
            assertThat(story.presentationAuthority()).isEqualTo("USER_DECLARED_PRESENTATION");
        });

        var context = agentHistory.contextPackage(owner.userId(), project.getId(), 12_000);
        assertThat(context.historicalCoverage())
            .contains("让事实游标避免重复记录", "经过用户修改", "主要变化")
            .doesNotContain("USER_DECLARED_PRESENTATION", "PRIMARY", "ENGINEERING_GROUPING", "DETERMINISTIC");
        assertThat(factRepository.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(project.getId()))
            .singleElement();
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
