package com.projectflow;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ChangeBatch;
import com.projectflow.entity.ChangeBatchStatus;
import com.projectflow.entity.DevelopmentSegment;
import com.projectflow.entity.DevelopmentSegmentStatus;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactHistoryState;
import com.projectflow.entity.ProjectFactHistoryStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSediment;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactCursorRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectSedimentRepository;
import com.projectflow.service.ProjectFactIngestionService;
import com.projectflow.service.ProjectFactMigrationService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectFactMemoryTest {
    private static final String BASE_SHA = "1111111111111111111111111111111111111111";
    private static final String MODEL_SHA = "2222222222222222222222222222222222222222";
    private static final String LOCAL_SHA = "3333333333333333333333333333333333333333";
    private static final String HEAD_SHA = "4444444444444444444444444444444444444444";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-01T08:00:00Z");

    @Autowired ProjectRepository projectRepository;
    @Autowired ChangeBatchRepository batchRepository;
    @Autowired DevelopmentSegmentRepository segmentRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectFactCommitRefRepository commitRefRepository;
    @Autowired ProjectFactCursorRepository cursorRepository;
    @Autowired ProjectSedimentRepository sedimentRepository;
    @Autowired ProjectFactHistoryStateRepository historyStateRepository;
    @Autowired ProjectFactIngestionService ingestionService;
    @Autowired ProjectFactMigrationService migrationService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void ingestionRecordsModelAndLocalFactsAndAdvancesCursorDespiteAttention() {
        UUID projectId = project("Fact classification").getId();
        ChangeBatch batch = batch(projectId, HEAD_SHA);
        DevelopmentSegment model = segment(projectId, batch.getId(), "Model fact", "MODEL", MODEL_SHA, true);
        DevelopmentSegment local = segment(projectId, batch.getId(), "Local fact", "LOCAL_RULE", LOCAL_SHA, true);
        DevelopmentSegment noEvidence = segment(projectId, batch.getId(), "Needs evidence", "MODEL", "", false);
        segmentRepository.saveAllAndFlush(List.of(model, local, noEvidence));

        var result = ingestionService.ingestBatch(projectId, batch.getId(), ProjectFactOrigin.INCREMENTAL_SCAN, true);

        assertThat(result.factCount()).isEqualTo(3);
        assertThat(result.attentionCount()).isEqualTo(1);
        Map<String, ProjectFact> facts = factRepository.findByBatchIdOrderByOccurredFromAscCreatedAtAsc(batch.getId())
            .stream().collect(java.util.stream.Collectors.toMap(ProjectFact::getTitle, fact -> fact));
        assertThat(facts.get("Model fact").getSourceMode()).isEqualTo("MODEL");
        assertThat(facts.get("Model fact").getRecordStatus()).isEqualTo(ProjectFactRecordStatus.RECORDED);
        assertThat(facts.get("Local fact").getSourceMode()).isEqualTo("LOCAL_RULE");
        assertThat(facts.get("Local fact").getRecordStatus()).isEqualTo(ProjectFactRecordStatus.RECORDED);
        assertThat(facts.get("Needs evidence").getRecordStatus()).isEqualTo(ProjectFactRecordStatus.NEEDS_ATTENTION);
        assertThat(facts.get("Needs evidence").getAttentionReason()).isNotBlank();

        ChangeBatch recordedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(recordedBatch.getStatus()).isEqualTo(ChangeBatchStatus.FACTS_RECORDED_WITH_ATTENTION);
        assertThat(recordedBatch.getFactCount()).isEqualTo(3);
        assertThat(recordedBatch.getAttentionCount()).isEqualTo(1);
        var cursor = cursorRepository.findByProjectId(projectId).orElseThrow();
        assertThat(cursor.getLastRecordedCommitSha()).isEqualTo(HEAD_SHA);
        assertThat(cursor.getLastBatchId()).isEqualTo(batch.getId());
    }

    @Test
    void sameBatchIngestionIsIdempotentUnderConcurrentCalls() throws Exception {
        UUID projectId = project("Concurrent ingestion").getId();
        ChangeBatch batch = batch(projectId, "5555555555555555555555555555555555555555");
        DevelopmentSegment segment = segment(
            projectId, batch.getId(), "Concurrent fact", "MODEL", "6666666666666666666666666666666666666666", true
        );
        segmentRepository.saveAndFlush(segment);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var calls = List.of(
                executor.submit(() -> ingestAfterBarrier(projectId, batch.getId(), ready, start)),
                executor.submit(() -> ingestAfterBarrier(projectId, batch.getId(), ready, start))
            );
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();
            assertThat(calls.get(0).get(10, SECONDS).factCount()).isEqualTo(1);
            assertThat(calls.get(1).get(10, SECONDS).factCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        ingestionService.ingestBatch(projectId, batch.getId(), ProjectFactOrigin.INCREMENTAL_SCAN, true);
        assertThat(factRepository.countByBatchId(batch.getId())).isEqualTo(1);
        ProjectFact fact = factRepository.findByBatchIdOrderByOccurredFromAscCreatedAtAsc(batch.getId()).get(0);
        assertThat(commitRefRepository.findByFactId(fact.getId())).hasSize(1);
    }

    @Test
    void legacySegmentAndLinkedSedimentMigrationIsIdempotent() {
        UUID projectId = project("Segment migration").getId();
        ChangeBatch batch = batch(projectId, "7777777777777777777777777777777777777777");
        DevelopmentSegment segment = segment(
            projectId, batch.getId(), "Migrated segment", "MODEL", "8888888888888888888888888888888888888888", true
        );
        segmentRepository.saveAndFlush(segment);
        ProjectSediment sediment = new ProjectSediment(projectId);
        sediment.updateCore(
            "Linked sediment", "Linked summary", "Linked result", "PROJECT_CAPABILITY",
            List.of(segment.getId().toString()), List.of("commit:8888888888888888888888888888888888888888")
        );
        sediment.recordConfirmation(batch.getId(), List.of("src/linked.java"), "MODEL_RESULT", "PASS");
        sedimentRepository.saveAndFlush(sediment);

        migrationService.migrateOnStartup();
        migrationService.migrateOnStartup();

        assertThat(factRepository.countByProjectId(projectId)).isEqualTo(1);
        ProjectFact fact = factRepository.findFirstByProjectIdAndSourceSegmentId(projectId, segment.getId()).orElseThrow();
        assertThat(fact.getOrigin()).isEqualTo(ProjectFactOrigin.LEGACY_SEGMENT_MIGRATION);
        assertThat(fact.getLegacySedimentId()).isEqualTo(sediment.getId());
    }

    @Test
    void legacySedimentOnlyMigrationIsIdempotent() {
        UUID projectId = project("Sediment migration").getId();
        ProjectSediment sediment = new ProjectSediment(projectId);
        sediment.updateCore(
            "Legacy sediment", "Legacy summary", "Legacy result", "PROJECT_CAPABILITY",
            List.of(), List.of("file:src/legacy.java")
        );
        sediment.recordConfirmation(null, List.of("src/legacy.java"), "LEGACY_UNKNOWN", "NEEDS_REVIEW");
        sedimentRepository.saveAndFlush(sediment);

        migrationService.migrateOnStartup();
        migrationService.migrateOnStartup();

        assertThat(factRepository.countByProjectId(projectId)).isEqualTo(1);
        ProjectFact fact = factRepository.findAll().stream()
            .filter(candidate -> projectId.equals(candidate.getProjectId()))
            .findFirst().orElseThrow();
        assertThat(fact.getOrigin()).isEqualTo(ProjectFactOrigin.LEGACY_SEDIMENT_MIGRATION);
        assertThat(fact.getLegacySedimentId()).isEqualTo(sediment.getId());
        assertThat(fact.getRecordStatus()).isEqualTo(ProjectFactRecordStatus.RECORDED);
    }

    @Test
    void historyStatePersistsProgressCheckpoint() {
        UUID projectId = project("History checkpoint").getId();
        UUID batchId = UUID.randomUUID();
        ProjectFactHistoryState state = new ProjectFactHistoryState(projectId);
        state.initialize(HEAD_SHA, 100, 20, true);
        state.markRunning(100, 20);
        state.recordChunk(MODEL_SHA, batchId, 100, 40);
        historyStateRepository.saveAndFlush(state);

        ProjectFactHistoryState restored = historyStateRepository.findByProjectId(projectId).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(ProjectFactHistoryStatus.PAUSED);
        assertThat(restored.getHeadSnapshotSha()).isEqualTo(HEAD_SHA);
        assertThat(restored.getLastProcessedCommitSha()).isEqualTo(MODEL_SHA);
        assertThat(restored.getLastBatchId()).isEqualTo(batchId);
        assertThat(restored.getTotalCommitCount()).isEqualTo(100);
        assertThat(restored.getCoveredCommitCount()).isEqualTo(40);
        assertThat(restored.getRemainingCommitCount()).isEqualTo(60);
        assertThat(restored.getCompletedChunkCount()).isEqualTo(1);
    }

    @Test
    void factApiPaginatesAndEnforcesProjectOwnership() throws Exception {
        Identity owner = register();
        Identity other = register();
        UUID projectId = UUID.fromString(createProject(owner.token(), "Fact API"));
        ChangeBatch batch = batch(projectId, "9999999999999999999999999999999999999999");
        segmentRepository.saveAllAndFlush(List.of(
            segment(projectId, batch.getId(), "First API fact", "MODEL", MODEL_SHA, true),
            segment(projectId, batch.getId(), "Second API fact", "LOCAL_RULE", LOCAL_SHA, true)
        ));
        ingestionService.ingestBatch(projectId, batch.getId(), ProjectFactOrigin.INCREMENTAL_SCAN, true);
        UUID factId = factRepository.findByBatchIdOrderByOccurredFromAscCreatedAtAsc(batch.getId()).get(0).getId();

        mockMvc.perform(get("/api/projects/" + projectId + "/facts?page=0&size=1")
                .header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get("/api/projects/" + projectId + "/facts")
                .header("Authorization", "Bearer " + other.token()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/project-facts/" + factId)
                .header("Authorization", "Bearer " + other.token()))
            .andExpect(status().isNotFound());
    }

    private ProjectFactIngestionService.IngestionResult ingestAfterBarrier(
        UUID projectId,
        UUID batchId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return ingestionService.ingestBatch(projectId, batchId, ProjectFactOrigin.INCREMENTAL_SCAN, true);
    }

    private ProjectSpace project(String name) {
        ProjectSpace project = new ProjectSpace(UUID.randomUUID());
        project.update(name, "V3.4.0 fact memory test", ProjectStatus.BUILDING, List.of("Java"), "", LocalDate.now(), null);
        return projectRepository.saveAndFlush(project);
    }

    private ChangeBatch batch(UUID projectId, String headSha) {
        ChangeBatch batch = new ChangeBatch(projectId, BASE_SHA, headSha, "master", false);
        batch.complete(1, 1, 0, List.of());
        batch.updateDiagnostics("fact-test-" + UUID.randomUUID(), false, "", "", "MODEL", "SUCCESS", "Test Provider", "", 1, 1, 0, 2);
        return batchRepository.saveAndFlush(batch);
    }

    private DevelopmentSegment segment(
        UUID projectId,
        UUID batchId,
        String title,
        String mode,
        String commitSha,
        boolean withEvidence
    ) {
        List<String> commits = commitSha.isBlank() ? List.of() : List.of(commitSha);
        List<String> evidence = withEvidence ? List.of("commit:" + commitSha) : List.of();
        DevelopmentSegment segment = new DevelopmentSegment(projectId, batchId);
        segment.updateContent(
            title, title + " summary", List.of(title + " change"), title + " value", commits, List.of(),
            List.of("src/fact-memory.java"), evidence, EvidenceConfidence.HIGH, DevelopmentSegmentStatus.PENDING
        );
        segment.recordOccurrence(OCCURRED_AT, OCCURRED_AT.plusSeconds(60));
        segment.updateAnalysis(mode, "MODEL".equals(mode) ? "Test Provider" : "", "", "PASS", "", List.of(), List.of());
        return segment;
    }

    private Identity register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        JsonNode data = body(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"fact-" + unique.substring(0, 8) + "\",\"email\":\"" + unique
                    + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk())
            .andReturn()).path("data");
        return new Identity(data.path("accessToken").asText());
    }

    private String createProject(String token, String name) throws Exception {
        return body(mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"V3.4.0\",\"status\":\"BUILDING\","
                    + "\"techStack\":[\"Java\"],\"repoUrl\":\"\",\"startDate\":\"2026-07-01\",\"endDate\":null}"))
            .andExpect(status().isOk())
            .andReturn()).at("/data/id").asText();
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record Identity(String token) {
    }
}
