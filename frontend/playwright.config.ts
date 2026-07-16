import { defineConfig, devices } from "@playwright/test";
import path from "node:path";

const isWindows = process.platform === "win32";
const windowsMaven = process.env.MAVEN_CMD ?? "mvn.cmd";
const windowsMavenCommand = /\s/.test(windowsMaven) ? `"${windowsMaven}"` : windowsMaven;
const backendCommand = isWindows
  ? `${windowsMavenCommand} -q spring-boot:run "-Dspring-boot.run.profiles=embedded" "-Dspring-boot.run.arguments=--server.port=18080"`
  : "mvn -q spring-boot:run -Dspring-boot.run.profiles=embedded -Dspring-boot.run.arguments=--server.port=18080";

export default defineConfig({
  testDir: "./e2e",
  timeout: 120_000,
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:13037",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: process.env.CI ? "retain-on-failure" : "off",
  },
  projects: [{
    name: "chromium",
    use: { ...devices["Desktop Chrome"], ...(process.env.CI ? {} : { channel: "msedge" as const }) },
  }],
  webServer: [
    {
      command: "node e2e/fixed-model-server.mjs",
      cwd: __dirname,
      url: "http://127.0.0.1:19037/health",
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: backendCommand.replaceAll("18080", "18037"),
      cwd: path.resolve(__dirname, "../backend"),
      env: {
        PROJECTFLOW_DATA_DIR: path.resolve(__dirname, ".e2e-data"),
        PROJECTFLOW_AUTH_REQUIRED: "false",
        PROJECTFLOW_JOB_CORE_THREADS: "1",
        PROJECTFLOW_JOB_MAX_THREADS: "2",
        PROJECTFLOW_JOB_QUEUE_CAPACITY: "4",
        FRONTEND_ORIGIN: "http://127.0.0.1:13037",
      },
      url: "http://127.0.0.1:18037/actuator/health",
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: "npm run dev -- --port 13037",
      cwd: __dirname,
      env: { NEXT_PUBLIC_API_PORT: "18037" },
      url: "http://127.0.0.1:13037",
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
  outputDir: "test-results",
});
