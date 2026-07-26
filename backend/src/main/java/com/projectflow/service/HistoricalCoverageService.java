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
import com.projectflow.dto.ProjectUnderstandingDtos.HistoricalCoverageResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.ProjectEvidenceSourceResponse;
import com.projectflow.dto.ProjectUnderstandingDtos.RepositoryIntakeResponse;
import com.projectflow.repository.ProjectFactCommitRefRepository;

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
        if (!intake.git().available() || intake.git().commitCount() <= 0) {
            List<String> limitations = new ArrayList<>();
            limitations.add("缺少本地 Git 提交，不能从当前文件反推完整项目历史。");
            if (documentHistoryCount > 0) {
                limitations.add("发现少量带历史价值的文档候选，但尚不足以替代版本历史。");
            }
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
                documentHistoryCount > 0 ? 0.15 : 0,
                List.copyOf(limitations)
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
        List<String> periods = parsePeriods(output(
            root,
            List.of(
                "git", "log", "--max-count=" + MAX_COMMIT_PERIOD_SAMPLE,
                "--date=format:%Y-%m", "--format=%ad", "HEAD"
            ),
            12
        ));
        List<String> gaps = commitCount <= MAX_COMMIT_PERIOD_SAMPLE ? gapPeriods(periods) : List.of();
        Map<String, String> confidenceByPeriod = confidenceByPeriod(periods, coveredCommitCount, commitCount);
        double factCoverage = commitCount <= 0 ? 0 : Math.min(1, (double) coveredCommitCount / commitCount);
        double overall = round(Math.min(0.95, 0.55 + factCoverage * 0.35 + (tags.isEmpty() ? 0 : 0.1)));
        List<String> limitations = new ArrayList<>();
        if (commitCount > MAX_COMMIT_PERIOD_SAMPLE) {
            limitations.add("提交量超过 " + MAX_COMMIT_PERIOD_SAMPLE + "，周期分布只采样最近提交，不逐提交调用模型。");
        }
        if (coveredCommitCount < commitCount) {
            limitations.add("ProjectFact 已覆盖 " + coveredCommitCount + "/" + commitCount + " 个本地提交引用。");
        }
        if (tags.isEmpty()) {
            limitations.add("没有本地 Tag/Release 锚点，里程碑只能依赖提交密度、事实和结构变化候选。");
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
            confidenceByPeriod,
            overall,
            List.copyOf(limitations)
        );
        EvolutionPreviewResponse preview = preview(commitCount, periods, tags, limitations);
        return new HistoricalAnalysis(coverage, preview, 3);
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
            strategy = "先按时间、Tag、变化密度和结构区域聚类，再选择不超过 15 个里程碑候选；禁止逐提交模型调用。";
            candidates = 15;
        }
        List<String> anchors = new ArrayList<>();
        tags.stream().limit(10).map(TagAnchor::name).forEach(anchors::add);
        if (anchors.isEmpty() && !periods.isEmpty()) {
            anchors.add(periods.get(periods.size() - 1) + " 起始证据");
            if (periods.size() > 1) anchors.add(periods.get(0) + " 当前证据");
        }
        return new EvolutionPreviewResponse(
            mode,
            strategy,
            candidates,
            List.copyOf(anchors),
            List.copyOf(limitations)
        );
    }

    private long safeCoveredCommitCount(UUID projectId) {
        try {
            return factCommitRefRepository.countDistinctCommitShaByProjectId(projectId);
        } catch (RuntimeException ignored) {
            return 0;
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
                // Try the next root commit when a repository has multiple roots.
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

    private static List<String> parsePeriods(String output) {
        if (output == null || output.isBlank()) return List.of();
        LinkedHashSet<String> periods = new LinkedHashSet<>();
        output.lines()
            .map(String::strip)
            .filter(value -> value.matches("\\d{4}-\\d{2}"))
            .limit(MAX_COMMIT_PERIOD_SAMPLE)
            .forEach(periods::add);
        return periods.stream()
            .sorted(Comparator.reverseOrder())
            .limit(MAX_PERIODS)
            .toList();
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

    private static Map<String, String> confidenceByPeriod(
        List<String> periods,
        long coveredCommitCount,
        long commitCount
    ) {
        String confidence = coveredCommitCount > 0 && coveredCommitCount >= commitCount ? "HIGH" : "MEDIUM";
        Map<String, String> result = new LinkedHashMap<>();
        periods.forEach(period -> result.put(period, confidence));
        return Map.copyOf(result);
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
