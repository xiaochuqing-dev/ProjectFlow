import { expect, test, type APIRequestContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";

const backend = "http://127.0.0.1:18037/api";
const modelControl = "http://127.0.0.1:19037/control";
const headers = { Authorization: "Bearer local-user" };
const repositories = new Set<string>();
const projects = new Set<string>();

type Job = { id: string; status: string; requestCount: number; inputSummary?: string; retriedFromJobId?: string | null };
type Batch = { batchId: string; formalSuggestionCount: number; localDraftCount: number; pendingCount: number; processedCount: number; resultSource: string };

test.beforeEach(async ({ request }) => {
  await request.post(`${modelControl}/reset`);
});

test.afterEach(async ({ request }) => {
  for (const projectId of projects) await request.delete(`${backend}/projects/${projectId}`, { headers }).catch(() => undefined);
  projects.clear();
  for (const repository of repositories) rmSync(repository, { recursive: true, force: true });
  repositories.clear();
});

test("项目分析生成可恢复的模型批次", async ({ page, request }) => {
  const fixture = await createAnalyzableProject(request, "分析批次");
  const job = await startScan(request, fixture.projectId);
  await waitForJob(request, job.id, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);

  const batches = await api<Batch[]>(request, "GET", `/projects/${fixture.projectId}/sediment-review-batches`);
  expect(batches).toHaveLength(1);
  expect(batches[0]).toMatchObject({ resultSource: "MODEL_RESULT", localDraftCount: 0 });
  expect(batches[0].formalSuggestionCount).toBeGreaterThan(0);

  await selectProject(page, fixture.projectId);
  await page.goto(`/sediment-review?projectId=${fixture.projectId}`);
  await expect(page.getByText("完整模型分析")).toBeVisible();
  await expect(page.getByText("正式建议").first()).toBeVisible();
  await page.reload();
  await expect(page.getByText("完整模型分析")).toBeVisible();
  await page.goto(`/dashboard?projectId=${fixture.projectId}`);
  await page.goto(`/sediment-review?projectId=${fixture.projectId}`);
  await expect(page.getByText("完整模型分析")).toBeVisible();
  await expect(page.getByText("分析任务失败", { exact: false })).toHaveCount(0);
});

test("沉淀处理逐条确认并隔离本地事实草稿", async ({ page, request }) => {
  const fixture = await createAnalyzableProject(request, "沉淀闭环");
  await waitForJob(request, (await startScan(request, fixture.projectId)).id, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);
  const modelBatch = (await api<Batch[]>(request, "GET", `/projects/${fixture.projectId}/sediment-review-batches`))[0];

  await selectProject(page, fixture.projectId);
  await page.goto(`/sediment-review/${modelBatch.batchId}?projectId=${fixture.projectId}`);
  await expect(page.getByText(/批次进度 0 \/ /)).toBeVisible();
  await page.getByRole("button", { name: "新建并确认" }).click();
  await expect(page.getByText("已进入待能力分析", { exact: true })).toBeVisible();

  const updated = await api<{ batch: Batch }>(request, "GET", `/sediment-review-batches/${modelBatch.batchId}`);
  expect(updated.batch.processedCount).toBe(1);
  expect(updated.batch.pendingCount).toBe(modelBatch.pendingCount - 1);
  const sediments = await api<Array<{ id: string; capabilityStatus: string }>>(request, "GET", `/projects/${fixture.projectId}/sediments`);
  expect(sediments).toHaveLength(1);
  expect(sediments[0].capabilityStatus).toBe("PENDING_ANALYSIS");

  appendCommit(fixture.repository, "docs/local-draft.txt", "local draft", "docs: add local draft evidence");
  await request.post(`${modelControl}/fail-next`, { data: { count: 2 } });
  await waitForJob(request, (await startScan(request, fixture.projectId)).id, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);
  const batches = await api<Batch[]>(request, "GET", `/projects/${fixture.projectId}/sediment-review-batches`);
  const localBatch = batches.find((batch) => batch.resultSource === "LOCAL_FACT_DRAFT");
  expect(localBatch).toBeTruthy();
  expect(localBatch).toMatchObject({ formalSuggestionCount: 0, pendingCount: 0 });
  expect(localBatch!.localDraftCount).toBeGreaterThan(0);

  await page.goto(`/sediment-review?projectId=${fixture.projectId}`);
  await expect(page.getByText("本地事实草稿").first()).toBeVisible();
  await page.reload();
  await expect(page.getByText("本地事实草稿").first()).toBeVisible();
  await page.goto(`/project-sediments/${sediments[0].id}?projectId=${fixture.projectId}`);
  await expect(page.getByText("待能力分析", { exact: false }).first()).toBeVisible();
});

test("项目沉淀生成能力，后续失败不覆盖上次成功结果", async ({ page, request }) => {
  const fixture = await createAnalyzableProject(request, "能力闭环");
  await waitForJob(request, (await startScan(request, fixture.projectId)).id, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);
  const batch = (await api<Batch[]>(request, "GET", `/projects/${fixture.projectId}/sediment-review-batches`))[0];
  const detail = await api<{ formalSuggestions: Array<{ changeId: string }> }>(request, "GET", `/sediment-review-batches/${batch.batchId}`);
  await api(request, "POST", `/project-changes/${detail.formalSuggestions[0].changeId}/confirm`, { action: "NEW_SEDIMENT", targetSedimentId: null });

  await selectProject(page, fixture.projectId);
  await page.goto(`/project-intelligence/capabilities?projectId=${fixture.projectId}`);
  await expect(page.getByText("待能力分析").first()).toBeVisible();
  const beforeAnalysisIds = (await api<Job[]>(request, "GET", `/projects/${fixture.projectId}/analysis-jobs`)).map((job) => job.id);
  await page.getByRole("button", { name: /分析 1 条新增沉淀|分析项目能力/ }).click();
  let analysisJobId = "";
  await expect.poll(async () => {
    const jobs = await api<Job[]>(request, "GET", `/projects/${fixture.projectId}/analysis-jobs`);
    analysisJobId = jobs.find((job) => !beforeAnalysisIds.includes(job.id))?.id || "";
    return analysisJobId;
  }).not.toBe("");
  await page.reload();
  await page.goto(`/dashboard?projectId=${fixture.projectId}`);
  await page.goto(`/project-intelligence/capabilities?projectId=${fixture.projectId}`);
  await waitForJob(request, analysisJobId, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);
  await page.reload();
  await expect(page.getByText("当前生效结果")).toBeVisible();
  await expect(page.getByText("后台任务可靠性")).toBeVisible();

  const cards = await api<Array<{ sourceRefs: string[]; analysisJobId: string }>>(request, "GET", `/projects/${fixture.projectId}/capability-cards`);
  expect(cards[0].analysisJobId).toBe(analysisJobId);
  expect(cards[0].sourceRefs[0]).toMatch(/^sediment:/);
  const sediments = await api<Array<{ capabilityStatus: string; lastCapabilityAnalysisJobId: string }>>(request, "GET", `/projects/${fixture.projectId}/sediments`);
  expect(sediments[0].lastCapabilityAnalysisJobId).toBe(analysisJobId);
  expect(sediments[0].capabilityStatus).not.toBe("PENDING_ANALYSIS");

  await request.post(`${modelControl}/fail-next`, { data: { count: 2 } });
  const beforeFailureIds = (await api<Job[]>(request, "GET", `/projects/${fixture.projectId}/analysis-jobs`)).map((job) => job.id);
  await page.getByRole("button", { name: "分析项目能力" }).click();
  let failedJobId = "";
  await expect.poll(async () => {
    const jobs = await api<Job[]>(request, "GET", `/projects/${fixture.projectId}/analysis-jobs`);
    failedJobId = jobs.find((job) => !beforeFailureIds.includes(job.id))?.id || "";
    return failedJobId;
  }).not.toBe("");
  await waitForJob(request, failedJobId, ["FAILED"]);
  await page.reload();
  await expect(page.getByText("最近一次能力分析失败，当前仍展示上一次成功结果")).toBeVisible();
  await expect(page.getByText("后台任务可靠性")).toBeVisible();
});

test("任务刷新、取消与 retry 复用等价活动任务", async ({ page, request }) => {
  const fixture = await createAnalyzableProject(request, "任务可靠性");
  const failed = await startCapability(request, fixture.projectId);
  await waitForJob(request, failed.id, ["FAILED"]);

  await waitForJob(request, (await startScan(request, fixture.projectId)).id, ["SUCCEEDED", "SUCCEEDED_WITH_WARNINGS"]);
  const batch = (await api<Batch[]>(request, "GET", `/projects/${fixture.projectId}/sediment-review-batches`))[0];
  const detail = await api<{ formalSuggestions: Array<{ changeId: string }> }>(request, "GET", `/sediment-review-batches/${batch.batchId}`);
  await api(request, "POST", `/project-changes/${detail.formalSuggestions[0].changeId}/confirm`, { action: "NEW_SEDIMENT", targetSedimentId: null });

  await request.post(`${modelControl}/delay-next`, { data: { count: 1, ms: 2500 } });
  const active = await startCapability(request, fixture.projectId);
  const retried = await api<Job>(request, "POST", `/analysis-jobs/${failed.id}/retry`);
  expect(retried.id).toBe(active.id);
  const jobs = (await api<Job[]>(request, "GET", `/projects/${fixture.projectId}/analysis-jobs`)).filter((job) => job.status === "QUEUED" || job.status === "RUNNING" || job.status === "CANCEL_REQUESTED");
  expect(jobs.map((job) => job.id)).toEqual([active.id]);

  await api(request, "POST", `/analysis-jobs/${active.id}/cancel`);
  const cancelled = await waitForJob(request, active.id, ["CANCELLED"]);
  const requestCount = cancelled.requestCount;
  await page.waitForTimeout(500);
  expect((await api<Job>(request, "GET", `/analysis-jobs/${active.id}`)).requestCount).toBe(requestCount);

  await selectProject(page, fixture.projectId);
  await page.goto(`/project-intelligence/capabilities?projectId=${fixture.projectId}`);
  await page.reload();
  await expect(page.getByRole("button", { name: "重新运行" })).toBeVisible();
});

async function createAnalyzableProject(request: APIRequestContext, label: string) {
  const project = await api<{ id: string }>(request, "POST", "/projects", {
    name: `E2E ${label} ${Date.now()}-${Math.random().toString(16).slice(2)}`,
    description: "ProjectFlow V3.3.7 固定模型业务 E2E",
    status: "BUILDING",
    techStack: ["Spring Boot", "Next.js"],
    repoUrl: "",
    startDate: "2026-07-11",
    endDate: null,
  });
  projects.add(project.id);
  await api(request, "POST", "/ai-providers", {
    name: `固定 E2E 模型 ${Date.now()}-${Math.random().toString(16).slice(2)}`,
    baseUrl: "http://127.0.0.1:19037/v1",
    apiKey: "e2e-placeholder-key",
    modelName: "projectflow-fixed-e2e",
    type: "OPENAI_COMPATIBLE",
    temperature: 0,
    maxTokens: 4000,
    defaultEnabled: true,
    purposeTags: ["E2E_BUSINESS_FLOW_NOT_REAL_DEEPSEEK"],
  });
  const repository = createRepository(label);
  await api(request, "PATCH", `/projects/${project.id}/memory/local-path`, { localProjectPath: repository });
  return { projectId: project.id, repository };
}

function createRepository(label: string) {
  const repository = path.resolve(process.cwd(), ".e2e-data", "repositories", `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  mkdirSync(repository, { recursive: true });
  repositories.add(repository);
  git(repository, "init", "-b", "master");
  git(repository, "config", "user.email", "e2e@example.com");
  git(repository, "config", "user.name", "ProjectFlow E2E");
  appendCommit(repository, "src/feature.txt", "initial feature", "feat: add predictable workflow evidence");
  writeFileSync(path.join(repository, "src/feature.txt"), "initial feature\nuncommitted worktree change\n", "utf8");
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

async function startScan(request: APIRequestContext, projectId: string) {
  return api<Job>(request, "POST", `/projects/${projectId}/scan/jobs`);
}

async function startCapability(request: APIRequestContext, projectId: string) {
  return api<Job>(request, "POST", `/projects/${projectId}/capabilities/analyze/jobs`);
}

async function waitForJob(request: APIRequestContext, jobId: string, statuses: string[]) {
  let latest: Job | null = null;
  await expect.poll(async () => {
    latest = await api<Job>(request, "GET", `/analysis-jobs/${jobId}`);
    return statuses.includes(latest.status);
  }, { timeout: 30_000 }).toBeTruthy();
  return latest!;
}

async function selectProject(page: import("@playwright/test").Page, projectId: string) {
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
