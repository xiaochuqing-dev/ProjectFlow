import { expect, test, type Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import path from "node:path";

const views = {
  projects: "项目库",
  current: "当前状态",
  history: "项目历程",
  handoff: "Agent 交接",
  "project-settings": "项目设置",
  settings: "全局设置",
};

async function open(page: Page, view = "current", suffix = "?demo=1") {
  await page.goto(`/workspace/${view}${suffix}`);
  await expect(page.locator(".pf-page-heading h1")).toBeVisible();
  await page.waitForLoadState("networkidle");
}

async function screenshot(page: Page, name: string) {
  if (!process.env.PROJECTFLOW_GUI_SCREENSHOTS) return;
  const directory = path.resolve(process.env.PROJECTFLOW_GUI_SCREENSHOTS);
  mkdirSync(directory, { recursive: true });
  await page.screenshot({
    path: path.join(directory, `${name}.png`),
    animations: "disabled",
  });
}

test("six pages share the reference shell and keep their own responsibilities", async ({
  page,
}) => {
  const errors: string[] = [];
  const apiRequests: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("request", (r) => {
    if (new URL(r.url()).pathname.startsWith("/api/"))
      apiRequests.push(r.url());
  });
  await page.setViewportSize({ width: 1280, height: 801 });
  for (const [view, title] of Object.entries(views)) {
    await open(page, view);
    await expect(
      page.getByRole("heading", { name: title, exact: true }).first(),
    ).toBeVisible();
    await expect(
      page.getByText("设计预览 · 示例数据", { exact: true }),
    ).toBeVisible();
    expect(
      await page
        .locator(".pf-main")
        .evaluate((e) => e.scrollWidth <= e.clientWidth),
    ).toBe(true);
    await expect(page.locator(".pf-sidebar-landscape")).toBeVisible();
    await screenshot(page, `${view}-desktop`);
  }
  expect(errors).toEqual([]);
  expect(apiRequests).toEqual([]);
  await open(page);
  const shell = await page
    .locator(".pf-sidebar,.pf-hero,.pf-evidence")
    .evaluateAll((nodes) =>
      nodes.map((n) => {
        const r = n.getBoundingClientRect();
        return { x: r.x, y: r.y, w: r.width, h: r.height };
      }),
    );
  expect(shell[0].w).toBe(200);
  expect(shell[1].x).toBeCloseTo(216, 0);
  expect(shell[1].y).toBeCloseTo(128, 0);
  expect(shell[1].h).toBeCloseTo(211, 0);
  expect(shell[2].w).toBe(282);
  for (const asset of [
    "sidebar-mountains.webp",
    "hero-valley.webp",
    "evidence-planet.webp",
    "mark.svg",
  ]) {
    expect((await page.request.get(`/workspace/${asset}`)).status()).toBe(200);
  }
  await expect(page.locator(".pf-agent-quote")).toBeInViewport();
});

test("chapter, theme and evidence drilldowns retain context and keyboard focus", async ({
  page,
}) => {
  await open(page, "history");
  await page.getByRole("button", { name: "按时间查看", exact: true }).click();
  await page.getByRole("button", { name: /CHAPTER 02/ }).click();
  await expect(page.locator(".pf-chapter-reading h2")).toHaveText(
    "多 Agent 能力建设",
  );
  const story = page.locator(".pf-story-card").first();
  await story.click();
  const dialog = page.getByRole("dialog", { name: "优化权限体系与审计日志" });
  await expect(dialog).toBeVisible();
  for (const title of ["此前状态", "本次变化", "当前结果"])
    await expect(dialog.getByRole("heading", { name: title })).toBeVisible();
  await dialog.getByText("查看工程证据与来源", { exact: true }).click();
  await expect(dialog.getByText(/演示 Commit 9a0114/)).toBeVisible();
  await screenshot(page, "story-evidence-detail");
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(story).toBeFocused();
  await page.getByRole("button", { name: "按长期主题查看", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "从独立执行到多 Agent 协作" }),
  ).toBeVisible();
  await screenshot(page, "history-threads");
  await page.getByRole("button", { name: "相关文档", exact: true }).click();
  await page.getByRole("button", { name: /协作框架设计说明/ }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "关联讨论", exact: true }).click();
  await expect(
    page.getByText("尚未接入讨论来源", { exact: true }),
  ).toBeVisible();
});

test("command search switches project, supports empty results, and restores focus", async ({
  page,
}) => {
  await open(page);
  const trigger = page.getByRole("button", { name: /搜索项目、页面/ });
  await trigger.click();
  await page
    .getByRole("textbox", { name: "搜索项目和页面" })
    .fill("no-such-project");
  await expect(page.getByText("未找到匹配结果")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await page.keyboard.press("Control+k");
  await page
    .getByRole("textbox", { name: "搜索项目和页面" })
    .fill("MindStudio");
  await page
    .getByRole("dialog")
    .getByRole("link", { name: /MindStudio/ })
    .click();
  await expect(page.locator(".pf-hero h2")).toHaveText("MindStudio");
  await expect(page.locator(".pf-progress-ring")).toHaveCount(0);
  await expect(page.locator(".pf-history-preview")).toHaveCount(0);
  await expect(page.getByText("访谈材料只代表受访者陈述")).toBeVisible();
  await screenshot(page, "current-sparse");
  await page
    .locator(".pf-recents")
    .getByRole("link", { name: /DataHarbor/ })
    .click();
  await expect(page.locator(".pf-hero h2")).toHaveText("DataHarbor");
  await expect(
    page.getByText("还没有可确认的变化", { exact: true }),
  ).toBeVisible();
  await screenshot(page, "current-empty");
});

test("demo refresh is local and handoff copy includes uncertainty and scope", async ({
  page,
  context,
}) => {
  const writes: string[] = [];
  page.on("request", (r) => {
    if (
      new URL(r.url()).pathname.startsWith("/api/") &&
      !["GET", "HEAD"].includes(r.method())
    )
      writes.push(r.url());
  });
  await open(page);
  await page.getByRole("button", { name: "更新项目状态", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "正在更新状态" }),
  ).toBeDisabled();
  await expect(page.locator(".pf-hero")).toContainText("Corporation-Agent");
  await expect(
    page.getByText("示例状态已重载，未扫描项目或调用模型", { exact: true }),
  ).toBeVisible();
  await open(page, "handoff");
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await page.getByRole("button", { name: "复制交接内容", exact: true }).click();
  const text = await page.evaluate(() => navigator.clipboard.readText());
  expect(text).toContain("设计示例，非真实项目事实");
  expect(text).toContain("企业权限模型与现有系统存在集成风险");
  expect(text).toContain("适用范围与限制");
  expect(text).toContain("协作框架设计说明");
  await page.getByRole("checkbox", { name: "包含推荐材料" }).uncheck();
  await page.getByRole("button", { name: "复制交接内容", exact: true }).click();
  expect(
    await page.evaluate(() => navigator.clipboard.readText()),
  ).not.toContain("建议继续查看");
  expect(writes).toEqual([]);
});

test("settings keep provider credentials global and integration gaps explicit", async ({
  page,
}) => {
  await open(page, "project-settings");
  await page.getByRole("button", { name: "Git / GitHub", exact: true }).click();
  await expect(page.getByText("示例组织 / Corporation-Agent")).toBeVisible();
  await screenshot(page, "project-settings-github");
  await page
    .getByRole("button", { name: "Provider 策略", exact: true })
    .click();
  await expect(page.getByRole("link", { name: "前往全局设置" })).toBeVisible();
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await page.getByRole("button", { name: "Obsidian", exact: true }).click();
  await expect(page.getByText("状态未读取", { exact: true })).toBeVisible();
  await open(page, "settings");
  await page.getByRole("button", { name: "添加 Provider", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "添加 Provider", exact: true })).toBeVisible();
  await expect(page.getByLabel("API Key", { exact: true })).toHaveValue("");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "外观", exact: true }).click();
  await page.getByRole("checkbox", { name: "弱化背景装饰" }).check();
  await expect(page.locator(".pf-workspace")).toHaveClass(/pf-quiet/);
  await page.getByRole("button", { name: "数据与备份", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "数据留在你的本地" }),
  ).toBeVisible();
});

test("compact layouts preserve navigation, evidence access and readable content", async ({
  page,
}) => {
  for (const viewport of [
    { width: 1024, height: 768 },
    { width: 640, height: 800 },
    { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport);
    for (const view of Object.keys(views)) {
      await open(page, view);
      expect(
        await page
          .locator(".pf-main")
          .evaluate((e) => e.scrollWidth <= e.clientWidth),
        `${view} at ${viewport.width}`,
      ).toBe(true);
      if (view === "current")
        await screenshot(page, `current-${viewport.width}`);
    }
  }
  await page.getByRole("button", { name: "展开导航" }).click();
  await page
    .getByRole("navigation", { name: "主导航" })
    .getByRole("link", { name: "当前状态", exact: true })
    .click();
  await expect(page.locator(".pf-page-heading h1")).toHaveText("当前状态");
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "展开工程证据", exact: true }).click();
  await expect(
    page.getByRole("dialog", { name: "工程详情与证据" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("complementary", { name: "工程详情与证据" }),
  ).toBeHidden();
  await page.setViewportSize({ width: 1600, height: 1000 });
  await open(page);
  await screenshot(page, "current-wide");
});

const liveProject = {
  id: "fixture-project",
  name: "真实读取合同项目",
  description: "仅供测试的 API 响应",
  status: "BUILDING",
  techStack: [],
  repoUrl: "",
  updatedAt: "2026-09-01T10:00:00Z",
};
function currentState(confirmedState = "上次可确认结果") {
  return {
    projectId: liveProject.id,
    confirmedState,
    recentConfirmedChanges: ["已确认的材料变化"],
    relatedStoryRefs: ["saved-result"],
    latestSuccessfulAt: "2026-09-01T10:00:00Z",
    stale: true,
    degraded: false,
    conflicts: [],
    unknowns: ["真实读取未确认项"],
    limitations: ["只覆盖已读取材料"],
    presentationRevision: "r1",
  };
}
const overview = {
  projectId: liveProject.id,
  presentationRevision: "r1",
  overview: { chapters: [], conflicts: [], unknowns: [] },
  coverage: { gaps: [], limitations: [], complete: false },
};

async function fixtureApi(
  page: Page,
  extra?: (pathname: string, method: string) => unknown,
) {
  await page.route("**/api/**", async (route) => {
    const req = route.request();
    const pathname = new URL(req.url()).pathname;
    let data = extra?.(pathname, req.method());
    if (data === undefined) {
      if (pathname === "/api/projects") data = [liveProject];
      else if (pathname.endsWith("/current-state")) data = currentState();
      else if (pathname.endsWith("/overview")) data = overview;
      else if (pathname.endsWith("/stories"))
        data = {
          items: [
            {
              id: "declared-story",
              humanTitle: "仅声明但未确认的历史描述",
              oneSentenceSummary: "声明不进入强事实",
              role: "PRIMARY",
              evidenceRefs: [],
              eventRefs: [],
              occurredTo: "2026-09-01",
              unknowns: [],
              conflicts: [],
            },
          ],
          totalElements: 1,
        };
      else if (pathname.includes("analysis-jobs") || pathname.endsWith("/jobs"))
        data = [];
      else data = liveProject;
    }
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ data, message: "OK" }),
    });
  });
}

test("live reads never fall back to fixtures or promote ordinary stories", async ({
  page,
}) => {
  await fixtureApi(page);
  await open(page, "current", "?project=fixture-project");
  await expect(page.locator(".pf-hero h2")).toHaveText(liveProject.name);
  const confirmed = page.locator(".pf-real-section").filter({ has: page.getByRole("heading", { name: "直接证据支持的变化" }) });
  await expect(confirmed).toContainText("尚不足以展示可确认的结果");
  await expect(confirmed).not.toContainText(
    "仅声明但未确认的历史描述",
  );
  await expect(page.getByText("78%", { exact: true })).toHaveCount(0);
  await expect(
    page.getByText("Corporation-Agent", { exact: true }),
  ).toHaveCount(0);
  await expect(page.getByText(/材料可能已变化/)).toBeVisible();
  await screenshot(page, "current-live-stale");
  await page.unrouteAll();
  await page.route("**/api/**", (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({ error: { message: "测试服务暂不可用" } }),
    }),
  );
  await page.reload();
  await expect(page.locator(".pf-main").getByRole("alert")).toContainText(
    "测试服务暂不可用",
  );
  await expect(
    page.getByText("Corporation-Agent", { exact: true }),
  ).toHaveCount(0);
  await screenshot(page, "current-service-error");
});

test("explicit refresh keeps prior content, resumes its job, and sends one POST", async ({
  page,
}) => {
  let posts = 0;
  let polls = 0;
  let created = false;
  const job = {
    id: "fixture-job",
    projectId: liveProject.id,
    jobType: "PROJECT_HISTORY_REFRESH",
    status: "RUNNING",
    stageMessage: "正在核查项目材料",
  };
  await fixtureApi(page, (pathname, method) => {
    if (pathname.endsWith("/history/refresh") && method === "POST") {
      posts++;
      created = true;
      return job;
    }
    if (pathname.endsWith("/analysis-jobs/fixture-job")) {
      polls++;
      return { ...job, status: polls >= 3 ? "SUCCEEDED" : "RUNNING" };
    }
    if (pathname.endsWith("/analysis-jobs")) return created ? [job] : [];
    if (pathname.endsWith("/current-state"))
      return currentState(polls >= 3 ? "更新后的可确认结果" : "上次可确认结果");
    return undefined;
  });
  await open(page, "current", "?project=fixture-project");
  await page.getByRole("button", { name: "更新项目状态", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "正在更新状态", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".pf-hero-summary")).toHaveText("上次可确认结果");
  await page.reload();
  await expect(
    page.getByRole("button", { name: "正在更新状态", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".pf-hero-summary")).toHaveText(
    "更新后的可确认结果",
    { timeout: 15_000 },
  );
  expect(posts).toBe(1);
});

test("live context copy keeps actual strong facts, unknowns and truncation disclosure", async ({
  page,
  context,
}) => {
  await fixtureApi(page, (pathname) =>
    pathname.endsWith("/context-package")
      ? {
          projectId: liveProject.id,
          generatedAt: "2026-09-01T10:00:00Z",
          currentProjectState: currentState(),
          currentStrongFacts: [
            {
              itemId: "f1",
              statement: "工程已核验的真实陈述",
              epistemicStatus: "VERIFIED",
            },
          ],
          latestVerifiedChanges: [],
          conflicts: [{ itemId: "c1", statement: "待核查的矛盾" }],
          unknowns: [],
          suggestedDeepReadTargets: ["README.md"],
          limitations: ["仅选择相关材料"],
          unreadScope: ["未读附录"],
          truncated: true,
        }
      : undefined,
  );
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await open(page, "handoff", "?project=fixture-project");
  await expect(page.getByText("工程已核验的真实陈述")).toBeVisible();
  await page.getByRole("button", { name: "复制交接内容", exact: true }).click();
  const text = await page.evaluate(() => navigator.clipboard.readText());
  expect(text).toContain("工程已核验的真实陈述");
  expect(text).toContain("待核查的矛盾");
  expect(text).toContain("未读附录");
  expect(text).toContain("不能代表全部项目材料");
  expect(text).not.toContain("Corporation-Agent");
});

test("a reused completed refresh reloads saved results and a rejected update preserves them", async ({
  page,
}) => {
  let refreshed = false;
  await fixtureApi(page, (pathname, method) => {
    if (pathname.endsWith("/history/refresh") && method === "POST") {
      refreshed = true;
      return {
        id: "completed-job",
        projectId: liveProject.id,
        jobType: "PROJECT_HISTORY_REFRESH",
        status: "SUCCEEDED",
      };
    }
    if (pathname.endsWith("/current-state"))
      return currentState(
        refreshed ? "已完成任务中的保存结果" : "上次可确认结果",
      );
    return undefined;
  });
  await open(page, "current", "?project=fixture-project");
  await page.getByRole("button", { name: "更新项目状态", exact: true }).click();
  await expect(page.locator(".pf-hero-summary")).toHaveText(
    "已完成任务中的保存结果",
  );
  await page.route("**/history/refresh", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({ error: { message: "本次更新被拒绝" } }),
    }),
  );
  await page.getByRole("button", { name: "更新项目状态", exact: true }).click();
  await expect(page.getByText("本次更新被拒绝", { exact: true })).toBeVisible();
  await expect(page.locator(".pf-hero-summary")).toHaveText(
    "已完成任务中的保存结果",
  );
});
