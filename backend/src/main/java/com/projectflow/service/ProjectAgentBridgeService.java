package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.V2ProjectDtos.AgentBridgeRequest;
import com.projectflow.dto.V2ProjectDtos.AgentBridgeWriteResponse;
import com.projectflow.dto.V2ProjectDtos.AgentResultScanResponse;
import com.projectflow.dto.V2ProjectDtos.AgentTaskBriefResponse;
import com.projectflow.dto.V2ProjectDtos.AiSuggestionResponse;
import com.projectflow.dto.V2ProjectDtos.ProjectMaterialResponse;
import com.projectflow.entity.AiSuggestion;
import com.projectflow.entity.AiSuggestionStatus;
import com.projectflow.entity.AiSuggestionType;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.entity.ProjectMaterial;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.TaskItem;
import com.projectflow.repository.AiSuggestionRepository;
import com.projectflow.repository.ProjectMaterialRepository;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.TaskRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectAgentBridgeService {
    private static final String GLOBAL_RULE = "If the current project root contains `.projectflow/agent-protocol.md`, read it before work. After finishing development work, write a ProjectFlow Agent Result to `.projectflow/inbox/` or the task result file. Do not directly modify ProjectFlow task state.";
    private static final int MAX_AGENT_RESULT_CHARS = 200_000;

    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectMaterialRepository materialRepository;
    private final AiSuggestionRepository suggestionRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public ProjectAgentBridgeService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        ProjectMaterialRepository materialRepository,
        AiSuggestionRepository suggestionRepository,
        TaskRepository taskRepository,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.materialRepository = materialRepository;
        this.suggestionRepository = suggestionRepository;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentBridgeWriteResponse writeProtocol(UUID userId, UUID projectId, AgentBridgeRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        Path projectRoot = resolveProjectRoot(request.projectPath());
        ProjectMemory memory = rememberProjectPath(project, projectRoot);
        Path projectFlowDir = projectRoot.resolve(".projectflow");
        boolean alreadyLinked = Files.exists(projectFlowDir.resolve("agent-protocol.md"));
        List<Path> writtenFiles = new ArrayList<>();

        try {
            Files.createDirectories(projectFlowDir.resolve("context"));
            Files.createDirectories(projectFlowDir.resolve("tasks"));
            Files.createDirectories(projectFlowDir.resolve("inbox"));

            writeFile(projectFlowDir.resolve("agent-protocol.md"), protocolContent(), writtenFiles);
            writeFile(projectFlowDir.resolve("context/project-profile.md"), projectProfileContent(project, memory), writtenFiles);
            writeFile(projectFlowDir.resolve("context/requirements.md"), requirementsContent(request.requirements()), writtenFiles);
            writeFile(projectFlowDir.resolve("context/confirmed-decisions.md"), sectionOrFallback(memory == null ? "" : memory.getTechnicalDecisions(), "No confirmed decisions yet."), writtenFiles);
            writeFile(projectFlowDir.resolve("context/known-risks.md"), sectionOrFallback(memory == null ? "" : memory.getCurrentRisks(), "No known risks yet."), writtenFiles);
            writeFile(projectFlowDir.resolve("context/update-history.md"), updateHistoryContent(memory), writtenFiles);
        } catch (IOException exception) {
            throw new AppException("PROJECTFLOW_WRITE_FAILED", "ProjectFlow bridge files could not be written", HttpStatus.BAD_REQUEST);
        }

        return new AgentBridgeWriteResponse(
            projectFlowDir.toString(),
            writtenFiles.stream().map(path -> projectRoot.relativize(path).toString().replace("\\", "/")).toList(),
            GLOBAL_RULE,
            alreadyLinked
        );
    }

    @Transactional
    public AgentTaskBriefResponse writeTaskBrief(UUID userId, UUID projectId, UUID taskId, AgentBridgeRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        TaskItem task = findOwnedTask(project.getId(), taskId);
        Path projectRoot = resolveProjectRoot(request.projectPath());
        ProjectMemory memory = rememberProjectPath(project, projectRoot);
        Path taskDir = projectRoot.resolve(".projectflow").resolve("tasks").resolve(task.getId().toString());
        List<Path> writtenFiles = new ArrayList<>();

        try {
            Files.createDirectories(taskDir);
            Path briefPath = taskDir.resolve("brief.md");
            Path resultPath = taskDir.resolve("result.md");
            Path statusPath = taskDir.resolve("status.json");
            writeFile(briefPath, taskBriefContent(project, task, memory, request.requirements()), writtenFiles);
            writeFile(resultPath, taskResultTemplate(project, task), writtenFiles);
            writeFile(statusPath, taskStatusContent(task), writtenFiles);
            return new AgentTaskBriefResponse(
                task.getId(),
                responsePath(taskDir),
                responsePath(briefPath),
                responsePath(resultPath),
                responsePath(statusPath),
                writtenFiles.stream().map(path -> projectRoot.relativize(path).toString().replace("\\", "/")).toList()
            );
        } catch (IOException exception) {
            throw new AppException("PROJECTFLOW_TASK_BRIEF_WRITE_FAILED", "ProjectFlow task brief files could not be written", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public AgentResultScanResponse scanAgentResults(UUID userId, UUID projectId, AgentBridgeRequest request) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        Path projectRoot = resolveProjectRoot(request.projectPath());
        rememberProjectPath(project, projectRoot);
        Path projectFlowDir = projectRoot.resolve(".projectflow");
        if (!Files.isDirectory(projectFlowDir)) {
            throw new AppException("PROJECTFLOW_DIR_NOT_FOUND", ".projectflow directory was not found", HttpStatus.BAD_REQUEST);
        }

        List<Path> resultFiles = findResultFiles(projectFlowDir);
        List<ProjectMaterialResponse> materials = new ArrayList<>();
        List<AiSuggestionResponse> suggestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Path resultFile : resultFiles) {
            if (isProcessed(resultFile)) {
                continue;
            }
            String content = readResult(resultFile);
            if (content.isBlank()) {
                continue;
            }
            if (!isValidAgentResult(content)) {
                warnings.add(projectRoot.relativize(resultFile).toString().replace("\\", "/") + " is an invalid ProjectFlow Agent Result.");
                continue;
            }
            ProjectMaterial material = saveMaterial(project.getId(), projectRoot.relativize(resultFile).toString().replace("\\", "/"), content);
            AgentResult agentResult = parseAgentResult(content);
            List<AiSuggestion> createdSuggestions = generateSuggestions(project, material, agentResult);
            materials.add(toMaterialResponse(material));
            suggestions.addAll(createdSuggestions.stream().map(this::toSuggestionResponse).toList());
            markProcessed(resultFile);
        }

        return new AgentResultScanResponse(materials.size(), materials, suggestions, warnings);
    }

    private List<Path> findResultFiles(Path projectFlowDir) {
        List<Path> files = new ArrayList<>();
        Path inbox = projectFlowDir.resolve("inbox");
        if (Files.isDirectory(inbox)) {
            Path defaultInbox = inbox.resolve("agent-result.md");
            if (Files.isRegularFile(defaultInbox)) {
                files.add(defaultInbox);
            }
            try (Stream<Path> stream = Files.list(inbox)) {
                stream
                    .filter(path -> path.getFileName().toString().endsWith("-agent-result.md"))
                    .sorted()
                    .forEach(files::add);
            } catch (IOException exception) {
                throw new AppException("AGENT_RESULT_SCAN_FAILED", "Agent result inbox could not be scanned", HttpStatus.BAD_REQUEST);
            }
        }

        Path tasks = projectFlowDir.resolve("tasks");
        if (Files.isDirectory(tasks)) {
            try (Stream<Path> stream = Files.walk(tasks, 2)) {
                stream
                    .filter(path -> path.getFileName().toString().equals("result.md"))
                    .sorted()
                    .forEach(files::add);
            } catch (IOException exception) {
                throw new AppException("AGENT_RESULT_SCAN_FAILED", "Agent task results could not be scanned", HttpStatus.BAD_REQUEST);
            }
        }
        return files;
    }

    private List<AiSuggestion> generateSuggestions(ProjectSpace project, ProjectMaterial material, AgentResult result) {
        List<AiSuggestion> suggestions = new ArrayList<>();
        String summary = defaultText(result.section("Summary"), "Agent returned a ProjectFlow result.");
        String taskUpdates = result.section("Task Updates");
        String decisions = result.section("Decisions");
        String risks = result.section("Risks");
        String devLog = defaultText(result.section("Dev Log"), summary);
        TaskItem linkedTask = findLinkedTask(project.getId(), result.taskId());

        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.CREATE_DEV_LOG,
            "Import agent development record",
            "Agent result was detected in .projectflow and should be reviewed before it becomes project history.",
            withTaskPayload(linkedTask, Map.of(
                "title", "Agent update: " + firstLine(summary),
                "content", devLog + "\n\nChanged files:\n" + defaultText(result.section("Changed Files"), "- Not reported"),
                "taskRef", result.taskId(),
                "sourceFile", material.getFileName()
            ))
        ));

        suggestions.add(saveSuggestion(
            project.getId(),
            material.getId(),
            AiSuggestionType.UPDATE_PROJECT_MEMORY,
            "Review project state after agent work",
            "Agent work may change the project profile, current stage, or next steps. Confirm before writing it to Project Memory.",
            Map.of(
                "positioning", project.getDescription(),
                "currentStage", "Agent result review",
                "completedCapabilities", summary,
                "nextStepSuggestions", defaultText(taskUpdates, "Review agent result and decide the next task.")
            )
        ));

        if (!taskUpdates.isBlank()) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.CREATE_TASK,
                inferTaskTitle(taskUpdates),
                result.taskId().isBlank()
                    ? "Agent result was not bound to a ProjectFlow task. Confirm whether to create or link a task."
                    : "Agent result contains task updates. Confirm whether any follow-up task should enter the task board.",
                withTaskPayload(linkedTask, Map.of(
                    "title", inferTaskTitle(taskUpdates),
                    "description", taskUpdates,
                    "taskRef", result.taskId()
                ))
            ));
        }

        if (!decisions.isBlank()) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.RECORD_TECHNICAL_DECISION,
                "Record agent-reported decision",
                "Agent result contains decisions. Confirm before treating them as project facts.",
                withTaskPayload(linkedTask, Map.of("decision", decisions, "taskRef", result.taskId()))
            ));
        }

        if (!risks.isBlank()) {
            suggestions.add(saveSuggestion(
                project.getId(),
                material.getId(),
                AiSuggestionType.RECORD_RISK,
                "Track agent-reported risk",
                "Agent result contains risks or unresolved issues. Confirm before adding them to project risk tracking.",
                withTaskPayload(linkedTask, Map.of("risk", risks, "taskRef", result.taskId()))
            ));
        }

        return suggestions;
    }

    private AgentResult parseAgentResult(String content) {
        String taskId = headerValue(content, "TaskId");
        Map<String, String> sections = new LinkedHashMap<>();
        String currentHeading = "";
        StringBuilder currentBody = new StringBuilder();

        for (String line : content.replace("\r", "").split("\n")) {
            if (line.startsWith("## ")) {
                if (!currentHeading.isBlank()) {
                    sections.put(currentHeading, currentBody.toString().trim());
                }
                currentHeading = line.substring(3).trim();
                currentBody = new StringBuilder();
            } else if (!currentHeading.isBlank()) {
                currentBody.append(line).append("\n");
            }
        }
        if (!currentHeading.isBlank()) {
            sections.put(currentHeading, currentBody.toString().trim());
        }
        return new AgentResult(taskId, sections);
    }

    private boolean isValidAgentResult(String content) {
        return content.lines().anyMatch(line -> line.trim().equals("# ProjectFlow Agent Result"));
    }

    private String headerValue(String content, String header) {
        String prefix = header + ":";
        return content.lines()
            .filter(line -> line.startsWith(prefix))
            .map(line -> line.substring(prefix.length()).trim())
            .findFirst()
            .orElse("");
    }

    private ProjectMaterial saveMaterial(UUID projectId, String fileName, String content) {
        ProjectMaterial material = new ProjectMaterial(projectId);
        material.update(MaterialSourceType.AGENT_SUMMARY, fileName, truncate(content.trim(), MAX_AGENT_RESULT_CHARS), firstLine(content));
        return materialRepository.save(material);
    }

    private AiSuggestion saveSuggestion(UUID projectId, UUID materialId, AiSuggestionType type, String title, String reason, Map<String, Object> payload) {
        AiSuggestion suggestion = new AiSuggestion(projectId, materialId);
        suggestion.update(type, title, reason, writePayload(payload));
        return suggestionRepository.save(suggestion);
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private TaskItem findOwnedTask(UUID projectId, UUID taskId) {
        TaskItem task = taskRepository.findById(taskId)
            .orElseThrow(() -> new AppException("TASK_NOT_FOUND", "Task was not found", HttpStatus.NOT_FOUND));
        if (!task.getProjectId().equals(projectId)) {
            throw new AppException("TASK_NOT_FOUND", "Task was not found", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private TaskItem findLinkedTask(UUID projectId, String taskRef) {
        if (taskRef == null || taskRef.isBlank()) {
            return null;
        }
        try {
            UUID taskId = UUID.fromString(taskRef);
            TaskItem task = taskRepository.findById(taskId).orElse(null);
            return task != null && task.getProjectId().equals(projectId) ? task : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Path resolveProjectRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new AppException("PROJECT_PATH_REQUIRED", "Project folder path is required", HttpStatus.BAD_REQUEST);
        }
        Path path = Path.of(projectPath).toAbsolutePath().normalize();
        if (isTooBroadPath(path)) {
            throw new AppException("PROJECT_PATH_TOO_BROAD", "Project folder path is too broad", HttpStatus.BAD_REQUEST);
        }
        if (!Files.isDirectory(path)) {
            throw new AppException("PROJECT_PATH_NOT_FOUND", "Project folder path was not found", HttpStatus.BAD_REQUEST);
        }
        return path;
    }

    private ProjectMemory rememberProjectPath(ProjectSpace project, Path projectRoot) {
        ProjectMemory memory = memoryRepository.findByProjectId(project.getId()).orElseGet(() -> initialMemory(project));
        memory.rememberLocalProjectPath(projectRoot.toString());
        return memoryRepository.save(memory);
    }

    private ProjectMemory initialMemory(ProjectSpace project) {
        ProjectMemory memory = new ProjectMemory(project.getId());
        memory.update(
            defaultText(project.getDescription(), project.getName() + " 的长期项目档案"),
            project.getStatus().name(),
            "暂无已确认能力。",
            "暂无进行中能力。",
            "暂无已确认风险。",
            "暂无技术决策记录。",
            "暂无开发者收获。",
            "暂无可展示成果。",
            "先导入项目材料，生成并确认 AI 建议。"
        );
        return memory;
    }

    private boolean isTooBroadPath(Path path) {
        if (path.getParent() == null || path.equals(path.getRoot())) {
            return true;
        }
        String lowerPath = path.toString().toLowerCase();
        return lowerPath.endsWith("\\windows")
            || lowerPath.endsWith("/windows")
            || lowerPath.endsWith("\\program files")
            || lowerPath.endsWith("/program files")
            || lowerPath.endsWith("\\program files (x86)")
            || lowerPath.endsWith("/program files (x86)");
    }

    private void writeFile(Path path, String content, List<Path> writtenFiles) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        writtenFiles.add(path);
    }

    private String responsePath(Path path) {
        return path.toString().replace("\\", "/");
    }

    private String readResult(Path resultFile) {
        try {
            return Files.readString(resultFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AppException("AGENT_RESULT_READ_FAILED", "Agent result could not be read", HttpStatus.BAD_REQUEST);
        }
    }

    private void markProcessed(Path resultFile) {
        try {
            Files.writeString(resultFile.resolveSibling(resultFile.getFileName() + ".processed"), Instant.now().toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AppException("AGENT_RESULT_MARK_FAILED", "Agent result could not be marked as processed", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isProcessed(Path resultFile) {
        return Files.exists(resultFile.resolveSibling(resultFile.getFileName() + ".processed"));
    }

    private String protocolContent() {
        return """
            # ProjectFlow Agent Protocol

            ## Before Work
            - Read `.projectflow/context/project-profile.md` if it exists.
            - Read `.projectflow/context/requirements.md` if it exists.
            - Read `.projectflow/context/confirmed-decisions.md` if it exists.
            - Read `.projectflow/context/known-risks.md` if it exists.
            - If a task brief exists, follow `.projectflow/tasks/<task-id>/brief.md`.

            ## If User Starts Work Directly In Agent
            - It is allowed to work without a ProjectFlow task brief.
            - After finishing, create a result file under `.projectflow/inbox/`.
            - Use filename format: `YYYYMMDD-HHMM-agent-result.md`.

            ## Result Rules
            - Do not directly modify ProjectFlow task state files as completed.
            - Do not invent product decisions as confirmed facts.
            - Record changed files, summary, risks, decisions, and suggested task updates.
            - ProjectFlow will import the result and ask the user to confirm updates.

            ## Required Result Format
            # ProjectFlow Agent Result

            ProjectId: <project-id-or-name>
            TaskId: <optional-task-ref>
            Status: ready_for_review

            ## Summary
            <what was done>

            ## Changed Files
            - <path>

            ## Task Updates
            - <task update or suggested follow-up>

            ## Decisions
            - <decision that needs confirmation>

            ## Risks
            - <risk or unresolved issue>

            ## Dev Log
            <short development process record>
            """;
    }

    private String projectProfileContent(ProjectSpace project, ProjectMemory memory) {
        return """
            # Project Profile

            ProjectId: %s
            Name: %s
            Status: %s
            TechStack: %s
            LocalProjectPath: %s

            ## Description
            %s

            ## Current Stage
            %s

            ## Confirmed Capabilities
            %s
            """.formatted(
            project.getId(),
            project.getName(),
            project.getStatus(),
            String.join(", ", project.getTechStack()),
            memory == null ? "Not linked yet." : defaultText(memory.getLocalProjectPath(), "Not linked yet."),
            defaultText(project.getDescription(), "No description yet."),
            memory == null ? "Not confirmed yet." : defaultText(memory.getCurrentStage(), "Not confirmed yet."),
            memory == null ? "No confirmed capabilities yet." : defaultText(memory.getCompletedCapabilities(), "No confirmed capabilities yet.")
        );
    }

    private String requirementsContent(String requirements) {
        return """
            # Current Requirements

            %s
            """.formatted(defaultText(requirements, "No current requirement note. User may describe the next change directly in the agent."));
    }

    private String updateHistoryContent(ProjectMemory memory) {
        if (memory == null) {
            return "# Update History\n\nNo confirmed ProjectFlow updates yet.\n";
        }
        return """
            # Update History

            Last exported: %s

            ## In Progress
            %s

            ## Developer Learnings
            %s

            ## Next Steps
            %s
            """.formatted(
            LocalDate.now().format(DateTimeFormatter.ISO_DATE),
            defaultText(memory.getInProgressCapabilities(), "No in-progress capabilities recorded."),
            defaultText(memory.getDeveloperLearnings(), "No developer learnings recorded."),
            defaultText(memory.getNextStepSuggestions(), "No next steps recorded.")
        );
    }

    private String taskBriefContent(ProjectSpace project, TaskItem task, ProjectMemory memory, String requirements) {
        return """
            # ProjectFlow Agent Brief

            ProjectId: %s
            TaskId: %s
            Task: %s

            ## User Intent
            %s

            ## Project Context Files
            - .projectflow/agent-protocol.md
            - .projectflow/context/project-profile.md
            - .projectflow/context/requirements.md
            - .projectflow/context/confirmed-decisions.md
            - .projectflow/context/known-risks.md

            ## Task Goal
            %s

            ## Current Project Stage
            %s

            ## Acceptance Criteria
            - Keep changes focused on this task.
            - Preserve existing project direction and confirmed decisions.
            - Do not update ProjectFlow task state directly.
            - After finishing, write the result file listed below.

            ## Result File
            `.projectflow/tasks/%s/result.md`

            ## Required Result Format
            Follow `.projectflow/agent-protocol.md`.
            """.formatted(
            project.getId(),
            task.getId(),
            task.getTitle(),
            defaultText(requirements, "Developer chose this optional ProjectFlow task brief path."),
            defaultText(task.getDescription(), task.getTitle()),
            memory == null ? "Not confirmed yet." : defaultText(memory.getCurrentStage(), "Not confirmed yet."),
            task.getId()
        );
    }

    private String taskResultTemplate(ProjectSpace project, TaskItem task) {
        return """
            # ProjectFlow Agent Result

            ProjectId: %s
            TaskId: %s
            Status: ready_for_review

            ## Summary

            ## Changed Files

            ## Task Updates

            ## Decisions

            ## Risks

            ## Dev Log
            """.formatted(project.getId(), task.getId());
    }

    private String taskStatusContent(TaskItem task) {
        return """
            {
              "taskId": "%s",
              "status": "waiting_for_agent",
              "taskTitle": "%s",
              "updatedAt": "%s"
            }
            """.formatted(task.getId(), escapeJson(task.getTitle()), Instant.now());
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sectionOrFallback(String content, String fallback) {
        return defaultText(content, fallback) + "\n";
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new AppException("INVALID_AI_PAYLOAD", "AI suggestion payload could not be serialized", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException exception) {
            return Map.of("raw", payload);
        }
    }

    private Map<String, Object> withTaskPayload(TaskItem task, Map<String, Object> payload) {
        if (task == null) {
            return payload;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        enriched.put("taskId", task.getId().toString());
        enriched.put("taskTitle", task.getTitle());
        return enriched;
    }

    private ProjectMaterialResponse toMaterialResponse(ProjectMaterial material) {
        return new ProjectMaterialResponse(
            material.getId(),
            material.getProjectId(),
            material.getSourceType(),
            material.getFileName(),
            material.getContent(),
            material.getNormalizedSummary(),
            material.getCreatedAt(),
            material.getUpdatedAt()
        );
    }

    private AiSuggestionResponse toSuggestionResponse(AiSuggestion suggestion) {
        return new AiSuggestionResponse(
            suggestion.getId(),
            suggestion.getProjectId(),
            suggestion.getMaterialId(),
            suggestion.getType(),
            suggestion.getStatus(),
            suggestion.getTitle(),
            suggestion.getReason(),
            readPayload(suggestion.getPayload()),
            suggestion.getCreatedAt(),
            suggestion.getUpdatedAt(),
            suggestion.getResolvedAt()
        );
    }

    private String inferTaskTitle(String taskUpdates) {
        return taskUpdates.lines()
            .map(line -> line.replaceFirst("^-\\s*", "").trim())
            .filter(line -> line.startsWith("New:"))
            .map(line -> line.substring("New:".length()).trim())
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse("Review agent-reported task updates");
    }

    private String firstLine(String content) {
        return truncate(content.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("Agent result"), 180);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record AgentResult(
        String taskId,
        Map<String, String> sections
    ) {
        String section(String name) {
            return sections.getOrDefault(name, "");
        }
    }
}
