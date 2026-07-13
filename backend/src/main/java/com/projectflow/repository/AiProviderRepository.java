package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderType;

public interface AiProviderRepository extends JpaRepository<AiProvider, UUID> {
    List<AiProvider> findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(UUID userId);

    Optional<AiProvider> findByIdAndUserId(UUID id, UUID userId);

    Optional<AiProvider> findByUserIdAndTypeAndBaseUrlAndModelName(UUID userId, AiProviderType type, String baseUrl, String modelName);

    Optional<AiProvider> findFirstByUserIdAndDefaultEnabledTrueOrderByUpdatedAtDesc(UUID userId);
}
