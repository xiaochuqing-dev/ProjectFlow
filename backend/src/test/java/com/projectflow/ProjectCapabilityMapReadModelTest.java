package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityEvolutionType;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectCapabilityMapState;
import com.projectflow.entity.ProjectCapabilityMaturity;
import com.projectflow.entity.ProjectCapabilityRelationRole;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectSpace;
import com.projectflow.entity.ProjectStatus;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityMapStateRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.service.ProjectCapabilityQueryService;
import com.projectflow.support.AppException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectCapabilityMapReadModelTest {
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectFactRepository factRepository;
    @Autowired ProjectCapabilityRepository capabilityRepository;
    @Autowired ProjectCapabilityEvolutionRepository evolutionRepository;
    @Autowired ProjectCapabilityFactRepository relationRepository;
    @Autowired ProjectCapabilityMapStateRepository stateRepository;
    @Autowired ProjectCapabilityQueryService queryService;

    @Test
    void overviewListAndDetailUseStableLongLivedCapabilityData() {
        UUID userId = UUID.randomUUID();
        ProjectSpace project = project(userId, "能力地图");
        ProjectFact fact = fact(project.getId());
        ProjectCapability capability = capability(project.getId(), "模型可靠性治理", "identity-a");
        ProjectCapabilityEvolution evolution = new ProjectCapabilityEvolution(
            project.getId(), capability.getId(), ProjectCapabilityEvolutionType.NEW_CAPABILITY, 0, 1,
            "形成统一模型治理", "统一模型入口和失败保护", fact.getTimelineEventAt(), "e".repeat(64)
        );
        evolution.attachSourceStats(1, 1, List.of("2026-07"), UUID.randomUUID(), "fixed", "fixed");
        evolution = evolutionRepository.saveAndFlush(evolution);
        relationRepository.saveAndFlush(new ProjectCapabilityFact(
            project.getId(), capability.getId(), fact.getId(), ProjectCapabilityRelationRole.FORMATION, evolution.getId()
        ));
        capability.updateStatistics(1, 1, 1, 2, 0, 1, ProjectCapabilityMaturity.FORMING,
            "由 1 条事实、1 个批次、1 个提交、1 次演进和 0 天跨度支持", fact.getTimelineEventAt(), fact.getTimelineEventAt());
        capabilityRepository.saveAndFlush(capability);
        ProjectCapabilityMapState state = new ProjectCapabilityMapState(project.getId());
        state.markDirty(1, "f".repeat(64), fact.getUpdatedAt());
        state.complete(1, 1, 1, 0, 0, "f".repeat(64), fact.getTimelineEventAt(), UUID.randomUUID());
        stateRepository.saveAndFlush(state);

        var overview = queryService.overview(userId, project.getId());
        var list = queryService.list(userId, project.getId(), "ACTIVE", "FORMING", "模型", "name", 0, 20);
        var detail = queryService.detail(userId, capability.getId());

        assertThat(overview.activeCount()).isEqualTo(1);
        assertThat(overview.sourceFactCount()).isEqualTo(1);
        assertThat(list.items()).extracting(item -> item.id()).containsExactly(capability.getId());
        assertThat(detail.evolutions().items()).hasSize(1);
        assertThat(detail.recentFacts().items()).extracting(item -> item.factId()).containsExactly(fact.getId());
        assertThat(detail.maturityReason()).contains("1 条事实");
    }

    @Test
    void ownershipAndInvalidFiltersAreRejected() {
        UUID owner = UUID.randomUUID();
        ProjectSpace project = project(owner, "私有能力");
        ProjectCapability capability = capability(project.getId(), "私有能力", "identity-b");

        assertThatThrownBy(() -> queryService.detail(UUID.randomUUID(), capability.getId()))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> queryService.list(owner, project.getId(), "UNKNOWN", "", "", "name", 0, 20))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> queryService.list(owner, project.getId(), "", "UNKNOWN", "", "name", 0, 20))
            .isInstanceOf(AppException.class);
    }

    @Test
    void mergedCapabilityKeepsOldIdAndRedirectTarget() {
        UUID owner = UUID.randomUUID();
        ProjectSpace project = project(owner, "合并能力");
        ProjectCapability target = capability(project.getId(), "统一能力", "identity-c");
        ProjectCapability source = capability(project.getId(), "旧版同义能力", "identity-d");
        source.markMerged(target.getId());
        capabilityRepository.saveAndFlush(source);

        var oldDetail = queryService.detail(owner, source.getId());
        var targetDetail = queryService.detail(owner, target.getId());

        assertThat(oldDetail.status()).isEqualTo("MERGED");
        assertThat(oldDetail.mergedIntoCapabilityId()).isEqualTo(target.getId());
        assertThat(targetDetail.mergedHistory()).extracting(item -> item.id()).contains(source.getId());
    }

    private ProjectSpace project(UUID userId, String name) {
        ProjectSpace value = new ProjectSpace(userId);
        value.update(name, "test", ProjectStatus.BUILDING, List.of("Spring Boot"), "", LocalDate.now(), null);
        return projectRepository.saveAndFlush(value);
    }

    private ProjectFact fact(UUID projectId) {
        Instant occurred = Instant.parse("2026-07-01T00:00:00Z");
        ProjectFact fact = new ProjectFact(projectId, UUID.randomUUID(), UUID.randomUUID(), ProjectFactOrigin.INCREMENTAL_SCAN, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        fact.updateContent(
            "模型失败保护", "失败不覆盖旧结果", List.of("保留旧结果"), "模型入口更可靠",
            occurred, occurred, List.of("abcdef12"), List.of(), List.of(), List.of("src/model.java"),
            List.of("commit:abcdef12", "file:src/model.java"), "MODEL", "PASS", EvidenceConfidence.HIGH,
            ProjectFactRecordStatus.RECORDED, ""
        );
        fact.assignTimeline(occurred, "2026-07-01", "2026-W27", "2026-07");
        return factRepository.saveAndFlush(fact);
    }

    private ProjectCapability capability(UUID projectId, String name, String identitySeed) {
        String identity = (identitySeed + "0".repeat(64)).substring(0, 64);
        ProjectCapability capability = new ProjectCapability(projectId, identity, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        capability.initialize(name, "长期能力摘要", "解决真实工程问题", "长期维护价值", List.of("核心"), Instant.parse("2026-07-01T00:00:00Z"), "MODEL", "fixed", "fixed", UUID.randomUUID());
        return capabilityRepository.saveAndFlush(capability);
    }
}
