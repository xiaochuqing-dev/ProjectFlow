package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.ClaimAttribution;

class ProjectHistoryChapterRepresentationPlannerTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();
    private final ProjectHistoryChapterRepresentationPlanner planner =
        new ProjectHistoryChapterRepresentationPlanner(language);

    @Test
    void a_largeCoherentPhaseKeepsRelatedOutcomeClustersInOneChapter() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("auth", "建立登录流程并形成登录入口", 12, 0, "IMPLEMENTED")
        );
        stories.addAll(stories("export", "建立成果导出并形成下载结果", 10, 0, "IMPLEMENTED"));

        var plan = planner.plan(stories);
        String summary = language.chapterSummary(
            plan.representativeOutcomes(), plan.primaryStoryCount(), plan.supportingStoryCount(),
            plan.selectedClusters().size()
        );

        assertThat(stories).hasSizeGreaterThanOrEqualTo(20);
        assertThat(plan.clusters()).hasSize(2);
        assertThat(plan.representativePrimaryCoverage()).isEqualTo(1.0);
        assertThat(planner.split(stories)).hasSize(1);
        assertThat(summary).contains("登录", "导出");
    }

    @Test
    void b_heterogeneousPhaseSplitsAtAChronologicalSemanticShift() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("auth", "建立登录流程并形成登录入口", 6, 0, "IMPLEMENTED")
        );
        stories.addAll(stories("export", "建立成果导出并形成下载结果", 6, 14, "IMPLEMENTED"));

        List<List<ChangeStory>> split = planner.split(stories);

        assertThat(split).hasSize(2);
        assertThat(split).allSatisfy(group -> assertThat(group.stream().filter(ChangeStory::primary)).hasSize(6));
        assertThat(planner.plan(split.get(0)).clusters()).hasSize(1);
        assertThat(planner.plan(split.get(1)).clusters()).hasSize(1);
    }

    @Test
    void c_minorFirstStoryCannotBecomeTheDominantCluster() {
        List<ChangeStory> stories = new ArrayList<>();
        stories.add(story("minor-first", "readme", "补充项目使用说明并形成启动指引", 0,
            "OBSERVED", "PRIMARY", ""));
        stories.addAll(stories("auth", "建立登录流程并形成登录入口", 8, 1, "IMPLEMENTED"));

        var plan = planner.plan(stories);

        assertThat(plan.selectedClusters().get(0).representativeOutcomes())
            .allMatch(value -> value.contains("登录"));
        assertThat(plan.dominantClusterIds()).containsExactly(plan.selectedClusters().get(0).id());
    }

    @Test
    void d_supportingHeavyWorkDoesNotOutweighItsPrimaryOutcome() {
        ChangeStory primary = story("auth-primary", "auth", "建立登录流程并形成登录入口", 0,
            "IMPLEMENTED", "PRIMARY", "");
        List<ChangeStory> stories = new ArrayList<>(List.of(primary));
        for (int index = 0; index < 30; index++) {
            stories.add(story("support-" + index, "tests", "补充登录验证记录", index + 1,
                "VERIFIED", "SUPPORTING", primary.id()));
        }

        var plan = planner.plan(stories);

        assertThat(plan.primaryStoryCount()).isEqualTo(1);
        assertThat(plan.supportingStoryCount()).isEqualTo(30);
        assertThat(plan.clusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.primaryStoryCount()).isEqualTo(1);
            assertThat(cluster.supportingStoryCount()).isEqualTo(30);
            assertThat(cluster.role()).isEqualTo("DOMINANT");
        });
    }

    @Test
    void e_oneDominantAndOneMinorKeepsTheDominantTitleAndMinorSummaryCoverage() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("auth", "建立登录流程并形成登录入口", 8, 0, "IMPLEMENTED")
        );
        stories.add(story("readme-one", "readme", "补充项目使用说明并形成启动指引", 8,
            "OBSERVED", "PRIMARY", ""));

        var plan = planner.plan(stories);
        String title = language.chapterTitle(
            plan.representativeOutcomes(), List.of(), START, START.plus(9, ChronoUnit.DAYS),
            plan.dominantClusterCount()
        );
        String summary = language.chapterSummary(
            plan.representativeOutcomes(), plan.primaryStoryCount(), plan.supportingStoryCount(),
            plan.selectedClusters().size()
        );

        assertThat(title).contains("登录").doesNotContain("使用说明");
        assertThat(summary).contains("登录", "使用说明");
    }

    @Test
    void f_twoCoDominantOutcomesProduceAnAuditableDualCenterPlan() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("auth", "建立登录流程并形成登录入口", 6, 0, "IMPLEMENTED")
        );
        stories.addAll(stories("export", "建立成果导出并形成下载结果", 5, 1, "IMPLEMENTED"));

        var plan = planner.plan(stories);
        String title = language.chapterTitle(
            plan.representativeOutcomes(), List.of(), START, START.plus(11, ChronoUnit.DAYS),
            plan.dominantClusterCount()
        );

        assertThat(plan.dominantClusterIds()).hasSize(2);
        assertThat(plan.selectedClusters()).filteredOn(cluster -> !"MINOR".equals(cluster.role())).hasSize(2);
        assertThat(title).contains("登录", "导出");
    }

    @Test
    void g_nonCodeArtifactsRemainNeutralFirstLayerOutcomes() {
        List<ChangeStory> stories = List.of(
            story("deck", "quarterly-presentation", "建立演示文稿并形成汇报版本", 0,
                "OBSERVED", "PRIMARY", ""),
            story("report", "research-report", "整理研究报告并形成研究结论", 1,
                "OBSERVED", "PRIMARY", ""),
            story("data", "data", "整理数据分析结果并形成汇总表", 2,
                "OBSERVED", "PRIMARY", "")
        );

        var plan = planner.plan(stories);
        String firstLayer = String.join(" ", plan.representativeOutcomes());

        assertThat(firstLayer).contains("演示文稿", "研究报告", "数据分析结果")
            .doesNotContain("Controller", "Service", "后端", "能力");
    }

    @Test
    void h_noGitEvidenceStillProducesADeterministicPlan() {
        ChangeStory document = story("document", "research-report", "整理研究报告并形成研究结论", 0,
            "DECLARED", "PRIMARY", "");

        var plan = planner.plan(List.of(document));

        assertThat(document.evidenceRefs()).allMatch(value -> !value.startsWith("commit:"));
        assertThat(plan.clusters()).hasSize(1);
        assertThat(plan.fingerprint()).hasSize(64);
    }

    @Test
    void i_userDeclaredPresentationMetadataIsNeverMutatedByPlanning() {
        ChangeStory declared = userDeclared(story(
            "declared", "auth", "用户命名的登录阶段", 0, "DECLARED", "PRIMARY", ""
        ));

        planner.plan(List.of(declared));

        assertThat(declared.presentationAuthority()).isEqualTo("USER_DECLARED_PRESENTATION");
        assertThat(declared.humanTitle()).isEqualTo("用户命名的登录阶段");
        assertThat(declared.userCorrectionRefs()).containsExactly("correction:user");
    }

    @Test
    void j_deterministicFallbackRepresentsThePhaseInsteadOfTheFirstMinorStory() {
        List<ChangeStory> stories = new ArrayList<>();
        stories.add(story("config-first", "environment-example", "补充环境配置示例并形成配置模板", 0,
            "CONFIGURED", "PRIMARY", ""));
        stories.addAll(stories("project-history", "建立项目历程并形成可读历史", 12, 1, "IMPLEMENTED"));

        var plan = planner.plan(stories);
        String title = language.chapterTitle(
            plan.representativeOutcomes(), List.of(), START, START.plus(13, ChronoUnit.DAYS),
            plan.dominantClusterCount()
        );

        assertThat(title).contains("项目历程").doesNotStartWith("补充环境配置示例");
    }

    @Test
    void k_claimStateAndSupportingOwnershipRemainTruthful() {
        ChangeStory declared = story("declared", "research-report", "整理研究报告并形成方案记录", 0,
            "DECLARED", "PRIMARY", "");
        ChangeStory supporting = story("support", "tests", "补充验证材料", 1,
            "VERIFIED", "SUPPORTING", declared.id());

        var plan = planner.plan(List.of(declared, supporting));

        assertThat(plan.clusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.allowedClaimStates()).containsExactly("DECLARED");
            assertThat(cluster.claimCeiling()).isEqualTo("DECLARED");
            assertThat(cluster.supportingStoryCount()).isEqualTo(1);
        });
    }

    @Test
    void l_planIsInternalAndDoesNotChangeStoryIdentityOrMembership() {
        List<ChangeStory> stories = stories("auth", "建立登录流程并形成登录入口", 4, 0, "IMPLEMENTED");
        List<String> ids = stories.stream().map(ChangeStory::id).toList();

        planner.plan(stories);

        assertThat(stories).extracting(ChangeStory::id).containsExactlyElementsOf(ids);
        assertThat(stories).allSatisfy(story -> assertThat(story.eventRefs()).hasSize(1));
    }

    @Test
    void m_planPublishesExactRequiredClusterIdsForRepairValidation() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("auth", "建立登录流程并形成登录入口", 5, 0, "IMPLEMENTED")
        );
        stories.addAll(stories("export", "建立成果导出并形成下载结果", 4, 1, "IMPLEMENTED"));

        var plan = planner.plan(stories);

        assertThat(plan.requiredRepresentativeClusterIds())
            .containsExactlyElementsOf(plan.selectedClusters().stream().map(cluster -> cluster.id()).toList())
            .doesNotHaveDuplicates();
    }

    @Test
    void n_oversizedHistoryKeepsRepresentativePlanBounded() {
        List<ChangeStory> stories = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            stories.add(story("topic-" + index, "topic-" + index,
                "整理主题" + index + "并形成可查看结果", index, "OBSERVED", "PRIMARY", ""));
        }

        var plan = planner.plan(stories);

        assertThat(plan.selectedClusters()).hasSizeLessThanOrEqualTo(
            ProjectHistoryChapterRepresentationPlanner.MAX_REPRESENTATIVE_CLUSTERS
        );
        assertThat(plan.selectedClusters()).allSatisfy(cluster ->
            assertThat(cluster.representativeStoryIds()).hasSizeLessThanOrEqualTo(
                ProjectHistoryChapterRepresentationPlanner.MAX_REPRESENTATIVE_STORIES_PER_CLUSTER
            )
        );
        assertThat(planner.split(stories("auth", "建立登录流程并形成登录入口", 60, 0, "IMPLEMENTED")))
            .as("a large coherent history must not be split by Story count alone")
            .hasSize(1);
    }

    @Test
    void o_smallMixedHistoryDoesNotExplodeIntoTinyChapters() {
        List<ChangeStory> stories = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            stories.add(story("small-" + index, "subject-" + index,
                "整理小型成果" + index + "并形成记录", index, "OBSERVED", "PRIMARY", ""));
        }

        assertThat(planner.split(stories)).hasSize(1);
    }

    @Test
    void genericMergeLikeMetadataCannotOutweighAConcreteOutcomeByCountAlone() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("pull-request", "整理项目材料并记录合并信息", 8, 0, "OBSERVED")
        );
        stories.addAll(stories("auth", "建立登录流程并形成登录入口", 2, 0, "IMPLEMENTED"));

        var plan = planner.plan(stories);

        assertThat(plan.selectedClusters().get(0).humanLabel()).contains("登录");
        assertThat(plan.selectedClusters().get(0).role()).isEqualTo("DOMINANT");
    }

    @Test
    void genericDocumentSubjectsFormOneLowWeightDigestInsteadOfDilutingCoverage() {
        List<ChangeStory> stories = new ArrayList<>(
            stories("auth", "建立登录流程并形成登录入口", 6, 0, "IMPLEMENTED")
        );
        for (int index = 0; index < 20; index++) {
            stories.add(story("document-" + index, "v1." + index,
                "记录项目阶段文档并保留核对材料", index, "OBSERVED", "PRIMARY", ""));
        }

        var plan = planner.plan(stories);

        assertThat(plan.clusters()).filteredOn(cluster -> "阶段成果记录".equals(cluster.humanLabel()))
            .singleElement().satisfies(cluster -> {
                assertThat(cluster.primaryStoryCount()).isEqualTo(20);
                assertThat(cluster.role()).isEqualTo("MINOR");
            });
        assertThat(plan.selectedClusters().get(0).humanLabel()).contains("登录");
        assertThat(plan.representativePrimaryCoverage()).isGreaterThanOrEqualTo(0.72);
    }

    @Test
    void frontendBackendAndCombinedSkeletonSubjectsShareOneSemanticFamily() {
        assertThat(ProjectHistoryChapterRepresentationPlanner.semanticFamily("前端项目骨架"))
            .isEqualTo("项目骨架");
        assertThat(ProjectHistoryChapterRepresentationPlanner.semanticFamily("后端项目骨架"))
            .isEqualTo("项目骨架");
        assertThat(ProjectHistoryChapterRepresentationPlanner.semanticFamily("前后端项目骨架"))
            .isEqualTo("项目骨架");

        List<ChangeStory> stories = List.of(
            story("frontend", "project-skeleton", "建立前端项目骨架并形成初始页面", 0,
                "OBSERVED", "PRIMARY", ""),
            story("backend", "project-skeleton", "建立后端项目骨架并形成初始服务", 1,
                "OBSERVED", "PRIMARY", ""),
            story("full-stack", "project-skeleton", "实现前后端项目骨架并形成可使用功能", 2,
                "IMPLEMENTED", "PRIMARY", "")
        );

        var plan = planner.plan(stories);

        assertThat(plan.clusters()).hasSize(1);
        assertThat(plan.selectedClusters().get(0)).satisfies(cluster -> {
            assertThat(cluster.primaryStoryCount()).isEqualTo(3);
            assertThat(cluster.headlineOutcome()).contains("前后端项目骨架");
            assertThat(cluster.role()).isEqualTo("DOMINANT");
        });
        assertThat(plan.representativeOutcomes().get(0)).contains("前后端项目骨架");
    }

    private List<ChangeStory> stories(String subject, String title, int count, int dayOffset, String state) {
        List<ChangeStory> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(story(subject + "-" + dayOffset + "-" + index, subject, title, dayOffset + index,
                state, "PRIMARY", ""));
        }
        return result;
    }

    private ChangeStory story(
        String id,
        String subject,
        String title,
        int dayOffset,
        String state,
        String role,
        String primaryStoryId
    ) {
        Instant occurredAt = START.plus(dayOffset, ChronoUnit.DAYS);
        String extension = switch (subject) {
            case "quarterly-presentation" -> "pptx";
            case "research-report", "readme" -> "md";
            case "data" -> "csv";
            default -> "java";
        };
        String path = ("SUPPORTING".equals(role) ? "tests/" : "results/") + subject + "." + extension;
        UUID eventId = UUID.nameUUIDFromBytes(("event:" + id).getBytes(StandardCharsets.UTF_8));
        ChangeStory value = new ChangeStory(
            id, subject, title, title + "。", "此前尚未形成该结果。", title + "。", "当前保留这一结果。",
            List.of("results"), "", List.of(), "", List.of(), List.of(), occurredAt, occurredAt,
            1, 1, "ENGINEERING_GROUPING", "DETERMINISTIC", "FULL_WITHIN_DISCOVERED_SOURCES", List.of(),
            List.of(eventId), List.of("document:" + id), role, primaryStoryId, List.of(), List.of(), List.of(),
            List.of(path), "AUTOMATIC", "", title, title + "。", List.of(), false, false, "", "ACTIVE", List.of()
        );
        return value.withClaimAttribution(new ClaimAttribution(
            language.readableObject(subject, List.of(path), List.of()), "MODIFY", state, title,
            List.of("document:" + id), List.of(), List.of("FACTUAL_SOURCE"), "DIRECT", ""
        ));
    }

    private static ChangeStory userDeclared(ChangeStory value) {
        return new ChangeStory(
            value.id(), value.primarySubjectKey(), value.humanTitle(), value.oneSentenceSummary(), value.beforeState(),
            value.change(), value.afterState(), value.affectedAreas(), value.reason(), value.reasonEvidenceRefs(),
            value.laterOutcome(), value.conflicts(), value.unknowns(), value.occurredFrom(), value.occurredTo(),
            value.evidenceCount(), value.rawEventCount(), value.authority(), value.summaryStatus(), value.coverage(),
            value.limitations(), value.eventRefs(), value.evidenceRefs(), value.role(), value.primaryStoryId(),
            value.supportingChangeRefs(), value.technicalAtomRefs(), value.commitSummaries(), value.technicalDetails(),
            "USER_DECLARED_PRESENTATION", "user-revision", value.automaticTitle(), value.automaticSummary(),
            List.of("correction:user"), value.hiddenByDefault(), value.pinned(), value.mergedIntoStoryId(),
            value.displayStatus(), value.correctionConflicts(), value.claimAttribution()
        );
    }
}
