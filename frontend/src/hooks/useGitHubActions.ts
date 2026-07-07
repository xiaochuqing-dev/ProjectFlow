"use client";

import { useState } from "react";
import { getGitHubLoginGuide, refreshProjectGitHub, type GitHubStatus } from "@/lib/api";
import { readSession } from "@/lib/auth";

/**
 * V3.3.3: GitHub 状态与登录指引操作。
 * 刷新同步状态只读取远程提交信息，不修改本地代码（不会 pull、merge、rebase）。
 * 登录指引不读取、不展示、不保存 token；只提供命令让用户在终端执行。
 */
export function useGitHubActions(
  projectId: string,
  setGithubStatus: (status: GitHubStatus | null) => void,
  setNotice: (message: string) => void,
  setError: (message: string) => void,
) {
  const [refreshingGitHub, setRefreshingGitHub] = useState(false);

  async function refreshGitHub() {
    const session = readSession();
    if (!session || !projectId) {
      return;
    }
    setRefreshingGitHub(true);
    setError("");
    try {
      const updated = await refreshProjectGitHub(session.accessToken, projectId);
      setGithubStatus(updated);
      setNotice(updated.warnings[0] ?? "已刷新 GitHub 同步状态。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "GitHub 同步状态刷新失败");
    } finally {
      setRefreshingGitHub(false);
    }
  }

  async function showGitHubLogin() {
    const session = readSession();
    if (!session || !projectId) {
      return;
    }
    setError("");
    try {
      const guide = await getGitHubLoginGuide(session.accessToken, projectId);
      setNotice(
        guide.ghInstalled
          ? "请在终端执行登录命令，完成后回到 ProjectFlow 点击「重新检查」。"
          : "未检测到 GitHub CLI，请先安装后再登录。",
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "GitHub 登录指引获取失败");
    }
  }

  return { refreshingGitHub, refreshGitHub, showGitHubLogin };
}
