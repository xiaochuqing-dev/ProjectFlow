package com.projectflow.entity;

import java.time.Instant;
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
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "project_facts",
    uniqueConstraints = @UniqueConstraint(name = "uk_project_fact_fingerprint", columnNames = {"project_id", "fact_fingerprint"}),
    indexes = {
        @Index(name = "idx_project_fact_project_time", columnList = "project_id,occurred_to"),
        @Index(name = "idx_project_fact_batch", columnList = "batch_id"),
        @Index(name = "idx_project_fact_segment", columnList = "source_segment_id"),
        @Index(name = "idx_project_fact_status", columnList = "project_id,record_status"),
        @Index(name = "idx_project_fact_timeline_day", columnList = "project_id,timeline_day_key"),
        @Index(name = "idx_project_fact_timeline_week", columnList = "project_id,timeline_week_key"),
        @Index(name = "idx_project_fact_timeline_month", columnList = "project_id,timeline_month_key")
    }
)
public class ProjectFact {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "source_segment_id")
    private UUID sourceSegmentId;

    @Column(name = "legacy_sediment_id")
    private UUID legacySedimentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectFactOrigin origin;

    @Column(nullable = false, length = 200)
    private String title = "";

    @Column(columnDefinition = "text")
    private String summary = "";

    @Convert(converter = StringListConverter.class)
    @Column(name = "main_changes", columnDefinition = "text")
    private List<String> mainChanges = new ArrayList<>();

    @Column(name = "user_visible_value", columnDefinition = "text")
    private String userVisibleValue = "";

    @Column(name = "occurred_from")
    private Instant occurredFrom;

    @Column(name = "occurred_to")
    private Instant occurredTo;

    @Column(name = "timeline_event_at")
    private Instant timelineEventAt;

    @Column(name = "timeline_day_key", length = 10)
    private String timelineDayKey;

    @Column(name = "timeline_week_key", length = 8)
    private String timelineWeekKey;

    @Column(name = "timeline_month_key", length = 7)
    private String timelineMonthKey;

    @Convert(converter = StringListConverter.class)
    @Column(name = "commit_refs", columnDefinition = "text")
    private List<String> commitRefs = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "commit_urls", columnDefinition = "text")
    private List<String> commitUrls = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "agent_result_refs", columnDefinition = "text")
    private List<String> agentResultRefs = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "affected_files", columnDefinition = "text")
    private List<String> affectedFiles = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "evidence_refs", columnDefinition = "text")
    private List<String> evidenceRefs = new ArrayList<>();

    @Column(name = "commit_count")
    private Integer commitCount = 0;

    @Column(name = "agent_result_count")
    private Integer agentResultCount = 0;

    @Column(name = "affected_file_count")
    private Integer affectedFileCount = 0;

    @Column(name = "evidence_count")
    private Integer evidenceCount = 0;

    @Column(name = "source_mode", length = 40)
    private String sourceMode = "LOCAL_RULE";

    @Column(name = "quality_status", length = 40)
    private String qualityStatus = "NEEDS_REVIEW";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceConfidence confidence = EvidenceConfidence.LOW;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_status", nullable = false, length = 40)
    private ProjectFactRecordStatus recordStatus = ProjectFactRecordStatus.NEEDS_ATTENTION;

    @Column(name = "attention_reason", columnDefinition = "text")
    private String attentionReason = "";

    @Column(name = "fact_fingerprint", nullable = false, length = 64)
    private String factFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectFact() {
    }

    public ProjectFact(
        UUID projectId,
        UUID batchId,
        UUID sourceSegmentId,
        ProjectFactOrigin origin,
        String factFingerprint
    ) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.batchId = batchId;
        this.sourceSegmentId = sourceSegmentId;
        this.origin = origin == null ? ProjectFactOrigin.INCREMENTAL_SCAN : origin;
        this.factFingerprint = requireText(factFingerprint, "factFingerprint");
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (origin == null) origin = ProjectFactOrigin.INCREMENTAL_SCAN;
        if (recordStatus == null) recordStatus = ProjectFactRecordStatus.NEEDS_ATTENTION;
        if (confidence == null) confidence = EvidenceConfidence.LOW;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void updateContent(
        String title,
        String summary,
        List<String> mainChanges,
        String userVisibleValue,
        Instant occurredFrom,
        Instant occurredTo,
        List<String> commitRefs,
        List<String> commitUrls,
        List<String> agentResultRefs,
        List<String> affectedFiles,
        List<String> evidenceRefs,
        String sourceMode,
        String qualityStatus,
        EvidenceConfidence confidence,
        ProjectFactRecordStatus recordStatus,
        String attentionReason
    ) {
        this.title = safe(title);
        this.summary = safe(summary);
        this.mainChanges = copy(mainChanges);
        this.userVisibleValue = safe(userVisibleValue);
        this.occurredFrom = occurredFrom;
        this.occurredTo = occurredTo == null ? occurredFrom : occurredTo;
        this.commitRefs = copy(commitRefs);
        this.commitUrls = copy(commitUrls);
        this.agentResultRefs = copy(agentResultRefs);
        this.affectedFiles = copy(affectedFiles);
        this.evidenceRefs = copy(evidenceRefs);
        this.commitCount = this.commitRefs.size();
        this.agentResultCount = this.agentResultRefs.size();
        this.affectedFileCount = this.affectedFiles.size();
        this.evidenceCount = this.evidenceRefs.size();
        this.sourceMode = fallback(sourceMode, "LOCAL_RULE");
        this.qualityStatus = fallback(qualityStatus, "NEEDS_REVIEW");
        this.confidence = confidence == null ? EvidenceConfidence.LOW : confidence;
        this.recordStatus = recordStatus == null ? ProjectFactRecordStatus.NEEDS_ATTENTION : recordStatus;
        this.attentionReason = safe(attentionReason);
    }

    public void linkLegacySediment(UUID sedimentId) {
        if (sedimentId != null) this.legacySedimentId = sedimentId;
    }

    public void assignTimeline(Instant eventAt, String dayKey, String weekKey, String monthKey) {
        this.timelineEventAt = eventAt;
        this.timelineDayKey = safe(dayKey);
        this.timelineWeekKey = safe(weekKey);
        this.timelineMonthKey = safe(monthKey);
    }

    /** V3.4.2 只重新评级既有证据，不改写事实内容、来源、时间、指纹或游标。 */
    public void reclassify(ProjectFactRecordStatus status, String reason) {
        this.recordStatus = status == null ? ProjectFactRecordStatus.NEEDS_ATTENTION : status;
        this.attentionReason = safe(reason);
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getBatchId() { return batchId; }
    public UUID getSourceSegmentId() { return sourceSegmentId; }
    public UUID getLegacySedimentId() { return legacySedimentId; }
    public ProjectFactOrigin getOrigin() { return origin == null ? ProjectFactOrigin.INCREMENTAL_SCAN : origin; }
    public String getTitle() { return safe(title); }
    public String getSummary() { return safe(summary); }
    public List<String> getMainChanges() { return immutable(mainChanges); }
    public String getUserVisibleValue() { return safe(userVisibleValue); }
    public Instant getOccurredFrom() { return occurredFrom; }
    public Instant getOccurredTo() { return occurredTo == null ? occurredFrom : occurredTo; }
    public Instant getTimelineEventAt() { return timelineEventAt; }
    public String getTimelineDayKey() { return safe(timelineDayKey); }
    public String getTimelineWeekKey() { return safe(timelineWeekKey); }
    public String getTimelineMonthKey() { return safe(timelineMonthKey); }
    public List<String> getCommitRefs() { return immutable(commitRefs); }
    public List<String> getCommitUrls() { return immutable(commitUrls); }
    public List<String> getAgentResultRefs() { return immutable(agentResultRefs); }
    public List<String> getAffectedFiles() { return immutable(affectedFiles); }
    public List<String> getEvidenceRefs() { return immutable(evidenceRefs); }
    public int getCommitCount() { return commitCount == null ? immutable(commitRefs).size() : commitCount; }
    public int getAgentResultCount() { return agentResultCount == null ? immutable(agentResultRefs).size() : agentResultCount; }
    public int getAffectedFileCount() { return affectedFileCount == null ? immutable(affectedFiles).size() : affectedFileCount; }
    public int getEvidenceCount() { return evidenceCount == null ? immutable(evidenceRefs).size() : evidenceCount; }
    public String getSourceMode() { return fallback(sourceMode, "LOCAL_RULE"); }
    public String getQualityStatus() { return fallback(qualityStatus, "NEEDS_REVIEW"); }
    public EvidenceConfidence getConfidence() { return confidence == null ? EvidenceConfidence.LOW : confidence; }
    public ProjectFactRecordStatus getRecordStatus() { return recordStatus == null ? ProjectFactRecordStatus.NEEDS_ATTENTION : recordStatus; }
    public String getAttentionReason() { return safe(attentionReason); }
    public String getFactFingerprint() { return safe(factFingerprint); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static List<String> copy(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }
    private static List<String> immutable(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
