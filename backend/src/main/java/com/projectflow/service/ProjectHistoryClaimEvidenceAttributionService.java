package com.projectflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projectflow.entity.ProjectFactEpistemicStatus;
import com.projectflow.entity.ProjectHistoryEvent.Authority;
import com.projectflow.entity.ProjectHistoryEvent.Category;
import com.projectflow.entity.ProjectHistoryEvent.Transition;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.ClaimState;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceAtom;
import com.projectflow.service.ProjectHistoryNarrativeEntailmentValidator.EvidenceProfile;

/**
 * Preserves claim-to-atom structure while deciding the strongest state that
 * may be narrated. Same-commit and story membership are intentionally absent
 * from the promotion rules.
 */
@Component
final class ProjectHistoryClaimEvidenceAttributionService {
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "kt", "kts", "go", "rs", "py", "js", "jsx", "ts", "tsx", "vue", "svelte",
        "cs", "cpp", "c", "h", "hpp", "rb", "php", "swift", "scala"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "md", "mdx", "txt", "rst", "adoc", "pdf", "doc", "docx", "ppt", "pptx", "key", "odp"
    );
    private static final Set<String> ARTIFACT_EXTENSIONS = Set.of(
        "csv", "tsv", "xls", "xlsx", "parquet", "arrow", "fig", "sketch", "psd", "ai", "xd",
        "svg", "png", "jpg", "jpeg", "gif", "webp", "mp4", "mov", "mkv", "webm", "avi", "html", "htm"
    );

    Attribution attribute(EvidenceProfile profile) {
        EvidenceProfile safe = profile == null ? EvidenceProfile.empty() : profile;
        String subjectKey = normalizeKey(safe.subjectKey());
        List<EvidenceAtom> direct = new ArrayList<>();
        List<EvidenceAtom> indirect = new ArrayList<>();
        for (EvidenceAtom atom : safe.atoms()) {
            if (supportsSubject(atom, subjectKey) && eligibleAuthority(atom)) direct.add(atom);
            else indirect.add(atom);
        }

        List<ClassifiedAtom> classified = direct.stream()
            .map(atom -> new ClassifiedAtom(atom, evidenceClass(atom)))
            .toList();
        ClaimState state = state(classified);
        String action = action(state);
        String outcome = outcome(state, safe.subjectLabel());
        List<String> directRefs = evidenceRefs(direct, true);
        LinkedHashSet<String> indirectSet = new LinkedHashSet<>(evidenceRefs(indirect, false));
        indirectSet.removeAll(directRefs);
        List<String> indirectRefs = List.copyOf(indirectSet);
        String supportClass = supportClass(state, direct);
        String downgradeReason = downgradeReason(state, direct, indirect, classified);
        return new Attribution(
            subjectKey,
            safe.subjectLabel(),
            action,
            state,
            outcome,
            directRefs,
            indirectRefs,
            direct.stream().map(EvidenceAtom::authority).distinct().map(Enum::name).toList(),
            supportClass,
            downgradeReason,
            supportSummary(classified),
            indirectSummary(indirect)
        );
    }

    private static ClaimState state(List<ClassifiedAtom> direct) {
        if (direct.stream().anyMatch(item -> item.atom().epistemicStatus() == ProjectFactEpistemicStatus.CONFLICTED)) {
            return ClaimState.CONFLICTED;
        }
        boolean removed = direct.stream().anyMatch(item -> strong(item.atom()) && Set.of(
            Transition.REMOVED, Transition.REVERTED
        ).contains(item.atom().transition()));
        boolean restored = direct.stream().anyMatch(item -> strong(item.atom()) && Set.of(
            Transition.RESTORED, Transition.REAPPLIED
        ).contains(item.atom().transition()));
        if (removed) return ClaimState.REMOVED;
        if (restored) return ClaimState.RESTORED;

        List<ClassifiedAtom> implementations = direct.stream()
            .filter(item -> item.evidenceClass() == EvidenceClass.IMPLEMENTATION && strong(item.atom()))
            .toList();
        List<ClassifiedAtom> validations = direct.stream()
            .filter(item -> item.evidenceClass() == EvidenceClass.VALIDATION && strong(item.atom()))
            .toList();
        boolean independentValidation = !implementations.isEmpty() && validations.stream().anyMatch(validation ->
            implementations.stream().anyMatch(implementation -> independent(implementation.atom(), validation.atom()))
        );
        if (independentValidation) return ClaimState.VERIFIED;
        if (!implementations.isEmpty()) return ClaimState.IMPLEMENTED;
        if (direct.stream().anyMatch(item -> item.evidenceClass() == EvidenceClass.CONFIGURATION && strong(item.atom()))) {
            return ClaimState.CONFIGURED;
        }
        if (direct.stream().anyMatch(item -> item.evidenceClass() == EvidenceClass.PLAN)) return ClaimState.PLANNED;
        if (direct.stream().anyMatch(item -> item.evidenceClass() == EvidenceClass.DECLARATION)) return ClaimState.DECLARED;
        if (direct.stream().anyMatch(item -> strong(item.atom()))) return ClaimState.OBSERVED;
        if (direct.stream().anyMatch(item -> item.atom().authority() == Authority.DECLARED
            || item.atom().epistemicStatus() == ProjectFactEpistemicStatus.DECLARED)) return ClaimState.DECLARED;
        return ClaimState.UNKNOWN;
    }

    private static EvidenceClass evidenceClass(EvidenceAtom atom) {
        if (atom.category() == Category.AGENT_RESULT || atom.authority() == Authority.PROCESS_EVIDENCE
            || atom.epistemicStatus() == ProjectFactEpistemicStatus.PROCESS_EVIDENCE) return EvidenceClass.PROCESS;
        if (atom.category() == Category.VALIDATION) return EvidenceClass.VALIDATION;
        if (Set.of(Transition.REMOVED, Transition.REVERTED, Transition.RESTORED, Transition.REAPPLIED)
            .contains(atom.transition())) return EvidenceClass.LIFECYCLE;
        List<String> paths = atom.paths();
        if (!paths.isEmpty() && paths.stream().allMatch(ProjectHistoryClaimEvidenceAttributionService::configurationPath)) {
            return EvidenceClass.CONFIGURATION;
        }
        String label = atom.sourceLabel().toLowerCase(Locale.ROOT);
        boolean plan = containsAny(label, "plan", "roadmap", "proposal", "will support", "计划", "规划", "后续", "方案");
        if (plan && (atom.category() == Category.DOCUMENT_VERSION || atom.authority() == Authority.DECLARED)) {
            return EvidenceClass.PLAN;
        }
        if (atom.category() == Category.USER_DECLARATION || atom.authority() == Authority.DECLARED
            || atom.epistemicStatus() == ProjectFactEpistemicStatus.DECLARED) return EvidenceClass.DECLARATION;
        if (!paths.isEmpty() && paths.stream().allMatch(path -> DOCUMENT_EXTENSIONS.contains(extension(path)))) {
            return atom.category() == Category.DOCUMENT_VERSION ? EvidenceClass.DOCUMENT : EvidenceClass.ARTIFACT;
        }
        if (!paths.isEmpty() && paths.stream().allMatch(path -> ARTIFACT_EXTENSIONS.contains(extension(path)))) {
            return EvidenceClass.ARTIFACT;
        }
        if (atom.category() == Category.FILE_CHANGE && paths.stream().anyMatch(
            ProjectHistoryClaimEvidenceAttributionService::implementationPath
        )) return EvidenceClass.IMPLEMENTATION;
        return EvidenceClass.CONTEXT;
    }

    private static boolean supportsSubject(EvidenceAtom atom, String subjectKey) {
        if (subjectKey.isBlank()) return false;
        return atom.subjectKeys().stream().map(ProjectHistoryClaimEvidenceAttributionService::normalizeKey)
            .anyMatch(subjectKey::equals);
    }

    private static boolean eligibleAuthority(EvidenceAtom atom) {
        return atom.authority() != Authority.PROCESS_EVIDENCE
            && atom.authority() != Authority.INFERRED_NON_AUTHORITATIVE
            && atom.authority() != Authority.UNKNOWN
            && atom.epistemicStatus() != ProjectFactEpistemicStatus.PROCESS_EVIDENCE
            && atom.epistemicStatus() != ProjectFactEpistemicStatus.INFERRED
            && atom.epistemicStatus() != ProjectFactEpistemicStatus.UNKNOWN;
    }

    private static boolean strong(EvidenceAtom atom) {
        return atom.epistemicStatus().isStrongFact()
            && Set.of(Authority.SOURCE_BACKED, Authority.FACTUAL_SOURCE).contains(atom.authority());
    }

    private static boolean independent(EvidenceAtom implementation, EvidenceAtom validation) {
        if (implementation.atomRef().equals(validation.atomRef())) return false;
        Set<String> implementationRefs = new LinkedHashSet<>(implementation.evidenceRefs());
        Set<String> validationRefs = new LinkedHashSet<>(validation.evidenceRefs());
        return validationRefs.stream().anyMatch(ref -> !implementationRefs.contains(ref));
    }

    private static boolean implementationPath(String path) {
        String safe = normalizedPath(path);
        if (safe.isBlank() || configurationPath(safe) || testPath(safe)) return false;
        return CODE_EXTENSIONS.contains(extension(safe));
    }

    private static boolean testPath(String path) {
        String safe = normalizedPath(path);
        String file = safe.substring(safe.lastIndexOf('/') + 1);
        return safe.contains("/test/") || safe.contains("/tests/") || safe.contains("/__tests__/")
            || safe.contains("/spec/") || file.matches(".*(?:test|tests|spec)\\.[^.]+$")
            || file.matches(".*(?:test|tests|spec)\\.[^.]+\\.[^.]+$");
    }

    private static boolean configurationPath(String path) {
        String safe = normalizedPath(path);
        String file = safe.substring(safe.lastIndexOf('/') + 1);
        String extension = extension(safe);
        return Set.of("yml", "yaml", "toml", "ini", "properties", "conf", "config", "env", "lock").contains(extension)
            || safe.endsWith(".env.example") || file.equals(".gitignore") || file.equals("package.json")
            || file.equals("pom.xml") || file.equals("build.gradle") || file.equals("settings.gradle")
            || file.equals("next-env.d.ts") || file.contains(".config.") || safe.contains("/config/")
            || safe.startsWith(".github/workflows/");
    }

    private static String action(ClaimState state) {
        return switch (state) {
            case PLANNED -> "PLAN";
            case DECLARED -> "DECLARE";
            case CONFIGURED -> "CONFIGURE";
            case IMPLEMENTED -> "IMPLEMENT";
            case OBSERVED -> "OBSERVE";
            case VERIFIED -> "VERIFY";
            case REMOVED -> "REMOVE";
            case RESTORED -> "RESTORE";
            case UNKNOWN -> "UNKNOWN";
            case CONFLICTED -> "CONFLICT";
        };
    }

    private static String outcome(ClaimState state, String subjectLabel) {
        String subject = subjectLabel == null || subjectLabel.isBlank() ? "项目材料" : subjectLabel.trim();
        return switch (state) {
            case PLANNED -> subject + "已有规划记录，不能确认已经实现";
            case DECLARED -> subject + "已有声明或设计记录，实际结果仍待确认";
            case CONFIGURED -> subject + "已有配置记录，不能确认部署或运行结果";
            case IMPLEMENTED -> subject + "已有直接实现证据，验证状态仍待确认";
            case OBSERVED -> subject + "已有可直接观察的产物或变化";
            case VERIFIED -> subject + "已有直接实现和独立验证证据";
            case REMOVED -> subject + "已有直接移除或回退证据";
            case RESTORED -> subject + "已有直接恢复或重新应用证据";
            case UNKNOWN -> subject + "缺少足够的直接证据";
            case CONFLICTED -> subject + "的直接来源存在冲突";
        };
    }

    private static String supportClass(ClaimState state, List<EvidenceAtom> direct) {
        if (state == ClaimState.CONFLICTED) return "CONFLICTED";
        if (!direct.isEmpty() && state != ClaimState.UNKNOWN) return "DIRECT";
        return direct.isEmpty() ? "INDIRECT_ONLY" : "INSUFFICIENT";
    }

    private static String downgradeReason(
        ClaimState state,
        List<EvidenceAtom> direct,
        List<EvidenceAtom> indirect,
        List<ClassifiedAtom> classified
    ) {
        if (state == ClaimState.CONFLICTED) return "同一主体的直接来源存在冲突，禁止形成强结论。";
        if (direct.isEmpty()) return indirect.isEmpty()
            ? "没有可归因到当前主体的 Evidence。"
            : "现有 Evidence 仅与当前主体间接相关，不能提升状态。";
        if (state == ClaimState.OBSERVED && classified.stream().anyMatch(item -> item.evidenceClass() == EvidenceClass.VALIDATION)) {
            return "存在验证记录，但缺少同一主体的直接实现 Evidence，不能升级为 VERIFIED。";
        }
        if (state == ClaimState.IMPLEMENTED) return "已有直接实现 Evidence，但缺少独立验证结果。";
        if (state == ClaimState.CONFIGURED) return "配置 Evidence 不证明部署或运行结果。";
        if (state == ClaimState.PLANNED || state == ClaimState.DECLARED) return "规划或声明 Evidence 不证明实际实现。";
        if (state == ClaimState.UNKNOWN) return "直接来源的权威或状态不足。";
        return "";
    }

    private static List<String> supportSummary(List<ClassifiedAtom> atoms) {
        Map<EvidenceClass, Integer> counts = new LinkedHashMap<>();
        atoms.forEach(item -> counts.merge(item.evidenceClass(), 1, Integer::sum));
        return counts.entrySet().stream().map(entry -> entry.getKey().display + " " + entry.getValue() + " 项").toList();
    }

    private static List<String> indirectSummary(List<EvidenceAtom> atoms) {
        if (atoms.isEmpty()) return List.of();
        return List.of("间接上下文 " + atoms.size() + " 项；不能用于提升当前 Claim 状态");
    }

    private static List<String> evidenceRefs(List<EvidenceAtom> atoms, boolean direct) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        atoms.forEach(atom -> {
            List<String> refs = atom.evidenceRefs();
            boolean hasSpecific = refs.stream().anyMatch(ref -> ref != null && !ref.startsWith("commit:"));
            refs.forEach(ref -> {
                if (ref == null || ref.isBlank() || result.size() >= 100) return;
                if (direct && hasSpecific && ref.startsWith("commit:")) return;
                result.add(ref.trim());
            });
        });
        return List.copyOf(result);
    }

    private static String normalizedPath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static String extension(String path) {
        String safe = normalizedPath(path);
        int slash = safe.lastIndexOf('/');
        int dot = safe.lastIndexOf('.');
        return dot > slash ? safe.substring(dot + 1) : "";
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... markers) {
        String safe = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String marker : markers) if (safe.contains(marker.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    record Attribution(
        String subjectKey,
        String subjectLabel,
        String action,
        ClaimState state,
        String outcome,
        List<String> directEvidenceRefs,
        List<String> indirectEvidenceRefs,
        List<String> sourceAuthorities,
        String supportClass,
        String downgradeReason,
        List<String> directSupportSummary,
        List<String> indirectContextSummary
    ) {
        Attribution {
            directEvidenceRefs = List.copyOf(directEvidenceRefs == null ? List.of() : directEvidenceRefs);
            indirectEvidenceRefs = List.copyOf(indirectEvidenceRefs == null ? List.of() : indirectEvidenceRefs);
            sourceAuthorities = List.copyOf(sourceAuthorities == null ? List.of() : sourceAuthorities);
            directSupportSummary = List.copyOf(directSupportSummary == null ? List.of() : directSupportSummary);
            indirectContextSummary = List.copyOf(indirectContextSummary == null ? List.of() : indirectContextSummary);
        }
    }

    private record ClassifiedAtom(EvidenceAtom atom, EvidenceClass evidenceClass) {
    }

    private enum EvidenceClass {
        IMPLEMENTATION("直接实现 Evidence"),
        VALIDATION("独立验证 Evidence"),
        CONFIGURATION("配置 Evidence"),
        PLAN("规划 Evidence"),
        DECLARATION("声明 Evidence"),
        DOCUMENT("文档产物 Evidence"),
        ARTIFACT("非代码产物 Evidence"),
        LIFECYCLE("生命周期 Evidence"),
        PROCESS("过程声明"),
        CONTEXT("来源上下文");

        private final String display;

        EvidenceClass(String display) {
            this.display = display;
        }
    }
}
