package com.projectflow.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.projectflow.dto.ProjectHistoryDtos.ChangeStory;
import com.projectflow.dto.ProjectHistoryDtos.EvolutionThread;
import com.projectflow.dto.ProjectHistoryDtos.HistoryChapter;
import com.projectflow.dto.ProjectHistoryDtos.HistoryEventResponse;

/** Test-only evaluator for the frozen V3.8.5 presentation contract. */
public final class ProjectHistoryV385QualityEvaluator {
    private static final List<String> GENERIC_TEMPLATES = List.of(
        "相关变化", "形成初始结果", "进入当前时间点可确认的新状态", "工程分组", "修改 n 个文件",
        "当前行为得到更新", "项目开始具备这项能力"
    );
    private static final List<String> ACTION_MARKERS = List.of(
        "新增", "建立", "整理", "完善", "更新", "恢复", "移除", "撤销", "重新", "替换", "拆分", "合并",
        "调整", "记录", "保留", "隐藏", "统一", "形成", "推进", "实现", "完成", "创建", "编写", "补充", "保存", "应用"
    );
    private static final List<String> RESULT_MARKERS = List.of(
        "结果", "版本", "当前", "可以", "可确认", "可核对", "可阅读", "继续", "重新出现", "不再", "保留",
        "恢复", "完成", "形成", "统一", "分别查看", "代码实现", "实现代码", "实现基础", "基础代码", "功能基础",
        "变更记录", "现状记录", "结构更新", "更新了结构", "首次创建", "保存了", "保存相关", "工作交接记录",
        "可供查看", "已有实现", "已有内容"
    );
    private static final List<String> STRONG_CLAIM_MARKERS = List.of(
        "已证明", "已经证明", "确认成功", "全部完成", "完全正确", "验证通过"
    );
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("(?i)(?:^|[\\s\"'])\\p{Alpha}:[\\\\/]");
    private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile("(?:^|[\\s\"'])/(?:home|Users|var|tmp|opt)/");
    private static final Pattern SECRET = Pattern.compile(
        "(?i)(?:Bearer\\s+[A-Za-z0-9._-]{16,}|(?:sk|ark)-[A-Za-z0-9_-]{16,}|github_pat_[A-Za-z0-9_]{16,}|api[_-]?key\\s*[:=])"
    );

    private ProjectHistoryV385QualityEvaluator() {
    }

    /**
     * Compatibility entry point retained for focused unit tests. Production
     * Ground Truth execution uses {@link #evaluateCases(JsonNode, List)}.
     */
    public static Metrics evaluate(JsonNode groundTruth, List<ChangeStory> stories, List<HistoryChapter> chapters) {
        List<ChangeStory> safeStories = safe(stories);
        List<HistoryChapter> safeChapters = safe(chapters);
        List<String> forbidden = textList(groundTruth.path("technicalWordRules").path("forbiddenFirstLayer"));
        Set<String> allowed = new HashSet<>(textList(groundTruth.path("technicalWordRules").path("allowedProductTerms")));
        int primary = 0;
        int supporting = 0;
        int orphanSupporting = 0;
        int reasonWithoutEvidence = 0;
        int technicalLeak = 0;
        int generic = 0;
        int complete = 0;
        Set<String> referencedSupporting = new HashSet<>();
        for (ChangeStory story : safeStories) {
            if (story.supporting()) supporting++;
            else primary++;
            referencedSupporting.addAll(story.supportingChangeRefs());
            if (!text(story.reason()).isBlank() && story.reasonEvidenceRefs().isEmpty()) reasonWithoutEvidence++;
            String firstLayer = storyFirstLayer(story);
            if (containsForbidden(firstLayer, forbidden, allowed)) technicalLeak++;
            if (containsAny(firstLayer, GENERIC_TEMPLATES)) generic++;
            if (complete(story)) complete++;
        }
        for (ChangeStory story : safeStories) {
            if (story.supporting() && text(story.primaryStoryId()).isBlank()
                && !referencedSupporting.contains(story.id())) orphanSupporting++;
        }
        Set<String> chapterRefs = new HashSet<>();
        int overlap = 0;
        for (HistoryChapter chapter : safeChapters) {
            for (String ref : chapter.storyRefs()) if (!chapterRefs.add(ref)) overlap++;
        }
        double denominator = Math.max(1, safeStories.size());
        double genericRate = generic / denominator;
        double technicalLeakRate = technicalLeak / denominator;
        double completenessRate = complete / denominator;
        JsonNode gates = groundTruth.path("hardGates");
        boolean passes = orphanSupporting <= gates.path("primarySupportingOrphanCountMax").asInt(0)
            && overlap <= gates.path("chapterStoryOverlapCountMax").asInt(0)
            && reasonWithoutEvidence <= gates.path("reasonWithoutEvidenceCountMax").asInt(0)
            && genericRate <= gates.path("genericTemplateRateMax").asDouble(0.05)
            && technicalLeakRate <= gates.path("firstLayerTechnicalLeakRateMax").asDouble(0.05);
        return new Metrics(primary, supporting, orphanSupporting, overlap, reasonWithoutEvidence, technicalLeak,
            generic, genericRate, technicalLeakRate, completenessRate, passes);
    }

    public static EvaluationReport evaluateCases(JsonNode groundTruth, List<CaseObservation> observations) {
        Map<String, JsonNode> expected = new LinkedHashMap<>();
        groundTruth.path("cases").forEach(value -> expected.put(value.path("id").asText(), value));
        List<CaseResult> results = safe(observations).stream()
            .map(observation -> evaluateCase(groundTruth, expected.get(observation.caseId()), observation))
            .toList();
        List<CaseResult> calibrationCases = results.stream()
            .filter(value -> "CALIBRATION".equals(value.split())).toList();
        List<CaseResult> holdoutCases = results.stream()
            .filter(value -> "HOLDOUT".equals(value.split())).toList();
        AggregateMetrics calibration = aggregate(groundTruth, calibrationCases);
        AggregateMetrics holdout = aggregate(groundTruth, holdoutCases);
        AggregateMetrics overall = aggregate(groundTruth, results);
        Set<String> observedIds = results.stream().map(CaseResult::caseId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missingCases = expected.keySet().stream().filter(id -> !observedIds.contains(id)).toList();
        return new EvaluationReport(
            "projectflow-v3.8.5-history-evaluator-v1", calibration, holdout, overall,
            results, missingCases, missingCases.isEmpty() && calibration.passes() && holdout.passes()
        );
    }

    private static CaseResult evaluateCase(JsonNode root, JsonNode expected, CaseObservation observation) {
        if (expected == null || expected.isMissingNode()) {
            return CaseResult.missing(observation.caseId(), observation.split(), "Ground Truth case 不存在");
        }
        List<ChangeStory> stories = safe(observation.stories());
        List<HistoryChapter> chapters = safe(observation.chapters());
        List<EvolutionThread> threads = safe(observation.threads());
        List<HistoryEventResponse> events = safe(observation.events());
        Map<UUID, HistoryEventResponse> eventsById = events.stream()
            .collect(LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll);
        Map<String, Set<UUID>> aliases = normalizedAliases(observation.eventAliases());
        Map<String, ChangeStory> aliasStories = aliasStories(aliases, stories);

        List<List<String>> primaryGroups = groups(expected.path("primaryStoryGroups"));
        List<List<String>> supportingGroups = groups(expected.path("supportingStoryGroups"));
        Set<String> expectedPrimaryAliases = flatten(primaryGroups);
        Set<String> expectedSupportingAliases = flatten(supportingGroups);
        Set<String> allExpectedAliases = new LinkedHashSet<>(expectedPrimaryAliases);
        allExpectedAliases.addAll(expectedSupportingAliases);

        PairScore grouping = pairScore(primaryGroups, projectedStoryGroups(
            stories.stream().filter(ChangeStory::primary).toList(), aliases, expectedPrimaryAliases
        ));
        RoleScore role = roleScore(expectedPrimaryAliases, expectedSupportingAliases, aliasStories);
        List<List<String>> expectedChapterGroups = groups(expected.path("chapterBoundaries"));
        List<String> chapterAliasPreference = expectedChapterGroups.stream().flatMap(Collection::stream).distinct().toList();
        PairScore chapter = pairScore(expectedChapterGroups, projectedChapterGroups(
            chapters, stories, aliases, chapterAliasPreference
        ));
        TransitionScore thread = transitionScore(expected.path("thread").path("transitions"), threads,
            aliases, expectedPrimaryAliases, stories);

        List<ChangeStory> evaluatedPrimaryStories = primaryGroups.stream()
            .map(group -> bestStory(group, aliasStories)).filter(java.util.Objects::nonNull).distinct().toList();
        int titleExpected = Math.max(1, primaryGroups.size());
        int titlePass = (int) evaluatedPrimaryStories.stream().filter(ProjectHistoryV385QualityEvaluator::actionObjectResult).count();
        double titleActionObjectResult = rate(titlePass, titleExpected);
        double titlePositiveAlignment = average(primaryGroups.stream().map(group -> {
            ChangeStory actual = bestStory(group, aliasStories);
            if (actual == null) return 0.0;
            return textList(expected.path("titlePositive")).stream()
                .mapToDouble(ideal -> semanticSimilarity(ideal, actual.humanTitle() + " " + actual.oneSentenceSummary()))
                .max().orElse(0);
        }).toList());
        int negativeTitleCount = countMarkers(firstLayer(stories, chapters, threads), textList(expected.path("titleNegative")));

        double completeness = rate(evaluatedPrimaryStories.stream().filter(ProjectHistoryV385QualityEvaluator::complete).count(), titleExpected);
        double beforeAlignment = fieldAlignment(expected.path("beforeChangeAfter").path("before").asText(),
            evaluatedPrimaryStories, ChangeStory::beforeState);
        double changeAlignment = fieldAlignment(expected.path("beforeChangeAfter").path("change").asText(),
            evaluatedPrimaryStories, ChangeStory::change);
        double afterAlignment = fieldAlignment(expected.path("beforeChangeAfter").path("after").asText(),
            evaluatedPrimaryStories, ChangeStory::afterState);
        double semanticAlignment = average(List.of(beforeAlignment, changeAlignment, afterAlignment));

        List<String> actualUnknowns = mergeText(observation.overviewUnknowns(),
            stories.stream().flatMap(value -> value.unknowns().stream()).toList(),
            threads.stream().flatMap(value -> value.unknowns().stream()).toList());
        List<String> actualConflicts = mergeText(observation.overviewConflicts(),
            stories.stream().flatMap(value -> value.conflicts().stream()).toList(),
            threads.stream().flatMap(value -> value.conflicts().stream()).toList());
        double unknownRecall = phraseRecall(textList(expected.path("expectedUnknowns")), actualUnknowns);
        double conflictRecall = phraseRecall(textList(expected.path("expectedConflicts")), actualConflicts);

        int invalidEvidence = 0;
        int crossProject = 0;
        int unsupportedStrongFact = 0;
        int reasonWithoutEvidence = 0;
        int validReasonRefs = 0;
        int totalReasonRefs = 0;
        for (ChangeStory story : stories) {
            Set<String> eligibleEvidence = story.eventRefs().stream().map(eventsById::get)
                .filter(java.util.Objects::nonNull).flatMap(value -> value.evidenceRefs().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            for (UUID eventRef : story.eventRefs()) if (!eventsById.containsKey(eventRef)) crossProject++;
            for (String evidenceRef : story.evidenceRefs()) if (!eligibleEvidence.contains(evidenceRef)) invalidEvidence++;
            if (!text(story.reason()).isBlank() && story.reasonEvidenceRefs().isEmpty()) reasonWithoutEvidence++;
            for (String ref : story.reasonEvidenceRefs()) {
                totalReasonRefs++;
                if (eligibleEvidence.contains(ref)) validReasonRefs++;
                else invalidEvidence++;
            }
            boolean hasStrongSource = story.eventRefs().stream().map(eventsById::get).filter(java.util.Objects::nonNull)
                .anyMatch(value -> Set.of("OBSERVED", "VERIFIED").contains(value.epistemicStatus())
                    && Set.of("SOURCE_BACKED", "FACTUAL_SOURCE").contains(value.authority()));
            if (!hasStrongSource && containsAny(storyFirstLayer(story), STRONG_CLAIM_MARKERS)) unsupportedStrongFact++;
        }
        double reasonEvidencePrecision = totalReasonRefs == 0 ? 1 : rate(validReasonRefs, totalReasonRefs);

        int orphanSupporting = orphanSupporting(stories);
        int chapterOverlap = chapterOverlap(chapters);
        int rawEventLoss = Math.max(0, observation.snapshotSourceEventCount() - observation.currentPersistedEventCount())
            + Math.max(0, observation.currentPersistedEventCount() - observation.snapshotSourceEventCount());
        String firstLayer = firstLayer(stories, chapters, threads);
        List<String> forbidden = textList(root.path("technicalWordRules").path("forbiddenFirstLayer"));
        Set<String> allowed = new HashSet<>(textList(root.path("technicalWordRules").path("allowedProductTerms")));
        int genericCount = stories.stream().map(ProjectHistoryV385QualityEvaluator::storyFirstLayer)
            .mapToInt(value -> containsAny(value, GENERIC_TEMPLATES) ? 1 : 0).sum();
        int technicalLeakCount = stories.stream().map(ProjectHistoryV385QualityEvaluator::storyFirstLayer)
            .mapToInt(value -> containsForbidden(value, forbidden, allowed) ? 1 : 0).sum()
            + chapters.stream().map(value -> text(value.title()) + " " + text(value.summary()))
                .mapToInt(value -> containsForbidden(value, forbidden, allowed) ? 1 : 0).sum();
        int absolutePathOrSecretLeak = leakCount(firstLayer);
        double storyDenominator = Math.max(1, stories.size());
        double genericRate = genericCount / storyDenominator;
        double technicalLeakRate = technicalLeakCount / Math.max(1.0, stories.size() + chapters.size());
        double readability = readabilityScore(evaluatedPrimaryStories, forbidden, allowed);

        List<String> failures = new ArrayList<>();
        if (!expected.path("split").asText().equals(observation.split())) failures.add("split 与冻结标签不一致");
        if (!aliases.keySet().containsAll(allExpectedAliases)) failures.add("fixture 输出缺少已冻结 alias");
        addIfPositive(failures, invalidEvidence, "Invalid Evidence Reference");
        addIfPositive(failures, crossProject, "Cross-project Reference");
        addIfPositive(failures, unsupportedStrongFact, "Unsupported Strong Fact");
        addIfPositive(failures, rawEventLoss, "Raw Event Loss");
        addIfPositive(failures, orphanSupporting, "Orphan Supporting");
        addIfPositive(failures, chapterOverlap, "Chapter Story Overlap");
        addIfPositive(failures, reasonWithoutEvidence, "Reason Without Evidence");
        addIfPositive(failures, absolutePathOrSecretLeak, "Secret / Absolute Path Leak");
        if (negativeTitleCount > 0) failures.add("titleNegative 命中 " + negativeTitleCount + " 次");
        JsonNode rubric = root.path("rubric");
        if (role.macroF1() < rubric.path("primarySupportingF1Min").asDouble(0.8)) {
            failures.add("Primary / Supporting F1 = " + decimal(role.macroF1()));
        }
        if (chapter.precision() < rubric.path("chapterBoundaryPrecisionMin").asDouble(0.8)) {
            failures.add("Chapter boundary precision = " + decimal(chapter.precision()));
        }
        if (thread.recall() < rubric.path("threadContinuityMin").asDouble(0.8)) {
            failures.add("Thread continuity = " + decimal(thread.recall()));
        }
        if (titleActionObjectResult < rubric.path("titleActionObjectResultMin").asDouble(0.85)) {
            failures.add("Title action + object + result = " + decimal(titleActionObjectResult));
        }
        if (completeness < rubric.path("beforeChangeAfterCompletenessMin").asDouble(0.9)) {
            failures.add("Before / Change / After completeness = " + decimal(completeness));
        }
        if (reasonEvidencePrecision < rubric.path("reasonEvidencePrecisionMin").asDouble(1.0)) {
            failures.add("Reason Evidence precision = " + decimal(reasonEvidencePrecision));
        }

        return new CaseResult(
            observation.caseId(), observation.split(), expected.path("fixtureHash").asText(),
            grouping.precision(), grouping.recall(), role.primaryPrecision(), role.primaryRecall(),
            role.supportingPrecision(), role.supportingRecall(), role.macroF1(),
            chapter.precision(), chapter.recall(), thread.precision(), thread.recall(),
            titleActionObjectResult, titlePositiveAlignment, completeness, semanticAlignment,
            reasonEvidencePrecision, unknownRecall, conflictRecall, readability,
            invalidEvidence, crossProject, unsupportedStrongFact, rawEventLoss, orphanSupporting,
            chapterOverlap, reasonWithoutEvidence, absolutePathOrSecretLeak, genericCount,
            technicalLeakCount, negativeTitleCount, genericRate, technicalLeakRate,
            observation.modelRequestCount(), observation.modelTokenCount(), List.copyOf(failures)
        );
    }

    private static AggregateMetrics aggregate(JsonNode root, List<CaseResult> values) {
        int caseCount = values.size();
        int invalidEvidence = sum(values, CaseResult::invalidEvidenceReferenceCount);
        int crossProject = sum(values, CaseResult::crossProjectReferenceCount);
        int unsupportedStrongFact = sum(values, CaseResult::unsupportedStrongFactCount);
        int rawEventLoss = sum(values, CaseResult::rawEventLossCount);
        int orphan = sum(values, CaseResult::orphanSupportingCount);
        int chapterOverlap = sum(values, CaseResult::chapterStoryOverlapCount);
        int reasonWithoutEvidence = sum(values, CaseResult::reasonWithoutEvidenceCount);
        int leaks = sum(values, CaseResult::absolutePathOrSecretLeakCount);
        int generic = sum(values, CaseResult::genericTemplateCount);
        int technical = sum(values, CaseResult::technicalLeakCount);
        int requests = sum(values, CaseResult::modelRequestCount);
        long tokens = values.stream().mapToLong(CaseResult::modelTokenCount).sum();
        double genericRate = average(values.stream().map(CaseResult::genericTemplateRate).toList());
        double technicalRate = average(values.stream().map(CaseResult::technicalLeakRate).toList());
        double title = average(values.stream().map(CaseResult::titleActionObjectResult).toList());
        double completeness = average(values.stream().map(CaseResult::beforeChangeAfterCompleteness).toList());
        double roleF1 = average(values.stream().map(CaseResult::primarySupportingF1).toList());
        double chapterPrecision = average(values.stream().map(CaseResult::chapterBoundaryPrecision).toList());
        double reasonPrecision = average(values.stream().map(CaseResult::reasonEvidencePrecision).toList());
        double threadContinuity = average(values.stream().map(CaseResult::threadContinuityRecall).toList());
        double readability = average(values.stream().map(CaseResult::readabilityScore).toList());
        JsonNode hard = root.path("hardGates");
        JsonNode rubric = root.path("rubric");
        boolean passes = caseCount > 0
            && invalidEvidence <= hard.path("invalidEvidenceReferenceCountMax").asInt(0)
            && crossProject <= hard.path("crossProjectReferenceCountMax").asInt(0)
            && unsupportedStrongFact <= hard.path("unsupportedStrongFactCountMax").asInt(0)
            && rawEventLoss <= hard.path("rawEventLossCountMax").asInt(0)
            && orphan <= hard.path("primarySupportingOrphanCountMax").asInt(0)
            && chapterOverlap <= hard.path("chapterStoryOverlapCountMax").asInt(0)
            && reasonWithoutEvidence <= hard.path("reasonWithoutEvidenceCountMax").asInt(0)
            && leaks <= hard.path("absolutePathOrSecretLeakCountMax").asInt(0)
            && genericRate <= hard.path("genericTemplateRateMax").asDouble(0.05)
            && technicalRate <= hard.path("firstLayerTechnicalLeakRateMax").asDouble(0.05)
            && title >= rubric.path("titleActionObjectResultMin").asDouble(0.85)
            && completeness >= rubric.path("beforeChangeAfterCompletenessMin").asDouble(0.9)
            && roleF1 >= rubric.path("primarySupportingF1Min").asDouble(0.8)
            && chapterPrecision >= rubric.path("chapterBoundaryPrecisionMin").asDouble(0.8)
            && reasonPrecision >= rubric.path("reasonEvidencePrecisionMin").asDouble(1.0)
            && threadContinuity >= rubric.path("threadContinuityMin").asDouble(0.8)
            && readability >= rubric.path("humanReadabilityScoreMin").asDouble(4.0);
        return new AggregateMetrics(
            caseCount, values.stream().filter(value -> !value.failures().isEmpty()).count(),
            average(values.stream().map(CaseResult::storyGroupingPrecision).toList()),
            average(values.stream().map(CaseResult::storyGroupingRecall).toList()),
            average(values.stream().map(CaseResult::primaryPrecision).toList()),
            average(values.stream().map(CaseResult::primaryRecall).toList()),
            average(values.stream().map(CaseResult::supportingPrecision).toList()),
            average(values.stream().map(CaseResult::supportingRecall).toList()), roleF1,
            chapterPrecision, average(values.stream().map(CaseResult::chapterBoundaryRecall).toList()),
            average(values.stream().map(CaseResult::titleActionObjectResult).toList()),
            average(values.stream().map(CaseResult::titlePositiveAlignment).toList()), completeness,
            average(values.stream().map(CaseResult::beforeChangeAfterSemanticAlignment).toList()),
            reasonPrecision, threadContinuity,
            average(values.stream().map(CaseResult::expectedUnknownRecall).toList()),
            average(values.stream().map(CaseResult::expectedConflictRecall).toList()), readability,
            invalidEvidence, crossProject, unsupportedStrongFact, rawEventLoss, orphan, chapterOverlap,
            reasonWithoutEvidence, leaks, generic, technical, genericRate, technicalRate, requests, tokens, passes
        );
    }

    private static Map<String, Set<UUID>> normalizedAliases(Map<String, ? extends Collection<UUID>> aliases) {
        Map<String, Set<UUID>> result = new LinkedHashMap<>();
        if (aliases == null) return result;
        aliases.forEach((key, values) -> result.put(key, values == null ? Set.of() : Set.copyOf(values)));
        return result;
    }

    private static Map<String, ChangeStory> aliasStories(Map<String, Set<UUID>> aliases, List<ChangeStory> stories) {
        Map<String, ChangeStory> result = new LinkedHashMap<>();
        aliases.forEach((alias, eventIds) -> stories.stream()
            .max(java.util.Comparator.comparingLong(story -> story.eventRefs().stream().filter(eventIds::contains).count()))
            .filter(story -> story.eventRefs().stream().anyMatch(eventIds::contains))
            .ifPresent(story -> result.put(alias, story)));
        return result;
    }

    private static List<Set<String>> projectedStoryGroups(
        List<ChangeStory> stories,
        Map<String, Set<UUID>> aliases,
        Set<String> eligibleAliases
    ) {
        List<Set<String>> result = new ArrayList<>();
        for (ChangeStory story : stories) {
            Set<String> members = aliases.entrySet().stream()
                .filter(entry -> eligibleAliases.contains(entry.getKey()))
                .filter(entry -> story.eventRefs().stream().anyMatch(entry.getValue()::contains))
                .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
            if (!members.isEmpty()) result.add(members);
        }
        return result;
    }

    private static List<Set<String>> projectedChapterGroups(
        List<HistoryChapter> chapters,
        List<ChangeStory> stories,
        Map<String, Set<UUID>> aliases,
        List<String> aliasPreference
    ) {
        Map<String, ChangeStory> byId = stories.stream()
            .collect(LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll);
        List<Set<String>> result = new ArrayList<>();
        for (HistoryChapter chapter : chapters) {
            Set<String> members = new LinkedHashSet<>();
            for (String storyId : chapter.storyRefs()) {
                ChangeStory story = byId.get(storyId);
                if (story == null) continue;
                aliasPreference.stream().filter(alias -> aliases.containsKey(alias))
                    .filter(alias -> story.eventRefs().stream().anyMatch(aliases.get(alias)::contains))
                    .findFirst().ifPresent(members::add);
            }
            if (!members.isEmpty()) result.add(members);
        }
        return result;
    }

    private static PairScore pairScore(List<List<String>> expected, List<Set<String>> actual) {
        Set<String> expectedPairs = pairs(expected.stream().map(LinkedHashSet::new).toList());
        Set<String> actualPairs = pairs(actual);
        long truePositive = actualPairs.stream().filter(expectedPairs::contains).count();
        return new PairScore(
            actualPairs.isEmpty() ? (expectedPairs.isEmpty() ? 1 : 0) : rate(truePositive, actualPairs.size()),
            expectedPairs.isEmpty() ? 1 : rate(truePositive, expectedPairs.size())
        );
    }

    private static Set<String> pairs(List<? extends Set<String>> groups) {
        Set<String> result = new LinkedHashSet<>();
        for (Set<String> group : groups) {
            List<String> ordered = group.stream().sorted().toList();
            for (int left = 0; left < ordered.size(); left++) {
                for (int right = left; right < ordered.size(); right++) {
                    result.add(ordered.get(left) + "\u0000" + ordered.get(right));
                }
            }
        }
        return result;
    }

    private static RoleScore roleScore(
        Set<String> expectedPrimary,
        Set<String> expectedSupporting,
        Map<String, ChangeStory> actual
    ) {
        BinaryScore primary = binaryRole(expectedPrimary, expectedSupporting, actual, "PRIMARY");
        BinaryScore supporting = binaryRole(expectedSupporting, expectedPrimary, actual, "SUPPORTING");
        return new RoleScore(
            primary.precision(), primary.recall(), supporting.precision(), supporting.recall(),
            average(List.of(f1(primary.precision(), primary.recall()), f1(supporting.precision(), supporting.recall())))
        );
    }

    private static BinaryScore binaryRole(
        Set<String> positives,
        Set<String> negatives,
        Map<String, ChangeStory> actual,
        String role
    ) {
        long predicted = actual.entrySet().stream()
            .filter(entry -> positives.contains(entry.getKey()) || negatives.contains(entry.getKey()))
            .filter(entry -> role.equals(entry.getValue().role())).count();
        long truePositive = positives.stream().filter(alias -> actual.containsKey(alias))
            .filter(alias -> role.equals(actual.get(alias).role())).count();
        return new BinaryScore(predicted == 0 ? (positives.isEmpty() ? 1 : 0) : rate(truePositive, predicted),
            positives.isEmpty() ? 1 : rate(truePositive, positives.size()));
    }

    private static TransitionScore transitionScore(
        JsonNode expectedNode,
        List<EvolutionThread> threads,
        Map<String, Set<UUID>> aliases,
        Set<String> expectedAliases,
        List<ChangeStory> stories
    ) {
        List<String> expected = textList(expectedNode);
        Map<String, ChangeStory> byId = stories.stream()
            .collect(LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll);
        EvolutionThread best = threads.stream().max(java.util.Comparator.comparingLong(thread -> {
            Set<UUID> ids = thread.storyRefs().stream().map(byId::get).filter(java.util.Objects::nonNull)
                .flatMap(value -> value.eventRefs().stream()).collect(Collectors.toSet());
            return aliases.entrySet().stream().filter(entry -> expectedAliases.contains(entry.getKey()))
                .filter(entry -> entry.getValue().stream().anyMatch(ids::contains)).count();
        })).orElse(null);
        List<String> actual = best == null ? List.of() : best.transitions();
        int lcs = longestCommonSubsequence(expected, actual);
        return new TransitionScore(actual.isEmpty() ? (expected.isEmpty() ? 1 : 0) : rate(lcs, actual.size()),
            expected.isEmpty() ? 1 : rate(lcs, expected.size()));
    }

    private static int longestCommonSubsequence(List<String> left, List<String> right) {
        int[][] values = new int[left.size() + 1][right.size() + 1];
        for (int i = 1; i <= left.size(); i++) {
            for (int j = 1; j <= right.size(); j++) {
                values[i][j] = left.get(i - 1).equalsIgnoreCase(right.get(j - 1))
                    ? values[i - 1][j - 1] + 1 : Math.max(values[i - 1][j], values[i][j - 1]);
            }
        }
        return values[left.size()][right.size()];
    }

    private static ChangeStory bestStory(List<String> aliases, Map<String, ChangeStory> actual) {
        return aliases.stream().map(actual::get).filter(java.util.Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
            .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static double fieldAlignment(String expected, List<ChangeStory> stories, Function<ChangeStory, String> field) {
        if (expected == null || expected.isBlank()) return 1;
        return stories.stream().map(field).mapToDouble(value -> semanticSimilarity(expected, value)).max().orElse(0);
    }

    private static double phraseRecall(List<String> expected, List<String> actual) {
        if (expected.isEmpty()) return 1;
        long matched = expected.stream().filter(label -> actual.stream()
            .anyMatch(value -> semanticSimilarity(label, value) >= 0.18 || sharesSignal(label, value))).count();
        return rate(matched, expected.size());
    }

    private static boolean sharesSignal(String left, String right) {
        for (String marker : List.of("未知", "原因", "冲突", "失败", "验证", "历史", "覆盖", "敏感", "Agent", "Git")) {
            if (left.contains(marker) && right.contains(marker)) return true;
        }
        return false;
    }

    private static double semanticSimilarity(String left, String right) {
        Set<String> leftTokens = bigrams(normalizeText(left));
        Set<String> rightTokens = bigrams(normalizeText(right));
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return normalizeText(left).equals(normalizeText(right)) ? 1 : 0;
        long overlap = leftTokens.stream().filter(rightTokens::contains).count();
        return 2.0 * overlap / (leftTokens.size() + rightTokens.size());
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new LinkedHashSet<>();
        String compact = value.replaceAll("[^\\p{L}\\p{N}]", "");
        for (int index = 0; index < compact.length() - 1; index++) result.add(compact.substring(index, index + 2));
        if (compact.length() == 1) result.add(compact);
        return result;
    }

    private static String normalizeText(String value) {
        return text(value).toLowerCase(Locale.ROOT)
            .replaceAll("(此前|当前|变化后|来源|项目|结果|已经|这次|一项|可以|继续)", "");
    }

    private static int orphanSupporting(List<ChangeStory> stories) {
        Map<String, ChangeStory> byId = stories.stream()
            .collect(LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll);
        int count = 0;
        for (ChangeStory story : stories) {
            if (!story.supporting()) continue;
            ChangeStory primary = byId.get(story.primaryStoryId());
            if (primary == null || !primary.primary() || !primary.supportingChangeRefs().contains(story.id())) count++;
        }
        return count;
    }

    private static int chapterOverlap(List<HistoryChapter> chapters) {
        Set<String> refs = new LinkedHashSet<>();
        int overlap = 0;
        for (HistoryChapter chapter : chapters) for (String ref : chapter.storyRefs()) if (!refs.add(ref)) overlap++;
        return overlap;
    }

    private static int leakCount(String value) {
        return (WINDOWS_ABSOLUTE_PATH.matcher(value).find() ? 1 : 0)
            + (UNIX_ABSOLUTE_PATH.matcher(value).find() ? 1 : 0)
            + (SECRET.matcher(value).find() ? 1 : 0);
    }

    private static double readabilityScore(List<ChangeStory> stories, List<String> forbidden, Set<String> allowed) {
        if (stories.isEmpty()) return 0;
        return average(stories.stream().map(story -> {
            double score = 1;
            if (actionObjectResult(story)) score++;
            if (complete(story)) score++;
            String firstLayer = storyFirstLayer(story);
            if (!containsForbidden(firstLayer, forbidden, allowed)
                && !containsAny(firstLayer, GENERIC_TEMPLATES) && leakCount(firstLayer) == 0) score++;
            if (text(story.humanTitle()).length() >= 4 && text(story.oneSentenceSummary()).length() >= 8) score++;
            return score;
        }).toList());
    }

    private static boolean actionObjectResult(ChangeStory story) {
        return actionObjectResult(story.humanTitle(), story.oneSentenceSummary());
    }

    static boolean actionObjectResult(String humanTitle, String oneSentenceSummary) {
        String title = text(humanTitle);
        String firstLayer = title + " " + text(oneSentenceSummary);
        boolean action = containsAny(title, ACTION_MARKERS);
        boolean result = containsAny(firstLayer, RESULT_MARKERS);
        String object = title.replaceAll("[，,。.!！？]", "").replaceAll(String.join("|", ACTION_MARKERS), "").trim();
        return action && result && object.length() >= 2 && !containsAny(firstLayer, GENERIC_TEMPLATES);
    }

    private static boolean complete(ChangeStory story) {
        return !text(story.beforeState()).isBlank() && !text(story.change()).isBlank() && !text(story.afterState()).isBlank();
    }

    private static String storyFirstLayer(ChangeStory story) {
        return String.join(" ", List.of(
            text(story.humanTitle()), text(story.oneSentenceSummary()), text(story.beforeState()),
            text(story.change()), text(story.afterState()), text(story.reason()),
            String.join(" ", safe(story.conflicts())), String.join(" ", safe(story.unknowns()))
        ));
    }

    private static String firstLayer(
        List<ChangeStory> stories,
        List<HistoryChapter> chapters,
        List<EvolutionThread> threads
    ) {
        StringBuilder value = new StringBuilder();
        stories.forEach(story -> value.append(storyFirstLayer(story)).append('\n'));
        chapters.forEach(chapter -> value.append(text(chapter.title())).append(' ')
            .append(text(chapter.summary())).append('\n'));
        threads.forEach(thread -> value.append(text(thread.subjectLabel())).append(' ')
            .append(text(thread.currentOutcome())).append(' ')
            .append(String.join(" ", safe(thread.unknowns()))).append(' ')
            .append(String.join(" ", safe(thread.conflicts()))).append('\n'));
        return value.toString();
    }

    private static boolean containsForbidden(String text, List<String> forbidden, Set<String> allowed) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : forbidden) {
            if (term == null || term.isBlank()) continue;
            if (allowed.stream().anyMatch(value -> value.equalsIgnoreCase(term))) continue;
            if (lower.contains(term.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> values) {
        String lower = text.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value != null && !value.isBlank())
            .anyMatch(value -> lower.contains(value.toLowerCase(Locale.ROOT)));
    }

    private static int countMarkers(String text, List<String> markers) {
        int count = 0;
        for (String marker : markers) if (marker != null && !marker.isBlank() && text.contains(marker)) count++;
        return count;
    }

    private static List<List<String>> groups(JsonNode node) {
        List<List<String>> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode group : node) result.add(textList(group));
        return result;
    }

    private static Set<String> flatten(List<List<String>> groups) {
        return groups.stream().flatMap(Collection::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(value -> values.add(value.asText()));
        return values;
    }

    @SafeVarargs
    private static List<String> mergeText(List<String>... sources) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (List<String> source : sources) result.addAll(safe(source));
        return List.copyOf(result);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }


    private static double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0 : (double) numerator / denominator;
    }

    private static double f1(double precision, double recall) {
        return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
    }

    private static double average(List<Double> values) {
        return values == null || values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static int sum(List<CaseResult> values, Function<CaseResult, Integer> field) {
        return values.stream().map(field).mapToInt(Integer::intValue).sum();
    }

    private static void addIfPositive(List<String> failures, int value, String label) {
        if (value > 0) failures.add(label + " = " + value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private record PairScore(double precision, double recall) {
    }

    private record BinaryScore(double precision, double recall) {
    }

    private record RoleScore(
        double primaryPrecision,
        double primaryRecall,
        double supportingPrecision,
        double supportingRecall,
        double macroF1
    ) {
    }

    private record TransitionScore(double precision, double recall) {
    }

    public record CaseObservation(
        String caseId,
        String split,
        List<ChangeStory> stories,
        List<HistoryChapter> chapters,
        List<EvolutionThread> threads,
        List<HistoryEventResponse> events,
        Map<String, ? extends Collection<UUID>> eventAliases,
        List<String> overviewUnknowns,
        List<String> overviewConflicts,
        String presentationRevision,
        int snapshotSourceEventCount,
        int currentPersistedEventCount,
        int modelRequestCount,
        long modelTokenCount
    ) {
        public CaseObservation {
            stories = List.copyOf(safe(stories));
            chapters = List.copyOf(safe(chapters));
            threads = List.copyOf(safe(threads));
            events = List.copyOf(safe(events));
            eventAliases = eventAliases == null ? Map.of() : Map.copyOf(eventAliases);
            overviewUnknowns = List.copyOf(safe(overviewUnknowns));
            overviewConflicts = List.copyOf(safe(overviewConflicts));
            presentationRevision = presentationRevision == null ? "" : presentationRevision.trim();
        }
    }

    public record CaseResult(
        String caseId,
        String split,
        String fixtureHash,
        double storyGroupingPrecision,
        double storyGroupingRecall,
        double primaryPrecision,
        double primaryRecall,
        double supportingPrecision,
        double supportingRecall,
        double primarySupportingF1,
        double chapterBoundaryPrecision,
        double chapterBoundaryRecall,
        double threadContinuityPrecision,
        double threadContinuityRecall,
        double titleActionObjectResult,
        double titlePositiveAlignment,
        double beforeChangeAfterCompleteness,
        double beforeChangeAfterSemanticAlignment,
        double reasonEvidencePrecision,
        double expectedUnknownRecall,
        double expectedConflictRecall,
        double readabilityScore,
        int invalidEvidenceReferenceCount,
        int crossProjectReferenceCount,
        int unsupportedStrongFactCount,
        int rawEventLossCount,
        int orphanSupportingCount,
        int chapterStoryOverlapCount,
        int reasonWithoutEvidenceCount,
        int absolutePathOrSecretLeakCount,
        int genericTemplateCount,
        int technicalLeakCount,
        int negativeTitleMatchCount,
        double genericTemplateRate,
        double technicalLeakRate,
        int modelRequestCount,
        long modelTokenCount,
        List<String> failures
    ) {
        static CaseResult missing(String caseId, String split, String failure) {
            return new CaseResult(
                caseId, split, "",
                0, 0,
                0, 0, 0, 0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0,
                0, 0L, List.of(failure)
            );
        }
    }

    public record AggregateMetrics(
        int caseCount,
        long caseWithFailureCount,
        double storyGroupingPrecision,
        double storyGroupingRecall,
        double primaryPrecision,
        double primaryRecall,
        double supportingPrecision,
        double supportingRecall,
        double primarySupportingF1,
        double chapterBoundaryPrecision,
        double chapterBoundaryRecall,
        double titleActionObjectResult,
        double titlePositiveAlignment,
        double beforeChangeAfterCompleteness,
        double beforeChangeAfterSemanticAlignment,
        double reasonEvidencePrecision,
        double threadContinuity,
        double expectedUnknownRecall,
        double expectedConflictRecall,
        double readabilityScore,
        int invalidEvidenceReferenceCount,
        int crossProjectReferenceCount,
        int unsupportedStrongFactCount,
        int rawEventLossCount,
        int orphanSupportingCount,
        int chapterStoryOverlapCount,
        int reasonWithoutEvidenceCount,
        int absolutePathOrSecretLeakCount,
        int genericTemplateCount,
        int technicalLeakCount,
        double genericTemplateRate,
        double technicalLeakRate,
        int modelRequestCount,
        long modelTokenCount,
        boolean passes
    ) {
    }

    public record EvaluationReport(
        String evaluatorVersion,
        AggregateMetrics calibration,
        AggregateMetrics holdout,
        AggregateMetrics overall,
        List<CaseResult> cases,
        List<String> missingCases,
        boolean passes
    ) {
    }

    public record Metrics(
        int primaryCount,
        int supportingCount,
        int orphanSupportingCount,
        int chapterStoryOverlapCount,
        int reasonWithoutEvidenceCount,
        int technicalLeakCount,
        int genericTemplateCount,
        double genericTemplateRate,
        double technicalLeakRate,
        double beforeChangeAfterCompleteness,
        boolean passes
    ) {
    }
}
