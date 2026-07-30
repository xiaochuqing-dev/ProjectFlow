package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.projectflow.entity.ProjectAgentCandidate;
import com.projectflow.entity.ProjectFactEpistemicStatus;

class AgentCandidateWriteTest {
    @Test
    void candidateEntityRejectsStrongFactStatusesAtItsWriteBoundary() {
        for (ProjectFactEpistemicStatus status : List.of(
            ProjectFactEpistemicStatus.OBSERVED,
            ProjectFactEpistemicStatus.VERIFIED
        )) {
            assertThatThrownBy(() -> new ProjectAgentCandidate(
                UUID.randomUUID(), "ASSERTION", "未经工程验证的 Agent 断言", status,
                List.of(), "UNKNOWN", "", List.of(), "test-agent"
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
