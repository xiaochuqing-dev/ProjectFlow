"use client";

import { useEffect, useMemo, useState } from "react";
import { listProjects, type Project } from "@/lib/api";
import { readSession, type AuthResult } from "@/lib/auth";
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";

type UseProjectSelectionOptions = {
  queryProjectId?: string;
};

export function useProjectSelection(options: UseProjectSelectionOptions = {}) {
  const [session, setSession] = useState<AuthResult | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [projectError, setProjectError] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );

  async function refreshProjects() {
    const auth = readSession();
    setSession(auth);
    if (!auth) {
      setProjects([]);
      setSelectedProjectId("");
      setLoadingProjects(false);
      return;
    }

    setLoadingProjects(true);
    setProjectError("");
    try {
      const items = await listProjects(auth.accessToken);
      const nextProjectId = resolveSelectedProjectId(items, options.queryProjectId);
      setProjects(items);
      setSelectedProjectId(nextProjectId);
      if (nextProjectId) {
        rememberSelectedProjectId(nextProjectId);
      }
    } catch (exception) {
      setProjectError(exception instanceof Error ? exception.message : "项目加载失败");
    } finally {
      setLoadingProjects(false);
    }
  }

  function selectProject(projectId: string) {
    rememberSelectedProjectId(projectId);
    setSelectedProjectId(projectId);
  }

  useEffect(() => {
    refreshProjects();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [options.queryProjectId]);

  return {
    session,
    projects,
    selectedProject,
    selectedProjectId,
    setSelectedProjectId: selectProject,
    selectProject,
    loadingProjects,
    projectError,
    refreshProjects,
  };
}
