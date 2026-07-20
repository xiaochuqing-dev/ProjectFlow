package com.projectflow.service;

import static com.projectflow.dto.ProjectMemoryGatewayDtos.*;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.entity.ProjectCapability;
import com.projectflow.entity.ProjectCapabilityEvolution;
import com.projectflow.entity.ProjectCapabilityFact;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectTimelineSummary;
import com.projectflow.entity.ProjectTimelineTheme;
import com.projectflow.repository.ProjectCapabilityEvolutionRepository;
import com.projectflow.repository.ProjectCapabilityFactRepository;
import com.projectflow.repository.ProjectCapabilityRepository;
import com.projectflow.repository.ProjectFactRepository;
import com.projectflow.repository.ProjectRepository;
import com.projectflow.repository.ProjectTimelineSummaryRepository;
import com.projectflow.repository.ProjectTimelineThemeRepository;
import com.projectflow.support.AppException;

/** 跨事实与派生层的有界只读检索；不触发模型、不修改事实。 */
@Service
public class ProjectMemorySearchService {
    private static final String FACT_TRUTH = "FACTUAL_SOURCE";
    private static final String TIMELINE_TRUTH = "DERIVED_FROM_FACTS_TEMPORAL";
    private static final String CAPABILITY_TRUTH = "DERIVED_FROM_FACTS_CAPABILITY";
    private static final Pattern SEARCH_TOKEN = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9_\\-]{2,}");

    private final ProjectRepository projectRepository;
    private final ProjectFactRepository factRepository;
    private final ProjectTimelineSummaryRepository timelineSummaryRepository;
    private final ProjectTimelineThemeRepository timelineThemeRepository;
    private final ProjectCapabilityRepository capabilityRepository;
    private final ProjectCapabilityEvolutionRepository evolutionRepository;
    private final ProjectCapabilityFactRepository capabilityFactRepository;

    public ProjectMemorySearchService(
        ProjectRepository projectRepository,
        ProjectFactRepository factRepository,
        ProjectTimelineSummaryRepository timelineSummaryRepository,
        ProjectTimelineThemeRepository timelineThemeRepository,
        ProjectCapabilityRepository capabilityRepository,
        ProjectCapabilityEvolutionRepository evolutionRepository,
        ProjectCapabilityFactRepository capabilityFactRepository
    ) {
        this.projectRepository = projectRepository;
        this.factRepository = factRepository;
        this.timelineSummaryRepository = timelineSummaryRepository;
        this.timelineThemeRepository = timelineThemeRepository;
        this.capabilityRepository = capabilityRepository;
        this.evolutionRepository = evolutionRepository;
        this.capabilityFactRepository = capabilityFactRepository;
    }

    @Transactional(readOnly = true)
    public MemorySearchResponse search(
        UUID userId, UUID projectId, String query, Instant from, Instant to,
        String entityTypes, int page, int size, String detailLevel
    ) {
        projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AppException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
        String normalized = normalize(query);
        if (normalized.isBlank()) throw new AppException("MEMORY_QUERY_REQUIRED", "请输入要查询的项目记忆", HttpStatus.BAD_REQUEST);
        if (normalized.length() > 500) throw new AppException("MEMORY_QUERY_TOO_LONG", "查询内容不能超过 500 个字符", HttpStatus.BAD_REQUEST);
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException("INVALID_TIME_RANGE", "起始时间不能晚于结束时间", HttpStatus.BAD_REQUEST);
        }
        Set<SearchType> types = searchTypes(entityTypes);
        List<String> tokens = searchTokens(normalized);
        List<SearchCandidate> candidates = new ArrayList<>();
        if (types.contains(SearchType.FACT)) addFactCandidates(projectId, normalized, tokens, from, to, candidates);
        if (types.contains(SearchType.TIMELINE)) addTimelineCandidates(projectId, normalized, tokens, from, to, candidates);
        if (types.contains(SearchType.CAPABILITY)) addCapabilityCandidates(projectId, normalized, tokens, from, to, candidates);
        if (types.contains(SearchType.EVOLUTION)) addEvolutionCandidates(projectId, normalized, tokens, from, to, candidates);
        Map<String, SearchCandidate> unique = new LinkedHashMap<>();
        candidates.stream().sorted(SearchCandidate.ORDER).forEach(candidate ->
            unique.putIfAbsent(candidate.result().entityType() + ":" + candidate.result().entityId(), candidate));
        List<MemorySearchResultResponse> sorted = unique.values().stream().sorted(SearchCandidate.ORDER).map(SearchCandidate::result).toList();
        int bounded = clamp(size, detailed(detailLevel) ? 50 : 20, 10);
        int safePage = Math.max(0, page);
        int start = Math.min(sorted.size(), safePage * bounded);
        int end = Math.min(sorted.size(), start + bounded);
        int totalPages = sorted.isEmpty() ? 0 : (sorted.size() + bounded - 1) / bounded;
        return new MemorySearchResponse(
            projectId, types.stream().map(Enum::name).toList(), sorted.subList(start, end), safePage,
            bounded, sorted.size(), totalPages, safePage + 1 < totalPages
        );
    }

    private void addFactCandidates(UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target) {
        LinkedHashMap<UUID, ProjectFact> facts = new LinkedHashMap<>();
        parseUuid(query).flatMap(id -> factRepository.findByIdAndProjectId(id, projectId)).ifPresent(value -> facts.put(value.getId(), value));
        for (String token : tokens.stream().limit(4).toList()) {
            factRepository.searchMemoryCandidates(projectId, token, from, to, PageRequest.of(0, 100))
                .forEach(value -> facts.putIfAbsent(value.getId(), value));
        }
        Map<UUID, List<UUID>> related = relatedCapabilities(projectId, new ArrayList<>(facts.keySet()));
        for (ProjectFact fact : facts.values()) {
            Instant occurred = eventAt(fact);
            if (!within(occurred, from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", fact.getId().toString(), "标题", fact.getTitle(), "摘要", fact.getSummary(),
                "主要变化", String.join(" ", fact.getMainChanges()), "用户价值", fact.getUserVisibleValue(), "关注原因", fact.getAttentionReason()));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "FACT", fact.getId(), text(fact.getTitle()), text(fact.getSummary()), occurred,
                fact.getOccurredFrom(), fact.getOccurredTo(), match.reason(), match.fields(), related.getOrDefault(fact.getId(), List.of()),
                FACT_TRUTH, "/api/projects/" + projectId + "/project-memory/facts/" + fact.getId() + "/trace")));
        }
    }

    private void addTimelineCandidates(UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target) {
        List<ProjectTimelineSummary> summaries = timelineSummaryRepository.findByProjectIdOrderByUpdatedAtDesc(projectId);
        Map<UUID, ProjectTimelineSummary> byId = summaries.stream().collect(Collectors.toMap(ProjectTimelineSummary::getId, Function.identity()));
        for (ProjectTimelineSummary item : summaries) {
            if (!overlaps(item.getPeriodStart(), item.getPeriodEnd(), from, to)) continue;
            String title = item.getGranularity().name() + " " + item.getPeriodKey();
            Match match = match(query, tokens, Map.of("stable id", item.getId().toString(), "时间段", title, "摘要", item.getSummary()));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "TIMELINE_PERIOD", item.getId(), title, text(item.getSummary()), item.getPeriodEnd(), item.getPeriodStart(),
                item.getPeriodEnd(), match.reason(), match.fields(), List.of(), TIMELINE_TRUTH,
                "/api/projects/" + projectId + "/project-memory/timeline?granularity=" + item.getGranularity().name() + "&periodKey=" + item.getPeriodKey())));
        }
        for (ProjectTimelineTheme item : timelineThemeRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)) {
            ProjectTimelineSummary summary = byId.get(item.getSummaryId());
            Instant start = summary == null ? null : summary.getPeriodStart();
            Instant end = summary == null ? null : summary.getPeriodEnd();
            if (!overlaps(start, end, from, to)) continue;
            Match match = match(query, tokens, Map.of("stable id", item.getId().toString(), "主题", item.getTitle(), "摘要", item.getSummary()));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "TIMELINE_THEME", item.getId(), item.getTitle(), text(item.getSummary()), end, start, end, match.reason(), match.fields(),
                List.of(item.getSummaryId()), TIMELINE_TRUTH, summary == null ? "" : "/api/projects/" + projectId
                    + "/project-memory/timeline?granularity=" + summary.getGranularity().name() + "&periodKey=" + summary.getPeriodKey())));
        }
    }

    private void addCapabilityCandidates(UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target) {
        for (ProjectCapability item : capabilityRepository.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            Instant occurred = firstNonNull(item.getLastEnhancedAt(), item.getFirstFormedAt());
            if (!within(occurred, from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", item.getId().toString(), "能力名称", item.getCanonicalName(), "别名", String.join(" ", item.getAliases()),
                "摘要", item.getCurrentSummary(), "解决问题", item.getProblemSolved(), "长期价值", item.getLongTermValue(),
                "产品区域", String.join(" ", item.getProductAreas())));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "CAPABILITY", item.getId(), item.getCanonicalName(), text(item.getCurrentSummary()), occurred,
                item.getFirstFormedAt(), item.getLastEnhancedAt(), match.reason(), match.fields(),
                item.getMergedIntoCapabilityId() == null ? List.of() : List.of(item.getMergedIntoCapabilityId()), CAPABILITY_TRUTH,
                "/api/projects/" + projectId + "/project-memory/capabilities/" + item.getId() + "/evolution")));
        }
    }

    private void addEvolutionCandidates(UUID projectId, String query, List<String> tokens, Instant from, Instant to, List<SearchCandidate> target) {
        List<ProjectCapabilityEvolution> items = evolutionRepository
            .findByProjectIdOrderByOccurredAtDescCreatedAtDesc(projectId, PageRequest.of(0, 2_000)).getContent();
        Set<UUID> capabilityIds = items.stream().map(ProjectCapabilityEvolution::getCapabilityId).collect(Collectors.toSet());
        Map<UUID, String> names = capabilityRepository.findAllById(capabilityIds).stream()
            .collect(Collectors.toMap(ProjectCapability::getId, ProjectCapability::getCanonicalName));
        for (ProjectCapabilityEvolution item : items) {
            if (!within(item.getOccurredAt(), from, to)) continue;
            Match match = match(query, tokens, Map.of(
                "stable id", item.getId().toString(), "能力名称", names.getOrDefault(item.getCapabilityId(), ""),
                "演进标题", item.getTitle(), "演进摘要", item.getSummary(), "来源时间段", String.join(" ", item.getSourceTimelinePeriods())));
            if (match.score() <= 0) continue;
            target.add(new SearchCandidate(match.score(), new MemorySearchResultResponse(
                "EVOLUTION", item.getId(), item.getTitle(), text(item.getSummary()), item.getOccurredAt(), item.getOccurredAt(),
                item.getOccurredAt(), match.reason(), match.fields(), List.of(item.getCapabilityId()), CAPABILITY_TRUTH,
                "/api/projects/" + projectId + "/project-memory/capabilities/" + item.getCapabilityId() + "/evolution")));
        }
    }

    private Map<UUID, List<UUID>> relatedCapabilities(UUID projectId, List<UUID> factIds) {
        if (factIds.isEmpty()) return Map.of();
        Map<UUID, LinkedHashSet<UUID>> grouped = new LinkedHashMap<>();
        for (ProjectCapabilityFact link : capabilityFactRepository.findByProjectIdAndFactIdIn(projectId, factIds)) {
            grouped.computeIfAbsent(link.getFactId(), ignored -> new LinkedHashSet<>()).add(link.getCapabilityId());
        }
        return grouped.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    private Match match(String query, List<String> tokens, Map<String, String> fields) {
        int score = 0;
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = normalize(entry.getValue());
            if (value.isBlank()) continue;
            int weight = "stable id".equals(entry.getKey()) ? 100 : nameField(entry.getKey()) ? 45 : 20;
            if (value.equals(query)) { score += weight + 50; matched.add(entry.getKey()); continue; }
            if (value.contains(query)) { score += weight; matched.add(entry.getKey()); }
            for (String token : tokens) if (value.contains(token)) { score += Math.max(2, weight / 4); matched.add(entry.getKey()); }
        }
        List<String> fieldsMatched = List.copyOf(matched);
        return new Match(score, fieldsMatched, fieldsMatched.isEmpty() ? "" : "匹配" + String.join("、", fieldsMatched));
    }

    private Set<SearchType> searchTypes(String value) {
        if (value == null || value.isBlank()) return EnumSet.allOf(SearchType.class);
        EnumSet<SearchType> types = EnumSet.noneOf(SearchType.class);
        for (String raw : value.split(",")) {
            try { types.add(SearchType.valueOf(raw.trim().toUpperCase(Locale.ROOT))); }
            catch (RuntimeException exception) { throw new AppException("INVALID_MEMORY_ENTITY_TYPE", "无效的项目记忆实体类型", HttpStatus.BAD_REQUEST); }
        }
        return types.isEmpty() ? EnumSet.allOf(SearchType.class) : types;
    }

    private List<String> searchTokens(String normalized) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = SEARCH_TOKEN.matcher(normalized);
        while (matcher.find() && tokens.size() < 8) tokens.add(matcher.group());
        if (tokens.isEmpty()) tokens.add(normalized);
        return tokens.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    private static String text(String value) { String safe = value == null ? "" : value.trim(); return safe.length() <= 600 ? safe : safe.substring(0, 599) + "…"; }
    private static boolean detailed(String value) { return "detailed".equalsIgnoreCase(value == null ? "" : value.trim()); }
    private static int clamp(int value, int maximum, int fallback) { return Math.max(1, Math.min(maximum, value <= 0 ? fallback : value)); }
    private static Instant eventAt(ProjectFact fact) { return fact.getOccurredTo() == null ? fact.getOccurredFrom() : fact.getOccurredTo(); }
    private static Instant firstNonNull(Instant first, Instant second) { return first == null ? second : first; }
    private static boolean within(Instant value, Instant from, Instant to) { return from == null && to == null || value != null && (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to)); }
    private static boolean overlaps(Instant start, Instant end, Instant from, Instant to) {
        if (from == null && to == null) return true;
        Instant effectiveStart = start == null ? end : start, effectiveEnd = end == null ? start : end;
        return effectiveStart != null && (to == null || !effectiveStart.isAfter(to)) && (from == null || !effectiveEnd.isBefore(from));
    }
    private static boolean nameField(String field) { return field.contains("标题") || field.contains("名称") || field.contains("主题") || field.contains("时间段") || field.contains("别名"); }
    private static java.util.Optional<UUID> parseUuid(String value) { try { return java.util.Optional.of(UUID.fromString(value)); } catch (RuntimeException exception) { return java.util.Optional.empty(); } }

    private enum SearchType { FACT, TIMELINE, CAPABILITY, EVOLUTION }
    private record Match(int score, List<String> fields, String reason) {}
    private record SearchCandidate(int score, MemorySearchResultResponse result) {
        private static final Comparator<SearchCandidate> ORDER = Comparator.comparingInt(SearchCandidate::score).reversed()
            .thenComparing(candidate -> candidate.result().occurredAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.result().entityId());
    }
}
