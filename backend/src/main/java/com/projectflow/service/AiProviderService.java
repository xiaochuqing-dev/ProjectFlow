package com.projectflow.service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.ApplicationEventPublisher;

import com.projectflow.dto.AiProviderDtos.AiProviderRequest;
import com.projectflow.dto.AiProviderDtos.AiProviderResponse;
import com.projectflow.dto.AiProviderDtos.DuplicateCleanupRequest;
import com.projectflow.dto.AiProviderDtos.DuplicateCleanupResponse;
import com.projectflow.dto.AiProviderDtos.DuplicateProviderGroupResponse;
import com.projectflow.dto.AiProviderDtos.ProviderTestResponse;
import com.projectflow.dto.AiProviderDtos.ProviderCompatibilityProfile;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.support.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiProviderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiProviderService.class);
    private final AiProviderRepository aiProviderRepository;
    private final AiProviderUrlGuard aiProviderUrlGuard;
    private final ModelGatewayService modelGatewayService;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelCapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;
    private final ProviderCredentialStore providerCredentialStore;

    /** Compatibility constructor retained for focused unit tests. */
    public AiProviderService(
        AiProviderRepository aiProviderRepository,
        AiProviderUrlGuard aiProviderUrlGuard,
        ModelGatewayService modelGatewayService,
        ApplicationEventPublisher eventPublisher,
        ModelCapabilityRegistry capabilityRegistry,
        ObjectMapper objectMapper
    ) {
        this(
            aiProviderRepository, aiProviderUrlGuard, modelGatewayService, eventPublisher,
            capabilityRegistry, objectMapper, new InMemoryProviderCredentialStore()
        );
    }

    @Autowired
    public AiProviderService(
        AiProviderRepository aiProviderRepository,
        AiProviderUrlGuard aiProviderUrlGuard,
        ModelGatewayService modelGatewayService,
        ApplicationEventPublisher eventPublisher,
        ModelCapabilityRegistry capabilityRegistry,
        ObjectMapper objectMapper,
        ProviderCredentialStore providerCredentialStore
    ) {
        this.aiProviderRepository = aiProviderRepository;
        this.aiProviderUrlGuard = aiProviderUrlGuard;
        this.modelGatewayService = modelGatewayService;
        this.eventPublisher = eventPublisher;
        this.capabilityRegistry = capabilityRegistry;
        this.objectMapper = objectMapper;
        this.providerCredentialStore = providerCredentialStore;
    }

    @Transactional
    public List<AiProviderResponse> list(UUID userId) {
        List<AiProvider> providers = aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId);
        normalizeHistoricalDefaults(providers);
        List<AiProviderResponse> saved = providers
            .stream()
            .map(this::toResponse)
            .toList();
        if (!saved.isEmpty()) {
            return saved;
        }
        return List.of(mockProvider());
    }

    @Transactional
    public AiProviderResponse create(UUID userId, AiProviderRequest request) {
        ModelProtocol protocol = request.protocol() == null ? defaultProtocol(request.type()) : request.protocol();
        String baseUrl = canonicalBaseUrl(request.baseUrl(), protocol);
        String modelName = request.modelName().trim();
        AiProvider provider = aiProviderRepository
            .findByUserIdAndTypeAndBaseUrlAndModelNameAndProtocol(userId, request.type(), baseUrl, modelName, protocol)
            .map(existing -> findOwnedForMutation(userId, existing.getId()))
            .orElseGet(() -> new AiProvider(userId));
        if (request.defaultEnabled()) ensureSingleDefault(userId, provider.getId());
        AiProvider saved = saveProviderWithCredential(
            provider, request, request.name().trim(), baseUrl, modelName
        );
        if (saved.isDefaultEnabled()) eventPublisher.publishEvent(new ModelProviderConfiguredEvent(userId));
        return toResponse(saved);
    }

    @Transactional
    public AiProviderResponse update(UUID userId, UUID providerId, AiProviderRequest request) {
        AiProvider provider = findOwnedForMutation(userId, providerId);
        if (request.defaultEnabled()) ensureSingleDefault(userId, provider.getId());
        AiProvider saved = saveProviderWithCredential(
            provider,
            request,
            request.name().trim(),
            canonicalBaseUrl(request.baseUrl(), request.protocol() == null ? defaultProtocol(request.type()) : request.protocol()),
            request.modelName().trim()
        );
        if (provider.isDefaultEnabled()) eventPublisher.publishEvent(new ModelProviderConfiguredEvent(userId));
        return toResponse(saved);
    }

    /**
     * Migrates one legacy row using store write/read-back followed by the
     * secretRef + plaintext clear in the surrounding database transaction.
     */
    @Transactional
    public boolean migrateLegacyCredential(UUID userId, UUID providerId) {
        AiProvider provider = findOwnedForMutation(userId, providerId);
        String legacy = blankToNull(provider.getApiKey());
        if (legacy == null) return false;
        String oldRef = blankToNull(provider.getSecretRef());
        String oldSecret = oldRef == null ? null : readStoredCredential(oldRef);
        String newRef = null;
        try {
            newRef = writeAndVerifyCredential(provider, legacy, oldRef, oldSecret);
            provider.setSecretRef(newRef);
            provider.clearLegacyApiKey();
            aiProviderRepository.saveAndFlush(provider);
            scheduleStoreRollback(provider, newRef, oldRef, oldSecret);
            if (oldRef != null && !oldRef.equals(newRef)) scheduleCredentialDeletion(oldRef);
            return true;
        } catch (RuntimeException failure) {
            if (newRef != null) deleteAfterFailedPersistence(provider, newRef, oldRef, oldSecret);
            provider.restoreCredentialState(oldRef, legacy);
            throw failure;
        }
    }

    @Transactional
    public void delete(UUID userId, UUID providerId) {
        AiProvider provider = findOwnedForMutation(userId, providerId);
        long otherProviders = aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(item -> !item.getId().equals(providerId))
            .count();
        if (provider.isDefaultEnabled() && otherProviders > 0) {
            throw new AppException(
                "DEFAULT_PROVIDER_REPLACEMENT_REQUIRED",
                "这是当前默认模型，请先把其他 Provider 设为默认后再删除。",
                HttpStatus.CONFLICT
            );
        }
        scheduleProviderDeletion(provider);
    }

    @Transactional
    public List<DuplicateProviderGroupResponse> duplicateGroups(UUID userId) {
        List<AiProvider> providers = aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId);
        normalizeHistoricalDefaults(providers);
        Map<String, List<AiProvider>> groups = groupDuplicates(providers);
        List<DuplicateProviderGroupResponse> result = new ArrayList<>();
        groups.forEach((key, values) -> {
            if (values.size() < 2) return;
            List<AiProvider> sorted = values.stream().sorted(keeperComparator()).toList();
            result.add(new DuplicateProviderGroupResponse(
                key, toResponse(sorted.get(0)), sorted.stream().skip(1).map(this::toResponse).toList()
            ));
        });
        return result;
    }

    @Transactional
    public DuplicateCleanupResponse cleanupDuplicates(UUID userId, DuplicateCleanupRequest request) {
        List<AiProvider> all = aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId);
        Map<UUID, AiProvider> owned = all.stream().collect(java.util.stream.Collectors.toMap(AiProvider::getId, value -> value));
        Set<UUID> requestedIds = Set.copyOf(request.providerIds());
        if (requestedIds.size() != request.providerIds().size()) {
            throw new AppException("DUPLICATE_PROVIDER_SELECTION_INVALID", "重复清理列表中包含重复项。", HttpStatus.BAD_REQUEST);
        }
        Map<String, List<AiProvider>> groups = groupDuplicates(all);
        for (UUID providerId : requestedIds) {
            AiProvider provider = owned.get(providerId);
            if (provider == null) throw new AppException("AI_PROVIDER_NOT_FOUND", "模型配置不存在。", HttpStatus.NOT_FOUND);
            List<AiProvider> group = groups.getOrDefault(duplicateKey(provider), List.of());
            long selectedInGroup = group.stream().filter(item -> requestedIds.contains(item.getId())).count();
            if (group.size() < 2 || selectedInGroup >= group.size()) {
                throw new AppException("DUPLICATE_PROVIDER_SELECTION_INVALID", "每组重复配置必须至少保留一项。", HttpStatus.BAD_REQUEST);
            }
            if (provider.isDefaultEnabled()) {
                throw new AppException("DEFAULT_PROVIDER_DELETE_FORBIDDEN", "默认 Provider 不会被重复清理删除。", HttpStatus.CONFLICT);
            }
        }
        // Re-read and lock every selected row before capturing its secretRef;
        // otherwise a concurrent replacement could leave a newly written
        // opaque reference orphaned after this batch deletion commits.
        List<AiProvider> selected = requestedIds.stream()
            .map(providerId -> aiProviderRepository.findLockedByIdAndUserId(providerId, userId)
                .orElseThrow(() -> new AppException("AI_PROVIDER_NOT_FOUND", "模型配置不存在。", HttpStatus.NOT_FOUND)))
            .toList();
        scheduleProviderDeletion(selected);
        List<AiProviderResponse> remaining = aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)
            .stream().filter(provider -> !requestedIds.contains(provider.getId())).map(this::toResponse).toList();
        return new DuplicateCleanupResponse(requestedIds.size(), remaining);
    }

    public ProviderTestResponse test(UUID userId, UUID providerId) {
        AiProvider provider = findOwned(userId, providerId);
        if (provider.getType() == AiProviderType.MOCK) {
            return testResult(provider, true, true, "本地模拟 Provider 可用，但这不是真实模型验收。", "MOCK_ONLY", 0,
                List.of("未调用真实模型。"), "UNAVAILABLE", "FAILED");
        }
        if (provider.getAuthMode() != AiProviderAuthMode.NONE && !provider.hasConfiguredCredential()) {
            return testResult(provider, false, false, "请先配置 API Key，再测试连接。", "INCOMPATIBLE", 0,
                List.of("缺少认证信息。"), "UNAVAILABLE", "FAILED");
        }

        try {
            ModelGatewayService.StructuredModelResponse response = modelGatewayService.callStructured(
                provider,
                "基于事实‘ProjectFlow 使用 ProjectFact 保存已发生开发结果’，只返回 {\"summary\":\"\"}，填入一句简短中文。",
                ModelTaskType.PROVIDER_PROJECTFLOW_COMPATIBILITY_TEST
            );
            boolean projectFlowOk = !response.parsed().root().path("summary").asText("").isBlank();
            ModelGatewayService.ModelCallDiagnostics diagnostics = response.diagnostics();
            return testResult(
                provider, projectFlowOk, true,
                projectFlowOk
                    ? "连接、协议解析和 ProjectFlow 最小结构化任务均通过。能力档案：" + diagnostics.capabilityProfile() + "。"
                    : "连接成功，但 ProjectFlow 最小结构化任务不兼容。",
                projectFlowOk ? "FULL" : "INCOMPATIBLE",
                diagnostics.requestCount(),
                capabilitiesWarnings(provider), usage(diagnostics), "PASSED"
            );
        } catch (Exception exception) {
            boolean connectionPassed = requestReachedProvider(exception);
            return testResult(
                provider, false, connectionPassed,
                (connectionPassed ? "连接可达，但协议响应或 ProjectFlow 最小任务不兼容。" : "传输或协议测试失败。")
                    + modelGatewayService.failureMessage(exception),
                "INCOMPATIBLE", requestCount(exception),
                List.of("失败阶段已归一化，未保存原始响应。"), "UNAVAILABLE", connectionPassed ? "FAILED" : "UNAVAILABLE"
            );
        }
    }

    private AiProvider findOwned(UUID userId, UUID providerId) {
        return aiProviderRepository.findByIdAndUserId(providerId, userId)
            .orElseThrow(() -> new AppException("AI_PROVIDER_NOT_FOUND", "AI provider was not found", HttpStatus.NOT_FOUND));
    }

    private AiProvider findOwnedForMutation(UUID userId, UUID providerId) {
        return aiProviderRepository.findLockedByIdAndUserId(providerId, userId).orElseThrow(() -> new AppException(
            "AI_PROVIDER_NOT_FOUND", "AI provider was not found", HttpStatus.NOT_FOUND
        ));
    }

    private void ensureSingleDefault(UUID userId, UUID selectedProviderId) {
        aiProviderRepository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId).stream()
            .filter(AiProvider::isDefaultEnabled)
            .filter(provider -> selectedProviderId == null || !provider.getId().equals(selectedProviderId))
            .forEach(provider -> provider.setDefaultEnabled(false));
    }

    private void normalizeHistoricalDefaults(List<AiProvider> providers) {
        List<AiProvider> defaults = providers.stream().filter(AiProvider::isDefaultEnabled).toList();
        if (defaults.size() <= 1) return;
        AiProvider keeper = defaults.stream().sorted(keeperComparator()).findFirst().orElseThrow();
        defaults.stream().filter(provider -> !provider.getId().equals(keeper.getId()))
            .forEach(provider -> provider.setDefaultEnabled(false));
    }

    private Map<String, List<AiProvider>> groupDuplicates(List<AiProvider> providers) {
        Map<String, List<AiProvider>> groups = new LinkedHashMap<>();
        for (AiProvider provider : providers) {
            groups.computeIfAbsent(duplicateKey(provider), ignored -> new ArrayList<>()).add(provider);
        }
        return groups;
    }

    private String duplicateKey(AiProvider provider) {
        return provider.getType().name() + "|" + normalizeBaseUrl(provider.getBaseUrl()).toLowerCase(Locale.ROOT)
            + "|" + provider.getProtocol().name() + "|" + provider.getModelName().trim().toLowerCase(Locale.ROOT);
    }

    private Comparator<AiProvider> keeperComparator() {
        return Comparator.<AiProvider>comparingInt(provider ->
            (provider.isDefaultEnabled() ? 2 : 0) + (provider.hasConfiguredCredential() ? 1 : 0)
        ).reversed().thenComparing(AiProvider::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private AiProviderResponse toResponse(AiProvider provider) {
        return new AiProviderResponse(
            provider.getId(),
            provider.getName(),
            provider.getBaseUrl(),
            provider.getModelName(),
            provider.getType(),
            provider.getProtocol(),
            provider.getEndpointOverride(),
            provider.getAuthMode(),
            provider.getAuthHeaderName(),
            provider.getQueryKeyName(),
            provider.getSafeHeaders().keySet().stream().sorted().toList(),
            provider.getRequestTimeoutSeconds(),
            provider.getSupportsTemperature(),
            provider.getSupportsJsonMode(),
            provider.getSupportsStructuredOutput(),
            provider.getSupportsReasoning(),
            provider.getSupportsReasoningControl(),
            provider.getTemperature(),
            provider.getMaxTokens(),
            provider.isDefaultEnabled(),
            provider.getPurposeTags(),
            provider.hasConfiguredCredential(),
            credentialStatus(provider),
            provider.getLastProbeProfile(),
            provider.getLastProbedAt(),
            provider.getCreatedAt(),
            provider.getUpdatedAt()
        );
    }

    private AiProvider saveProviderWithCredential(
        AiProvider provider,
        AiProviderRequest request,
        String name,
        String baseUrl,
        String modelName
    ) {
        String oldRef = blankToNull(provider.getSecretRef());
        String oldLegacy = blankToNull(provider.getApiKey());
        String submitted = blankToNull(request.apiKey());
        String oldSecret = null;
        String newRef = null;
        boolean storeChanged = false;
        try {
            if (!request.clearApiKey() && submitted != null) {
                if (oldRef != null) oldSecret = readStoredCredential(oldRef);
                newRef = writeAndVerifyCredential(provider, submitted, oldRef, oldSecret);
                storeChanged = true;
            } else if (!request.clearApiKey() && oldRef != null) {
                newRef = oldRef;
            } else if (!request.clearApiKey() && oldLegacy != null) {
                newRef = writeAndVerifyCredential(provider, oldLegacy, null, null);
                storeChanged = true;
            }

            provider.updateWithSecretRef(
                name,
                baseUrl,
                newRef,
                modelName,
                request.type(),
                request.temperature(),
                request.maxTokens(),
                request.defaultEnabled(),
                request.purposeTags()
            );
            configureProtocol(provider, request);
            AiProvider saved = aiProviderRepository.saveAndFlush(provider);
            if (storeChanged && newRef != null) {
                scheduleStoreRollback(provider, newRef, oldRef, oldSecret);
            }
            if (oldRef != null && (newRef == null || !oldRef.equals(newRef))) {
                scheduleCredentialDeletion(oldRef);
            }
            return saved;
        } catch (RuntimeException failure) {
            if (storeChanged && newRef != null) {
                deleteAfterFailedPersistence(provider, newRef, oldRef, oldSecret);
            }
            provider.restoreCredentialState(oldRef, oldLegacy);
            throw failure;
        }
    }

    private String writeAndVerifyCredential(AiProvider provider, String secret, String oldRef, String oldSecret) {
        String ref = null;
        try {
            ref = providerCredentialStore.writeAndVerify(provider.getId(), secret);
            if (ref == null || ref.isBlank()) {
                throw new ProviderCredentialStoreException("SECRET_STORE_VERIFY_FAILED", "凭据存储未返回有效引用。");
            }
            String readBack = providerCredentialStore.read(ref);
            if (readBack == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), readBack.getBytes(StandardCharsets.UTF_8)
            )) {
                throw new ProviderCredentialStoreException("SECRET_STORE_VERIFY_FAILED", "凭据存储回读校验失败。");
            }
            return ref;
        } catch (ProviderCredentialStoreException failure) {
            rollbackStoreWrite(provider, ref, oldRef, oldSecret);
            throw credentialAppException(failure.code(), false);
        } catch (RuntimeException failure) {
            rollbackStoreWrite(provider, ref, oldRef, oldSecret);
            throw credentialAppException("SECRET_STORE_UNAVAILABLE", false);
        }
    }

    private void rollbackStoreWrite(AiProvider provider, String ref, String oldRef, String oldSecret) {
        try {
            if (oldRef != null && oldSecret != null && (ref == null || Objects.equals(ref, oldRef))) {
                providerCredentialStore.writeAndVerify(provider.getId(), oldSecret);
            } else if (ref != null && !ref.isBlank()) {
                providerCredentialStore.delete(ref);
            }
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn("Provider credential write rollback incomplete code={} provider={}",
                storeFailureCode(cleanupFailure), provider.getId());
        }
    }

    private String readStoredCredential(String secretRef) {
        try {
            String secret = providerCredentialStore.read(secretRef);
            if (secret == null || secret.isBlank()) {
                throw new ProviderCredentialStoreException("SECRET_NOT_FOUND", "凭据不存在或已被删除。");
            }
            return secret;
        } catch (ProviderCredentialStoreException failure) {
            throw credentialAppException(failure.code(), false);
        } catch (RuntimeException failure) {
            throw credentialAppException("SECRET_STORE_UNAVAILABLE", false);
        }
    }

    private void deleteAfterFailedPersistence(AiProvider provider, String newRef, String oldRef, String oldSecret) {
        try {
            if (oldRef != null && oldSecret != null && Objects.equals(newRef, oldRef)) {
                providerCredentialStore.writeAndVerify(provider.getId(), oldSecret);
            } else if (newRef != null && !newRef.isBlank()) {
                providerCredentialStore.delete(newRef);
            }
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn("Provider credential rollback incomplete code={} provider={}",
                storeFailureCode(cleanupFailure), provider.getId());
        }
    }

    private void scheduleProviderDeletion(AiProvider provider) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aiProviderRepository.delete(provider);
            aiProviderRepository.flush();
            deleteCredentialIfPresent(provider.getSecretRef());
            return;
        }
        String secretRef = blankToNull(provider.getSecretRef());
        aiProviderRepository.delete(provider);
        aiProviderRepository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    deleteCredentialIfPresent(secretRef);
                } catch (RuntimeException cleanupFailure) {
                    LOGGER.warn("Provider credential cleanup after committed deletion failed code={} provider={}",
                        storeFailureCode(cleanupFailure), provider.getId());
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    LOGGER.debug("Provider deletion rolled back; credential retained provider={}", provider.getId());
                }
            }
        });
    }

    private void scheduleProviderDeletion(List<AiProvider> providers) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aiProviderRepository.deleteAll(providers);
            aiProviderRepository.flush();
            providers.forEach(provider -> deleteCredentialIfPresent(provider.getSecretRef()));
            return;
        }
        List<UUID> providerIds = providers.stream().map(AiProvider::getId).toList();
        List<String> secretRefs = providers.stream().map(AiProvider::getSecretRef).toList();
        aiProviderRepository.deleteAll(providers);
        aiProviderRepository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (int index = 0; index < secretRefs.size(); index++) {
                    try {
                        deleteCredentialIfPresent(secretRefs.get(index));
                    } catch (RuntimeException cleanupFailure) {
                        LOGGER.warn("Duplicate provider credential cleanup after committed deletion failed code={} provider={}",
                            storeFailureCode(cleanupFailure), providerIds.get(index));
                    }
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    LOGGER.debug("Duplicate provider deletion rolled back; credentials retained count={}", providerIds.size());
                }
            }
        });
    }

    private void scheduleCredentialDeletion(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteCredentialIfPresent(secretRef);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    deleteCredentialIfPresent(secretRef);
                } catch (RuntimeException cleanupFailure) {
                    LOGGER.warn("Provider credential cleanup after committed replacement failed code={}",
                        storeFailureCode(cleanupFailure));
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    LOGGER.debug("Credential deletion deferred after database rollback");
                }
            }
        });
    }

    private void scheduleStoreRollback(AiProvider provider, String newRef, String oldRef, String oldSecret) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        UUID providerId = provider.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                try {
                    if (oldRef != null && oldSecret != null && Objects.equals(newRef, oldRef)) {
                        providerCredentialStore.writeAndVerify(providerId, oldSecret);
                    } else if (newRef != null && !newRef.isBlank()) {
                        providerCredentialStore.delete(newRef);
                    }
                } catch (RuntimeException cleanupFailure) {
                    LOGGER.warn("Provider credential transaction rollback incomplete code={} provider={}",
                        storeFailureCode(cleanupFailure), providerId);
                }
            }
        });
    }

    private void deleteCredentialIfPresent(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return;
        try {
            providerCredentialStore.delete(secretRef);
        } catch (ProviderCredentialStoreException failure) {
            throw new AppException(
                "SECRET_CLEANUP_FAILED",
                "安全凭据清理失败，模型配置保留未删除。",
                HttpStatus.CONFLICT
            );
        } catch (RuntimeException failure) {
            throw new AppException(
                "SECRET_CLEANUP_FAILED",
                "安全凭据清理失败，模型配置保留未删除。",
                HttpStatus.CONFLICT
            );
        }
    }

    private AppException credentialAppException(String code, boolean cleanup) {
        String safeCode = code == null || code.isBlank()
            ? (cleanup ? "SECRET_CLEANUP_FAILED" : "SECRET_STORE_UNAVAILABLE") : code;
        return new AppException(
            safeCode,
            cleanup ? "安全凭据清理失败，请稍后重试。" : "安全凭据存储不可用，模型配置未保存。",
            cleanup ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private String storeFailureCode(Throwable failure) {
        return failure instanceof ProviderCredentialStoreException exception
            ? exception.code() : "SECRET_CLEANUP_FAILED";
    }

    private String credentialStatus(AiProvider provider) {
        if (provider.getSecretRef() == null || provider.getSecretRef().isBlank()) {
            return provider.getApiKey() == null || provider.getApiKey().isBlank() ? "MISSING" : "LEGACY_PLAINTEXT";
        }
        try {
            return providerCredentialStore.status(provider.getSecretRef()).name();
        } catch (RuntimeException failure) {
            return "UNAVAILABLE";
        }
    }

    private AiProviderResponse mockProvider() {
        return new AiProviderResponse(
            null,
            "Mock Provider",
            "mock://local",
            "projectflow-mock",
            AiProviderType.MOCK,
            ModelProtocol.OPENAI_CHAT_COMPLETIONS,
            null,
            AiProviderAuthMode.NONE,
            null,
            null,
            List.of(),
            30,
            true,
            true,
            false,
            false,
            false,
            0.2,
            2048,
            true,
            List.of("项目分析", "材料解析", "成果生成"),
            false,
            "MISSING",
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String canonicalBaseUrl(String baseUrl, ModelProtocol protocol) {
        return aiProviderUrlGuard.sdkBaseUrl(normalizeBaseUrl(baseUrl), protocol, null);
    }

    private void configureProtocol(AiProvider provider, AiProviderRequest request) {
        ModelProtocol protocol = request.protocol() == null ? defaultProtocol(request.type()) : request.protocol();
        AiProviderAuthMode authMode = request.authMode() == null ? AiProviderAuthMode.PROTOCOL_DEFAULT : request.authMode();
        if (authMode == AiProviderAuthMode.API_KEY_HEADER || authMode == AiProviderAuthMode.QUERY_API_KEY
            || authMode == AiProviderAuthMode.NONE) {
            aiProviderUrlGuard.endpointUri(request.baseUrl(), protocol, blankToNull(request.endpointOverride()));
        } else {
            aiProviderUrlGuard.sdkBaseUrl(request.baseUrl(), protocol, blankToNull(request.endpointOverride()));
        }
        // Validate both new input and an existing row. A legacy row can carry
        // unsafe JSON in safe_headers, so treating null request headers as
        // already trusted would create an adapter-level credential bypass.
        Map<String, String> headers = request.safeHeaders() == null
            ? validateSafeHeaders(provider.getSafeHeaders()) : validateSafeHeaders(request.safeHeaders());
        String credentialHeaderName = validateCredentialHeaderName(request.authHeaderName());
        String credentialQueryName = validateCredentialQueryName(request.queryKeyName());
        provider.configureProtocol(
            protocol, request.endpointOverride(), request.authMode(), credentialHeaderName, credentialQueryName, headers,
            request.requestTimeoutSeconds(), request.supportsTemperature(), request.supportsJsonMode(),
            request.supportsStructuredOutput(), request.supportsReasoning(), request.supportsReasoningControl()
        );
    }

    private Map<String, String> validateSafeHeaders(Map<String, String> submitted) {
        try {
            return AiProviderHeaderPolicy.requireSafe(submitted);
        } catch (IllegalArgumentException invalid) {
            throw new AppException("AI_PROVIDER_HEADER_BLOCKED", "自定义请求头不允许包含凭据或受保护字段。", HttpStatus.BAD_REQUEST);
        }
    }

    private String validateCredentialHeaderName(String submitted) {
        try {
            return AiProviderHeaderPolicy.requireCredentialHeaderName(submitted);
        } catch (IllegalArgumentException invalid) {
            throw new AppException("AI_PROVIDER_HEADER_BLOCKED", "凭据请求头名称无效。", HttpStatus.BAD_REQUEST);
        }
    }

    private String validateCredentialQueryName(String submitted) {
        try {
            return AiProviderHeaderPolicy.requireCredentialQueryName(submitted);
        } catch (IllegalArgumentException invalid) {
            throw new AppException("AI_PROVIDER_HEADER_BLOCKED", "凭据查询参数名称无效。", HttpStatus.BAD_REQUEST);
        }
    }

    private ProviderTestResponse testResult(
        AiProvider provider,
        boolean ok,
        boolean connectionPassed,
        String message,
        String compatibility,
        int requestsMade,
        List<String> warnings,
        String usage,
        String outputLimitDetection
    ) {
        var capabilities = capabilityRegistry.resolve(provider);
        ProviderCompatibilityProfile profile = new ProviderCompatibilityProfile(
            connectionPassed ? "PASSED" : "FAILED", provider.getProtocol(), provider.getAuthMode(),
            connectionPassed ? "PASSED" : "FAILED",
            capabilities.supportsStructuredOutput() ? "SUPPORTED" : "FALLBACK",
            capabilities.supportsJsonMode() ? "SUPPORTED" : "UNKNOWN",
            capabilities.supportsTemperature() ? "SUPPORTED" : "OMITTED",
            capabilities.supportsReasoning() ? "SUPPORTED" : "NOT_DETECTED",
            usage, outputLimitDetection, compatibility, warnings, requestsMade
        );
        try {
            provider.recordProbeProfile(objectMapper.writeValueAsString(profile));
            aiProviderRepository.save(provider);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize provider probe profile", exception);
        }
        return new ProviderTestResponse(ok, provider.getName(), message, profile);
    }

    private String usage(ModelGatewayService.ModelCallDiagnostics diagnostics) {
        return "ACTUAL".equals(diagnostics.usageSource()) ? "ACTUAL" : "UNAVAILABLE";
    }

    private int requestCount(Exception exception) {
        if (exception instanceof ModelGatewayService.ModelResponseFormatException format && format.diagnostics() != null) {
            return Math.max(1, format.diagnostics().requestCount());
        }
        if (exception instanceof ModelGatewayService.ModelHttpException http) return http.requestCount();
        if (exception instanceof ModelGatewayService.ModelTransportException transport) return transport.requestCount();
        return 1;
    }

    private boolean requestReachedProvider(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ModelGatewayService.ModelResponseFormatException format
                && format.diagnostics() != null) {
                return format.diagnostics().requestSucceeded();
            }
            if (current instanceof ModelGatewayService.ModelHttpException
                || current instanceof ModelGatewayService.ModelTransportException) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<String> capabilitiesWarnings(AiProvider provider) {
        var capabilities = capabilityRegistry.resolve(provider);
        List<String> warnings = new ArrayList<>();
        if (!capabilities.supportsStructuredOutput()) warnings.add("原生 Structured Output 未声明，使用 JSON/Prompt 约束与 Schema 校验。 ");
        if (!capabilities.supportsJsonMode()) warnings.add("JSON Mode 未确认，ProjectFlow 将使用 Prompt 约束与恢复管线。 ");
        warnings.add("输出上限识别由协议契约验证；本次在线探测未主动制造截断。 ");
        return warnings.stream().map(String::trim).toList();
    }

    private ModelProtocol defaultProtocol(AiProviderType type) {
        if (type == AiProviderType.OPENAI) return ModelProtocol.OPENAI_RESPONSES;
        if (type == AiProviderType.ANTHROPIC) return ModelProtocol.ANTHROPIC_MESSAGES;
        return ModelProtocol.OPENAI_CHAT_COMPLETIONS;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
