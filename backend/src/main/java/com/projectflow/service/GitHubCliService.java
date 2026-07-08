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
import com.projectflow.dto.V33WorkflowDtos.GitHubOpenTerminalResponse;
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

    // V3.3.4: 打开一个新终端窗口执行固定白名单命令 gh auth login --web --clipboard。
    // 安全约束：
    // - 只执行这个固定命令（平台适配为打开终端的 wrapper），不接受前端传入的任意命令。
    // - 不读取、不展示、不保存 token；命令本身是交互式登录，token 由 gh 自己管理。
    // - 打开失败时返回 opened=false，让前端回退到复制命令。
    public GitHubOpenTerminalResponse openLoginTerminal(Path projectRoot) {
        List<String> warnings = new ArrayList<>();
        String command = "gh auth login --web --clipboard";
        String platform = detectPlatform();
        try {
            ProcessBuilder builder = terminalBuilder(platform, projectRoot);
            if (builder == null) {
                warnings.add("当前平台无法自动打开终端，请复制命令在终端手动执行。");
                return new GitHubOpenTerminalResponse(false, command, platform, warnings);
            }
            Process process = builder.start();
            // 不等待登录完成（交互式），只确认终端窗口已拉起。start() 不抛异常即视为已打开。
            warnings.add("已打开登录终端，完成浏览器授权后回到 ProjectFlow 点击「重新检查」。");
            return new GitHubOpenTerminalResponse(true, command, platform, warnings);
        } catch (Exception exception) {
            warnings.add("自动打开终端失败：" + exception.getMessage() + "；请复制命令在终端手动执行。");
            return new GitHubOpenTerminalResponse(false, command, platform, warnings);
        }
    }

    private String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        return "linux";
    }

    // 构建打开终端的 ProcessBuilder。固定命令为 gh auth login --web --clipboard。
    private ProcessBuilder terminalBuilder(String platform, Path projectRoot) {
        String loginCommand = "gh auth login --web --clipboard";
        switch (platform) {
            case "windows" -> {
                // cmd /c start 打开一个新的 cmd 窗口执行登录命令，窗口保持打开（/k）。
                return new ProcessBuilder("cmd", "/c", "start", "GitHub 登录", "cmd", "/k", loginCommand)
                    .directory(projectRoot.toFile());
            }
            case "mac" -> {
                // osascript 在 Terminal.app 中运行命令。
                String script = "tell application \"Terminal\" to do script \"" + loginCommand.replace("\"", "\\\"") + "\"";
                return new ProcessBuilder("osascript", "-e", script).directory(projectRoot.toFile());
            }
            case "linux" -> {
                // best-effort: 尝试常见的 x-terminal-emulator / gnome-terminal。
                String[] terminals = {"x-terminal-emulator", "gnome-terminal", "konsole", "xterm"};
                for (String terminal : terminals) {
                    if (isCommandAvailable(terminal)) {
                        return new ProcessBuilder(terminal, "-e", loginCommand).directory(projectRoot.toFile());
                    }
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("which", command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
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
