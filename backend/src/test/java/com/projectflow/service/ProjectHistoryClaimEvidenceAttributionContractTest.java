package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.ClaimState;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.NarrativeViolation;

class ProjectHistoryClaimEvidenceAttributionContractTest {
    private final ProjectHistoryNarrativeEntailmentValidator validator =
        new ProjectHistoryNarrativeEntailmentValidator();

    @Test
    void projectSkeletonDoesNotBecomeAnUnrelatedFeature() {
        var envelope = envelope("project-area-backend", "后端项目骨架", List.of(
            atom("pom", "pom", Category.FILE_CHANGE, "backend/pom.xml", "commit:skeleton"),
            atom("health", "health", Category.FILE_CHANGE,
                "backend/src/main/java/com/projectflow/HealthController.java", "file:HealthController.java"),
            atom("application", "application", Category.FILE_CHANGE,
                "backend/src/main/java/com/projectflow/ProjectFlowApplication.java", "file:ProjectFlowApplication.java")
        ));

        assertThat(envelope.claimState()).isNotIn(ClaimState.VERIFIED);
        assertThat(envelope.directEvidenceRefs()).isEmpty();
    }

    @Test
    void threeIndependentFunctionsInOneCommitDoNotLendEvidenceToEachOther() {
        var envelope = envelope("checkout", "结算流程", List.of(
            atom("checkout", "checkout", Category.FILE_CHANGE, "src/CheckoutService.java", "file:checkout"),
            atom("inventory", "inventory", Category.FILE_CHANGE, "src/InventoryService.java", "file:inventory"),
            atom("notification", "notification", Category.FILE_CHANGE, "src/NotificationService.java", "file:notification")
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.IMPLEMENTED);
        assertThat(envelope.directEvidenceRefs()).containsExactly("file:checkout");
        assertThat(envelope.indirectEvidenceRefs()).containsExactlyInAnyOrder("file:inventory", "file:notification");
    }

    @Test
    void readmePlanAndUnrelatedCodeRemainPlanned() {
        var envelope = validator.envelope(new EvidenceProfile(
            "login", "登录流程", Transition.CREATED, List.of(
                atom("login-plan", "login", Category.DOCUMENT_VERSION, Transition.CREATED, Authority.DECLARED,
                    ProjectFactEpistemicStatus.DECLARED, "docs/login-plan.md", "规划登录流程", "doc:login-plan"),
                atom("inventory", "inventory", Category.FILE_CHANGE, "src/InventoryService.java", "file:inventory")
            ), false
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.PLANNED);
        assertThat(envelope.indirectEvidenceRefs()).contains("file:inventory");
    }

    @Test
    void configurationOnlyCannotBecomeDeployedOrImplemented() {
        var envelope = envelope("runtime", "运行环境配置", List.of(
            atom("runtime", "runtime", Category.FILE_CHANGE, "config/application.yml", "file:application.yml")
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.CONFIGURED);
        assertThatThrownBy(() -> validate(envelope, "运行环境配置已经完成生产部署。"))
            .isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void testOnlyDoesNotVerifyAnImplementation() {
        var envelope = validator.envelope(new EvidenceProfile(
            "export", "成果导出", Transition.MODIFIED, List.of(
                atom("export-validation", "export", Category.VALIDATION, Transition.MODIFIED,
                    Authority.FACTUAL_SOURCE, ProjectFactEpistemicStatus.VERIFIED,
                    "tests/export-validation.json", "自动化验证通过", "validation:export")
            ), false
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.OBSERVED);
    }

    @Test
    void implementationAndIndependentValidationCanBecomeVerified() {
        var envelope = validator.envelope(new EvidenceProfile(
            "export", "成果导出", Transition.MODIFIED, List.of(
                atom("export-code", "export", Category.FILE_CHANGE, Transition.MODIFIED,
                    Authority.SOURCE_BACKED, ProjectFactEpistemicStatus.OBSERVED,
                    "src/ExportService.java", "实现成果导出", "file:export"),
                atom("export-validation", "export", Category.VALIDATION, Transition.MODIFIED,
                    Authority.FACTUAL_SOURCE, ProjectFactEpistemicStatus.VERIFIED,
                    "reports/export-validation.json", "自动化验证通过", "validation:export")
            ), false
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.VERIFIED);
    }

    @Test
    void nonCodeArtifactIsObservedWithoutCodeEvidence() {
        var envelope = envelope("research-report", "研究报告", List.of(
            atom("report", "research-report", Category.DOCUMENT_VERSION,
                "research/ResearchReport.pptx", "file:research-report")
        ));

        assertThat(envelope.claimState()).isEqualTo(ClaimState.OBSERVED);
        assertThat(envelope.directEvidenceRefs()).containsExactly("file:research-report");
    }

    @Test
    void agentResultCannotPromoteAClaimToStrongFact() {
        var envelope = validator.envelope(new EvidenceProfile(
            "billing", "账单功能", Transition.MODIFIED, List.of(
                atom("agent", "billing", Category.AGENT_RESULT, Transition.MODIFIED,
                    Authority.PROCESS_EVIDENCE, ProjectFactEpistemicStatus.PROCESS_EVIDENCE,
                    "src/BillingService.java", "已实现并验证账单功能", "agent-result:billing")
            ), false
        ));

        assertThat(envelope.claimState()).isIn(ClaimState.DECLARED, ClaimState.UNKNOWN);
        assertThat(envelope.directEvidenceRefs()).isEmpty();
    }

    @Test
    void presentationRewriteCannotUpgradePlannedToVerified() {
        var envelope = validator.envelope(new EvidenceProfile(
            "login", "登录流程", Transition.CREATED, List.of(
                atom("login-plan", "login", Category.DOCUMENT_VERSION, Transition.CREATED, Authority.DECLARED,
                    ProjectFactEpistemicStatus.DECLARED, "docs/login-plan.md", "规划登录流程", "doc:login-plan")
            ), false
        ));

        assertThatThrownBy(() -> validate(envelope, "登录流程已经实现并验证通过。"))
            .isInstanceOf(NarrativeViolation.class);
    }

    @Test
    void roundTwoProjectFlowLoginP0StaysBelowImplemented() {
        List<EvidenceAtom> atoms = List.of(
            atom("login-background", "project-area-frontend", Category.FILE_CHANGE,
                "frontend/public/assets/login-background.png", "file:frontend/public/assets/login-background.png"),
            atom("next-env", "project-area-frontend", Category.FILE_CHANGE,
                "frontend/next-env.d.ts", "file:frontend/next-env.d.ts"),
            atom("next-config", "project-area-frontend", Category.FILE_CHANGE,
                "frontend/next.config.ts", "file:frontend/next.config.ts"),
            atom("package", "project-area-frontend", Category.FILE_CHANGE,
                "frontend/package.json", "file:frontend/package.json"),
            atom("postcss", "project-area-frontend", Category.FILE_CHANGE,
                "frontend/postcss.config.mjs", "file:frontend/postcss.config.mjs")
        );
        var envelope = envelope("project-area-frontend", "前端项目骨架", atoms);

        assertThat(envelope.claimState()).isNotIn(ClaimState.IMPLEMENTED, ClaimState.VERIFIED);
        assertThatThrownBy(() -> validate(envelope, "前端项目骨架已经实现登录流程。"))
            .isInstanceOf(NarrativeViolation.class);
    }

    private ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope(
        String subjectKey,
        String subjectLabel,
        List<EvidenceAtom> atoms
    ) {
        return validator.envelope(new EvidenceProfile(subjectKey, subjectLabel, Transition.CREATED, atoms, false));
    }

    private static EvidenceAtom atom(
        String atomRef,
        String subjectKey,
        Category category,
        String path,
        String evidenceRef
    ) {
        return atom(atomRef, subjectKey, category, Transition.CREATED, Authority.SOURCE_BACKED,
            ProjectFactEpistemicStatus.OBSERVED, path, "", evidenceRef);
    }

    private static EvidenceAtom atom(
        String atomRef,
        String subjectKey,
        Category category,
        Transition transition,
        Authority authority,
        ProjectFactEpistemicStatus status,
        String path,
        String label,
        String evidenceRef
    ) {
        return new EvidenceAtom(atomRef, List.of(subjectKey), category, transition, authority, status,
            List.of(path), label, List.of(evidenceRef));
    }

    private void validate(
        ProjectHistoryNarrativeEntailmentValidator.NarrativeEnvelope envelope,
        String summary
    ) {
        validator.validateStory(envelope,
            "整理" + envelope.subjectLabel() + "，记录当前变化",
            summary,
            "此前状态仍待确认。",
            "这一阶段留下了相关记录。",
            "当前状态可继续核对。",
            "",
            "目前没有足够信息确认为什么做这次调整。"
        );
    }
}
