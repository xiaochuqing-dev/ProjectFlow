import { expect, test } from "@playwright/test";
import path from "node:path";

const backend = "http://127.0.0.1:18037/api";
const headers = { Authorization: "Bearer local-user" };

test("项目刷新、重复任务幂等和取消状态可恢复", async ({ page, request }) => {
  const projectName = `E2E 项目 ${Date.now()}`;
  await page.goto("/projects");
  await page.getByPlaceholder("例如：英语写作训练工具").fill(projectName);
  await page.getByRole("button", { name: "先创建，后面再完善" }).click();
  await expect(page.getByText(projectName).first()).toBeVisible();

  await page.reload();
  await expect(page.getByText(projectName).first()).toBeVisible();
  await page.goto("/dashboard");
  await page.goto("/projects");
  await expect(page.getByText(projectName).first()).toBeVisible();

  const projectsResponse = await request.get(`${backend}/projects`, { headers });
  expect(projectsResponse.ok()).toBeTruthy();
  const projects = (await projectsResponse.json()).data as Array<{ id: string; name: string }>;
  const project = projects.find((item) => item.name === projectName);
  expect(project).toBeTruthy();

  const repositoryPath = path.resolve(process.cwd(), "..");
  const bindResponse = await request.patch(`${backend}/projects/${project!.id}/memory/local-path`, {
    headers: { ...headers, "Content-Type": "application/json" },
    data: { localProjectPath: repositoryPath },
  });
  expect(bindResponse.ok()).toBeTruthy();

  const starts = await Promise.all(Array.from({ length: 10 }, () =>
    request.post(`${backend}/projects/${project!.id}/scan/jobs`, { headers })
  ));
  expect(starts.every((response) => response.ok())).toBeTruthy();
  const jobs = await Promise.all(starts.map(async (response) => (await response.json()).data as { id: string }));
  expect(new Set(jobs.map((job) => job.id)).size).toBe(1);

  const jobId = jobs[0].id;
  const cancelResponse = await request.post(`${backend}/analysis-jobs/${jobId}/cancel`, { headers });
  expect(cancelResponse.ok()).toBeTruthy();

  let cancelledJob: { status: string; requestCount: number } | null = null;
  await expect.poll(async () => {
    const response = await request.get(`${backend}/analysis-jobs/${jobId}`, { headers });
    cancelledJob = (await response.json()).data;
    return cancelledJob?.status;
  }, { timeout: 20_000 }).toBe("CANCELLED");

  const requestCountAtCancel = cancelledJob!.requestCount;
  await page.waitForTimeout(500);
  const stableResponse = await request.get(`${backend}/analysis-jobs/${jobId}`, { headers });
  const stableJob = (await stableResponse.json()).data as { status: string; requestCount: number };
  expect(stableJob.status).toBe("CANCELLED");
  expect(stableJob.requestCount).toBe(requestCountAtCancel);

  await page.goto("/dashboard");
  await page.reload();
  await expect(page.getByText("分析已取消").first()).toBeVisible({ timeout: 15_000 });
});
