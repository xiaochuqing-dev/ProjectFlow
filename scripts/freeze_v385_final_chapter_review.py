from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from freeze_v385_human_review_sample import (
    ABSOLUTE_PATH_PATTERN,
    SENSITIVE_PATTERN,
    canonical_hash,
    inline,
    load_json,
    provider_samples,
    qualified,
    relative,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FINAL_ROOT = ROOT / "docs" / "acceptance-evidence" / "v3.8.5" / "final-chapter-real-model"
DEFAULT_MANIFEST = ROOT / "docs" / "acceptance-evidence" / "v3.8.5" / "final-chapter-review-manifest.json"
DEFAULT_WORKSHEET = ROOT / "docs" / "acceptance-evidence" / "v3.8.5" / "final-chapter-review-worksheet.md"
ROUND3_ROOT = ROOT / "docs" / "acceptance-evidence" / "v3.8.5"
ROUND3_MANIFEST = ROUND3_ROOT / "human-review-round3-manifest.json"
ROUND3_WORKSHEET = ROUND3_ROOT / "human-review-round3-worksheet.md"
FILLED_SCORE = re.compile(r"(?m)^.*评分（1-5）：\s*[1-5]\s*$")
FILLED_RESULT = re.compile(r"(?m)^结论（PASS/FAIL）：\s*(?:PASS|FAIL)\s*$")
FILLED_BOOLEAN = re.compile(r"(?m)^.*（是/否）：\s*(?:是|否)\s*$")
FILLED_REVIEWER = re.compile(r"(?m)^评审人：[ \t]*\S+.*$")
FILLED_NOTE = re.compile(r"(?m)^备注：[ \t]*\S+.*$")


@dataclass(frozen=True)
class ChapterSpec:
    provider: str
    scenario: str
    sample_type: str
    selector: str
    tags: tuple[str, ...]


CHAPTER_SPECS = (
    ChapterSpec("luna", "projectflow-current-history-dogfood", "chapter-representativeness", "largest",
        ("projectflow", "large-coherent-long-history")),
    ChapterSpec("luna", "chapter-large-coherent", "chapter-representativeness", "largest",
        ("large-coherent",)),
    ChapterSpec("luna", "chapter-large-heterogeneous", "chapter-representativeness", "representation-boundary",
        ("large-heterogeneous", "representation-boundary")),
    ChapterSpec("luna", "chapter-review-fixtures", "minor-first", "first", ("minor-first",)),
    ChapterSpec("deepseek", "projectflow-current-history-dogfood", "chapter-representativeness", "largest",
        ("projectflow", "large-coherent-long-history")),
    ChapterSpec("deepseek", "chapter-review-fixtures", "supporting-heavy", "largest", ("supporting-heavy",)),
    ChapterSpec("deepseek", "non-code-research-report", "chapter-representativeness", "first",
        ("non-code", "report-document")),
    ChapterSpec("deepseek", "non-code-data-analysis", "chapter-representativeness", "first",
        ("non-code", "data-analysis")),
    ChapterSpec("qwen", "non-code-presentation", "chapter-representativeness", "first",
        ("non-code", "presentation")),
    ChapterSpec("qwen", "chapter-repair-safety", "deterministic-fallback", "largest",
        ("deterministic-fallback", "repair")),
    ChapterSpec("qwen", "chapter-review-fixtures", "short-coherent", "first", ("short-coherent",)),
    ChapterSpec("qwen", "chapter-review-fixtures", "user-declared", "first",
        ("correction", "user-declared")),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Freeze the V3.8.5 Final Chapter Representativeness human-review package."
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-code-head", required=True)
    parser.add_argument("--evidence-root", type=Path, default=DEFAULT_FINAL_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--worksheet", type=Path, default=DEFAULT_WORKSHEET)
    return parser.parse_args()


def canonical_lf_sha256(path: Path) -> str:
    value = path.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def provider_profile(value: dict[str, Any], slug: str, path: Path) -> dict[str, str]:
    profile = value.get("provider")
    if not isinstance(profile, dict):
        raise ValueError(f"Provider profile is missing: {relative(path)}")
    expected = {
        "luna": ("gpt-5.6-luna", "OPENAI_RESPONSES", "max"),
        "deepseek": ("deepseek-v4-flash", "OPENAI_CHAT_COMPLETIONS", "max"),
        "qwen": ("qwen3.7-plus", "ANTHROPIC_MESSAGES", "max"),
    }[slug]
    actual = (profile.get("model"), profile.get("protocol"), profile.get("reasoningEffort"))
    if actual != expected:
        raise ValueError(f"unexpected frozen Provider profile for {slug}: {actual}")
    return {
        "name": str(profile.get("name") or ""),
        "model": str(actual[0]),
        "protocol": str(actual[1]),
        "reasoningEffort": str(actual[2]),
    }


def scenario(value: dict[str, Any], name: str) -> dict[str, Any]:
    for item in value.get("scenarios", []):
        if isinstance(item, dict) and item.get("name") == name and item.get("status") == "PASS":
            return item
    raise ValueError(f"passed Final Chapter scenario is missing: {name}")


def sample_group(value: dict[str, Any], name: str, sample_type: str) -> list[dict[str, Any]]:
    samples = scenario(value, name).get("samples")
    if not isinstance(samples, list):
        raise ValueError(f"scenario samples are missing: {name}")
    for group in samples:
        if isinstance(group, dict) and group.get("sampleType") == sample_type:
            items = group.get("items")
            if isinstance(items, list) and all(isinstance(item, dict) for item in items):
                return items
    raise ValueError(f"sample group is missing: {name}:{sample_type}")


def select_chapter(items: list[dict[str, Any]], selector: str, source: str) -> dict[str, Any]:
    if not items:
        raise ValueError(f"Chapter sample group is empty: {source}")
    if selector == "first":
        return items[0]
    if selector == "largest":
        return max(items, key=lambda item: int(item.get("storyCount") or 0))
    if selector == "representation-boundary":
        for item in items:
            if "REPRESENTATION_BOUNDARY" in (item.get("boundarySignals") or []):
                return item
        raise ValueError(f"representation boundary sample is missing: {source}")
    raise ValueError(f"unsupported Chapter selector: {selector}")


def validate_chapter(entity: dict[str, Any], source: str) -> None:
    required = (
        "id", "title", "summary", "storyCount", "primaryStoryCount", "supportingStoryCount",
        "representativeClusters", "representativePrimaryCoverage", "selectedRepresentativeOutcomes",
        "evidenceSafeStatus", "unknowns", "conflicts", "narrativeStatus", "deterministicFallback",
        "presentationRevision",
    )
    missing = [key for key in required if key not in entity]
    if missing:
        raise ValueError(f"Chapter sample lacks engineering fields {missing}: {source}")
    if not isinstance(entity.get("representativeClusters"), list) or not entity["representativeClusters"]:
        raise ValueError(f"Chapter sample has no representative clusters: {source}")
    coverage = float(entity.get("representativePrimaryCoverage") or 0.0)
    if coverage < 0.60 or coverage > 1.0:
        raise ValueError(f"Chapter representative coverage is out of bounds: {source}={coverage}")
    if entity.get("evidenceSafeStatus") != "VALIDATED_WITHIN_STORY_CLAIM_CEILINGS":
        raise ValueError(f"Chapter sample is not Evidence-safe: {source}")


def truth_semantic(value: dict[str, Any]) -> str:
    attribution = value.get("claimAttribution") if isinstance(value.get("claimAttribution"), dict) else {}
    payload = {
        "role": value.get("role"),
        "primaryStoryId": value.get("primaryStoryId"),
        "supportingChangeRefs": sorted(value.get("supportingChangeRefs") or []),
        "evidenceRefs": sorted(value.get("evidenceRefs") or []),
        "reasonEvidenceRefs": sorted(value.get("reasonEvidenceRefs") or []),
        "claimSubject": attribution.get("subject"),
        "claimAction": attribution.get("action"),
        "claimState": attribution.get("state"),
        "directEvidenceRefs": sorted(attribution.get("directEvidenceRefs") or []),
        "indirectEvidenceRefs": sorted(attribution.get("indirectEvidenceRefs") or []),
        "supportClass": attribution.get("supportClass"),
    }
    content = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(content.encode("utf-8")).hexdigest()


def current_story_candidates(evidence_root: Path) -> tuple[
    list[dict[str, Any]], dict[str, dict[str, Any]], dict[str, dict[str, Any]]
]:
    entries: list[dict[str, Any]] = []
    entities: dict[str, dict[str, Any]] = {}
    profiles: dict[str, dict[str, Any]] = {}
    # Luna replaces the retired GLM slot for the frozen Round 3 Story inputs.
    # The alias preserves the immutable Round 3 sample IDs while recording the
    # actual current Provider on every changed-subset entry.
    for slug, frozen_slug, name in (
        ("luna", "glm", "GPT 5.6 Luna"),
        ("deepseek", "deepseek", "DeepSeek V4 Flash"),
    ):
        stories, _, selected = provider_samples(evidence_root, slug, name, 3)
        for entry in stories:
            source_sample_id = entry["sampleId"]
            sample_id = source_sample_id.replace(f"{slug}-story-", f"{frozen_slug}-story-", 1)
            entry["sampleId"] = sample_id
            entries.append(entry)
            entities[sample_id] = selected[source_sample_id]
        scenario_path = evidence_root / slug / "history-real-scenarios.json"
        profiles[slug] = provider_profile(load_json(scenario_path), slug, scenario_path)
    qwen_path = evidence_root / "qwen" / "history-real-scenarios.json"
    profiles["qwen"] = provider_profile(load_json(qwen_path), "qwen", qwen_path)
    if len(entries) != 30:
        raise ValueError(f"Final Story regression must resolve 30 Round 3 inputs, got {len(entries)}")
    return entries, entities, profiles


def frozen_story_candidates() -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]], dict[str, Any]]:
    manifest = load_json(ROUND3_MANIFEST)
    if manifest.get("reviewerCount") != 0 or len(manifest.get("stories") or []) != 30:
        raise ValueError("Round 3 Story baseline is not the frozen blank 30-sample package")
    entries: dict[str, dict[str, Any]] = {}
    entities: dict[str, dict[str, Any]] = {}
    for slug, name in (("glm", "GLM"), ("deepseek", "DeepSeek")):
        stories, _, selected = provider_samples(ROUND3_ROOT / "real-model", slug, name, 3)
        entries.update({entry["sampleId"]: entry for entry in stories})
        entities.update(selected)
    for sample in manifest["stories"]:
        sample_id = sample.get("sampleId")
        if sample_id not in entities or canonical_hash(entities[sample_id]) != sample.get("contentHash"):
            raise ValueError(f"Round 3 frozen Story content no longer matches its manifest: {sample_id}")
    return entries, entities, manifest


def story_regression(
    current_entries: list[dict[str, Any]],
    current_entities: dict[str, dict[str, Any]],
    frozen_entities: dict[str, dict[str, Any]],
    frozen_manifest: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]], dict[str, dict[str, Any]]]:
    frozen_manifest_entries = {item["sampleId"]: item for item in frozen_manifest["stories"]}
    changed: list[dict[str, Any]] = []
    truth_changed: list[str] = []
    current_by_id = {item["sampleId"]: item for item in current_entries}
    for sample_id in sorted(frozen_manifest_entries):
        old = frozen_entities[sample_id]
        current = current_entities.get(sample_id)
        entry = current_by_id.get(sample_id)
        if current is None or entry is None:
            raise ValueError(f"current Final Closure Story is missing: {sample_id}")
        old_truth = truth_semantic(old)
        current_truth = truth_semantic(current)
        exact = canonical_hash(current) == frozen_manifest_entries[sample_id]["contentHash"]
        truth_equal = old_truth == current_truth
        if not truth_equal:
            truth_changed.append(sample_id)
        if not exact:
            changed.append({
                "sampleId": sample_id,
                "provider": entry["provider"],
                "source": entry["source"],
                "artifact": entry["artifact"],
                "entityId": current.get("id"),
                "round3ContentHash": frozen_manifest_entries[sample_id]["contentHash"],
                "finalContentHash": canonical_hash(current),
                "round3TruthSemanticHash": old_truth,
                "finalTruthSemanticHash": current_truth,
                "truthSemanticUnchanged": truth_equal,
                "titleChanged": old.get("title") != current.get("title"),
                "summaryChanged": old.get("summary") != current.get("summary"),
                "coverageTags": frozen_manifest_entries[sample_id].get("coverageTags") or [],
            })
    if truth_changed:
        raise ValueError("Story truth/Evidence semantic regression detected: " + ", ".join(truth_changed))
    summary = {
        "round3FrozenStoryCount": 30,
        "exactContentUnchangedCount": 30 - len(changed),
        "changedPresentationCount": len(changed),
        "truthSemanticUnchangedCount": 30,
        "truthSemanticChangedCount": 0,
        "changedSampleIds": [item["sampleId"] for item in changed],
        "changedSubsetHumanReviewRequired": bool(changed),
    }
    return summary, changed, current_entities


def chapter_entries(
    evidence_root: Path,
    profiles: dict[str, dict[str, Any]],
) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    artifacts = {
        slug: load_json(evidence_root / slug / "history-real-scenarios.json")
        for slug in ("luna", "deepseek", "qwen")
    }
    result: list[dict[str, Any]] = []
    entities: dict[str, dict[str, Any]] = {}
    provider_counts = {"luna": 0, "deepseek": 0, "qwen": 0}
    provider_names = {
        "luna": "GPT 5.6 Luna",
        "deepseek": "DeepSeek V4 Flash",
        "qwen": "Qwen3.7 Plus",
    }
    for sequence, spec in enumerate(CHAPTER_SPECS, start=1):
        source = f"{spec.scenario}:{spec.sample_type}"
        entity = select_chapter(
            sample_group(artifacts[spec.provider], spec.scenario, spec.sample_type), spec.selector, source
        )
        validate_chapter(entity, source)
        provider_counts[spec.provider] += 1
        sample_id = f"{spec.provider}-final-chapter-{provider_counts[spec.provider]:02d}"
        entry = {
            "sampleId": sample_id,
            "provider": provider_names[spec.provider],
            "model": profiles[spec.provider]["model"],
            "protocol": profiles[spec.provider]["protocol"],
            "reasoningEffort": profiles[spec.provider]["reasoningEffort"],
            "artifact": relative(evidence_root / spec.provider / "history-real-scenarios.json"),
            "source": source,
            "entityId": entity.get("id"),
            "contentHash": canonical_hash(entity),
            "presentationRevision": entity.get("presentationRevision"),
            "coverageTags": list(spec.tags),
        }
        result.append(entry)
        entities[sample_id] = entity
    if len(result) < 8 or len(result) > 12 or provider_counts != {"luna": 4, "deepseek": 4, "qwen": 4}:
        raise ValueError(f"Final Chapter sample must be 8-12 and balanced; got {len(result)} {provider_counts}")
    return result, entities


def artifact_hashes(evidence_root: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for slug in ("luna", "deepseek", "qwen"):
        for name in ("history-ground-truth-real-result.json", "history-real-scenarios.json"):
            path = evidence_root / slug / name
            if not path.is_file():
                raise ValueError(f"missing Final Closure normalized artifact: {relative(path)}")
            result[relative(path)] = canonical_lf_sha256(path)
    return result


def chapter_worksheet(sample: dict[str, Any], entity: dict[str, Any]) -> list[str]:
    clusters = entity.get("representativeClusters") or []
    cluster_text = "；".join(
        f"{cluster.get('role')} {cluster.get('label')} weight={cluster.get('weight')} "
        f"Primary={cluster.get('primaryStoryCount')} Supporting={cluster.get('supportingStoryCount')} "
        f"outcomes={inline(cluster.get('representativeOutcomes'))} state≤{cluster.get('claimCeiling')}"
        for cluster in clusters
    )
    return [
        f"## {sample['sampleId']}  {sample['provider']}  CHAPTER",
        "",
        f"覆盖标签：{', '.join(sample['coverageTags'])}",
        f"来源：{sample['source']}",
        f"Chapter ID：{sample['entityId']}",
        f"内容哈希：{sample['contentHash']}",
        f"Provider/Model：{sample['provider']} / {sample['model']} / {sample['protocol']} / {sample['reasoningEffort']}",
        f"时间范围：{inline(entity.get('from'))} 至 {inline(entity.get('to'))}",
        f"Story count：{entity.get('storyCount')}",
        f"Primary count：{entity.get('primaryStoryCount')}",
        f"Supporting count：{entity.get('supportingStoryCount')}",
        f"Representative clusters：{cluster_text}",
        f"Cluster coverage：{entity.get('representativePrimaryCoverage')}",
        f"Selected representative outcomes：{inline(entity.get('selectedRepresentativeOutcomes'))}",
        f"Evidence-safe status：{inline(entity.get('evidenceSafeStatus'))}",
        f"标题：{inline(entity.get('title'))}",
        f"摘要：{inline(entity.get('summary'))}",
        f"Unknown：{inline(entity.get('unknowns'))}",
        f"Conflict：{inline(entity.get('conflicts'))}",
        f"Narrative status：{inline(entity.get('narrativeStatus'))}",
        f"Deterministic fallback：{str(bool(entity.get('deterministicFallback'))).lower()}",
        f"Boundary signals：{inline(entity.get('boundarySignals'))}",
        "第一眼能否理解阶段中心（是/否）：",
        "标题是否代表整个 Chapter 而不是一个小 Story（是/否）：",
        "摘要是否覆盖主要成果群（是/否）：",
        "是否遗漏最重要主成果（是/否）：",
        "是否把 Supporting 当阶段中心（是/否）：",
        "多主题没有共同中心时是否本应拆分（是/否）：",
        "是否过度空泛（是/否）：",
        "是否过度具体到 minor outcome（是/否）：",
        "是否有 planned→implemented 等 P0（是/否）：",
        "是否愿意继续下钻 Story/Evidence（是/否）：",
        "人工可读性评分（1-5）：",
        "Chapter representativeness 评分（1-5）：",
        "结论（PASS/FAIL）：",
        "备注：",
        "",
    ]


def changed_story_worksheet(sample: dict[str, Any], entity: dict[str, Any]) -> list[str]:
    attribution = entity.get("claimAttribution") if isinstance(entity.get("claimAttribution"), dict) else {}
    return [
        f"## {sample['sampleId']}  {sample['provider']}  CHANGED STORY",
        "",
        f"来源：{sample['source']}",
        f"Round 3 hash：{sample['round3ContentHash']}",
        f"Final Closure hash：{sample['finalContentHash']}",
        f"Truth semantic unchanged：{str(sample['truthSemanticUnchanged']).lower()}",
        f"标题：{inline(entity.get('title'))}",
        f"摘要：{inline(entity.get('summary'))}",
        f"Before：{inline(entity.get('before'))}",
        f"Change：{inline(entity.get('change'))}",
        f"After：{inline(entity.get('after'))}",
        f"Claim Subject/Action/State：{inline(attribution.get('subject'))} / "
        f"{inline(attribution.get('action'))} / {inline(attribution.get('state'))}",
        f"Direct Evidence IDs：{inline(attribution.get('directEvidenceRefs'))}",
        "第一眼能否理解（是/否）：",
        "Evidence 是否支撑标题与摘要（是/否）：",
        "Claim state 是否被提升（是/否）：",
        "P0 truthfulness failure（是/否）：",
        "人工可读性评分（1-5）：",
        "结论（PASS/FAIL）：",
        "备注：",
        "",
    ]


def write_outputs(args: argparse.Namespace) -> tuple[int, int]:
    if not re.fullmatch(r"[1-9][0-9]*", args.run_id):
        raise ValueError("run ID must be a positive GitHub Actions run ID")
    source_head = args.source_code_head.strip().lower()
    if not re.fullmatch(r"[0-9a-f]{40}", source_head):
        raise ValueError("source code head must be an exact 40-character SHA")
    evidence_root = args.evidence_root.resolve()
    output = args.output.resolve()
    worksheet = args.worksheet.resolve()
    relative(evidence_root)
    relative(output)
    relative(worksheet)
    if output in {ROUND3_MANIFEST.resolve(), ROUND3_WORKSHEET.resolve()} or worksheet in {
        ROUND3_MANIFEST.resolve(), ROUND3_WORKSHEET.resolve()
    }:
        raise ValueError("Final Chapter package must not overwrite Round 3")

    for slug in ("luna", "deepseek", "qwen"):
        for name in ("history-ground-truth-real-result.json", "history-real-scenarios.json"):
            path = evidence_root / slug / name
            value = load_json(path)
            qualified(value, path)
            if name == "history-real-scenarios.json" and value.get("scenarioScope") != "chapter":
                raise ValueError(f"Final Chapter artifact is not affected Chapter scope: {relative(path)}")

    current_entries, current_entities, profiles = current_story_candidates(evidence_root)
    _, frozen_entities, frozen_manifest = frozen_story_candidates()
    regression, changed_stories, changed_entities = story_regression(
        current_entries, current_entities, frozen_entities, frozen_manifest
    )
    chapters, chapter_entities = chapter_entries(evidence_root, profiles)
    hashes = artifact_hashes(evidence_root)
    manifest = {
        "version": "projectflow-v385-final-chapter-review-v1",
        "status": "HUMAN_REVIEW_REQUIRED",
        "v385FinalAcceptance": "NOT_PASS",
        "chapterRepresentativenessAutomatedGate": "PASS",
        "chapterRepresentativenessHumanGate": "NOT_RUN",
        "storyHumanGate": "PENDING_ROUND3_AND_CHANGED_SUBSET_REVIEW",
        "sourceRunId": args.run_id,
        "sourceRunUrl": f"https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/{args.run_id}",
        "sourceCodeHead": source_head,
        "providerProfiles": profiles,
        "sourceArtifactCanonicalLfSha256": hashes,
        "round3": {
            "status": frozen_manifest.get("status"),
            "reviewerCount": frozen_manifest.get("reviewerCount"),
            "storyCount": len(frozen_manifest.get("stories") or []),
            "chapterCount": len(frozen_manifest.get("chapters") or []),
            "manifestCanonicalLfSha256": canonical_lf_sha256(ROUND3_MANIFEST),
            "worksheetCanonicalLfSha256": canonical_lf_sha256(ROUND3_WORKSHEET),
            "immutable": True,
        },
        "storyRegression": regression,
        "changedStories": changed_stories,
        "chapters": chapters,
        "reviewerCount": 0,
        "reviewMode": "PENDING_SINGLE_HUMAN_REVIEWER",
        "thresholds": {
            "storyReadabilityAverage": 4.0,
            "chapterReadabilityAverage": 4.0,
            "chapterRepresentativenessAverage": 4.0,
            "everyCoreDimensionAverage": 3.5,
            "truthfulnessP0Count": 0,
        },
        "security": {
            "modelSelfScoring": False,
            "rawPromptStored": False,
            "rawResponseStored": False,
            "reasoningStored": False,
            "credentialsStored": False,
            "absolutePathStored": False,
        },
    }
    worksheet_lines = [
        "# ProjectFlow V3.8.5 Final Chapter Representativeness 人工复核表",
        "",
        "状态：HUMAN_REVIEW_REQUIRED / V3.8.5 NOT PASS。此文件只冻结工程样本；不得由 Codex 或任何验证模型代填。",
        f"来源 Run：{args.run_id}",
        f"来源 Head：{source_head}",
        "Round 1：NEEDS_REVISION_NOT_APPROVED；Round 2：NEEDS_REVISION_NOT_APPROVED；Round 3 保持冻结且 reviewerCount=0。",
        f"Story regression：30 个 Round 3 输入中，{regression['changedPresentationCount']} 个展示内容变化，"
        f"Truth/Evidence semantic change 为 0。变化子集必须由同一真实评审人复核。",
        f"Final Chapter 样本：{len(chapters)} 个，GPT 5.6 Luna 4 个、DeepSeek V4 Flash 4 个、Qwen3.7 Plus 4 个。",
        "评审模式：待一名真实人工评审；最终结论必须披露 single-reviewer limitation。",
        "评审人：",
        "",
        "# Round 3 Story 展示变化子集",
        "",
    ]
    for sample in changed_stories:
        worksheet_lines.extend(changed_story_worksheet(sample, changed_entities[sample["sampleId"]]))
    worksheet_lines.extend(["# Final Chapter Representativeness 样本", ""])
    for sample in chapters:
        worksheet_lines.extend(chapter_worksheet(sample, chapter_entities[sample["sampleId"]]))
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    worksheet_text = "\n".join(worksheet_lines)
    for label, content in (("manifest", manifest_text), ("worksheet", worksheet_text)):
        if SENSITIVE_PATTERN.search(content) or ABSOLUTE_PATH_PATTERN.search(content):
            raise ValueError(f"refusing to write {label} containing sensitive data or an absolute path")
    if FILLED_SCORE.search(worksheet_text) or FILLED_RESULT.search(worksheet_text):
        raise ValueError("Final review worksheet contains a prefilled human score or result")
    if FILLED_BOOLEAN.search(worksheet_text) or FILLED_REVIEWER.search(worksheet_text) or FILLED_NOTE.search(worksheet_text):
        raise ValueError("Final review worksheet contains a prefilled human field")
    output.parent.mkdir(parents=True, exist_ok=True)
    worksheet.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(manifest_text, encoding="utf-8")
    worksheet.write_text(worksheet_text, encoding="utf-8")
    return len(changed_stories), len(chapters)


def main() -> int:
    args = parse_args()
    try:
        changed, chapters = write_outputs(args)
    except (OSError, ValueError, KeyError, TypeError) as exception:
        print(f"V385_FINAL_CHAPTER_REVIEW_FAILED {exception}", file=sys.stderr)
        return 1
    print(f"V385_FINAL_CHAPTER_REVIEW_OK changedStories={changed} chapters={chapters} reviewerCount=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
