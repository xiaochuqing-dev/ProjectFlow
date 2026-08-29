package com.projectflow.config;

import java.nio.file.Path;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.projectflow.service.InMemoryProviderCredentialStore;
import com.projectflow.service.ProviderCredentialStore;
import com.projectflow.service.UnavailableProviderCredentialStore;
import com.projectflow.service.WindowsDpapiProviderCredentialStore;

/** Selects a credential store explicitly; production never silently uses memory. */
@Configuration
public class ProviderCredentialStoreConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProviderCredentialStore.class)
    ProviderCredentialStore providerCredentialStore(
        Environment environment,
        @Value("${projectflow.credentials.store:auto}") String configuredStore,
        @Value("${projectflow.storage.config-dir:${PROJECTFLOW_CONFIG_DIR:./.projectflow/config}}") String configDir
    ) {
        boolean testProfile = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equalsIgnoreCase("test")
                || profile.equalsIgnoreCase("ci")
                || profile.equalsIgnoreCase("protected-provider"));
        if ("in-memory".equalsIgnoreCase(configuredStore)) {
            if (!testProfile) {
                return new UnavailableProviderCredentialStore();
            }
            return new InMemoryProviderCredentialStore();
        }
        if (testProfile) return new InMemoryProviderCredentialStore();
        if (WindowsDpapiProviderCredentialStore.isWindows()) {
            return new WindowsDpapiProviderCredentialStore(Path.of(configDir));
        }
        return new UnavailableProviderCredentialStore();
    }
}
