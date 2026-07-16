package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_timeline_themes", indexes = {
    @Index(name = "idx_timeline_theme_summary", columnList = "summary_id,sort_order"),
    @Index(name = "idx_timeline_theme_project", columnList = "project_id")
})
public class ProjectTimelineTheme {
    @Id
    private UUID id;

    @Column(name = "summary_id", nullable = false)
    private UUID summaryId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectTimelineTheme() {
    }

    public ProjectTimelineTheme(UUID summaryId, UUID projectId, String title, String summary, int sortOrder) {
        this.id = UUID.randomUUID();
        this.summaryId = summaryId;
        this.projectId = projectId;
        this.title = title == null ? "" : title.trim();
        this.summary = summary == null ? "" : summary.trim();
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getSummaryId() { return summaryId; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title == null ? "" : title; }
    public String getSummary() { return summary == null ? "" : summary; }
    public int getSortOrder() { return sortOrder == null ? 0 : sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
