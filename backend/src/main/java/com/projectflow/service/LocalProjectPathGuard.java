package com.projectflow.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.projectflow.support.AppException;

@Service
public class LocalProjectPathGuard {
    public ValidatedProjectPath requireProjectDirectory(String projectPath) {
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
        return new ValidatedProjectPath(path);
    }

    public ValidatedProjectPath requireGitProjectDirectory(String projectPath) {
        ValidatedProjectPath validated = requireProjectDirectory(projectPath);
        if (!Files.isDirectory(validated.path().resolve(".git"))) {
            throw new AppException("PROJECT_GIT_REQUIRED", "Bound project path is not a Git repository", HttpStatus.BAD_REQUEST);
        }
        return validated;
    }

    private boolean isTooBroadPath(Path path) {
        if (path.getParent() == null || path.equals(path.getRoot())) {
            return true;
        }
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (path.equals(home)) {
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

    public record ValidatedProjectPath(Path path) {
    }
}
