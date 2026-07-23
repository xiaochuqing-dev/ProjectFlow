package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

class RepositoryIntakeServiceTest {
    @TempDir
    Path root;

    private RepositoryIntakeService service;
    private ManifestFilesystemProjectStructureIndexer indexer;

    @BeforeEach
    void setUp() {
        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(1, "", false);
        SccCodeMetricsAdapter scc = mock(SccCodeMetricsAdapter.class);
        when(scc.inspect(root)).thenReturn(SccCodeMetricsAdapter.CodeMetrics.unavailable());
        service = new RepositoryIntakeService(commands, scc, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxFiles", 1000);
        ReflectionTestUtils.setField(service, "maxFileDetails", 100);
        ReflectionTestUtils.setField(service, "maxFileReadBytes", 8_388_608L);
        ReflectionTestUtils.setField(service, "maxTotalReadBytes", 536_870_912L);
        ReflectionTestUtils.setField(service, "smallLoc", 20L);
        ReflectionTestUtils.setField(service, "mediumLoc", 50L);
        ReflectionTestUtils.setField(service, "largeLoc", 100L);
        indexer = new ManifestFilesystemProjectStructureIndexer();
    }

    @Test
    void classifiesEmptyAndNonCodeDirectoriesWithoutGit() throws Exception {
        var empty = service.scan(root);
        assertThat(empty.intake().classification()).isEqualTo("EMPTY");
        assertThat(empty.intake().estimatedLoc()).isZero();
        assertThat(empty.intake().git().available()).isFalse();

        Files.writeString(root.resolve("README.md"), "notes only");
        var nonCode = service.scan(root);
        assertThat(nonCode.intake().classification()).isEqualTo("UNKNOWN_NON_CODE");
        assertThat(nonCode.intake().sourceFileCount()).isZero();
    }

    @Test
    void classifiesCodeWithoutGitAndBuildsBoundedEvidenceIndex() throws Exception {
        Files.createDirectories(root.resolve("src/main/java/demo"));
        Files.writeString(root.resolve("pom.xml"), "<project><modules><module>worker</module></modules></project>");
        Files.writeString(root.resolve("src/main/java/demo/DemoApplication.java"), """
            package demo;
            public class DemoApplication {
                public static void main(String[] args) {}
            }
            """);

        var scan = service.scan(root);
        var index = indexer.build(scan);

        assertThat(scan.intake().classification()).isEqualTo("CODE_NO_GIT");
        assertThat(scan.intake().monorepo()).isTrue();
        assertThat(scan.workspaceSignals()).contains("pom.xml -> worker");
        assertThat(index.modules()).isNotEmpty();
        assertThat(index.entryPoints()).extracting(item -> item.kind()).contains("APPLICATION");
        assertThat(index.evidence()).extracting(item -> item.id()).contains("intake:scan");
        assertThat(index.coverage().symbolCoverage()).isZero();
        assertThat(index.unsupportedAreas()).anyMatch(item -> item.contains("调用"));
    }

    @Test
    void largeWorkspaceUsesHierarchicalClassificationInputsWithoutReadingBuildOutputs() throws Exception {
        Files.writeString(root.resolve("package.json"), """
            {"name":"workspace","workspaces":["apps/*","packages/*"]}
            """);
        Files.createDirectories(root.resolve("apps/web"));
        Files.writeString(root.resolve("apps/web/app.tsx"), "export default function App() { return null; }\n".repeat(30));
        Files.createDirectories(root.resolve("node_modules/vendor"));
        Files.writeString(root.resolve("node_modules/vendor/noise.ts"), "ignored\n".repeat(1000));

        var scan = service.scan(root);

        assertThat(scan.intake().classification()).isEqualTo("CODE_NO_GIT");
        assertThat(scan.intake().scale()).isEqualTo("MONOREPO");
        assertThat(scan.intake().estimatedLoc()).isLessThan(1000);
        assertThat(scan.intake().languageDistribution()).containsEntry("TypeScript", 30L);
        assertThat(scan.intake().warnings()).anyMatch(item -> item.contains("跳过"));
    }

    @Test
    void deterministicThresholdsSeparateMediumAndLargeCodeWithoutGit() throws Exception {
        Files.writeString(root.resolve("main.py"), "print('x')\n".repeat(30));
        var medium = service.scan(root);
        assertThat(medium.intake().classification()).isEqualTo("CODE_NO_GIT");
        assertThat(medium.intake().scale()).isEqualTo("MEDIUM");

        Files.writeString(root.resolve("main.py"), "print('x')\n".repeat(70));
        var large = service.scan(root);
        assertThat(large.intake().scale()).isEqualTo("LARGE");
    }

    @Test
    void gradleRootNameAloneDoesNotPretendAProjectIsAMonorepo() throws Exception {
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'single-app'\n");
        Files.writeString(root.resolve("Main.java"), "class Main {}\n");

        var singleProject = service.scan(root);

        assertThat(singleProject.intake().monorepo()).isFalse();
        assertThat(singleProject.intake().scale()).isEqualTo("SMALL");

        Files.writeString(root.resolve("settings.gradle"), """
            rootProject.name = 'workspace'
            include 'app', 'library'
            """);
        var workspace = service.scan(root);

        assertThat(workspace.intake().monorepo()).isTrue();
        assertThat(workspace.intake().scale()).isEqualTo("MONOREPO");
    }
}
