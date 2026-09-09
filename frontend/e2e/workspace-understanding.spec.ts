import { expect, test, type Page } from "@playwright/test";
const project = { id: "truth-fixture", name: "可信阅读验收", description: "用户提供的项目说明", techStack: [], repoUrl: "", updatedAt: "2026-09-09T00:00:00Z" };
async function fixture(page: Page, many = false, offline = false) {
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url()), pathname = url.pathname;
    let data: unknown = project;
    if (pathname === "/api/projects") data = [project];
    else if (pathname.endsWith("/current-state")) data = { historyStatus: "READY", confirmedState: "整体进度78%，下一阶段将发布，团队执行力很强", relatedStoryRefs: ["story"], recentConfirmedChanges: [], conflicts: ["两份记录不一致"], unknowns: ["真实交付日期未知"], limitations: [], latestSuccessfulAt: "2026-09-09T00:00:00Z" };
    else if (pathname.endsWith("/overview")) data = { overview: { chapters: [] }, coverage: { gaps: [], limitations: [] }, diagnostics: {} };
    else if (pathname.endsWith("/stories")) data = { items: [], totalElements: 0 };
    else if (pathname.includes("analysis-jobs") || pathname.endsWith("/jobs")) data = [];
    else if (pathname.endsWith("/worklines")) {
      const index = Number(url.searchParams.get("page") ?? 0), group = url.searchParams.get("group");
      const items = Array.from({ length: many ? 36 : 1 }, (_, i) => ({ id: `line-${i}`, branch: i ? `codex/very-long-工作线-${i}-${"名称".repeat(12)}` : "main", head: "b".repeat(40),
        purpose: i ? `PR 中声明的设置改动 ${i}` : "已观察到项目主线文件变化", classification: i ? "DECLARED" : "INFERRED", state: i ? "DEPENDENT" : "MAIN", mergeState: i ? "OPEN_PR" : "CONTAINED", lastActivity: "2026-09-09T00:00:00Z", firstSampleActivity: "2026-09-01T00:00:00Z", ahead: 3, behind: 1, dependsOn: i ? "prior-work" : "", changedFiles: ["src/settings.ts"], commitSubjects: ["edit settings"], authors: ["Example"],
        pullRequest: i ? { number: i, title: `PR 中声明的设置改动 ${i}`, excerpt: "计划为设置增加编辑入口", state: "OPEN", draft: true, base: "prior-work", url: `https://github.com/example/repository/pull/${i}`, issues: [], checks: "" } : null,
        sources: [{ label: "本地 Git HEAD", reference: "b".repeat(40), observedAt: "2026-09-09T00:00:00Z" }], limitations: ["提交计数不能证明尚未合入"] }));
      const filtered = items.filter(item => !group || group === "ALL" || item.state === group);
      data = { observedAt: "2026-09-09T00:00:00Z", githubObservedAt: offline ? "" : "2026-09-09T00:00:00Z", githubStatus: offline ? "UNAVAILABLE" : "AVAILABLE", defaultBranch: "main", branchCount: items.length, stale: offline, truncated: false, groups: { MAIN: 1, DEPENDENT: many ? 35 : 0 }, items: filtered.slice(index * 12, index * 12 + 12), page: index, totalPages: Math.ceil(filtered.length / 12), totalElements: filtered.length, limitations: ["这是隔离的 UI 合同样本"] };
    }
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ data, message: "OK" }) });
  });
}
test("unsupported claims are absent from the entire real workspace and unknown plans stay valid", async ({ page }) => {
  await fixture(page); await page.goto(`/workspace/current?project=${project.id}`);
  await expect(page.getByRole("heading", { name: "项目明确写过的计划" })).toBeVisible();
  await expect(page.getByText(/暂无明确计划记录/)).toBeVisible();
  await expect(page.locator(".pf-workspace")).not.toContainText("78%");
  await expect(page.locator(".pf-workspace")).not.toContainText("团队执行力");
  await expect(page.locator(".pf-agent-quote")).toHaveCount(0);
  await expect(page.getByText("两份记录不一致", { exact: true }).first()).toBeVisible();
});
test("single branch is compact and GitHub failure does not erase local worklines", async ({ page }) => {
  await fixture(page, false, true); await page.goto(`/workspace/worklines?project=${project.id}`);
  await expect(page.locator(".pf-workline-card")).toHaveCount(1);
  await expect(page.getByText(/远程协作信息不完整/)).toBeVisible();
  await expect(page.getByRole("navigation", { name: "工作线分页" })).toHaveCount(0);
  await page.locator(".pf-workline-card").click();
  await expect(page.getByRole("dialog", { name: "工作线依据" })).toContainText("系统归纳");
  await page.keyboard.press("Escape");
  await expect(page.locator(".pf-workline-card")).toBeFocused();
});
test("many worklines group and paginate with declared PR purpose and evidence at five widths", async ({ page }) => {
  await fixture(page, true); await page.goto(`/workspace/worklines?project=${project.id}`);
  await expect(page.locator(".pf-workline-card")).toHaveCount(12);
  await page.getByRole("button", { name: "工作线下一页" }).click();
  await expect(page.getByText("第 2 / 3 页")).toBeVisible();
  await page.locator(".pf-workline-filters").getByRole("button", { name: /有依赖的工作/ }).click();
  await expect(page.getByText("第 1 / 3 页")).toBeVisible();
  for (const width of [390, 640, 1024, 1280, 1600]) {
    await page.setViewportSize({ width, height: 801 });
    expect(await page.locator(".pf-main").evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  }
  await page.locator(".pf-workline-card").first().click();
  const dialog = page.getByRole("dialog", { name: "工作线依据" });
  await expect(dialog).toContainText("项目明确声明"); await expect(dialog).toContainText("PR 基于 prior-work");
  await expect(dialog.getByRole("link", { name: /PR #/ })).toHaveAttribute("href", /https:\/\/github.com\/example\/repository\/pull\//);
});
test("normal projects and settings routes stay in V4 and new project creation uses the backend", async ({ page, request }) => {
  await page.goto("/projects"); await expect(page).toHaveURL(/\/workspace\/projects/);
  await page.getByRole("link", { name: "添加项目", exact: true }).last().click();
  await expect(page).toHaveURL(/\/workspace\/intake/);
  await page.getByRole("button", { name: "新建项目", exact: true }).click();
  await page.getByLabel("项目名称", { exact: true }).fill("V40D 创建验收");
  await page.getByLabel("项目说明 · 你明确提供的内容").fill("用于核对首次接入路径的合成空项目");
  await page.getByRole("button", { name: "添加项目并继续" }).click();
  await expect(page).toHaveURL(/\/workspace\/current\?project=/);
  const id = new URL(page.url()).searchParams.get("project");
  await expect(page.locator(".pf-hero h2")).toHaveText("V40D 创建验收");
  await expect(page.locator(".pf-workspace")).not.toContainText("V3.9");
  await page.goto("/settings"); await expect(page).toHaveURL(/\/workspace\/settings/);
  await request.delete(`http://127.0.0.1:18037/api/projects/${id}`, { headers: { Authorization: "Bearer local-user" } });
});
