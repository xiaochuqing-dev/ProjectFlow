package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.projectflow.dto.ProjectHistoryDtos.TimeProvenance;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.ClaimState;

class ProjectHistorySemanticUsefulnessTest {

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

    @Test void areaNamesConcreteFilesWithoutClaimingFunctionalCompletion() {
        var paths = List.of("frontend/src/NotificationPanel.tsx", "frontend/src/CommunicationAttachments.tsx");
        var wording = language.fallback(ClaimState.OBSERVED, Transition.CREATED, "project-area-frontend", paths, List.of(), List.of());
        assertThat(wording.title()).contains("通知", "沟通", "附件", "文件变更");
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
            .isEqualTo("前端项目骨架");
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
