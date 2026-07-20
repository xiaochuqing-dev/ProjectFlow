package com.projectflow.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.repository.AiProviderRepository;

@Service
public class AiProviderProtocolMigrationService {
    private final AiProviderRepository repository;

    public AiProviderProtocolMigrationService(AiProviderRepository repository) {
        this.repository = repository;
    }

    @Order(20)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateOnStartup() {
        repository.findAll().stream().filter(provider -> provider.migrateProtocolDefaults()).forEach(repository::save);
    }
}
