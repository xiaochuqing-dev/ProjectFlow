package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.ProjectChange;
import com.projectflow.entity.ProjectChangeImpactLevel;
import com.projectflow.entity.ProjectChangeKind;
import com.projectflow.entity.ProjectChangeSourceType;
import com.projectflow.repository.ProjectReviewCursorRepository;
import com.projectflow.repository.ProjectChangeRepository;
import com.projectflow.repository.DevelopmentSegmentRepository;
import com.projectflow.service.PendingChangeScanService;
import com.projectflow.service.ProjectSedimentService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectSedimentControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectReviewCursorRepository cursorRepository;
    @Autowired
    private ProjectChangeRepository changeRepository;
    @Autowired
    private DevelopmentSegmentRepository segmentRepository;
    @Autowired
    private PendingChangeScanService pendingChangeScanService;
    @Autowired
    private ProjectSedimentService sedimentService;

    @Test
    void confirmsFourActionsAndAdvancesCursorOnlyAfterTheBatchIsResolved() throws Exception {
        String token = register();
        String projectId = createProject(token);
        Path root = createGitProject();
        bindProject(token, projectId, root);

        MvcResult scanResult = mockMvc.perform(post("/api/projects/" + projectId + "/scan").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.segments", hasSize(4)))
            .andReturn();
        String batchId = body(scanResult).at("/data/batch/id").asText();
        promoteBatchToModelResult(projectId, batchId);

        JsonNode changes = body(mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(4)))
            .andReturn()).path("data");

        String first = changes.get(0).path("id").asText();
        String second = changes.get(1).path("id").asText();
        String third = changes.get(2).path("id").asText();
        String fourth = changes.get(3).path("id").asText();

        mockMvc.perform(post("/api/project-changes/" + first + "/confirmation-preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"NEW_SEDIMENT\",\"targetSedimentId\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.actionLabel").isNotEmpty())
            .andExpect(jsonPath("$.data.evidenceToAdd").isNumber())
            .andExpect(jsonPath("$.data.filesToAdd").isNumber())
            .andExpect(jsonPath("$.data.affectsConfirmedCapabilities").value(false))
            .andExpect(jsonPath("$.data.usedByNextCapabilityAnalysis").value(true));

        MvcResult created = confirm(token, first, "NEW_SEDIMENT", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment.title").isNotEmpty())
            .andExpect(jsonPath("$.data.sediment.capabilityStatus").value("PENDING_ANALYSIS"))
            .andExpect(jsonPath("$.data.sediment.sourceBatchIds[0]").value(batchId))
            .andExpect(jsonPath("$.data.resultMessage").isNotEmpty())
            .andExpect(jsonPath("$.data.sedimentPath").isNotEmpty())
            .andReturn();
        String sedimentId = body(created).at("/data/sediment/id").asText();
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).isEmpty();

        confirm(token, second, "MERGE_EXISTING", UUID.randomUUID().toString())
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/project-changes/" + second + "/confirmation-preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"MERGE_EXISTING\",\"targetSedimentId\":\"" + sedimentId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetTitle").isNotEmpty())
            .andExpect(jsonPath("$.data.updatedFields", hasSize(3)));
        confirm(token, second, "MERGE_EXISTING", sedimentId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment.id").value(sedimentId));
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).isEmpty();

        String summaryBeforeEvidenceOnly = body(mockMvc.perform(get("/api/project-sediments/" + sedimentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()).at("/data/summary").asText();
        confirm(token, third, "EVIDENCE_ONLY", sedimentId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment.summary").value(summaryBeforeEvidenceOnly));
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).isEmpty();

        confirm(token, fourth, "IGNORE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sediment").doesNotExist());

        mockMvc.perform(get("/api/projects/" + projectId + "/sediments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
        assertThat(cursorRepository.findByProjectId(UUID.fromString(projectId))).get()
            .extracting(cursor -> cursor.getLastReviewedCommitSha()).isEqualTo(run(root, "git", "rev-parse", "HEAD").trim());
        mockMvc.perform(get("/api/sediment-review-batches/" + batchId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.pendingCount").value(0))
            .andExpect(jsonPath("$.data.batch.processedCount").value(4));

        // V3.3.3: 未配置模型时，"分析项目能力"不生成完整卡片，而是明确提示去配置模型。
        mockMvc.perform(post("/api/projects/" + projectId + "/capabilities/analyze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MODEL_NOT_CONFIGURED"));
    }

    @Test
    void legacyAcceptEndpointCreatesASedimentForV33Suggestions() throws Exception {
        String token = register();
        String projectId = createProject(token);
        Path root = createSingleChangeGitProject();
        bindProject(token, projectId, root);
        MvcResult scanResult = mockMvc.perform(post("/api/projects/" + projectId + "/scan").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        promoteBatchToModelResult(projectId, body(scanResult).at("/data/batch/id").asText());
        String changeId = body(mockMvc.perform(get("/api/projects/" + projectId + "/changes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn()).at("/data/0/id").asText();

        mockMvc.perform(post("/api/project-changes/" + changeId + "/accept").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/sediments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void confirmEndpointCanIgnoreLegacyProjectChanges() throws Exception {
        String token = register();
        String projectId = createProject(token);
        ProjectChange change = new ProjectChange(UUID.fromString(projectId), null);
        change.update(
            ProjectChangeSourceType.USER_MANUAL,
            "manual",
            null,
            ProjectChangeKind.DOCS,
            ProjectChangeImpactLevel.MINOR,
            "旧版候选",
            "旧版候选摘要",
            "",
            "README.md",
            "",
            "",
            "",
            "",
            "",
            "",
            ""
        );
        change = changeRepository.save(change);

        confirm(token, change.getId().toString(), "IGNORE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changeStatus").value("IGNORED"))
            .andExpect(jsonPath("$.data.batchStatus").value("LEGACY_CHANGE"));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String changeId, String action, String targetId) throws Exception {
        String target = targetId == null ? "null" : "\"" + targetId + "\"";
        return mockMvc.perform(post("/api/project-changes/" + changeId + "/confirm")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"" + action + "\",\"targetSedimentId\":" + target + "}"));
    }

    private String register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sediment-" + unique.substring(0, 8) + "\",\"email\":\"" + unique + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk()).andReturn();
        return body(result).at("/data/accessToken").asText();
    }

    private String createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Sediment Project","description":"V3.3","status":"BUILDING","techStack":["Java"],"repoUrl":"","startDate":"2026-07-01","endDate":null}
                    """))
            .andExpect(status().isOk()).andReturn();
        return body(result).at("/data/id").asText();
    }

    private void bindProject(String token, String projectId, Path root) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/agent-bridge/protocol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectPath\":\"" + root.toAbsolutePath().normalize().toString().replace("\\", "\\\\") + "\",\"requirements\":\"V3.3\"}"))
            .andExpect(status().isOk());
    }

    private Path createGitProject() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "v33-sediment-tests", UUID.randomUUID().toString()));
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "agent@example.com");
        run(root, "git", "config", "user.name", "Codex Agent");
        String[] scopes = {"scan", "protocol", "sediment", "output"};
        for (int index = 0; index < 12; index++) {
            String scope = scopes[index % scopes.length];
            Path file = root.resolve(scope + "/file-" + index + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "change " + index, StandardCharsets.UTF_8);
            run(root, "git", "add", ".");
            run(root, "git", "commit", "-m", "feat(" + scope + "): change " + index);
        }
        return root;
    }

    private Path createSingleChangeGitProject() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "v33-sediment-tests", UUID.randomUUID().toString()));
        run(root, "git", "init", "-b", "master");
        run(root, "git", "config", "user.email", "agent@example.com");
        run(root, "git", "config", "user.name", "Codex Agent");
        Path file = root.resolve("scan/cursor.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "cursor", StandardCharsets.UTF_8);
        run(root, "git", "add", ".");
        run(root, "git", "commit", "-m", "feat(scan): add cursor");
        return root;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void localFactDraftsStayOutOfFormalSuggestionsAndRemainVisibleByBatch() throws Exception {
        String token = register();
        String projectId = createProject(token);
        Path root = createSingleChangeGitProject();
        bindProject(token, projectId, root);
        MvcResult scan = mockMvc.perform(post("/api/projects/" + projectId + "/scan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        String batchId = body(scan).at("/data/batch/id").asText();

        mockMvc.perform(get("/api/projects/" + projectId + "/changes").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/sediment-review-batches/" + batchId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.formalSuggestionCount").value(0))
            .andExpect(jsonPath("$.data.batch.localDraftCount").value(1))
            .andExpect(jsonPath("$.data.batch.needsReanalysis").value(true))
            .andExpect(jsonPath("$.data.localDrafts", hasSize(1)));
    }

    private void promoteBatchToModelResult(String projectId, String batchId) {
        UUID parsedBatchId = UUID.fromString(batchId);
        var segments = segmentRepository.findByBatchIdOrderByCreatedAtAsc(parsedBatchId);
        segments.forEach(segment -> segment.updateAnalysis("MODEL", "Test Model", "", "PASS", "", java.util.List.of(), java.util.List.of()));
        segmentRepository.saveAll(segments);
        sedimentService.createSuggestions(UUID.fromString(projectId), pendingChangeScanService.listSegments(parsedBatchId));
    }

    private String run(Path root, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new AssertionError(String.join(" ", command) + " failed: " + output);
        return output;
    }
}
