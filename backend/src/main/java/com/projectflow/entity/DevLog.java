package com.projectflow.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projectflow.support.StringListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "dev_logs")
public class DevLog {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DevLogCategory category;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "minutes_spent", nullable = false)
    private Integer minutesSpent;

    @Column(nullable = false)
    private boolean blocked;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> tags = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DevLog() {
    }

    public DevLog(UUID projectId) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public DevLogCategory getCategory() {
        return category;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public Integer getMinutesSpent() {
        return minutesSpent;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public List<String> getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(UUID taskId, String title, String content, DevLogCategory category, LocalDate logDate, Integer minutesSpent, boolean blocked, List<String> tags) {
        this.taskId = taskId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.logDate = logDate;
        this.minutesSpent = minutesSpent;
        this.blocked = blocked;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }
}
