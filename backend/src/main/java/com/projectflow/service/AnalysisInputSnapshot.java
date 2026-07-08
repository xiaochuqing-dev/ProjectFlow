package com.projectflow.service;

import java.util.List;

/**
 * V3.3.3: 统一"分析输入快照"。把本地 Git、工作区、GitHub、Agent result、扫描范围
 * 整理成结构化事实交给模型，让模型基于多来源证据灵活判断当前真实开发状态，
 * 而不是只看 commit message 和文件列表。
 *
 * 这是纯数据记录，不持久化（由 ChangeBatch.analysisScope 记录口径摘要）。
 */
public record AnalysisInputSnapshot(
    GitFacts git,
    WorktreeFacts worktree,
    GitHubFacts github,
    AgentResultFacts agentResults,
    ScanScopeFacts scanScope
) {
    public record GitFacts(
        String branch,
        String headCommit,
        String baseCommit,
        boolean firstScan,
        int commitCount,
        List<String> commitMessages,
        List<String> changedFiles,
        List<String> diffHints
    ) {
    }

    public record WorktreeFacts(
        boolean hasUnstaged,
        boolean hasStaged,
        boolean hasUntracked,
        boolean worktreeDirty,
        List<String> unstagedFiles,
        List<String> stagedFiles,
        List<String> untrackedFiles,
        boolean possiblyUnfinished
    ) {
    }

    public record GitHubFacts(
        boolean installed,
        boolean authenticated,
        boolean detected,
        String repo,
        String upstream,
        int localAhead,
        int remoteAhead,
        String relation,
        boolean githubParticipated,
        String githubStatus
    ) {
    }

    public record AgentResultFacts(
        int count,
        List<String> taskGoals,
        List<String> referencedFiles,
        boolean overlapsWithGitDiff,
        boolean onlyAgentResultsWithoutCode
    ) {
    }

    public record ScanScopeFacts(
        int inputCommitCount,
        int inputFileCount,
        int inputAgentResultCount,
        boolean includesUncommitted,
        boolean githubParticipated,
        boolean modelParticipated,
        boolean evidenceGap,
        // V3.3.4: 证据缺口的具体原因（无缺口时为空串）。供口径展示和模型 prompt 使用。
        String evidenceGapReason
    ) {
        // 兼容旧调用：未提供原因时默认空串。
        public ScanScopeFacts(
            int inputCommitCount,
            int inputFileCount,
            int inputAgentResultCount,
            boolean includesUncommitted,
            boolean githubParticipated,
            boolean modelParticipated,
            boolean evidenceGap
        ) {
            this(inputCommitCount, inputFileCount, inputAgentResultCount, includesUncommitted,
                githubParticipated, modelParticipated, evidenceGap, "");
        }
    }
}
