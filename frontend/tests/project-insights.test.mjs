import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

const sourcePath = path.resolve("src/lib/project-insights.ts");
const source = fs.readFileSync(sourcePath, "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const moduleUrl = `data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`;
const insights = await import(moduleUrl);

const scorecardPaths = [
  "frontend/scorecard-upload-prototype.html",
  "tools/scorecard_upload_server.py",
  "scorecard_batch/identity.py",
  "scorecard_batch/models.py",
  "scorecard_batch/ocr.py",
  "requirements.txt",
  "requirements-ocr.txt",
  "README_TEST_PACKAGE.md",
  "start_server.bat",
  "start_server.ps1",
  "output/scorecard_upload.db",
];

const scorecardArchitecture = insights.buildProjectArchitecture(scorecardPaths);
assert.equal(scorecardArchitecture.primaryShape, "local_prototype_package");
assert(scorecardArchitecture.shapeTags.includes("数据处理"));
assert.equal(scorecardArchitecture.entrypoints[0].path, "start_server.bat");
assert.equal(scorecardArchitecture.readingOrder[0].path, "start_server.bat");
assert.equal(scorecardArchitecture.summary, "本地原型包");

const scorecardFiles = insights.buildFileInsights(scorecardPaths);
assert.equal(scorecardFiles.find((file) => file.path === "requirements-ocr.txt")?.role, "依赖配置");
assert.equal(scorecardFiles.find((file) => file.path === "output/scorecard_upload.db")?.fileType, "runtime");
assert.equal(scorecardFiles.find((file) => file.path === "tools/scorecard_upload_server.py")?.role, "服务入口");
assert.equal(
  insights.compactProjectPath("scorecard_upload_test_package_20260530_173307/frontend/scorecard-upload-prototype.html"),
  "frontend/scorecard-upload-prototype.html",
);
assert.equal(
  insights.compactProjectPath("backend/src/main/java/com/projectflow/controller/AiOutputController.java"),
  "controller/AiOutputController.java",
);

const monolithPaths = [
  "pom.xml",
  "src/main/java/com/example/web/HomeController.java",
  "src/main/java/com/example/service/ScoreService.java",
  "src/main/java/com/example/repository/ScoreRepository.java",
  "src/main/java/com/example/domain/ScoreRecord.java",
  "src/main/resources/templates/index.html",
  "src/main/resources/static/app.css",
  "src/main/resources/application.yml",
  "src/test/java/com/example/HomeControllerTest.java",
];

const monolithArchitecture = insights.buildProjectArchitecture(monolithPaths);
assert.equal(monolithArchitecture.primaryShape, "fullstack_monolith");
assert(monolithArchitecture.shapeTags.includes("一体化页面"));
assert(monolithArchitecture.coreModules.some((item) => item.path.includes("HomeController.java")));
assert(monolithArchitecture.coreModules.some((item) => item.path.includes("templates/index.html")));

console.log("project-insights tests passed");
