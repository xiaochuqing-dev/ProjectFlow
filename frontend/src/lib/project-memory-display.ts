const PLACEHOLDER_PATTERN = /^暂无|暂无已确认能力/;
const PATH_EXT_PATTERN = /\.(java|kt|js|jsx|ts|tsx|vue|py|go|rs|php|cs|rb|sql|graphql|properties|ya?ml|xml|md|txt|css|scss|html|json|toml|gradle|ps1|bat)$/i;
const PATH_ROOT_PATTERN = /^(backend|frontend|src|app|apps|services|docs|test|tests|scripts|config|docker|\.github|\.vscode|\.projectflow)[/\\]/i;

export function capabilityBulletItems(value: string, maxItems = 8) {
  const lines = value
    .replace(/\r/g, "\n")
    .split("\n")
    .map(cleanLine)
    .filter(Boolean);

  const directCapabilities: string[] = [];
  const pathLines: string[] = [];
  for (const line of lines) {
    if (PLACEHOLDER_PATTERN.test(line)) continue;
    if (isPathLikeLine(line)) {
      pathLines.push(line);
    } else {
      directCapabilities.push(line);
    }
  }

  const inferred = inferCapabilitiesFromPaths(pathLines);
  return dedupe([...directCapabilities, ...inferred])
    .map((item) => item.length > 120 ? `${item.slice(0, 117)}...` : item)
    .slice(0, maxItems);
}

export function capabilityCountLabel(value: string) {
  const count = capabilityBulletItems(value).length;
  return count ? `${count} 项` : "0 项";
}

function cleanLine(value: string) {
  return value
    .trim()
    .replace(/^[-*]\s*/, "")
    .replace(/^能力[：:]\s*/, "")
    .trim();
}

function isPathLikeLine(value: string) {
  const normalized = value.replace(/\\/g, "/");
  if (!normalized.includes("/")) return false;
  if (PATH_ROOT_PATTERN.test(normalized)) return true;
  return PATH_EXT_PATTERN.test(normalized);
}

function inferCapabilitiesFromPaths(paths: string[]) {
  if (paths.length === 0) return [];
  const normalized = paths.map((path) => path.replace(/\\/g, "/").toLowerCase());
  const capabilities: string[] = ["已导入完整项目结构，可作为后续项目理解和变更追溯的基础证据。"];
  if (normalized.some((path) => path.startsWith("backend/")) && normalized.some((path) => path.startsWith("frontend/"))) {
    capabilities.push("已形成前后端分层的工作台结构，包含 API 服务和前端交互界面。");
  }
  if (normalized.some((path) => /controller|service|repository|api\.ts|app\//.test(path))) {
    capabilities.push("已建立页面、接口和服务层之间的项目数据流转闭环。");
  }
  if (normalized.some((path) => /test|tests|spec|\.test\.|\.spec\./.test(path))) {
    capabilities.push("已沉淀自动化测试入口，可用于后续回归验收。");
  }
  if (normalized.some((path) => /readme|docs\/|agents\.md|architecture|data-model/.test(path))) {
    capabilities.push("已保留项目说明、架构和协作规则，支持长期维护追溯。");
  }
  if (normalized.some((path) => /package\.json|pom\.xml|docker-compose|start-|\.bat|\.ps1/.test(path))) {
    capabilities.push("已具备依赖、启动或部署配置线索，支持本地运行验证。");
  }
  return capabilities;
}

function dedupe(values: string[]) {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    const normalized = value.replace(/\s+/g, " ").replace("：", ":").toLowerCase();
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(value);
  }
  return result;
}
