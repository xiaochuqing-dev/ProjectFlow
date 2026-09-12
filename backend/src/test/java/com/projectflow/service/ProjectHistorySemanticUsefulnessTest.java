package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.projectflow.dto.ProjectHistoryDtos.TimeProvenance;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.ClaimState;

class ProjectHistorySemanticUsefulnessTest {

    @Test void readableDocumentContentNeedNotRepeatTheProjectPrefixOrDanglingConjunction() {
        var gate = new ProjectHistoryNarrativeEntailmentValidator();
        assertThat(gate.semanticallyUseful("更新使用说明，补充通知和任务沟通的操作说明",
            "使用说明记录了附件与通知的操作范围，实际运行结果尚未确认。", "项目使用说明")).isTrue();
        assertThat(gate.semanticallyUseful("补充检查记录，保存持续集成和标签核对说明",
            "这些记录描述了检查和归档范围，不能据此确认验收通过。", "项目检查记录与文档")).isTrue();
        assertThat(gate.semanticallyUseful("修改项目材料，保留记录", "当前文件已更新", "通知、沟通、附件相关代码")).isFalse();
    }

    @Test void pullRequestContextNamesItsScopeWithoutPromotingAuthorClaims() {
        var value = new ProjectHistoryLanguageService().fallback(ClaimState.DECLARED, Transition.MODIFIED,
            "pull-request-51", List.of(), List.of("Pull Request #51：feat: invoice payment review；说明：tests passed"),
            List.of("MODIFIED"));
        assertThat(value.title()).contains("合并请求51", "发票", "支付", "来源声明").doesNotContain("项目材料");
        assertThat(value.summary()).contains("不能单独证明");
        assertThat(value.after()).doesNotContain("已经实现", "验证通过", "已经合入");
    }

    @Test void mixedDocumentObservationsNeverClaimFirstExistenceOfAllDocuments() {
        var value = new ProjectHistoryLanguageService().fallback(ClaimState.OBSERVED, Transition.CREATED,
            "project-area-docs", List.of("docs/guide.md", "docs/reports/check.md"), List.of(),
            List.of("MODIFIED", "CREATED"));
        assertThat(value.title()).contains("新增和修改", "项目文档").doesNotContain("首次", "初始成果");
        assertThat(value.before()).contains("不能证明").doesNotContain("此前项目中还没有");
        assertThat(value.change()).doesNotContain("首次");
    }

    @Test void reportTopicsUseSourceIdentifiersWithoutInventingDatesOrProjectRules() {
        var labels = new ProjectHistoryHumanSubjectLabelService();
        assertThat(labels.label("acme-v1-task01-report", List.of("docs/Acme_V1_Task01_Report.md"), List.of()))
            .isEqualTo("任务01报告（版本1）");
        assertThat(labels.label("other-v1-task02-run-reliability-report",
            List.of("docs/Other_V1_Task02_Run_Reliability_Report.md"), List.of()))
            .isEqualTo("运行可靠性报告（版本1 · 任务02）");
        assertThat(labels.label("change-import", List.of(".gitignore", "src/Frob.java"), List.of()))
            .doesNotContain("忽略规则");
    }

    @org.junit.jupiter.api.Test
    void commitInventoryIsNotMislabelledByOneEnvironmentFile() {
        var labels = new ProjectHistoryHumanSubjectLabelService();
        assertThat(labels.label("change-add-communication", List.of("backend/.env.example",
            "backend/notifications.py", "frontend/CommunicationAttachments.tsx"), List.of()))
            .contains("通知", "沟通", "附件").doesNotContain("环境配置", "完成", "上线");
        assertThat(labels.label("third-party-notices", List.of("THIRD_PARTY_NOTICES.md"), List.of()))
            .isEqualTo("第三方组件声明");
        assertThat(labels.label("docker-compose", List.of("docker-compose.yml"), List.of()))
            .isEqualTo("容器服务编排配置");
        assertThat(labels.label("gitattributes", List.of(".gitattributes"), List.of()))
            .isEqualTo("版本库文本与文件属性规则");
        assertThat(labels.label("清理了前端 next 和后端 target", List.of(), List.of()))
            .doesNotContain("next", "target");
    }
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();
    private final ProjectHistoryNarrativeEntailmentValidator validator = new ProjectHistoryNarrativeEntailmentValidator();

    @Test void sourceStateNeverInventsAnUnobservedBeforeState() {
        for (var state : List.of(ClaimState.PLANNED, ClaimState.DECLARED, ClaimState.CONFIGURED,
                ClaimState.IMPLEMENTED, ClaimState.VERIFIED)) {
            var wording = language.fallback(state, Transition.CREATED, "login",
                List.of("backend/LoginService.java"), List.of(), List.of("CREATED"));
            assertThat(wording.before()).doesNotContain("还没有", "已经有", "首次");
        }
    }

    @Test void areaNamesConcreteFilesWithoutClaimingFunctionalCompletion() {
        var paths = List.of("frontend/src/NotificationPanel.tsx", "frontend/src/CommunicationAttachments.tsx");
        var wording = language.fallback(ClaimState.OBSERVED, Transition.CREATED, "project-area-frontend", paths, List.of(), List.of());
        assertThat(wording.title()).contains("通知", "沟通", "附件");
        assertThat(wording.summary()).contains("变更记录");
        assertThat(wording.title()).doesNotContain("已经实现", "已完成", "上线", "NotificationPanel", "项目骨架");
        assertThat(wording.summary()).contains("不能据此确认功能运行验收通过");
        assertThat(validator.semanticallyUseful(wording.title(), wording.summary(), wording.object())).isTrue();
        assertThat(validator.semanticallyUseful("更新项目材料，保留记录", "当前文件已更新", wording.object())).isFalse();
    }

    @Test void unfamiliarProjectUsesTheSameEvidenceNamingAndUnknownFallback() {
        var named = language.readableObject("project-area-backend", List.of(
            "backend/InvoiceService.java", "backend/PaymentService.java", "backend/InventoryRepository.java"), List.of());
        assertThat(named).contains("发票", "支付", "库存").doesNotContain("ProjectFlow", "Corporation");
        assertThat(language.readableObject("unfamiliar", List.of("unknown/frob.md"), List.of())).isEqualTo("项目文档");
        assertThat(language.readableObject("login", List.of("frontend/package.json"), List.of("完成登录功能")))
            .isEqualTo("前端依赖清单").doesNotContain("登录", "项目骨架");
        var scripts = language.fallback(ClaimState.OBSERVED, Transition.MODIFIED, "project-area-scripts",
            List.of("scripts/backup-storage.ps1", "scripts/storage.ps1"), List.of(), List.of());
        assertThat(scripts.change()).containsOnlyOnce("存储脚本").contains("备份脚本").doesNotContain("备份、");
    }

    @Test void documentContextAddsSpecificityWithoutBorrowingRuntimeAuthority() {
        var result = language.fallback(ClaimState.DECLARED, Transition.MODIFIED, "memo",
            List.of("docs/memo.md"), List.of("新增发票审核范围说明"), List.of());
        assertThat(result.title()).contains("发票审核");
        assertThat(result.after()).contains("不能据此判断功能已经实现");
    }

    @Test void provenanceSeparatesEventRecordObservationAndMixedDates() {
        assertThat(TimeProvenance.fromBases(List.of("GIT_COMMIT_TIME")).eventTimeKnown()).isTrue();
        assertThat(TimeProvenance.fromBases(List.of("PR_MERGED_AT")).label()).isEqualTo("合入时间");
        assertThat(TimeProvenance.fromBases(List.of("PR_UPDATED_AT")).eventTimeKnown()).isFalse();
        assertThat(TimeProvenance.fromBases(List.of("AGENT_GIT_RECORD_TIME")).eventTimeKnown()).isFalse();
        assertThat(TimeProvenance.fromBases(List.of("SOURCE_OBSERVATION_TIME")).label()).contains("发生时间未知");
        var mixed = TimeProvenance.fromBases(List.of("GIT_COMMIT_TIME", "SOURCE_OBSERVATION_TIME"));
        assertThat(mixed.basis()).isEqualTo("MIXED");
        assertThat(mixed.eventTimeKnown()).isFalse();
        assertThat(TimeProvenance.unknown().eventTimeKnown()).isFalse();
    }
}
