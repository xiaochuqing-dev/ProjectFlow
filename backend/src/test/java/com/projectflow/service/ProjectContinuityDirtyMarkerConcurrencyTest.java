package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.projectflow.entity.ProjectHistorySnapshot;
import com.projectflow.repository.ProjectHistorySnapshotRepository;

@SpringBootTest
@ActiveProfiles("test")
class ProjectContinuityDirtyMarkerConcurrencyTest {
    private static final int WRITE_COUNT = 8;

    @Autowired ProjectContinuityDirtyMarker marker;
    @Autowired ProjectHistorySnapshotRepository snapshotRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        ProjectHistorySnapshot snapshot = new ProjectHistorySnapshot(projectId);
        snapshot.complete(
            "revision-1", "fingerprint-1", 1, Instant.EPOCH, Instant.EPOCH, "strategy", "prompt",
            "{}", "[]", "[]", "[]", "{}", "{}", UUID.randomUUID(), false
        );
        snapshotRepository.saveAndFlush(snapshot);
        jdbcTemplate.update(
            "update project_history_snapshots set continuity_dirty_generation = null where project_id = ?",
            projectId
        );
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            snapshotRepository.deleteByProjectId(projectId)
        );
    }

    @Test
    void concurrentSameTargetWritesAreSerializedWithoutLosingTheLatestGeneration() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(WRITE_COUNT);
        CountDownLatch ready = new CountDownLatch(WRITE_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < WRITE_COUNT; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(20, TimeUnit.SECONDS)).isTrue();
                    return marker.mark(projectId, "HISTORY_CORRECTION", "correction:same");
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> revisions = new ArrayList<>();
            for (Future<String> future : futures) {
                revisions.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(revisions).doesNotContain("").doesNotHaveDuplicates();

            String latest = snapshotRepository.findByProjectId(projectId).orElseThrow()
                .getContinuityDirtyRevision();
            assertThat(snapshotRepository.findByProjectId(projectId).orElseThrow().getContinuityDirtyGeneration())
                .isEqualTo(WRITE_COUNT);
            assertThat(revisions).contains(latest);
            String staleObserved = revisions.stream().filter(value -> !value.equals(latest)).findFirst().orElseThrow();

            assertThat(acknowledge(staleObserved)).isFalse();
            assertThat(snapshotRepository.findByProjectId(projectId).orElseThrow().getContinuityDirtyRevision())
                .isEqualTo(latest);
            assertThat(acknowledge(latest)).isTrue();
            assertThat(snapshotRepository.findByProjectId(projectId).orElseThrow().getContinuityDirtyRevision())
                .isBlank();

            String afterAcknowledgement = marker.mark(
                projectId, "HISTORY_CORRECTION", "correction:same"
            );
            assertThat(revisions).doesNotContain(afterAcknowledgement);
            assertThat(snapshotRepository.findByProjectId(projectId).orElseThrow().getContinuityDirtyGeneration())
                .isEqualTo(WRITE_COUNT + 1L);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean acknowledge(String observedRevision) {
        Boolean acknowledged = new TransactionTemplate(transactionManager).execute(status -> {
            ProjectHistorySnapshot snapshot = snapshotRepository.findLockedByProjectId(projectId).orElseThrow();
            boolean result = snapshot.acknowledgeContinuityDirty(observedRevision);
            snapshotRepository.save(snapshot);
            return result;
        });
        return Boolean.TRUE.equals(acknowledged);
    }
}
