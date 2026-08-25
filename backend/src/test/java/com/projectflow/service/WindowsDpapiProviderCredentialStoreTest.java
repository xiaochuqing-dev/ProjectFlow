package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class WindowsDpapiProviderCredentialStoreTest {
    @Test
    void roundTripsAndDeletesCurrentUserProtectedCredential() throws Exception {
        Path root = Files.createTempDirectory("projectflow-dpapi-test-");
        try {
            WindowsDpapiProviderCredentialStore store = new WindowsDpapiProviderCredentialStore(root);
            UUID providerId = UUID.randomUUID();
            String sentinel = "projectflow-dpapi-non-provider-sentinel";

            String reference = store.writeAndVerify(providerId, sentinel);

            assertThat(reference).startsWith("win-dpapi:user:v1:");
            assertThat(store.status(reference)).isEqualTo(ProviderCredentialStore.Status.CONFIGURED);
            assertThat(store.read(reference)).isEqualTo(sentinel);

            store.delete(reference);

            assertThat(store.status(reference)).isEqualTo(ProviderCredentialStore.Status.MISSING);
            assertThatThrownBy(() -> store.read(reference))
                .isInstanceOf(ProviderCredentialStoreException.class)
                .hasMessageNotContaining(sentinel);
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Best-effort cleanup of an isolated non-provider test artifact.
                    }
                });
            }
        }
    }
}
