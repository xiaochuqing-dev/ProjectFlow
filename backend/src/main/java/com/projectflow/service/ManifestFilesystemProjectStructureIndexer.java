package com.projectflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.ProjectStructureIndexResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureCoverage;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureDelta;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEntryPoint;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureEvidence;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureFileNode;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureModuleNode;
import com.projectflow.dto.ProjectUnderstandingDtos.StructureRelation;
import com.projectflow.service.RepositoryIntakeService.ModuleSignal;
import com.projectflow.service.RepositoryIntakeService.ScanResult;
import com.projectflow.service.RepositoryIntakeService.ScannedFile;

@Service
public class ManifestFilesystemProjectStructureIndexer implements ProjectStructureIndexer {
    static final String INDEX_VERSION = "manifest-fs-v1";

    @Override
    public ProjectStructureIndexResponse build(ScanResult scan) {
        List<ScannedFile> orderedFiles = scan.fileDetails().stream()
            .sorted(Comparator.comparing(ScannedFile::path))
            .toList();
        List<StructureFileNode> files = orderedFiles.stream()
            .map(file -> new StructureFileNode(
                file.path(), file.language(), file.lines(), file.bytes(), file.generated(), file.binary()
            ))
            .toList();

        Map<String, StructureEvidence> evidenceByPath = new LinkedHashMap<>();
        evidenceByPath.put("", new StructureEvidence(
            "intake:scan",
            "REPOSITORY_INTAKE",
            "",
            "有界目录扫描、语言统计与仓库分类"
        ));
        if (scan.intake().git().available()) {
            evidenceByPath.put("#git", new StructureEvidence(
                "git:head",
                "GIT_STATE",
                "",
                "Git 当前版本、分支、提交数量与工作区状态"
            ));
        }
        orderedFiles.stream()
            .filter(file -> file.keyFile() || file.manifest())
            .limit(500)
            .forEach(file -> putEvidence(
                evidenceByPath,
                file.path(),
                evidence(file.path(), file.manifest() ? "MANIFEST" : "KEY_FILE")
            ));
        scan.engineeringSignals().values().stream()
            .flatMap(List::stream)
            .limit(300)
            .forEach(path -> putEvidence(evidenceByPath, path, evidence(path, "ENGINEERING_SIGNAL")));
        scan.entryCandidates().stream()
            .limit(200)
            .forEach(path -> putEvidence(evidenceByPath, path, evidence(path, "ENTRY_POINT")));

        List<StructureModuleNode> modules = new ArrayList<>();
        List<StructureRelation> relations = new ArrayList<>();
        scan.modules().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String moduleId = "module:" + stableId(entry.getKey());
                ModuleSignal signal = entry.getValue();
                List<String> refs = signal.evidencePaths().stream()
                    .limit(5)
                    .map(path -> {
                        putEvidence(evidenceByPath, path, evidence(path, "MODULE_SAMPLE"));
                        return evidenceByPath.getOrDefault(path, evidenceByPath.get("")).id();
                    })
                    .toList();
                modules.add(new StructureModuleNode(
                    moduleId,
                    entry.getKey(),
                    signal.fileCount(),
                    signal.sourceFileCount(),
                    signal.estimatedLoc(),
                    signal.languages(),
                    refs
                ));
                relations.add(new StructureRelation(
                    "project:root",
                    moduleId,
                    "CONTAINS",
                    refs
                ));
            });

        List<StructureEntryPoint> entryPoints = scan.entryCandidates().stream()
            .sorted()
            .map(path -> new StructureEntryPoint(
                path,
                entryKind(path),
                "MEDIUM",
                evidenceByPath.getOrDefault(path, evidenceByPath.get("")).id()
            ))
            .toList();

        double manifestCoverage = scan.intake().manifestFiles().isEmpty()
            ? scan.intake().sourceFileCount() == 0 ? 1.0 : 0.35
            : 1.0;
        double languageCoverage = "SCC".equals(scan.intake().metricsSource()) ? 1.0 : 0.75;
        double overall = round(
            scan.intake().supportedStructureCoverage() * 0.45
                + languageCoverage * 0.20
                + manifestCoverage * 0.20
                + 0.0 * 0.15
        );
        StructureCoverage coverage = new StructureCoverage(
            scan.intake().supportedStructureCoverage(),
            languageCoverage,
            manifestCoverage,
            0.0,
            "目录包含关系；未声明调用图、继承图或符号引用图",
            overall
        );

        Set<String> provenance = new LinkedHashSet<>();
        provenance.add("本地文件系统元数据");
        provenance.add("manifest/workspace 声明");
        provenance.add("扩展名语言统计");
        if ("SCC".equals(scan.intake().metricsSource())) provenance.add("scc 语言与代码行统计");
        if (scan.intake().git().available()) provenance.add("Git 当前版本与工作区状态");

        return new ProjectStructureIndexResponse(
            INDEX_VERSION,
            "MANIFEST_FILESYSTEM",
            scan.intake().sourceRevision(),
            scan.intake().contentHash(),
            false,
            scan.intake().fileCount(),
            files,
            scan.fileDetailsTruncated(),
            List.copyOf(modules),
            List.copyOf(relations),
            entryPoints,
            scan.intake().manifestFiles(),
            scan.engineeringSignals(),
            List.copyOf(evidenceByPath.values()),
            coverage,
            List.copyOf(provenance),
            List.of(
                "未安装或未配置语义索引器，因此不提供可靠符号、调用、继承和引用关系",
                "不会把未扫描源码内容或 README 宣传语自动当作已验证能力"
            ),
            new StructureDelta(
                "INITIAL",
                scan.intake().fileCount(),
                0,
                0,
                0,
                !scan.intake().scanTruncated()
            ),
            Instant.now()
        );
    }

    private static StructureEvidence evidence(String path, String kind) {
        return new StructureEvidence("e:" + stableId(path), kind, path, summary(kind));
    }

    private static void putEvidence(
        Map<String, StructureEvidence> evidence,
        String key,
        StructureEvidence value
    ) {
        if (evidence.containsKey(key) || evidence.size() >= 700) return;
        evidence.put(key, value);
    }

    private static String summary(String kind) {
        return switch (kind) {
            case "MANIFEST" -> "构建或依赖清单";
            case "ENGINEERING_SIGNAL" -> "测试、质量、持续集成或部署信号";
            case "ENTRY_POINT" -> "基于文件名识别的候选入口";
            default -> "模块结构样本";
        };
    }

    private static String entryKind(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith("application.java") || lower.endsWith("main.java")
            || lower.endsWith("main.kt") || lower.endsWith("main.go") || lower.endsWith("main.rs")) {
            return "APPLICATION";
        }
        if (lower.endsWith("app.tsx") || lower.endsWith("app.jsx")) return "UI";
        return "MODULE";
    }

    private static String stableId(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes, 0, 8);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 1000.0) / 1000.0;
    }
}
