package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.V2ProjectDtos.ProjectAnalysisResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisRequest;
import com.projectflow.dto.V2ProjectDtos.ProjectFileAnalysisResponse;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.entity.ProjectMaterial;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectMaterialRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectAnalysisService.class);
    private static final int MAX_FILE_SNIPPET_CHARS = 6_000;
    private static final int MODEL_ANALYSIS_MAX_TOKENS = 100_000;

    private final ProjectRepository projectRepository;
    private final ProjectMaterialRepository materialRepository;
    private final AiProviderRepository aiProviderRepository;
    private final ModelGatewayService modelGatewayService;

    public ProjectAnalysisService(
        ProjectRepository projectRepository,
        ProjectMaterialRepository materialRepository,
        AiProviderRepository aiProviderRepository,
        ModelGatewayService modelGatewayService
    ) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.aiProviderRepository = aiProviderRepository;
        this.modelGatewayService = modelGatewayService;
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisResponse runProjectAnalysis(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMaterial zipMaterial = latestZipMaterial(project.getId());
        String analysisMaterial = sanitizeProjectMaterialForAnalysis(zipMaterial.getContent());
        ProjectAnalysisResponse fallback = localProjectAnalysis(project, analysisMaterial);
        AiProvider provider = configuredProvider(userId);
        if (provider == null) {
            return fallback;
        }
        try {
            String prompt = """
                你是 ProjectFlow 的项目架构分析器。只依据下方项目材料分析，所有自然语言必须使用简体中文。
                返回严格 JSON，字段为：
                summary, architecture, modules, risks, importantFiles, evidence, limitations, confidence。
                modules、risks、importantFiles、evidence、limitations 必须是字符串数组。

                质量要求：
                1. summary 用 3-5 句说明项目用途、主要技术组成和当前工程状态。
                2. architecture 必须结合真实目录或配置文件说明前端、后端、数据和部署关系。
                3. 每项风险必须写明证据文件和可能影响；无证据时不要猜测。
                4. evidence 至少列出 3 条“文件路径：观察到的事实”。
                5. limitations 明确列出材料缺失、未读取文件或无法确认的内容。
                6. 技术名、文件路径和代码标识符保留原文，禁止输出完整英文说明句。

                项目名称：%s
                已有描述：%s
                项目材料：
                %s
                """.formatted(project.getName(), project.getDescription(), truncate(analysisMaterial, 20_000));
            JsonNode json = modelGatewayService.callJson(provider, prompt, MODEL_ANALYSIS_MAX_TOKENS);
            return new ProjectAnalysisResponse(
                chineseTextOr(json, "summary", fallback.summary()),
                chineseTextOr(json, "architecture", fallback.architecture()),
                stringArrayOr(json, "modules", fallback.modules()),
                chineseStringArrayOr(json, "risks", fallback.risks()),
                stringArrayOr(json, "importantFiles", fallback.importantFiles()),
                chineseStringArrayOr(json, "evidence", fallback.evidence()),
                chineseStringArrayOr(json, "limitations", fallback.limitations()),
                true,
                true,
                provider.getName(),
                "MODEL_ANALYSIS",
                normalizeConfidence(json),
                "模型已完成项目分析；结论已附带文件证据和分析局限。"
            );
        } catch (Exception exception) {
            LOGGER.warn(
                "Project model analysis fell back to local rules: projectId={}, provider={}, error={}",
                projectId,
                provider.getName(),
                exception.toString()
            );
            return new ProjectAnalysisResponse(
                fallback.summary(),
                fallback.architecture(),
                fallback.modules(),
                fallback.risks(),
                fallback.importantFiles(),
                fallback.evidence(),
                fallback.limitations(),
                true,
                false,
                provider.getName(),
                "LOCAL_RULE",
                fallback.confidence(),
                "模型分析失败，已保留本地规则结果。" + modelGatewayService.failureMessage(exception)
            );
        }
    }

    @Transactional(readOnly = true)
    public ProjectFileAnalysisResponse analyzeProjectFile(UUID userId, UUID projectId, ProjectFileAnalysisRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        ProjectMaterial zipMaterial = latestZipMaterial(project.getId());
        String analysisMaterial = sanitizeProjectMaterialForAnalysis(zipMaterial.getContent());
        List<String> paths = parseDirectoryTree(analysisMaterial);
        String requestedPath = request.path().trim();
        if (paths.stream().noneMatch(path -> path.equals(requestedPath))) {
            throw new AppException("PROJECT_FILE_NOT_FOUND", "Project file was not found in imported zip material", HttpStatus.NOT_FOUND);
        }

        String fileContent = extractIndexedFileContent(analysisMaterial, requestedPath);
        AiProvider provider = configuredProvider(userId);
        ProjectFileAnalysisResponse fallback = localFileAnalysis(
            requestedPath,
            fileContent,
            provider != null,
            provider == null ? null : provider.getName(),
            "LOCAL_RULE",
            "已使用本地规则生成基础解释。"
        );
        if (provider == null) {
            return fallback;
        }
        if (isSensitivePath(requestedPath)) {
            return localFileAnalysis(requestedPath, "", true, provider.getName(), "LOCAL_RULE", "敏感文件不会发送给模型，已使用本地规则解释。");
        }
        try {
            String prompt = """
                你是 ProjectFlow 的文件分析器。所有自然语言必须使用简体中文。
                返回严格 JSON，字段为：
                path, fileType, role, summary, importance, riskLevel, riskNotes,
                evidence, relatedFiles, limitations, confidence。
                evidence 和 relatedFiles 必须是字符串数组。

                质量要求：
                1. role 说明该文件在当前项目中的具体职责，不写通用模板话术。
                2. summary 必须引用可见的类名、依赖、配置项、函数或文本事实。
                3. riskNotes 说明证据、影响和建议检查点；没有风险证据时明确写“未发现明确风险证据”。
                4. evidence 至少列出 2 条代码或配置事实；没有文件内容时只能依据路径，并在 limitations 中说明。
                5. relatedFiles 只填写项目材料中真实存在的相关路径。
                6. 技术名、路径和代码标识符保留原文，禁止输出完整英文说明句。

                项目：%s
                文件路径：%s
                文件内容：
                %s

                项目结构摘要：
                %s
                """.formatted(
                    project.getName(),
                    requestedPath,
                    fileContent.isBlank() ? "[未索引到文件内容，只能依据路径分析]" : truncate(fileContent, MAX_FILE_SNIPPET_CHARS),
                    fileStructureContext(analysisMaterial, requestedPath)
                );
            JsonNode json = modelGatewayService.callJson(provider, prompt, MODEL_ANALYSIS_MAX_TOKENS);
            return new ProjectFileAnalysisResponse(
                requestedPath,
                textOr(json, "fileType", fallback.fileType()),
                chineseTextOr(json, "role", fallback.role()),
                chineseTextOr(json, "summary", fallback.summary()),
                textOr(json, "importance", fallback.importance()),
                textOr(json, "riskLevel", fallback.riskLevel()),
                chineseTextOr(json, "riskNotes", fallback.riskNotes()),
                chineseStringArrayOr(json, "evidence", fallback.evidence()),
                stringArrayOr(json, "relatedFiles", fallback.relatedFiles()),
                chineseTextOr(json, "limitations", fallback.limitations()),
                true,
                true,
                provider.getName(),
                "MODEL_ANALYSIS",
                normalizeConfidence(json),
                "模型已根据已索引的文件内容生成中文解释。"
            );
        } catch (Exception exception) {
            LOGGER.warn(
                "File model analysis fell back to local rules: projectId={}, path={}, provider={}, error={}",
                projectId,
                requestedPath,
                provider.getName(),
                exception.toString()
            );
            return localFileAnalysis(
                requestedPath,
                fileContent,
                true,
                provider.getName(),
                "LOCAL_RULE",
                "模型分析失败，已使用本地规则解释。" + modelGatewayService.failureMessage(exception)
            );
        }
    }

    private ProjectAnalysisResponse localProjectAnalysis(ProjectSpace project, String materialContent) {
        List<String> paths = parseDirectoryTree(materialContent);
        List<String> modules = paths.stream()
            .map(this::moduleName)
            .distinct()
            .limit(12)
            .toList();
        List<String> importantFiles = paths.stream()
            .filter(this::isImportantProjectFile)
            .limit(12)
            .toList();
        String readmeTitle = extractReadmeTitle(materialContent);
        String summary = !readmeTitle.isBlank()
            ? readmeTitle + "：已导入 " + paths.size() + " 个文件信号，当前使用本地规则生成基础项目画像。"
            : project.getName() + "：已导入 " + paths.size() + " 个文件信号，当前使用本地规则生成基础项目画像。";
        List<String> risks = new ArrayList<>();
        risks.add("模型深度分析尚未完成；当前结论只来自目录树、文件名和关键配置规则。");
        if (paths.stream().anyMatch(path -> path.toLowerCase().contains("docker-compose"))) {
            risks.add("存在部署配置文件，后续需要确认端口、凭据来源和服务依赖。");
        }
        if (paths.stream().noneMatch(path -> path.toLowerCase().contains("test"))) {
            risks.add("未识别测试文件，工程质量证据可能不足。");
        }
        List<String> evidence = new ArrayList<>();
        if (!importantFiles.isEmpty()) {
            importantFiles.stream()
                .limit(6)
                .forEach(path -> evidence.add(path + "：已在导入项目中识别，可作为架构判断依据。"));
        }
        if (evidence.isEmpty()) {
            evidence.add("目录树：已识别 " + paths.size() + " 个可分析文件路径。");
        }
        List<String> limitations = List.of(
            "当前结果未使用模型，仅依据目录、文件名和已索引文本生成。",
            "未索引的二进制文件、生成产物和敏感文件未参与分析。"
        );
        return new ProjectAnalysisResponse(
            summary,
            modules.isEmpty() ? "尚未识别模块。请先导入完整项目 zip。" : "识别到模块：" + String.join("、", modules) + "。点击模块可进入文件理解页集中查看。",
            modules,
            risks,
            importantFiles,
            evidence,
            limitations,
            false,
            false,
            null,
            "LOCAL_RULE",
            "medium",
            "未配置可用模型，已使用本地规则生成基础项目画像。"
        );
    }

    private ProjectFileAnalysisResponse localFileAnalysis(
        String path,
        String fileContent,
        boolean providerConfigured,
        String providerName,
        String analysisSource,
        String message
    ) {
        String fileType = inferFileType(path);
        String role = inferFileRole(path, fileType);
        String riskLevel = inferFileRiskLevel(path, fileType);
        List<String> evidence = new ArrayList<>();
        evidence.add(path + "：文件路径和扩展名已从导入项目中确认。");
        if (!fileContent.isBlank()) {
            String firstSignal = fileContent.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");
            if (!firstSignal.isBlank()) {
                evidence.add("文件内容首个有效信号：" + truncate(firstSignal, 180));
            }
        }
        return new ProjectFileAnalysisResponse(
            path,
            fileType,
            role,
            role + (fileContent.isBlank()
                ? "。当前未索引到文件正文，只能依据路径和文件类型给出基础判断。"
                : "。已读取导入时保存的安全文本片段，可供模型进一步解释。"),
            inferFileImportance(path, fileType),
            riskLevel,
            inferFileRiskNotes(path, fileType, riskLevel),
            evidence,
            List.of(),
            fileContent.isBlank()
                ? "导入材料中没有该文件正文；重新导入项目 zip 后可补充安全文本片段。"
                : "本地规则未执行完整语义分析，代码调用关系仍需模型或开发者确认。",
            providerConfigured,
            false,
            providerName,
            analysisSource,
            "medium",
            message
        );
    }

    private ProjectMaterial latestZipMaterial(UUID projectId) {
        return materialRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .filter(material -> material.getSourceType() == MaterialSourceType.PROJECT_ZIP)
            .findFirst()
            .orElseThrow(() -> new AppException("PROJECT_ZIP_NOT_FOUND", "Import a project zip before running project analysis", HttpStatus.BAD_REQUEST));
    }

    private AiProvider configuredProvider(UUID userId) {
        return aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)
            .stream()
            .filter(provider -> provider.getType() != AiProviderType.MOCK)
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .findFirst()
            .orElse(null);
    }

    private String fileStructureContext(String materialContent, String requestedPath) {
        String targetModule = moduleName(requestedPath);
        LinkedHashSet<String> relatedPaths = new LinkedHashSet<>();
        parseDirectoryTree(materialContent).stream()
            .filter(path -> moduleName(path).equals(targetModule))
            .limit(50)
            .forEach(relatedPaths::add);
        parseDirectoryTree(materialContent).stream()
            .filter(this::isImportantProjectFile)
            .limit(20)
            .forEach(relatedPaths::add);
        return relatedPaths.isEmpty()
            ? "[未识别到相关项目结构]"
            : truncate(String.join("\n", relatedPaths.stream().map(path -> "- " + path).toList()), 6_000);
    }

    private String textOr(JsonNode json, String field, String fallback) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return fallback;
        }
        return value.asText();
    }

    // 模型有时把 confidence 返回成整句中文（如"中等偏高，材料覆盖了完整的目录..."），
    // 而 confidence 列只有 VARCHAR(40)，会溢出。这里统一压成 high/medium/low。
    private String normalizeConfidence(JsonNode json) {
        String raw = textOr(json, "confidence", "medium");
        if (raw.length() <= 20) {
            return raw;
        }
        String lower = raw.toLowerCase();
        if (lower.contains("high") || lower.contains("高") || raw.contains("充分") || raw.contains("完整")) {
            return "high";
        }
        if (lower.contains("low") || lower.contains("低") || raw.contains("不足") || raw.contains("有限")) {
            return "low";
        }
        return "medium";
    }

    private String chineseTextOr(JsonNode json, String field, String fallback) {
        String value = textOr(json, field, fallback);
        return containsChinese(value) ? value : fallback;
    }

    private List<String> stringArrayOr(JsonNode json, String field, List<String> fallback) {
        JsonNode value = json.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            return fallback;
        }
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (!item.asText("").isBlank()) {
                result.add(item.asText());
            }
        });
        return result.isEmpty() ? fallback : result;
    }

    private List<String> chineseStringArrayOr(JsonNode json, String field, List<String> fallback) {
        List<String> values = stringArrayOr(json, field, fallback);
        if (values == fallback) {
            return fallback;
        }
        List<String> chineseValues = values.stream()
            .filter(this::containsChinese)
            .toList();
        return chineseValues.isEmpty() ? fallback : chineseValues;
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints()
            .anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private List<String> parseDirectoryTree(String content) {
        List<String> result = new ArrayList<>();
        boolean inTree = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.equals("## Directory tree")) {
                inTree = true;
                continue;
            }
            if (inTree && trimmed.startsWith("## ")) {
                break;
            }
            if (inTree && trimmed.startsWith("- ")) {
                String path = trimmed.substring(2);
                if (!isProjectNoisePath(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private String sanitizeProjectMaterialForAnalysis(String content) {
        StringBuilder sanitized = new StringBuilder();
        boolean skippingNoiseBlock = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("### ")) {
                String path = trimmed.substring(4).trim();
                skippingNoiseBlock = isProjectNoisePath(path) || lineContainsProjectNoise(path);
            } else if (trimmed.startsWith("## ")) {
                skippingNoiseBlock = false;
            }
            if (skippingNoiseBlock || lineContainsProjectNoise(line)) {
                continue;
            }
            sanitized.append(line).append('\n');
        }
        return sanitized.toString().trim();
    }

    private boolean lineContainsProjectNoise(String value) {
        String lower = value.toLowerCase().replace("\\", "/");
        return lower.contains(".codex-run/")
            || lower.contains("old-git-")
            || lower.contains(".git/objects/")
            || lower.contains(".git/config")
            || lower.contains(".git/head");
    }

    private String extractReadmeTitle(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.equals("# Project zip summary")) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    private String moduleName(String path) {
        String lower = path.toLowerCase();
        if (looksLikeFrontendPath(lower)) {
            return "frontend";
        }
        if (looksLikeBackendPath(lower)) {
            return "backend";
        }
        if (lower.startsWith("docs/") || lower.endsWith("readme.md") || lower.endsWith("agents.md")) {
            return "docs";
        }
        if (lower.contains(".vscode/") || lower.contains(".github/") || lower.contains("docker") || lower.contains(".env")) {
            return "config";
        }
        if (lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.endsWith(".sh") || lower.contains("start-")) {
            return "scripts";
        }
        return path.contains("/") ? path.substring(0, path.indexOf('/')) : "root";
    }

    private boolean isImportantProjectFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith("package.json")
            || lower.endsWith("pom.xml")
            || lower.endsWith("build.gradle")
            || lower.endsWith("build.gradle.kts")
            || lower.endsWith("settings.gradle")
            || lower.endsWith("pyproject.toml")
            || lower.endsWith("requirements.txt")
            || lower.endsWith("go.mod")
            || lower.endsWith("cargo.toml")
            || lower.endsWith("composer.json")
            || lower.endsWith(".csproj")
            || lower.endsWith("docker-compose.yml")
            || lower.endsWith("readme.md")
            || lower.endsWith("page.tsx")
            || lower.endsWith("app.tsx")
            || lower.endsWith("main.py")
            || lower.contains("/controller/")
            || lower.contains("/service/");
    }

    private String inferFileType(String path) {
        String lower = path.toLowerCase();
        if (lower.contains(".env")) return "env";
        if (lower.endsWith(".md") || lower.endsWith(".mdx")) return "docs";
        if (lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".toml") || lower.endsWith(".xml")) return "config";
        if (lower.endsWith(".bat") || lower.endsWith(".ps1") || lower.endsWith(".sh")) return "script";
        if (isTestPath(lower)) return "test";
        if (isSourceCodePath(lower)) return "source";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg")) return "asset";
        return "unknown";
    }

    private String inferFileRole(String path, String fileType) {
        String lower = path.toLowerCase();
        if (lower.endsWith("page.tsx")) return "页面入口";
        if (lower.endsWith("layout.tsx")) return "应用布局";
        if (lower.contains("/controller/")) return "后端接口层";
        if (lower.contains("/service/")) return "业务服务层";
        if (lower.contains("/repository/")) return "数据访问层";
        if (lower.endsWith("package.json") || lower.endsWith("pom.xml")) return "依赖与构建配置";
        if (fileType.equals("config")) return "工程配置";
        if (fileType.equals("docs")) return "项目说明文档";
        if (fileType.equals("test")) return "测试或验收证据";
        if (fileType.equals("script")) return "本地脚本";
        return "项目文件";
    }

    private String inferFileImportance(String path, String fileType) {
        String lower = path.toLowerCase();
        if (lower.endsWith("package.json") || lower.endsWith("pom.xml") || lower.endsWith("docker-compose.yml") || lower.endsWith("layout.tsx") || lower.endsWith("page.tsx")) {
            return "critical";
        }
        if (fileType.equals("config") || fileType.equals("script") || lower.contains("/controller/") || lower.contains("/service/")) {
            return "important";
        }
        return "normal";
    }

    private String inferFileRiskLevel(String path, String fileType) {
        String lower = path.toLowerCase();
        if ((fileType.equals("env") && !lower.endsWith(".env.example")) || lower.endsWith(".pem") || lower.endsWith(".key")) {
            return "high";
        }
        if (lower.contains("security") || lower.contains("auth") || lower.contains("jwt") || lower.contains("docker-compose")) {
            return "medium";
        }
        return "none";
    }

    private String inferFileRiskNotes(String path, String fileType, String riskLevel) {
        String lower = path.toLowerCase();
        if (riskLevel.equals("none")) {
            return "未识别明显风险。";
        }
        if (fileType.equals("env") && !lower.endsWith(".env.example")) {
            return "疑似环境变量或敏感配置文件，默认不发送给模型。";
        }
        if (lower.contains("auth") || lower.contains("jwt") || lower.contains("security")) {
            return "认证或安全相关文件，后续改动需要重点审查。";
        }
        if (lower.contains("docker-compose")) {
            return "部署配置会影响本地和生产运行方式，建议确认端口、凭据和服务依赖。";
        }
        return "需要模型或用户进一步确认风险。";
    }

    private boolean isProjectNoisePath(String path) {
        String lower = path.toLowerCase();
        return lower.startsWith(".codex-run/")
            || lower.contains("/.codex-run/")
            || lower.contains("/old-git-")
            || lower.startsWith(".git/")
            || lower.contains("/.git/")
            || lower.startsWith("node_modules/")
            || lower.contains("/node_modules/")
            || lower.startsWith(".venv/")
            || lower.contains("/.venv/")
            || lower.startsWith("venv/")
            || lower.contains("/venv/")
            || lower.contains("/__pycache__/")
            || lower.contains("/.pytest_cache/")
            || lower.contains("/.mypy_cache/")
            || lower.contains("/.ruff_cache/")
            || lower.contains("/coverage/")
            || lower.contains("/dist/")
            || lower.contains("/build/")
            || lower.contains("/target/")
            || lower.contains("/.next/")
            || lower.contains("/.turbo/");
    }

    private boolean looksLikeFrontendPath(String lowerPath) {
        return lowerPath.startsWith("frontend/")
            || lowerPath.startsWith("web/")
            || lowerPath.startsWith("client/")
            || lowerPath.startsWith("ui/")
            || lowerPath.startsWith("apps/web/")
            || lowerPath.startsWith("apps/frontend/")
            || lowerPath.startsWith("packages/web/")
            || lowerPath.startsWith("packages/ui/")
            || lowerPath.contains("/src/app/")
            || lowerPath.contains("/src/components/")
            || lowerPath.endsWith("page.tsx")
            || lowerPath.endsWith("app.tsx")
            || lowerPath.endsWith("vite.config.ts")
            || lowerPath.endsWith("vite.config.js")
            || lowerPath.endsWith("next.config.ts")
            || lowerPath.endsWith("next.config.js");
    }

    private boolean looksLikeBackendPath(String lowerPath) {
        return lowerPath.startsWith("backend/")
            || lowerPath.startsWith("server/")
            || lowerPath.startsWith("api/")
            || lowerPath.startsWith("services/api/")
            || lowerPath.startsWith("services/server/")
            || lowerPath.startsWith("services/worker/")
            || lowerPath.contains("/src/main/")
            || lowerPath.contains("/controller/")
            || lowerPath.contains("/service/")
            || lowerPath.endsWith("pom.xml")
            || lowerPath.endsWith("build.gradle")
            || lowerPath.endsWith("build.gradle.kts")
            || lowerPath.endsWith("pyproject.toml")
            || lowerPath.endsWith("requirements.txt")
            || lowerPath.endsWith("go.mod")
            || lowerPath.endsWith("main.py");
    }

    private boolean isTestPath(String lowerPath) {
        return lowerPath.startsWith("test/")
            || lowerPath.startsWith("tests/")
            || lowerPath.startsWith("spec/")
            || lowerPath.contains("/test/")
            || lowerPath.contains("/tests/")
            || lowerPath.contains("/spec/")
            || lowerPath.contains("/__tests__/")
            || lowerPath.contains(".test.")
            || lowerPath.contains(".spec.")
            || lowerPath.endsWith("_test.py")
            || lowerPath.endsWith("test_main.py");
    }

    private boolean isSourceCodePath(String lowerPath) {
        return lowerPath.contains("/src/")
            || lowerPath.startsWith("src/")
            || lowerPath.contains("/app/")
            || lowerPath.endsWith(".java")
            || lowerPath.endsWith(".kt")
            || lowerPath.endsWith(".ts")
            || lowerPath.endsWith(".tsx")
            || lowerPath.endsWith(".js")
            || lowerPath.endsWith(".jsx")
            || lowerPath.endsWith(".vue")
            || lowerPath.endsWith(".py")
            || lowerPath.endsWith(".go")
            || lowerPath.endsWith(".rs")
            || lowerPath.endsWith(".php")
            || lowerPath.endsWith(".cs")
            || lowerPath.endsWith(".rb");
    }

    private boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();
        return (lower.contains(".env") && !lower.endsWith(".env.example"))
            || lower.endsWith(".pem")
            || lower.endsWith(".key")
            || lower.contains("secret");
    }

    private String extractIndexedFileContent(String materialContent, String path) {
        String marker = "### " + path + "\n";
        int start = materialContent.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int contentStart = start + marker.length();
        int nextSection = materialContent.indexOf("\n### ", contentStart);
        String content = nextSection < 0
            ? materialContent.substring(contentStart)
            : materialContent.substring(contentStart, nextSection);
        return truncate(content.trim(), MAX_FILE_SNIPPET_CHARS);
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
