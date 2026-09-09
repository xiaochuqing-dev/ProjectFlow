package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.repository.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDeclarationReadTest {
    @TempDir Path root;
    final LocalCommandExecutor commands = mock(LocalCommandExecutor.class);
    final ProjectHistorySourceCollector collector = new ProjectHistorySourceCollector(
        mock(ProjectRepository.class), mock(ProjectMemoryRepository.class), mock(ProjectFactRepository.class),
        mock(ProjectAgentCandidateRepository.class), mock(LocalProjectPathGuard.class), commands,
        new SensitiveContentRedactor(), new ObjectMapper());
    ProjectHistorySourceCollector.CollectionOutcome source() {
        var source = mock(ProjectHistorySourceCollector.CollectionOutcome.class);
        when(source.projectRoot()).thenReturn(root);
        return source;
    }
    @Test void noDocumentIsAnUnknownPlanAndNeverRunsCommands() {
        assertThat(collector.collectDeclarations(source())).isEmpty();
        verifyNoInteractions(commands);
    }
    @Test void onlyPlanSectionsProduceQuotedDeclarationsWithSourceLocation() throws Exception {
        Files.writeString(root.resolve("README.md"), "# Example\n\n这是用于协作和文件管理的示例项目。\n\n## 计划\n- [ ] 计划增加任务附件的预览入口\n### 细节\n- 预计随后支持更多预览格式\n## 已完成\n已经增加稳定的通知读取入口。\n");
        var result = collector.collectDeclarations(source());
        assertThat(result).hasSize(3);
        assertThat(result.get(0).kind()).isEqualTo("IDENTITY");
        var plan = result.get(1);
        assertThat(plan.kind()).isEqualTo("PLAN");
        assertThat(plan.text()).isEqualTo("计划增加任务附件的预览入口");
        assertThat(plan.source()).isEqualTo("README.md");
        assertThat(plan.line()).isEqualTo(6);
        assertThat(plan.sourceHash()).matches("[a-f0-9]{64}");
        assertThat(plan.observedAt()).isNotBlank();
        verifyNoInteractions(commands);
    }
    @Test void fencedExamplesAndOversizedDocumentsCannotBecomePlans() throws Exception {
        Files.writeString(root.resolve("README.md"), "```md\n## 计划\n这里是代码示例中的伪造发布计划\n```\n# 项目\n这是代码块以外真实项目的介绍。\n");
        Files.writeString(root.resolve("ROADMAP.md"), "## 计划\n" + "不应读取".repeat(20_000));
        assertThat(collector.collectDeclarations(source())).hasSize(1).allMatch(item -> item.kind().equals("IDENTITY"));
    }
    @Test void longIdentityIsBoundedWithoutChoosingAnUnrelatedLaterParagraph() throws Exception {
        String identity = "这是当前项目的说明。".repeat(80);
        Files.writeString(root.resolve("README.md"), "# 项目\n" + identity + "\n另一段不会成为项目身份的说明。\n");
        assertThat(collector.collectDeclarations(source())).hasSize(1).first().satisfies(item -> {
            assertThat(item.text()).isEqualTo(identity.substring(0, 400));
            assertThat(item.line()).isEqualTo(2);
        });
    }
}
