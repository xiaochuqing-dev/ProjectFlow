package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectAgentCandidateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.SensitiveContentRedactor;

@SpringBootTest(properties = "projectflow.auth.required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentContextApiContractTest {
    @TempDir Path projectRoot;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectMemoryRepository memoryRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectAgentCandidateRepository candidateRepository;

    private Identity owner;
    private ProjectSpace project;
    private ProjectSpace otherProject;
    private ProjectFact fact;

    @BeforeEach
    void setUp() throws Exception {
        owner = register();
        project = project(owner.userId(), "Agent Context Contract");
        otherProject = project(UUID.randomUUID(), "Other owner");
        Files.createDirectories(projectRoot.resolve("docs"));
        Files.writeString(projectRoot.resolve("docs/mail-spec.md"), String.join("\n", List.of(
            "邮件发送失败时执行有界重试。",
            "状态反馈保留最终失败原因。",
            "Agent 声明仍需工程复验。"
        )));
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update("", "", "", "", "", "", "", "", "");
        memory.rememberLocalProjectPath(projectRoot.toString());
        memoryRepository.saveAndFlush(memory);
        fact = factRepository.saveAndFlush(fact(project.getId()));
    }

    @Test
    void taskContextWorkResultAndLocalRevalidationUseOneOwnedFactLayer() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/project-memory/context-package")
                .header("Authorization", "Bearer " + owner.token())
                .queryParam("taskDescription", "改进邮件失败重试 api_key=abcdefghijklmnop")
                .queryParam("scope", "docs")
                .queryParam("revisionPreference", "CURRENT_SNAPSHOT")
                .queryParam("evidenceDepth", "DEEP")
                .queryParam("sizeBudget", "8000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.packageVersion").value("projectflow-agent-context-v2"))
            .andExpect(jsonPath("$.data.packageRevision").value(org.hamcrest.Matchers.startsWith("sha256:")))
            .andExpect(jsonPath("$.data.requestedScope[0]").value("docs"))
            .andExpect(jsonPath("$.data.requestedEvidenceDepth").value("DEEP"))
            .andExpect(jsonPath("$.data.currentStrongFacts[0].itemId").value("fact:" + fact.getId()))
            .andExpect(jsonPath("$.data.taskDescription").value(
                org.hamcrest.Matchers.containsString(SensitiveContentRedactor.REDACTED)
            ))
            .andExpect(jsonPath("$.data.generationMetadata.modelCalled").value(false));

        mockMvc.perform(post("/api/projects/" + project.getId() + "/agent-candidates/work-results")
                .header("Authorization", "Bearer " + owner.token())
                .header("X-ProjectFlow-Caller", "agent-context-api-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "changedFiles":["docs/mail-spec.md"],
                      "claimedBehaviors":["增加邮件失败重试和状态反馈"],
                      "executedCommands":["mvn test"],
                      "testResults":["Agent 声称定向测试通过"],
                      "evidenceRefs":["fact:%s"],
                      "candidateFacts":[{
                        "candidateType":"ASSERTION",
                        "assertion":"邮件重试行为已实现，等待工程验证",
                        "epistemicStatus":"INFERRED",
                        "evidenceRefs":["fact:%s"]
                      }],
                      "knownLimitations":["尚未验证外部 SMTP"],
                      "sourceAgentId":"api-contract-agent"
                    }
                    """.formatted(fact.getId(), fact.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.validationStatus").value("SOURCE_IDENTITY_REVALIDATED"))
            .andExpect(jsonPath("$.data.rereadEvidenceRefs[0]").value(
                org.hamcrest.Matchers.startsWith("file:docs/mail-spec.md#sha256=")
            ))
            .andExpect(jsonPath("$.data.candidates[0].epistemicStatus").value("PROCESS_EVIDENCE"))
            .andExpect(jsonPath("$.data.candidates[1].epistemicStatus").value("INFERRED"));

        mockMvc.perform(post("/api/projects/" + project.getId() + "/project-memory/revalidate")
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"VERIFY_FACT","targetId":"fact:%s"}
                    """.formatted(fact.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.validationStatus").value("LOCAL_EVIDENCE_REVALIDATED"))
            .andExpect(jsonPath("$.data.verifiedEvidenceRefs[0]").value("file:docs/mail-spec.md"))
            .andExpect(jsonPath("$.data.range.locator").value("docs/mail-spec.md"))
            .andExpect(jsonPath("$.data.range.text").value(org.hamcrest.Matchers.containsString("有界重试")));

        assertThat(candidateRepository.findTop100ByProjectIdOrderByCreatedAtDesc(project.getId()))
            .hasSize(2)
            .allSatisfy(candidate -> assertThat(candidate.getEpistemicStatus()).isNotIn(
                ProjectFactEpistemicStatus.OBSERVED, ProjectFactEpistemicStatus.VERIFIED
            ));
    }

    @Test
    void apiRejectsDirectStrongFactAndCrossProjectReadsBeforeMutation() throws Exception {
        long before = candidateRepository.count();

        mockMvc.perform(post("/api/projects/" + project.getId() + "/agent-candidates/work-results")
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "changedFiles":["docs/mail-spec.md"],
                      "candidateFacts":[{
                        "candidateType":"ASSERTION",
                        "assertion":"Agent 不能直接声明强事实",
                        "epistemicStatus":"VERIFIED"
                      }]
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/" + otherProject.getId() + "/project-memory/revalidate")
                .header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"VERIFY_FACT","targetId":"fact:%s"}
                    """.formatted(fact.getId())))
            .andExpect(status().isNotFound());

        assertThat(candidateRepository.count()).isEqualTo(before);
    }

    private Identity register() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        JsonNode data = objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"agent-" + unique.substring(0, 8) + "\",\"email\":\"" + unique
                    + "@example.com\",\"password\":\"local-password-123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        return new Identity(data.path("accessToken").asText(), UUID.fromString(data.at("/user/id").asText()));
    }

    private ProjectSpace project(UUID userId, String name) {
        ProjectSpace value = new ProjectSpace(userId);
        value.update(name, "Agent 可信上下文", ProjectStatus.BUILDING, List.of(), "", LocalDate.now(), null);
        return projectRepository.saveAndFlush(value);
    }

    private ProjectFact fact(UUID projectId) {
        String fingerprint = UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
        ProjectFact value = new ProjectFact(
            projectId, UUID.randomUUID(), UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN, fingerprint
        );
        Instant occurred = Instant.parse("2026-08-01T00:00:00Z");
        value.updateContent(
            "邮件失败重试边界", "邮件失败重试和状态反馈已有项目文件证据", List.of(), "", occurred, occurred,
            List.of(), List.of(), List.of(), List.of("docs/mail-spec.md"), List.of("file:docs/mail-spec.md"),
            "LOCAL_RULE", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        value.applyKnowledgeContract(
            ProjectFactEpistemicStatus.OBSERVED, List.of("PROJECT_FILE"), "CURRENT", "", occurred, occurred,
            List.of(), List.of(), "ENGINEERING_VALIDATION", "", "", "VALIDATED"
        );
        return value;
    }

    private record Identity(String token, UUID userId) {
    }
}
