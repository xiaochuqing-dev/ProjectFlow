package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projectflow.entity.AiProvider;

public interface AiProviderRepository extends JpaRepository<AiProvider, UUID> {
    List<AiProvider> findByUserIdOrderByDefaultEnabledDescUpdatedAtDesc(UUID userId);

    Optional<AiProvider> findByIdAndUserId(UUID id, UUID userId);
}
