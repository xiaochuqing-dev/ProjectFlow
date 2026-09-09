import { expect, test, type Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import path from "node:path";
import type { AiProvider, AiProviderPayload, ProjectHistoryStory, ProjectHistoryThread } from "../src/lib/api";

const project = { id: "v40c-project", name: "发布简报 · 界面验收样本", description: "合成 DTO，用于检查真实模式的交互边界", status: "BUILDING", techStack: [], repoUrl: "", updatedAt: "2026-09-01T10:00:00Z" };
const stories: ProjectHistoryStory[] = Array.from({ length: 14 }, (_, i) => ({
  id: `story-${i}`, primarySubjectKey: "launch-brief", humanTitle: `整理发布简报的第 ${i + 1} 次变化`,
  oneSentenceSummary: "读者可以核对简报结构与相关来源，当前材料没有证明正式发布。", beforeState: "已有初步简报结构。", change: "补充评审说明与核对材料。", afterState: "形成可供核对的简报结构。",
  affectedAreas: [], reason: "", reasonEvidenceRefs: [], laterOutcome: "后续发布结果尚未确认。", conflicts: ["评审记录与草稿描述存在差异"], unknowns: ["正式发布结果未知"],
  occurredFrom: `2026-08-${String(i + 1).padStart(2, "0")}T10:00:00Z`, occurredTo: `2026-08-${String(i + 1).padStart(2, "0")}T11:00:00Z`,
  evidenceCount: 2, rawEventCount: 2, authority: "ENGINEERING_GROUPING", summaryStatus: "DETERMINISTIC", coverage: "BOUNDED", limitations: ["未包含线下评审"],
  eventRefs: [`event-${i}-0`, `event-${i}-1`], evidenceRefs: ["file:presentation/launch-brief.md"], role: "PRIMARY", presentationRevision: "revision-1",
}));
const threads: ProjectHistoryThread[] = Array.from({ length: 13 }, (_, i) => ({
  id: `thread-${i}`, subjectKey: `launch-brief-${i}`, subjectLabel: i === 0 ? "发布简报的结构演变" : `简报主题 ${i + 1}`,
  subjectType: "DOCUMENT", storyRefs: i === 0 ? [...stories.map((s) => s.id), "missing-story"] : [stories[0].id], transitions: ["CREATED", "MODIFIED"],
  currentOutcome: "简报已有可阅读的评审结构，后续发布结果仍待核对。", gaps: ["部分评审记录尚未接入"], conflicts: ["草稿与评审结论待核对"], unknowns: ["没有正式发布记录"], evidenceCount: 14, capabilityId: null,
}));
const chapter = { id: "chapter-main", title: "整理与试用", summary: "梳理简报并接受早期评审。", from: stories[0].occurredFrom, to: stories[13].occurredTo, storyRefs: stories.map((s) => s.id), storyCount: 14, rawEventCount: 28, authority: "ENGINEERING_GROUPING", coverage: "BOUNDED", limitations: ["仅包含已读取的材料"] };
const provider: AiProvider = {
  id: "configured-provider", name: "本地验收模型", baseUrl: "https://example.test/v1", modelName: "configured-model", type: "CUSTOM", protocol: "OPENAI_RESPONSES",
  endpointOverride: null, authMode: "BEARER", authHeaderName: null, queryKeyName: null, safeHeaderNames: ["X-Client-Name"], requestTimeoutSeconds: 420,
  supportsTemperature: false, supportsJsonMode: null, supportsStructuredOutput: true, supportsReasoning: true, supportsReasoningControl: true,
  temperature: 0.8, maxTokens: 48000, defaultEnabled: true, purposeTags: ["Existing purpose"], apiKeyConfigured: true, lastProbeProfile: null, lastProbedAt: null, createdAt: "", updatedAt: "",
};

async function capture(page: Page, name: string) {
  if (!process.env.PROJECTFLOW_GUI_SCREENSHOTS) return;
  const directory = path.resolve(process.env.PROJECTFLOW_GUI_SCREENSHOTS);
  mkdirSync(directory, { recursive: true });
  await page.screenshot({ path: path.join(directory, `${name}.png`), animations: "disabled" });
}

async function fixture(page: Page) {
  const state = { providers: [{ ...provider }], writes: [] as Array<{ method: string; pathname: string; body: AiProviderPayload | null }>, failures: new Set<string>(), waits: new Map<string, Promise<void>>(), threadSummary: threads[0].currentOutcome, emptyThreads: false, emptyProviders: false };
  await page.route("**/api/**", async (route) => {
    const req = route.request(), url = new URL(req.url()), pathname = url.pathname, method = req.method();
    await state.waits.get(`${method}:${pathname}`);
    const body = req.postData() ? req.postDataJSON() : null;
    if (method !== "GET") state.writes.push({ method, pathname, body });
    const fail = (message: string) => route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ error: { message } }) });
    if (state.failures.has(`${method}:${pathname}`)) return fail("验收服务暂不可用，请重试");
    let data: unknown;
    if (pathname === "/api/ai-providers") {
      if (method === "GET") data = state.emptyProviders ? [] : state.providers;
      else {
        const { apiKey, clearApiKey, safeHeaders, ...configuration } = body as AiProviderPayload;
        const created = { ...provider, ...configuration, id: `new-${state.providers.length}`, apiKeyConfigured: !!apiKey && !clearApiKey, safeHeaderNames: Object.keys(safeHeaders ?? {}) };
        if (created.defaultEnabled) state.providers = state.providers.map((p) => ({ ...p, defaultEnabled: false }));
        state.providers.push(created); data = created;
      }
    } else if (pathname.startsWith("/api/ai-providers/")) {
      const id = pathname.split("/")[3];
      if (pathname.endsWith("/test")) data = { ok: false, message: "模型服务拒绝连接，已保存的配置继续保留。", provider: "local", profile: { connection: "FAILED", protocol: "OPENAI_RESPONSES", warnings: [], requestsMade: 1 } };
      else if (method === "PATCH") {
        const previous = state.providers.find((p) => p.id === id)!;
        const { apiKey, clearApiKey, safeHeaders, ...configuration } = body as AiProviderPayload;
        const saved = { ...previous, ...configuration, apiKeyConfigured: !clearApiKey && (!!apiKey || previous.apiKeyConfigured), safeHeaderNames: safeHeaders ? Object.keys(safeHeaders) : previous.safeHeaderNames };
        state.providers = state.providers.map((p) => p.id === id ? saved : saved.defaultEnabled ? { ...p, defaultEnabled: false } : p); data = saved;
      } else if (method === "DELETE") { state.providers = state.providers.filter((p) => p.id !== id); data = null; }
    } else if (pathname === "/api/projects") data = [project];
    else if (pathname === `/api/projects/${project.id}`) data = project;
    else if (pathname.endsWith("/current-state")) data = { projectId: project.id, confirmedState: "简报已形成可阅读的结构，正式发布待核对。", recentConfirmedChanges: [], conflicts: [], unknowns: [], limitations: [], stale: true, degraded: true, presentationRevision: "revision-1", latestSuccessfulAt: "2026-09-01T10:00:00Z" };
    else if (pathname.endsWith("/overview")) data = { projectId: project.id, presentationRevision: "revision-1", overview: { chapters: [chapter], conflicts: [], unknowns: [] }, coverage: { gaps: [], limitations: [] } };
    else if (pathname.endsWith("/chapters")) data = { projectId: project.id, presentationRevision: "revision-1", items: [chapter], page: 0, size: 20, totalPages: 1, totalElements: 1 };
    else if (pathname.includes("/chapters/")) data = { projectId: project.id, presentationRevision: "revision-1", chapter, stories };
    else if (pathname.endsWith("/threads")) {
      const index = Number(url.searchParams.get("page") || "0");
      const matches = state.emptyThreads ? [] : threads.filter((t) => t.subjectLabel.includes(url.searchParams.get("subject") || ""));
      data = { projectId: project.id, presentationRevision: "revision-1", items: matches.slice(index * 12, index * 12 + 12), page: index, size: 12, totalElements: matches.length, totalPages: Math.ceil(matches.length / 12) };
    } else if (pathname.includes("/threads/")) data = { projectId: project.id, presentationRevision: "revision-1", thread: { ...threads.find((t) => pathname.endsWith(t.id)), currentOutcome: state.threadSummary }, stories };
    else if (pathname.endsWith("/stories")) data = { items: stories, totalElements: stories.length };
    else if (pathname.includes("/stories/")) {
      const story = stories.find((s) => pathname.endsWith(`/${s.id}`))!;
      data = { projectId: project.id, presentationRevision: "revision-1", story, threads: [threads[0]], events: story.eventRefs.map((id, i) => ({
        id, occurredAt: story.occurredTo, sourceType: "GIT", category: "DOCUMENT", transition: "MODIFIED", userSummary: `核对简报来源 ${i + 1}`, safeSourceLabel: "update reviewable launch brief",
        affectedPaths: ["presentation/launch-brief.md"], evidenceRefs: ["file:presentation/launch-brief.md"], authority: "GIT", epistemicStatus: "OBSERVED", limitations: [], rawSourceDeepLink: "javascript:alert(1)", rewriteState: "STALE",
      })) };
    } else if (pathname.endsWith("/evidence")) data = { projectId: project.id, eventId: pathname.split("/").at(-2), items: [{ type: "FILE", reference: "file:presentation/launch-brief.md", label: "发布简报来源", currentness: "STALE", revision: "test-revision", validation: "OBSERVED", coverage: "BOUNDED", limitations: ["只证明已读取的变化"], deepLink: "javascript:alert(1)" }], truncated: true };
    else if (pathname.includes("analysis-jobs")) data = [];
    else return fail(`没有对应的验收读取：${pathname}`);
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ data, message: "OK" }) });
  });
  return state;
}

test("real-mode Thread DTOs support paging, retained boundaries, Story and multiple Evidence sources", async ({ page }) => {
  const state = await fixture(page);
  await page.goto(`/workspace/history?project=${project.id}`);
  await expect(page.locator(".pf-chapter-reading h2")).toHaveText(chapter.title);
  await capture(page, "history-chapter");
  await page.getByRole("button", { name: "演变主线", exact: true }).click();
  await expect(page.locator(".pf-thread-card")).toHaveCount(12);
  await expect(page.getByText("来源可能已变化", { exact: false })).toBeVisible();
  await capture(page, "history-thread-list");
  await page.getByRole("button", { name: "主线目录下一页" }).click();
  await expect(page.locator(".pf-thread-card")).toHaveCount(1);
  await expect(page.locator(".pf-thread-card")).toContainText("简报主题 13");
  await page.getByRole("button", { name: "主线目录上一页" }).click();
  await page.locator(".pf-thread-card").filter({ hasText: threads[0].subjectLabel }).click();
  await expect(page.locator(".pf-thread-detail-heading h2")).toHaveText(threads[0].subjectLabel);
  await expect(page.locator(".pf-threads-view")).toContainText("1 条关联记录目前不可读取");
  await expect(page.locator(".pf-history-boundaries")).toContainText(["草稿与评审结论待核对"]);
  await capture(page, "thread-detail");
  await page.getByRole("button", { name: "主线故事下一页" }).click();
  await expect(page.locator(".pf-thread-story-list .pf-story-card")).toHaveCount(2);
  await page.getByRole("button", { name: "主线故事上一页" }).click();
  await page.reload();
  await expect(page.locator(".pf-thread-detail-heading h2")).toHaveText(threads[0].subjectLabel);
  const storyButton = page.locator(".pf-thread-story-list .pf-story-card").first();
  await storyButton.click();
  const dialog = page.getByRole("dialog", { name: stories[0].humanTitle });
  await expect(dialog.getByRole("heading", { name: "当前结果" })).toBeVisible();
  await dialog.getByText("查看工程证据与来源", { exact: true }).click();
  await expect(dialog.locator(".pf-evidence-items h4")).toHaveText("发布简报来源");
  await expect(dialog).toContainText("证据详情已达到安全读取上限");
  await expect(dialog.locator('a[href^="javascript:"]')).toHaveCount(0);
  state.failures.add(`GET:/api/projects/${project.id}/history/events/event-0-1/evidence`);
  await dialog.getByRole("button", { name: "查看 Evidence 详情", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("验收服务暂不可用");
  state.failures.clear();
  await dialog.getByRole("button", { name: "重新读取", exact: true }).click();
  await expect(dialog.locator(".pf-evidence-items h4")).toHaveCount(2);
  await capture(page, "story-evidence");
  await page.keyboard.press("Escape");
  await expect(storyButton).toBeFocused();
  await page.getByText("查看相关时间篇章", { exact: true }).click();
  await page.getByRole("button", { name: chapter.title, exact: true }).click();
  await expect(page.locator(".pf-chapter-reading h2")).toHaveText(chapter.title);
  expect(state.writes).toEqual([]);
  await expect(page.getByText("Corporation-Agent", { exact: true })).toHaveCount(0);
});

test("Thread empty, unavailable and search states stay distinct without demo fallback", async ({ page }) => {
  const state = await fixture(page);
  state.emptyThreads = true;
  await page.goto(`/workspace/history?project=${project.id}&axis=threads`);
  await expect(page.getByRole("heading", { name: "还没有已保存的演变主线" })).toBeVisible();
  state.emptyThreads = false;
  state.failures.add(`GET:/api/projects/${project.id}/history/threads`);
  await page.reload();
  await expect(page.locator(".pf-threads-view").getByRole("alert")).toContainText("验收服务暂不可用");
  await expect(page.locator(".pf-thread-card")).toHaveCount(0);
  state.failures.clear();
  await page.getByRole("button", { name: "重新读取", exact: true }).click();
  await expect(page.locator(".pf-thread-card")).toHaveCount(12);
  await page.getByRole("textbox", { name: "搜索演变主线" }).fill("不存在的主题");
  await page.getByRole("button", { name: "查找", exact: true }).click();
  await expect(page.getByRole("heading", { name: "没有匹配的演变主线" })).toBeVisible();
  await page.getByRole("textbox", { name: "搜索演变主线" }).fill("发布简报");
  await page.getByRole("button", { name: "查找", exact: true }).click();
  await expect(page.locator(".pf-thread-card")).toHaveCount(1);
});

test("Provider edits preserve credentials and advanced configuration; failed operations never claim success", async ({ page }) => {
  const state = await fixture(page);
  await page.goto("/workspace/settings");
  const card = page.getByRole("article", { name: provider.name });
  await expect(card).toContainText("凭据已保存 · 不回显");
  await capture(page, "provider-list");
  await card.getByRole("button", { name: "编辑", exact: true }).click();
  let dialog = page.getByRole("dialog", { name: "编辑 Provider" });
  await expect(dialog.getByLabel("新 API Key（留空保留）")).toHaveValue("");
  await dialog.getByLabel("配置名称", { exact: true }).fill("改名后的验收模型");
  await capture(page, "provider-edit");
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  await expect(dialog).toBeHidden();
  const payload = state.writes.at(-1)!.body!;
  expect(payload.apiKey).toBe(""); expect(payload.clearApiKey).toBe(false);
  expect(payload.safeHeaders).toBeUndefined(); expect(payload.supportsTemperature).toBe(false);
  expect(payload.supportsReasoningControl).toBe(true); expect(payload.requestTimeoutSeconds).toBe(420);
  expect(payload.purposeTags).toEqual(provider.purposeTags);
  let saved = page.getByRole("article", { name: "改名后的验收模型" });
  await saved.getByRole("button", { name: "测试连接", exact: true }).click();
  await expect(saved.getByRole("alert")).toContainText("连接测试未通过");
  await expect(saved).toContainText("凭据已保存 · 不回显");
  await capture(page, "provider-connection-error");
  await saved.getByRole("button", { name: "编辑", exact: true }).click();
  dialog = page.getByRole("dialog", { name: "编辑 Provider" });
  await dialog.getByLabel("清除已保存的凭据", { exact: true }).check();
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  const beforeConfirm = state.writes.length;
  await expect(dialog.getByRole("button", { name: "确认变更并保存" })).toBeVisible();
  await dialog.getByRole("button", { name: "返回检查" }).click();
  expect(state.writes).toHaveLength(beforeConfirm);
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  await dialog.getByRole("button", { name: "确认变更并保存" }).click();
  await expect(dialog).toBeHidden();
  expect(state.writes.at(-1)!.body?.clearApiKey).toBe(true);
  await expect(saved).toContainText("未保存凭据");
  state.failures.add(`PATCH:/api/ai-providers/${provider.id}`);
  await saved.getByRole("button", { name: "编辑", exact: true }).click();
  dialog = page.getByRole("dialog", { name: "编辑 Provider" });
  await dialog.getByLabel("配置名称", { exact: true }).fill("不能保存的名字");
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("验收服务暂不可用");
  await capture(page, "provider-save-error");
  await page.keyboard.press("Escape");
  await expect(saved).toBeVisible();
  state.failures.clear();
  await saved.getByRole("button", { name: "删除", exact: true }).click();
  await page.getByRole("dialog", { name: "删除 Provider" }).getByRole("button", { name: "取消", exact: true }).click();
  await expect(saved).toBeVisible();
  await saved.getByRole("button", { name: "删除", exact: true }).click();
  await page.getByRole("button", { name: "确认删除", exact: true }).click();
  await expect(page.getByRole("heading", { name: "添加第一个模型服务" })).toBeVisible();
});

test("Provider create, secret replacement confirmation and unique default selection remain in Workspace", async ({ page }) => {
  const state = await fixture(page);
  await page.goto("/workspace/settings");
  await page.getByRole("button", { name: "添加 Provider", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "添加 Provider" });
  await dialog.getByLabel("配置名称", { exact: true }).fill("新的服务");
  await dialog.getByLabel("模型标识", { exact: true }).fill("new-model");
  await dialog.getByLabel("服务地址", { exact: true }).fill("https://example.test/v1");
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  await expect(dialog).toBeHidden();
  const card = page.getByRole("article", { name: "新的服务" });
  await card.getByRole("button", { name: "设为默认", exact: true }).click();
  await expect(card).toContainText("全局默认");
  await expect(page.locator(".pf-provider-card .pf-chip", { hasText: "全局默认" })).toHaveCount(1);
  await expect(card.getByRole("button", { name: "删除", exact: true })).toBeDisabled();
  const old = page.getByRole("article", { name: provider.name });
  await old.getByRole("button", { name: "编辑", exact: true }).click();
  const editor = page.getByRole("dialog", { name: "编辑 Provider" });
  await editor.getByLabel("新 API Key（留空保留）").fill("synthetic-replacement");
  await editor.getByRole("button", { name: "保存 Provider", exact: true }).click();
  const count = state.writes.length;
  await expect(editor.getByRole("button", { name: "确认变更并保存" })).toBeVisible();
  expect(state.writes).toHaveLength(count);
  await editor.getByRole("button", { name: "确认变更并保存" }).click();
  await expect(editor).toBeHidden();
  expect(state.writes.at(-1)!.body?.apiKey.length).toBeGreaterThan(0);
  await old.getByRole("button", { name: "编辑", exact: true }).click();
  await expect(editor.getByLabel("新 API Key（留空保留）")).toHaveValue("");
  await page.keyboard.press("Escape");
  await expect(old.getByRole("button", { name: "编辑", exact: true })).toBeFocused();
});

test("demo Provider interactions issue no API requests and all supported widths keep forms readable", async ({ page }) => {
  const requests: string[] = [];
  page.on("request", (r) => { if (new URL(r.url()).pathname.startsWith("/api/")) requests.push(r.method()); });
  for (const width of [390, 640, 1024, 1280, 1600]) {
    await page.setViewportSize({ width, height: width === 1280 ? 801 : 900 });
    await page.goto("/workspace/settings?demo=1");
    await page.getByRole("article").getByRole("button", { name: "编辑", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "编辑 Provider" });
    await expect(dialog).toBeVisible();
    await dialog.getByText("高级设置：认证、请求限制与能力覆盖", { exact: true }).click();
    expect(await dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
    await capture(page, `provider-form-${width}`);
    await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
    await expect(dialog).toBeHidden();
    await page.getByRole("article").getByRole("button", { name: "测试连接", exact: true }).click();
    await page.getByRole("article").getByRole("button", { name: "删除", exact: true }).click();
    await page.getByRole("button", { name: "确认删除", exact: true }).click();
    await expect(page.getByRole("heading", { name: "添加第一个模型服务" })).toBeVisible();
  }
  expect(requests).toEqual([]);
});

test("compact Evidence drawer contains Tab focus, closes with Escape and restores its trigger", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/workspace/current?demo=1");
  const trigger = page.getByRole("button", { name: "展开工程证据", exact: true });
  await trigger.click();
  const drawer = page.getByRole("dialog", { name: "工程详情与证据" });
  await expect(drawer).toBeVisible();
  for (let i = 0; i < 16; i++) {
    await page.keyboard.press("Tab");
    expect(await drawer.evaluate((element) => element.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await page.getByRole("button", { name: "展开导航", exact: true }).click();
  const navigation = page.getByRole("dialog", { name: "侧边栏" });
  for (let i = 0; i < 15; i++) {
    await page.keyboard.press("Tab");
    expect(await navigation.evaluate((element) => element.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(page.getByRole("button", { name: "展开导航", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "展开导航", exact: true }).click();
  await page.setViewportSize({ width: 640, height: 900 });
  await expect(page.getByRole("dialog", { name: "侧边栏" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "收起导航", exact: true })).toHaveCount(0);
  expect(await page.locator(".pf-main").evaluate((element) => (element as HTMLElement).inert)).toBe(false);
});

test("local welcome and login entry lead into V4 while compatibility remains reachable", async ({ page }) => {
  const state = await fixture(page);
  await page.goto("/login");
  await expect(page.getByRole("link", { name: "进入 V4 工作区", exact: true })).toHaveAttribute("href", "/workspace/projects");
  await expect(page.getByRole("link", { name: "兼容工作台", exact: true })).toHaveAttribute("href", "/dashboard");
  await page.getByRole("link", { name: "进入 V4 工作区", exact: true }).click();
  await expect(page.locator(".pf-page-heading h1")).toHaveText("项目库");
  await expect(page.getByText("设计预览 · 示例数据", { exact: true })).toHaveCount(0);
  expect(state.writes).toEqual([]);
});

test("leaving demo for an unavailable real project library clears demonstration content", async ({ page }) => {
  const state = await fixture(page);
  state.failures.add("GET:/api/projects");
  await page.goto("/workspace/current?demo=1");
  await page.getByLabel("工作区选项").click();
  await page.getByRole("link", { name: "打开真实项目", exact: true }).click();
  await expect(page.locator(".pf-main").getByRole("alert")).toContainText("验收服务暂不可用");
  await expect(page.getByText("Corporation-Agent", { exact: true })).toHaveCount(0);
  await expect(page.locator(".pf-recents a")).toHaveCount(0);
});

test("loading remains visible until Provider and Thread reads resolve", async ({ page }) => {
  const state = await fixture(page);
  let finishProvider!: () => void;
  state.waits.set("GET:/api/ai-providers", new Promise<void>((resolve) => { finishProvider = resolve; }));
  await page.goto("/workspace/settings");
  await expect(page.getByText("正在读取 Provider 配置…", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "添加 Provider", exact: true })).toBeDisabled();
  finishProvider();
  await expect(page.getByRole("article", { name: provider.name })).toBeVisible();
  let finishThread!: () => void;
  state.waits.set(`GET:/api/projects/${project.id}/history/threads`, new Promise<void>((resolve) => { finishThread = resolve; }));
  await page.goto(`/workspace/history?project=${project.id}&axis=threads`);
  await expect(page.getByText("正在读取演变主线…", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "还没有已保存的演变主线" })).toHaveCount(0);
  finishThread();
  await expect(page.locator(".pf-thread-card")).toHaveCount(12);
  expect(state.writes).toEqual([]);
});

test("long Thread and Evidence reading stays usable at every supported width", async ({ page }) => {
  const state = await fixture(page);
  state.threadSummary = "发布简报保留评审过程与来源，现有证据尚未确认正式发布。".repeat(6) + " extended-reference-" + "document".repeat(24);
  for (const width of [390, 640, 1024, 1280, 1600]) {
    await page.setViewportSize({ width, height: width === 1280 ? 801 : 900 });
    await page.goto(`/workspace/history?project=${project.id}&axis=threads&thread=thread-0`);
    await expect(page.locator(".pf-thread-detail-heading h2")).toHaveText(threads[0].subjectLabel);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(await page.locator(".pf-main").evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true);
    await capture(page, `thread-reading-${width}`);
    await page.locator(".pf-thread-story-list .pf-story-card").first().click();
    const dialog = page.getByRole("dialog", { name: stories[0].humanTitle });
    await dialog.getByText("查看工程证据与来源", { exact: true }).click();
    await expect(dialog.locator(".pf-evidence-items")).toContainText("发布简报来源");
    expect(await dialog.evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true);
    if (width === 390 || width === 1600) await capture(page, `story-evidence-${width}`);
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
  }
  expect(state.writes).toEqual([]);
});
