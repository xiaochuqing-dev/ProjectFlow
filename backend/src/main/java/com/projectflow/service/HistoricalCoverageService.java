package com.projectflow.service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.projectflow.dto.ProjectUnderstandingDtos.EvidenceSourceMapResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.EvolutionPreviewResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageBreakdown;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalPeriodCoverage;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.repository.ProjectFactCommitRefRepository;
import com.projectflow.repository.TimelineNamedCountRow;

@Service
public class HistoricalCoverageService {
    private static final int MAX_COMMIT_PERIOD_SAMPLE = 5_000;
    private static final int MAX_PERIODS = 120;

    private final LocalCommandExecutor commandExecutor;
    private final ProjectFactCommitRefRepository factCommitRefRepository;

    public HistoricalCoverageService(
        LocalCommandExecutor commandExecutor,
        ProjectFactCommitRefRepository factCommitRefRepository
    ) {
        this.commandExecutor = commandExecutor;
        this.factCommitRefRepository = factCommitRefRepository;
    }

    public HistoricalAnalysis analyze(
        UUID projectId,
        Path root,
        RepositoryIntakeResponse intake,
        EvidenceSourceMapResponse sourceMap
    ) {
        int documentHistoryCount = countCategories(sourceMap, Set.of("ADR", "CHANGELOG", "AGENT_RESULT"));
        int agentEvidenceCount = countCategories(sourceMap, Set.of("AGENT_RESULT"));
        double documentCoverage = ratio(documentHistoryCount, 10);
        double agentCoverage = ratio(agentEvidenceCount, 5);
        if (!intake.git().available() || intake.git().commitCount() <= 0) {
            List<String> limitations = new ArrayList<>();
            limitations.add("缺少本地 Git 提交，不能从当前文件反推完整项目历史。");
            if (documentHistoryCount > 0) {
                limitations.add("带历史价值的文档只能证明局部材料存在，不能替代版本历史。");
            }
            HistoricalCoverageBreakdown breakdown = new HistoricalCoverageBreakdown(
                0, 0, 0, documentCoverage, agentCoverage, 0, 0, 0, false, List.of()
            );
            double overall = aggregate(breakdown);
            HistoricalCoverageResponse coverage = new HistoricalCoverageResponse(
                documentHistoryCount > 0,
                documentHistoryCount > 0 ? "LIMITED_DOCUMENT_HISTORY" : "UNAVAILABLE",
                null,
                null,
                0,
                0,
                0,
                0,
                documentHistoryCount,
                agentEvidenceCount,
                List.of(),
                List.of(),
                Map.of(),
                overall,
                List.copyOf(limitations),
                breakdown
            );
            return new HistoricalAnalysis(
                coverage,
                new EvolutionPreviewResponse(
                    "CURRENT_STATE_ONLY",
                    "历史证据不足，仅展示当前状态；不生成 Timeline 或成熟阶段。",
                    0,
                    List.of(),
                    List.copyOf(limitations)
                ),
                0
            );
        }

        long commitCount = intake.git().commitCount();
        long coveredCommitCount = safeCoveredCommitCount(projectId);
        Instant earliest = parseFirstInstant(output(
            root,
            List.of("git", "log", "--max-parents=0", "--format=%cI", "HEAD"),
            8
        ));
        Instant latest = parseFirstInstant(output(
            root,
            List.of("git", "show", "-s", "--format=%cI", "HEAD"),
            5
        ));
        List<TagAnchor> tags = parseTags(output(
            root,
            List.of("git", "tag", "--list", "--sort=-creatordate", "--format=%(refname:short)|%(creatordate:iso-strict)"),
            8
        ));
        Map<String, Long> commitCounts = parsePeriodCounts(output(
            root,
            List.of(
                "git", "log", "--max-count=" + MAX_COMMIT_PERIOD_SAMPLE,
                "--date=format:%Y-%m", "--format=%ad", "HEAD"
            ),
            12
        ));
        List<String> periods = commitCounts.keySet().stream()
            .sorted(Comparator.reverseOrder())
            .limit(MAX_PERIODS)
            .toList();
        boolean sampleTruncated = commitCount > MAX_COMMIT_PERIOD_SAMPLE;
        int sampledCommitCount = Math.toIntExact(Math.min(commitCount, MAX_COMMIT_PERIOD_SAMPLE));
        Map<String, Long> factCounts = safeFactCounts(projectId, periods);
        Map<String, Integer> tagCounts = tagCounts(tags);
        List<HistoricalPeriodCoverage> periodCoverage = periods.stream()
            .map(period -> periodCoverage(
                period,
                commitCounts.getOrDefault(period, 0L),
                factCounts.getOrDefault(period, 0L),
                tagCounts.getOrDefault(period, 0),
                sampleTruncated
            ))
            .toList();
        Map<String, String> confidenceByPeriod = new LinkedHashMap<>();
        periodCoverage.forEach(period -> confidenceByPeriod.put(period.period(), confidenceLabel(period.confidence())));
        List<String> gaps = sampleTruncated ? List.of() : gapPeriods(periods);

        HistoricalCoverageBreakdown breakdown = new HistoricalCoverageBreakdown(
            commitCount <= 0 ? 0 : round((double) sampledCommitCount / commitCount),
            commitCount <= 0 ? 0 : round((double) coveredCommitCount / commitCount),
            tags.isEmpty() ? 0 : round(Math.min(1, tags.size() / 5.0)),
            documentCoverage,
            agentCoverage,
            0,
            0,
            sampledCommitCount,
            sampleTruncated,
            periodCoverage
        );
        double overall = aggregate(breakdown);
        List<String> limitations = new ArrayList<>();
        if (sampleTruncated) {
            limitations.add("提交量超过 " + MAX_COMMIT_PERIOD_SAMPLE + "，周期覆盖只基于最近 "
                + sampledCommitCount + " 次提交；未扫描部分不获得置信度。");
        }
        if (coveredCommitCount < commitCount) {
            limitations.add("ProjectFact 已覆盖 " + coveredCommitCount + "/" + commitCount + " 个本地提交引用。");
        }
        if (tags.isEmpty()) {
            limitations.add("没有本地 Tag 锚点；Git 元数据存在不等于项目一生已被语义重建。");
        }
        if (coveredCommitCount == 0 && tags.isEmpty()) {
            limitations.add("只有 Git 元数据时整体覆盖最多来自 25% 的元数据维度，不冒充完整历史理解。");
        }
        HistoricalCoverageResponse coverage = new HistoricalCoverageResponse(
            true,
            commitCount <= 5 ? "SHORT_GIT_HISTORY" : "GIT_HISTORY_AVAILABLE",
            earliest,
            latest,
            commitCount,
            coveredCommitCount,
            tags.size(),
            0,
            documentHistoryCount,
            agentEvidenceCount,
            periods,
            gaps,
            Map.copyOf(confidenceByPeriod),
            overall,
            List.copyOf(limitations),
            breakdown
        );
        return new HistoricalAnalysis(coverage, preview(commitCount, periods, tags, limitations), 3);
    }

    private static HistoricalPeriodCoverage periodCoverage(
        String period,
        long commits,
        long facts,
        int tags,
        boolean sampled
    ) {
        double factRatio = commits <= 0 ? 0 : Math.min(1, (double) facts / commits);
        double confidence = (sampled ? 0.15 : 0.25) + factRatio * 0.55 + (tags > 0 ? 0.15 : 0);
        String limitation;
        if (sampled) limitation = "该周期来自有界提交样本；更早周期可能未进入样本。";
        else if (facts == 0 && tags == 0) limitation = "该周期只有 Git 元数据，没有 Fact 或 Tag 语义锚点。";
        else limitation = "";
        return new HistoricalPeriodCoverage(
            period,
            commits,
            facts,
            tags,
            0,
            0,
            round(Math.min(0.95, confidence)),
            sampled,
            limitation
        );
    }

    private static double aggregate(HistoricalCoverageBreakdown value) {
        return round(
            value.gitMetadataCoverage() * 0.25
                + value.factCoverage() * 0.35
                + value.tagAnchorCoverage() * 0.10
                + value.documentHistoryCoverage() * 0.08
                + value.agentEvidenceCoverage() * 0.07
                + value.structuralSnapshotCoverage() * 0.10
                + value.remoteCollaborationCoverage() * 0.05
        );
    }

    private EvolutionPreviewResponse preview(
        long commitCount,
        List<String> periods,
        List<TagAnchor> tags,
        List<String> limitations
    ) {
        String mode;
        String strategy;
        int candidates;
        if (commitCount <= 5) {
            mode = "EARLY_PROJECT";
            strategy = "短历史只展示创建、首批实现和当前状态，不生成虚假成熟阶段。";
            candidates = (int) commitCount;
        } else if (commitCount <= MAX_COMMIT_PERIOD_SAMPLE) {
            mode = "MILESTONE_WINDOWS";
            strategy = "以 Tag、事实覆盖、月度提交密度和结构变化筛选候选窗口，再做有界语义归纳。";
            candidates = Math.min(15, Math.max(3, tags.size() + Math.max(1, periods.size() / 6)));
        } else {
            mode = "CLUSTERED_LONG_HISTORY";
            strategy = "按时间、Tag、变化密度和结构区域选择不超过 15 个窗口；禁止逐提交模型调用。";
            candidates = 15;
        }
        List<String> anchors = new ArrayList<>();
        tags.stream().limit(10).map(TagAnchor::name).forEach(anchors::add);
        if (anchors.isEmpty() && !periods.isEmpty()) {
            anchors.add(periods.get(periods.size() - 1) + " 起始证据");
            if (periods.size() > 1) anchors.add(periods.get(0) + " 当前证据");
        }
        return new EvolutionPreviewResponse(mode, strategy, candidates, List.copyOf(anchors), List.copyOf(limitations));
    }

    private long safeCoveredCommitCount(UUID projectId) {
        try {
            return factCommitRefRepository.countDistinctCommitShaByProjectId(projectId);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private Map<String, Long> safeFactCounts(UUID projectId, List<String> periods) {
        if (periods.isEmpty()) return Map.of();
        try {
            List<TimelineNamedCountRow> rows = factCommitRefRepository.countByTimelineMonths(projectId, periods);
            if (rows == null) return Map.of();
            Map<String, Long> result = new LinkedHashMap<>();
            rows.forEach(row -> result.put(row.getPeriodKey(), row.getItemCount()));
            return Map.copyOf(result);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private String output(Path root, List<String> command, int timeoutSeconds) {
        LocalCommandExecutor.CommandResult result = commandExecutor.execute(
            root,
            command,
            Duration.ofSeconds(timeoutSeconds)
        );
        return result.timedOut() || result.exitCode() != 0 ? "" : result.output().trim();
    }

    private static int countCategories(EvidenceSourceMapResponse sourceMap, Set<String> categories) {
        return sourceMap.sources().stream()
            .filter(source -> categories.contains(source.category()))
            .mapToInt(ignored -> 1)
            .sum();
    }

    private static Instant parseFirstInstant(String output) {
        if (output == null || output.isBlank()) return null;
        for (String line : output.lines().toList()) {
            try {
                return OffsetDateTime.parse(line.trim()).toInstant();
            } catch (RuntimeException ignored) {
                // Multiple Git roots are legal; keep looking for a parseable timestamp.
            }
        }
        return null;
    }

    private static List<TagAnchor> parseTags(String output) {
        if (output == null || output.isBlank()) return List.of();
        List<TagAnchor> result = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String[] parts = line.strip().split("\\|", 2);
            if (parts.length > 0 && !parts[0].isBlank()) {
                result.add(new TagAnchor(parts[0], parts.length > 1 ? parts[1] : ""));
            }
            if (result.size() >= 500) break;
        }
        return List.copyOf(result);
    }

    private static Map<String, Long> parsePeriodCounts(String output) {
        if (output == null || output.isBlank()) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        output.lines()
            .map(String::strip)
            .filter(value -> value.matches("\\d{4}-\\d{2}"))
            .limit(MAX_COMMIT_PERIOD_SAMPLE)
            .forEach(period -> result.merge(period, 1L, Long::sum));
        return Map.copyOf(result);
    }

    private static Map<String, Integer> tagCounts(List<TagAnchor> tags) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (TagAnchor tag : tags) {
            if (tag.occurredAt().length() >= 7) {
                String period = tag.occurredAt().substring(0, 7);
                if (period.matches("\\d{4}-\\d{2}")) result.merge(period, 1, Integer::sum);
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> gapPeriods(List<String> periods) {
        if (periods.size() < 2) return List.of();
        Set<YearMonth> present = new LinkedHashSet<>();
        periods.forEach(value -> present.add(YearMonth.parse(value)));
        YearMonth earliest = present.stream().min(Comparator.naturalOrder()).orElse(null);
        YearMonth latest = present.stream().max(Comparator.naturalOrder()).orElse(null);
        if (earliest == null || latest == null) return List.of();
        List<String> gaps = new ArrayList<>();
        for (YearMonth current = earliest; !current.isAfter(latest) && gaps.size() < 60; current = current.plusMonths(1)) {
            if (!present.contains(current)) gaps.add(current.toString());
        }
        return List.copyOf(gaps);
    }

    private static String confidenceLabel(double value) {
        if (value >= 0.75) return "HIGH";
        if (value >= 0.4) return "MEDIUM";
        return "LOW";
    }

    private static double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0 : round(Math.min(1, (double) numerator / denominator));
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 1000.0) / 1000.0;
    }

    private record TagAnchor(String name, String occurredAt) {
    }

    public record HistoricalAnalysis(
        HistoricalCoverageResponse coverage,
        EvolutionPreviewResponse evolutionPreview,
        int logicalToolCalls
    ) {
    }
}
