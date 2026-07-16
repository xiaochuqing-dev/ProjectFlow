package com.projectflow.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_fact_file_refs",
    uniqueConstraints = @UniqueConstraint(name = "uk_project_fact_file", columnNames = {"fact_id", "file_path"}),
    indexes = {
        @Index(name = "idx_project_fact_file_project", columnList = "project_id"),
        @Index(name = "idx_project_fact_file_fact", columnList = "fact_id")
    }
)
public class ProjectFactFileRef {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "fact_id", nullable = false)
    private UUID factId;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    protected ProjectFactFileRef() {
    }

    public ProjectFactFileRef(UUID projectId, UUID factId, String filePath) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.factId = factId;
        this.filePath = filePath == null ? "" : filePath.trim().replace('\\', '/');
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getFactId() { return factId; }
    public String getFilePath() { return filePath; }
}
