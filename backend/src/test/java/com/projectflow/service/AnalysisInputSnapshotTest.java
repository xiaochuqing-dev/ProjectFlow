package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projectflow.service.AnalysisInputSnapshot.AgentResultFacts;
import com.projectflow.service.AnalysisInputSnapshot.GitFacts;
import com.projectflow.service.AnalysisInputSnapshot.GitHubFacts;
import com.projectflow.service.AnalysisInputSnapshot.ScanScopeFacts;
import com.projectflow.service.AnalysisInputSnapshot.WorktreeFacts;

/**
 * V3.3.3: 验证分析输入快照把多来源证据整理成结构化事实。
 * 模型基于这个快照灵活判断真实开发状态，而不是只看 commit message。
 */
class AnalysisInputSnapshotTest {

    @Test
    void capturesMultiSourceEvidenceForFlexibleModelJudgment() {
        GitFacts git = new GitFacts("master", "abc123", "def456", false, 5,
            List.of("feat(scan): add cursor", "fix(github): refresh"),
            List.of("backend/Scan.java", "frontend/Panel.tsx"),
            List.of("feat(scan): add cursor"));
        WorktreeFacts worktree = new WorktreeFacts(true, false, true, true,
            List.of("backend/Scan.java"), List.of(), List.of("backend/New.java"), true);
        GitHubFacts github = new GitHubFacts(true, true, true, "owner/repo", "origin/master",
            2, 0, "local_ahead", true, "CONNECTED");
        AgentResultFacts agent = new AgentResultFacts(2,
            List.of("实现扫描游标", "修复 GitHub 刷新"),
            List.of("backend/Scan.java"), true, false);
        ScanScopeFacts scope = new ScanScopeFacts(5, 2, 2, true, true, true, false);

        AnalysisInputSnapshot snapshot = new AnalysisInputSnapshot(git, worktree, github, agent, scope);

        // 本地 Git 与 GitHub 都参与，模型应基于两者灵活判断。
        assertThat(snapshot.git().commitCount()).isEqualTo(5);
        assertThat(snapshot.github().githubParticipated()).isTrue();
        assertThat(snapshot.github().relation()).isEqualTo("local_ahead");
        // 工作区有未提交变化，可能是未完成开发。
        assertThat(snapshot.worktree().worktreeDirty()).isTrue();
        assertThat(snapshot.worktree().possiblyUnfinished()).isTrue();
        // Agent result 与 Git diff 重叠，应合并分析。
        assertThat(snapshot.agentResults().overlapsWithGitDiff()).isTrue();
        // 输入规模。
        assertThat(snapshot.scanScope().inputCommitCount()).isEqualTo(5);
        assertThat(snapshot.scanScope().includesUncommitted()).isTrue();
    }

    @Test
    void flagsEvidenceGapWhenOnlyAgentResultsWithoutCode() {
        AgentResultFacts agent = new AgentResultFacts(3, List.of("任务意图"), List.of("src/x.java"), false, true);
        ScanScopeFacts scope = new ScanScopeFacts(0, 0, 3, false, false, true, true);

        AnalysisInputSnapshot snapshot = new AnalysisInputSnapshot(
            new GitFacts("master", "abc", "", true, 0, List.of(), List.of(), List.of()),
            new WorktreeFacts(false, false, false, false, List.of(), List.of(), List.of(), false),
            new GitHubFacts(false, false, false, "", "", 0, 0, "github_unavailable", false, "NOT_INSTALLED"),
            agent, scope
        );

        // 只有 Agent result 缺少代码变化 → 证据缺口。
        assertThat(snapshot.agentResults().onlyAgentResultsWithoutCode()).isTrue();
        assertThat(snapshot.scanScope().evidenceGap()).isTrue();
    }
}
