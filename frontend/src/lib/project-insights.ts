import type { ProjectMaterial } from "./api";

export type FileInsight = {
  path: string;
  name: string;
  moduleName: string;
  fileType: "source" | "test" | "config" | "dependency" | "docs" | "script" | "asset" | "build" | "runtime" | "env" | "unknown";
  role: string;
  summary: string;
  importance: "critical" | "important" | "normal" | "low";
  readPriority: "first_read" | "key" | "normal" | "low";
  actionHint: "modify" | "read_only" | "exclude" | "verify";
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

export type ProjectShape =
  | "frontend_backend_split"
  | "fullstack_monolith"
  | "backend_only"
  | "frontend_only"
  | "script_tool"
  | "desktop_app"
  | "mobile_app"
  | "data_ai_pipeline"
  | "local_prototype_package"
  | "unknown";

export type ArchitectureItem = {
  path: string;
  label: string;
  reason: string;
};

export type ProjectArchitecture = {
  primaryShape: ProjectShape;
  shapeLabel: string;
  shapeTags: string[];
  summary: string;
  entrypoints: ArchitectureItem[];
  coreModules: ArchitectureItem[];
  runtimeArtifacts: ArchitectureItem[];
  dependencySignals: ArchitectureItem[];
  readingOrder: ArchitectureItem[];
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
    const normalizedPath = normalizeProjectPath(path);
    const name = normalizedPath.split("/").filter(Boolean).at(-1) ?? normalizedPath;
    const fileType = inferFileType(normalizedPath);
    const importance = inferImportance(normalizedPath, fileType);
    const role = inferRole(normalizedPath, fileType);
    const riskLevel = inferRiskLevel(normalizedPath, fileType);
    return {
      path,
      name,
      moduleName: inferModuleName(normalizedPath),
      fileType,
      role,
      summary: inferSummary(normalizedPath, fileType),
      importance,
      readPriority: inferReadPriority(normalizedPath, fileType, role),
      actionHint: inferActionHint(normalizedPath, fileType, role),
      riskLevel,
      riskNotes: riskLevel === "none" ? "未识别明显风险。" : inferRiskNotes(normalizedPath, fileType),
    };
  });
}

export function compactProjectPath(path: string) {
  const normalized = normalizeProjectPath(path);
  const javaMarkers = ["/controller/", "/service/", "/repository/", "/mapper/", "/dto/", "/entity/", "/domain/", "/security/", "/support/"];
  if (normalized.includes("src/main/java/") || normalized.includes("src/test/java/")) {
    for (const marker of javaMarkers) {
      const index = normalized.indexOf(marker);
      if (index >= 0) {
        return normalized.slice(index + 1);
      }
    }
  }
  if (normalized.startsWith("frontend/src/app/")) {
    return normalized.replace("frontend/src/", "");
  }
  if (normalized.startsWith("frontend/src/components/")) {
    return normalized.replace("frontend/src/", "");
  }
  if (normalized.startsWith("backend/src/main/resources/")) {
    return normalized.replace("backend/src/main/resources/", "resources/");
  }
  const segments = normalized.split("/").filter(Boolean);
  if (segments.length >= 4 && normalized.length > 56) {
    return `${segments[0]}/.../${segments.slice(-2).join("/")}`;
  }
  return normalized;
}

export function buildProjectArchitecture(paths: string[]): ProjectArchitecture {
  const files = buildFileInsights(paths);
  const primaryShape = inferProjectShape(files);
  const shapeTags = inferShapeTags(files, primaryShape);
  const entrypoints = files
    .filter((file) => isEntrypoint(file.path, file.role))
    .sort((a, b) => scoreEntrypoint(b) - scoreEntrypoint(a))
    .slice(0, 5)
    .map((file) => architectureItem(file, "启动入口", entrypointReason(file)));
  const coreModules = files
    .filter((file) => isCoreModule(file.path, file.role))
    .sort((a, b) => scoreCoreModule(b) - scoreCoreModule(a))
    .slice(0, 8)
    .map((file) => architectureItem(file, file.role, coreModuleReason(file)));
  const runtimeArtifacts = files
    .filter((file) => file.fileType === "runtime" || file.actionHint === "exclude")
    .slice(0, 6)
    .map((file) => architectureItem(file, "运行产物", "运行时生成或保存的数据，理解状态时有用，但通常不应作为源码优先修改。"));
  const dependencySignals = files
    .filter((file) => file.fileType === "dependency" || file.role === "依赖配置")
    .sort((a, b) => scoreDependency(b.path) - scoreDependency(a.path))
    .slice(0, 6)
    .map((file) => architectureItem(file, "依赖配置", "决定项目安装、构建或运行依赖。"));
  const readingOrder = dedupeArchitectureItems([
    ...entrypoints,
    ...coreModules,
    ...dependencySignals,
    ...files
      .filter((file) => file.fileType === "docs")
      .slice(0, 2)
      .map((file) => architectureItem(file, "文档证据", "用于校验项目用途、启动方式和限制说明。")),
  ]).slice(0, 10);

  return {
    primaryShape,
    shapeLabel: shapeLabel(primaryShape),
    shapeTags,
    summary: architectureSummary(primaryShape),
    entrypoints,
    coreModules,
    runtimeArtifacts,
    dependencySignals,
    readingOrder,
  };
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
        .map((item) => compactProjectPath(item.path)),
      riskCount: items.filter((item) => item.riskLevel === "high" || item.riskLevel === "medium").length,
    }))
    .sort((a, b) => scoreModule(b.name) - scoreModule(a.name) || b.count - a.count)
    .slice(0, 8);
}

function normalizeProjectPath(path: string) {
  const normalized = path.replace(/\\/g, "/").replace(/^\/+/, "");
  const segments = normalized.split("/").filter(Boolean);
  if (segments.length <= 1) {
    return normalized;
  }
  const [first, second] = segments;
  const knownRootAfterArchive = new Set([
    "backend",
    "frontend",
    "src",
    "tools",
    "scripts",
    "scorecard_batch",
    "output",
    "docs",
    "test",
    "tests",
  ]);
  const rootLooksGenerated = /(_test_package_\d{8,}|-\d{8,}|_\d{8,})/i.test(first) || first.length > 36;
  if (rootLooksGenerated && knownRootAfterArchive.has(second.toLowerCase())) {
    return segments.slice(1).join("/");
  }
  return normalized;
}

function inferModuleName(path: string) {
  const lower = path.toLowerCase();
  if (lower.startsWith("output/") || lower.startsWith("logs/") || lower.includes("/output/") || lower.includes("/logs/")) {
    return "运行产物";
  }
  if (lower.startsWith("scorecard_batch/") || lower.includes("/domain/") || lower.includes("/model/") || lower.includes("/models/")) {
    return "核心业务";
  }
  if (lower.startsWith("tools/") || lower.startsWith("scripts/") || lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.includes("start-")) {
    return "脚本与入口";
  }
  if (lower.includes("/templates/") || lower.includes("/static/") || lower.endsWith(".jsp") || lower.endsWith(".ftl")) {
    return "一体化页面";
  }
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
  return path.split("/")[0] || "root";
}

function inferFileType(path: string): FileInsight["fileType"] {
  const lower = path.toLowerCase();
  if (lower.includes(".env")) return "env";
  if (isRuntimeArtifactPath(lower)) return "runtime";
  if (isDependencyConfigPath(lower)) return "dependency";
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
  if (isEntrypointPath(lower) || lower.endsWith("package.json") || lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("pyproject.toml") || lower.endsWith("go.mod") || lower.endsWith("cargo.toml") || lower.endsWith("docker-compose.yml") || lower.endsWith("layout.tsx") || lower.endsWith("page.tsx")) {
    return "critical";
  }
  if (fileType === "config" || fileType === "dependency" || fileType === "script" || lower.includes("/controller/") || lower.includes("/service/") || lower.includes("/templates/")) {
    return "important";
  }
  if (fileType === "build" || fileType === "asset" || fileType === "runtime") {
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
  if (lower.endsWith("scorecard_upload_server.py") || lower.endsWith("main.py") || lower.endsWith("app.py") || lower.endsWith("manage.py") || lower.endsWith("server.py")) return "服务入口";
  if (lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.endsWith(".sh")) return lower.includes("start") || lower.includes("启动") ? "启动脚本" : "脚本工具";
  if (lower.includes("/templates/") || lower.endsWith(".jsp") || lower.endsWith(".ftl") || lower.endsWith(".thymeleaf.html")) return "服务端页面模板";
  if (lower.includes("/static/")) return "一体化静态资源";
  if (lower.includes("/controller/") || lower.endsWith("controller.java") || lower.endsWith("views.py")) return "路由/控制器";
  if (lower.includes("/service/") || lower.endsWith("service.java")) return "业务服务";
  if (lower.includes("/repository/") || lower.includes("/mapper/") || lower.endsWith("repository.java")) return "数据访问";
  if (lower.includes("/domain/") || lower.includes("/entity/") || lower.includes("/model/") || lower.includes("/models.") || lower.endsWith("models.py")) return "数据模型";
  if (lower.endsWith("scorecard-upload-prototype.html") || lower.includes("prototype")) return "前端原型";
  if (lower.endsWith("page.tsx")) return "页面入口";
  if (lower.endsWith("layout.tsx")) return "应用布局";
  if (lower.endsWith("package.json") || lower.endsWith("pom.xml") || lower.endsWith("requirements.txt") || lower.endsWith("requirements-ocr.txt") || lower.endsWith("pyproject.toml") || lower.endsWith("go.mod") || lower.endsWith("cargo.toml")) return "依赖配置";
  if (lower.includes("ocr") || lower.includes("ai") || lower.includes("embedding") || lower.includes("model")) return "数据处理";
  if (fileType === "runtime") return "运行产物";
  if (fileType === "config") return "工程配置";
  if (fileType === "docs") return "项目说明文档";
  if (fileType === "test") return "测试或验收证据";
  if (fileType === "script") return "脚本工具";
  return "项目文件";
}

function inferSummary(path: string, fileType: FileInsight["fileType"]) {
  const role = inferRole(path, fileType);
  const lower = path.toLowerCase();
  if (fileType === "runtime") {
    return `${role}。用于理解项目运行状态或本地数据，不建议当作源码优先修改。`;
  }
  if (role === "服务入口" || role === "启动脚本") {
    return `${role}。建议作为第一阅读对象，用它确认项目如何启动、监听端口以及依赖哪些核心模块。`;
  }
  if (lower.includes("/templates/") || role === "前端原型") {
    return `${role}。它承载用户界面和交互入口，适合和控制器、服务入口一起阅读。`;
  }
  return `${role}。当前解释来自 zip 目录树和文件名规则，配置模型后可生成更完整的文件职责、风险和关联变更分析。`;
}

function inferReadPriority(path: string, fileType: FileInsight["fileType"], role: string): FileInsight["readPriority"] {
  const lower = path.toLowerCase();
  if (isEntrypointPath(lower) || role === "服务入口" || role === "启动脚本" || lower.endsWith("readme.md")) {
    return "first_read";
  }
  if (role === "路由/控制器" || role === "业务服务" || role === "数据处理" || role === "前端原型" || role === "服务端页面模板" || fileType === "dependency") {
    return "key";
  }
  if (fileType === "runtime" || fileType === "asset" || fileType === "build") {
    return "low";
  }
  return "normal";
}

function inferActionHint(path: string, fileType: FileInsight["fileType"], role: string): FileInsight["actionHint"] {
  const lower = path.toLowerCase();
  if (fileType === "runtime" || fileType === "build" || lower.includes("/vendor/")) {
    return "exclude";
  }
  if (role === "项目说明文档" || role === "测试或验收证据") {
    return "read_only";
  }
  if (fileType === "env" || fileType === "config" || fileType === "dependency" || role === "启动脚本") {
    return "verify";
  }
  return "modify";
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
    核心业务: "核心业务、计算或数据处理逻辑。",
    脚本与入口: "项目启动入口、本地服务脚本和维护工具。",
    一体化页面: "传统单体项目中的服务端页面模板与静态资源。",
    运行产物: "运行时生成的数据、日志或数据库文件，默认折叠为低优先级。",
  }[name] ?? "项目模块或目录分组。";
  return `${base}${critical ? ` 关键文件 ${critical} 个。` : ""}${risks ? ` 风险信号 ${risks} 个。` : ""}`;
}

function scoreModule(name: string) {
  return {
    核心业务: 6,
    frontend: 5,
    backend: 5,
    一体化页面: 5,
    脚本与入口: 5,
    docs: 4,
    config: 3,
    scripts: 2,
    运行产物: 1,
  }[name as keyof Record<string, number>] ?? 1;
}

function inferProjectShape(files: FileInsight[]): ProjectShape {
  const paths = files.map((file) => file.path.toLowerCase());
  const hasFrontend = files.some((file) => file.moduleName === "frontend" || file.role === "前端原型" || file.role === "页面入口");
  const hasBackend = files.some((file) => file.moduleName === "backend" || file.role === "服务入口" || file.role === "路由/控制器" || file.role === "业务服务");
  const hasServerTemplates = files.some((file) => file.role === "服务端页面模板" || file.role === "一体化静态资源");
  const hasMobile = paths.some((path) => path.includes("android/") || path.includes("ios/") || path.endsWith("pubspec.yaml") || path.includes("react-native"));
  const hasDesktop = paths.some((path) => path.includes("electron") || path.includes("tauri") || path.endsWith("src-tauri/tauri.conf.json"));
  const hasDataAi = files.some((file) => file.role === "数据处理" || file.path.toLowerCase().includes("ocr") || file.path.toLowerCase().includes("dataset"));
  const hasPrototype = paths.some((path) => path.includes("prototype") || path.includes("test_package") || path.includes("start_server") || path.endsWith(".db"));
  const onlyScripts = files.length > 0 && files.every((file) => file.fileType === "script" || file.fileType === "docs" || file.fileType === "dependency");

  if (hasPrototype && hasDataAi) return "local_prototype_package";
  if (hasMobile) return "mobile_app";
  if (hasDesktop) return "desktop_app";
  if (hasServerTemplates && hasBackend) return "fullstack_monolith";
  if (hasFrontend && hasBackend) return "frontend_backend_split";
  if (hasDataAi) return "data_ai_pipeline";
  if (hasBackend) return "backend_only";
  if (hasFrontend) return "frontend_only";
  if (onlyScripts) return "script_tool";
  return "unknown";
}

function inferShapeTags(files: FileInsight[], primaryShape: ProjectShape) {
  const tags = new Set<string>([shapeLabel(primaryShape)]);
  if (files.some((file) => file.role === "服务端页面模板" || file.role === "一体化静态资源")) {
    tags.add("一体化页面");
  }
  if (files.some((file) => file.role === "数据处理" || file.path.toLowerCase().includes("ocr"))) {
    tags.add("数据处理");
  }
  if (files.some((file) => file.fileType === "runtime")) {
    tags.add("含运行数据");
  }
  if (files.some((file) => file.role === "启动脚本" || file.role === "服务入口")) {
    tags.add("有入口");
  }
  return [...tags];
}

function shapeLabel(shape: ProjectShape) {
  const labels: Record<ProjectShape, string> = {
    frontend_backend_split: "前后端分离项目",
    fullstack_monolith: "传统一体项目",
    backend_only: "后端服务项目",
    frontend_only: "前端项目",
    script_tool: "脚本工具项目",
    desktop_app: "桌面端项目",
    mobile_app: "移动端项目",
    data_ai_pipeline: "数据处理项目",
    local_prototype_package: "本地原型包",
    unknown: "未确定项目形态",
  };
  return labels[shape];
}

function architectureSummary(primaryShape: ProjectShape) {
  return shapeLabel(primaryShape);
}

function architectureItem(file: FileInsight, label: string, reason: string): ArchitectureItem {
  return { path: file.path, label, reason };
}

function dedupeArchitectureItems(items: ArchitectureItem[]) {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.path)) {
      return false;
    }
    seen.add(item.path);
    return true;
  });
}

function isEntrypoint(path: string, role: string) {
  const lower = path.toLowerCase();
  return role === "服务入口" || role === "启动脚本" || isEntrypointPath(lower);
}

function isEntrypointPath(lower: string) {
  return lower.endsWith("main.py")
    || lower.endsWith("app.py")
    || lower.endsWith("manage.py")
    || lower.endsWith("server.py")
    || lower.endsWith("scorecard_upload_server.py")
    || lower.endsWith("application.java")
    || lower.endsWith("program.cs")
    || lower.endsWith("main.go")
    || lower.endsWith("main.rs")
    || lower.endsWith("index.js")
    || lower.endsWith("index.ts")
    || lower.endsWith("start_server.bat")
    || lower.endsWith("start_server.ps1")
    || lower.includes("启动.bat");
}

function isCoreModule(path: string, role: string) {
  const lower = path.toLowerCase();
  return role === "路由/控制器"
    || role === "业务服务"
    || role === "数据模型"
    || role === "数据访问"
    || role === "数据处理"
    || role === "前端原型"
    || role === "服务端页面模板"
    || lower.startsWith("scorecard_batch/");
}

function scoreEntrypoint(file: FileInsight) {
  const lower = file.path.toLowerCase();
  if (lower.endsWith(".bat") || lower.endsWith(".ps1")) return 5;
  if (file.role === "服务入口") return 4;
  if (lower.endsWith("main.py") || lower.endsWith("application.java")) return 3;
  return 1;
}

function scoreCoreModule(file: FileInsight) {
  if (file.role === "路由/控制器" || file.role === "前端原型") return 5;
  if (file.role === "服务端页面模板" || file.role === "数据处理") return 4;
  if (file.role === "业务服务") return 3;
  if (file.role === "数据模型" || file.role === "数据访问") return 2;
  return 1;
}

function scoreDependency(path: string) {
  const lower = path.toLowerCase();
  if (lower.endsWith("package.json") || lower.endsWith("pom.xml") || lower.endsWith("pyproject.toml")) return 5;
  if (lower.includes("requirements")) return 4;
  if (lower.endsWith("go.mod") || lower.endsWith("cargo.toml")) return 3;
  return 1;
}

function entrypointReason(file: FileInsight) {
  if (file.role === "启动脚本") {
    return "最接近开发者双击或命令行启动项目的入口。";
  }
  if (file.role === "服务入口") {
    return "后端或本地服务进程入口，通常能看到端口、路由和核心模块调用。";
  }
  return "可能承担应用启动或首屏加载职责。";
}

function coreModuleReason(file: FileInsight) {
  if (file.role === "前端原型" || file.role === "服务端页面模板") {
    return "承载用户流程和页面结构，适合用来理解业务入口。";
  }
  if (file.role === "数据处理") {
    return "承载数据识别、转换或计算链路。";
  }
  if (file.role === "路由/控制器") {
    return "连接页面请求和业务服务，是理解功能流转的关键节点。";
  }
  return "属于核心业务或数据结构，适合在入口之后阅读。";
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
    || lower.startsWith("vendor/")
    || lower.includes("/vendor/")
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

function isDependencyConfigPath(lower: string) {
  return lower.endsWith("package.json")
    || lower.endsWith("pom.xml")
    || lower.endsWith("build.gradle")
    || lower.endsWith("build.gradle.kts")
    || lower.endsWith("pyproject.toml")
    || lower.endsWith("requirements.txt")
    || lower.endsWith("requirements-ocr.txt")
    || lower.endsWith("go.mod")
    || lower.endsWith("cargo.toml")
    || lower.endsWith("gemfile")
    || lower.endsWith("composer.json");
}

function isRuntimeArtifactPath(lower: string) {
  return lower.startsWith("output/")
    || lower.startsWith("logs/")
    || lower.includes("/output/")
    || lower.includes("/logs/")
    || lower.endsWith(".db")
    || lower.endsWith(".sqlite")
    || lower.endsWith(".sqlite3")
    || lower.endsWith(".log");
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
    || lower.includes("/templates/")
    || lower.endsWith(".java")
    || lower.endsWith(".kt")
    || lower.endsWith(".ts")
    || lower.endsWith(".tsx")
    || lower.endsWith(".js")
    || lower.endsWith(".jsx")
    || lower.endsWith(".vue")
    || lower.endsWith(".html")
    || lower.endsWith(".py")
    || lower.endsWith(".go")
    || lower.endsWith(".rs")
    || lower.endsWith(".php")
    || lower.endsWith(".cs")
    || lower.endsWith(".rb");
}
