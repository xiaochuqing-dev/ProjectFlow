package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.repository.AiProviderRepository;

class ProviderCredentialMigrationCoordinatorTest {
    @Test
    void startupMigrationIsIdempotentAndLeavesNoLegacyPlaintext() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        ProviderCredentialStore store = new InMemoryProviderCredentialStore();
        AiProviderService providerService = mock(AiProviderService.class);
        AiProvider provider = provider("legacy-sentinel");
        when(repository.findAll()).thenReturn(List.of(provider));
        doAnswer(ignored -> {
            provider.setSecretRef("test:" + provider.getId());
            provider.clearLegacyApiKey();
            return true;
        }).when(providerService).migrateLegacyCredential(provider.getUserId(), provider.getId());

        new ProviderCredentialMigrationCoordinator(repository, store, providerService).run(null);

        assertThat(provider.getApiKey()).isNull();
        assertThat(provider.getSecretRef()).isEqualTo("test:" + provider.getId());
        verify(providerService).migrateLegacyCredential(provider.getUserId(), provider.getId());

        new ProviderCredentialMigrationCoordinator(repository, store, providerService).run(null);
        verify(providerService).migrateLegacyCredential(provider.getUserId(), provider.getId());
    }

    @Test
    void legacyRowsFailStartupWhenStoreIsUnavailable() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        ProviderCredentialStore store = new UnavailableProviderCredentialStore();
        AiProviderService providerService = mock(AiProviderService.class);
        AiProvider provider = provider("legacy-sentinel");
        when(repository.findAll()).thenReturn(List.of(provider));

        assertThatThrownBy(() -> new ProviderCredentialMigrationCoordinator(repository, store, providerService).run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SECRET_MIGRATION_FAILED")
            .hasMessageNotContaining("legacy-sentinel");
        verify(providerService, never()).migrateLegacyCredential(provider.getUserId(), provider.getId());
    }

    @Test
    void startupMigrationDoesNotIgnoreRowsContainingBothPlaintextAndAReference() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        ProviderCredentialStore store = new InMemoryProviderCredentialStore();
        AiProviderService providerService = mock(AiProviderService.class);
        AiProvider provider = provider("legacy-sentinel");
        provider.setSecretRef("existing-reference");
        when(repository.findAll()).thenReturn(List.of(provider));
        doAnswer(ignored -> {
            provider.clearLegacyApiKey();
            return true;
        }).when(providerService).migrateLegacyCredential(provider.getUserId(), provider.getId());

        new ProviderCredentialMigrationCoordinator(repository, store, providerService).run(null);

        verify(providerService).migrateLegacyCredential(provider.getUserId(), provider.getId());
        assertThat(provider.getApiKey()).isNull();
    }

    @Test
    void noLegacyRowsAllowDegradedStartupWhenStoreIsUnavailable() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        when(repository.findAll()).thenReturn(List.of(provider(null)));
        AiProviderService providerService = mock(AiProviderService.class);

        new ProviderCredentialMigrationCoordinator(
            repository,
            new UnavailableProviderCredentialStore(),
            providerService
        ).run(null);

        verify(providerService, never()).migrateLegacyCredential(
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.any(UUID.class)
        );
    }

    private AiProvider provider(String secret) {
        AiProvider provider = new AiProvider(UUID.randomUUID());
        provider.update("Provider", "https://api.deepseek.com", secret, "deepseek-chat", AiProviderType.OPENAI,
            0.1, 2048, false, List.of("TEST"));
        return provider;
    }
}
