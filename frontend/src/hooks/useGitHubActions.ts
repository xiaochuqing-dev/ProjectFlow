"use client";

import { useState } from "react";
import { getGitHubLoginGuide, openGitHubLoginTerminal, refreshProjectGitHub, type GitHubLoginGuide, type GitHubStatus, type GitHubOpenTerminalResult } from "@/lib/api";
import { readSession } from "@/lib/auth";

/**
 * V3.3.3: GitHub 状态与登录指引操作。
 * 刷新同步状态只读取远程提交信息，不修改本地代码（不会 pull、merge、rebase）。
 * 登录指引不读取、不展示、不保存 token；只提供命令让用户在终端执行。
 *
 * V3.3.4: 新增打开登录终端（固定白名单命令）和复制命令能力。
 * showGitHubLogin 现在返回 loginGuide，供前端展示命令和复制按钮。
 */
export function useGitHubActions(
  projectId: string,
  setGithubStatus: (status: GitHubStatus | null) => void,
  setNotice: (message: string) => void,
  setError: (message: string) => void,
) {
  const [refreshingGitHub, setRefreshingGitHub] = useState(false);
  const [openingTerminal, setOpeningTerminal] = useState(false);
  const [loginGuide, setLoginGuide] = useState<GitHubLoginGuide | null>(null);

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

  // V3.3.4: 拉取登录指引并保存到 state，供前端展示命令和复制按钮。
  async function showGitHubLogin(): Promise<GitHubLoginGuide | null> {
    const session = readSession();
    if (!session || !projectId) {
      return null;
    }
    setError("");
    try {
      const guide = await getGitHubLoginGuide(session.accessToken, projectId);
      setLoginGuide(guide);
      setNotice(
        guide.ghInstalled
          ? "请在终端执行登录命令，完成后回到 ProjectFlow 点击「重新检查」。"
          : "未检测到 GitHub CLI，请先安装后再登录。",
      );
      return guide;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "GitHub 登录指引获取失败");
      return null;
    }
  }

  // V3.3.4: 打开登录终端，执行固定白名单命令 gh auth login --web --clipboard。
  // 后端只执行固定命令，不接受前端传入的任意命令。失败时前端回退到复制命令。
  async function openLoginTerminal(): Promise<GitHubOpenTerminalResult | null> {
    const session = readSession();
    if (!session || !projectId) {
      return null;
    }
    setOpeningTerminal(true);
    setError("");
    try {
      const result = await openGitHubLoginTerminal(session.accessToken, projectId);
      if (result.opened) {
        setNotice(result.warnings[0] ?? "已打开登录终端，完成浏览器授权后回到 ProjectFlow 点击「重新检查」。");
      } else {
        setNotice(result.warnings[0] ?? "无法自动打开终端，请复制命令在终端手动执行。");
      }
      return result;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "打开登录终端失败，请复制命令手动执行。");
      return null;
    } finally {
      setOpeningTerminal(false);
    }
  }

  function clearLoginGuide() {
    setLoginGuide(null);
  }

  return { refreshingGitHub, openingTerminal, loginGuide, refreshGitHub, showGitHubLogin, openLoginTerminal, clearLoginGuide };
}
