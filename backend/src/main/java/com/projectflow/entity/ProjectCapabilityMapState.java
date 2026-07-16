package com.projectflow.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name="project_capability_map_states",uniqueConstraints=@UniqueConstraint(name="uk_capability_map_project",columnNames="project_id"))
public class ProjectCapabilityMapState {
    @Id private UUID id;
    @Column(name="project_id",nullable=false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ProjectCapabilityMapStatus status=ProjectCapabilityMapStatus.NOT_INITIALIZED;
    @Column(name="source_fact_count",nullable=false) private Integer sourceFactCount=0;
    @Column(name="covered_fact_count",nullable=false) private Integer coveredFactCount=0;
    @Column(name="assigned_fact_count",nullable=false) private Integer assignedFactCount=0;
    @Column(name="no_change_fact_count",nullable=false) private Integer noChangeFactCount=0;
    @Column(name="attention_fact_count",nullable=false) private Integer attentionFactCount=0;
    @Column(name="source_fact_fingerprint",length=64) private String sourceFactFingerprint="";
    @Column(name="last_processed_fact_at") private Instant lastProcessedFactAt;
    @Column(name="latest_successful_job_id") private UUID latestSuccessfulJobId;
    @Column(name="latest_attempt_job_id") private UUID latestAttemptJobId;
    @Column(name="dirty_since") private Instant dirtySince;
    @Column(name="latest_successful_at") private Instant latestSuccessfulAt;
    @Column(name="latest_attempt_at") private Instant latestAttemptAt;
    @Column(name="generation_version",nullable=false) private Integer generationVersion=0;
    @Column(name="error_code",length=80) private String errorCode="";
    @Column(name="error_summary",length=1000) private String errorSummary="";
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private Long rowVersion;
    protected ProjectCapabilityMapState(){}
    public ProjectCapabilityMapState(UUID projectId){this.id=UUID.randomUUID();this.projectId=projectId;}
    @PrePersist void prePersist(){Instant now=Instant.now();if(id==null)id=UUID.randomUUID();if(createdAt==null)createdAt=now;updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public void markDirty(int facts,String fingerprint,Instant maxUpdatedAt){
        sourceFactCount=Math.max(0,facts);sourceFactFingerprint=safe(fingerprint);lastProcessedFactAt=maxUpdatedAt;dirtySince=dirtySince==null?Instant.now():dirtySince;
        status=latestSuccessfulAt==null?ProjectCapabilityMapStatus.DIRTY:ProjectCapabilityMapStatus.READY_STALE;errorCode="";errorSummary="";
    }
    public void markQueued(UUID jobId){status=ProjectCapabilityMapStatus.QUEUED;latestAttemptJobId=jobId;latestAttemptAt=Instant.now();}
    public void markGenerating(UUID jobId){status=ProjectCapabilityMapStatus.GENERATING;latestAttemptJobId=jobId;latestAttemptAt=Instant.now();}
    public void markWaitingForModel(){status=latestSuccessfulAt==null?ProjectCapabilityMapStatus.WAITING_FOR_MODEL:ProjectCapabilityMapStatus.READY_STALE;errorCode="MODEL_NOT_CONFIGURED";errorSummary="等待配置模型后自动维护能力地图";}
    public void complete(int source,int covered,int assigned,int noChange,int attention,String fingerprint,Instant lastFactAt,UUID jobId){
        sourceFactCount=source;coveredFactCount=covered;assignedFactCount=assigned;noChangeFactCount=noChange;attentionFactCount=attention;sourceFactFingerprint=safe(fingerprint);
        lastProcessedFactAt=lastFactAt;latestSuccessfulJobId=jobId;latestAttemptJobId=jobId;latestSuccessfulAt=Instant.now();latestAttemptAt=latestSuccessfulAt;
        generationVersion=getGenerationVersion()+1;dirtySince=null;status=ProjectCapabilityMapStatus.READY;errorCode="";errorSummary="";
    }
    public void markFailed(String code,String summary,UUID jobId){latestAttemptJobId=jobId;latestAttemptAt=Instant.now();status=latestSuccessfulAt==null?ProjectCapabilityMapStatus.FAILED:ProjectCapabilityMapStatus.READY_STALE;errorCode=safe(code);errorSummary=safe(summary);}
    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public ProjectCapabilityMapStatus getStatus(){return status;}
    public int getSourceFactCount(){return value(sourceFactCount);} public int getCoveredFactCount(){return value(coveredFactCount);} public int getAssignedFactCount(){return value(assignedFactCount);}
    public int getNoChangeFactCount(){return value(noChangeFactCount);} public int getAttentionFactCount(){return value(attentionFactCount);} public String getSourceFactFingerprint(){return safe(sourceFactFingerprint);}
    public Instant getLastProcessedFactAt(){return lastProcessedFactAt;} public UUID getLatestSuccessfulJobId(){return latestSuccessfulJobId;} public UUID getLatestAttemptJobId(){return latestAttemptJobId;}
    public Instant getDirtySince(){return dirtySince;} public Instant getLatestSuccessfulAt(){return latestSuccessfulAt;} public Instant getLatestAttemptAt(){return latestAttemptAt;}
    public int getGenerationVersion(){return value(generationVersion);} public String getErrorCode(){return safe(errorCode);} public String getErrorSummary(){return safe(errorSummary);}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    private static int value(Integer value){return value==null?0:value;} private static String safe(String value){return value==null?"":value.trim();}
}
