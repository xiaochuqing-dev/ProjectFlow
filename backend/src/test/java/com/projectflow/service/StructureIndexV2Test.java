package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sourcegraph.Scip;

class StructureIndexV2Test {
    @TempDir
    Path root;

    private RepositoryIntakeService intake;
    private CompositeProjectStructureIndexer indexer;

    @BeforeEach
    void setUp() {
        LocalCommandExecutor commands = (directory, command, timeout) ->
            new LocalCommandExecutor.CommandResult(1, "", false);
        SccCodeMetricsAdapter scc = mock(SccCodeMetricsAdapter.class);
        when(scc.inspect(root)).thenReturn(SccCodeMetricsAdapter.CodeMetrics.unavailable());
        intake = new RepositoryIntakeService(commands, scc, new ObjectMapper());
        ReflectionTestUtils.setField(intake, "maxFiles", 10_000);
        ReflectionTestUtils.setField(intake, "maxFileDetails", 1_000);
        ReflectionTestUtils.setField(intake, "maxFileReadBytes", 8_388_608L);
        ReflectionTestUtils.setField(intake, "maxTotalReadBytes", 536_870_912L);
        ReflectionTestUtils.setField(intake, "smallLoc", 20_000L);
        ReflectionTestUtils.setField(intake, "mediumLoc", 100_000L);
        ReflectionTestUtils.setField(intake, "largeLoc", 500_000L);
        indexer = new CompositeProjectStructureIndexer(
            new ManifestFilesystemProjectStructureIndexer(),
            new ScipProjectStructureIndexer()
        );
    }

    @Test
    void consumesOfficialScipProtocolAndBuildsRankedRelationshipAreas() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("web"));
        Files.writeString(root.resolve("src/App.java"), "class App { Service service; }\n");
        Files.writeString(root.resolve("src/Service.java"), "class Service {}\n");
        Files.writeString(root.resolve("web/App.tsx"), "export function Screen() { return Service(); }\n");
        Files.write(root.resolve("index.scip"), scipFixture().toByteArray());

        var result = indexer.build(intake.scan(root));

        assertThat(result.indexVersion()).isEqualTo("structure-v2");
        assertThat(result.indexerSource()).isEqualTo("MANIFEST_FILESYSTEM+SCIP");
        assertThat(result.symbols()).extracting(item -> item.displayName())
            .contains("App", "Service", "Screen");
        assertThat(result.definitions()).hasSize(3);
        assertThat(result.references()).hasSize(2);
        assertThat(result.relations()).anyMatch(item -> "REFERENCES".equals(item.type()));
        assertThat(result.importantNodes()).isNotEmpty();
        assertThat(result.functionalAreas()).isNotEmpty();
        assertThat(result.functionalAreas()).allMatch(item -> "JGRAPHT_LABEL_PROPAGATION".equals(item.namingSource()));
        assertThat(result.coverage().symbolCoverage()).isPositive();
        assertThat(result.providerDiagnostics()).anyMatch(item -> "SCIP".equals(item.provider())
            && "SUCCEEDED".equals(item.status()));
        assertThat(result.metrics().symbolCount()).isEqualTo(3);
    }

    @Test
    void invalidScipFallsBackWithoutPretendingPreciseCoverage() throws Exception {
        Files.writeString(root.resolve("main.py"), "print('ok')\n");
        Files.write(root.resolve("index.scip"), new byte[] { 1, 2, 3, 4 });

        var result = indexer.build(intake.scan(root));

        assertThat(result.indexerSource()).isEqualTo("MANIFEST_FILESYSTEM");
        assertThat(result.symbols()).isEmpty();
        assertThat(result.coverage().symbolCoverage()).isZero();
        assertThat(result.providerDiagnostics()).anyMatch(item -> "SCIP".equals(item.provider())
            && "FAILED".equals(item.status()));
        assertThat(result.unsupportedAreas()).anyMatch(item -> item.contains("fallback"));
    }

    private static Scip.Index scipFixture() {
        String app = "scip-java maven demo 1.0 App#";
        String service = "scip-java maven demo 1.0 Service#";
        String screen = "scip-typescript npm demo 1.0 web/App.tsx/Screen().";
        return Scip.Index.newBuilder()
            .setMetadata(Scip.Metadata.newBuilder()
                .setToolInfo(Scip.ToolInfo.newBuilder().setName("fixture").setVersion("1.0")))
            .addDocuments(document(
                "Java",
                "src/App.java",
                symbol(app, "App", Scip.SymbolInformation.Kind.Class),
                definition(app, 0),
                reference(service, 0)
            ))
            .addDocuments(document(
                "Java",
                "src/Service.java",
                symbol(service, "Service", Scip.SymbolInformation.Kind.Class),
                definition(service, 0)
            ))
            .addDocuments(document(
                "TypeScript",
                "web/App.tsx",
                symbol(screen, "Screen", Scip.SymbolInformation.Kind.Function),
                definition(screen, 0),
                reference(service, 0)
            ))
            .build();
    }

    private static Scip.Document document(
        String language,
        String path,
        Scip.SymbolInformation symbol,
        Scip.Occurrence... occurrences
    ) {
        return Scip.Document.newBuilder()
            .setLanguage(language)
            .setRelativePath(path)
            .addSymbols(symbol)
            .addAllOccurrences(List.of(occurrences))
            .build();
    }

    private static Scip.SymbolInformation symbol(
        String symbol,
        String displayName,
        Scip.SymbolInformation.Kind kind
    ) {
        return Scip.SymbolInformation.newBuilder()
            .setSymbol(symbol)
            .setDisplayName(displayName)
            .setKind(kind)
            .build();
    }

    private static Scip.Occurrence definition(String symbol, int line) {
        return Scip.Occurrence.newBuilder()
            .setSymbol(symbol)
            .setSymbolRoles(Scip.SymbolRole.Definition_VALUE)
            .addAllRange(List.of(line, 0, 5))
            .build();
    }

    private static Scip.Occurrence reference(String symbol, int line) {
        return Scip.Occurrence.newBuilder()
            .setSymbol(symbol)
            .addAllRange(List.of(line, 10, 17))
            .build();
    }
}
