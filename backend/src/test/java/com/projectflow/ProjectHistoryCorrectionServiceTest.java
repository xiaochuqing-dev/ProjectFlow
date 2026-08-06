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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCorrectionRequest;
import com.projectflow.dto.ProjectHistoryDtos.HistoryCoverage;
import com.projectflow.dto.ProjectHistoryDtos.HistoryOverviewContent;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectHistoryCorrectionRepository;
import com.projectflow.repository.ProjectHistorySnapshotRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectHistoryCorrectionService;
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
    @Autowired ProjectHistoryCorrectionService correctionService;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mockMvc;
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
        );
    }
}
