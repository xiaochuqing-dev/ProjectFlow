from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE_ROOT = ROOT / "docs" / "acceptance-evidence" / "v3.8.0"
MANIFEST_PATH = EVIDENCE_ROOT / "acceptance-freeze-manifest.json"
SENSITIVE_VALUE = re.compile(
    r"(?i)(?:[a-z]:[\\/](?:users|documents and settings)[\\/]|/(?:home|users)/[^/\s]+/|"
    r"(?:sk|ark)-[a-z0-9_-]{20,}|github_pat_[a-z0-9_]{20,}|bearer\s+[a-z0-9._-]{24,})"
)
FORBIDDEN_KEYS = {"apikey", "authorization", "prompt", "rawresponse", "reasoning"}


def walk_json(value: Any, location: str, failures: list[str]) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = re.sub(r"[^a-z]", "", str(key).lower())
            if normalized in FORBIDDEN_KEYS:
                failures.append(f"{location}: forbidden JSON key {key}")
            walk_json(child, f"{location}.{key}", failures)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            walk_json(child, f"{location}[{index}]", failures)
    elif isinstance(value, str) and SENSITIVE_VALUE.search(value):
        failures.append(f"{location}: sensitive or absolute-path value")


def frozen_artifact_bytes(relative: str, target: Path) -> bytes:
    safe_directory = f"safe.directory={ROOT.as_posix()}"
    try:
        staged = subprocess.run(
            ["git", "-c", safe_directory, "ls-files", "--stage", "--", relative],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        ).stdout.strip()
        if staged:
            object_id = staged.split(maxsplit=3)[1]
            return subprocess.run(
                ["git", "-c", safe_directory, "cat-file", "blob", object_id],
                cwd=ROOT,
                check=True,
                capture_output=True,
            ).stdout
    except (OSError, subprocess.CalledProcessError, IndexError):
        pass
    return target.read_bytes()


def verify_manifest(failures: list[str]) -> None:
    if not MANIFEST_PATH.is_file():
        failures.append("acceptance freeze manifest is missing")
        return
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exception:
        failures.append(f"acceptance freeze manifest is invalid JSON at line {exception.lineno}")
        return
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        failures.append("acceptance freeze manifest has no artifacts")
        return
    evidence_root = EVIDENCE_ROOT.resolve()
    for index, artifact in enumerate(artifacts):
        location = f"acceptance freeze manifest artifacts[{index}]"
        if not isinstance(artifact, dict):
            failures.append(f"{location}: entry must be an object")
            continue
        relative = artifact.get("path")
        expected_hash = artifact.get("sha256")
        expected_length = artifact.get("length")
        if not isinstance(relative, str) or not relative:
            failures.append(f"{location}: path is missing")
            continue
        target = (ROOT / relative).resolve()
        if target == MANIFEST_PATH.resolve() or evidence_root not in target.parents:
            failures.append(f"{location}: path escapes evidence root or points to the manifest")
            continue
        if not target.is_file():
            failures.append(f"{location}: artifact is missing")
            continue
        content = frozen_artifact_bytes(relative, target)
        actual_hash = hashlib.sha256(content).hexdigest().upper()
        if not isinstance(expected_hash, str) or actual_hash != expected_hash.upper():
            failures.append(f"{location}: SHA-256 mismatch")
        if not isinstance(expected_length, int) or len(content) != expected_length:
            failures.append(f"{location}: length mismatch")


def main() -> int:
    failures: list[str] = []
    if not EVIDENCE_ROOT.is_dir():
        failures.append("docs/acceptance-evidence/v3.8.0 is missing")
    for path in sorted(EVIDENCE_ROOT.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".json", ".md", ".txt"}:
            continue
        relative = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        if SENSITIVE_VALUE.search(text):
            failures.append(f"{relative}: sensitive token or machine absolute path")
        if path.suffix.lower() == ".json":
            try:
                walk_json(json.loads(text), relative, failures)
            except json.JSONDecodeError as exception:
                failures.append(f"{relative}: invalid JSON at line {exception.lineno}")
    verify_manifest(failures)
    if failures:
        for failure in failures:
            print(f"V380_ACCEPTANCE_EVIDENCE_FAILED {failure}")
        return 1
    print("V380_ACCEPTANCE_EVIDENCE_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
