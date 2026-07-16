import http from "node:http";

let failNext = 0;
let delayNext = 0;
let delayMs = 0;
let failTask = "capability";
let delayTask = "capability";

function json(response, status, value) {
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(value));
}

async function body(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}

const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    return json(response, 200, { status: "UP", service: "fixed-e2e-model" });
  }
  if (request.method === "POST" && request.url === "/control/reset") {
    failNext = 0;
    delayNext = 0;
    delayMs = 0;
    failTask = "capability";
    delayTask = "capability";
    return json(response, 200, { ok: true });
  }
  if (request.method === "POST" && request.url === "/control/fail-next") {
    const payload = JSON.parse((await body(request)) || "{}");
    failNext = Math.max(0, Number(payload.count) || 1);
    failTask = String(payload.task || "capability");
    return json(response, 200, { failNext });
  }
  if (request.method === "POST" && request.url === "/control/delay-next") {
    const payload = JSON.parse((await body(request)) || "{}");
    delayNext = Math.max(0, Number(payload.count) || 1);
    delayMs = Math.max(0, Number(payload.ms) || 1500);
    delayTask = String(payload.task || "capability");
    return json(response, 200, { delayNext, delayMs });
  }
  if (request.method !== "POST" || request.url !== "/v1/chat/completions") {
    return json(response, 404, { error: "not found" });
  }

  const payload = JSON.parse((await body(request)) || "{}");
  const prompt = Array.isArray(payload.messages)
    ? payload.messages.map((message) => String(message.content || "")).join("\n")
    : "";
  const task = prompt.includes("项目历程") ? "timeline"
    : prompt.includes("capabilities") || prompt.includes("项目能力") ? "capability"
      : "segment";
  if (delayNext > 0 && (delayTask === "any" || delayTask === task)) {
    delayNext -= 1;
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }
  if (failNext > 0 && (failTask === "any" || failTask === task)) {
    failNext -= 1;
    return json(response, 503, { error: { message: "controlled E2E failure" } });
  }

  const content = prompt.includes("ALLOWED_MONTH_KEYS_JSON=")
    ? JSON.stringify({
        periodSummary: "项目从最早记录到当前形成了连续、可追溯的演进过程。",
        stages: [{
          title: "持续演进",
          summary: "各月已记录事实共同构成项目的持续演进。",
          monthKeys: jsonArrayAfter(prompt, "ALLOWED_MONTH_KEYS_JSON="),
        }],
        ungroupedMonthKeys: [],
      })
    : prompt.includes("ALLOWED_IDS_JSON=")
      ? JSON.stringify({
          periodSummary: "本时间段围绕可追溯的项目变化形成了连续演进。",
          themes: [{
            title: "可追溯的项目演进",
            summary: "已记录事实完整反映了本时间段的项目变化。",
            factIds: jsonArrayAfter(prompt, "ALLOWED_IDS_JSON="),
          }],
          ungroupedFactIds: [],
        })
      : prompt.includes("capabilities") || prompt.includes("项目能力")
    ? JSON.stringify({ capabilities: [{
        name: "后台任务可靠性",
        summary: "基于兼容项目沉淀形成可取消、可恢复且可追溯的分析能力。",
        problemSolved: "避免重复调用和结果覆盖。",
        featureEntry: "能力与成果 / 分析项目能力",
        sourceIndexes: ["S1"],
        readme: "支持持久化任务、取消与恢复。",
        resume: "完成后台分析任务可靠性与历史兼容闭环。",
        interview: "可说明幂等、取消检查点与持久化状态设计。"
      }] })
    : JSON.stringify({ segments: [{
        segmentTitle: "完成可追溯的项目变化分析",
        plainSummary: "将固定测试仓库中的提交和未提交变化整理为开发推进段。",
        sourceIndexes: ["S1"],
        mainChanges: ["读取本地 Git 变化", "生成分析批次", "自动记录有证据的项目事实"],
        userVisibleValue: "用户完成分析后可以直接离开，并按批次查看自动保存的项目事实。",
        affectedFiles: [],
        confidence: "HIGH",
        needsUserReview: false
      }] });
  return json(response, 200, {
    choices: [{ message: { content }, finish_reason: "stop" }],
    usage: { prompt_tokens: 120, completion_tokens: 80, total_tokens: 200 }
  });
});

function jsonArrayAfter(prompt, marker) {
  const start = prompt.indexOf(marker);
  if (start < 0) return [];
  const line = prompt.slice(start + marker.length).split(/\r?\n/, 1)[0];
  try {
    const value = JSON.parse(line);
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

server.listen(19037, "127.0.0.1");
