from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_EVIDENCE_ROOT = ROOT / "docs" / "acceptance-evidence" / "v3.8.5" / "real-model"
DEFAULT_MANIFEST = ROOT / "docs" / "acceptance-evidence" / "v3.8.5" / "human-review-sample-manifest.json"
DEFAULT_WORKSHEET = ROOT / "docs" / "acceptance-evidence" / "v3.8.5" / "human-review-worksheet.md"
SENSITIVE_PATTERN = re.compile(
    r"(?:sk-[A-Za-z0-9_-]{20,}|ark-[A-Za-z0-9-]{20,}|Bearer [A-Za-z0-9._-]{24,})"
)
ABSOLUTE_PATH_PATTERN = re.compile(r"(?:(?<![A-Za-z0-9+.-])[A-Za-z]:[\\/]|/(?:Users|home|tmp|var)/)")


@dataclass(frozen=True)
class SampleSpec:
    source: str
    index: int
    tags: tuple[str, ...]


QUALIFICATION_STORIES = (
    SampleSpec("cal-small-five-commit-project", 0, ("short-history",)),
    SampleSpec("cal-create-modify-delete-restore", 0, ("lifecycle-restore",)),
    SampleSpec("cal-multi-commit-one-change", 0, ("multi-commit-one-result",)),
    SampleSpec("cal-primary-supporting", 0, ("supporting",)),
    SampleSpec("cal-non-code-project", 0, ("non-code",)),
    SampleSpec("cal-reason-unknown", 0, ("unknown-reason",)),
    SampleSpec("cal-conflict-preservation", 0, ("conflict",)),
    SampleSpec("holdout-chaotic-history", 0, ("long-history",)),
    SampleSpec("holdout-rename-move-split-merge", 0, ("rename-move", "split-merge")),
    SampleSpec("holdout-unrelated-commit", 0, ("one-commit-multiple-results",)),
    SampleSpec("holdout-generic-message", 0, ("generic-commit",)),
)

QUALIFICATION_CHAPTERS = (
    SampleSpec("cal-small-five-commit-project", 0, ("short-history",)),
    SampleSpec("cal-non-code-project", 0, ("non-code",)),
)

SCENARIO_STORIES = (
    SampleSpec("correction-local-invalidation", 0, ("correction",)),
    SampleSpec("projectflow-current-history-dogfood:primary", 0, ("projectflow", "long-history")),
    SampleSpec("projectflow-current-history-dogfood:primary", 1, ("projectflow", "long-history")),
    SampleSpec("projectflow-current-history-dogfood:explicit-supporting", 0,
        ("projectflow", "supporting")),
)

SCENARIO_CHAPTERS = (
    SampleSpec("projectflow-current-history-dogfood:chapters", 0, ("projectflow", "long-history")),
    SampleSpec("projectflow-current-history-dogfood:chapters", 1, ("projectflow", "long-history")),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Freeze a genuine-human review sample from qualified V3.8.5 normalized artifacts."
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--evidence-root", type=Path, default=DEFAULT_EVIDENCE_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--worksheet", type=Path, default=DEFAULT_WORKSHEET)
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exception:
        raise ValueError(f"missing normalized artifact: {relative(path)}") from exception
    except json.JSONDecodeError as exception:
        raise ValueError(f"invalid JSON artifact {relative(path)} at line {exception.lineno}") from exception
    if not isinstance(value, dict):
        raise ValueError(f"artifact root must be an object: {relative(path)}")
    return value


def relative(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError as exception:
        raise ValueError("artifact and output paths must stay inside the repository") from exception


def qualified(value: dict[str, Any], path: Path) -> None:
    qualification = value.get("qualification")
    if not isinstance(qualification, dict) or qualification.get("qualified") is not True:
        raise ValueError(f"refusing to freeze human samples from an unqualified artifact: {relative(path)}")
    security = value.get("security")
    if not isinstance(security, dict):
        raise ValueError(f"qualified artifact lacks security diagnostics: {relative(path)}")
    unsafe = [
        key for key, result in security.items()
        if ("persisted" in key.lower() or "stored" in key.lower()) and result is not False
    ]
    if unsafe:
        raise ValueError(f"qualified artifact reports unsafe persisted content: {relative(path)}")


def candidate(value: dict[str, Any], case_id: str) -> dict[str, Any]:
    for item in value.get("humanReviewCandidates", []):
        if isinstance(item, dict) and item.get("caseId") == case_id:
            return item
    raise ValueError(f"human review candidate is missing for case {case_id}")


def scenario(value: dict[str, Any], name: str) -> dict[str, Any]:
    for item in value.get("scenarios", []):
        if isinstance(item, dict) and item.get("name") == name and item.get("status") == "PASS":
            return item
    raise ValueError(f"passed human review scenario is missing: {name}")


def entity_from_list(values: Any, index: int, source: str) -> dict[str, Any]:
    if not isinstance(values, list) or index < 0 or index >= len(values) or not isinstance(values[index], dict):
        raise ValueError(f"sample index {index} is unavailable for {source}")
    return values[index]


def scenario_entity(value: dict[str, Any], spec: SampleSpec) -> dict[str, Any]:
    name, separator, sample_type = spec.source.partition(":")
    selected = scenario(value, name)
    samples = selected.get("samples")
    if not separator:
        return entity_from_list(samples, spec.index, spec.source)
    if not isinstance(samples, list):
        raise ValueError(f"scenario samples are missing: {name}")
    for group in samples:
        if isinstance(group, dict) and group.get("sampleType") == sample_type:
            return entity_from_list(group.get("items"), spec.index, spec.source)
    raise ValueError(f"scenario sample group is missing: {spec.source}")


def canonical_hash(entity: dict[str, Any]) -> str:
    content = json.dumps(entity, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return "sha256:" + hashlib.sha256(content).hexdigest()


def project_type(tags: tuple[str, ...]) -> str:
    if "non-code" in tags:
        return "NON_CODE"
    if "projectflow" in tags:
        return "PROJECTFLOW_SOFTWARE"
    return "SOFTWARE_FIXTURE"


def sample_entry(
    kind: str,
    sequence: int,
    provider_slug: str,
    provider_name: str,
    artifact_path: Path,
    source: str,
    tags: tuple[str, ...],
    entity: dict[str, Any],
) -> dict[str, Any]:
    entity_id = entity.get("id")
    revision = entity.get("presentationRevision")
    if not isinstance(entity_id, str) or not entity_id or not isinstance(revision, str) or not revision:
        raise ValueError(f"{kind} sample lacks stable identity or presentation revision: {source}")
    return {
        "kind": kind,
        "sampleId": f"{provider_slug}-{kind.lower()}-{sequence:02d}",
        "entityId": entity_id,
        "artifact": relative(artifact_path),
        "source": source,
        "contentHash": canonical_hash(entity),
        "presentationRevision": revision,
        "provider": provider_name,
        "projectType": project_type(tags),
        "coverageTags": list(tags),
    }


def provider_samples(
    evidence_root: Path,
    provider_slug: str,
    provider_name: str,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, dict[str, Any]]]:
    provider_root = evidence_root / provider_slug
    qualification_path = provider_root / "history-ground-truth-real-result.json"
    scenarios_path = provider_root / "history-real-scenarios.json"
    qualification = load_json(qualification_path)
    scenarios = load_json(scenarios_path)
    qualified(qualification, qualification_path)
    qualified(scenarios, scenarios_path)

    stories: list[dict[str, Any]] = []
    chapters: list[dict[str, Any]] = []
    entities: dict[str, dict[str, Any]] = {}
    for spec in QUALIFICATION_STORIES:
        entity = entity_from_list(candidate(qualification, spec.source).get("stories"), spec.index, spec.source)
        entry = sample_entry("STORY", len(stories) + 1, provider_slug, provider_name,
            qualification_path, spec.source, spec.tags, entity)
        stories.append(entry)
        entities[entry["sampleId"]] = entity
    for spec in SCENARIO_STORIES:
        entity = scenario_entity(scenarios, spec)
        entry = sample_entry("STORY", len(stories) + 1, provider_slug, provider_name,
            scenarios_path, spec.source, spec.tags, entity)
        stories.append(entry)
        entities[entry["sampleId"]] = entity
    for spec in QUALIFICATION_CHAPTERS:
        entity = entity_from_list(candidate(qualification, spec.source).get("chapters"), spec.index, spec.source)
        entry = sample_entry("CHAPTER", len(chapters) + 1, provider_slug, provider_name,
            qualification_path, spec.source, spec.tags, entity)
        chapters.append(entry)
        entities[entry["sampleId"]] = entity
    for spec in SCENARIO_CHAPTERS:
        entity = scenario_entity(scenarios, spec)
        entry = sample_entry("CHAPTER", len(chapters) + 1, provider_slug, provider_name,
            scenarios_path, spec.source, spec.tags, entity)
        chapters.append(entry)
        entities[entry["sampleId"]] = entity
    return stories, chapters, entities


def inline(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        value = "；".join(str(item) for item in value if item is not None)
    return " ".join(str(value).replace("\r", " ").replace("\n", " ").split())


def worksheet_section(sample: dict[str, Any], entity: dict[str, Any]) -> list[str]:
    lines = [
        f"## {sample['sampleId']}  {sample['provider']}  {sample['kind']}",
        "",
        f"项目类型：{sample['projectType']}",
        f"来源：{sample['source']}",
        f"Story/Chapter ID：{sample['entityId']}",
        f"覆盖标签：{', '.join(sample['coverageTags'])}",
        f"内容哈希：{sample['contentHash']}",
        f"标题：{inline(entity.get('title'))}",
        f"摘要：{inline(entity.get('summary'))}",
    ]
    if sample["kind"] == "STORY":
        lines.extend([
            f"Before：{inline(entity.get('before'))}",
            f"Change：{inline(entity.get('change'))}",
            f"After：{inline(entity.get('after'))}",
            f"Reason：{inline(entity.get('reason'))}",
            f"Reason Evidence 数：{len(entity.get('reasonEvidenceRefs') or [])}",
            f"Reason Evidence IDs：{inline(entity.get('reasonEvidenceRefs'))}",
            f"Evidence IDs：{inline(entity.get('evidenceRefs'))}",
            f"Unknowns：{inline(entity.get('unknowns'))}",
            f"Conflicts：{inline(entity.get('conflicts'))}",
            "不看文件名能说清改了什么（是/否）：",
            "能说清原来状态（是/否）：",
            "能说清现在状态（是/否）：",
            "能说清对项目的结果（是/否）：",
            "英文内部 enum 泄漏（是/否）：",
            "“当前行为得到更新”式废话（是/否）：",
            "文件变化冒充项目成果（是/否）：",
        ])
    else:
        lines.extend([
            "Before：不适用（Chapter 是 Story 的时间汇总层）",
            "Change：不适用（Chapter 是 Story 的时间汇总层）",
            "After：不适用（Chapter 是 Story 的时间汇总层）",
            "Reason：不适用（Chapter 不新增原因事实）",
            f"时间范围：{inline(entity.get('from'))} 至 {inline(entity.get('to'))}",
            f"Story 数：{inline(entity.get('storyCount'))}",
            "时间层次清楚（是/否）：",
            "中心变化清楚（是/否）：",
            "Supporting 未冒充主要成果（是/否）：",
        ])
    lines.extend([
        "技术术语泄漏（是/否）：",
        "空泛模板（是/否）：",
        "无 Evidence 猜测原因（是/否）：",
        "人工可读性评分（1-5）：",
        "评审备注：",
        "结论（PASS/FAIL）：",
        "",
    ])
    return lines


def write_outputs(
    run_id: str,
    output: Path,
    worksheet: Path,
    stories: list[dict[str, Any]],
    chapters: list[dict[str, Any]],
    entities: dict[str, dict[str, Any]],
) -> None:
    manifest = {
        "version": "projectflow-v385-human-review-sample-v1",
        "status": "PENDING_HUMAN_REVIEW",
        "sourceRunId": run_id,
        "sourceRunUrl": f"https://github.com/xiaochuqing-dev/ProjectFlow/actions/runs/{run_id}",
        "samplingMethod": "fixed stratified selection from qualified normalized GLM and DeepSeek artifacts",
        "reviewerCount": 0,
        "reviewMode": "PENDING_SINGLE_HUMAN_REVIEWER",
        "stories": stories,
        "chapters": chapters,
        "security": {
            "modelSelfScoring": False,
            "rawPromptStored": False,
            "rawResponseStored": False,
            "reasoningStored": False,
            "credentialsStored": False,
        },
    }
    worksheet_lines = [
        "# ProjectFlow V3.8.5 RC2 人工可读性复核表",
        "",
        "状态：PENDING_HUMAN_REVIEW。此文件只冻结样本并提供空白人工评分项；不得由模型代填。",
        "",
        f"来源 Run：{run_id}",
        f"样本：{len(stories)} Story，{len(chapters)} Chapter。",
        "评审模式：待一名真实人工评审；最终报告必须明确 single-reviewer limitation，不冒充多人一致。",
        "评审人：",
        "4 分表示普通用户读一遍后能大致转述原来怎样、改了什么、现在怎样。低分必须保留。",
        "",
    ]
    for sample in stories + chapters:
        worksheet_lines.extend(worksheet_section(sample, entities[sample["sampleId"]]))
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    worksheet_text = "\n".join(worksheet_lines)
    for label, content in (("manifest", manifest_text), ("worksheet", worksheet_text)):
        if SENSITIVE_PATTERN.search(content) or ABSOLUTE_PATH_PATTERN.search(content):
            raise ValueError(f"refusing to write {label} containing sensitive data or an absolute path")
    output.parent.mkdir(parents=True, exist_ok=True)
    worksheet.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(manifest_text, encoding="utf-8")
    worksheet.write_text(worksheet_text, encoding="utf-8")


def main() -> int:
    args = parse_args()
    try:
        if not re.fullmatch(r"[1-9][0-9]*", args.run_id):
            raise ValueError("run ID must be a positive numeric GitHub Actions run ID")
        evidence_root = args.evidence_root.resolve()
        relative(evidence_root)
        output = args.output.resolve()
        worksheet = args.worksheet.resolve()
        relative(output)
        relative(worksheet)
        all_stories: list[dict[str, Any]] = []
        all_chapters: list[dict[str, Any]] = []
        all_entities: dict[str, dict[str, Any]] = {}
        for slug, name in (("glm", "GLM"), ("deepseek", "DeepSeek")):
            stories, chapters, entities = provider_samples(evidence_root, slug, name)
            all_stories.extend(stories)
            all_chapters.extend(chapters)
            all_entities.update(entities)
        if len(all_stories) != 30 or len(all_chapters) != 8:
            raise ValueError("frozen sample must contain exactly 30 Story and 8 Chapter entries")
        write_outputs(args.run_id, output, worksheet, all_stories, all_chapters, all_entities)
    except (OSError, ValueError) as exception:
        print(f"V385_HUMAN_REVIEW_SAMPLE_FAILED {exception}", file=sys.stderr)
        return 1
    print(f"V385_HUMAN_REVIEW_SAMPLE_OK stories={len(all_stories)} chapters={len(all_chapters)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
