import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { removeTestRepository } from "./support/repository-cleanup";
import { providerUpdatePayload } from "../src/lib/provider-settings";
import type { AiProvider, ProjectHistoryThread } from "../src/lib/api";

const backend = "http://127.0.0.1:18037/api";
const headers = { Authorization: "Bearer local-user" };
const repositories = new Set<string>();
const projects = new Set<string>();
const providers = new Set<string>();

type Job = { id: string; status: string };
type Story = { id: string; humanTitle: string; oneSentenceSummary: string };
type StoryPage = { items: Story[]; totalElements: number };
type CorrectionList = { presentationRevision: string };

test.afterEach(async ({ request }) => {
  for (const projectId of projects) await request.delete(`${backend}/projects/${projectId}`, { headers }).catch(() => undefined);
  projects.clear();
  for (const repository of repositories) removeTestRepository(repository);
  repositories.clear();
  const saved = await api<AiProvider[]>(request, "GET", "/ai-providers");
  for (const provider of saved.filter((item) => item.id && providers.has(item.id))) {
    if (provider.defaultEnabled) await api(request, "PATCH", `/ai-providers/${provider.id}`, { ...providerUpdatePayload(provider), defaultEnabled: false });
    const response = await request.delete(`${backend}/ai-providers/${provider.id}`, { headers });
    expect(response.ok()).toBeTruthy();
  }
  providers.clear();
});

test("项目历程默认可读、工程证据可下钻且基础修正保持冲突安全", async ({ page, request }) => {
  const fixture = await createHistoryProject(request);
  const job = await api<Job>(request, "POST", `/projects/${fixture.projectId}/history/refresh`, { force: false });
  await waitForJob(request, job.id);

  const stories = await api<StoryPage>(request, "GET", `/projects/${fixture.projectId}/history/stories?page=0&size=20`);
  expect(stories.items.length).toBeGreaterThan(0);
  const story = stories.items[0];
  const initial = await api<CorrectionList>(request, "GET", `/projects/${fixture.projectId}/history/corrections?page=0&size=50`);

  await selectProject(page, fixture.projectId);
  await page.goto(`/projects/${fixture.projectId}/history?compat=1&type=story&id=${encodeURIComponent(story.id)}`);
  await expect(page.getByRole("heading", { name: story.humanTitle })).toBeVisible();
  await expect(page.getByText(/主要变化|支撑工作/, { exact: true })).toBeVisible();
  await expect(page.getByText("自动整理", { exact: true })).toBeVisible();
  for (const internal of ["PRIMARY", "SUPPORTING", "ENGINEERING_GROUPING", "DETERMINISTIC", "USER_DECLARED_PRESENTATION"]) {
    await expect(page.getByText(internal, { exact: true })).toBeHidden();
  }

  await page.getByText("查看来源事件、Commit 与 Evidence", { exact: true }).click();
  await expect(page.getByText(/并保留原始提交信息供核对。/, { exact: true }).first()).toBeVisible();
  await expect(page.getByText("create launch brief with reviewable outline", { exact: true })).toBeHidden();
  await expect(page.getByText("presentation/launch-brief.md", { exact: true }).first()).toBeVisible();
  await page.getByText("查看原始提交与工程信息", { exact: true }).first().click();
  await expect(page.getByText("原始提交信息：create launch brief with reviewable outline", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "查看 Evidence 详情" }).first().click();
  await expect(page.getByRole("button", { name: "Evidence 已展开" }).first()).toBeVisible();
  await expect(page.getByText(/commit:|file:/).first()).toBeVisible();

  await page.getByText("查看工程详情与审计信息", { exact: true }).click();
  await expect(page.getByText("归纳权威", { exact: true })).toBeVisible();
  await expect(page.getByText("摘要状态", { exact: true })).toBeVisible();
  await expect(page.getByText(/PRIMARY|SUPPORTING/, { exact: true })).toBeVisible();

  const directTitle = "整理发布简报并形成可评审版本";
  const direct = await request.post(`${backend}/projects/${fixture.projectId}/history/corrections`, {
    headers: { ...headers, "Content-Type": "application/json" },
    data: {
      type: "RENAME_STORY",
      targetType: "STORY",
      targetId: story.id,
      title: directTitle,
      expectedPresentationRevision: initial.presentationRevision,
    },
  });
  expect(direct.ok(), await direct.text()).toBeTruthy();

  await page.getByLabel("摘要").fill("读者可以直接检查发布简报的完整结构。");
  await page.getByRole("button", { name: "保存摘要" }).click();
  await expect(page.getByRole("status")).toContainText("展示版本已变化");

  await page.reload();
  await expect(page.getByRole("heading", { name: directTitle })).toBeVisible();
  await page.getByLabel("摘要").fill("读者可以直接检查发布简报的完整结构。");
  await page.getByRole("button", { name: "保存摘要" }).click();
  await expect(page.getByRole("status")).toHaveText("展示内容已更新。");

  await page.getByRole("button", { name: "置顶阅读" }).click();
  await expect(page.getByRole("status")).toHaveText("展示内容已更新。");
  await page.getByRole("button", { name: "从默认阅读中隐藏" }).click();
  await expect(page.getByRole("status")).toHaveText("展示内容已更新。");
  const hidden = await api<StoryPage>(request, "GET", `/projects/${fixture.projectId}/history/stories?page=0&size=100`);
  expect(hidden.items.map((item) => item.id)).not.toContain(story.id);

  await page.getByRole("button", { name: "恢复自动展示" }).click();
  await expect(page.getByRole("status")).toHaveText("展示内容已更新。");
  const restored = await api<StoryPage>(request, "GET", `/projects/${fixture.projectId}/history/stories?page=0&size=100`);
  expect(restored.items.map((item) => item.id)).toContain(story.id);
  await expect(page.getByRole("heading", { name: story.humanTitle })).toBeVisible();

  const overview = await api<{ overview: { chapters: unknown[] } }>(request, "GET", `/projects/${fixture.projectId}/history/overview`);
  expect(overview.overview.chapters.length).toBeLessThanOrEqual(8);
});

test("V4 工作区读取真实后端、显式更新并共享同一 Agent 交接与工程证据", async ({ page, request }) => {
  const fixture = await createHistoryProject(request);
  await page.goto(`/workspace/current?project=${fixture.projectId}`);
  await expect(page.locator(".pf-hero h2")).toContainText("E2E 项目历程");
  await expect(page.getByText("Corporation-Agent", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "更新项目状态", exact: true }).click();
  await expect(page.getByRole("button", { name: "更新项目状态", exact: true })).toBeEnabled({ timeout: 90_000 });
  const current = await api<{ confirmedState: string }>(request, "GET", `/projects/${fixture.projectId}/history/current-state`);
  await expect(page.locator(".pf-hero-summary")).toHaveText(current.confirmedState);
  await page.getByRole("navigation", { name: "主导航" }).getByRole("link", { name: "项目历程", exact: true }).click();
  await page.getByRole("button", { name: "按时间查看", exact: true }).click();
  await expect(page.locator(".pf-chapter-reading h2")).toBeVisible();
  await page.locator(".pf-story-card").first().click();
  await expect(page.getByRole("dialog").getByRole("heading", { name: "当前结果" })).toBeVisible();
  await page.getByRole("dialog").getByText("查看工程证据与来源", { exact: true }).click();
  await expect(page.locator(".pf-source-details h4").first()).toBeVisible();
  await expect(page.getByRole("link", { name: "工程兼容工具：来源审计与修正" })).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("navigation", { name: "主导航" }).getByRole("link", { name: "Agent 交接", exact: true }).click();
  await expect(page.getByRole("button", { name: "复制交接内容", exact: true })).toBeEnabled();
  await expect(page.locator(".pf-handoff-document")).toContainText(current.confirmedState);
  await expect(page.locator(".pf-handoff-document")).not.toContainText("示例内容只用于界面评审");
});

test("V4 真实持久化演变主线在 Workspace 内完成 Thread、Story、Evidence 阅读", async ({ page, request }) => {
  const fixture = await createHistoryProject(request);
  const job = await api<Job>(request, "POST", `/projects/${fixture.projectId}/history/refresh`, { force: false });
  await waitForJob(request, job.id);
  const threads = await api<{ items: ProjectHistoryThread[] }>(request, "GET", `/projects/${fixture.projectId}/history/threads?page=0&size=12`);
  expect(threads.items.length).toBeGreaterThan(0);
  const thread = threads.items[0];
  const persisted = await api<{ stories: Story[] }>(request, "GET", `/projects/${fixture.projectId}/history/threads/${encodeURIComponent(thread.id)}`);
  expect(persisted.stories.length).toBeGreaterThan(0);
  const writes: string[] = [];
  page.on("request", (r) => { if (new URL(r.url()).pathname.startsWith("/api/") && r.method() !== "GET") writes.push(r.method()); });
  await page.goto(`/workspace/history?project=${fixture.projectId}`);
  await page.getByRole("button", { name: "按长期主题查看", exact: true }).click();
  await page.locator(".pf-thread-card").filter({ hasText: thread.subjectLabel }).click();
  await expect(page.locator(".pf-thread-detail-heading h2")).toHaveText(thread.subjectLabel);
  await page.reload();
  await expect(page.locator(".pf-thread-detail-heading h2")).toHaveText(thread.subjectLabel);
  await page.locator(".pf-thread-story-list .pf-story-card").filter({ hasText: persisted.stories[0].humanTitle }).click();
  const dialog = page.getByRole("dialog", { name: persisted.stories[0].humanTitle });
  await expect(dialog.getByRole("heading", { name: persisted.stories[0].humanTitle, exact: true })).toBeVisible();
  await dialog.getByText("查看工程证据与来源", { exact: true }).click();
  await expect(dialog.locator(".pf-evidence-items h4").first()).toBeVisible();
  await expect(dialog.locator(".pf-evidence-items").first()).toContainText(/commit:|file:/);
  await expect(page.getByText("Corporation-Agent", { exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(/\/workspace\/history\?.*thread=/);
  if (process.env.PROJECTFLOW_GUI_SCREENSHOTS) {
    const directory = path.resolve(process.env.PROJECTFLOW_GUI_SCREENSHOTS);
    mkdirSync(directory, { recursive: true });
    await page.screenshot({ path: path.join(directory, "real-backend-thread-story-evidence.png") });
  }
  expect(writes).toEqual([]);
});

async function createHistoryProject(request: APIRequestContext) {
  const project = await api<{ id: string }>(request, "POST", "/projects", {
    name: `E2E 项目历程 ${Date.now()}-${Math.random().toString(16).slice(2)}`,
    description: "验证普通用户可读的项目历程与基础展示修正",
    status: "BUILDING",
    techStack: [],
    repoUrl: "https://github.com/example/project-history-e2e",
    startDate: "2026-08-01",
    endDate: null,
  });
  projects.add(project.id);
  const provider = await api<AiProvider>(request, "POST", "/ai-providers", {
    name: `固定项目历程模型 ${Date.now()}-${Math.random().toString(16).slice(2)}`,
    baseUrl: "http://127.0.0.1:19037/v1",
    apiKey: "e2e-placeholder-key",
    modelName: "projectflow-fixed-history-e2e",
    type: "OPENAI_COMPATIBLE",
    temperature: 0,
    maxTokens: 16000,
    defaultEnabled: true,
    purposeTags: ["PROJECT_HISTORY_UI_E2E_NOT_REAL_PROVIDER"],
  });
  if (provider.id) providers.add(provider.id);
  const repository = createRepository();
  await api(request, "PATCH", `/projects/${project.id}/memory/local-path`, { localProjectPath: repository });
  return { projectId: project.id, repository };
}

function createRepository() {
  const repository = path.resolve(process.cwd(), ".e2e-data", "history-repositories", `${Date.now()}-${Math.random().toString(16).slice(2)}`);
  mkdirSync(path.join(repository, "presentation"), { recursive: true });
  repositories.add(repository);
  git(repository, "init", "-b", "master");
  git(repository, "config", "user.email", "history-e2e@example.com");
  git(repository, "config", "user.name", "ProjectFlow History E2E");
  writeFileSync(path.join(repository, "presentation", "launch-brief.md"), "# Launch brief\n\nA reviewable outline.\n", "utf8");
  git(repository, "add", ".");
  git(repository, "commit", "-m", "create launch brief with reviewable outline");
  return repository;
}

function git(repository: string, ...args: string[]) {
  execFileSync("git", args, { cwd: repository, stdio: "pipe" });
}

async function waitForJob(request: APIRequestContext, jobId: string) {
  let latest: Job | null = null;
  await expect.poll(async () => {
    latest = await api<Job>(request, "GET", `/analysis-jobs/${jobId}`);
    return latest.status;
  }, { timeout: 90_000 }).toMatch(/SUCCEEDED|SUCCEEDED_WITH_WARNINGS/);
  return latest!;
}

async function selectProject(page: Page, projectId: string) {
  await page.goto("/");
  await page.evaluate((id) => window.localStorage.setItem("projectflow:selectedProjectId", id), projectId);
}

async function api<T>(
  request: APIRequestContext,
  method: "GET" | "POST" | "PATCH",
  route: string,
  data?: unknown,
): Promise<T> {
  const response = await request.fetch(`${backend}${route}`, {
    method,
    headers: { ...headers, ...(data === undefined ? {} : { "Content-Type": "application/json" }) },
    data,
  });
  const responseText = await response.text();
  expect(response.ok(), `${method} ${route}: ${responseText}`).toBeTruthy();
  return ((JSON.parse(responseText).data ?? null) as T);
}
