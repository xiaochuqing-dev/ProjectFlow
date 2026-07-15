package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
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
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectAnalysisJob;
import com.projectflow.entity.ProjectAnalysisJobType;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.SedimentAction;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.service.ProjectSedimentService;
import com.projectflow.service.ProjectFactService;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataReadReliabilityTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ChangeBatchRepository batchRepository;
    @Autowired DevelopmentSegmentRepository segmentRepository;
    @Autowired ProjectChangeRepository changeRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectAnalysisJobRepository jobRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectSedimentService sedimentService;
    @Autowired ProjectFactService factService;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void legacyNullFieldsDoNotBreakBatchListOrDetail() throws Exception {
        Identity identity = register();
        UUID projectId = UUID.fromString(createProject(identity.token(), "Legacy batches"));
        ChangeBatch legacy = batch(projectId, "legacy", Instant.parse("2026-07-01T00:00:00Z"), "");
        for (String field : List.of(
            "modelStatus", "modelProvider", "segmentationMode", "fallbackReason", "analysisScope",
            "githubStatus", "remoteRelation", "scanFingerprint"
        )) {
            ReflectionTestUtils.setField(legacy, field, null);
        }
        legacy = batchRepository.saveAndFlush(legacy);

        DevelopmentSegment oldSegment = segment(projectId, legacy.getId(), "旧开发推进段");
        for (String field : List.of("generationMode", "modelProvider", "fallbackReason", "qualityStatus", "qualityReason")) {
            ReflectionTestUtils.setField(oldSegment, field, null);
        }
        segmentRepository.saveAndFlush(oldSegment);

        ProjectChange incompleteChange = change(projectId, legacy.getId(), null, "旧正式建议");
        ReflectionTestUtils.setField(incompleteChange, "contentSource", null);
        ReflectionTestUtils.setField(incompleteChange, "qualityStatus", null);
        ReflectionTestUtils.setField(incompleteChange, "recommendationStrength", null);
        ReflectionTestUtils.setField(incompleteChange, "evidenceRefs", null);
        changeRepository.saveAndFlush(incompleteChange);

        ChangeBatch latest = batch(projectId, "master", Instant.parse("2026-07-02T00:00:00Z"), "SUCCESS");
        latest = batchRepository.saveAndFlush(latest);

        mockMvc.perform(get("/api/projects/" + projectId + "/sediment-review-batches")
                .header("Authorization", "Bearer " + identity.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].batchId").value(latest.getId().toString()))
            .andExpect(jsonPath("$.data[1].batchId").value(legacy.getId().toString()))
            .andExpect(jsonPath("$.data[1].modelStatus").value(""))
            .andExpect(jsonPath("$.data[1].resultSource").value("LEGACY_INCOMPLETE"))
            .andExpect(jsonPath("$.data[1].formalSuggestionCount").value(1))
            .andExpect(jsonPath("$.data[1].localDraftCount").value(1));

        mockMvc.perform(get("/api/sediment-review-batches/" + legacy.getId())
                .header("Authorization", "Bearer " + identity.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.formalSuggestions", hasSize(1)))
            .andExpect(jsonPath("$.data.formalSuggestions[0].contentSource").value("LEGACY_UNKNOWN"))
            .andExpect(jsonPath("$.data.formalSuggestions[0].qualityStatus").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$.data.formalSuggestions[0].recommendationStrength").value("REFERENCE_ONLY"))
            .andExpect(jsonPath("$.data.formalSuggestions[0].evidenceCount").value(0))
            .andExpect(jsonPath("$.data.localDrafts", hasSize(1)))
            .andExpect(jsonPath("$.data.localDrafts[0].generationMode").value("LOCAL_RULE"));
    }

    @Test
    void fiftyBatchesUseBoundedQueries() throws Exception {
        Identity identity = register();
        UUID projectId = UUID.fromString(createProject(identity.token(), "Fifty batches"));
        List<ChangeBatch> batches = new ArrayList<>();
        for (int index = 0; index < 50; index += 1) {
            batches.add(batch(projectId, "batch-" + index, Instant.parse("2026-06-01T00:00:00Z").plusSeconds(index), "SUCCESS"));
        }
        batchRepository.saveAllAndFlush(batches);

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long startedAt = System.nanoTime();
        var result = sedimentService.listReviewBatches(identity.userId(), projectId);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        long queryCount = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        System.out.printf("V3381_METRIC sediment_50_batches_ms=%d queries=%d%n", elapsedMs, queryCount);

        assertThat(result).hasSize(50);
        assertThat(queryCount).isLessThanOrEqualTo(4);
        assertThat(elapsedMs).isLessThan(1_000);
    }

    @Test
    void projectFactScaleQueriesStayPagedAndBoundedAtOneAndFiveThousandFacts() throws Exception {
        Identity identity = register();
        UUID projectId = UUID.fromString(createProject(identity.token(), "Project fact scale"));
        List<ChangeBatch> batches = new ArrayList<>();
        Instant batchStart = Instant.parse("2025-01-01T00:00:00Z");
        for (int index = 0; index < 100; index += 1) {
            ChangeBatch batch = batch(projectId, "scale-" + index, batchStart.plusSeconds(index * 86_400L), "SUCCESS");
            batch.markFactsRecorded(10, 0, batchStart.plusSeconds(index), batchStart.plusSeconds(index + 1L));
            batches.add(batch);
        }
        batches = batchRepository.saveAllAndFlush(batches);
        seedFacts(projectId, batches, 0, 1_000);

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        Metric facts1000 = measure(statistics, () -> factService.listFacts(
            identity.userId(), projectId, null, null, null, null, 0, 20
        ));
        System.out.printf("V340_METRIC facts_1000_first_page_ms=%d queries=%d%n", facts1000.elapsedMs(), facts1000.queries());
        assertThat(facts1000.itemCount()).isEqualTo(20);
        assertThat(facts1000.totalElements()).isEqualTo(1_000);
        assertThat(facts1000.queries()).isLessThanOrEqualTo(3);
        assertThat(facts1000.elapsedMs()).isLessThan(1_000);

        seedFacts(projectId, batches, 1_000, 5_000);
        batches.forEach(batch -> batch.markFactsRecorded(50, 0, batch.getFactOccurredFrom(), batch.getFactOccurredTo()));
        batchRepository.saveAllAndFlush(batches);

        Metric facts5000 = measure(statistics, () -> factService.listFacts(
            identity.userId(), projectId, null, null, null, null, 0, 20
        ));
        System.out.printf("V340_METRIC facts_5000_first_page_ms=%d queries=%d%n", facts5000.elapsedMs(), facts5000.queries());
        assertThat(facts5000.itemCount()).isEqualTo(20);
        assertThat(facts5000.totalElements()).isEqualTo(5_000);
        assertThat(facts5000.queries()).isLessThanOrEqualTo(3);
        assertThat(facts5000.elapsedMs()).isLessThan(1_000);

        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long overviewStarted = System.nanoTime();
        var overview = factService.overview(identity.userId(), projectId);
        long overviewMs = (System.nanoTime() - overviewStarted) / 1_000_000;
        long overviewQueries = statistics.getPrepareStatementCount();
        System.out.printf("V340_METRIC fact_memory_overview_5000_ms=%d queries=%d%n", overviewMs, overviewQueries);
        assertThat(overview.totalFactCount()).isEqualTo(5_000);
        assertThat(overviewQueries).isLessThanOrEqualTo(4);
        assertThat(overviewMs).isLessThan(1_000);

        statistics.clear();
        long batchesStarted = System.nanoTime();
        var batchPage = factService.listRecordBatches(identity.userId(), projectId, 0, 20);
        long batchesMs = (System.nanoTime() - batchesStarted) / 1_000_000;
        long batchQueries = statistics.getPrepareStatementCount();
        System.out.printf("V340_METRIC project_records_100_batches_ms=%d queries=%d%n", batchesMs, batchQueries);
        assertThat(batchPage.items()).hasSize(20);
        assertThat(batchPage.totalElements()).isEqualTo(100);
        assertThat(batchQueries).isLessThanOrEqualTo(3);

        statistics.clear();
        long detailStarted = System.nanoTime();
        var detail = factService.getRecordBatch(identity.userId(), batches.get(0).getId(), 0, 20);
        long detailMs = (System.nanoTime() - detailStarted) / 1_000_000;
        long detailQueries = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        System.out.printf("V340_METRIC batch_detail_50_facts_ms=%d queries=%d%n", detailMs, detailQueries);
        assertThat(detail.facts().items()).hasSize(20);
        assertThat(detail.facts().totalElements()).isEqualTo(50);
        assertThat(detailQueries).isLessThanOrEqualTo(4);
    }

    @Test
    void dashboardBootstrapRestoresPersistedBatchSegmentsAndJob() throws Exception {
        Identity identity = register();
        UUID projectId = UUID.fromString(createProject(identity.token(), "Dashboard bootstrap"));
        ProjectMemory memory = new ProjectMemory(projectId);
        memory.update("本地开发工具", "可靠性修复", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath("sample-project");
        memoryRepository.saveAndFlush(memory);

        ChangeBatch batch = batch(projectId, "master", Instant.parse("2026-07-03T00:00:00Z"), "SUCCESS");
        batch = batchRepository.saveAndFlush(batch);
        List<DevelopmentSegment> segments = new ArrayList<>();
        for (int index = 0; index < 8; index += 1) {
            segments.add(segment(projectId, batch.getId(), "推进段 " + index));
        }
        segments = segmentRepository.saveAllAndFlush(segments);
        ProjectChange pending = change(projectId, batch.getId(), segments.get(0).getId(), "待确认建议");
        pending.updateSedimentSuggestion(
            segments.get(0).getId(), SedimentAction.NEW_SEDIMENT, null, "解决读取可靠性", "证据完整",
            List.of("commit:abc"), EvidenceConfidence.HIGH, true
        );
        changeRepository.saveAndFlush(pending);

        ProjectAnalysisJob job = new ProjectAnalysisJob(projectId, identity.userId(), ProjectAnalysisJobType.WORK_SESSION_SCAN, null);
        job.markSucceeded("{}", null);
        job = jobRepository.saveAndFlush(job);

        long startedAt = System.nanoTime();
        var response = mockMvc.perform(get("/api/projects/" + projectId + "/dashboard-bootstrap")
                .header("Authorization", "Bearer " + identity.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.project.id").value(projectId.toString()))
            .andExpect(jsonPath("$.data.memory.localProjectPath").value("sample-project"))
            .andExpect(jsonPath("$.data.latestScanJob.id").value(job.getId().toString()))
            .andExpect(jsonPath("$.data.workSessionScan.batch.id").value(batch.getId().toString()))
            .andExpect(jsonPath("$.data.workSessionScan.segments", hasSize(8)))
            .andExpect(jsonPath("$.data.pendingSedimentReviewCount").value(1))
            .andExpect(jsonPath("$.data.providerAvailability.configured").value(false))
            .andReturn();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        System.out.printf("V3381_METRIC dashboard_bootstrap_ms=%d bytes=%d%n", elapsedMs, response.getResponse().getContentAsByteArray().length);
        assertThat(elapsedMs).isLessThan(1_000);
    }

    private ChangeBatch batch(UUID projectId, String branch, Instant startedAt, String modelStatus) {
        ChangeBatch batch = new ChangeBatch(projectId, "base", "head", branch, false);
        ReflectionTestUtils.setField(batch, "scanStartedAt", startedAt);
        ReflectionTestUtils.setField(batch, "createdAt", startedAt);
        ReflectionTestUtils.setField(batch, "updatedAt", startedAt);
        batch.complete(1, 1, 0, List.of());
        batch.updateDiagnostics("fingerprint-" + branch, false, "", "", modelStatus.isBlank() ? "" : "MODEL",
            modelStatus, modelStatus.isBlank() ? "" : "Test Provider", "", 1, 1, 0, 2);
        return batch;
    }

    private DevelopmentSegment segment(UUID projectId, UUID batchId, String title) {
        DevelopmentSegment segment = new DevelopmentSegment(projectId, batchId);
        segment.updateContent(
            title, "完成数据读取可靠性修复", List.of("保留完整分析结果"), "工作台可快速恢复",
            List.of("abc"), List.of(), List.of("src/read-model.ts"), List.of("commit:abc"),
            EvidenceConfidence.HIGH, DevelopmentSegmentStatus.PENDING
        );
        segment.updateAnalysis("MODEL", "Test Provider", "", "PASS", "", List.of(), List.of());
        return segment;
    }

    private void seedFacts(UUID projectId, List<ChangeBatch> batches, int startInclusive, int endExclusive) {
        Instant occurred = Instant.parse("2020-01-01T00:00:00Z");
        List<ProjectFact> buffer = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index += 1) {
            ChangeBatch batch = batches.get(index % batches.size());
            ProjectFact fact = new ProjectFact(
                projectId, batch.getId(), null, ProjectFactOrigin.INCREMENTAL_SCAN, String.format("%064x", index + 1L)
            );
            fact.updateContent(
                "规模事实 " + index, "分页读取规模事实 " + index, List.of("自动记录规模事实"), "保持长期项目记忆可读",
                occurred.plusSeconds(index), occurred.plusSeconds(index), List.of(), List.of(), List.of(),
                List.of("src/fact-" + index + ".java"), List.of("file:src/fact-" + index + ".java"),
                "LOCAL_RULE", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
            );
            buffer.add(fact);
            if (buffer.size() == 500) {
                factRepository.saveAllAndFlush(buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) factRepository.saveAllAndFlush(buffer);
    }

    private Metric measure(org.hibernate.stat.Statistics statistics, java.util.function.Supplier<com.projectflow.dto.ProjectFactDtos.ProjectFactPageResponse> operation) {
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long started = System.nanoTime();
        var page = operation.get();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        long queries = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return new Metric(elapsedMs, queries, page.items().size(), page.totalElements());
    }

    private ProjectChange change(UUID projectId, UUID batchId, UUID segmentId, String title) {
        ProjectChange change = new ProjectChange(projectId, null);
        change.update(
            ProjectChangeSourceType.DEVELOPMENT_SEGMENT, segmentId == null ? "legacy" : segmentId.toString(), null,
            ProjectChangeKind.CAPABILITY, ProjectChangeImpactLevel.MINOR, title, "建议摘要", "", "src/read-model.ts",
            "", "", "", "", "", "", ""
        );
        change.updateSedimentSuggestion(
            segmentId, SedimentAction.NEW_SEDIMENT, null, "解决读取可靠性", "证据完整",
            List.of("commit:abc"), EvidenceConfidence.HIGH, true
        );
        change.recordReviewMetadata(batchId, "MODEL_RESULT", "PASS", "HIGH");
        return change;
    }

    private Identity register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        JsonNode data = body(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"read-" + unique.substring(0, 8) + "\",\"email\":\"" + unique + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk())
            .andReturn()).path("data");
        return new Identity(data.path("accessToken").asText(), UUID.fromString(data.at("/user/id").asText()));
    }

    private String createProject(String token, String name) throws Exception {
        return body(mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"V3.3.8.1\",\"status\":\"BUILDING\",\"techStack\":[\"Java\"],\"repoUrl\":\"\",\"startDate\":\"2026-07-01\",\"endDate\":null}"))
            .andExpect(status().isOk())
            .andReturn()).at("/data/id").asText();
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Identity(String token, UUID userId) {
    }

    private record Metric(long elapsedMs, long queries, int itemCount, long totalElements) {
    }
}
