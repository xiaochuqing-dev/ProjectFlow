package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.ClaimAttribution;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCoverage;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewContent;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectHistoryCorrection;
import com.projectflow.entity.ProjectHistoryEvent;
import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectHistoryCorrectionRepository;
import com.projectflow.repository.ProjectHistoryEventRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectHistoryCorrectionService;
import com.projectflow.service.ProjectAgentHistoryService;
import com.projectflow.service.ProjectMemoryGatewayService;
import com.projectflow.service.AuthService;
import com.projectflow.support.AppException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectHistoryCorrectionServiceTest {
    private static final Instant FIRST = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired ProjectHistoryCorrectionRepository correctionRepository;
    @Autowired ProjectHistoryEventRepository eventRepository;
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ProjectMemoryGatewayService memoryGatewayService;
    @Autowired ProjectAgentHistoryService agentHistoryService;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mockMvc;
    @Autowired EntityManager entityManager;
    @MockitoBean AuthService authService;

    private UUID ownerId;
    private ProjectSpace project;
    private ProjectHistorySnapshot snapshot;
    private UUID eventA;
    private UUID eventB;

    @BeforeEach
    void setUp() throws Exception {
        ownerId = UUID.randomUUID();
        when(authService.currentUser(any())).thenAnswer(ignored -> new AuthUser(ownerId, "history-owner", "history@example.com"));
        project = new ProjectSpace(ownerId);
        project.update("History Correction", "展示修正边界测试", ProjectStatus.BUILDING,
            List.of("Java"), "", LocalDate.of(2026, 1, 1), null);
        project = projectRepository.saveAndFlush(project);

        eventA = UUID.randomUUID();
        eventB = UUID.randomUUID();
        ChangeStory first = story("story-a", "建立登录流程", List.of(eventA, eventB), FIRST);
        ChangeStory second = story("story-b", "补充登录测试", List.of(UUID.randomUUID()), FIRST.plusSeconds(60));
        ChangeStory third = story("story-c", "整理登录说明", List.of(UUID.randomUUID()), FIRST.plusSeconds(120));
        HistoryChapter chapter = new HistoryChapter(
            "chapter-a", "登录能力形成", "登录相关工作形成一个阶段。", FIRST, FIRST.plusSeconds(120),
            List.of("EARLIEST_DISCOVERED_EVENT"), List.of(first.id(), second.id(), third.id()), 3, 4,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        EvolutionThread thread = new EvolutionThread(
            "thread-a", "login", "登录流程", "PROJECT_SUBJECT", List.of(first.id(), second.id(), third.id()),
            List.of("CREATED", "MODIFIED"), "登录流程已经形成。", List.of(), List.of(), List.of(), 4, null
        );
        snapshot = new ProjectHistorySnapshot(project.getId());
        snapshot.complete(
            "git:fixture", "fixture-source-fingerprint", 4, FIRST, FIRST.plusSeconds(120),
            "project-history-v385-semantic-compression-v1", "project-history-synthesis-v3",
            objectMapper.writeValueAsString(new HistoryOverviewContent(
                "此前尚无登录流程。", "登录流程已经形成。", List.of(), List.of(), List.of(), List.of()
            )),
            objectMapper.writeValueAsString(List.of(chapter)),
            objectMapper.writeValueAsString(List.of(first, second, third)),
            objectMapper.writeValueAsString(List.of(thread)),
            objectMapper.writeValueAsString(new HistoryCoverage(
                true, "CURRENT", 4, 4, 0, 0, java.util.Map.of("GIT", 4), List.of(), List.of()
            )),
            "{}", UUID.randomUUID(), false
        );
        snapshot = snapshotRepository.saveAndFlush(snapshot);
    }

    @Test
    void persistsRenameSummaryHidePinAndRestoresAutomaticPresentationOnly() {
        String storiesBefore = snapshot.getStoriesJson();
        String chaptersBefore = snapshot.getChaptersJson();

        correctionService.create(ownerId, project.getId(), request("RENAME_STORY", "story-a", List.of(),
            "用户可读的登录体验", "", "", "", ""));
        correctionService.create(ownerId, project.getId(), request("EDIT_SUMMARY", "story-a", List.of(),
            "", "登录流程现在有清晰的入口和结果。", "", "", ""));
        correctionService.create(ownerId, project.getId(), request("HIDE_STORY", "story-b", List.of(),
            "", "", "", "", ""));
        correctionService.create(ownerId, project.getId(), request("PIN_STORY", "story-c", List.of(),
            "", "", "", "", ""));

        var corrected = correctionService.resolve(project.getId(), snapshot);
        ChangeStory first = corrected.stories().stream().filter(item -> item.id().equals("story-a")).findFirst().orElseThrow();
        assertThat(first.humanTitle()).isEqualTo("用户可读的登录体验");
        assertThat(first.oneSentenceSummary()).isEqualTo("登录流程现在有清晰的入口和结果。");
        assertThat(first.presentationAuthority()).isEqualTo(ProjectHistoryCorrectionService.USER_DECLARED_PRESENTATION);
        assertThat(corrected.stories().stream().filter(item -> item.id().equals("story-b")).findFirst().orElseThrow().hiddenByDefault()).isTrue();
        assertThat(corrected.stories().stream().filter(item -> item.id().equals("story-c")).findFirst().orElseThrow().pinned()).isTrue();
        assertThat(corrected.presentationRevision()).startsWith("presentation:");

        correctionService.create(ownerId, project.getId(), request("RESTORE_AUTOMATIC", "story-a", List.of(),
            "", "", "", "", ""));
        ChangeStory restored = correctionService.resolve(project.getId(), snapshot).stories().stream()
            .filter(item -> item.id().equals("story-a")).findFirst().orElseThrow();
        assertThat(restored.humanTitle()).isEqualTo("建立登录流程");
        assertThat(restored.oneSentenceSummary()).isEqualTo("建立登录流程的可确认结果。");

        ProjectHistorySnapshot persisted = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        assertThat(persisted.getStoriesJson()).isEqualTo(storiesBefore);
        assertThat(persisted.getChaptersJson()).isEqualTo(chaptersBefore);
        assertThat(correctionRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).hasSize(5);
    }

    @Test
    void plannedStoryCorrectionCannotClaimImplementationOrVerification() {
        assertThatThrownBy(() -> correctionService.create(
            ownerId,
            project.getId(),
            request("RENAME_STORY", "story-a", List.of(),
                "登录流程已经实现并验证通过", "", "", "", "")
        )).isInstanceOf(AppException.class)
            .hasMessageContaining("展示修正不能把现有证据状态提升为更强事实");

        assertThat(correctionRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).isEmpty();
    }

    @Test
    void supportsRoleReattachmentMergeEventSplitAndDeclaredChapter() {
        correctionService.create(ownerId, project.getId(), request("SET_SUPPORTING", "story-b", List.of("story-a"),
            "", "", "SUPPORTING", "", ""));
        correctionService.create(ownerId, project.getId(), request("REATTACH_SUPPORTING", "story-b", List.of("story-c"),
            "", "", "SUPPORTING", "", ""));
        correctionService.create(ownerId, project.getId(), request("SET_PRIMARY", "story-b", List.of(),
            "", "", "PRIMARY", "", ""));
        correctionService.create(ownerId, project.getId(), request("MERGE_STORIES", "story-b", List.of("story-b", "story-c"),
            "合并后的登录保障", "测试和说明合并为同一项保障。", "", "", ""));
        correctionService.create(ownerId, project.getId(), request("SPLIT_STORY", "story-a", List.of(eventA.toString()),
            "拆分出的登录变化", "", "", "", ""));
        correctionService.create(ownerId, project.getId(), request("DECLARE_CHAPTER", "", List.of("story-a", "story-b"),
            "用户确认的登录阶段", "用户把核心登录工作归为一个阶段。", "", "", ""));

        var corrected = correctionService.resolve(project.getId(), snapshot);
        ChangeStory merged = corrected.stories().stream().filter(item -> item.id().equals("story-b")).findFirst().orElseThrow();
        ChangeStory mergedAway = corrected.stories().stream().filter(item -> item.id().equals("story-c")).findFirst().orElseThrow();
        assertThat(merged.humanTitle()).isEqualTo("合并后的登录保障");
        assertThat(merged.eventRefs()).hasSize(2);
        assertThat(mergedAway.displayStatus()).isEqualTo("MERGED");
        assertThat(mergedAway.hiddenByDefault()).isTrue();

        List<ChangeStory> splitParts = corrected.stories().stream()
            .filter(item -> item.id().equals("story-a") || item.id().startsWith("story-") && !List.of("story-b", "story-c").contains(item.id()))
            .filter(item -> item.eventRefs().contains(eventA) || item.eventRefs().contains(eventB)).toList();
        assertThat(splitParts).hasSize(2);
        assertThat(splitParts).allSatisfy(item -> assertThat(item.eventRefs()).hasSize(1));
        assertThat(corrected.chapters()).anySatisfy(chapter -> {
            assertThat(chapter.title()).isEqualTo("用户确认的登录阶段");
            assertThat(chapter.userDeclared()).isTrue();
            assertThat(chapter.storyRefs()).contains("story-a", "story-b");
        });
    }

    @Test
    void rejectsStalePresentationRevisionAndCrossUserAccess() {
        String revision = correctionService.list(ownerId, project.getId()).presentationRevision();
        correctionService.create(ownerId, project.getId(), request("RENAME_STORY", "story-a", List.of(),
            "第一次命名", "", "", "", revision));

        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "过期命名", "", "", "", revision
        ))).isInstanceOf(AppException.class).hasMessageContaining("展示版本已变化");
        assertThatThrownBy(() -> correctionService.list(UUID.randomUUID(), project.getId()))
            .isInstanceOf(AppException.class).hasMessageContaining("项目不存在");
    }

    @Test
    void correctionApiReturnsDeclaredDifferenceAndCorrectedReadView() throws Exception {
        mockMvc.perform(post("/api/projects/" + project.getId() + "/history/corrections")
                .header("Authorization", "Bearer local-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type":"RENAME_STORY",
                      "targetType":"STORY",
                      "targetId":"story-a",
                      "declaredTitle":"普通用户看得懂的登录结果",
                      "sourceFingerprint":"%s"
                    }
                    """.formatted(snapshot.getSourceEventFingerprint())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.declaredTitle").value("普通用户看得懂的登录结果"))
            .andExpect(jsonPath("$.data.automaticValue").value("建立登录流程"))
            .andExpect(jsonPath("$.data.difference").value("用户声明覆盖自动展示"))
            .andExpect(jsonPath("$.data.targetPresent").value(true));

        mockMvc.perform(get("/api/projects/" + project.getId() + "/history/stories/story-a")
                .header("Authorization", "Bearer local-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.story.humanTitle").value("普通用户看得懂的登录结果"))
            .andExpect(jsonPath("$.data.story.presentationAuthority").value("USER_DECLARED_PRESENTATION"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleRevisionConflictIsCommittedAsAuditableNonActiveRow() {
        String staleRevision = "presentation:stale-revision";
        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "不会覆盖自动结果", "", "", "", staleRevision
        ))).isInstanceOf(AppException.class)
            .satisfies(error -> {
                AppException app = (AppException) error;
                assertThat(app.getCode()).isEqualTo("PROJECT_HISTORY_CORRECTION_CONFLICT");
                assertThat(app.getStatus().value()).isEqualTo(409);
            });

        entityManager.clear();
        assertThat(correctionRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
            .anySatisfy(value -> {
                assertThat(value.getStatus()).isEqualTo(ProjectHistoryCorrection.Status.CONFLICT);
                assertThat(value.getConflictReason()).isNotBlank();
            });
        assertThat(correctionService.resolve(project.getId(), snapshotRepository.findByProjectId(project.getId()).orElseThrow())
            .stories()).noneMatch(value -> value.humanTitle().equals("不会覆盖自动结果"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleSourceConflictIsCommittedAndDoesNotBecomeActive() {
        HistoryCorrectionRequest staleSource = new HistoryCorrectionRequest(
            "RENAME_STORY", "STORY", "story-a", List.of(), "来源已变化", "", "", "",
            "", "source-that-is-not-current", "", "", "", "", "", ""
        );
        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), staleSource))
            .isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode())
                .isEqualTo("PROJECT_HISTORY_CORRECTION_STALE"));

        entityManager.clear();
        assertThat(correctionRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
            .anySatisfy(value -> assertThat(value.getStatus()).isEqualTo(ProjectHistoryCorrection.Status.CONFLICT));
        assertThat(correctionRepository.findByProjectIdAndStatusOrderByCreatedAtAscIdAsc(
            project.getId(), ProjectHistoryCorrection.Status.ACTIVE)).isEmpty();
    }

    @Test
    void rejectsOversizedCorrectionInputWithoutTruncatingTargetsOrText() {
        List<String> tooMany = IntStream.range(0, 101).mapToObj(index -> "story-" + index).toList();
        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "MERGE_STORIES", "story-a", tooMany, "", "", "", "", ""
        ))).isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode())
                .isEqualTo("TOO_MANY_HISTORY_CORRECTION_TARGETS"));

        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "x".repeat(181), List.of(), "标题", "", "", "", ""
        ))).isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode())
                .isEqualTo("INVALID_HISTORY_CORRECTION_TARGET"));

        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "x".repeat(8_001), "", "", "", ""
        ))).isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode())
                .isEqualTo("INVALID_HISTORY_CORRECTION_CONTENT"));

        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "EDIT_SUMMARY", "story-a", List.of(), "", "x".repeat(12_001), "", "", ""
        ))).isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode())
                .isEqualTo("INVALID_HISTORY_CORRECTION_CONTENT"));
        assertThat(correctionRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).isEmpty();
    }

    @Test
    void correctionsArePagedAcrossHistoryApiAndGatewayWithoutSilentlyDroppingRows() throws Exception {
        List<ProjectHistoryCorrection> rows = IntStream.range(0, 2_500)
            .mapToObj(this::persistedCorrection).toList();
        correctionRepository.saveAllAndFlush(rows);

        var first = correctionService.list(ownerId, project.getId(), 0, 100);
        var last = correctionService.list(ownerId, project.getId(), 24, 100);
        assertThat(first.items()).hasSize(100);
        assertThat(last.items()).hasSize(100);
        assertThat(first.total()).isEqualTo(2_500);
        assertThat(last.total()).isEqualTo(2_500);
        assertThat(first.activeCount()).isEqualTo(2_500);
        assertThat(first.activeLimit()).isEqualTo(2_000);
        assertThat(first.activeLimitExceeded()).isTrue();
        assertThat(first.truncated()).isTrue();
        assertThat(last.truncated()).isFalse();

        mockMvc.perform(get("/api/projects/" + project.getId() + "/history/corrections?page=24&size=100")
                .header("Authorization", "Bearer local-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(24))
            .andExpect(jsonPath("$.data.size").value(100))
            .andExpect(jsonPath("$.data.total").value(2_500))
            .andExpect(jsonPath("$.data.items.length()").value(100))
            .andExpect(jsonPath("$.data.activeLimitExceeded").value(true));

        mockMvc.perform(get("/api/projects/" + project.getId()
                + "/project-memory/history/corrections?page=24&size=100")
                .header("Authorization", "Bearer local-test")
                .header("X-ProjectFlow-Caller", "correction-pagination-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(24))
            .andExpect(jsonPath("$.data.size").value(100))
            .andExpect(jsonPath("$.data.total").value(2_500))
            .andExpect(jsonPath("$.data.items.length()").value(100))
            .andExpect(jsonPath("$.data.presentationRevision").value(first.presentationRevision()));
    }

    @Test
    void restoreAtActiveLimitMustActuallyRevokeAnExistingCorrection() {
        correctionRepository.saveAllAndFlush(IntStream.range(0, 2_000)
            .mapToObj(index -> persistedCorrection(index, index == 0 ? "story-a" : "legacy-" + index)).toList());

        assertThatThrownBy(() -> correctionService.create(ownerId, project.getId(), request(
            "RESTORE_AUTOMATIC", "story-b", List.of(), "", "", "", "", ""
        ))).isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode())
                .isEqualTo("PROJECT_HISTORY_CORRECTION_LIMIT_REACHED"));
        assertThat(correctionRepository.countByProjectIdAndStatus(project.getId(), ProjectHistoryCorrection.Status.ACTIVE))
            .isEqualTo(2_000);

        correctionService.create(ownerId, project.getId(), request(
            "RESTORE_AUTOMATIC", "story-a", List.of(), "", "", "", "", ""
        ));
        assertThat(correctionRepository.countByProjectIdAndStatus(project.getId(), ProjectHistoryCorrection.Status.ACTIVE))
            .isEqualTo(2_000);
    }

    @Test
    void sourceChangeWithSameMembershipStillReplaysCorrection() throws Exception {
        correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "用户确认的标题", "", "", "", ""
        ));
        rewriteSnapshot("new-source-fingerprint", List.of(
            story("story-a", "建立登录流程", List.of(eventA, eventB), FIRST),
            story("story-b", "补充登录测试", List.of(UUID.randomUUID()), FIRST.plusSeconds(60)),
            story("story-c", "整理登录说明", List.of(UUID.randomUUID()), FIRST.plusSeconds(120))
        ));

        var corrected = correctionService.resolve(project.getId(), snapshotRepository.findByProjectId(project.getId()).orElseThrow());
        assertThat(corrected.stories().stream().filter(value -> value.id().equals("story-a")).findFirst().orElseThrow().humanTitle())
            .isEqualTo("用户确认的标题");
        var listed = correctionService.list(ownerId, project.getId()).items().stream()
            .filter(value -> value.targetId().equals("story-a")).findFirst().orElseThrow();
        assertThat(listed.sourceStale()).isTrue();
        assertThat(listed.membershipStale()).isFalse();
        assertThat(listed.status()).isEqualTo("ACTIVE");
    }

    @Test
    void additiveStoryContinuationKeepsCorrectionOnlyWhenEveryOriginalMemberRemains() throws Exception {
        correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "持续沿用的用户标题", "", "", "", ""
        ));
        UUID appendedEvent = UUID.randomUUID();
        List<ChangeStory> updatedStories = List.of(
            story("story-a", "自动标题已经扩展", List.of(eventA, eventB, appendedEvent), FIRST),
            story("story-b", "补充登录测试", List.of(UUID.randomUUID()), FIRST.plusSeconds(60)),
            story("story-c", "整理登录说明", List.of(UUID.randomUUID()), FIRST.plusSeconds(120))
        );
        HistoryChapter updatedChapter = new HistoryChapter(
            "chapter-a", "登录能力形成", "登录相关工作形成一个阶段。", FIRST, FIRST.plusSeconds(120),
            List.of("EARLIEST_DISCOVERED_EVENT"), List.of("story-a", "story-b", "story-c"), 3, 5,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        EvolutionThread updatedThread = new EvolutionThread(
            "thread-a", "login", "登录流程", "PROJECT_SUBJECT", List.of("story-a", "story-b", "story-c"),
            List.of("CREATED", "MODIFIED"), "登录流程已经形成。", List.of(), List.of(), List.of(), 5, null
        );
        rewriteSnapshot("additive-source-fingerprint", updatedStories, List.of(updatedChapter), List.of(updatedThread));

        ProjectHistorySnapshot refreshed = snapshotRepository.findByProjectId(project.getId()).orElseThrow();
        ChangeStory corrected = correctionService.resolve(project.getId(), refreshed).stories().stream()
            .filter(value -> value.id().equals("story-a")).findFirst().orElseThrow();
        assertThat(corrected.humanTitle()).isEqualTo("持续沿用的用户标题");
        assertThat(corrected.eventRefs()).containsExactly(eventA, eventB, appendedEvent);
        var listed = correctionService.list(ownerId, project.getId()).items().stream()
            .filter(value -> value.targetId().equals("story-a")).findFirst().orElseThrow();
        assertThat(listed.membershipStale()).isFalse();
        assertThat(listed.additiveContinuationReplayed()).isTrue();
        assertThat(listed.difference()).contains("安全追加");
        assertThat(correctionRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()).get(0)
            .getTargetMembershipRefsJson()).contains(eventA.toString(), eventB.toString());
    }

    @Test
    void changedStoryMembershipDoesNotSilentlyApplyOldCorrection() throws Exception {
        correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "旧对象标题", "", "", "", ""
        ));
        rewriteSnapshot("rewritten-source-fingerprint", List.of(
            story("story-a", "重建后的对象", List.of(eventA, UUID.randomUUID()), FIRST),
            story("story-b", "补充登录测试", List.of(UUID.randomUUID()), FIRST.plusSeconds(60)),
            story("story-c", "整理登录说明", List.of(UUID.randomUUID()), FIRST.plusSeconds(120))
        ));

        var corrected = correctionService.resolve(project.getId(), snapshotRepository.findByProjectId(project.getId()).orElseThrow());
        ChangeStory changed = corrected.stories().stream().filter(value -> value.id().equals("story-a")).findFirst().orElseThrow();
        assertThat(changed.humanTitle()).isEqualTo("重建后的对象");
        assertThat(changed.displayStatus()).isEqualTo("CONFLICT");
        assertThat(changed.correctionConflicts()).isNotEmpty();
        var listed = correctionService.list(ownerId, project.getId()).items().stream()
            .filter(value -> value.targetId().equals("story-a")).findFirst().orElseThrow();
        assertThat(listed.membershipStale()).isTrue();
        assertThat(listed.status()).isEqualTo("CONFLICT");
    }

    @Test
    void listRevisionMatchesCorrectedReadModel() {
        String before = correctionService.list(ownerId, project.getId()).presentationRevision();
        correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "修正后的标题", "", "", "", before
        ));
        String listedRevision = correctionService.list(ownerId, project.getId()).presentationRevision();
        String resolvedRevision = correctionService.resolve(project.getId(), snapshot).presentationRevision();
        assertThat(listedRevision).isEqualTo(resolvedRevision);
    }

    @Test
    void currentStateAndAgentContextRevisionsAreStableOnNoopAndChangeWithCorrection() throws Exception {
        var firstState = memoryGatewayService.historyCurrentState(ownerId, project.getId());
        var firstPackage = agentHistoryService.contextPackage(ownerId, project.getId(), 32_000);
        var secondPackage = agentHistoryService.contextPackage(ownerId, project.getId(), 32_000);
        assertThat(firstPackage.packageRevision()).isEqualTo(secondPackage.packageRevision());
        assertThat(firstPackage.currentProjectState().stateRevision()).isEqualTo(firstState.stateRevision());
        assertThat(firstPackage.currentProjectState().modelCalled()).isFalse();

        correctionService.create(ownerId, project.getId(), request(
            "RENAME_STORY", "story-a", List.of(), "修正后持续可读的登录结果", "", "", "", ""
        ));
        var changedState = memoryGatewayService.historyCurrentState(ownerId, project.getId());
        var changedPackage = agentHistoryService.contextPackage(ownerId, project.getId(), 32_000);
        assertThat(changedState.stateRevision()).isNotEqualTo(firstState.stateRevision());
        assertThat(changedState.presentationRevision()).isEqualTo(correctionService.list(ownerId, project.getId()).presentationRevision());
        assertThat(changedPackage.packageRevision()).isNotEqualTo(firstPackage.packageRevision());
        assertThat(changedPackage.currentProjectState().stateRevision()).isEqualTo(changedState.stateRevision());

        mockMvc.perform(get("/api/projects/" + project.getId() + "/history/current-state")
                .header("Authorization", "Bearer local-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stateRevision").value(changedState.stateRevision()))
            .andExpect(jsonPath("$.data.modelCalled").value(false));
        mockMvc.perform(get("/api/projects/" + project.getId() + "/project-memory/history/current-state")
                .header("Authorization", "Bearer local-test")
                .header("X-ProjectFlow-Caller", "continuity-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stateRevision").value(changedState.stateRevision()));
    }

    @Test
    void splitPartitionsEventsEvidenceTimeAndOnlyOriginalChapterAndThread() throws Exception {
        ProjectHistoryEvent firstEvent = historyEvent(
            "split-first", FIRST, List.of("materials/part-a.md"), List.of("evidence:a", "evidence:shared"),
            ProjectHistoryEvent.Transition.CREATED
        );
        ProjectHistoryEvent secondEvent = historyEvent(
            "split-second", FIRST.plusSeconds(300), List.of("materials/part-b.md"),
            List.of("evidence:b", "evidence:shared"), ProjectHistoryEvent.Transition.MODIFIED
        );
        ChangeStory original = richStory(
            "story-split", "原始完整成果", List.of(firstEvent, secondEvent), "PRIMARY", "", List.of()
        );
        ChangeStory unrelated = story("story-unrelated", "其他成果", List.of(UUID.randomUUID()), FIRST.plusSeconds(600));
        HistoryChapter originalChapter = new HistoryChapter(
            "chapter-original", "原篇章", "只包含待拆分成果", FIRST, FIRST.plusSeconds(300),
            List.of("SOURCE_BOUNDARY"), List.of(original.id()), 1, 2,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        HistoryChapter unrelatedChapter = new HistoryChapter(
            "chapter-unrelated", "其他篇章", "不应接收拆分结果", FIRST.plusSeconds(600), FIRST.plusSeconds(600),
            List.of("SOURCE_BOUNDARY"), List.of(unrelated.id()), 1, 1,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        EvolutionThread originalThread = new EvolutionThread(
            "thread-original", "material", "材料成果", "PROJECT_SUBJECT", List.of(original.id()),
            List.of("CREATED", "MODIFIED"), original.afterState(), List.of(), List.of(), List.of(), 3, null
        );
        EvolutionThread unrelatedThread = new EvolutionThread(
            "thread-unrelated", "other", "其他成果", "PROJECT_SUBJECT", List.of(unrelated.id()),
            List.of("MODIFIED"), unrelated.afterState(), List.of(), List.of(), List.of(), 1, null
        );
        rewriteSnapshot("split-source", List.of(original, unrelated),
            List.of(originalChapter, unrelatedChapter), List.of(originalThread, unrelatedThread));

        correctionService.create(ownerId, project.getId(), new HistoryCorrectionRequest(
            "SPLIT_STORY", "STORY", original.id(), List.of(firstEvent.getId().toString()),
            "", "", "", "", "", "split-source",
            "第一部分", "第一部分摘要", "", "", "第二部分", "第二部分摘要"
        ));

        var corrected = correctionService.resolve(project.getId(), snapshotRepository.findByProjectId(project.getId()).orElseThrow());
        ChangeStory first = corrected.stories().stream().filter(value -> value.id().equals(original.id())).findFirst().orElseThrow();
        ChangeStory second = corrected.stories().stream()
            .filter(value -> !value.id().equals(original.id()) && !value.id().equals(unrelated.id())).findFirst().orElseThrow();
        assertThat(first.eventRefs()).containsExactly(firstEvent.getId());
        assertThat(second.eventRefs()).containsExactly(secondEvent.getId());
        assertThat(first.eventRefs()).doesNotContainAnyElementsOf(second.eventRefs());
        LinkedHashSet<UUID> splitEvents = new LinkedHashSet<>(first.eventRefs());
        splitEvents.addAll(second.eventRefs());
        assertThat(splitEvents).containsExactlyInAnyOrderElementsOf(original.eventRefs());
        assertThat(first.evidenceRefs()).containsExactlyInAnyOrder("evidence:a", "evidence:shared");
        assertThat(second.evidenceRefs()).containsExactlyInAnyOrder("evidence:b", "evidence:shared");
        assertThat(first.reasonEvidenceRefs()).containsExactly("evidence:a");
        assertThat(second.reasonEvidenceRefs()).containsExactly("evidence:b");
        assertThat(first.occurredFrom()).isEqualTo(FIRST);
        assertThat(first.occurredTo()).isEqualTo(FIRST);
        assertThat(second.occurredFrom()).isEqualTo(FIRST.plusSeconds(300));
        assertThat(second.occurredTo()).isEqualTo(FIRST.plusSeconds(300));
        assertThat(first.technicalAtomRefs()).containsExactly("split-first");
        assertThat(second.technicalAtomRefs()).containsExactly("split-second");
        assertThat(first.technicalDetails()).containsExactly("materials/part-a.md");
        assertThat(second.technicalDetails()).containsExactly("materials/part-b.md");
        assertThat(first.humanTitle()).isEqualTo("第一部分");
        assertThat(first.oneSentenceSummary()).isEqualTo("第一部分摘要");
        assertThat(second.humanTitle()).isEqualTo("第二部分");
        assertThat(second.oneSentenceSummary()).isEqualTo("第二部分摘要");

        HistoryChapter splitChapter = corrected.chapters().stream()
            .filter(value -> value.id().equals(originalChapter.id())).findFirst().orElseThrow();
        assertThat(splitChapter.storyRefs()).containsExactly(original.id(), second.id());
        assertThat(splitChapter.storyCount()).isEqualTo(2);
        assertThat(splitChapter.rawEventCount()).isEqualTo(2);
        assertThat(corrected.chapters().stream().filter(value -> value.id().equals(unrelatedChapter.id()))
            .findFirst().orElseThrow().storyRefs()).doesNotContain(second.id());
        EvolutionThread splitThread = corrected.threads().stream()
            .filter(value -> value.id().equals(originalThread.id())).findFirst().orElseThrow();
        assertThat(splitThread.storyRefs()).containsExactly(original.id(), second.id());
        assertThat(splitThread.transitions()).containsExactly("CREATED", "MODIFIED");
        assertThat(splitThread.evidenceCount()).isEqualTo(3);
        assertThat(corrected.threads().stream().filter(value -> value.id().equals(unrelatedThread.id()))
            .findFirst().orElseThrow().storyRefs()).doesNotContain(second.id());

        var gatewayStories = memoryGatewayService.historyStories(
            ownerId, project.getId(), null, false, null, null, 0, 20
        );
        assertThat(gatewayStories.items()).extracting(ChangeStory::id)
            .contains(original.id(), second.id(), unrelated.id());
        assertThat(memoryGatewayService.historyChapters(ownerId, project.getId(), 0, 20).items())
            .filteredOn(value -> value.id().equals(originalChapter.id())).singleElement()
            .satisfies(value -> assertThat(value.storyRefs()).containsExactly(original.id(), second.id()));
        assertThat(memoryGatewayService.historyThreads(ownerId, project.getId(), null, 0, 20).items())
            .filteredOn(value -> value.id().equals(originalThread.id())).singleElement()
            .satisfies(value -> assertThat(value.storyRefs()).containsExactly(original.id(), second.id()));
        assertThat(memoryGatewayService.historyCorrections(ownerId, project.getId()).presentationRevision())
            .isEqualTo(corrected.presentationRevision());
        assertThat(agentHistoryService.contextPackage(ownerId, project.getId(), 32_000).historicalCoverage())
            .contains("第一部分", "第二部分");
    }

    @Test
    void mergeUsesUniqueEventsEvidenceAndCleansMembershipAndRoleReferences() throws Exception {
        ProjectHistoryEvent firstEvent = historyEvent(
            "merge-first", FIRST, List.of("materials/first.md"), List.of("evidence:first"),
            ProjectHistoryEvent.Transition.CREATED
        );
        ProjectHistoryEvent sharedEvent = historyEvent(
            "merge-shared", FIRST.plusSeconds(60), List.of("materials/shared.md"), List.of("evidence:shared"),
            ProjectHistoryEvent.Transition.MODIFIED
        );
        ProjectHistoryEvent lastEvent = historyEvent(
            "merge-last", FIRST.plusSeconds(120), List.of("materials/last.md"), List.of("evidence:last"),
            ProjectHistoryEvent.Transition.MODIFIED
        );
        ChangeStory left = richStory(
            "story-left", "左侧成果", List.of(firstEvent, sharedEvent), "PRIMARY", "", List.of("story-right")
        );
        ChangeStory right = richStory(
            "story-right", "右侧成果", List.of(sharedEvent, lastEvent), "SUPPORTING", "story-left", List.of()
        );
        HistoryChapter chapter = new HistoryChapter(
            "chapter-merge", "合并篇章", "两个待合并成果", FIRST, FIRST.plusSeconds(120),
            List.of("SOURCE_BOUNDARY"), List.of(left.id(), right.id()), 2, 3,
            "ENGINEERING_GROUPING", "FULL_WITHIN_DISCOVERED_SOURCES", List.of()
        );
        EvolutionThread thread = new EvolutionThread(
            "thread-merge", "material", "材料成果", "PROJECT_SUBJECT", List.of(left.id(), right.id()),
            List.of("CREATED", "MODIFIED"), right.afterState(), List.of(), List.of(), List.of(), 3, null
        );
        rewriteSnapshot("merge-source", List.of(left, right), List.of(chapter), List.of(thread));

        correctionService.create(ownerId, project.getId(), new HistoryCorrectionRequest(
            "MERGE_STORIES", "STORY", left.id(), List.of(left.id(), right.id()),
            "", "", "", "", "", "merge-source",
            "合并后的成果", "合并后的摘要", "", "", "", ""
        ));
        var corrected = correctionService.resolve(project.getId(), snapshotRepository.findByProjectId(project.getId()).orElseThrow());
        ChangeStory merged = corrected.stories().stream().filter(value -> value.id().equals(left.id())).findFirst().orElseThrow();
        ChangeStory mergedAway = corrected.stories().stream().filter(value -> value.id().equals(right.id())).findFirst().orElseThrow();
        assertThat(merged.eventRefs()).containsExactlyInAnyOrder(firstEvent.getId(), sharedEvent.getId(), lastEvent.getId());
        assertThat(merged.rawEventCount()).isEqualTo(3);
        assertThat(merged.evidenceRefs()).containsExactlyInAnyOrder("evidence:first", "evidence:shared", "evidence:last");
        assertThat(merged.evidenceCount()).isEqualTo(3);
        assertThat(merged.supportingChangeRefs()).doesNotContain(left.id(), right.id());
        assertThat(mergedAway.displayStatus()).isEqualTo("MERGED");
        assertThat(mergedAway.hiddenByDefault()).isTrue();
        assertThat(mergedAway.mergedIntoStoryId()).isEqualTo(left.id());
        assertThat(corrected.chapters().get(0).storyRefs()).containsExactly(left.id());
        assertThat(corrected.chapters().get(0).storyCount()).isEqualTo(1);
        assertThat(corrected.chapters().get(0).rawEventCount()).isEqualTo(3);
        assertThat(corrected.threads().get(0).storyRefs()).containsExactly(left.id());
        assertThat(corrected.threads().get(0).evidenceCount()).isEqualTo(3);

        var gatewayStories = memoryGatewayService.historyStories(
            ownerId, project.getId(), null, false, null, null, 0, 20
        );
        assertThat(gatewayStories.items()).extracting(ChangeStory::id).contains(left.id()).doesNotContain(right.id());
        assertThat(memoryGatewayService.historyChapters(ownerId, project.getId(), 0, 20).items())
            .singleElement().satisfies(value -> {
                assertThat(value.storyRefs()).containsExactly(left.id());
                assertThat(value.rawEventCount()).isEqualTo(3);
            });
        assertThat(memoryGatewayService.historyThreads(ownerId, project.getId(), null, 0, 20).items())
            .singleElement().satisfies(value -> {
                assertThat(value.storyRefs()).containsExactly(left.id());
                assertThat(value.evidenceCount()).isEqualTo(3);
            });

        correctionService.create(ownerId, project.getId(), request(
            "RESTORE_AUTOMATIC", left.id(), List.of(), "", "", "", "", ""
        ));
        var restored = correctionService.resolve(project.getId(), snapshotRepository.findByProjectId(project.getId()).orElseThrow());
        assertThat(restored.stories().stream().filter(value -> !value.hiddenByDefault()).map(ChangeStory::id))
            .contains(left.id());
        assertThat(restored.stories().stream().map(ChangeStory::displayStatus)).allMatch("ACTIVE"::equals);
    }

    private ProjectHistoryCorrection persistedCorrection(int index) {
        return persistedCorrection(index, "story-a");
    }

    private ProjectHistoryCorrection persistedCorrection(int index, String targetId) {
        return new ProjectHistoryCorrection(
            project.getId(), ownerId, "RENAME_STORY", "STORY", targetId, "[]",
            "title-" + index, "summary-" + index, "", "", "", snapshot.getSourceEventFingerprint(),
            "membership-" + index, "presentation-" + index, "", ""
        );
    }

    private void rewriteSnapshot(String sourceFingerprint, List<ChangeStory> stories) throws Exception {
        snapshot.complete(
            snapshot.getProjectRevision(), sourceFingerprint, stories.size(), FIRST, FIRST.plusSeconds(120),
            snapshot.getStrategyVersion(), snapshot.getPromptVersion(), snapshot.getOverviewJson(),
            snapshot.getChaptersJson(), objectMapper.writeValueAsString(stories), snapshot.getThreadsJson(),
            snapshot.getCoverageJson(), snapshot.getDiagnosticsJson(), snapshot.getAnalysisJobId(), false
        );
        snapshot = snapshotRepository.saveAndFlush(snapshot);
    }

    private void rewriteSnapshot(
        String sourceFingerprint,
        List<ChangeStory> stories,
        List<HistoryChapter> chapters,
        List<EvolutionThread> threads
    ) throws Exception {
        snapshot.complete(
            snapshot.getProjectRevision(), sourceFingerprint,
            stories.stream().mapToInt(ChangeStory::rawEventCount).sum(),
            stories.stream().map(ChangeStory::occurredFrom).filter(java.util.Objects::nonNull)
                .min(Instant::compareTo).orElse(null),
            stories.stream().map(ChangeStory::occurredTo).filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(null),
            snapshot.getStrategyVersion(), snapshot.getPromptVersion(), snapshot.getOverviewJson(),
            objectMapper.writeValueAsString(chapters), objectMapper.writeValueAsString(stories),
            objectMapper.writeValueAsString(threads), snapshot.getCoverageJson(), snapshot.getDiagnosticsJson(),
            snapshot.getAnalysisJobId(), false
        );
        snapshot = snapshotRepository.saveAndFlush(snapshot);
    }

    private ProjectHistoryEvent historyEvent(
        String stableKey,
        Instant occurredAt,
        List<String> paths,
        List<String> evidenceRefs,
        ProjectHistoryEvent.Transition transition
    ) throws Exception {
        ProjectHistoryEvent event = new ProjectHistoryEvent(project.getId(), stableKey);
        event.replace(
            ProjectHistoryEvent.SourceType.GIT, stableKey, "revision-" + stableKey, "project-revision",
            occurredAt, occurredAt, "test-author", ProjectHistoryEvent.Scope.HISTORICAL,
            ProjectHistoryEvent.Category.COMMIT, transition, stableKey,
            objectMapper.writeValueAsString(paths), objectMapper.writeValueAsString(List.of("material")),
            objectMapper.writeValueAsString(evidenceRefs), "[]", ProjectHistoryEvent.Authority.FACTUAL_SOURCE,
            ProjectFactEpistemicStatus.OBSERVED, "{}", objectMapper.writeValueAsString(List.of()),
            "", "payload-" + stableKey
        );
        return eventRepository.saveAndFlush(event);
    }

    private ChangeStory richStory(
        String id,
        String title,
        List<ProjectHistoryEvent> sourceEvents,
        String role,
        String primaryStoryId,
        List<String> supportingChangeRefs
    ) {
        List<ProjectHistoryEvent> events = sourceEvents.stream()
            .sorted(Comparator.comparing(ProjectHistoryEvent::getOccurredAt).thenComparing(ProjectHistoryEvent::getId))
            .toList();
        List<String> evidence = events.stream().flatMap(value -> strings(value.getEvidenceRefsJson()).stream())
            .distinct().toList();
        List<String> reasonEvidence = evidence.stream().filter(value -> !value.endsWith(":shared")).toList();
        List<String> paths = events.stream().flatMap(value -> strings(value.getAffectedPathsJson()).stream())
            .distinct().toList();
        List<String> limitations = events.stream().flatMap(value -> strings(value.getLimitationsJson()).stream())
            .distinct().toList();
        Instant from = events.stream().map(ProjectHistoryEvent::getOccurredAt).min(Instant::compareTo).orElse(null);
        Instant to = events.stream().map(ProjectHistoryEvent::getOccurredAt).max(Instant::compareTo).orElse(from);
        return new ChangeStory(
            id, "material", title, title + "摘要", "此前状态", "来源记录发生变化", "当前状态",
            paths.stream().map(value -> value.split("/", 2)[0]).distinct().toList(),
            "来源记录了变化原因", reasonEvidence, "", List.of(), List.of("部分背景未知"),
            from, to, evidence.size(), events.size(), "ENGINEERING_GROUPING", "DETERMINISTIC",
            "FULL_WITHIN_DISCOVERED_SOURCES", limitations, events.stream().map(ProjectHistoryEvent::getId).distinct().toList(),
            evidence, role, primaryStoryId, supportingChangeRefs,
            events.stream().map(ProjectHistoryEvent::getStableEventKey).distinct().toList(),
            events.stream().map(ProjectHistoryEvent::getSafeSourceLabel).distinct().toList(), paths,
            "AUTOMATIC", "", title, title + "摘要", List.of(), false, false, "", "ACTIVE", List.of()
        );
    }

    private List<String> strings(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private HistoryCorrectionRequest request(
        String type, String targetId, List<String> targetIds, String title, String summary,
        String role, String chapterId, String expectedRevision
    ) {
        return new HistoryCorrectionRequest(
            type, type.contains("CHAPTER") ? "CHAPTER" : "STORY", targetId, targetIds,
            title, summary, role, chapterId, expectedRevision, snapshot.getSourceEventFingerprint()
        );
    }

    private static ChangeStory story(String id, String title, List<UUID> events, Instant occurredAt) {
        return new ChangeStory(
            id, "login", title, title + "的可确认结果。", "此前没有该结果。", "来源记录显示发生变化。",
            "该结果已经存在。", List.of("登录体验"), "", List.of(), "", List.of(), List.of("原因保持 UNKNOWN。"),
            occurredAt, occurredAt, events.size(), events.size(), "ENGINEERING_GROUPING", "DETERMINISTIC",
            "FULL_WITHIN_DISCOVERED_SOURCES", List.of(), events,
            events.stream().map(value -> "event:" + value).toList()
        ).withClaimAttribution(new ClaimAttribution(
            "登录流程", "PLAN", "PLANNED", "登录流程已有规划记录，不能确认已经实现",
            events.stream().map(value -> "event:" + value).toList(), List.of(), List.of("DECLARED"), "DIRECT",
            "规划 Evidence 不证明实际实现。"
        ));
    }
}
