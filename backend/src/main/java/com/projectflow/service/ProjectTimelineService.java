package com.projectflow.service;

import static com.projectflow.dto.ProjectTimelineDtos.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.ProjectFactDtos.ProjectFactPageResponse;
import com.projectflow.dto.ProjectFactDtos.ProjectFactSummaryResponse;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactHistoryState;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineSummaryStatus;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.ChangeBatchRepository;
import com.projectflow.repository.ProjectFactAgentResultRefRepository;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.ProjectFactFileRefRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeFactRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.repository.TimelineNamedCountRow;
import com.projectflow.repository.TimelineOverviewRow;
import com.projectflow.repository.TimelinePeriodStatsRow;
import com.projectflow.support.AppException;

@Service
public class ProjectTimelineService {
    private static final List<ProjectTimelineSummaryStatus> DIRTY_STATUSES = List.of(
        ProjectTimelineSummaryStatus.DIRTY,
        ProjectTimelineSummaryStatus.QUEUED,
        ProjectTimelineSummaryStatus.GENERATING,
        ProjectTimelineSummaryStatus.FAILED,
        ProjectTimelineSummaryStatus.WAITING_FOR_MODEL
    );

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectFactCommitRefRepository commitRefRepository;
    private final ProjectFactFileRefRepository fileRefRepository;
    private final ProjectFactAgentResultRefRepository agentResultRefRepository;
    private final ProjectFactHistoryStateRepository historyRepository;
    private final ProjectTimelineSummaryRepository summaryRepository;
    private final ProjectTimelineThemeRepository themeRepository;
    private final ProjectTimelineThemeFactRepository themeFactRepository;
    private final TimelinePeriodResolver resolver;

    public ProjectTimelineService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectFactCommitRefRepository commitRefRepository,
        ProjectFactFileRefRepository fileRefRepository,
        ProjectFactAgentResultRefRepository agentResultRefRepository,
        ProjectFactHistoryStateRepository historyRepository,
        ProjectTimelineSummaryRepository summaryRepository,
        ProjectTimelineThemeRepository themeRepository,
        ProjectTimelineThemeFactRepository themeFactRepository,
        TimelinePeriodResolver resolver
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.commitRefRepository = commitRefRepository;
        this.fileRefRepository = fileRefRepository;
        this.agentResultRefRepository = agentResultRefRepository;
        this.historyRepository = historyRepository;
        this.summaryRepository = summaryRepository;
        this.themeRepository = themeRepository;
        this.themeFactRepository = themeFactRepository;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public TimelineOverviewResponse overview(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        TimelineOverviewRow row = safeOverview(projectId);
        ProjectFactHistoryState history = historyRepository.findByProjectId(projectId).orElse(null);
        long covered = commitRefRepository.countDistinctCommitShaByProjectId(projectId);
        long total = history == null ? covered : Math.max(covered, history.getTotalCommitCount());
        String latestStatus = summaryRepository.findFirstByProjectIdOrderByUpdatedAtDesc(projectId)
            .map(value -> value.getStatus().name()).orElse("NOT_GENERATED");
        return new TimelineOverviewResponse(
            projectId, resolver.zoneId(), row.earliestFactAt(), row.latestFactAt(), row.factCount(), row.batchCount(),
            new CommitCoverageResponse(covered, total), history(history, covered),
            summaryRepository.countByProjectIdAndStatusIn(projectId, DIRTY_STATUSES), latestStatus
        );
    }

    @Transactional(readOnly = true)
    public TimelinePeriodPageResponse periods(
        UUID userId, UUID projectId, TimelineGranularity granularity,
        String from, String to, int page, int size
    ) {
        ownedProject(userId, projectId);
        requireListGranularity(granularity);
        String fromKey = validateOptionalKey(granularity, from);
        String toKey = validateOptionalKey(granularity, to);
        if (fromKey != null && toKey != null && fromKey.compareTo(toKey) > 0) {
            throw badPeriod("Timeline from must not be after to");
        }
        Page<TimelinePeriodStatsRow> result = periodRows(
            projectId, granularity, fromKey, toKey,
            PageRequest.of(Math.max(0, page), clamp(size))
        );
        List<TimelinePeriodResponse> items = enrichPeriods(projectId, granularity, result.getContent());
        return new TimelinePeriodPageResponse(
            projectId, resolver.zoneId(), granularity.name(), items, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public TimelinePeriodDetailResponse period(
        UUID userId, UUID projectId, TimelineGranularity granularity, String periodKey, int factPage, int factSize
    ) {
        ownedProject(userId, projectId);
        requireListGranularity(granularity);
        TimelinePeriodResolver.PeriodRange range = validRange(granularity, periodKey);
        Page<TimelinePeriodStatsRow> rows = periodRows(
            projectId, granularity, range.periodKey(), range.periodKey(), PageRequest.of(0, 1)
        );
        if (rows.isEmpty()) throw new AppException("TIMELINE_PERIOD_NOT_FOUND", "项目历程时间段不存在", HttpStatus.NOT_FOUND);
        TimelinePeriodStatsRow row = rows.getContent().get(0);
        TimelineStatsResponse stats = stats(projectId, granularity, row);
        ProjectTimelineSummary summary = summaryRepository
            .findByProjectIdAndGranularityAndPeriodKey(projectId, granularity, range.periodKey()).orElse(null);
        List<TimelineThemeResponse> themes = themes(summary);
        Page<ProjectFact> facts = factPage(projectId, granularity, range.periodKey(), factPage, factSize);
        ProjectFactHistoryState history = historyRepository.findByProjectId(projectId).orElse(null);
        long covered = commitRefRepository.countDistinctCommitShaByProjectId(projectId);
        return new TimelinePeriodDetailResponse(
            projectId, resolver.zoneId(), granularity.name(), range.periodKey(), range.startInclusive(), range.endExclusive(),
            stats, summary(summary), themes, summary == null ? Math.toIntExact(row.getFactCount()) : summary.getSourceFactCount(),
            summary == null ? 0 : summary.getCoveredFactCount(), factPage(facts), history(history, covered)
        );
    }

    @Transactional(readOnly = true)
    public TimelineThemeFactsResponse themeFacts(
        UUID userId, UUID projectId, UUID themeId, int page, int size
    ) {
        ownedProject(userId, projectId);
        ProjectTimelineTheme theme = themeRepository.findByIdAndProjectId(themeId, projectId)
            .orElseThrow(() -> new AppException("TIMELINE_THEME_NOT_FOUND", "项目历程主题不存在", HttpStatus.NOT_FOUND));
        Page<ProjectFact> facts = factRepository.findThemeFacts(
            projectId, themeId, PageRequest.of(Math.max(0, page), clamp(size))
        );
        return new TimelineThemeFactsResponse(projectId, themeId, theme.getTitle(), factPage(facts));
    }

    @Transactional(readOnly = true)
    public TimelineLifecycleResponse lifecycle(UUID userId, UUID projectId) {
        ownedProject(userId, projectId);
        TimelineOverviewRow row = safeOverview(projectId);
        ProjectTimelineSummary summary = summaryRepository
            .findByProjectIdAndGranularityAndPeriodKey(projectId, TimelineGranularity.LIFECYCLE, "ALL").orElse(null);
        List<TimelinePeriodStatsRow> monthRows = new ArrayList<>();
        int page = 0;
        Page<TimelinePeriodStatsRow> result;
        do {
            result = periodRows(projectId, TimelineGranularity.MONTH, null, null, PageRequest.of(page++, 200));
            monthRows.addAll(result.getContent());
        } while (result.hasNext());
        long commits = commitRefRepository.countDistinctCommitShaByProjectId(projectId);
        TimelineStatsResponse stats = new TimelineStatsResponse(
            row.factCount(), row.batchCount(), commits, fileRefRepository.countDistinctByProjectId(projectId),
            agentResultRefRepository.countDistinctByProjectId(projectId), row.attentionCount(),
            row.earliestFactAt(), row.latestFactAt()
        );
        ProjectFactHistoryState history = historyRepository.findByProjectId(projectId).orElse(null);
        return new TimelineLifecycleResponse(
            projectId, resolver.zoneId(), row.earliestFactAt(), row.latestFactAt(), stats, summary(summary), themes(summary),
            enrichPeriods(projectId, TimelineGranularity.MONTH, monthRows),
            summary == null ? Math.toIntExact(row.factCount()) : summary.getSourceFactCount(),
            summary == null ? 0 : summary.getCoveredFactCount(), history(history, commits)
        );
    }

    private List<TimelinePeriodResponse> enrichPeriods(
        UUID projectId, TimelineGranularity granularity, List<TimelinePeriodStatsRow> rows
    ) {
        if (rows.isEmpty()) return List.of();
        List<String> keys = rows.stream().map(TimelinePeriodStatsRow::getPeriodKey).toList();
        Map<String, Long> commits = counts(commitCounts(projectId, granularity, keys));
        Map<String, Long> files = counts(fileCounts(projectId, granularity, keys));
        Map<String, Long> agents = counts(agentCounts(projectId, granularity, keys));
        Map<String, ProjectTimelineSummary> summaries = new HashMap<>();
        summaryRepository.findByProjectIdAndGranularityAndPeriodKeyIn(projectId, granularity, keys)
            .forEach(value -> summaries.put(value.getPeriodKey(), value));
        List<UUID> summaryIds = summaries.values().stream().map(ProjectTimelineSummary::getId).toList();
        Map<UUID, Long> themeCounts = new HashMap<>();
        if (!summaryIds.isEmpty()) themeRepository.countBySummaryIds(summaryIds)
            .forEach(value -> themeCounts.put(value.getSummaryId(), value.getThemeCount()));
        return rows.stream().map(row -> {
            TimelinePeriodResolver.PeriodRange range = resolver.resolve(granularity, row.getPeriodKey());
            ProjectTimelineSummary summary = summaries.get(row.getPeriodKey());
            TimelineStatsResponse stats = new TimelineStatsResponse(
                row.getFactCount(), row.getBatchCount(), commits.getOrDefault(row.getPeriodKey(), 0L),
                files.getOrDefault(row.getPeriodKey(), 0L), agents.getOrDefault(row.getPeriodKey(), 0L),
                row.getAttentionCount(), row.getEarliestEventAt(), row.getLatestEventAt()
            );
            return new TimelinePeriodResponse(
                row.getPeriodKey(), range.startInclusive(), range.endExclusive(), stats,
                summary == null ? (granularity == TimelineGranularity.DAY ? "NOT_REQUIRED" : "DIRTY") : summary.getStatus().name(),
                summary == null ? "" : preview(summary.getSummary()), stale(summary),
                summary == null ? 0 : themeCounts.getOrDefault(summary.getId(), 0L)
            );
        }).toList();
    }

    private TimelineStatsResponse stats(UUID projectId, TimelineGranularity granularity, TimelinePeriodStatsRow row) {
        String key = row.getPeriodKey();
        return new TimelineStatsResponse(
            row.getFactCount(), row.getBatchCount(), switch (granularity) {
                case DAY -> commitRefRepository.countByTimelineDay(projectId, key);
                case WEEK -> commitRefRepository.countByTimelineWeek(projectId, key);
                case MONTH -> commitRefRepository.countByTimelineMonth(projectId, key);
                default -> 0;
            }, switch (granularity) {
                case DAY -> fileRefRepository.countByTimelineDay(projectId, key);
                case WEEK -> fileRefRepository.countByTimelineWeek(projectId, key);
                case MONTH -> fileRefRepository.countByTimelineMonth(projectId, key);
                default -> 0;
            }, switch (granularity) {
                case DAY -> agentResultRefRepository.countByTimelineDay(projectId, key);
                case WEEK -> agentResultRefRepository.countByTimelineWeek(projectId, key);
                case MONTH -> agentResultRefRepository.countByTimelineMonth(projectId, key);
                default -> 0;
            }, row.getAttentionCount(), row.getEarliestEventAt(), row.getLatestEventAt()
        );
    }

    private Page<TimelinePeriodStatsRow> periodRows(
        UUID projectId, TimelineGranularity granularity, String from, String to, PageRequest pageable
    ) {
        return switch (granularity) {
            case DAY -> factRepository.summarizeTimelineDays(projectId, from, to, ProjectFactRecordStatus.NEEDS_ATTENTION, pageable);
            case WEEK -> factRepository.summarizeTimelineWeeks(projectId, from, to, ProjectFactRecordStatus.NEEDS_ATTENTION, pageable);
            case MONTH -> factRepository.summarizeTimelineMonths(projectId, from, to, ProjectFactRecordStatus.NEEDS_ATTENTION, pageable);
            default -> throw badPeriod("Lifecycle is available from the lifecycle endpoint");
        };
    }

    private Page<ProjectFact> factPage(
        UUID projectId, TimelineGranularity granularity, String key, int page, int size
    ) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), clamp(size));
        return switch (granularity) {
            case DAY -> factRepository.findByProjectIdAndTimelineDayKeyOrderByTimelineEventAtDescCreatedAtDesc(projectId, key, pageable);
            case WEEK -> factRepository.findByProjectIdAndTimelineWeekKeyOrderByTimelineEventAtDescCreatedAtDesc(projectId, key, pageable);
            case MONTH -> factRepository.findByProjectIdAndTimelineMonthKeyOrderByTimelineEventAtDescCreatedAtDesc(projectId, key, pageable);
            default -> throw badPeriod("Invalid timeline granularity");
        };
    }

    private List<TimelineThemeResponse> themes(ProjectTimelineSummary summary) {
        if (summary == null || !summary.hasGeneratedContent()) return List.of();
        List<ProjectTimelineTheme> themes = themeRepository.findBySummaryIdOrderBySortOrderAsc(summary.getId());
        Map<UUID, Long> counts = new HashMap<>();
        if (!themes.isEmpty()) themeFactRepository.countByThemeIds(themes.stream().map(ProjectTimelineTheme::getId).toList())
            .forEach(row -> counts.put(row.getThemeId(), row.getFactCount()));
        return themes.stream()
            .map(theme -> new TimelineThemeResponse(
                theme.getId(), theme.getTitle(), theme.getSummary(), theme.getSortOrder(),
                counts.getOrDefault(theme.getId(), 0L)
            )).toList();
    }

    private TimelineSummaryResponse summary(ProjectTimelineSummary value) {
        if (value == null) return null;
        return new TimelineSummaryResponse(
            value.getId(), value.getGranularity().name(), value.getPeriodKey(), value.getStatus().name(),
            value.getSummary(), value.getSourceFactCount(), value.getCoveredFactCount(), stale(value),
            value.getGenerationVersion(), value.getAnalysisJobId(), value.getErrorCode(), value.getErrorSummary(),
            value.getGeneratedAt(), value.getUpdatedAt()
        );
    }

    private ProjectFactPageResponse factPage(Page<ProjectFact> page) {
        return new ProjectFactPageResponse(
            page.getContent().stream().map(this::factSummary).toList(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages()
        );
    }

    private ProjectFactSummaryResponse factSummary(ProjectFact fact) {
        return new ProjectFactSummaryResponse(
            fact.getId(), fact.getProjectId(), fact.getBatchId(), fact.getSourceSegmentId(), fact.getLegacySedimentId(),
            fact.getOrigin().name(), fact.getTitle(), fact.getSummary(), fact.getOccurredFrom(), fact.getOccurredTo(),
            fact.getSourceMode(), fact.getQualityStatus(), fact.getConfidence().name(), fact.getRecordStatus().name(),
            fact.getAttentionReason(), fact.getCommitCount(), fact.getAgentResultCount(), fact.getAffectedFileCount(),
            fact.getEvidenceCount(), fact.getCreatedAt(), fact.getUpdatedAt(), fact.getEpistemicStatus().name(),
            fact.getCurrentness(), fact.getRevision(), fact.getValidationStatus(), fact.getLimitations()
        );
    }

    private List<TimelineNamedCountRow> commitCounts(UUID projectId, TimelineGranularity granularity, List<String> keys) {
        return switch (granularity) {
            case DAY -> commitRefRepository.countByTimelineDays(projectId, keys);
            case WEEK -> commitRefRepository.countByTimelineWeeks(projectId, keys);
            case MONTH -> commitRefRepository.countByTimelineMonths(projectId, keys);
            default -> List.of();
        };
    }

    private List<TimelineNamedCountRow> fileCounts(UUID projectId, TimelineGranularity granularity, List<String> keys) {
        return switch (granularity) {
            case DAY -> fileRefRepository.countByTimelineDays(projectId, keys);
            case WEEK -> fileRefRepository.countByTimelineWeeks(projectId, keys);
            case MONTH -> fileRefRepository.countByTimelineMonths(projectId, keys);
            default -> List.of();
        };
    }

    private List<TimelineNamedCountRow> agentCounts(UUID projectId, TimelineGranularity granularity, List<String> keys) {
        return switch (granularity) {
            case DAY -> agentResultRefRepository.countByTimelineDays(projectId, keys);
            case WEEK -> agentResultRefRepository.countByTimelineWeeks(projectId, keys);
            case MONTH -> agentResultRefRepository.countByTimelineMonths(projectId, keys);
            default -> List.of();
        };
    }

    private Map<String, Long> counts(List<TimelineNamedCountRow> rows) {
        Map<String, Long> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getPeriodKey(), row.getItemCount()));
        return result;
    }

    private HistoryCoverageResponse history(ProjectFactHistoryState state, long covered) {
        if (state == null) {
            return new HistoryCoverageResponse("NOT_STARTED", Math.toIntExact(Math.min(Integer.MAX_VALUE, covered)),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, covered)), 0, "当前历程基于已经记录的项目事实");
        }
        String status = state.getStatus().name();
        String notice = "COMPLETED".equals(status)
            ? "Git 历史覆盖已完成"
            : "项目历史记忆仍在补齐，当前历程会随历史补齐自动扩展";
        return new HistoryCoverageResponse(
            status, state.getCoveredCommitCount(), state.getTotalCommitCount(), state.getRemainingCommitCount(), notice
        );
    }

    private TimelineOverviewRow safeOverview(UUID projectId) {
        if (!factRepository.existsByProjectIdAndTimelineEventAtIsNotNull(projectId)) {
            return new TimelineOverviewRow(0, 0, 0, null, null, null);
        }
        TimelineOverviewRow row = factRepository.timelineOverview(projectId, ProjectFactRecordStatus.NEEDS_ATTENTION);
        return row == null ? new TimelineOverviewRow(0, 0, 0, null, null, null) : row;
    }

    private boolean stale(ProjectTimelineSummary summary) {
        return summary != null && summary.hasGeneratedContent()
            && (summary.getStatus() != ProjectTimelineSummaryStatus.READY
                || summary.getCoveredFactCount() != summary.getSourceFactCount());
    }

    private String preview(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 160 ? text : text.substring(0, 160) + "…";
    }

    private void ownedProject(UUID userId, UUID projectId) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private void requireListGranularity(TimelineGranularity granularity) {
        if (granularity == null || granularity == TimelineGranularity.LIFECYCLE) {
            throw badPeriod("Timeline granularity must be DAY, WEEK, or MONTH");
        }
    }

    private String validateOptionalKey(TimelineGranularity granularity, String key) {
        if (key == null || key.isBlank()) return null;
        return validRange(granularity, key).periodKey();
    }

    private TimelinePeriodResolver.PeriodRange validRange(TimelineGranularity granularity, String key) {
        try {
            return resolver.resolve(granularity, key);
        } catch (IllegalArgumentException exception) {
            throw badPeriod(exception.getMessage());
        }
    }

    private AppException badPeriod(String message) {
        return new AppException("INVALID_TIMELINE_PERIOD", message, HttpStatus.BAD_REQUEST);
    }

    private int clamp(int size) { return Math.max(1, Math.min(size, 100)); }
}
