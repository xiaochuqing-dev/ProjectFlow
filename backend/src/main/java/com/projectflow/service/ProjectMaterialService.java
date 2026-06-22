package com.projectflow.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.projectflow.dto.V2ProjectDtos.ProjectMaterialResponse;
import com.projectflow.entity.MaterialSourceType;
import com.projectflow.entity.ProjectMaterial;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.repository.ProjectMaterialRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.support.AppException;

@Service
public class ProjectMaterialService {
    private static final int MAX_MATERIAL_CHARS = 500_000;

    private final ProjectRepository projectRepository;
    private final ProjectMaterialRepository materialRepository;
    private final ProjectZipScanService projectZipScanService;

    public ProjectMaterialService(
        ProjectRepository projectRepository,
        ProjectMaterialRepository materialRepository,
        ProjectZipScanService projectZipScanService
    ) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.projectZipScanService = projectZipScanService;
    }

    @Transactional(readOnly = true)
    public List<ProjectMaterialResponse> listMaterials(UUID userId, UUID projectId) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return materialRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .map(this::toMaterialResponse)
            .toList();
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public ProjectMaterialResponse createTextMaterial(UUID userId, UUID projectId, MaterialSourceType sourceType, String content) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        return toMaterialResponse(saveMaterial(project.getId(), sourceType, null, content));
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public ProjectMaterialResponse createFileMaterial(UUID userId, UUID projectId, MaterialSourceType sourceType, MultipartFile file) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        String fileName = cleanFileName(file.getOriginalFilename());
        String content = readUploadedFile(fileName, file);
        MaterialSourceType detectedType = sourceType == null || sourceType == MaterialSourceType.OTHER
            ? detectFileSourceType(fileName)
            : sourceType;
        return toMaterialResponse(saveMaterial(project.getId(), detectedType, fileName, content));
    }

    @Transactional
    @Deprecated(since = "3.2", forRemoval = false)
    public ProjectMaterialResponse createZipMaterial(UUID userId, UUID projectId, MultipartFile file) {
        ProjectSpace project = findOwnedProject(userId, projectId);
        String content = projectZipScanService.scan(file).content();
        return toMaterialResponse(saveMaterial(project.getId(), MaterialSourceType.PROJECT_ZIP, file.getOriginalFilename(), content));
    }

    @Transactional(readOnly = true)
    public ProjectMaterialResponse materialDetail(UUID userId, UUID materialId) {
        return toMaterialResponse(findOwnedMaterial(userId, materialId));
    }

    ProjectMaterial saveMaterial(UUID projectId, MaterialSourceType sourceType, String fileName, String content) {
        String safeContent = content == null ? "" : content;
        String normalized = normalizeContent(safeContent);
        ProjectMaterial material = new ProjectMaterial(projectId);
        material.update(sourceType, cleanNullableFileName(fileName), truncate(safeContent.trim(), MAX_MATERIAL_CHARS), normalized);
        return materialRepository.save(material);
    }

    ProjectMaterial findOwnedMaterial(UUID userId, UUID materialId) {
        ProjectMaterial material = materialRepository.findById(materialId)
            .orElseThrow(() -> new AppException("PROJECT_MATERIAL_NOT_FOUND", "Project material was not found", HttpStatus.NOT_FOUND));
        findOwnedProject(userId, material.getProjectId());
        return material;
    }

    ProjectMaterialResponse toMaterialResponse(ProjectMaterial material) {
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

    String cleanFileName(String fileName) {
        return cleanNullableFileName(fileName) == null ? "uploaded-material" : cleanNullableFileName(fileName);
    }

    private ProjectSpace findOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND));
    }

    private String readUploadedFile(String fileName, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (fileName.endsWith(".docx")) {
                return extractDocxText(bytes);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AppException("MATERIAL_READ_FAILED", "Project material could not be read", HttpStatus.BAD_REQUEST);
        }
    }

    private String extractDocxText(byte[] bytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    return xml
                        .replaceAll("</w:p>", "\n")
                        .replaceAll("<[^>]+>", "")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .trim();
                }
            }
        }
        throw new AppException("DOCX_READ_FAILED", "DOCX content could not be extracted", HttpStatus.BAD_REQUEST);
    }

    private MaterialSourceType detectFileSourceType(String fileName) {
        if (fileName.endsWith(".docx")) {
            return MaterialSourceType.DOCX_FILE;
        }
        if (fileName.endsWith(".md")) {
            return MaterialSourceType.README_MARKDOWN;
        }
        if (fileName.endsWith(".json") || fileName.endsWith(".log")) {
            return MaterialSourceType.JSON_LOG;
        }
        return MaterialSourceType.TEXT_FILE;
    }

    private String normalizeContent(String content) {
        String trimmed = truncate(content.trim(), MAX_MATERIAL_CHARS);
        String sentence = firstSentence(trimmed);
        return sentence.isBlank() ? "已保存项目材料，等待 AI 解析。" : sentence;
    }

    private String firstSentence(String content) {
        String normalized = content.replace("\r", "\n").replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        int end = normalized.indexOf('。');
        if (end < 0) {
            end = normalized.indexOf('.');
        }
        if (end < 0) {
            end = Math.min(normalized.length(), 280);
        }
        return truncate(normalized.substring(0, Math.min(normalized.length(), end + 1)), 280);
    }

    private String cleanNullableFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String normalized = fileName.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
