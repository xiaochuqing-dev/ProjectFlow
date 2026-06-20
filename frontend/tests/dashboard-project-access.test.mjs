import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const dashboard = readFileSync(join(root, "src/app/dashboard/page.tsx"), "utf8");
const layout = readFileSync(join(root, "src/components/ui/layout.tsx"), "utf8");

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  dashboard.includes("showZipImport"),
  "dashboard should gate zip import behind an explicit add-project entry instead of rendering it permanently",
);

assert(
  !dashboard.includes("导入 / 替换项目 zip"),
  "dashboard should not show a permanent import/replace zip card when a project already exists",
);

assert(
  dashboard.includes("添加项目"),
  "dashboard should expose an add-project entry that opens the zip import flow",
);

assert(
  layout.includes("actions?: ReactNode"),
  "ProjectContextBar should keep an action slot for page-level project actions",
);

console.log("dashboard project access checks passed");
