package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
    name = "project_timeline_summaries",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_timeline_summary_period", columnNames = {"project_id", "granularity", "period_key"}
    ),
    indexes = {
        @Index(name = "idx_timeline_summary_project_status", columnList = "project_id,status"),
        @Index(name = "idx_timeline_summary_project_period", columnList = "project_id,granularity,period_key")
    }
)
public class ProjectTimelineSummary {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimelineGranularity granularity;

    @Column(name = "period_key", nullable = false, length = 20)
    private String periodKey;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "timeline_zone", nullable = false, length = 80)
    private String timelineZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectTimelineSummaryStatus status = ProjectTimelineSummaryStatus.DIRTY;

    @Column(columnDefinition = "text")
    private String summary = "";

    @Column(name = "source_fact_count", nullable = false)
    private Integer sourceFactCount = 0;

    @Column(name = "covered_fact_count", nullable = false)
    private Integer coveredFactCount = 0;

    @Column(name = "source_fact_fingerprint", length = 64)
    private String sourceFactFingerprint = "";

    @Column(name = "source_fact_max_updated_at")
    private Instant sourceFactMaxUpdatedAt;

    @Column(name = "generation_version", nullable = false)
    private Integer generationVersion = 0;

    @Column(name = "model_provider", length = 200)
    private String modelProvider = "";

    @Column(name = "model_name", length = 200)
    private String modelName = "";

    @Column(name = "analysis_job_id")
    private UUID analysisJobId;

    @Column(name = "error_code", length = 80)
    private String errorCode = "";

    @Column(name = "error_summary", length = 1000)
    private String errorSummary = "";

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ProjectTimelineSummary() {
    }

    public ProjectTimelineSummary(
        UUID projectId, TimelineGranularity granularity, String periodKey,
        Instant periodStart, Instant periodEnd, String timelineZone
    ) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.granularity = granularity;
        this.periodKey = periodKey;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.timelineZone = timelineZone;
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

    public void markDirty(int factCount, String fingerprint, Instant maxUpdatedAt) {
        this.sourceFactCount = Math.max(0, factCount);
        this.sourceFactFingerprint = safe(fingerprint);
        this.sourceFactMaxUpdatedAt = maxUpdatedAt;
        this.status = ProjectTimelineSummaryStatus.DIRTY;
        this.errorCode = "";
        this.errorSummary = "";
    }

    public void markQueued(UUID jobId) {
        this.status = ProjectTimelineSummaryStatus.QUEUED;
        this.analysisJobId = jobId;
    }

    public void markGenerating(UUID jobId) {
        this.status = ProjectTimelineSummaryStatus.GENERATING;
        this.analysisJobId = jobId;
    }

    public void markWaitingForModel() {
        this.status = ProjectTimelineSummaryStatus.WAITING_FOR_MODEL;
        this.errorCode = "MODEL_NOT_CONFIGURED";
        this.errorSummary = "等待配置模型后自动生成摘要";
    }

    public void markFailed(String code, String message, UUID jobId) {
        this.status = ProjectTimelineSummaryStatus.FAILED;
        this.errorCode = safe(code);
        this.errorSummary = safe(message);
        this.analysisJobId = jobId;
    }

    public void complete(String summary, int coveredFactCount, String provider, String model, UUID jobId) {
        this.summary = safe(summary);
        this.coveredFactCount = Math.max(0, coveredFactCount);
        this.modelProvider = safe(provider);
        this.modelName = safe(model);
        this.analysisJobId = jobId;
        this.generationVersion = getGenerationVersion() + 1;
        this.generatedAt = Instant.now();
        this.status = ProjectTimelineSummaryStatus.READY;
        this.errorCode = "";
        this.errorSummary = "";
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public TimelineGranularity getGranularity() { return granularity; }
    public String getPeriodKey() { return periodKey; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getTimelineZone() { return timelineZone; }
    public ProjectTimelineSummaryStatus getStatus() { return status; }
    public String getSummary() { return safe(summary); }
    public int getSourceFactCount() { return sourceFactCount == null ? 0 : sourceFactCount; }
    public int getCoveredFactCount() { return coveredFactCount == null ? 0 : coveredFactCount; }
    public String getSourceFactFingerprint() { return safe(sourceFactFingerprint); }
    public Instant getSourceFactMaxUpdatedAt() { return sourceFactMaxUpdatedAt; }
    public int getGenerationVersion() { return generationVersion == null ? 0 : generationVersion; }
    public String getModelProvider() { return safe(modelProvider); }
    public String getModelName() { return safe(modelName); }
    public UUID getAnalysisJobId() { return analysisJobId; }
    public String getErrorCode() { return safe(errorCode); }
    public String getErrorSummary() { return safe(errorSummary); }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean hasGeneratedContent() { return generatedAt != null && !getSummary().isBlank(); }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
