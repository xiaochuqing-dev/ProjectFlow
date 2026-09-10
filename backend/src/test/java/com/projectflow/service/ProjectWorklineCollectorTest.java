package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProjectWorklineCollectorTest {
    static final String MAIN = "a".repeat(40), HEAD = "b".repeat(40), OTHER = "c".repeat(40);
    final ObjectMapper mapper = new ObjectMapper();
    final List<List<String>> calls = new ArrayList<>();
    String branches = "refs/heads/main\t" + MAIN + "\t2026-09-01T00:00:00Z\nrefs/heads/feature\t" + HEAD + "\t2026-09-08T00:00:00Z";
    String prs = "[]", counts = "2 3", mergeBase = MAIN;
    boolean github = true;
    String defaultBranch = "main";

    ProjectWorklineCollector.Snapshot collect() {
        LocalCommandExecutor executor = (root, command, timeout) -> {
            calls.add(command);
            String joined = String.join(" ", command), output = ""; int code = 0;
            if (joined.startsWith("git for-each-ref")) output = branches;
            else if (joined.startsWith("git symbolic-ref")) output = defaultBranch.isBlank() ? "" : "refs/remotes/origin/" + defaultBranch;
            else if (joined.equals("gh api repos/example/repository")) {
                if (github) output = "{\"default_branch\":\"main\"}"; else code = 1;
            } else if (joined.startsWith("gh api")) output = "[]";
            else if (joined.startsWith("gh pr list")) output = prs;
            else if (joined.startsWith("gh pr view")) output = "{}";
            else if (joined.startsWith("git cat-file")) output = "commit";
            else if (joined.startsWith("git merge-base")) output = mergeBase;
            else if (joined.startsWith("git rev-list")) output = command.contains(MAIN + "..." + MAIN) ? "0 0" : counts;
            else if (joined.contains(" diff ")) output = "src/provider/settings.ts\ntests/provider.test.ts";
            else if (joined.startsWith("git log")) output = "2026-09-08T00:00:00Z\tDeveloper\tImplement provider editing";
            return new LocalCommandExecutor.CommandResult(code, output, false);
        };
        return new ProjectWorklineCollector(executor, new SensitiveContentRedactor(), mapper).collect(Path.of("."), "https://github.com/example/repository");
    }
    ProjectWorklineCollector.Workline feature() { return collect().items().stream().filter(item -> item.branch().equals("feature")).findFirst().orElseThrow(); }
    void pr(String state, boolean draft, String base, String headSha) {
        prs = "[{\"number\":23,\"title\":\"Provider 编辑目标\",\"state\":\"" + state
            + "\",\"isDraft\":" + draft + ",\"baseRefName\":\"" + base + "\",\"headRefName\":\"feature\",\"headRefOid\":\""
            + headSha + "\",\"updatedAt\":\"2026-09-09T00:00:00Z\"}]";
    }
    @Test void singleBranchIsJustMain() { branches = branches.lines().findFirst().orElseThrow(); assertThat(collect().items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo("MAIN")); }
    @Test void noPrDoesNotInferPurposeFromBranchNameOrClaimUnmerged() { var item = feature(); assertThat(item.classification()).isEqualTo("INFERRED"); assertThat(item.purpose()).doesNotContain("feature"); assertThat(item.mergeState()).isEqualTo("UNKNOWN"); assertThat(item.sources()).isNotEmpty(); }
    @Test void openPrIsDeclaredAndInReview() { pr("OPEN", false, "main", HEAD); var item = feature(); assertThat(item.state()).isEqualTo("REVIEW"); assertThat(item.classification()).isEqualTo("DECLARED"); assertThat(item.purpose()).isEqualTo("Provider 编辑目标"); }
    @Test void draftIsDeveloping() { pr("OPEN", true, "main", HEAD); assertThat(feature().state()).isEqualTo("DEVELOPING"); }
    @Test void stackedBaseIsAnObservedDependency() { pr("OPEN", true, "prior-work", HEAD); var item = feature(); assertThat(item.state()).isEqualTo("DEPENDENT"); assertThat(item.dependsOn()).isEqualTo("prior-work"); }
    @Test void squashMergeAuthorityOverridesDivergentRawCommits() { pr("MERGED", false, "main", HEAD); var item = feature(); assertThat(item.ahead()).isEqualTo(3); assertThat(item.mergeState()).isEqualTo("MERGED"); assertThat(item.mergeBasis()).isEqualTo("GITHUB_PR_MERGED_HEAD"); }
    @Test void newCommitsAfterMergedPrAreUnknown() { pr("MERGED", false, "main", OTHER); assertThat(feature().mergeState()).isEqualTo("UNKNOWN"); }
    @Test void containedCommitsAreHistoricalWithoutAPr() { counts = "3 0"; assertThat(feature().state()).isEqualTo("HISTORY"); }
    @Test void ambiguousAncestryRemainsUnknown() { mergeBase = ""; var item = feature(); assertThat(item.ahead()).isNull(); assertThat(item.mergeState()).isEqualTo("UNKNOWN"); }
    @Test void githubUnavailableKeepsLocalWorklineAndNoFakeFreshness() { github = false; var snapshot = collect(); assertThat(snapshot.githubStatus()).isEqualTo("UNAVAILABLE"); assertThat(snapshot.githubObservedAt()).isBlank(); assertThat(snapshot.items()).hasSize(2); }
    @Test void missingDefaultBranchIsNotGuessed() { github = false; defaultBranch = ""; assertThat(collect().defaultBranchSource()).isEqualTo("UNKNOWN"); }
    @Test void oldBranchActivityReducesWeight() { branches = branches.replace("2026-09-08", "2020-01-01"); assertThat(feature().state()).isEqualTo("INACTIVE"); }
    @Test void manyBranchesHaveExplicitBoundsAndLongNamesAreData() {
        StringBuilder refs = new StringBuilder(branches);
        for (int i = 0; i < 400; i++) refs.append("\nrefs/heads/long-" + "x".repeat(90) + i + "\t" + OTHER + "\t2020-01-01T00:00:00Z");
        branches = refs.toString(); var snapshot = collect(); assertThat(snapshot.items()).hasSize(300); assertThat(snapshot.truncated()).isTrue(); assertThat(snapshot.detailedCount()).isEqualTo(60);
        assertThat(calls).noneMatch(command -> command.contains("push") || command.contains("fetch") || command.contains("checkout"));
        assertThat(calls.stream().filter(command -> command.contains("merge-base")).flatMap(Collection::stream)).noneMatch(value -> value.startsWith("long-"));
    }
}
