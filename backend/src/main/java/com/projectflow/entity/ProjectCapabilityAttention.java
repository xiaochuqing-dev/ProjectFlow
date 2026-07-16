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

@Entity
@Table(
    name = "project_capability_attention",
    uniqueConstraints = @UniqueConstraint(name = "uk_capability_attention_fingerprint", columnNames = {"project_id", "attention_fingerprint"}),
    indexes = @Index(name = "idx_capability_attention_project_status", columnList = "project_id,status")
)
public class ProjectCapabilityAttention {
    @Id private UUID id;
    @Column(name="project_id",nullable=false) private UUID projectId;
    @Column(name="attention_type",nullable=false,length=60) private String attentionType;
    @Column(nullable=false,length=1000) private String reason;
    @Column(name="fact_id") private UUID factId;
    @Column(name="source_capability_id") private UUID sourceCapabilityId;
    @Column(name="target_capability_id") private UUID targetCapabilityId;
    @Column(name="attention_fingerprint",nullable=false,length=64) private String attentionFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ProjectCapabilityAttentionStatus status=ProjectCapabilityAttentionStatus.OPEN;
    @Column(name="analysis_job_id") private UUID analysisJobId;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected ProjectCapabilityAttention(){}
    public ProjectCapabilityAttention(UUID projectId,String type,String reason,UUID factId,UUID sourceId,UUID targetId,String fingerprint,UUID jobId){
        this.id=UUID.randomUUID();this.projectId=projectId;this.attentionType=type;this.reason=reason;this.factId=factId;this.sourceCapabilityId=sourceId;this.targetCapabilityId=targetId;this.attentionFingerprint=fingerprint;this.analysisJobId=jobId;
    }
    @PrePersist void prePersist(){Instant now=Instant.now();if(id==null)id=UUID.randomUUID();if(createdAt==null)createdAt=now;updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public String getAttentionType(){return attentionType;} public String getReason(){return reason;}
    public UUID getFactId(){return factId;} public UUID getSourceCapabilityId(){return sourceCapabilityId;} public UUID getTargetCapabilityId(){return targetCapabilityId;}
    public String getAttentionFingerprint(){return attentionFingerprint;} public ProjectCapabilityAttentionStatus getStatus(){return status;} public UUID getAnalysisJobId(){return analysisJobId;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
