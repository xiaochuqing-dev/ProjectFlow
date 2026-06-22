package com.projectflow.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.DevLogDtos.DevLogRequest;
import com.projectflow.dto.DevLogDtos.DevLogResponse;
import com.projectflow.dto.MarkdownImportDtos.ImportRecordResponse;
import com.projectflow.dto.MarkdownImportDtos.MarkdownConfirmRequest;
import com.projectflow.dto.MarkdownImportDtos.MarkdownPreviewRequest;
import com.projectflow.dto.MarkdownImportDtos.MarkdownPreviewResponse;
import com.projectflow.entity.DevLogCategory;
import com.projectflow.entity.ImportRecord;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ImportRecordRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class MarkdownImportService {
    private static final Map<String, String> SECTION_ALIASES = Map.ofEntries(
        Map.entry("completed", "已完成"),
        Map.entry("完成", "已完成"),
        Map.entry("今日完成", "已完成"),
        Map.entry("bugs fixed", "修复问题"),
        Map.entry("bug fixes", "修复问题"),
        Map.entry("缺陷修复", "修复问题"),
        Map.entry("问题修复", "修复问题"),
        Map.entry("decisions", "技术决策"),
        Map.entry("技术决策", "技术决策"),
        Map.entry("取舍", "技术决策"),
        Map.entry("problems", "风险阻塞"),
        Map.entry("阻塞", "风险阻塞"),
        Map.entry("问题", "风险阻塞"),
        Map.entry("next steps", "下一步"),
        Map.entry("下一步", "下一步"),
        Map.entry("计划", "下一步"),
        Map.entry("reflection", "复盘思考"),
        Map.entry("复盘", "复盘思考"),
        Map.entry("反思", "复盘思考")
    );

    private final ProjectRepository projectRepository;
    private final ImportRecordRepository importRecordRepository;
    private final DevLogService devLogService;

    public MarkdownImportService(ProjectRepository projectRepository, ImportRecordRepository importRecordRepository, DevLogService devLogService) {
        this.projectRepository = projectRepository;
        this.importRecordRepository = importRecordRepository;
        this.devLogService = devLogService;
    }

    @Transactional(readOnly = true)
    public MarkdownPreviewResponse preview(UUID userId, MarkdownPreviewRequest request) {
        findOwnedProject(userId, request.projectId());
        return parse(request.markdown());
    }

    @Transactional
    public DevLogResponse confirm(UUID userId, MarkdownConfirmRequest request) {
        ProjectSpace project = findOwnedProject(userId, request.projectId());
        MarkdownPreviewResponse preview = parse(request.markdown());
        DevLogResponse devLog = devLogService.create(userId, project.getId(), new DevLogRequest(
            request.taskId(),
            preview.title(),
            preview.content(),
            preview.category(),
            preview.logDate(),
            preview.minutesSpent(),
            preview.blocked(),
            preview.tags()
        ));
        importRecordRepository.save(new ImportRecord(
            project.getId(),
            devLog.id(),
            preview.title(),
            preview.frontMatter().getOrDefault("source", "markdown"),
            request.markdown(),
            preview.warnings()
        ));
        return devLog;
    }

    @Transactional(readOnly = true)
    public List<ImportRecordResponse> list(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return importRecordRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private MarkdownPreviewResponse parse(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").trim();
        Map<String, String> frontMatter = parseFrontMatter(normalized);
        String body = stripFrontMatter(normalized);
        Map<String, List<String>> sections = parseSections(body);
        List<String> warnings = new ArrayList<>();

        String title = firstNonBlank(frontMatter.get("title"), findFirstHeading(body), "导入开发日志");
        LocalDate logDate = parseDate(frontMatter.get("date"), warnings);
        DevLogCategory category = parseCategory(firstNonBlank(frontMatter.get("category"), frontMatter.get("type"), ""), warnings);
        Integer minutesSpent = parseMinutes(firstNonBlank(frontMatter.get("minutes"), frontMatter.get("minutesSpent"), frontMatter.get("time"), ""), warnings);
        List<String> tags = parseTags(frontMatter.get("tags"));
        boolean blocked = Boolean.parseBoolean(frontMatter.getOrDefault("blocked", "false")) || sections.containsKey("风险阻塞");
        String content = buildContent(body, sections);

        if (sections.isEmpty()) {
            warnings.add("未识别到结构化小节，已按全文导入。");
        }
        if (content.isBlank()) {
            warnings.add("正文为空，请确认 Markdown 内容。");
        }

        return new MarkdownPreviewResponse(frontMatter, title, content, category, logDate, minutesSpent, blocked, tags, warnings);
    }

    private Map<String, String> parseFrontMatter(String markdown) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!markdown.startsWith("---\n")) {
            return values;
        }
        int end = markdown.indexOf("\n---", 4);
        if (end < 0) {
            return values;
        }
        String[] lines = markdown.substring(4, end).split("\n");
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim().replace("\"", ""));
        }
        return values;
    }

    private String stripFrontMatter(String markdown) {
        if (!markdown.startsWith("---\n")) {
            return markdown;
        }
        int end = markdown.indexOf("\n---", 4);
        if (end < 0) {
            return markdown;
        }
        return markdown.substring(end + 4).trim();
    }

    private Map<String, List<String>> parseSections(String body) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = "";
        for (String line : body.split("\n")) {
            if (line.startsWith("##")) {
                String heading = line.replaceFirst("^#+", "").trim();
                current = SECTION_ALIASES.getOrDefault(heading.toLowerCase(Locale.ROOT), heading);
                sections.putIfAbsent(current, new ArrayList<>());
            } else if (!current.isBlank()) {
                String cleaned = line.replaceFirst("^[-*]\\s+", "").trim();
                if (!cleaned.isBlank()) {
                    sections.get(current).add(cleaned);
                }
            }
        }
        return sections;
    }

    private String buildContent(String body, Map<String, List<String>> sections) {
        if (sections.isEmpty()) {
            return body;
        }
        StringBuilder content = new StringBuilder();
        sections.forEach((heading, items) -> {
            if (!items.isEmpty()) {
                content.append("## ").append(heading).append('\n');
                items.forEach(item -> content.append("- ").append(item).append('\n'));
                content.append('\n');
            }
        });
        return content.toString().trim();
    }

    private String findFirstHeading(String body) {
        for (String line : body.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return "";
    }

    private LocalDate parseDate(String value, List<String> warnings) {
        if (value == null || value.isBlank()) {
            warnings.add("未提供日期，已使用今天。");
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            warnings.add("日期格式无法识别，已使用今天。");
            return LocalDate.now();
        }
    }

    private DevLogCategory parseCategory(String value, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return DevLogCategory.FEATURE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        if (normalized.contains("BUG")) {
            return DevLogCategory.BUGFIX;
        }
        if (normalized.contains("REFACTOR")) {
            return DevLogCategory.REFACTOR;
        }
        if (normalized.contains("RESEARCH")) {
            return DevLogCategory.RESEARCH;
        }
        if (normalized.contains("REVIEW")) {
            return DevLogCategory.REVIEW;
        }
        if (normalized.contains("DEPLOY")) {
            return DevLogCategory.DEPLOYMENT;
        }
        try {
            return DevLogCategory.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            warnings.add("日志类型无法识别，已按功能开发导入。");
            return DevLogCategory.FEATURE;
        }
    }

    private Integer parseMinutes(String value, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return 60;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            warnings.add("耗时无法识别，已使用 60 分钟。");
            return 60;
        }
    }

    private List<String> parseTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of("imported");
        }
        return List.of(value.split(","))
            .stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private ImportRecordResponse toResponse(ImportRecord record) {
        return new ImportRecordResponse(
            record.getId(),
            record.getProjectId(),
            record.getDevLogId(),
            record.getTitle(),
            record.getSource(),
            record.getWarnings(),
            record.getCreatedAt()
        );
    }
}
