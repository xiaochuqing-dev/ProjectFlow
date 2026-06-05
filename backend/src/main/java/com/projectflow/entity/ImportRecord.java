package com.projectflow.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projectflow.support.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_records")
public class ImportRecord {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "dev_log_id", nullable = false)
    private UUID devLogId;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 80)
    private String source;

    @Column(name = "raw_markdown", nullable = false, columnDefinition = "text")
    private String rawMarkdown;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> warnings = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImportRecord() {
    }

    public ImportRecord(UUID projectId, UUID devLogId, String title, String source, String rawMarkdown, List<String> warnings) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.devLogId = devLogId;
        this.title = title;
        this.source = source;
        this.rawMarkdown = rawMarkdown;
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getDevLogId() {
        return devLogId;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public String getRawMarkdown() {
        return rawMarkdown;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
