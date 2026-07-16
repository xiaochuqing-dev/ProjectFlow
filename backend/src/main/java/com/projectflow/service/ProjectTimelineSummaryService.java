package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactHistoryStatus;
import com.projectflow.entity.ProjectAnalysisJobStatus;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineSummaryStatus;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.entity.ProjectTimelineThemeFact;
import com.projectflow.entity.TimelineGranularity;
import com.projectflow.repository.AiProviderRepository;
import com.projectflow.repository.ProjectFactHistoryStateRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectAnalysisJobRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeFactRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.repository.TimelineFactPeriodVersionRow;
import com.projectflow.repository.TimelineFactVersionRow;
import com.projectflow.repository.TimelinePeriodStatsRow;
import com.projectflow.support.AppException;

@Service
public class ProjectTimelineSummaryService {
    private static final int FACT_CHUNK_SIZE = 120;
    private static final Set<String> PROHIBITED_FIELDS = Set.of(
        "nextsteps", "recommendations", "futureplan", "priority", "developershould", "roadmap"
    );
    private static final List<String> PROHIBITED_TEXT = List.of("下一步", "路线图", "未来计划", "后续建议", "应该继续");

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectAnalysisJobRepository jobRepository;
    private final ProjectFactHistoryStateRepository historyRepository;
    private final ProjectTimelineSummaryRepository summaryRepository;
    private final ProjectTimelineThemeRepository themeRepository;
    private final ProjectTimelineThemeFactRepository themeFactRepository;
    private final AiProviderRepository providerRepository;
    private final TimelinePeriodResolver resolver;
    private final ModelGatewayService modelGateway;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public ProjectTimelineSummaryService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectAnalysisJobRepository jobRepository,
        ProjectFactHistoryStateRepository historyRepository,
        ProjectTimelineSummaryRepository summaryRepository,
        ProjectTimelineThemeRepository themeRepository,
        ProjectTimelineThemeFactRepository themeFactRepository,
        AiProviderRepository providerRepository,
        TimelinePeriodResolver resolver,
        ModelGatewayService modelGateway,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher,
        PlatformTransactionManager transactionManager
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.jobRepository = jobRepository;
        this.historyRepository = historyRepository;
        this.summaryRepository = summaryRepository;
        this.themeRepository = themeRepository;
        this.themeFactRepository = themeFactRepository;
        this.providerRepository = providerRepository;
        this.resolver = resolver;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void factsCommitted(ProjectFactsCommittedEvent event) {
        List<ProjectFact> facts = factRepository.findAllById(event.factIds()).stream()
            .filter(fact -> event.projectId().equals(fact.getProjectId()))
            .toList();
        if (facts.isEmpty()) return;
        facts.stream().map(ProjectFact::getTimelineWeekKey).filter(value -> !value.isBlank()).distinct()
            .forEach(key -> markDirty(event.projectId(), TimelineGranularity.WEEK, key, false));
        facts.stream().map(ProjectFact::getTimelineMonthKey).filter(value -> !value.isBlank()).distinct()
            .forEach(key -> markDirty(event.projectId(), TimelineGranularity.MONTH, key, false));
        markDirty(event.projectId(), TimelineGranularity.LIFECYCLE, "ALL", false);
        projectRepository.findById(event.projectId()).ifPresent(project ->
            eventPublisher.publishEvent(new ProjectTimelineRefreshRequestedEvent(project.getUserId(), project.getId()))
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void modelConfigured(ModelProviderConfiguredEvent event) {
        projectRepository.findByUserIdOrderByUpdatedAtDesc(event.userId()).forEach(project -> {
            List<ProjectTimelineSummary> waiting = summaryRepository.findByProjectIdAndStatusInOrderByUpdatedAtAsc(
                project.getId(), List.of(ProjectTimelineSummaryStatus.WAITING_FOR_MODEL)
            );
            waiting.forEach(summary -> markDirty(
                project.getId(), summary.getGranularity(), summary.getPeriodKey(), true
            ));
            if (!waiting.isEmpty()) {
                eventPublisher.publishEvent(new ProjectTimelineRefreshRequestedEvent(event.userId(), project.getId()));
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(300)
    public void bootstrapExistingFacts() {
        projectRepository.findAll().forEach(project -> {
            boolean found = false;
            for (TimelineGranularity granularity : List.of(TimelineGranularity.WEEK, TimelineGranularity.MONTH)) {
                int page = 0;
                Page<TimelinePeriodStatsRow> rows;
                do {
                    rows = periodRows(project.getId(), granularity, PageRequest.of(page++, 200));
                    rows.getContent().forEach(row -> markDirty(project.getId(), granularity, row.getPeriodKey(), false));
                    found = found || !rows.isEmpty();
                } while (rows.hasNext());
            }
            if (found) {
                markDirty(project.getId(), TimelineGranularity.LIFECYCLE, "ALL", false);
                eventPublisher.publishEvent(new ProjectTimelineRefreshRequestedEvent(project.getUserId(), project.getId()));
            }
        });
    }

    public void retry(UUID userId, UUID projectId, TimelineGranularity granularity, String periodKey) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        TimelinePeriodResolver.PeriodRange range;
        try {
            range = resolver.resolve(granularity, periodKey);
        } catch (IllegalArgumentException exception) {
            throw new AppException("INVALID_TIMELINE_PERIOD", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
        if (granularity == TimelineGranularity.DAY) {
            throw new AppException("TIMELINE_DAY_SUMMARY_NOT_REQUIRED", "日视图不需要模型摘要", HttpStatus.CONFLICT);
        }
        markDirty(projectId, granularity, range.periodKey(), true);
        eventPublisher.publishEvent(new ProjectTimelineRefreshRequestedEvent(userId, projectId));
    }

    public String nextDirtyScope(UUID projectId) {
        boolean historyRunning = historyRepository.findByProjectId(projectId)
            .map(state -> state.getStatus() == ProjectFactHistoryStatus.RUNNING)
            .orElse(false);
        if (historyRunning) return "";
        return summaryRepository.findByProjectIdAndStatusInOrderByUpdatedAtAsc(
            projectId, List.of(ProjectTimelineSummaryStatus.DIRTY)
        ).stream()
            .filter(summary -> summary.getGranularity() != TimelineGranularity.DAY)
            .sorted(Comparator.comparingInt(summary -> switch (summary.getGranularity()) {
                case WEEK -> 0;
                case MONTH -> 1;
                case LIFECYCLE -> 2;
                default -> 3;
            }))
            .map(this::scope)
            .findFirst().orElse("");
    }

    public void markQueued(String encodedScope, UUID jobId) {
        RefreshScope scope = RefreshScope.parse(encodedScope);
        transactionTemplate.executeWithoutResult(status -> summaryRepository
            .findLocked(scope.projectId(), scope.granularity(), scope.periodKey()).ifPresent(summary -> {
                if (summary.getStatus() == ProjectTimelineSummaryStatus.DIRTY
                    && summary.getSourceFactFingerprint().equals(scope.fingerprint())) {
                    summary.markQueued(jobId);
                    summaryRepository.save(summary);
                }
            }));
    }

    public RefreshOutcome refresh(UUID userId, UUID projectId, UUID jobId, String encodedScope) throws Exception {
        RefreshScope scope = RefreshScope.parse(encodedScope);
        if (!projectId.equals(scope.projectId())) throw new IllegalArgumentException("Timeline scope project mismatch");
        Prepared prepared = transactionTemplate.execute(status -> {
            ProjectTimelineSummary summary = summaryRepository
                .findLocked(projectId, scope.granularity(), scope.periodKey()).orElse(null);
            if (summary == null || !summary.getSourceFactFingerprint().equals(scope.fingerprint())) return null;
            summary.markGenerating(jobId);
            summaryRepository.save(summary);
            return new Prepared(summary.getId(), summary.getSourceFactCount(), summary.getSourceFactFingerprint());
        });
        if (prepared == null) return new RefreshOutcome("{\"status\":\"STALE_JOB_SKIPPED\"}", List.of(), false);

        AiProvider provider = providerRepository.findFirstByUserIdAndDefaultEnabledTrueOrderByUpdatedAtDesc(userId).orElse(null);
        if (provider == null) {
            transactionTemplate.executeWithoutResult(status -> summaryRepository
                .findLocked(projectId, scope.granularity(), scope.periodKey()).ifPresent(summary -> {
                    if (summary.getSourceFactFingerprint().equals(scope.fingerprint())) {
                        summary.markWaitingForModel();
                        summaryRepository.save(summary);
                    }
                }));
            return new RefreshOutcome("{\"status\":\"WAITING_FOR_MODEL\"}", List.of(), false);
        }

        List<ModelGatewayService.ModelCallDiagnostics> diagnostics = new ArrayList<>();
        try {
            GeneratedSummary generated = scope.granularity() == TimelineGranularity.LIFECYCLE
                ? generateLifecycle(provider, projectId, diagnostics)
                : generatePeriod(provider, projectId, scope.granularity(), scope.periodKey(), diagnostics);
            if (generated.coveredFactIds().size() != prepared.sourceFactCount()) {
                throw new TimelineCoverageException("Timeline coverage does not match source facts");
            }
            applyGenerated(scope, jobId, provider, generated);
            String result = objectMapper.writeValueAsString(Map.of(
                "status", "READY",
                "sourceFactCount", prepared.sourceFactCount(),
                "coveredFactCount", generated.coveredFactIds().size(),
                "themeCount", generated.themes().size(),
                "requestCount", diagnostics.stream().mapToInt(ModelGatewayService.ModelCallDiagnostics::requestCount).sum()
            ));
            return new RefreshOutcome(result, List.copyOf(diagnostics), true);
        } catch (CancellationException exception) {
            restoreDirty(scope);
            throw exception;
        } catch (Exception exception) {
            markFailed(scope, jobId, exception);
            throw exception;
        }
    }

    private GeneratedSummary generatePeriod(
        AiProvider provider, UUID projectId, TimelineGranularity granularity, String periodKey,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics
    ) throws Exception {
        List<ProjectFact> facts = switch (granularity) {
            case WEEK -> factRepository.findByProjectIdAndTimelineWeekKeyOrderByTimelineEventAtAscCreatedAtAsc(projectId, periodKey);
            case MONTH -> factRepository.findByProjectIdAndTimelineMonthKeyOrderByTimelineEventAtAscCreatedAtAsc(projectId, periodKey);
            default -> throw new IllegalArgumentException("Only WEEK and MONTH use period model summaries");
        };
        if (facts.isEmpty()) throw new TimelineCoverageException("Timeline period contains no facts");
        if (facts.size() <= FACT_CHUNK_SIZE) {
            ParsedDraft draft = callPeriodModel(provider, granularity, periodKey, facts, diagnostics, "完整时间段");
            return toGenerated(draft);
        }

        List<ChunkDraft> chunks = new ArrayList<>();
        for (int start = 0, index = 1; start < facts.size(); start += FACT_CHUNK_SIZE, index++) {
            ModelCancellationContext.throwIfCancelled();
            List<ProjectFact> chunkFacts = facts.subList(start, Math.min(facts.size(), start + FACT_CHUNK_SIZE));
            ParsedDraft chunk = callPeriodModel(provider, granularity, periodKey, chunkFacts, diagnostics, "分块 C" + index);
            chunks.add(new ChunkDraft("C" + index, chunk, chunkFacts.stream().map(ProjectFact::getId).toList()));
        }
        List<String> chunkIds = chunks.stream().map(ChunkDraft::id).toList();
        List<Map<String, Object>> compactChunks = chunks.stream().map(chunk -> Map.<String, Object>of(
            "factId", chunk.id(),
            "summary", chunk.draft().summary(),
            "themes", chunk.draft().themes().stream().map(theme -> theme.title() + "：" + theme.summary()).toList(),
            "sourceFactCount", chunk.factIds().size()
        )).toList();
        String prompt = basePeriodPrompt(granularity, periodKey)
            + "\n这是分块综合。每个 C 编号代表一个已完整覆盖的事实块。必须覆盖全部 C 编号。"
            + "\nALLOWED_IDS_JSON=" + objectMapper.writeValueAsString(chunkIds)
            + "\nFACTS_JSON=" + objectMapper.writeValueAsString(compactChunks);
        ParsedDraft synthesis = parsePeriodResponse(call(provider, prompt, ModelTaskType.PROJECT_TIMELINE_PERIOD_SUMMARY, diagnostics), new LinkedHashSet<>(chunkIds));
        Map<String, ChunkDraft> byId = new LinkedHashMap<>();
        chunks.forEach(chunk -> byId.put(chunk.id(), chunk));
        List<ThemeDraft> themes = new ArrayList<>();
        Set<UUID> covered = new LinkedHashSet<>();
        for (TextTheme theme : synthesis.themes()) {
            List<UUID> ids = theme.ids().stream().flatMap(id -> byId.get(id).factIds().stream()).toList();
            ids.forEach(covered::add);
            themes.add(new ThemeDraft(theme.title(), theme.summary(), ids));
        }
        return new GeneratedSummary(synthesis.summary(), themes, covered);
    }

    private ParsedDraft callPeriodModel(
        AiProvider provider, TimelineGranularity granularity, String periodKey, List<ProjectFact> facts,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics, String scopeLabel
    ) throws Exception {
        List<String> allowed = facts.stream().map(ProjectFact::getId).map(UUID::toString).toList();
        List<Map<String, Object>> compact = facts.stream().map(this::compactFact).toList();
        String prompt = basePeriodPrompt(granularity, periodKey)
            + "\n输入范围：" + scopeLabel
            + "\nALLOWED_IDS_JSON=" + objectMapper.writeValueAsString(allowed)
            + "\nFACTS_JSON=" + objectMapper.writeValueAsString(compact);
        return parsePeriodResponse(
            call(provider, prompt, ModelTaskType.PROJECT_TIMELINE_PERIOD_SUMMARY, diagnostics),
            new LinkedHashSet<>(allowed)
        );
    }

    private GeneratedSummary generateLifecycle(
        AiProvider provider, UUID projectId, List<ModelGatewayService.ModelCallDiagnostics> diagnostics
    ) throws Exception {
        List<TimelineFactPeriodVersionRow> versions = factRepository.lifecyclePeriodVersions(projectId);
        if (versions.isEmpty()) throw new TimelineCoverageException("Timeline lifecycle contains no facts");
        Map<String, List<UUID>> factIdsByMonth = new LinkedHashMap<>();
        versions.forEach(version -> factIdsByMonth
            .computeIfAbsent(version.periodKey(), ignored -> new ArrayList<>()).add(version.id()));
        Map<String, ProjectTimelineSummary> summaries = new HashMap<>();
        summaryRepository.findByProjectIdAndGranularityOrderByPeriodKeyDesc(projectId, TimelineGranularity.MONTH)
            .forEach(summary -> summaries.put(summary.getPeriodKey(), summary));
        List<Map<String, Object>> months = factIdsByMonth.entrySet().stream().map(entry -> {
            ProjectTimelineSummary summary = summaries.get(entry.getKey());
            return Map.<String, Object>of(
                "periodKey", entry.getKey(),
                "factCount", entry.getValue().size(),
                "summaryStatus", summary == null ? "DIRTY" : summary.getStatus().name(),
                "summary", summary == null ? "" : summary.getSummary()
            );
        }).toList();
        List<String> allowedMonths = new ArrayList<>(factIdsByMonth.keySet());
        String prompt = """
            你正在生成 ProjectFlow 项目完整历程摘要。输入只包含已经发生的月度事实统计与已有月摘要。
            只总结已经发生的事实，禁止下一步、建议、优先级、路线图、未来规划或未发生能力。
            返回严格 JSON：{"periodSummary":"自然中文摘要","stages":[{"title":"阶段主题","summary":"阶段摘要","monthKeys":["YYYY-MM"]}],"ungroupedMonthKeys":[]}。
            每个月必须且只能进入一个 stage 或 ungroupedMonthKeys，不得创造未知月份。
            """
            + "\nALLOWED_MONTH_KEYS_JSON=" + objectMapper.writeValueAsString(allowedMonths)
            + "\nMONTHS_JSON=" + objectMapper.writeValueAsString(months);
        JsonNode root = call(provider, prompt, ModelTaskType.PROJECT_TIMELINE_LIFECYCLE_SUMMARY, diagnostics);
        ParsedDraft parsed = parseGeneric(root, new LinkedHashSet<>(allowedMonths), "stages", "monthKeys", "ungroupedMonthKeys");
        List<ThemeDraft> themes = new ArrayList<>();
        Set<UUID> covered = new LinkedHashSet<>();
        for (TextTheme stage : parsed.themes()) {
            List<UUID> ids = stage.ids().stream().flatMap(month -> factIdsByMonth.get(month).stream()).toList();
            ids.forEach(covered::add);
            themes.add(new ThemeDraft(stage.title(), stage.summary(), ids));
        }
        return new GeneratedSummary(parsed.summary(), themes, covered);
    }

    private JsonNode call(
        AiProvider provider, String prompt, ModelTaskType task,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics
    ) throws Exception {
        ModelCancellationContext.throwIfCancelled();
        ModelGatewayService.StructuredModelResponse response = modelGateway.callStructured(provider, prompt, task);
        diagnostics.add(response.diagnostics());
        JsonNode root = response.parsed().root();
        rejectPlanning(root);
        return root;
    }

    private static ParsedDraft parsePeriodResponse(JsonNode root, Set<String> allowed) {
        return parseGeneric(root, allowed, "themes", "factIds", "ungroupedFactIds");
    }

    static int validatePeriodOutput(JsonNode root, List<String> allowedIds) {
        rejectPlanning(root);
        ParsedDraft draft = parsePeriodResponse(root, new LinkedHashSet<>(allowedIds));
        return draft.themes().stream().mapToInt(theme -> theme.ids().size()).sum();
    }

    static int validateLifecycleOutput(JsonNode root, List<String> allowedMonthKeys) {
        rejectPlanning(root);
        ParsedDraft draft = parseGeneric(
            root, new LinkedHashSet<>(allowedMonthKeys), "stages", "monthKeys", "ungroupedMonthKeys"
        );
        return draft.themes().stream().mapToInt(theme -> theme.ids().size()).sum();
    }

    static int plannedPeriodRequestCount(int factCount) {
        if (factCount <= 0) return 0;
        int chunks = (factCount + FACT_CHUNK_SIZE - 1) / FACT_CHUNK_SIZE;
        return chunks == 1 ? 1 : chunks + 1;
    }

    private static ParsedDraft parseGeneric(
        JsonNode root, Set<String> allowed, String themesField, String idsField, String ungroupedField
    ) {
        if (root == null || !root.isObject()) throw new TimelineCoverageException("Timeline model output is not an object");
        String summary = text(root, "periodSummary");
        if (summary.isBlank()) throw new TimelineCoverageException("Timeline model summary is empty");
        List<TextTheme> themes = new ArrayList<>();
        Set<String> assigned = new LinkedHashSet<>();
        JsonNode themeNodes = root.path(themesField);
        if (!themeNodes.isArray()) throw new TimelineCoverageException("Timeline model themes are missing");
        int fallbackIndex = 1;
        for (JsonNode node : themeNodes) {
            List<String> ids = validatedIds(node.path(idsField), allowed, assigned);
            if (ids.isEmpty()) continue;
            String title = text(node, "title");
            String themeSummary = text(node, "summary");
            themes.add(new TextTheme(title.isBlank() ? "项目变化 " + fallbackIndex : title, themeSummary, ids));
            fallbackIndex++;
        }
        List<String> ungrouped = validatedIds(root.path(ungroupedField), allowed, assigned);
        if (!ungrouped.isEmpty()) themes.add(new TextTheme("其他项目变化", "本时间段内未归入主要主题的已记录事实。", ungrouped));
        if (!assigned.equals(allowed)) {
            Set<String> missing = new LinkedHashSet<>(allowed);
            missing.removeAll(assigned);
            throw new TimelineCoverageException("Timeline model omitted " + missing.size() + " source facts");
        }
        return new ParsedDraft(summary, themes);
    }

    private static List<String> validatedIds(JsonNode node, Set<String> allowed, Set<String> assigned) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new TimelineCoverageException("Timeline membership must contain text IDs");
            String id = value.asText().trim();
            if (!allowed.contains(id)) throw new TimelineCoverageException("Timeline model returned an unknown source ID");
            if (assigned.add(id)) result.add(id);
        }
        return result;
    }

    private GeneratedSummary toGenerated(ParsedDraft draft) {
        List<ThemeDraft> themes = draft.themes().stream().map(theme -> new ThemeDraft(
            theme.title(), theme.summary(), theme.ids().stream().map(UUID::fromString).toList()
        )).toList();
        Set<UUID> covered = new LinkedHashSet<>();
        themes.forEach(theme -> theme.factIds().forEach(covered::add));
        return new GeneratedSummary(draft.summary(), themes, covered);
    }

    private Map<String, Object> compactFact(ProjectFact fact) {
        return Map.ofEntries(
            Map.entry("factId", fact.getId().toString()),
            Map.entry("title", fact.getTitle()),
            Map.entry("summary", fact.getSummary()),
            Map.entry("mainChanges", fact.getMainChanges().stream().limit(4).map(value -> bounded(value, 240)).toList()),
            Map.entry("occurredFrom", instant(fact.getOccurredFrom())),
            Map.entry("occurredTo", instant(fact.getOccurredTo())),
            Map.entry("sourceMode", fact.getSourceMode()),
            Map.entry("qualityStatus", fact.getQualityStatus()),
            Map.entry("commitCount", fact.getCommitCount()),
            Map.entry("fileCount", fact.getAffectedFileCount()),
            Map.entry("agentResultCount", fact.getAgentResultCount()),
            Map.entry("batchId", fact.getBatchId() == null ? "" : fact.getBatchId().toString())
        );
    }

    private String basePeriodPrompt(TimelineGranularity granularity, String periodKey) {
        return """
            你正在生成 ProjectFlow 项目历程的时间段摘要。ProjectFact 是唯一事实来源。
            只总结输入中已经发生的事实，禁止下一步、建议、优先级、路线图、未来规划或未发生能力。
            不要重新验证事实，不要创造统计数字，不得创造或遗漏 ID。
            返回严格 JSON：{"periodSummary":"自然中文摘要","themes":[{"title":"演进主题","summary":"主题摘要","factIds":["uuid"]}],"ungroupedFactIds":[]}。
            每个允许 ID 必须且只能进入一个 theme 或 ungroupedFactIds。
            """ + "\n粒度=" + granularity.name() + "，periodKey=" + periodKey;
    }

    private void applyGenerated(RefreshScope scope, UUID jobId, AiProvider provider, GeneratedSummary generated) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectTimelineSummary summary = summaryRepository
                .findLocked(scope.projectId(), scope.granularity(), scope.periodKey()).orElseThrow();
            if (!summary.getSourceFactFingerprint().equals(scope.fingerprint())) {
                throw new TimelineCoverageException("Timeline facts changed during generation");
            }
            List<ProjectTimelineTheme> oldThemes = themeRepository.findBySummaryIdOrderBySortOrderAsc(summary.getId());
            if (!oldThemes.isEmpty()) {
                themeFactRepository.deleteByThemeIdIn(oldThemes.stream().map(ProjectTimelineTheme::getId).toList());
                themeRepository.deleteBySummaryId(summary.getId());
            }
            int order = 0;
            for (ThemeDraft draft : generated.themes()) {
                ProjectTimelineTheme theme = themeRepository.save(new ProjectTimelineTheme(
                    summary.getId(), scope.projectId(), draft.title(), draft.summary(), order++
                ));
                draft.factIds().stream().distinct().forEach(factId -> themeFactRepository.save(
                    new ProjectTimelineThemeFact(scope.projectId(), theme.getId(), factId)
                ));
            }
            summary.complete(generated.summary(), generated.coveredFactIds().size(), provider.getName(), provider.getModelName(), jobId);
            summaryRepository.save(summary);
        });
    }

    private void markDirty(UUID projectId, TimelineGranularity granularity, String periodKey, boolean force) {
        List<TimelineFactVersionRow> versions = versions(projectId, granularity, periodKey);
        if (versions.isEmpty()) return;
        String fingerprint = fingerprint(projectId, granularity, periodKey, versions);
        Instant maxUpdatedAt = versions.stream().map(TimelineFactVersionRow::updatedAt)
            .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        TimelinePeriodResolver.PeriodRange range = resolver.resolve(granularity, periodKey);
        transactionTemplate.executeWithoutResult(status -> {
            ProjectTimelineSummary summary = summaryRepository.findLocked(projectId, granularity, periodKey)
                .orElseGet(() -> new ProjectTimelineSummary(
                    projectId, granularity, periodKey, range.startInclusive(), range.endExclusive(), resolver.zoneId()
                ));
            boolean inFlight = summary.getStatus() == ProjectTimelineSummaryStatus.QUEUED
                || summary.getStatus() == ProjectTimelineSummaryStatus.GENERATING;
            boolean activeJob = inFlight && hasActiveJob(summary.getAnalysisJobId());
            if (interruptedGeneration(
                force, summary.getSourceFactFingerprint().equals(fingerprint), summary.getStatus(), activeJob
            )) {
                summary.markFailed(
                    "TIMELINE_REFRESH_INTERRUPTED",
                    "服务中断了自动摘要任务；为避免重复模型调用，已保留事实统计和上次成功摘要，可手动重试。",
                    summary.getAnalysisJobId()
                );
                summaryRepository.save(summary);
                return;
            }
            if (keepsExistingState(
                force, summary.getSourceFactFingerprint().equals(fingerprint), summary.getStatus(), activeJob
            )) return;
            summary.markDirty(versions.size(), fingerprint, maxUpdatedAt);
            summaryRepository.save(summary);
        });
    }

    static boolean keepsExistingState(
        boolean force, boolean sameFingerprint, ProjectTimelineSummaryStatus status, boolean activeJob
    ) {
        if (force || !sameFingerprint) return false;
        return status != ProjectTimelineSummaryStatus.QUEUED
            && status != ProjectTimelineSummaryStatus.GENERATING
            || activeJob;
    }

    static boolean interruptedGeneration(
        boolean force, boolean sameFingerprint, ProjectTimelineSummaryStatus status, boolean activeJob
    ) {
        return !force && sameFingerprint && !activeJob
            && (status == ProjectTimelineSummaryStatus.QUEUED
                || status == ProjectTimelineSummaryStatus.GENERATING);
    }

    private boolean hasActiveJob(UUID jobId) {
        if (jobId == null) return false;
        return jobRepository.findById(jobId).map(job ->
            job.getStatus() == ProjectAnalysisJobStatus.QUEUED
                || job.getStatus() == ProjectAnalysisJobStatus.RUNNING
                || job.getStatus() == ProjectAnalysisJobStatus.CANCEL_REQUESTED
        ).orElse(false);
    }

    private List<TimelineFactVersionRow> versions(UUID projectId, TimelineGranularity granularity, String periodKey) {
        return switch (granularity) {
            case DAY -> factRepository.dayVersions(projectId, periodKey);
            case WEEK -> factRepository.weekVersions(projectId, periodKey);
            case MONTH -> factRepository.monthVersions(projectId, periodKey);
            case LIFECYCLE -> factRepository.lifecycleVersions(projectId);
        };
    }

    private String fingerprint(
        UUID projectId, TimelineGranularity granularity, String periodKey, List<TimelineFactVersionRow> versions
    ) {
        StringBuilder canonical = new StringBuilder(projectId.toString()).append('|').append(granularity).append('|').append(periodKey);
        versions.stream().sorted(Comparator.comparing(TimelineFactVersionRow::id)).forEach(version -> canonical
            .append('|').append(version.id()).append('@').append(instant(version.updatedAt())));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void markFailed(RefreshScope scope, UUID jobId, Exception exception) {
        transactionTemplate.executeWithoutResult(status -> summaryRepository
            .findLocked(scope.projectId(), scope.granularity(), scope.periodKey()).ifPresent(summary -> {
                if (summary.getSourceFactFingerprint().equals(scope.fingerprint())) {
                    summary.markFailed(
                        exception instanceof TimelineCoverageException ? "TIMELINE_COVERAGE_INVALID" : "TIMELINE_MODEL_FAILED",
                        bounded(exception.getMessage() == null ? "自动摘要生成失败" : exception.getMessage(), 900), jobId
                    );
                    summaryRepository.save(summary);
                }
            }));
    }

    private void restoreDirty(RefreshScope scope) {
        transactionTemplate.executeWithoutResult(status -> summaryRepository
            .findLocked(scope.projectId(), scope.granularity(), scope.periodKey()).ifPresent(summary -> {
                if (summary.getSourceFactFingerprint().equals(scope.fingerprint())) {
                    summary.markDirty(summary.getSourceFactCount(), scope.fingerprint(), summary.getSourceFactMaxUpdatedAt());
                    summaryRepository.save(summary);
                }
            }));
    }

    private String scope(ProjectTimelineSummary summary) {
        return new RefreshScope(
            summary.getProjectId(), summary.getGranularity(), summary.getPeriodKey(), summary.getSourceFactFingerprint()
        ).encode();
    }

    private Page<TimelinePeriodStatsRow> periodRows(
        UUID projectId, TimelineGranularity granularity, PageRequest pageable
    ) {
        return switch (granularity) {
            case WEEK -> factRepository.summarizeTimelineWeeks(projectId, null, null,
                com.projectflow.entity.ProjectFactRecordStatus.NEEDS_ATTENTION, pageable);
            case MONTH -> factRepository.summarizeTimelineMonths(projectId, null, null,
                com.projectflow.entity.ProjectFactRecordStatus.NEEDS_ATTENTION, pageable);
            default -> throw new IllegalArgumentException("Unsupported bootstrap granularity");
        };
    }

    private static void rejectPlanning(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            String field = entry.getKey().replace("_", "").toLowerCase(Locale.ROOT);
            if (PROHIBITED_FIELDS.contains(field)) throw new TimelineCoverageException("Timeline output contains planning fields");
            rejectPlanning(entry.getValue());
        });
        else if (node.isArray()) node.forEach(ProjectTimelineSummaryService::rejectPlanning);
        else if (node.isTextual()) {
            String text = node.asText();
            if (PROHIBITED_TEXT.stream().anyMatch(text::contains)) {
                throw new TimelineCoverageException("Timeline output contains future planning language");
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String bounded(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private String instant(Instant value) { return value == null ? "" : value.toString(); }

    public record RefreshOutcome(
        String resultJson,
        List<ModelGatewayService.ModelCallDiagnostics> diagnostics,
        boolean modelCalled
    ) {
    }

    private record Prepared(UUID summaryId, int sourceFactCount, String fingerprint) {
    }
    private record ParsedDraft(String summary, List<TextTheme> themes) {
    }
    private record TextTheme(String title, String summary, List<String> ids) {
    }
    private record ThemeDraft(String title, String summary, List<UUID> factIds) {
    }
    private record GeneratedSummary(String summary, List<ThemeDraft> themes, Set<UUID> coveredFactIds) {
    }
    private record ChunkDraft(String id, ParsedDraft draft, List<UUID> factIds) {
    }

    private record RefreshScope(UUID projectId, TimelineGranularity granularity, String periodKey, String fingerprint) {
        String encode() { return projectId + "|" + granularity + "|" + periodKey + "|" + fingerprint; }

        static RefreshScope parse(String value) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 4);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid timeline refresh scope");
            return new RefreshScope(UUID.fromString(parts[0]), TimelineGranularity.valueOf(parts[1]), parts[2], parts[3]);
        }
    }

    public static class TimelineCoverageException extends RuntimeException {
        public TimelineCoverageException(String message) { super(message); }
    }
}
