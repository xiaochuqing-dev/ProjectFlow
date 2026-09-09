import { expect, test, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { mkdirSync } from "node:fs";
import path from "node:path";
import type { AiProvider } from "../src/lib/api";
import { providerUpdatePayload } from "../src/lib/provider-settings";

const backend = "http://127.0.0.1:18037/api";
const headers = { Authorization: "Bearer local-user" };
const created = new Set<string>();

async function providers(request: APIRequestContext): Promise<AiProvider[]> {
  const response = await request.get(`${backend}/ai-providers`, { headers });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).data;
}

test.afterEach(async ({ request }) => {
  for (const provider of (await providers(request)).filter((p) => p.id && created.has(p.id))) {
    if (provider.defaultEnabled) {
      const update = await request.patch(`${backend}/ai-providers/${provider.id}`, { headers, data: { ...providerUpdatePayload(provider), defaultEnabled: false } });
      expect(update.ok()).toBeTruthy();
    }
    const deleted = await request.delete(`${backend}/ai-providers/${provider.id}`, { headers });
    expect(deleted.ok()).toBeTruthy();
  }
  for (const provider of await providers(request)) expect(created.has(provider.id!)).toBe(false);
  created.clear();
});

test("真实 Provider CRUD 使用安全凭据路径且 UI 不回显，测试后清理临时配置", async ({ page, request }) => {
  await request.post("http://127.0.0.1:19037/control/reset");
  const name = `V4-C 临时 Provider ${randomUUID().slice(0, 8)}`;
  await page.goto("/workspace/settings");
  await page.getByRole("button", { name: "添加 Provider", exact: true }).click();
  let dialog = page.getByRole("dialog", { name: "添加 Provider", exact: true });
  await dialog.getByLabel("配置名称", { exact: true }).fill(name);
  await dialog.getByLabel("模型标识", { exact: true }).fill("projectflow-fixed-provider-e2e");
  await dialog.getByLabel("服务地址", { exact: true }).fill("http://127.0.0.1:19037/v1");
  await dialog.getByLabel("API Key", { exact: true }).fill(`e2e-v40c-${randomUUID()}`);
  await dialog.getByLabel("设为全局默认 Provider", { exact: true }).uncheck();
  const responsePromise = page.waitForResponse((response) => response.request().method() === "POST" && response.url().endsWith("/api/ai-providers"));
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  const saved = (await response.json()).data as AiProvider;
  expect(saved.id).toBeTruthy(); created.add(saved.id!);
  expect(Object.keys(saved)).not.toEqual(expect.arrayContaining(["apiKey"]));
  expect(Object.keys(saved)).not.toEqual(expect.arrayContaining(["secretRef"]));
  expect(saved.apiKeyConfigured).toBe(true);
  await expect(dialog).toBeHidden();
  let card = page.getByRole("article", { name, exact: true });
  const probeResponse = page.waitForResponse((r) => r.request().method() === "POST" && r.url().endsWith(`/ai-providers/${saved.id}/test`));
  await card.getByRole("button", { name: "测试连接", exact: true }).click();
  expect((await (await probeResponse).json()).data.ok).toBe(true);
  await expect(card.getByRole("status")).toContainText("连接测试通过", { timeout: 60_000 });
  await card.getByRole("button", { name: "编辑", exact: true }).click();
  dialog = page.getByRole("dialog", { name: "编辑 Provider", exact: true });
  await expect(dialog.getByLabel("新 API Key（留空保留）")).toHaveValue("");
  await dialog.getByLabel("配置名称", { exact: true }).fill(`${name} 已编辑`);
  await dialog.getByText("高级设置：认证、请求限制与能力覆盖", { exact: true }).click();
  await dialog.getByLabel("单次请求超时（秒）", { exact: true }).fill("420");
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  await expect(dialog).toBeHidden();
  await page.reload();
  card = page.getByRole("article", { name: `${name} 已编辑`, exact: true });
  await expect(card).toContainText("凭据已保存 · 不回显");
  const reloaded = (await providers(request)).find((p) => p.id === saved.id)!;
  expect(reloaded.requestTimeoutSeconds).toBe(420);
  expect(reloaded.apiKeyConfigured).toBe(true);
  await card.getByRole("button", { name: "测试连接", exact: true }).click();
  await expect(card.getByRole("status")).toContainText("连接测试通过", { timeout: 60_000 });
  if (process.env.PROJECTFLOW_GUI_SCREENSHOTS) {
    const directory = path.resolve(process.env.PROJECTFLOW_GUI_SCREENSHOTS);
    mkdirSync(directory, { recursive: true });
    await page.screenshot({ path: path.join(directory, "real-backend-provider-credential.png") });
  }
  await card.getByRole("button", { name: "编辑", exact: true }).click();
  await dialog.getByLabel("清除已保存的凭据", { exact: true }).check();
  await dialog.getByRole("button", { name: "保存 Provider", exact: true }).click();
  await expect(dialog.getByRole("button", { name: "确认变更并保存" })).toBeVisible();
  expect((await providers(request)).find((p) => p.id === saved.id)?.apiKeyConfigured).toBe(true);
  await dialog.getByRole("button", { name: "确认变更并保存" }).click();
  await expect(dialog).toBeHidden();
  expect((await providers(request)).find((p) => p.id === saved.id)?.apiKeyConfigured).toBe(false);
  await card.getByRole("button", { name: "删除", exact: true }).click();
  await page.getByRole("dialog", { name: "删除 Provider" }).getByRole("button", { name: "确认删除", exact: true }).click();
  await expect(card).toHaveCount(0);
  expect((await providers(request)).some((p) => p.id === saved.id)).toBe(false);
});
