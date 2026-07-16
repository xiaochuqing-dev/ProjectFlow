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
    name = "project_timeline_theme_facts",
    uniqueConstraints = @UniqueConstraint(name = "uk_timeline_theme_fact", columnNames = {"theme_id", "fact_id"}),
    indexes = {
        @Index(name = "idx_timeline_theme_fact_theme", columnList = "theme_id"),
        @Index(name = "idx_timeline_theme_fact_fact", columnList = "fact_id"),
        @Index(name = "idx_timeline_theme_fact_project", columnList = "project_id")
    }
)
public class ProjectTimelineThemeFact {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "theme_id", nullable = false)
    private UUID themeId;

    @Column(name = "fact_id", nullable = false)
    private UUID factId;

    protected ProjectTimelineThemeFact() {
    }

    public ProjectTimelineThemeFact(UUID projectId, UUID themeId, UUID factId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.themeId = themeId;
        this.factId = factId;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getThemeId() { return themeId; }
    public UUID getFactId() { return factId; }
}
