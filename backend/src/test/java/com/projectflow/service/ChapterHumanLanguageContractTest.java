package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectHistoryEvent.Transition;

class ChapterHumanLanguageContractTest {
    private final ProjectHistoryLanguageService language = new ProjectHistoryLanguageService();

    @Test
    void chapterDescribesStageFocusAndKeepsSupportingWorkSecondary() {
        String title = language.chapterTitle(
            List.of("新增登录入口，形成首个可确认版本", "补充登录回归测试，为主要结果提供支撑"),
            List.of(Transition.CREATED, Transition.MODIFIED), Instant.EPOCH, Instant.EPOCH.plusSeconds(60)
        );
        String summary = language.chapterSummary(List.of("登录入口", "登录回归测试"), 1, 1);

        assertThat(title).contains("项目基础建设").doesNotContain("登录入口、登录回归测试", "整理项目材料并形成阶段结果");
        assertThat(summary).contains("项目基础建设", "支撑工作保留在工程详情中")
            .doesNotContain("1 项主要结果", "1 项支撑工作");
        assertThat(title + summary).doesNotContain("PRIMARY", "SUPPORTING", "ENGINEERING_GROUPING");
    }
}
