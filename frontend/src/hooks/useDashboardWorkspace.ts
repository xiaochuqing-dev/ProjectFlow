"use client";

import { useRef } from "react";
import type { ProjectAnalysis, WorkSessionCandidate, WorkSessionScanResult } from "@/lib/api";

export function useDashboardWorkspace() {
  const requestRef = useRef(0);

  function beginContextRequest() {
    requestRef.current += 1;
    return requestRef.current;
  }

  function isLatestContextRequest(requestId: number) {
    return requestId === requestRef.current;
  }

  return { beginContextRequest, isLatestContextRequest };
}

export function workSessionListResult(projectId: string, projectPath: string, sessions: WorkSessionCandidate[]): WorkSessionScanResult {
  return {
    projectId,
    projectPath,
    branchName: sessions[0]?.branchName ?? "",
    scannedAt: new Date().toISOString(),
    sessions,
    warnings: [],
    batch: null,
    segments: [],
    firstScan: false,
  };
}

export function projectAnalysisContainsNoise(analysis: ProjectAnalysis) {
  return [
    analysis.summary,
    analysis.architecture,
    analysis.message,
    ...analysis.modules,
    ...analysis.risks,
    ...analysis.importantFiles,
    ...analysis.evidence,
    ...analysis.limitations,
  ].some((value) => {
    const lower = value.toLowerCase().replaceAll("\\", "/");
    return lower.includes(".codex-run/")
      || lower.includes("old-git-")
      || lower.includes(".git/objects/")
      || lower.includes(".git/config")
      || lower.includes(".git/head");
  });
}
