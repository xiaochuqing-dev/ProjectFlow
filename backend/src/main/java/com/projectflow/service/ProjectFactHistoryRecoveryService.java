package com.projectflow.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ProjectFactHistoryStatus;
import com.projectflow.repository.ProjectFactHistoryStateRepository;

@Service
public class ProjectFactHistoryRecoveryService {
    private final ProjectFactHistoryStateRepository stateRepository;

    public ProjectFactHistoryRecoveryService(ProjectFactHistoryStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(200)
    @Transactional
    public void pauseUnknownRunningChunks() {
        stateRepository.findAll().stream()
            .filter(state -> state.getStatus() == ProjectFactHistoryStatus.RUNNING)
            .forEach(state -> {
                state.markPaused(
                    "RESTART_CHECKPOINT",
                    "服务重启后已保留完成的事实；模型请求状态未知，不自动重发本批，可安全重试并从未覆盖提交继续。"
                );
                stateRepository.save(state);
            });
    }
}
