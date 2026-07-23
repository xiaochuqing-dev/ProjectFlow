package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projectflow.dto.ProjectUnderstandingDtos.GitEvidenceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureCoverage;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureDelta;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureFunctionalArea;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureMetrics;
import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectEvolutionBridge;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.repository.ProjectEvolutionBridgeRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;

class ProjectEvolutionBridgeServiceTest {
    @TempDir
    Path root;

    @Test
    void createsOneEvidenceBackedBeforeChangeAfterBridgeAndKeepsItIdempotent() throws Exception {
        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "ProjectFlow Test");
        Files.writeString(root.resolve("Service.java"), "class Service {}\n");
        git("add", "Service.java");
        git("commit", "-m", "before");
        String before = git("rev-parse", "HEAD");
        Files.writeString(root.resolve("Service.java"), "class Service { void run() {} }\n");
        git("add", "Service.java");
        git("commit", "-m", "meaningful change");
        String after = git("rev-parse", "HEAD");

        UUID projectId = UUID.randomUUID();
        ProjectFact fact = new ProjectFact(
            projectId, null, null, ProjectFactOrigin.INCREMENTAL_SCAN, "f".repeat(64)
        );
        fact.updateContent(
            "服务执行能力形成",
            "新增可执行入口并保留真实提交证据",
            List.of("新增 run"),
            "服务可以执行",
            Instant.now().minusSeconds(60),
            Instant.now(),
            List.of(after),
            List.of(),
            List.of(),
            List.of(),
            List.of("fact:test"),
            "LOCAL_RULE",
            "PASS",
            EvidenceConfidence.HIGH,
            ProjectFactRecordStatus.RECORDED,
            ""
        );

        ProjectFactRepository facts = mock(ProjectFactRepository.class);
        when(facts.findTop200ByProjectIdOrderByOccurredToDescCreatedAtDesc(projectId)).thenReturn(List.of(fact));
        ProjectEvolutionBridgeRepository bridges = mock(ProjectEvolutionBridgeRepository.class);
        AtomicReference<ProjectEvolutionBridge> stored = new AtomicReference<>();
        when(bridges.existsByProjectIdAndBridgeFingerprint(any(), any()))
            .thenAnswer(invocation -> stored.get() != null
                && stored.get().getBridgeFingerprint().equals(invocation.getArgument(1)));
        when(bridges.save(any(ProjectEvolutionBridge.class))).thenAnswer(invocation -> {
            ProjectEvolutionBridge value = invocation.getArgument(0);
            stored.set(value);
            return value;
        });
        ProjectEvolutionBridgeService service = new ProjectEvolutionBridgeService(
            mock(ProjectRepository.class), facts, bridges, new FixedCommandExecutor()
        );
        StructureFunctionalArea area = new StructureFunctionalArea(
            "area:service", "服务执行区域", "HIGH", List.of("Service.java"),
            List.of("symbol:service"), 1, List.of("scip:service"), "JGRAPHT_LABEL_PROPAGATION"
        );
        ProjectStructureIndexResponse previous = structure("git:" + before + ":before", List.of(area));
        ProjectStructureIndexResponse current = structure("git:" + after + ":after", List.of(area));
        GitEvidenceResponse git = new GitEvidenceResponse(true, "master", after, 2, "CLEAN", 0);

        var first = service.rebuild(projectId, root, git, previous, current, Set.of("Service.java"));
        var second = service.rebuild(projectId, root, git, previous, current, Set.of("Service.java"));

        assertThat(first.createdCount()).isEqualTo(1);
        assertThat(second.createdCount()).isZero();
        assertThat(stored.get().getBeforeRevision()).isEqualTo(before);
        assertThat(stored.get().getAfterRevision()).isEqualTo(after);
        assertThat(stored.get().getAffectedAreaLabel()).isEqualTo("服务执行区域");
        assertThat(stored.get().getEpistemicStatus()).isEqualTo("OBSERVED");
        assertThat(stored.get().getSourceFactIds()).containsExactly(fact.getId().toString());
        assertThat(stored.get().getEvidenceRefs()).contains("git:" + after, "scip:service");
    }

    private ProjectStructureIndexResponse structure(
        String sourceRevision,
        List<StructureFunctionalArea> areas
    ) {
        return new ProjectStructureIndexResponse(
            "structure-v2", "MANIFEST_FILESYSTEM+SCIP", sourceRevision, "a".repeat(64),
            false, 1, List.of(), false, List.of(), List.of(), List.of(), List.of(), Map.of(),
            List.of(), new StructureCoverage(1, 1, 1, 1, "SCIP", 1), List.of("SCIP"), List.of(),
            List.of(), List.of(), List.of(), List.of(), areas, List.of(),
            new StructureMetrics(1, 1, 1, 1, 1, 1, areas.size(), 1, 0, -1, 1, false),
            new StructureDelta("INCREMENTAL_DIRTY_SET", 0, 1, 0, 0, true), Instant.now()
        );
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException(output);
        return output;
    }
}
