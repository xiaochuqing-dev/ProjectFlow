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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_providers")
public class AiProvider {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key", columnDefinition = "text")
    private String apiKey;

    @Column(name = "model_name", nullable = false, length = 160)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiProviderType type;

    @Column(nullable = false)
    private Double temperature;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens;

    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;

    @Convert(converter = StringListConverter.class)
    @Column(name = "purpose_tags", columnDefinition = "text")
    private List<String> purposeTags = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProvider() {
    }

    public AiProvider(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
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

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public AiProviderType getType() {
        return type;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public List<String> getPurposeTags() {
        return purposeTags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(
        String name,
        String baseUrl,
        String apiKey,
        String modelName,
        AiProviderType type,
        Double temperature,
        Integer maxTokens,
        boolean defaultEnabled,
        List<String> purposeTags
    ) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.type = type;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.defaultEnabled = defaultEnabled;
        this.purposeTags = purposeTags == null ? new ArrayList<>() : new ArrayList<>(purposeTags);
    }

    public void setDefaultEnabled(boolean enabled) {
        this.defaultEnabled = enabled;
    }
}
