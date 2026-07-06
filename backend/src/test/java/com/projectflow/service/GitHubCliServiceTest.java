package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.service.LocalCommandExecutor.CommandResult;

class GitHubCliServiceTest {
    @Test
    void readsAuthenticatedRepositoryMetadataWithoutExposingTokens() {
        StubExecutor executor = new StubExecutor();
        executor.reply("git remote -v", 0, "origin\thttps://github.com/example/projectflow.git (fetch)\norigin\thttps://github.com/example/projectflow.git (push)");
        executor.reply("git branch --show-current", 0, "master\n");
        executor.reply("gh --version", 0, "gh version 2.70.0");
        executor.reply("gh auth status", 0, "Logged in to github.com");
        executor.reply("gh repo view --json nameWithOwner,url,defaultBranchRef,primaryLanguage,visibility", 0, """
            {"nameWithOwner":"example/projectflow","url":"https://github.com/example/projectflow","defaultBranchRef":{"name":"master"},"primaryLanguage":{"name":"TypeScript"},"visibility":"PUBLIC"}
            """);

        var status = new GitHubCliService(executor, new ObjectMapper()).inspect(Path.of("."));

        assertThat(status.ghInstalled()).isTrue();
        assertThat(status.ghAuthenticated()).isTrue();
        assertThat(status.repoDetected()).isTrue();
        assertThat(status.nameWithOwner()).isEqualTo("example/projectflow");
        assertThat(status.defaultBranch()).isEqualTo("master");
        assertThat(status.currentBranch()).isEqualTo("master");
        assertThat(status.primaryLanguage()).isEqualTo("TypeScript");
        assertThat(status.commitUrlTemplate()).isEqualTo("https://github.com/example/projectflow/commit/{sha}");
        assertThat(executor.commands()).noneMatch(command -> command.contains("--show-token"));
    }

    @Test
    void missingGhStillReturnsLocalGitRepositoryInformation() {
        StubExecutor executor = new StubExecutor();
        executor.reply("git remote -v", 0, "origin\tgit@github.com:example/projectflow.git (fetch)");
        executor.reply("git branch --show-current", 0, "feature\n");
        executor.reply("gh --version", -1, "");

        var status = new GitHubCliService(executor, new ObjectMapper()).inspect(Path.of("."));

        assertThat(status.ghInstalled()).isFalse();
        assertThat(status.repoDetected()).isTrue();
        assertThat(status.remoteUrl()).isEqualTo("git@github.com:example/projectflow.git");
        assertThat(status.warnings()).singleElement().asString().contains("本地 Git 分析仍可使用");
    }

    @Test
    void unauthenticatedOrTimedOutGhOnlyAddsWarnings() {
        StubExecutor executor = new StubExecutor();
        executor.reply("git remote -v", 0, "origin\thttps://gitlab.com/example/projectflow.git (fetch)");
        executor.reply("git branch --show-current", 0, "master");
        executor.reply("gh --version", 0, "gh version");
        executor.reply("gh auth status", -1, "authentication failed token=secret-value");

        var status = new GitHubCliService(executor, new ObjectMapper()).inspect(Path.of("."));

        assertThat(status.ghInstalled()).isTrue();
        assertThat(status.ghAuthenticated()).isFalse();
        assertThat(status.repoDetected()).isFalse();
        assertThat(status.warnings()).anyMatch(warning -> warning.contains("尚未登录"));
        assertThat(status.toString()).doesNotContain("secret-value");
    }

    private static final class StubExecutor implements LocalCommandExecutor {
        private final Map<String, CommandResult> replies = new HashMap<>();
        private final List<String> commands = new ArrayList<>();

        void reply(String command, int exitCode, String output) {
            replies.put(command, new CommandResult(exitCode, output, false));
        }

        List<String> commands() {
            return commands;
        }

        @Override
        public CommandResult execute(Path directory, List<String> command, Duration timeout) {
            String key = String.join(" ", command);
            commands.add(key);
            return replies.getOrDefault(key, new CommandResult(-1, "", false));
        }
    }
}
