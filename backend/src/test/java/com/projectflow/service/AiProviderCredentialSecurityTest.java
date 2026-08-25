package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.dto.AiProviderDtos.AiProviderRequest;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.support.AppException;

class AiProviderCredentialSecurityTest {
    private final UUID userId = UUID.randomUUID();

    @Test
    void updateMigratesLegacyPlaintextAfterStoreReadbackAndClearsEntityColumn() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RecordingStore store = new RecordingStore();
        AiProvider provider = provider("legacy-sentinel");
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.saveAndFlush(provider)).thenReturn(provider);

        var response = service(repository, store).update(userId, provider.getId(), request("", Map.of()));

        assertThat(provider.getApiKey()).isNull();
        assertThat(provider.getSecretRef()).isEqualTo("memory:test:" + provider.getId());
        assertThat(store.values.get(provider.getSecretRef())).isEqualTo("legacy-sentinel");
        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(response.credentialStatus()).isEqualTo("CONFIGURED");
        verify(repository).saveAndFlush(provider);
    }

    @Test
    void migrationAlsoClearsPlaintextWhenAPartialRowAlreadyHasASecretReference() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RecordingStore store = new RecordingStore();
        AiProvider provider = provider("legacy-sentinel");
        String oldRef = store.writeAndVerify(provider.getId(), "old-sentinel");
        provider.setSecretRef(oldRef);
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.saveAndFlush(provider)).thenReturn(provider);

        assertThat(service(repository, store).migrateLegacyCredential(userId, provider.getId())).isTrue();

        assertThat(provider.getApiKey()).isNull();
        assertThat(provider.getSecretRef()).isEqualTo(oldRef);
        assertThat(store.values.get(oldRef)).isEqualTo("legacy-sentinel");
        verify(repository).saveAndFlush(provider);
    }

    @Test
    void failedLegacyStoreDoesNotClearDatabaseValueOrPersistProvider() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        ProviderCredentialStore store = new FailingStore();
        AiProvider provider = provider("legacy-sentinel");
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service(repository, store).update(userId, provider.getId(), request("", Map.of())))
            .isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode()).isEqualTo("SECRET_STORE_UNAVAILABLE"));

        assertThat(provider.getApiKey()).isEqualTo("legacy-sentinel");
        assertThat(provider.getSecretRef()).isNull();
        verify(repository, never()).saveAndFlush(any(AiProvider.class));
    }

    @Test
    void databaseFailureAfterSecretWriteCleansNewSecretAndRestoresLegacyColumns() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RecordingStore store = new RecordingStore();
        AiProvider provider = provider("old-sentinel");
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.saveAndFlush(provider)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service(repository, store).update(
            userId, provider.getId(), request("new-sentinel", Map.of())))
            .isInstanceOf(IllegalStateException.class);

        assertThat(provider.getApiKey()).isEqualTo("old-sentinel");
        assertThat(provider.getSecretRef()).isNull();
        assertThat(store.values).isEmpty();
    }

    @Test
    void rotatingStoreReferencesAreRemovedOnDatabaseFailureWithoutDeletingTheOldReference() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RotatingStore store = new RotatingStore();
        AiProvider provider = provider(null);
        String oldRef = store.writeAndVerify(provider.getId(), "old-sentinel");
        provider.updateWithSecretRef(
            provider.getName(), provider.getBaseUrl(), oldRef, provider.getModelName(), provider.getType(),
            provider.getTemperature(), provider.getMaxTokens(), false, provider.getPurposeTags()
        );
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.saveAndFlush(provider)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service(repository, store).update(
            userId, provider.getId(), request("new-sentinel", Map.of())))
            .isInstanceOf(IllegalStateException.class);

        assertThat(store.values).containsOnlyKeys(oldRef);
        assertThat(store.values.get(oldRef)).isEqualTo("old-sentinel");
    }

    @Test
    void failedCredentialReplacementRestoresThePreviouslyCommittedSecret() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        FailingReplaceStore store = new FailingReplaceStore();
        AiProvider provider = provider(null);
        String ref = store.writeAndVerify(provider.getId(), "old-sentinel");
        provider.updateWithSecretRef(
            provider.getName(), provider.getBaseUrl(), ref, provider.getModelName(), provider.getType(),
            provider.getTemperature(), provider.getMaxTokens(), false, provider.getPurposeTags()
        );
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service(repository, store).update(
            userId, provider.getId(), request("new-sentinel", Map.of())))
            .isInstanceOf(AppException.class);

        assertThat(provider.getSecretRef()).isEqualTo(ref);
        assertThat(store.values.get(ref)).isEqualTo("old-sentinel");
        verify(repository, never()).saveAndFlush(any(AiProvider.class));
    }

    @Test
    void transactionRollbackRemovesNewSecretWrittenBeforeDatabaseCommit() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RecordingStore store = new RecordingStore();
        AiProvider provider = provider(null);
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.saveAndFlush(provider)).thenReturn(provider);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service(repository, store).update(userId, provider.getId(), request("new-sentinel", Map.of()));
            assertThat(store.values).containsKey("memory:test:" + provider.getId());
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(store.values).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void transactionCommitDeletesClearedSecretOnlyAfterCommitCallback() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RecordingStore store = new RecordingStore();
        AiProvider provider = provider(null);
        String ref = store.writeAndVerify(provider.getId(), "old-sentinel");
        provider.updateWithSecretRef(
            provider.getName(), provider.getBaseUrl(), ref, provider.getModelName(), provider.getType(),
            provider.getTemperature(), provider.getMaxTokens(), false, provider.getPurposeTags()
        );
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.saveAndFlush(provider)).thenReturn(provider);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service(repository, store).update(userId, provider.getId(), request("", true, Map.of()));
            assertThat(store.values).containsKey(ref);
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(sync -> sync.afterCommit());
            synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertThat(store.values).doesNotContainKey(ref);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteCleanupFailureIsReportedWithoutExposingCredentialMaterial() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        ProviderCredentialStore store = new FailingDeleteStore();
        AiProvider provider = provider(null);
        provider.updateWithSecretRef(
            provider.getName(), provider.getBaseUrl(), "memory:test:" + provider.getId(), provider.getModelName(),
            provider.getType(), provider.getTemperature(), provider.getMaxTokens(), false, provider.getPurposeTags()
        );
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider));

        assertThatThrownBy(() -> service(repository, store).delete(userId, provider.getId()))
            .isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode()).isEqualTo("SECRET_CLEANUP_FAILED"));
        verify(repository).delete(provider);
        verify(repository).flush();
        assertThat(provider.getSecretRef()).isNotBlank();
    }

    @Test
    void providerDeleteFlushesMetadataInTransactionAndCleansSecretAfterCommit() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        RecordingStore store = new RecordingStore();
        AiProvider provider = provider(null);
        String ref = store.writeAndVerify(provider.getId(), "delete-sentinel");
        provider.updateWithSecretRef(
            provider.getName(), provider.getBaseUrl(), ref, provider.getModelName(), provider.getType(),
            provider.getTemperature(), provider.getMaxTokens(), false, provider.getPurposeTags()
        );
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(userId)).thenReturn(List.of(provider));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service(repository, store).delete(userId, provider.getId());
            verify(repository).delete(provider);
            verify(repository).flush();
            assertThat(store.values).containsKey(ref);
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(sync -> sync.afterCommit());
            synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertThat(store.values).doesNotContainKey(ref);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void credentialLikeSafeHeadersAreRejected() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        AiProvider provider = provider(null);
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));

        for (String header : List.of("X-Api-Key", "X-OpenAI-Key", "X-Auth-Token", "Cookie", "Authorization")) {
            assertThatThrownBy(() -> service(repository, new RecordingStore()).update(
                userId, provider.getId(), request("", Map.of(header, "sentinel"))))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).getCode()).isEqualTo("AI_PROVIDER_HEADER_BLOCKED"));
        }
        assertThatThrownBy(() -> service(repository, new RecordingStore()).update(
            userId, provider.getId(), request("", Map.of("X-Provider-Option", "sk-sensitive-value-1234567890"))))
            .isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode()).isEqualTo("AI_PROVIDER_HEADER_BLOCKED"));
        assertThatThrownBy(() -> AiProviderHeaderPolicy.requireSafe(Map.of("X-Provider-Option", "api_key=opaque-secret-value")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiProviderHeaderPolicy.requireCredentialHeaderName("X-Bad Header"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiProviderHeaderPolicy.requireCredentialQueryName("api key"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any(AiProvider.class));
    }

    @Test
    void persistedUnsafeHeaderValuesCannotBypassPolicyWhenRequestOmitsHeaderMap() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        AiProvider provider = provider(null);
        provider.configureProtocol(
            ModelProtocol.OPENAI_CHAT_COMPLETIONS, null, AiProviderAuthMode.PROTOCOL_DEFAULT,
            null, null, Map.of("X-Provider-Option", "api_key=legacy-secret"), 60,
            true, true, true, false, false
        );
        when(repository.findByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));
        when(repository.findLockedByIdAndUserId(provider.getId(), userId)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> service(repository, new RecordingStore()).update(
            userId, provider.getId(), request("", null)))
            .isInstanceOf(AppException.class)
            .satisfies(error -> assertThat(((AppException) error).getCode()).isEqualTo("AI_PROVIDER_HEADER_BLOCKED"));
        verify(repository, never()).saveAndFlush(any(AiProvider.class));
    }

    private AiProviderService service(AiProviderRepository repository, ProviderCredentialStore store) {
        return new AiProviderService(
            repository,
            new AiProviderUrlGuard(),
            mock(ModelGatewayService.class),
            mock(ApplicationEventPublisher.class),
            new ModelCapabilityRegistry(),
            new ObjectMapper().findAndRegisterModules(),
            store
        );
    }

    private AiProvider provider(String apiKey) {
        AiProvider provider = new AiProvider(userId);
        provider.update(
            "Test Provider", "https://api.deepseek.com", apiKey, "deepseek-chat", AiProviderType.OPENAI,
            0.1, 2048, false, List.of("TEST")
        );
        provider.configureProtocol(
            ModelProtocol.OPENAI_CHAT_COMPLETIONS, null, AiProviderAuthMode.PROTOCOL_DEFAULT,
            null, null, Map.of(), 60, true, true, true, false, false
        );
        return provider;
    }

    private AiProviderRequest request(String apiKey, Map<String, String> safeHeaders) {
        return request(apiKey, false, safeHeaders);
    }

    private AiProviderRequest request(String apiKey, boolean clearApiKey, Map<String, String> safeHeaders) {
        return new AiProviderRequest(
            "Test Provider", "https://api.deepseek.com", apiKey, "deepseek-chat", AiProviderType.OPENAI,
            0.1, 2048, false, List.of("TEST"), clearApiKey, ModelProtocol.OPENAI_CHAT_COMPLETIONS, null,
            AiProviderAuthMode.PROTOCOL_DEFAULT, null, null, safeHeaders, 60, true, true, true, false, false
        );
    }

    private static class RecordingStore implements ProviderCredentialStore {
        protected final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public String writeAndVerify(UUID providerId, String secret) {
            String ref = "memory:test:" + providerId;
            values.put(ref, secret);
            return ref;
        }

        @Override
        public String read(String secretRef) {
            return values.get(secretRef);
        }

        @Override
        public void delete(String secretRef) {
            values.remove(secretRef);
        }

        @Override
        public Status status(String secretRef) {
            return values.containsKey(secretRef) ? Status.CONFIGURED : Status.MISSING;
        }
    }

    private static final class RotatingStore extends RecordingStore {
        @Override
        public String writeAndVerify(UUID providerId, String secret) {
            String ref = "memory:rotating:" + UUID.randomUUID();
            values.put(ref, secret);
            return ref;
        }
    }

    private static final class FailingStore extends RecordingStore {
        @Override
        public String writeAndVerify(UUID providerId, String secret) {
            throw new ProviderCredentialStoreException("SECRET_STORE_UNAVAILABLE", "store unavailable");
        }
    }

    private static final class FailingDeleteStore extends RecordingStore {
        @Override
        public void delete(String secretRef) {
            throw new ProviderCredentialStoreException("SECRET_STORE_UNAVAILABLE", "store unavailable");
        }
    }

    private static final class FailingReplaceStore extends RecordingStore {
        private int writes;

        @Override
        public String writeAndVerify(UUID providerId, String secret) {
            String ref = super.writeAndVerify(providerId, secret);
            writes++;
            if (writes == 2) {
                throw new ProviderCredentialStoreException("SECRET_STORE_VERIFY_FAILED", "replacement failed");
            }
            return ref;
        }
    }
}
