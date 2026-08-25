package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;
import com.projectflow.entity.ModelProtocol;

import jakarta.persistence.LockModeType;

public interface AiProviderRepository extends JpaRepository<AiProvider, UUID> {
    List<AiProvider> findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(UUID userId);

    Optional<AiProvider> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Mutation paths must serialize credential-store changes with row deletion
     * and concurrent replacement. The ordinary read method remains unlocked
     * because provider probes can run without a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select provider from AiProvider provider where provider.id = :id and provider.userId = :userId")
    Optional<AiProvider> findLockedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<AiProvider> findByUserIdAndTypeAndBaseUrlAndModelName(UUID userId, AiProviderType type, String baseUrl, String modelName);

    Optional<AiProvider> findByUserIdAndTypeAndBaseUrlAndModelNameAndProtocol(
        UUID userId, AiProviderType type, String baseUrl, String modelName, ModelProtocol protocol
    );

    Optional<AiProvider> findFirstByUserIdAndDefaultEnabledTrueOrderByUpdatedAtDesc(UUID userId);
}
