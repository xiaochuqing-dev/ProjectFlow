package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.V33WorkflowDtos.AgentBridgeHealthResponse;
import com.projectflow.entity.ProjectMemory;
import com.projectflow.repository.ProjectMemoryRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class AgentBridgeHealthService {
    private static final String ENTRY_START = "<!-- PROJECTFLOW ENTRY START -->";
    private final ProjectRepository projectRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final LocalProjectPathGuard localProjectPathGuard;

    public AgentBridgeHealthService(
        ProjectRepository projectRepository,
        ProjectMemoryRepository memoryRepository,
        LocalProjectPathGuard localProjectPathGuard
    ) {
        this.projectRepository = projectRepository;
        this.memoryRepository = memoryRepository;
        this.localProjectPathGuard = localProjectPathGuard;
    }

    @Transactional(readOnly = true)
    public AgentBridgeHealthResponse health(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
        ProjectMemory memory = memoryRepository.findByProjectId(projectId)
            .orElseThrow(() -> new AppException("PROJECT_PATH_REQUIRED", "Bind a local project path before checking Agent bridge health", HttpStatus.BAD_REQUEST));
        Path root = localProjectPathGuard.requireGitProjectDirectory(memory.getLocalProjectPath()).path();
        Path protocol = root.resolve(".projectflow/AGENT_PROTOCOL.md");
        Path results = root.resolve(".projectflow/agent-results");
        Path agents = root.resolve("AGENTS.md");
        List<String> warnings = new ArrayList<>();
        boolean protocolExists = Files.isRegularFile(protocol);
        boolean resultsExist = Files.isDirectory(results);
        boolean agentsExists = Files.isRegularFile(agents);
        String protocolContent = read(protocol);
        String agentsContent = read(agents);
        boolean entryPresent = agentsContent.contains(ENTRY_START);
        if (!protocolExists) warnings.add("Agent 写回协议缺失，请重新初始化 ProjectFlow 入口规则。");
        if (!resultsExist) warnings.add("Agent 写回目录缺失，请重新初始化协议。");
        if (!entryPresent) warnings.add("AGENTS.md 中缺少 ProjectFlow 入口规则。");
        List<String> detected = List.of("CLAUDE.md", "GEMINI.md", ".cursor/rules", ".github/copilot-instructions.md").stream()
            .filter(relative -> Files.exists(root.resolve(relative)))
            .toList();
        return new AgentBridgeHealthResponse(
            Files.isDirectory(root), Files.isDirectory(root.resolve(".git")), protocolExists, resultsExist,
            agentsExists, entryPresent, protocolContent.contains("Protocol-Version: 3.3") ? "3.3" : "unknown", detected, warnings
        );
    }

    private String read(Path path) {
        if (!Files.isRegularFile(path)) return "";
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }
}
