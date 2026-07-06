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
        return inspect(projectRoot, false);
    }

    public GitHubStatusResponse inspect(Path projectRoot, boolean refreshRemote) {
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
            return localOnly(false, false, repoDetected, ownerRepo, currentBranch, remoteUrl, "NOT_INSTALLED", "github_unavailable", warnings);
        }

        CommandResult auth = run(projectRoot, "gh", "auth", "status");
        boolean authenticated = auth.exitCode() == 0 && !auth.timedOut();
        if (!authenticated) {
            warnings.add("GitHub CLI 已安装，但尚未登录；本地 Git 分析仍可使用。");
            return localOnly(true, false, repoDetected, ownerRepo, currentBranch, remoteUrl, "NOT_AUTHENTICATED", "github_unavailable", warnings);
        }
        if (!repoDetected) {
            warnings.add("当前项目没有检测到 GitHub remote，ProjectFlow 将继续使用本地 Git 分析。");
            return localOnly(true, true, false, "", currentBranch, remoteUrl, "NO_REMOTE", "no_upstream", warnings);
        }

        if (refreshRemote) {
            CommandResult fetch = run(projectRoot, "git", "fetch", "--quiet", "--prune", "origin");
            if (fetch.timedOut()) {
                warnings.add("GitHub 连接超时，可能需要代理；本次继续使用本地 Git 分析。");
                return localOnly(true, true, true, ownerRepo, currentBranch, remoteUrl, "CONNECTION_TIMEOUT", "github_unavailable", warnings);
            }
            if (fetch.exitCode() != 0) {
                String status = permissionFailure(fetch.output()) ? "PERMISSION_DENIED" : "FETCH_FAILED";
                warnings.add(permissionFailure(fetch.output())
                    ? "无法读取远程仓库信息，请检查 GitHub 登录账号或仓库权限；本地 Git 分析不受影响。"
                    : "无法刷新远程状态，可能是网络、代理或权限问题；本地 Git 分析不受影响。");
                return localOnly(true, true, true, ownerRepo, currentBranch, remoteUrl, status, "github_unavailable", warnings);
            }
        }

        CommandResult repo = run(projectRoot, "gh", "repo", "view", "--json", "nameWithOwner,url,defaultBranchRef,primaryLanguage,visibility");
        if (repo.exitCode() != 0 || repo.timedOut()) {
            String status = repo.timedOut() ? "CONNECTION_TIMEOUT" : permissionFailure(repo.output()) ? "PERMISSION_DENIED" : "CALL_FAILED";
            warnings.add(repo.timedOut() ? "GitHub 连接超时，可能需要代理；本次继续使用本地 Git 分析。" : "无法读取 GitHub 仓库信息，但本地 Git 分析不受影响。");
            return localOnly(true, true, true, ownerRepo, currentBranch, remoteUrl, status, "github_unavailable", warnings);
        }
        try {
            JsonNode json = objectMapper.readTree(repo.output());
            String nameWithOwner = json.path("nameWithOwner").asText(ownerRepo);
            String url = json.path("url").asText("https://github.com/" + ownerRepo);
            RemoteState remote = remoteState(projectRoot);
            return new GitHubStatusResponse(
                true, true, true, nameWithOwner, url,
                json.path("defaultBranchRef").path("name").asText(""), currentBranch,
                json.path("visibility").asText(""), json.path("primaryLanguage").path("name").asText(""),
                remoteUrl, url + "/commit/{sha}", "CONNECTED", remote.relation(), remote.localAhead(), remote.remoteAhead(), warnings
            );
        } catch (Exception exception) {
            warnings.add("GitHub 仓库信息格式无法解析，但本地 Git 分析不受影响。");
            return localOnly(true, true, true, ownerRepo, currentBranch, remoteUrl, "JSON_PARSE_FAILED", "github_unavailable", warnings);
        }
    }

    private GitHubStatusResponse localOnly(
        boolean installed,
        boolean authenticated,
        boolean detected,
        String ownerRepo,
        String currentBranch,
        String remoteUrl,
        String status,
        String remoteRelation,
        List<String> warnings
    ) {
        String url = ownerRepo.isBlank() ? "" : "https://github.com/" + ownerRepo;
        return new GitHubStatusResponse(
            installed, authenticated, detected, ownerRepo, url, "", currentBranch, "", "", remoteUrl,
            url.isBlank() ? "" : url + "/commit/{sha}", status, remoteRelation, 0, 0, warnings
        );
    }

    private RemoteState remoteState(Path projectRoot) {
        CommandResult upstream = run(projectRoot, "git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}");
        if (upstream.exitCode() != 0 || upstream.timedOut()) return new RemoteState("no_upstream", 0, 0);
        CommandResult counts = run(projectRoot, "git", "rev-list", "--left-right", "--count", "HEAD...@{upstream}");
        if (counts.exitCode() != 0 || counts.timedOut()) return new RemoteState("github_unavailable", 0, 0);
        String[] values = counts.output().trim().split("\\s+");
        if (values.length < 2) return new RemoteState("github_unavailable", 0, 0);
        try {
            int local = Integer.parseInt(values[0]);
            int remote = Integer.parseInt(values[1]);
            String relation = local > 0 && remote > 0 ? "diverged" : local > 0 ? "local_ahead" : remote > 0 ? "remote_ahead" : "synced";
            return new RemoteState(relation, local, remote);
        } catch (NumberFormatException exception) {
            return new RemoteState("github_unavailable", 0, 0);
        }
    }

    private boolean permissionFailure(String output) {
        String value = output == null ? "" : output.toLowerCase();
        return value.contains("permission") || value.contains("forbidden") || value.contains("403") || value.contains("denied");
    }

    private record RemoteState(String relation, int localAhead, int remoteAhead) {
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
