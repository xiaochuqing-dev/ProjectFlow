package com.projectflow.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.projectflow.support.StringListConverter;
import com.projectflow.support.StringMapConverter;

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

    /** Opaque ProviderCredentialStore reference; never a credential value. */
    @Column(name = "secret_ref", length = 200)
    private String secretRef;

    @Column(name = "model_name", nullable = false, length = 160)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiProviderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", length = 40)
    private ModelProtocol protocol;

    @Column(name = "endpoint_override", length = 500)
    private String endpointOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_mode", length = 40)
    private AiProviderAuthMode authMode;

    @Column(name = "auth_header_name", length = 120)
    private String authHeaderName;

    @Column(name = "query_key_name", length = 120)
    private String queryKeyName;

    @Convert(converter = StringMapConverter.class)
    @Column(name = "safe_headers", columnDefinition = "text")
    private Map<String, String> safeHeaders = new LinkedHashMap<>();

    @Column(name = "request_timeout_seconds")
    private Integer requestTimeoutSeconds;

    @Column(name = "supports_temperature")
    private Boolean supportsTemperature;

    @Column(name = "supports_json_mode")
    private Boolean supportsJsonMode;

    @Column(name = "supports_structured_output")
    private Boolean supportsStructuredOutput;

    @Column(name = "supports_reasoning")
    private Boolean supportsReasoning;

    @Column(name = "supports_reasoning_control")
    private Boolean supportsReasoningControl;

    @Column(name = "last_probe_profile", columnDefinition = "text")
    private String lastProbeProfile;

    @Column(name = "last_probed_at")
    private Instant lastProbedAt;

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

    public String getSecretRef() {
        return secretRef;
    }

    public boolean hasConfiguredCredential() {
        return (secretRef != null && !secretRef.isBlank()) || (apiKey != null && !apiKey.isBlank());
    }

    public String getModelName() {
        return modelName;
    }

    public AiProviderType getType() {
        return type;
    }

    public ModelProtocol getProtocol() {
        return protocol == null ? defaultProtocol(type) : protocol;
    }

    public String getEndpointOverride() { return endpointOverride; }
    public AiProviderAuthMode getAuthMode() { return authMode == null ? AiProviderAuthMode.PROTOCOL_DEFAULT : authMode; }
    public String getAuthHeaderName() { return authHeaderName; }
    public String getQueryKeyName() { return queryKeyName; }
    public Map<String, String> getSafeHeaders() { return Map.copyOf(safeHeaders == null ? Map.of() : safeHeaders); }
    public Integer getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public Boolean getSupportsTemperature() { return supportsTemperature; }
    public Boolean getSupportsJsonMode() { return supportsJsonMode; }
    public Boolean getSupportsStructuredOutput() { return supportsStructuredOutput; }
    public Boolean getSupportsReasoning() { return supportsReasoning; }
    public Boolean getSupportsReasoningControl() { return supportsReasoningControl; }
    public String getLastProbeProfile() { return lastProbeProfile; }
    public Instant getLastProbedAt() { return lastProbedAt; }

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
        this.secretRef = null;
        this.modelName = modelName;
        this.type = type;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.defaultEnabled = defaultEnabled;
        this.purposeTags = purposeTags == null ? new ArrayList<>() : new ArrayList<>(purposeTags);
    }

    /**
     * Release write path: only an opaque secret reference is persisted. The
     * legacy apiKey column is cleared in the same entity update.
     */
    public void updateWithSecretRef(
        String name,
        String baseUrl,
        String secretRef,
        String modelName,
        AiProviderType type,
        Double temperature,
        Integer maxTokens,
        boolean defaultEnabled,
        List<String> purposeTags
    ) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = null;
        this.secretRef = blankToNull(secretRef);
        this.modelName = modelName;
        this.type = type;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.defaultEnabled = defaultEnabled;
        this.purposeTags = purposeTags == null ? new ArrayList<>() : new ArrayList<>(purposeTags);
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = blankToNull(secretRef);
    }

    public void clearLegacyApiKey() {
        this.apiKey = null;
    }

    /** Restores the credential columns after a failed persistence attempt. */
    public void restoreCredentialState(String secretRef, String legacyApiKey) {
        this.secretRef = blankToNull(secretRef);
        this.apiKey = legacyApiKey;
    }

    public void setDefaultEnabled(boolean enabled) {
        this.defaultEnabled = enabled;
    }

    public void configureProtocol(
        ModelProtocol protocol,
        String endpointOverride,
        AiProviderAuthMode authMode,
        String authHeaderName,
        String queryKeyName,
        Map<String, String> safeHeaders,
        Integer requestTimeoutSeconds,
        Boolean supportsTemperature,
        Boolean supportsJsonMode,
        Boolean supportsStructuredOutput,
        Boolean supportsReasoning,
        Boolean supportsReasoningControl
    ) {
        this.protocol = protocol == null ? defaultProtocol(type) : protocol;
        this.endpointOverride = blankToNull(endpointOverride);
        this.authMode = authMode == null ? AiProviderAuthMode.PROTOCOL_DEFAULT : authMode;
        this.authHeaderName = blankToNull(authHeaderName);
        this.queryKeyName = blankToNull(queryKeyName);
        this.safeHeaders = safeHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(safeHeaders);
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.supportsTemperature = supportsTemperature;
        this.supportsJsonMode = supportsJsonMode;
        this.supportsStructuredOutput = supportsStructuredOutput;
        this.supportsReasoning = supportsReasoning;
        this.supportsReasoningControl = supportsReasoningControl;
    }

    public boolean migrateProtocolDefaults() {
        boolean changed = false;
        if (protocol == null) {
            protocol = defaultProtocol(type);
            changed = true;
        }
        if (authMode == null) {
            authMode = AiProviderAuthMode.PROTOCOL_DEFAULT;
            changed = true;
        }
        return changed;
    }

    public void recordProbeProfile(String profile) {
        this.lastProbeProfile = blankToNull(profile);
        this.lastProbedAt = Instant.now();
    }

    private static ModelProtocol defaultProtocol(AiProviderType type) {
        if (type == AiProviderType.OPENAI) return ModelProtocol.OPENAI_RESPONSES;
        if (type == AiProviderType.ANTHROPIC) return ModelProtocol.ANTHROPIC_MESSAGES;
        return ModelProtocol.OPENAI_CHAT_COMPLETIONS;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
