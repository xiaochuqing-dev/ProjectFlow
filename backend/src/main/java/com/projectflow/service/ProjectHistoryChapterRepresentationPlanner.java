package com.projectflow.service;

import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds a bounded, auditable representation plan from already validated
 * Story presentation. It never changes facts, Evidence, roles or Claim state.
 */
public final class ProjectHistoryChapterRepresentationPlanner {
    public static final String PLAN_VERSION = "project-history-chapter-representation-v2";
    static final int MAX_REPRESENTATIVE_CLUSTERS = 4;
    static final int MAX_REPRESENTATIVE_STORIES_PER_CLUSTER = 3;
    static final int MIN_PRIMARY_PER_SPLIT = 4;
    private static final int MAX_SPLIT_DEPTH = 2;
    private static final double REPRESENTATIVE_COVERAGE_TARGET = 0.72;
    private static final double LOW_REPRESENTATIVE_COVERAGE = 0.60;
    private static final double CO_DOMINANT_WEIGHT_RATIO = 0.80;
    private static final Set<String> GENERIC_LABELS = Set.of(
        "项目核心结果", "项目材料", "项目阶段成果", "阶段成果记录", "项目成果", "项目成果记录",
        "项目文档", "项目阶段文档", "项目使用说明"
    );
    private static final Set<String> GENERIC_ASCII_TOPICS = Set.of(
        "project", "projects", "result", "results", "content", "contents", "change", "changes",
        "feature", "features", "record", "records", "current", "stage", "phase", "item", "items",
        "test", "tests", "validation", "validate", "config", "configuration", "fixture", "fixtures",
        "json", "html", "java", "data", "page", "pages", "service", "system"
    );
    private static final Set<String> GENERIC_HAN_TOPICS = Set.of(
        "项目", "结果", "内容", "阶段", "时期", "记录", "变化", "相关", "当前", "形成", "完善", "建立",
        "功能", "能力", "工作", "材料", "成果", "进行", "调整", "更新", "实现", "完成", "整理", "新增"
    );

    private final ProjectHistoryLanguageService language;

    public ProjectHistoryChapterRepresentationPlanner(ProjectHistoryLanguageService language) {
        this.language = language == null ? new ProjectHistoryLanguageService() : language;
    }

    public Plan plan(List<ChangeStory> members) {
        List<ChangeStory> ordered = ordered(members);
        Plan base = basePlan(ordered);
        return base.withNeedsSplit(base.coherenceRisk() && bestBoundary(ordered, base) != null);
    }

    /**
     * Split only at a strong chronological semantic shift. Supporting stories
     * follow their Primary owner, so membership remains complete and disjoint.
     */
    public List<List<ChangeStory>> split(List<ChangeStory> members) {
        return split(ordered(members), 0);
    }

    private List<List<ChangeStory>> split(List<ChangeStory> members, int depth) {
        Plan current = basePlan(members);
        if (!current.coherenceRisk() || depth >= MAX_SPLIT_DEPTH) return List.of(List.copyOf(members));
        Boundary boundary = bestBoundary(members, current);
        if (boundary == null) return List.of(List.copyOf(members));
        Partition partition = partition(members, boundary.leftPrimaryIds());
        if (primaryCount(partition.left()) < MIN_PRIMARY_PER_SPLIT
            || primaryCount(partition.right()) < MIN_PRIMARY_PER_SPLIT) {
            return List.of(List.copyOf(members));
        }
        List<List<ChangeStory>> result = new ArrayList<>();
        result.addAll(split(partition.left(), depth + 1));
        result.addAll(split(partition.right(), depth + 1));
        return List.copyOf(result);
    }

    private Plan basePlan(List<ChangeStory> ordered) {
        List<ChangeStory> primaries = ordered.stream().filter(ChangeStory::primary).toList();
        int supportingCount = Math.max(0, ordered.size() - primaries.size());
        if (primaries.isEmpty()) {
            return new Plan(List.of(), List.of(), List.of(), List.of(), 0, supportingCount,
                0.0, false, false, List.of(), List.of(), fingerprint(List.of(), List.of()));
        }

        Map<String, MutableCluster> builders = new LinkedHashMap<>();
        Map<String, String> familyByPrimary = new LinkedHashMap<>();
        for (ChangeStory story : primaries) {
            String family = family(story);
            familyByPrimary.put(story.id(), family);
            builders.computeIfAbsent(family, ignored -> new MutableCluster(family)).primaries.add(story);
        }
        for (ChangeStory story : ordered) {
            if (story.primary()) continue;
            String family = familyByPrimary.get(story.primaryStoryId());
            if (family == null || family.isBlank()) {
                family = builders.values().stream()
                    .filter(value -> value.primaries.stream().anyMatch(primary -> primary.supportingChangeRefs().contains(story.id())))
                    .map(value -> value.family).findFirst().orElse("");
            }
            MutableCluster builder = builders.get(family);
            if (builder != null) builder.supportingCount++;
        }

        List<Cluster> ranked = builders.values().stream().map(this::cluster)
            .sorted(Comparator.comparingDouble(Cluster::weight).reversed()
                .thenComparing(Comparator.comparingInt(Cluster::primaryStoryCount).reversed())
                .thenComparing(Cluster::from, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Cluster::id))
            .toList();
        double topWeight = ranked.get(0).weight();
        int topCount = ranked.get(0).primaryStoryCount();
        List<Cluster> roleAssigned = new ArrayList<>();
        int coDominantCount = 0;
        for (int index = 0; index < ranked.size(); index++) {
            Cluster cluster = ranked.get(index);
            String role = "MINOR";
            if (index == 0) role = "DOMINANT";
            else if (coDominantCount < 2
                && cluster.weight() >= topWeight * CO_DOMINANT_WEIGHT_RATIO
                && (topCount <= 2
                    || cluster.primaryStoryCount() >= Math.max(1, (int) Math.ceil(topCount * 0.60)))) {
                role = "CO_DOMINANT";
                coDominantCount++;
            }
            roleAssigned.add(cluster.withRole(role));
        }

        List<Cluster> selected = new ArrayList<>();
        double totalWeight = roleAssigned.stream().mapToDouble(Cluster::weight).sum();
        double selectedWeight = 0.0;
        for (Cluster cluster : roleAssigned) {
            if (selected.size() >= MAX_REPRESENTATIVE_CLUSTERS) break;
            selected.add(cluster);
            selectedWeight += cluster.weight();
            double coverage = totalWeight <= 0.0 ? 0.0 : selectedWeight / totalWeight;
            if (coverage >= REPRESENTATIVE_COVERAGE_TARGET && selected.size() >= Math.min(2, roleAssigned.size())) break;
        }
        double coverage = totalWeight <= 0.0 ? 0.0 : selectedWeight / totalWeight;
        List<String> dominantIds = roleAssigned.stream()
            .filter(value -> !"MINOR".equals(value.role())).map(Cluster::id).toList();
        List<String> selectedIds = selected.stream().map(Cluster::id).toList();
        boolean unrelatedMajor = unrelatedMajorClusters(roleAssigned, primaries.size());
        boolean coherenceRisk = primaries.size() >= MIN_PRIMARY_PER_SPLIT * 2
            && (coverage < LOW_REPRESENTATIVE_COVERAGE || unrelatedMajor);
        List<String> unknowns = selected.stream().flatMap(value -> value.unknowns().stream()).distinct().limit(20).toList();
        List<String> conflicts = selected.stream().flatMap(value -> value.conflicts().stream()).distinct().limit(20).toList();
        return new Plan(
            List.copyOf(roleAssigned), List.copyOf(selected), selectedIds, dominantIds,
            primaries.size(), supportingCount, coverage, coherenceRisk, false,
            unknowns, conflicts, fingerprint(roleAssigned, selectedIds)
        );
    }

    private Cluster cluster(MutableCluster builder) {
        List<ChangeStory> stories = builder.primaries.stream()
            .sorted(Comparator.comparing(ChangeStory::occurredFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id)).toList();
        Instant from = stories.stream().map(ChangeStory::occurredFrom).filter(java.util.Objects::nonNull)
            .min(Instant::compareTo).orElse(null);
        Instant to = stories.stream().map(ChangeStory::occurredTo).filter(java.util.Objects::nonNull)
            .max(Instant::compareTo).orElse(null);
        long activeDays = from == null || to == null ? 1L : Math.max(1L, Duration.between(from, to).toDays() + 1L);
        long activeMonths = stories.stream().map(ChangeStory::occurredFrom).filter(java.util.Objects::nonNull)
            .map(value -> YearMonth.from(value.atZone(ZoneOffset.UTC))).distinct().count();
        double weight = (2.0 + Math.sqrt(stories.size())
            + Math.min(1.0, Math.log1p(activeDays) / 4.0)
            + Math.min(1.0, Math.max(0L, activeMonths - 1L) * 0.20));
        ChangeStory headlineStory = stories.stream().max(
            Comparator.comparingInt(ProjectHistoryChapterRepresentationPlanner::storyClaimRank)
                .thenComparing(ChangeStory::occurredTo, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id)
        ).orElse(stories.get(0));
        String label = label(headlineStory);
        if (label.isBlank() || GENERIC_LABELS.contains(label)) {
            label = stories.stream().map(this::label).filter(value -> !value.isBlank())
                .filter(value -> !GENERIC_LABELS.contains(value)).findFirst().orElseGet(() -> label(stories.get(0)));
        }
        weight *= explainabilityFactor(label, builder.family);
        List<ChangeStory> representatives = representatives(stories, headlineStory);
        List<String> representativeOutcomes = representatives.stream().map(ChangeStory::humanTitle)
            .filter(value -> value != null && !value.isBlank()).distinct().toList();
        String headlineOutcome = safe(headlineStory.humanTitle());
        if (headlineOutcome.isBlank() && !representativeOutcomes.isEmpty()) {
            headlineOutcome = representativeOutcomes.get(0);
        }
        List<String> grounding = stories.stream()
            .flatMap(story -> Stream.of(story.humanTitle(), story.oneSentenceSummary()))
            .filter(value -> value != null && !value.isBlank()).distinct().toList();
        List<String> states = stories.stream().map(ChangeStory::claimAttribution)
            .filter(java.util.Objects::nonNull).map(value -> value.state()).filter(value -> value != null && !value.isBlank())
            .map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        String ceiling = states.stream().max(Comparator.comparingInt(ProjectHistoryChapterRepresentationPlanner::claimRank))
            .orElse("UNKNOWN");
        Set<String> topics = stories.stream().flatMap(story -> topics(story).stream())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> areas = stories.stream().flatMap(story -> story.affectedAreas().stream())
            .map(ProjectHistoryChapterRepresentationPlanner::normalizeArea).filter(value -> !value.isBlank())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new Cluster(
            "cluster-" + ProjectHistorySourceCollector.sha256(builder.family).substring(0, 16),
            builder.family, label, headlineOutcome, "MINOR", weight, stories.size(), builder.supportingCount, activeDays,
            from, to, stories.stream().map(ChangeStory::id).toList(), representatives.stream().map(ChangeStory::id).toList(),
            representativeOutcomes, states, ceiling,
            stories.stream().flatMap(story -> story.unknowns().stream()).distinct().limit(12).toList(),
            stories.stream().flatMap(story -> story.conflicts().stream()).distinct().limit(12).toList(),
            grounding, topics, areas
        );
    }

    private Boundary bestBoundary(List<ChangeStory> members, Plan base) {
        if (!base.coherenceRisk()) return null;
        List<ChangeStory> primary = members.stream().filter(ChangeStory::primary).toList();
        Boundary best = null;
        for (int index = MIN_PRIMARY_PER_SPLIT; index <= primary.size() - MIN_PRIMARY_PER_SPLIT; index++) {
            List<ChangeStory> leftPrimary = primary.subList(0, index);
            List<ChangeStory> rightPrimary = primary.subList(index, primary.size());
            Plan left = basePlan(leftPrimary);
            Plan right = basePlan(rightPrimary);
            if (left.clusters().isEmpty() || right.clusters().isEmpty()) continue;
            Cluster leftTop = left.clusters().get(0);
            Cluster rightTop = right.clusters().get(0);
            if (related(leftTop, rightTop)) continue;
            double leftConcentration = (double) leftTop.primaryStoryCount() / leftPrimary.size();
            double rightConcentration = (double) rightTop.primaryStoryCount() / rightPrimary.size();
            Instant leftTo = leftPrimary.get(leftPrimary.size() - 1).occurredTo();
            Instant rightFrom = rightPrimary.get(0).occurredFrom();
            long gapDays = leftTo == null || rightFrom == null ? 0L
                : Math.max(0L, Duration.between(leftTo, rightFrom).toDays());
            boolean concentratedShift = leftConcentration >= 0.35 && rightConcentration >= 0.35;
            if (!concentratedShift && gapDays < 3L) continue;
            double combinedCoverage = (left.representativePrimaryCoverage() * leftPrimary.size()
                + right.representativePrimaryCoverage() * rightPrimary.size()) / primary.size();
            double gain = combinedCoverage - base.representativePrimaryCoverage();
            if (gain < 0.08 && gapDays < 7L) continue;
            double balance = (double) Math.min(leftPrimary.size(), rightPrimary.size()) / primary.size();
            double score = gain * 3.0 + balance + Math.min(1.0, gapDays / 14.0)
                + Math.min(leftConcentration, rightConcentration);
            if (best == null || score > best.score()) {
                best = new Boundary(
                    leftPrimary.stream().map(ChangeStory::id)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll), score
                );
            }
        }
        return best;
    }

    private Partition partition(List<ChangeStory> members, Set<String> leftPrimaryIds) {
        Set<String> allPrimaryIds = members.stream().filter(ChangeStory::primary).map(ChangeStory::id)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> rightPrimaryIds = new LinkedHashSet<>(allPrimaryIds);
        rightPrimaryIds.removeAll(leftPrimaryIds);
        Map<String, Boolean> sideByPrimary = new LinkedHashMap<>();
        leftPrimaryIds.forEach(id -> sideByPrimary.put(id, true));
        rightPrimaryIds.forEach(id -> sideByPrimary.put(id, false));
        Instant boundaryTime = members.stream().filter(ChangeStory::primary)
            .filter(story -> rightPrimaryIds.contains(story.id())).map(ChangeStory::occurredFrom)
            .filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null);
        List<ChangeStory> left = new ArrayList<>();
        List<ChangeStory> right = new ArrayList<>();
        for (ChangeStory story : members) {
            Boolean leftSide = story.primary() ? sideByPrimary.get(story.id()) : sideByPrimary.get(story.primaryStoryId());
            if (leftSide == null && story.supporting()) {
                leftSide = members.stream().filter(ChangeStory::primary)
                    .filter(primary -> primary.supportingChangeRefs().contains(story.id()))
                    .map(primary -> sideByPrimary.get(primary.id())).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            }
            if (leftSide == null) {
                leftSide = boundaryTime == null || story.occurredFrom() == null || story.occurredFrom().isBefore(boundaryTime);
            }
            (leftSide ? left : right).add(story);
        }
        return new Partition(ordered(left), ordered(right));
    }

    private boolean unrelatedMajorClusters(List<Cluster> clusters, int primaryCount) {
        List<Cluster> major = clusters.stream()
            .filter(value -> value.primaryStoryCount() >= Math.max(2, (int) Math.ceil(primaryCount * 0.20)))
            .limit(4).toList();
        for (int left = 0; left < major.size(); left++) {
            for (int right = left + 1; right < major.size(); right++) {
                if (!related(major.get(left), major.get(right))) return true;
            }
        }
        return false;
    }

    private static boolean related(Cluster left, Cluster right) {
        if (left.family().equals(right.family())) return true;
        if (left.topics().stream().anyMatch(right.topics()::contains)) return true;
        Set<String> sharedAreas = new LinkedHashSet<>(left.areas());
        sharedAreas.retainAll(right.areas());
        if (sharedAreas.isEmpty()) return false;
        if (left.from() == null || left.to() == null || right.from() == null || right.to() == null) return false;
        return !left.to().isBefore(right.from().minus(Duration.ofDays(7)))
            && !right.to().isBefore(left.from().minus(Duration.ofDays(7)));
    }

    private List<ChangeStory> representatives(List<ChangeStory> stories, ChangeStory headlineStory) {
        if (stories.size() <= MAX_REPRESENTATIVE_STORIES_PER_CLUSTER) return List.copyOf(stories);
        LinkedHashSet<Integer> indices = new LinkedHashSet<>();
        indices.add(0);
        indices.add(stories.size() - 1);
        indices.add(Math.max(0, stories.indexOf(headlineStory)));
        if (indices.size() < MAX_REPRESENTATIVE_STORIES_PER_CLUSTER) indices.add(stories.size() / 2);
        List<Integer> selected = indices.stream().limit(MAX_REPRESENTATIVE_STORIES_PER_CLUSTER).sorted().toList();
        return selected.stream().map(stories::get).toList();
    }

    private String family(ChangeStory story) {
        String label = label(story);
        // Generic presentation labels are deliberately one low-weight digest
        // family. Splitting them again by internal subject keys lets dozens of
        // tiny document/material clusters dilute the real phase outcomes.
        String value = label;
        if (value.isBlank()) value = story.affectedAreas().stream().findFirst().orElse("project-material");
        value = value.replaceAll("(?i)[0-9a-f]{12,}", " ").replaceAll("\\d+", " ")
            .replaceAll("[\\s_-]+", " ").trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "project-material";
        return semanticFamily(value);
    }

    static String semanticFamily(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        String candidate = normalized.replaceFirst(
            "^(前后端|前端|后端|客户端|服务端|管理端|移动端|桌面端|网页端)\\s*", ""
        ).trim();
        if (candidate.codePointCount(0, candidate.length()) >= 4) return candidate;
        candidate = normalized.replaceFirst(
            "^(front ?end|back ?end|full ?stack|client|server|admin|mobile|desktop|web)\\s+", ""
        ).trim();
        return candidate.codePointCount(0, candidate.length()) >= 5 ? candidate : normalized;
    }

    private String label(ChangeStory story) {
        return safe(language.readableObject(
            story.primarySubjectKey(), story.technicalDetails(), story.affectedAreas()
        ));
    }

    private static Set<String> topics(ChangeStory story) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String attributedSubject = story.claimAttribution() == null ? "" : story.claimAttribution().subject();
        // Topic relation is anchored to stable subjects, not repeated action
        // words in presentation sentences (for example "建立…并形成…").
        Stream.of(story.primarySubjectKey(), attributedSubject)
            .filter(value -> value != null && !value.isBlank()).forEach(value -> {
                String camelSplit = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
                for (String token : camelSplit.split("[^\\p{L}\\p{N}]+")) {
                    if (token.length() >= 3 && !GENERIC_ASCII_TOPICS.contains(token) && !token.matches("\\d+")) {
                        result.add(token);
                    }
                }
                String han = value.replaceAll("[^\\p{IsHan}]", "");
                for (int index = 0; index + 1 < han.length(); index++) {
                    String token = han.substring(index, index + 2);
                    if (!GENERIC_HAN_TOPICS.contains(token)) result.add(token);
                }
            });
        return Set.copyOf(result);
    }

    private static int claimRank(String value) {
        return switch (safe(value).toUpperCase(Locale.ROOT)) {
            case "VERIFIED" -> 6;
            case "IMPLEMENTED", "REMOVED", "RESTORED" -> 5;
            case "CONFIGURED" -> 4;
            case "OBSERVED" -> 3;
            case "DECLARED" -> 2;
            case "PLANNED" -> 1;
            default -> 0;
        };
    }

    private static int storyClaimRank(ChangeStory story) {
        return story == null || story.claimAttribution() == null
            ? 0
            : claimRank(story.claimAttribution().state());
    }

    private static double explainabilityFactor(String label, String family) {
        String safeLabel = safe(label);
        String safeFamily = safe(family).toLowerCase(Locale.ROOT);
        if (GENERIC_LABELS.contains(safeLabel)
            || safeFamily.startsWith("merge pull request")
            || safeFamily.equals("pull request")
            || safeFamily.startsWith("merge project")
            || safeFamily.startsWith("project area")) {
            return 0.20;
        }
        boolean technicalToken = safeLabel.codePoints().anyMatch(codePoint ->
            codePoint < 128 && Character.isLetterOrDigit(codePoint)
        ) || safeLabel.contains("target") || safeLabel.contains("next");
        return technicalToken ? 0.45 : 1.0;
    }

    private static String normalizeArea(String value) {
        String safe = safe(value).replace('\\', '/').toLowerCase(Locale.ROOT);
        if (safe.isBlank() || safe.equals("项目根目录")) return "";
        String[] parts = safe.split("/");
        return parts.length <= 2 ? safe : parts[0] + "/" + parts[1];
    }

    private static int primaryCount(List<ChangeStory> stories) {
        return (int) stories.stream().filter(ChangeStory::primary).count();
    }

    private static List<ChangeStory> ordered(List<ChangeStory> values) {
        return (values == null ? List.<ChangeStory>of() : values).stream().filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(ChangeStory::occurredFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChangeStory::id)).toList();
    }

    private static String fingerprint(List<Cluster> clusters, List<String> selectedIds) {
        StringBuilder value = new StringBuilder(PLAN_VERSION);
        for (Cluster cluster : clusters) {
            value.append('|').append(cluster.id()).append(':').append(cluster.role()).append(':')
                .append(cluster.primaryStoryCount()).append(':').append(cluster.claimCeiling()).append(':')
                .append(String.join(",", cluster.primaryStoryIds()));
        }
        value.append("|selected=").append(String.join(",", selectedIds));
        return ProjectHistorySourceCollector.sha256(value.toString());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableCluster {
        private final String family;
        private final List<ChangeStory> primaries = new ArrayList<>();
        private int supportingCount;

        private MutableCluster(String family) {
            this.family = family;
        }
    }

    private record Boundary(Set<String> leftPrimaryIds, double score) {
        private Boundary {
            leftPrimaryIds = Set.copyOf(leftPrimaryIds == null ? Set.of() : leftPrimaryIds);
        }
    }

    private record Partition(List<ChangeStory> left, List<ChangeStory> right) {
    }

    public record Cluster(
        String id,
        String family,
        String humanLabel,
        String headlineOutcome,
        String role,
        double weight,
        int primaryStoryCount,
        int supportingStoryCount,
        long activeDays,
        Instant from,
        Instant to,
        List<String> primaryStoryIds,
        List<String> representativeStoryIds,
        List<String> representativeOutcomes,
        List<String> allowedClaimStates,
        String claimCeiling,
        List<String> unknowns,
        List<String> conflicts,
        List<String> grounding,
        Set<String> topics,
        Set<String> areas
    ) {
        public Cluster {
            primaryStoryIds = List.copyOf(primaryStoryIds == null ? List.of() : primaryStoryIds);
            representativeStoryIds = List.copyOf(representativeStoryIds == null ? List.of() : representativeStoryIds);
            representativeOutcomes = List.copyOf(representativeOutcomes == null ? List.of() : representativeOutcomes);
            allowedClaimStates = List.copyOf(allowedClaimStates == null ? List.of() : allowedClaimStates);
            unknowns = List.copyOf(unknowns == null ? List.of() : unknowns);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            grounding = List.copyOf(grounding == null ? List.of() : grounding);
            topics = Set.copyOf(topics == null ? Set.of() : topics);
            areas = Set.copyOf(areas == null ? Set.of() : areas);
        }

        private Cluster withRole(String value) {
            return new Cluster(id, family, humanLabel, headlineOutcome, value, weight, primaryStoryCount, supportingStoryCount,
                activeDays, from, to, primaryStoryIds, representativeStoryIds, representativeOutcomes,
                allowedClaimStates, claimCeiling, unknowns, conflicts, grounding, topics, areas);
        }
    }

    public record Plan(
        List<Cluster> clusters,
        List<Cluster> selectedClusters,
        List<String> requiredRepresentativeClusterIds,
        List<String> dominantClusterIds,
        int primaryStoryCount,
        int supportingStoryCount,
        double representativePrimaryCoverage,
        boolean coherenceRisk,
        boolean needsSplit,
        List<String> unknowns,
        List<String> conflicts,
        String fingerprint
    ) {
        public Plan {
            clusters = List.copyOf(clusters == null ? List.of() : clusters);
            selectedClusters = List.copyOf(selectedClusters == null ? List.of() : selectedClusters);
            requiredRepresentativeClusterIds = List.copyOf(
                requiredRepresentativeClusterIds == null ? List.of() : requiredRepresentativeClusterIds
            );
            dominantClusterIds = List.copyOf(dominantClusterIds == null ? List.of() : dominantClusterIds);
            unknowns = List.copyOf(unknowns == null ? List.of() : unknowns);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
        }

        public int dominantClusterCount() {
            return dominantClusterIds.size();
        }

        public List<String> representativeOutcomes() {
            return selectedClusters.stream().map(Cluster::headlineOutcome)
                .filter(value -> value != null && !value.isBlank()).toList();
        }

        private Plan withNeedsSplit(boolean value) {
            return new Plan(clusters, selectedClusters, requiredRepresentativeClusterIds, dominantClusterIds,
                primaryStoryCount, supportingStoryCount, representativePrimaryCoverage, coherenceRisk, value,
                unknowns, conflicts, fingerprint);
        }
    }
}
