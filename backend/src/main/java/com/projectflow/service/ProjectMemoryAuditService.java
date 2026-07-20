package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ProjectMemoryReadAudit;
import com.projectflow.repository.ProjectMemoryReadAuditRepository;

@Service
public class ProjectMemoryAuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectMemoryAuditService.class);
    private final ProjectMemoryReadAuditRepository repository;

    public ProjectMemoryAuditService(ProjectMemoryReadAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        UUID userId, UUID projectId, String operation, int resultCount, long latencyMs,
        String status, String caller, String query, String entityTypes, String filters
    ) {
        try {
            String normalized = normalize(query);
            repository.save(new ProjectMemoryReadAudit(
                userId, projectId, operation, resultCount, latencyMs, status,
                hash(normalize(caller)), normalized.length(), hash(normalized), entityTypes, filters
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn("Project Memory read audit could not be persisted: operation={}", operation);
        }
    }

    public static String queryHash(String query) {
        return hash(normalize(query));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String hash(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
