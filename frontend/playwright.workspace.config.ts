import { defineConfig } from "@playwright/test";

// The isolated visual/adapter suite does not need a database or a model provider.
export default defineConfig({
  testDir: "./e2e",
  testMatch: /workspace(?:-productization)?\.spec\.ts/,
  workers: 1,
  timeout: 45_000,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:13040",
    viewport: { width: 1280, height: 801 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    ...(process.env.CI ? {} : { channel: "msedge" }),
  },
  webServer: {
    command: "npm run start -- --hostname 127.0.0.1 --port 13040",
    url: "http://127.0.0.1:13040/workspace/current?demo=1",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
