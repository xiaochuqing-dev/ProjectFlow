package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProviderCredentialStoreTest {
    @Test
    void inMemoryStoreWritesReadsAndDeletesWithoutPuttingSecretInReference() {
        InMemoryProviderCredentialStore store = new InMemoryProviderCredentialStore();
        UUID providerId = UUID.randomUUID();

        String ref = store.writeAndVerify(providerId, "credential-sentinel");

        assertThat(ref).startsWith("memory:v1:" ).doesNotContain("credential-sentinel");
        assertThat(store.status(ref)).isEqualTo(ProviderCredentialStore.Status.CONFIGURED);
        assertThat(store.read(ref)).isEqualTo("credential-sentinel");
        store.delete(ref);
        assertThat(store.status(ref)).isEqualTo(ProviderCredentialStore.Status.MISSING);
        assertThatThrownBy(() -> store.read(ref))
            .isInstanceOf(ProviderCredentialStoreException.class)
            .hasMessageNotContaining("credential-sentinel");
    }

    @Test
    void unavailableStoreNeverFallsBackToPlaintext() {
        ProviderCredentialStore store = new UnavailableProviderCredentialStore();

        assertThat(store.available()).isFalse();
        assertThat(store.status("win-dpapi:user:v1:missing")).isEqualTo(ProviderCredentialStore.Status.UNAVAILABLE);
        assertThatThrownBy(() -> store.writeAndVerify(UUID.randomUUID(), "credential-sentinel"))
            .isInstanceOf(ProviderCredentialStoreException.class)
            .hasMessageNotContaining("credential-sentinel");
    }
}
