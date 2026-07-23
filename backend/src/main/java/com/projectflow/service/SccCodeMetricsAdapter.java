package com.projectflow.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Optional adapter for the MIT-licensed scc executable. Project understanding
 * remains correct without it; when present, scc supplies mature language/LOC metrics.
 */
@Component
public class SccCodeMetricsAdapter {
    private final LocalCommandExecutor commandExecutor;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Boolean> available = new AtomicReference<>();

    public SccCodeMetricsAdapter(LocalCommandExecutor commandExecutor, ObjectMapper objectMapper) {
        this.commandExecutor = commandExecutor;
        this.objectMapper = objectMapper;
    }

    public CodeMetrics inspect(Path root) {
        if (!isAvailable(root)) {
            return CodeMetrics.unavailable();
        }
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(
            root,
            java.util.List.of("scc", "--format", "json", "--no-cocomo", "."),
            Duration.ofSeconds(45)
        );
        if (result.timedOut() || result.exitCode() != 0 || result.output().isBlank()) {
            return CodeMetrics.unavailable();
        }
        try {
            JsonNode rootNode = objectMapper.readTree(result.output());
            if (!rootNode.isArray()) {
                return CodeMetrics.unavailable();
            }
            long codeLines = 0;
            Map<String, Long> languages = new LinkedHashMap<>();
            for (JsonNode item : rootNode) {
                String language = item.path("Name").asText("").trim();
                long code = Math.max(0, item.path("Code").asLong(0));
                if (!language.isBlank() && code > 0) {
                    languages.merge(language, code, Long::sum);
                    codeLines += code;
                }
            }
            return languages.isEmpty()
                ? CodeMetrics.unavailable()
                : new CodeMetrics(true, codeLines, Map.copyOf(languages));
        } catch (Exception ignored) {
            return CodeMetrics.unavailable();
        }
    }

    private boolean isAvailable(Path root) {
        Boolean known = available.get();
        if (known != null) {
            return known;
        }
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(
            root,
            java.util.List.of("scc", "--version"),
            Duration.ofSeconds(3)
        );
        boolean detected = !result.timedOut() && result.exitCode() == 0;
        available.compareAndSet(null, detected);
        return detected;
    }

    public record CodeMetrics(boolean available, long codeLines, Map<String, Long> languageLines) {
        static CodeMetrics unavailable() {
            return new CodeMetrics(false, 0, Map.of());
        }
    }
}
