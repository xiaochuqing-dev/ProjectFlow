import type { ProjectMaterial } from "./api";

export type FileInsight = {
  path: string;
  name: string;
  moduleName: string;
  fileType: "source" | "test" | "config" | "docs" | "script" | "asset" | "build" | "env" | "unknown";
  role: string;
  summary: string;
  importance: "critical" | "important" | "normal" | "low";
  riskLevel: "high" | "medium" | "low" | "none" | "unknown";
  riskNotes: string;
};

export type ModuleGroup = {
  name: string;
  count: number;
  summary: string;
  important: string[];
  riskCount: number;
};

export function projectZipPaths(materials: ProjectMaterial[]) {
  const zipMaterial = materials.find((material) => material.sourceType === "PROJECT_ZIP");
  return zipMaterial ? parseZipDirectoryTree(zipMaterial.content) : [];
}

export function parseZipDirectoryTree(content: string) {
  const lines = content.split(/\r?\n/);
  const treeStart = lines.findIndex((line) => line.trim() === "## Directory tree");
  if (treeStart < 0) {
    return [];
  }
  const result: string[] = [];
  for (const line of lines.slice(treeStart + 1)) {
    if (line.startsWith("## ")) {
      break;
    }
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      const path = trimmed.slice(2);
      if (!isProjectNoisePath(path)) {
        result.push(path);
      }
    }
  }
  return result;
}

export function buildFileInsights(paths: string[]): FileInsight[] {
  return paths.map((path) => {
    const name = path.split("/").filter(Boolean).at(-1) ?? path;
    const fileType = inferFileType(path);
    const importance = inferImportance(path, fileType);
    const riskLevel = inferRiskLevel(path, fileType);
    return {
      path,
      name,
      moduleName: inferModuleName(path),
      fileType,
      role: inferRole(path, fileType),
      summary: inferSummary(path, fileType),
      importance,
      riskLevel,
      riskNotes: riskLevel === "none" ? "未识别明显风险。" : inferRiskNotes(path, fileType),
    };
  });
}

export function buildModuleGroups(paths: string[]): ModuleGroup[] {
  const insights = buildFileInsights(paths);
  const grouped = new Map<string, FileInsight[]>();
  for (const insight of insights) {
    grouped.set(insight.moduleName, [...(grouped.get(insight.moduleName) ?? []), insight]);
  }

  return [...grouped.entries()]
    .map(([name, items]) => ({
      name,
      count: items.length,
      summary: inferModuleSummary(name, items),
      important: items
        .filter((item) => item.importance === "critical" || item.importance === "important")
        .slice(0, 4)
        .map((item) => item.path),
      riskCount: items.filter((item) => item.riskLevel === "high" || item.riskLevel === "medium").length,
    }))
    .sort((a, b) => scoreModule(b.name) - scoreModule(a.name) || b.count - a.count)
    .slice(0, 8);
}

function inferModuleName(path: string) {
  const lower = path.toLowerCase();
  if (looksLikeFrontendPath(lower)) {
    return "frontend";
  }
  if (looksLikeBackendPath(lower)) {
    return "backend";
  }
  if (lower.startsWith("docs/") || lower.endsWith("readme.md") || lower.endsWith("agents.md")) {
    return "docs";
  }
  if (lower.includes(".vscode/") || lower.includes(".github/") || lower.includes("docker") || lower.includes(".env")) {
    return "config";
  }
  if (lower.startsWith("scripts/") || lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.includes("start-")) {
    return "scripts";
  }
  return path.split("/")[0] || "root";
}

function inferFileType(path: string): FileInsight["fileType"] {
  const lower = path.toLowerCase();
  if (lower.includes(".env")) return "env";
  if (lower.endsWith(".md") || lower.endsWith(".mdx")) return "docs";
  if (lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".toml") || lower.endsWith(".xml")) return "config";
  if (lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.endsWith(".sh")) return "script";
  if (isTestPath(lower)) return "test";
  if (isSourceCodePath(lower)) return "source";
  if (lower.includes("dist/") || lower.includes("build/") || lower.includes(".next/")) return "build";
  if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg")) return "asset";
  return "unknown";
}

function inferImportance(path: string, fileType: FileInsight["fileType"]): FileInsight["importance"] {
  const lower = path.toLowerCase();
  if (lower.endsWith("package.json") || lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("pyproject.toml") || lower.endsWith("go.mod") || lower.endsWith("cargo.toml") || lower.endsWith("docker-compose.yml") || lower.endsWith("layout.tsx") || lower.endsWith("page.tsx")) {
    return "critical";
  }
  if (fileType === "config" || fileType === "script" || lower.includes("/controller/") || lower.includes("/service/")) {
    return "important";
  }
  if (fileType === "build" || fileType === "asset") {
    return "low";
  }
  return "normal";
}

function inferRiskLevel(path: string, fileType: FileInsight["fileType"]): FileInsight["riskLevel"] {
  const lower = path.toLowerCase();
  if ((fileType === "env" && !lower.endsWith(".env.example")) || lower.endsWith(".pem") || lower.endsWith(".key")) {
    return "high";
  }
  if (lower.includes("security") || lower.includes("auth") || lower.includes("jwt") || lower.includes("docker-compose")) {
    return "medium";
  }
  if (fileType === "unknown") {
    return "unknown";
  }
  return "none";
}

function inferRole(path: string, fileType: FileInsight["fileType"]) {
  const lower = path.toLowerCase();
  if (lower.endsWith("page.tsx")) return "页面入口";
  if (lower.endsWith("layout.tsx")) return "应用布局";
  if (lower.includes("/controller/")) return "后端接口层";
  if (lower.includes("/service/")) return "业务服务层";
  if (lower.includes("/repository/")) return "数据访问层";
  if (lower.endsWith("package.json") || lower.endsWith("pom.xml")) return "依赖与构建配置";
  if (fileType === "config") return "工程配置";
  if (fileType === "docs") return "项目说明文档";
  if (fileType === "test") return "测试或验收证据";
  if (fileType === "script") return "本地脚本";
  return "项目文件";
}

function inferSummary(path: string, fileType: FileInsight["fileType"]) {
  const role = inferRole(path, fileType);
  return `${role}。当前解释来自 zip 目录树和文件名规则，配置模型后可生成更完整的文件职责、风险和关联变更分析。`;
}

function inferRiskNotes(path: string, fileType: FileInsight["fileType"]) {
  const lower = path.toLowerCase();
  if (fileType === "env" && !lower.endsWith(".env.example")) {
    return "疑似环境变量或敏感配置文件，默认不应发送给模型。";
  }
  if (lower.includes("auth") || lower.includes("jwt") || lower.includes("security")) {
    return "认证或安全相关文件，后续改动需要重点审查。";
  }
  if (lower.includes("docker-compose")) {
    return "部署配置会影响本地和生产运行方式，建议确认端口、凭据和服务依赖。";
  }
  return "需要模型或用户进一步确认风险。";
}

function inferModuleSummary(name: string, items: FileInsight[]) {
  const critical = items.filter((item) => item.importance === "critical").length;
  const risks = items.filter((item) => item.riskLevel === "high" || item.riskLevel === "medium").length;
  const base = {
    frontend: "前端界面与交互入口。",
    backend: "后端接口、业务逻辑和数据访问。",
    docs: "项目说明、设计文档和阶段计划。",
    config: "运行、部署、编辑器和环境配置。",
    scripts: "本地启动、维护和自动化脚本。",
  }[name] ?? "项目模块或目录分组。";
  return `${base}${critical ? ` 关键文件 ${critical} 个。` : ""}${risks ? ` 风险信号 ${risks} 个。` : ""}`;
}

function scoreModule(name: string) {
  return { frontend: 5, backend: 5, docs: 4, config: 3, scripts: 2 }[name as keyof Record<string, number>] ?? 1;
}

function isProjectNoisePath(path: string) {
  const lower = path.toLowerCase();
  return lower.startsWith(".codex-run/")
    || lower.includes("/.codex-run/")
    || lower.includes("/old-git-")
    || lower.startsWith(".git/")
    || lower.includes("/.git/")
    || lower.startsWith("node_modules/")
    || lower.includes("/node_modules/")
    || lower.startsWith(".venv/")
    || lower.includes("/.venv/")
    || lower.startsWith("venv/")
    || lower.includes("/venv/")
    || lower.includes("/__pycache__/")
    || lower.includes("/.pytest_cache/")
    || lower.includes("/.mypy_cache/")
    || lower.includes("/.ruff_cache/")
    || lower.includes("/coverage/")
    || lower.includes("/dist/")
    || lower.includes("/build/")
    || lower.includes("/target/")
    || lower.includes("/.next/")
    || lower.includes("/.turbo/");
}

function looksLikeFrontendPath(lower: string) {
  return lower.startsWith("frontend/")
    || lower.startsWith("web/")
    || lower.startsWith("client/")
    || lower.startsWith("ui/")
    || lower.startsWith("apps/web/")
    || lower.startsWith("apps/frontend/")
    || lower.startsWith("packages/web/")
    || lower.startsWith("packages/ui/")
    || lower.includes("/src/app/")
    || lower.includes("/src/components/")
    || lower.endsWith("page.tsx")
    || lower.endsWith("app.tsx")
    || lower.endsWith("vite.config.ts")
    || lower.endsWith("vite.config.js")
    || lower.endsWith("next.config.ts")
    || lower.endsWith("next.config.js");
}

function looksLikeBackendPath(lower: string) {
  return lower.startsWith("backend/")
    || lower.startsWith("server/")
    || lower.startsWith("api/")
    || lower.startsWith("services/api/")
    || lower.startsWith("services/server/")
    || lower.startsWith("services/worker/")
    || lower.includes("/src/main/")
    || lower.includes("/controller/")
    || lower.includes("/service/")
    || lower.endsWith("pom.xml")
    || lower.endsWith("build.gradle")
    || lower.endsWith("build.gradle.kts")
    || lower.endsWith("pyproject.toml")
    || lower.endsWith("requirements.txt")
    || lower.endsWith("go.mod")
    || lower.endsWith("main.py");
}

function isTestPath(lower: string) {
  return lower.startsWith("test/")
    || lower.startsWith("tests/")
    || lower.startsWith("spec/")
    || lower.includes("/test/")
    || lower.includes("/tests/")
    || lower.includes("/spec/")
    || lower.includes("/__tests__/")
    || lower.includes(".test.")
    || lower.includes(".spec.")
    || lower.endsWith("_test.py")
    || lower.endsWith("test_main.py");
}

function isSourceCodePath(lower: string) {
  return lower.includes("/src/")
    || lower.startsWith("src/")
    || lower.includes("/app/")
    || lower.endsWith(".java")
    || lower.endsWith(".kt")
    || lower.endsWith(".ts")
    || lower.endsWith(".tsx")
    || lower.endsWith(".js")
    || lower.endsWith(".jsx")
    || lower.endsWith(".vue")
    || lower.endsWith(".py")
    || lower.endsWith(".go")
    || lower.endsWith(".rs")
    || lower.endsWith(".php")
    || lower.endsWith(".cs")
    || lower.endsWith(".rb");
}
