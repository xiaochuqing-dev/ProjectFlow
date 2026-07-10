"use client";

import { useEffect, useRef, useState } from "react";
import {
  analyzeProjectFile,
  getProjectAnalysisJob,
  interpretCapability,
  listProjectAnalysisJobs,
  runProjectAnalysis,
  startProjectWorkSessionScan,
  type ProjectAnalysisJob,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const ACTIVE_STATUSES = new Set(["QUEUED", "RUNNING"]);

export function useProjectAnalysisJobs(projectId: string) {
  const [jobs, setJobs] = useState<ProjectAnalysisJob[]>([]);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [jobError, setJobError] = useState("");
  const refreshFailureCount = useRef(0);
  const activeJobKey = jobs
    .filter((job) => ACTIVE_STATUSES.has(job.status))
    .map((job) => job.id)
    .sort()
    .join(",");

  useEffect(() => {
    const session = readSession();
    if (!session || !projectId) {
      setJobs([]);
      return;
    }

    let cancelled = false;
    setLoadingJobs(true);
    setJobError("");
    listProjectAnalysisJobs(session.accessToken, projectId)
      .then((items) => {
        if (!cancelled) {
          setJobs(items);
        }
      })
      .catch((exception) => {
        if (!cancelled) {
          setJobError(exception instanceof Error ? exception.message : "分析任务加载失败");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingJobs(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  useEffect(() => {
    const session = readSession();
    const activeIds = activeJobKey ? activeJobKey.split(",") : [];
    if (!session || activeIds.length === 0) {
      return;
    }

    const accessToken = session.accessToken;
    let cancelled = false;
    async function refreshActiveJobs() {
      try {
        const updates = await Promise.all(activeIds.map((jobId) => getProjectAnalysisJob(accessToken, jobId)));
        if (!cancelled) {
          setJobs((current) => mergeJobs(current, updates));
          setJobError("");
          refreshFailureCount.current = 0;
        }
      } catch (exception) {
        if (!cancelled) {
          refreshFailureCount.current += 1;
          setJobError(refreshFailureCount.current < 3
            ? "状态刷新暂时失败，正在自动重试。后台任务可能仍在运行。"
            : "状态连续刷新失败，请检查本地服务连接。后台任务可能仍在运行。");
        }
      }
    }

    void refreshActiveJobs();
    const interval = window.setInterval(refreshActiveJobs, 1200);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [activeJobKey]);

  async function enqueueProjectAnalysis() {
    const session = readSession();
    if (!session || !projectId) {
      throw new Error("项目无效");
    }
    const job = await runProjectAnalysis(session.accessToken, projectId);
    setJobs((current) => mergeJobs(current, [job]));
    return job;
  }

  async function enqueueFileAnalysis(path: string) {
    const session = readSession();
    if (!session || !projectId) {
      throw new Error("项目无效");
    }
    const job = await analyzeProjectFile(session.accessToken, projectId, path);
    setJobs((current) => mergeJobs(current, [job]));
    return job;
  }

  async function enqueueCapabilityInterpret(capabilityFact: string) {
    const session = readSession();
    if (!session || !projectId) {
      throw new Error("项目无效");
    }
    const job = await interpretCapability(session.accessToken, projectId, capabilityFact);
    setJobs((current) => mergeJobs(current, [job]));
    return job;
  }

  async function enqueueWorkSessionScan() {
    const session = readSession();
    if (!session || !projectId) {
      throw new Error("项目无效");
    }
    const job = await startProjectWorkSessionScan(session.accessToken, projectId);
    setJobs((current) => mergeJobs(current, [job]));
    return job;
  }

  return {
    jobs,
    loadingJobs,
    jobError,
    enqueueProjectAnalysis,
    enqueueFileAnalysis,
    enqueueCapabilityInterpret,
    enqueueWorkSessionScan,
  };
}

function mergeJobs(current: ProjectAnalysisJob[], updates: ProjectAnalysisJob[]) {
  const byId = new Map(current.map((job) => [job.id, job]));
  for (const update of updates) {
    byId.set(update.id, update);
  }
  return Array.from(byId.values()).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}
