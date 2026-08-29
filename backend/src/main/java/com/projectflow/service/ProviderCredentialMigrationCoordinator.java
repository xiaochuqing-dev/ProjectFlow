package com.projectflow.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.projectflow.entity.AiProvider;
import com.projectflow.repository.AiProviderRepository;

/**
 * Runs after JPA/Flyway initialization and before ApplicationReadyEvent. A
 * legacy plaintext row is a startup failure unless the secure store can
 * complete the write/read-back/database-clear sequence.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public final class ProviderCredentialMigrationCoordinator implements ApplicationRunner {
    private final AiProviderRepository repository;
    private final ProviderCredentialStore store;
    private final AiProviderService providerService;

    public ProviderCredentialMigrationCoordinator(
        AiProviderRepository repository,
        ProviderCredentialStore store,
        AiProviderService providerService
    ) {
        this.repository = repository;
        this.store = store;
        this.providerService = providerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AiProvider> legacyProviders = repository.findAll().stream()
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .toList();
        if (legacyProviders.isEmpty()) return;
        if (!store.available()) {
            throw new IllegalStateException("SECRET_MIGRATION_FAILED: secure credential store unavailable");
        }

        for (AiProvider provider : legacyProviders) {
            try {
                providerService.migrateLegacyCredential(provider.getUserId(), provider.getId());
            } catch (RuntimeException failure) {
                throw new IllegalStateException(
                    "SECRET_MIGRATION_FAILED: secure credential migration did not commit",
                    failure
                );
            }
        }

        long remaining = repository.findAll().stream()
            .filter(provider -> provider.getApiKey() != null && !provider.getApiKey().isBlank())
            .count();
        if (remaining > 0) {
            throw new IllegalStateException("SECRET_MIGRATION_FAILED: legacy credential rows remain");
        }
    }

}
