import type { Project } from "./api";

const SELECTED_PROJECT_KEY = "projectflow:selectedProjectId";
const NO_PROJECT_SELECTED = "__projectflow_none__";

export function resolveSelectedProjectId(projects: Project[], currentProjectId = "") {
  if (currentProjectId && projects.some((project) => project.id === currentProjectId)) {
    return currentProjectId;
  }
  if (typeof window !== "undefined") {
    const stored = window.localStorage.getItem(SELECTED_PROJECT_KEY) ?? "";
    if (stored === NO_PROJECT_SELECTED) {
      return "";
    }
    if (stored && projects.some((project) => project.id === stored)) {
      return stored;
    }
  }
  return projects[0]?.id ?? "";
}

export function rememberSelectedProjectId(projectId: string) {
  if (typeof window === "undefined") {
    return;
  }
  if (projectId) {
    window.localStorage.setItem(SELECTED_PROJECT_KEY, projectId);
  } else {
    window.localStorage.setItem(SELECTED_PROJECT_KEY, NO_PROJECT_SELECTED);
  }
}
