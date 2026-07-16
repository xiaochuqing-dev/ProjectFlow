import { expect, test, type APIRequestContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";

const backend = "http://127.0.0.1:18037/api";
const modelControl = "http://127.0.0.1:19037/control";
const headers = { Authorization: "Bearer local-user" };
const repositories = new Set<string>();
const projects = new Set<string>();

type Job = { id: string; status: string };
type Overview = { mapStatus: string; sourceFactCount: number; coveredFactCount: number; activeCount: number; noCapabilityChangeFactCount: number; stale: boolean };
type CapabilityPage = { items: Array<{ id: string; factCount: number; evolutionCount: number }> };

test.beforeEach(async ({ request }) => {
  await request.post(`${modelControl}/reset`);
});

test.afterEach(async ({ request }) => {
  for (const projectId of projects) await request.delete(`${backend}/projects/${projectId}`, { headers }).catch(() => undefined);
  projects.clear();
  for (const repository of repositories) rmSync(repository, { recursive: true, force: true });
  repositories.clear();
});

test("V3.4.2 能力地图：完整初始化、稳定增量、普通事实、失败保护、详情和项目隔离", async ({ page, request }) => {
  const fixture = await createProject(request, "capability-main");
  await scan(request, fixture.projectId);
  const initialOverview = await waitMap(request, fixture.projectId, "READY");
  expect(initialOverview.coveredFactCount).toBe(initialOverview.sourceFactCount);
  expect(initialOverview.activeCount).toBeGreaterThan(0);
  const initialCapabilities = await api<CapabilityPage>(request, "GET", `/projects/${fixture.projectId}/capabilities?status=ACTIVE&page=0&size=20`);
  const stableId = initialCapabilities.items[0].id;

  appendCommit(fixture.repository, "src/enhance.txt", "enhance", "feat: enhance stable capability");
  await scan(request, fixture.projectId);
  await waitMap(request, fixture.projectId, "READY");
  const enhanced = await api<CapabilityPage>(request, "GET", `/projects/${fixture.projectId}/capabilities?status=ACTIVE&page=0&size=20`);
  expect(enhanced.items[0].id).toBe(stableId);
  expect(enhanced.items[0].factCount).toBeGreaterThanOrEqual(initialCapabilities.items[0].factCount);
  expect(enhanced.items[0].evolutionCount).toBeGreaterThanOrEqual(2);

  await request.post(`${modelControl}/capability-mode`, { data: { mode: "no-change" } });
  appendCommit(fixture.repository, "docs/maintenance.txt", "maintenance", "docs: maintenance only");
  await scan(request, fixture.projectId);
  const noChange = await waitMap(request, fixture.projectId, "READY");
  expect(noChange.noCapabilityChangeFactCount).toBeGreaterThan(0);
  expect(noChange.coveredFactCount).toBe(noChange.sourceFactCount);

  await request.post(`${modelControl}/capability-mode`, { data: { mode: "auto" } });
  await request.post(`${modelControl}/fail-next`, { data: { task: "capability", count: 2 } });
  appendCommit(fixture.repository, "src/failure.txt", "failure", "feat: capability refresh failure fixture");
  await scan(request, fixture.projectId);
  const stale = await waitMap(request, fixture.projectId, "READY_STALE");
  expect(stale.stale).toBeTruthy();
  const preserved = await api<CapabilityPage>(request, "GET", `/projects/${fixture.projectId}/capabilities?status=ACTIVE&page=0&size=20`);
  expect(preserved.items[0].id).toBe(stableId);
  await request.post(`${modelControl}/reset`);
  await api(request, "POST", `/projects/${fixture.projectId}/capability-map/retry`);
  const recovered = await waitMap(request, fixture.projectId, "READY");
  expect(recovered.coveredFactCount).toBe(recovered.sourceFactCount);

  await selectProject(page, fixture.projectId);
  await page.goto(`/project-intelligence/capabilities?projectId=${fixture.projectId}`);
  await expect(page.getByRole("heading", { name: "能力地图", level: 2 })).toBeVisible();
  await page.getByRole("link", { name: /可追溯项目演进能力/ }).first().click();
  await expect(page.getByRole("heading", { name: "能力演进", level: 3 })).toBeVisible();
  await expect(page.getByRole("heading", { name: "来源事实与证据", level: 3 })).toBeVisible();
  await expect(page.getByText("成熟度依据", { exact: true })).toBeVisible();

  const empty = await createBareProject(request, "capability-empty");
  await page.goto(`/project-intelligence/capabilities?projectId=${empty.id}`);
  await expect(page.getByText("能力地图正在等待完整初始化")).toBeVisible();
  await expect(page.getByText(/可追溯项目演进能力/)).toHaveCount(0);

  await page.goto(`/timeline?projectId=${fixture.projectId}`);
  await expect(page.getByText("主要演进主题").first()).toBeVisible();
  await page.goto(`/project-intelligence/capabilities?projectId=${fixture.projectId}`);
  await expect(page.getByText("长期能力").first()).toBeVisible();
});

async function createProject(request: APIRequestContext, label: string) {
  const project = await api<{ id: string }>(request, "POST", "/projects", payload(label));
  projects.add(project.id);
  await api(request, "POST", "/ai-providers", {
    name: `固定能力模型 ${Date.now()}-${Math.random()}`,
    baseUrl: "http://127.0.0.1:19037/v1",
    apiKey: "e2e-placeholder-key",
    modelName: "projectflow-fixed-e2e",
    type: "OPENAI_COMPATIBLE",
    temperature: 0,
    maxTokens: 8000,
    defaultEnabled: true,
    purposeTags: ["E2E_FIXED_NOT_REAL_PROVIDER"],
  });
  const repository = createRepository(label);
  await api(request, "PATCH", `/projects/${project.id}/memory/local-path`, { localProjectPath: repository });
  return { projectId: project.id, repository };
}

async function createBareProject(request: APIRequestContext, label: string) {
  const project = await api<{ id: string }>(request, "POST", "/projects", payload(label));
  projects.add(project.id);
  return project;
}

function payload(label: string) {
  return { name: `E2E ${label} ${Date.now()}-${Math.random()}`, description: "能力地图 E2E", status: "BUILDING", techStack: ["Spring Boot"], repoUrl: "", startDate: "2026-01-01", endDate: null };
}

function createRepository(label: string) {
  const repository = path.resolve(process.cwd(), ".e2e-data", "repositories", `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  mkdirSync(repository, { recursive: true });
  repositories.add(repository);
  git(repository, "init", "-b", "master");
  git(repository, "config", "user.email", "e2e@example.com");
  git(repository, "config", "user.name", "ProjectFlow E2E");
  appendCommit(repository, "src/initial.txt", "initial", "feat: initial capability evidence");
  return repository;
}

function appendCommit(repository: string, relativePath: string, content: string, message: string) {
  const file = path.join(repository, relativePath);
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, content, "utf8");
  git(repository, "add", ".");
  git(repository, "commit", "-m", message);
}

function git(repository: string, ...args: string[]) {
  execFileSync("git", args, { cwd: repository, stdio: "pipe" });
}

async function scan(request: APIRequestContext, projectId: string) {
  const job = await api<Job>(request, "POST", `/projects/${projectId}/scan/jobs`);
  await expect.poll(async () => (await api<Job>(request, "GET", `/analysis-jobs/${job.id}`)).status, { timeout: 90_000 }).toMatch(/SUCCEEDED/);
}

async function waitMap(request: APIRequestContext, projectId: string, status: string) {
  let latest: Overview | null = null;
  await expect.poll(async () => {
    latest = await api<Overview>(request, "GET", `/projects/${projectId}/capability-map/overview`);
    return latest.mapStatus;
  }, { timeout: 90_000 }).toBe(status);
  return latest!;
}

async function selectProject(page: import("@playwright/test").Page, projectId: string) {
  await page.goto("/");
  await page.evaluate((id) => window.localStorage.setItem("projectflow:selectedProjectId", id), projectId);
}

async function api<T>(request: APIRequestContext, method: "GET" | "POST" | "PATCH", route: string, data?: unknown): Promise<T> {
  const response = await request.fetch(`${backend}${route}`, { method, headers: { ...headers, ...(data === undefined ? {} : { "Content-Type": "application/json" }) }, data });
  const text = await response.text();
  expect(response.ok(), `${method} ${route}: ${text}`).toBeTruthy();
  return JSON.parse(text).data as T;
}
