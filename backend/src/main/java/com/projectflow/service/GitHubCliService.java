package com.projectflow.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V33WorkflowDtos.GitHubStatusResponse;
import com.projectflow.service.LocalCommandExecutor.CommandResult;

@Service
public class GitHubCliService {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern HTTPS_REMOTE = Pattern.compile("^https://github\\.com/([^/]+/[^/]+?)(?:\\.git)?$");
    private static final Pattern SSH_REMOTE = Pattern.compile("^(?:git@github\\.com:|ssh://git@github\\.com/)([^/]+/[^/]+?)(?:\\.git)?$");

    private final LocalCommandExecutor commandExecutor;
    private final ObjectMapper objectMapper;

    public GitHubCliService(LocalCommandExecutor commandExecutor, ObjectMapper objectMapper) {
        this.commandExecutor = commandExecutor;
        this.objectMapper = objectMapper;
    }

    public GitHubStatusResponse inspect(Path projectRoot) {
        List<String> warnings = new ArrayList<>();
        String remoteOutput = run(projectRoot, "git", "remote", "-v").output();
        String remoteUrl = firstRemote(remoteOutput);
        String ownerRepo = ownerRepo(remoteUrl);
        boolean repoDetected = !ownerRepo.isBlank();
        String currentBranch = run(projectRoot, "git", "branch", "--show-current").output().trim();

        CommandResult version = run(projectRoot, "gh", "--version");
        boolean installed = version.exitCode() == 0 && !version.timedOut();
        if (!installed) {
            warnings.add("未检测到 GitHub CLI，本地 Git 分析仍可使用。");
            return localOnly(false, false, repoDetected, ownerRepo, currentBranch, remoteUrl, warnings);
        }

        CommandResult auth = run(projectRoot, "gh", "auth", "status");
        boolean authenticated = auth.exitCode() == 0 && !auth.timedOut();
        if (!authenticated) {
            warnings.add("GitHub CLI 已安装，但尚未登录；本地 Git 分析仍可使用。");
            return localOnly(true, false, repoDetected, ownerRepo, currentBranch, remoteUrl, warnings);
        }
        if (!repoDetected) {
            warnings.add("当前项目没有检测到 GitHub remote，ProjectFlow 将继续使用本地 Git 分析。");
            return localOnly(true, true, false, "", currentBranch, remoteUrl, warnings);
        }

        CommandResult repo = run(projectRoot, "gh", "repo", "view", "--json", "nameWithOwner,url,defaultBranchRef,primaryLanguage,visibility");
        if (repo.exitCode() != 0 || repo.timedOut()) {
            warnings.add("无法读取 GitHub 仓库信息，但本地 Git 分析不受影响。");
            return localOnly(true, true, true, ownerRepo, currentBranch, remoteUrl, warnings);
        }
        try {
            JsonNode json = objectMapper.readTree(repo.output());
            String nameWithOwner = json.path("nameWithOwner").asText(ownerRepo);
            String url = json.path("url").asText("https://github.com/" + ownerRepo);
            return new GitHubStatusResponse(
                true, true, true, nameWithOwner, url,
                json.path("defaultBranchRef").path("name").asText(""), currentBranch,
                json.path("visibility").asText(""), json.path("primaryLanguage").path("name").asText(""),
                remoteUrl, url + "/commit/{sha}", warnings
            );
        } catch (Exception exception) {
            warnings.add("GitHub 仓库信息格式无法解析，但本地 Git 分析不受影响。");
            return localOnly(true, true, true, ownerRepo, currentBranch, remoteUrl, warnings);
        }
    }

    private GitHubStatusResponse localOnly(
        boolean installed,
        boolean authenticated,
        boolean detected,
        String ownerRepo,
        String currentBranch,
        String remoteUrl,
        List<String> warnings
    ) {
        String url = ownerRepo.isBlank() ? "" : "https://github.com/" + ownerRepo;
        return new GitHubStatusResponse(
            installed, authenticated, detected, ownerRepo, url, "", currentBranch, "", "", remoteUrl,
            url.isBlank() ? "" : url + "/commit/{sha}", warnings
        );
    }

    private CommandResult run(Path projectRoot, String... command) {
        return commandExecutor.execute(projectRoot, List.of(command), COMMAND_TIMEOUT);
    }

    private String firstRemote(String output) {
        for (String line : output.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && parts[0].equals("origin")) {
                return parts[1];
            }
        }
        return "";
    }

    private String ownerRepo(String remoteUrl) {
        Matcher https = HTTPS_REMOTE.matcher(remoteUrl);
        if (https.matches()) return stripGit(https.group(1));
        Matcher ssh = SSH_REMOTE.matcher(remoteUrl);
        if (ssh.matches()) return stripGit(ssh.group(1));
        return "";
    }

    private String stripGit(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }
}
