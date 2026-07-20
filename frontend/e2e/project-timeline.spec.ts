import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { removeTestRepository } from "./support/repository-cleanup";

const backend = "http://127.0.0.1:18037/api";
const modelControl = "http://127.0.0.1:19037/control";
const headers = { Authorization: "Bearer local-user" };
const repositories = new Set<string>();
const projects = new Set<string>();

type Job = { id: string; jobType: string; status: string };
type Period = { periodKey: string; stats: { factCount: number }; summaryStatus: string; summaryStale: boolean };
type PeriodPage = { items: Period[] };
type Summary = { status: string; summary: string; sourceFactCount: number; coveredFactCount: number; stale: boolean; generationVersion: number };
type Detail = { periodKey: string; sourceFactCount: number; coveredFactCount: number; currentSummary: Summary | null; themes: Array<{ id: string; factCount: number }>; facts: { items: Array<{ id: string; batchId: string }> } };
type Lifecycle = { sourceFactCount: number; coveredFactCount: number; currentSummary: Summary | null };
type Overview = { factCount: number; dirtyPeriodCount: number; history: { status: string; coveredCommitCount: number; totalCommitCount: number } };

test.beforeEach(async ({ request }) => {
  await request.post(`${modelControl}/reset`);
});

test.afterEach(async ({ request }) => {
  for (const projectId of projects) await request.delete(`${backend}/projects/${projectId}`, { headers }).catch(() => undefined);
  projects.clear();
  for (const repository of repositories) removeTestRepository(repository);
  repositories.clear();
});

test("V3.4.1 时间线 A-G：边界分组、自动摘要、追溯、切换、失败保护与兼容入口", async ({ page, request }) => {
  const fixture = await createProject(request, "timeline-main");
  appendDatedCommit(fixture.repository, "src/june.txt", "june", "feat: june boundary", "2026-06-29T12:00:00+08:00");
  await scanAndWait(request, fixture.projectId);
  appendDatedCommit(fixture.repository, "src/july.txt", "july", "feat: july boundary", "2026-07-01T12:00:00+08:00");
  await scanAndWait(request, fixture.projectId);
  appendDatedCommit(fixture.repository, "src/mid-july.txt", "mid july", "feat: mid july", "2026-07-15T12:00:00+08:00");
  await scanAndWait(request, fixture.projectId);
  await waitForTimelineReady(request, fixture.projectId, 3);
  const empty = await createBareProject(request, "timeline-switch");

  const months = await api<PeriodPage>(request, "GET", `/projects/${fixture.projectId}/timeline/periods?granularity=MONTH&page=0&size=20`);
  expect(period(months, "2026-06").stats.factCount).toBe(1);
  expect(period(months, "2026-07").stats.factCount).toBe(2);
  const weeks = await api<PeriodPage>(request, "GET", `/projects/${fixture.projectId}/timeline/periods?granularity=WEEK&page=0&size=20`);
  expect(period(weeks, "2026-W27").stats.factCount).toBe(2);
  expect(period(weeks, "2026-W29").stats.factCount).toBe(1);
  const days = await api<PeriodPage>(request, "GET", `/projects/${fixture.projectId}/timeline/periods?granularity=DAY&page=0&size=20`);
  expect(days.items.map((item) => item.periodKey).sort()).toEqual(["2026-06-29", "2026-07-01", "2026-07-15"]);

  const july = await detail(request, fixture.projectId, "MONTH", "2026-07");
  expect(july.currentSummary?.status).toBe("READY");
  expect(july.sourceFactCount).toBe(2);
  expect(july.coveredFactCount).toBe(2);
  expect(july.themes).toHaveLength(1);
  expect(july.themes[0].factCount).toBe(2);
  const themeFacts = await api<{ facts: { items: unknown[] } }>(request, "GET", `/projects/${fixture.projectId}/timeline/themes/${july.themes[0].id}/facts?page=0&size=20`);
  expect(themeFacts.facts.items).toHaveLength(2);

  await selectProject(page, fixture.projectId);
  await page.goto(`/timeline?projectId=${fixture.projectId}`);
  await expect(page.getByRole("button", { name: "按月" })).toBeVisible();
  await expect(page.getByText("2026 年 7 月").first()).toBeVisible();
  await expect(page.getByRole("button", { name: /保存|确认|下一步/ })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "追溯批次与证据" }).first()).toBeVisible();
  await page.reload();
  await expect(page.getByText(july.currentSummary!.summary)).toBeVisible();

  await page.locator("select").first().selectOption(empty.id);
  await expect(page.getByText(empty.name).first()).toBeVisible();
  await expect(page.getByText(july.currentSummary!.summary)).toHaveCount(0);

  await request.post(`${modelControl}/fail-next`, { data: { task: "timeline:2026-W29", count: 2 } });
  appendDatedCommit(fixture.repository, "src/failure-refresh.txt", "failure refresh", "fix: refresh existing week", "2026-07-16T12:00:00+08:00");
  await scanAndWait(request, fixture.projectId);
  const failedWeek = await waitForPeriodStatus(request, fixture.projectId, "WEEK", "2026-W29", "FAILED");
  const oldWeek = await detail(request, fixture.projectId, "WEEK", "2026-W29");
  expect(failedWeek.summaryStale).toBeTruthy();
  expect(oldWeek.currentSummary?.summary).not.toBe("");
  expect(oldWeek.currentSummary?.stale).toBeTruthy();
  const failedVersion = oldWeek.currentSummary!.generationVersion;

  await request.post(`${modelControl}/reset`);
  await api(request, "POST", `/projects/${fixture.projectId}/timeline/retry`, { granularity: "WEEK", periodKey: "2026-W29" });
  const recovered = await waitForPeriodStatus(request, fixture.projectId, "WEEK", "2026-W29", "READY");
  expect(recovered.summaryStale).toBeFalsy();
  expect((await detail(request, fixture.projectId, "WEEK", "2026-W29")).currentSummary!.generationVersion).toBeGreaterThan(failedVersion);
  await waitForTimelineReady(request, fixture.projectId, 4);

  await page.goto(`/dev-logs?projectId=${fixture.projectId}`);
  await expect(page.locator("body")).toContainText("每日");
});

test("V3.4.1 时间线 H：历史补齐期间已有事实可读并展示覆盖状态", async ({ page, request }) => {
  const fixture = await createProject(request, "timeline-history");
  for (let index = 1; index <= 56; index++) {
    appendDatedCommit(
      fixture.repository,
      "src/history.txt",
      `history ${index}\n`,
      `history ${index}`,
      `2026-05-${String(Math.min(index, 28)).padStart(2, "0")}T12:00:00+08:00`,
    );
  }
  await request.post(`${modelControl}/delay-next`, { data: { task: "segment", count: 2, ms: 4_000 } });
  await scanAndWait(request, fixture.projectId);
  const overview = await waitForHistoryRunning(request, fixture.projectId);
  expect(overview.factCount).toBeGreaterThan(0);
  expect(overview.history.coveredCommitCount).toBeLessThan(overview.history.totalCommitCount);

  await selectProject(page, fixture.projectId);
  await page.goto(`/timeline?projectId=${fixture.projectId}`);
  await expect(page.getByText(/历史.*补齐/).first()).toBeVisible();
  await expect(page.getByText(/项目事实/).first()).toBeVisible();
});

async function createProject(request: APIRequestContext, label: string) {
  const project = await api<{ id: string; name: string }>(request, "POST", "/projects", projectPayload(label));
  projects.add(project.id);
  await api(request, "POST", "/ai-providers", {
    name: `固定时间线模型 ${Date.now()}-${Math.random().toString(16).slice(2)}`,
    baseUrl: "http://127.0.0.1:19037/v1",
    apiKey: "e2e-placeholder-key",
    modelName: "projectflow-fixed-timeline-e2e",
    type: "OPENAI_COMPATIBLE",
    temperature: 0,
    maxTokens: 4000,
    defaultEnabled: true,
    purposeTags: ["TIMELINE_E2E_NOT_REAL_DEEPSEEK"],
  });
  const repository = createRepository(label);
  await api(request, "PATCH", `/projects/${project.id}/memory/local-path`, { localProjectPath: repository });
  return { projectId: project.id, repository };
}

async function createBareProject(request: APIRequestContext, label: string) {
  const project = await api<{ id: string; name: string }>(request, "POST", "/projects", projectPayload(label));
  projects.add(project.id);
  return project;
}

function projectPayload(label: string) {
  return {
    name: `E2E ${label} ${Date.now()}-${Math.random().toString(16).slice(2)}`,
    description: "ProjectFlow V3.4.1 自动项目历程 E2E",
    status: "BUILDING",
    techStack: ["Spring Boot", "Next.js"],
    repoUrl: "",
    startDate: "2026-05-01",
    endDate: null,
  };
}

function createRepository(label: string) {
  const repository = path.resolve(process.cwd(), ".e2e-data", "timeline-repositories", `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  mkdirSync(repository, { recursive: true });
  repositories.add(repository);
  git(repository, ["init", "-b", "master"]);
  git(repository, ["config", "user.email", "timeline-e2e@example.com"]);
  git(repository, ["config", "user.name", "ProjectFlow Timeline E2E"]);
  git(repository, ["config", "gc.auto", "0"]);
  return repository;
}

function appendDatedCommit(repository: string, relativePath: string, content: string, message: string, date: string) {
  const file = path.join(repository, relativePath);
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, content, { encoding: "utf8", flag: "a" });
  git(repository, ["add", "."]);
  git(repository, ["commit", "-m", message], date);
}

function git(repository: string, args: string[], date?: string) {
  execFileSync("git", args, {
    cwd: repository,
    stdio: "pipe",
    env: date ? { ...process.env, GIT_AUTHOR_DATE: date, GIT_COMMITTER_DATE: date } : process.env,
  });
}

async function scanAndWait(request: APIRequestContext, projectId: string) {
  const job = await api<Job>(request, "POST", `/projects/${projectId}/scan/jobs`);
  return waitForJob(request, job.id, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);
}

async function waitForJob(request: APIRequestContext, jobId: string, statuses: string[]) {
  let latest: Job | undefined;
  await expect.poll(async () => {
    latest = await api<Job>(request, "GET", `/analysis-jobs/${jobId}`);
    return statuses.includes(latest.status);
  }, { timeout: 90_000 }).toBeTruthy();
  return latest!;
}

async function waitForTimelineReady(request: APIRequestContext, projectId: string, factCount: number) {
  await expect.poll(async () => {
    const overview = await api<Overview>(request, "GET", `/projects/${projectId}/timeline/overview`);
    const months = await api<PeriodPage>(request, "GET", `/projects/${projectId}/timeline/periods?granularity=MONTH&page=0&size=50`);
    const lifecycle = await api<Lifecycle>(request, "GET", `/projects/${projectId}/timeline/lifecycle`);
    return overview.factCount === factCount && months.items.length > 0
      && months.items.every((item) => item.summaryStatus === "READY")
      && lifecycle.currentSummary?.status === "READY" && lifecycle.coveredFactCount === factCount;
  }, { timeout: 90_000 }).toBeTruthy();
}

async function waitForHistoryRunning(request: APIRequestContext, projectId: string) {
  let latest: Overview | undefined;
  await expect.poll(async () => {
    latest = await api<Overview>(request, "GET", `/projects/${projectId}/timeline/overview`);
    return latest.history.status;
  }, { timeout: 30_000, intervals: [100, 200, 500] }).toBe("RUNNING");
  return latest!;
}

async function waitForPeriodStatus(request: APIRequestContext, projectId: string, granularity: string, key: string, status: string) {
  let latest: Period | undefined;
  await expect.poll(async () => {
    const page = await api<PeriodPage>(request, "GET", `/projects/${projectId}/timeline/periods?granularity=${granularity}&page=0&size=50`);
    latest = page.items.find((item) => item.periodKey === key);
    return latest?.summaryStatus;
  }, { timeout: 90_000 }).toBe(status);
  return latest!;
}

function period(page: PeriodPage, key: string) {
  const value = page.items.find((item) => item.periodKey === key);
  expect(value, `missing period ${key}`).toBeTruthy();
  return value!;
}

function detail(request: APIRequestContext, projectId: string, granularity: string, key: string) {
  return api<Detail>(request, "GET", `/projects/${projectId}/timeline/periods/${granularity}/${key}?page=0&size=50`);
}

async function selectProject(page: Page, projectId: string) {
  await page.goto("/");
  await page.evaluate((id) => window.localStorage.setItem("projectflow:selectedProjectId", id), projectId);
}

async function api<T>(request: APIRequestContext, method: "GET" | "POST" | "PATCH", route: string, data?: unknown): Promise<T> {
  const response = await request.fetch(`${backend}${route}`, {
    method,
    headers: { ...headers, ...(data === undefined ? {} : { "Content-Type": "application/json" }) },
    data,
  });
  const responseText = await response.text();
  expect(response.ok(), `${method} ${route}: ${responseText}`).toBeTruthy();
  return ((JSON.parse(responseText).data ?? null) as T);
}
