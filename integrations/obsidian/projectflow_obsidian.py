#!/usr/bin/env python3
"""Safe, curated ProjectFlow Project Memory projection for Obsidian vaults."""

from __future__ import annotations

import argparse
import copy
import hashlib
import ipaddress
import json
import os
import re
import stat
import sys
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePath
from typing import Any, Callable, Iterable


PROJECTION_VERSION = "2"
MANIFEST_NAME = ".projectflow-manifest.json"
MANIFEST_BACKUP_NAME = ".projectflow-manifest.backup.json"
CONFLICT_NAME = ".projectflow-conflicts.json"
BEGIN_MARKER = "<!-- PROJECTFLOW:BEGIN -->"
END_MARKER = "<!-- PROJECTFLOW:END -->"
PROFILES = {"CORE", "EXTENDED", "FULL_FACTS"}
MAX_PERIODS = 600
MAX_FACTS = 100_000
MAX_CAPABILITIES = 2_000
MAX_EVOLUTIONS = 100_000
MAX_HISTORY_CHAPTERS = 2_000
MAX_HISTORY_STORIES = 20_000
MAX_HISTORY_THREADS = 10_000
MAX_HISTORY_CORRECTIONS = 10_000
MAX_PAGES = 1_000
RESERVED_NAMES = {"CON", "PRN", "AUX", "NUL", *{f"COM{i}" for i in range(1, 10)}, *{f"LPT{i}" for i in range(1, 10)}}
INVALID_FILENAME = re.compile(r"[<>:\"/\\|?*\x00-\x1f]")
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")


class ProjectionError(Exception):
    def __init__(self, code: str, message: str, *, retryable: bool = False):
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = retryable

    def payload(self) -> dict[str, Any]:
        return {"error": {"code": self.code, "message": self.message, "retryable": self.retryable}}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def compact_text(value: Any, limit: int = 500) -> str:
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    return text if len(text) <= limit else text[: max(1, limit - 1)].rstrip() + "…"


def normalize_block(value: str) -> str:
    return value.replace("\r\n", "\n").replace("\r", "\n").strip() + "\n"


def filename(value: str, fallback: str = "未命名") -> str:
    normalized = unicodedata.normalize("NFKC", str(value or ""))
    normalized = INVALID_FILENAME.sub("-", normalized)
    normalized = re.sub(r"\s+", " ", normalized).strip(" .-")
    normalized = re.sub(r"-+", "-", normalized)
    if not normalized:
        normalized = fallback
    if normalized.upper().split(".", 1)[0] in RESERVED_NAMES:
        normalized = "_" + normalized
    normalized = normalized[:80].rstrip(" .")
    return normalized or fallback


def stable_slug(value: str) -> str:
    try:
        return uuid.UUID(str(value)).hex
    except (ValueError, AttributeError):
        return sha256_text(str(value))[:32]


def note_link(path: str, label: str | None = None, anchor: str | None = None) -> str:
    target = path[:-3] if path.lower().endswith(".md") else path
    if anchor:
        target += anchor
    return f"[[{target}{'|' + label if label else ''}]]"


def obsidian_open_uri(vault_name: str, file_path: str) -> str:
    query = urllib.parse.urlencode({"vault": str(vault_name), "file": str(file_path).replace("\\", "/")})
    return "obsidian://open?" + query


def obsidian_advanced_uri(vault_name: str, file_path: str, heading: str = "") -> str:
    values = {"vault": str(vault_name), "filepath": str(file_path).replace("\\", "/")}
    if heading:
        values["heading"] = heading
    return "obsidian://advanced-uri?" + urllib.parse.urlencode(values)


def yaml_value(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    return json.dumps("" if value is None else str(value), ensure_ascii=False)


def parse_scalar(value: str) -> Any:
    value = value.strip()
    if value.lower() == "true":
        return True
    if value.lower() == "false":
        return False
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return value.strip("'\"")


def event_at(fact: dict[str, Any]) -> str:
    times = fact.get("time") or {}
    return str(times.get("eventAt") or times.get("occurredTo") or times.get("occurredFrom") or "")


def event_month(fact: dict[str, Any]) -> str:
    value = event_at(fact)
    return value[:7] if re.match(r"^\d{4}-\d{2}", value) else "unknown"


def status_label(value: Any) -> str:
    labels = {
        "RECORDED": "已记录", "NEEDS_ATTENTION": "需要关注", "ACTIVE": "活跃", "MERGED": "已合并",
        "FORMING": "形成中", "FORMED": "已形成", "CONTINUOUSLY_ENHANCED": "持续增强", "LONG_TERM_STABLE": "长期稳定",
        "NEW_CAPABILITY": "形成能力", "ENHANCE_CAPABILITY": "增强能力", "ADD_EVIDENCE": "补充证据",
        "MERGE_CAPABILITY": "合并能力", "CORRECTION": "修正记录", "FORMED_CAPABILITY": "形成能力",
        "ENHANCED_CAPABILITY": "增强能力", "ADDED_EVIDENCE": "补充证据", "MERGED_CAPABILITY": "合并能力",
        "READY": "已就绪", "FAILED": "生成失败", "WAITING_FOR_MODEL": "等待模型",
        "CREATED": "新增", "MODIFIED": "修改", "REMOVED": "删除", "RESTORED": "恢复",
        "RENAMED": "重命名", "MOVED": "移动", "REPLACED": "替换", "SPLIT": "拆分",
        "MERGED": "合并", "REVERTED": "撤销", "REAPPLIED": "重新实现",
        "UNKNOWN_TRANSITION": "转换未知", "SOURCE_BACKED": "来源支持", "FACTUAL_SOURCE": "强事实来源",
        "ENGINEERING_GROUPING": "工程分组", "INFERRED_NON_AUTHORITATIVE": "非权威归纳",
        "CURRENT": "当前", "STALE": "已过期", "INVALIDATED": "历史重写后失效",
    }
    return labels.get(str(value or ""), compact_text(value, 60) or "未知")


def history_authority_label(value: Any) -> str:
    labels = {
        "ENGINEERING_GROUPING": "按来源自动整理",
        "INFERRED_NON_AUTHORITATIVE": "非权威归纳",
        "SOURCE_BACKED": "来源支持",
        "FACTUAL_SOURCE": "事实来源支持",
        "DECLARED": "用户声明",
    }
    return labels.get(str(value or ""), "整理依据待核对")


def history_summary_label(value: Any) -> str:
    labels = {
        "DETERMINISTIC": "自动整理",
        "MODEL_VALIDATED": "模型表达已校验",
        "MODEL_ENHANCED": "模型辅助整理",
        "PARTIAL": "部分内容待核对",
    }
    return labels.get(str(value or ""), "摘要状态待核对")


def history_presentation_label(value: Any) -> str:
    return "经过用户修改" if str(value or "") == "USER_DECLARED_PRESENTATION" else "自动整理"


def history_role_label(value: Any) -> str:
    return "支撑工作" if str(value or "") == "SUPPORTING" else "主要变化"


def history_coverage_label(value: Any) -> str:
    labels = {
        "FULL_WITHIN_DISCOVERED_SOURCES": "已发现来源内完整",
        "PARTIAL_WITHIN_DISCOVERED_SOURCES": "已发现来源内部分覆盖",
        "PARTIAL": "部分覆盖",
        "UNKNOWN": "覆盖范围未知",
    }
    return labels.get(str(value or ""), "覆盖范围待核对")


def history_subject_type_label(value: Any) -> str:
    labels = {"PROJECT_SUBJECT": "项目对象", "DOCUMENT": "文档对象", "ARTIFACT": "项目成果"}
    return labels.get(str(value or ""), "项目对象")


class GatewayClient:
    def __init__(
        self,
        base_url: str = "http://127.0.0.1:8080",
        token: str = "",
        timeout: float = 20.0,
        app_url: str = "http://127.0.0.1:3000",
    ):
        self.base_url = base_url.strip().rstrip("/")
        self.app_url = app_url.strip().rstrip("/")
        self.token = token.strip()
        self.timeout = max(1.0, min(60.0, float(timeout)))
        parsed = urllib.parse.urlparse(self.base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ProjectionError("PROJECTFLOW_BASE_URL_INVALID", "ProjectFlow backend URL is invalid.")
        if not self._loopback(parsed.hostname):
            raise ProjectionError("PROJECTFLOW_REMOTE_DISABLED", "Obsidian sync accepts only a local loopback ProjectFlow backend in V3.4.5.")

    @staticmethod
    def _loopback(host: str) -> bool:
        if host.lower() == "localhost":
            return True
        try:
            return ipaddress.ip_address(host).is_loopback
        except ValueError:
            return False

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        query = {key: value for key, value in (params or {}).items() if value is not None and value != ""}
        url = self.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(query, doseq=True)
        headers = {"Accept": "application/json", "X-ProjectFlow-Caller": "obsidian-projection"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=self.timeout) as response:
                raw = response.read(2_000_001)
        except urllib.error.HTTPError as error:
            raw = error.read(64_000)
            try:
                body = json.loads(raw.decode("utf-8"))
                detail = body.get("error") or {}
                code = str(detail.get("code") or "PROJECTFLOW_REQUEST_FAILED")
                message = str(detail.get("message") or f"ProjectFlow returned HTTP {error.code}.")
            except (UnicodeDecodeError, json.JSONDecodeError):
                code, message = "PROJECTFLOW_REQUEST_FAILED", f"ProjectFlow returned HTTP {error.code}."
            raise ProjectionError(code, message, retryable=error.code >= 500) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise ProjectionError("PROJECTFLOW_BACKEND_UNAVAILABLE", "ProjectFlow backend is unavailable at the configured local URL.", retryable=True) from None
        if len(raw) > 2_000_000:
            raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "ProjectFlow response exceeded the projection input bound.")
        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise ProjectionError("PROJECTFLOW_RESPONSE_INVALID", "ProjectFlow returned invalid JSON.", retryable=True) from None
        if not isinstance(body, dict) or "data" not in body:
            raise ProjectionError("PROJECTFLOW_RESPONSE_INVALID", "ProjectFlow response did not contain the expected data envelope.", retryable=True)
        return body["data"]

    def collect(self, project_id: str) -> dict[str, Any]:
        base = f"/api/projects/{project_id}/project-memory"
        snapshot = self.get(base + "/snapshot")
        history_overview = self.get(base + "/history/overview")
        current_state = self.get(base + "/history/current-state")
        history_chapters, chapter_revision = self._pages_with_revision(base + "/history/chapters", None, {"size": 100})
        history_stories, story_revision = self._pages_with_revision(
            base + "/history/stories", None, {"size": 100, "includeHidden": "true"}
        )
        history_threads, thread_revision = self._pages_with_revision(base + "/history/threads", None, {"size": 100})
        try:
            history_corrections = self._correction_pages(base + "/history/corrections")
        except ProjectionError as error:
            if error.code in {"PROJECT_HISTORY_CORRECTIONS_NOT_FOUND", "PROJECT_HISTORY_NOT_INITIALIZED", "NOT_FOUND"}:
                history_corrections = {"items": [], "presentationRevision": ""}
            else:
                raise
        revisions = {
            str(value) for value in (
                history_overview.get("presentationRevision"), chapter_revision, story_revision, thread_revision,
                history_corrections.get("presentationRevision"),
            ) if str(value or "").strip()
        }
        if len(revisions) > 1:
            raise ProjectionError(
                "PROJECTFLOW_HISTORY_REVISION_CHANGED",
                "Project History presentation changed while projection inputs were being read; retry the projection.",
                retryable=True,
            )
        if len(history_chapters) > MAX_HISTORY_CHAPTERS:
            raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project History chapters exceeded the projection input bound.")
        if len(history_stories) > MAX_HISTORY_STORIES:
            raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project History stories exceeded the projection input bound.")
        if len(history_threads) > MAX_HISTORY_THREADS:
            raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project History threads exceeded the projection input bound.")
        lifecycle = self.get(base + "/timeline", {"granularity": "LIFECYCLE", "detailLevel": "detailed"})
        period_summaries = self._pages(base + "/timeline", "periods", {"granularity": "MONTH", "detailLevel": "detailed"})
        if len(period_summaries) > MAX_PERIODS:
            raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project timeline exceeded the projection period bound.")
        months: list[dict[str, Any]] = []
        total_facts = 0
        for period in period_summaries:
            key = period.get("periodKey")
            page = 0
            detail: dict[str, Any] | None = None
            facts: list[dict[str, Any]] = []
            while True:
                response = self.get(base + "/timeline", {
                    "granularity": "MONTH", "periodKey": key, "page": page, "size": 100, "detailLevel": "detailed",
                })
                current = response.get("period") or {}
                if detail is None:
                    detail = copy.deepcopy(current)
                fact_page = current.get("facts") or {}
                facts.extend(fact_page.get("items") or [])
                if total_facts + len(facts) > MAX_FACTS:
                    raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project facts exceeded the projection input bound.")
                if not fact_page.get("hasMore"):
                    break
                page += 1
                if page >= MAX_PAGES:
                    raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project fact pagination exceeded the projection bound.")
            detail = detail or {"periodKey": key, "facts": {}}
            detail["facts"] = {"items": facts, "totalElements": len(facts), "hasMore": False}
            months.append(detail)
            total_facts += len(facts)
        capabilities = self._pages(base + "/capabilities", None, {
            "activeOnly": "false", "detailLevel": "detailed", "size": 100,
        })
        if len(capabilities) > MAX_CAPABILITIES:
            raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project capabilities exceeded the projection input bound.")
        evolutions: dict[str, list[dict[str, Any]]] = {}
        total_evolutions = 0
        for capability in capabilities:
            capability_id = str(capability.get("capabilityId"))
            evolutions[capability_id] = self._pages(
                base + f"/capabilities/{capability_id}/evolution", None, {"detailLevel": "detailed", "size": 100}
            )
            total_evolutions += len(evolutions[capability_id])
            if total_evolutions > MAX_EVOLUTIONS:
                raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Capability evolutions exceeded the projection input bound.")
        return {
            "snapshot": snapshot,
            "historyOverview": history_overview,
            "currentState": current_state,
            "historyChapters": history_chapters,
            "historyStories": history_stories,
            "historyThreads": history_threads,
            "historyCorrections": history_corrections,
            "lifecycle": lifecycle,
            "months": months,
            "capabilities": capabilities,
            "evolutions": evolutions,
        }

    def _correction_pages(self, path: str) -> dict[str, Any]:
        items: list[dict[str, Any]] = []
        first: dict[str, Any] | None = None
        revision = ""
        page = 0
        while True:
            if page >= MAX_PAGES:
                raise ProjectionError(
                    "PROJECTFLOW_RESULT_TOO_LARGE",
                    "Project History correction pagination exceeded the projection bound.",
                )
            response = self.get(path, {"page": page, "size": 100})
            current_revision = str(response.get("presentationRevision") or "")
            if first is None:
                first = dict(response)
                revision = current_revision
            elif current_revision != revision:
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_REVISION_CHANGED",
                    "Project History presentation changed while corrections were being read; retry the projection.",
                    retryable=True,
                )
            current = response.get("items") or []
            if not isinstance(current, list):
                raise ProjectionError("PROJECTFLOW_RESPONSE_INVALID", "Project History corrections were not a list.")
            items.extend(current)
            if len(items) > MAX_HISTORY_CORRECTIONS:
                raise ProjectionError(
                    "PROJECTFLOW_RESULT_TOO_LARGE",
                    "Project History corrections exceeded the projection input bound.",
                )
            total = int(response.get("total") or len(items))
            if len(items) >= total:
                result = first or {}
                result.update({"items": items, "presentationRevision": revision, "page": 0,
                               "size": len(items), "total": total, "truncated": False})
                return result
            if not current:
                raise ProjectionError(
                    "PROJECTFLOW_RESPONSE_INVALID",
                    "Project History correction pagination ended before the declared total.",
                    retryable=True,
                )
            page += 1

    def _pages(self, path: str, nested: str | None, params: dict[str, Any]) -> list[dict[str, Any]]:
        return self._pages_with_revision(path, nested, params)[0]

    def _pages_with_revision(
        self, path: str, nested: str | None, params: dict[str, Any]
    ) -> tuple[list[dict[str, Any]], str]:
        items: list[dict[str, Any]] = []
        revisions: set[str] = set()
        page = 0
        while True:
            if page >= MAX_PAGES:
                raise ProjectionError("PROJECTFLOW_RESULT_TOO_LARGE", "Project Memory pagination exceeded the projection bound.")
            request = dict(params)
            request.update({"page": page, "size": request.get("size", 100)})
            response = self.get(path, request)
            current_revision = str(response.get("presentationRevision") or "").strip()
            if current_revision:
                revisions.add(current_revision)
            if len(revisions) > 1:
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_REVISION_CHANGED",
                    "Project History presentation changed while a paged result was being read; retry the projection.",
                    retryable=True,
                )
            page_data = response.get(nested) if nested else response
            page_data = page_data or {}
            items.extend(page_data.get("items") or [])
            has_more = page_data.get("hasMore")
            if has_more is None:
                total_pages = int(page_data.get("totalPages") or 0)
                has_more = page + 1 < total_pages
            if not has_more:
                break
            page += 1
        return items, next(iter(revisions), "")


@dataclass
class ExistingNote:
    path: str
    metadata: dict[str, Any]
    user_frontmatter: list[str]
    before_block: str
    managed_body: str
    after_block: str


@dataclass
class DesiredNote:
    key: str
    entity_type: str
    entity_id: str
    path: str
    source_version: str
    source_updated_at: str
    body: str
    extra_metadata: dict[str, Any]
    redirected: bool = False

    @property
    def managed_hash(self) -> str:
        return sha256_text(normalize_block(self.body))


class AtomicWriter:
    def __init__(self):
        self.writes = 0
        self.bytes_written = 0

    def write(self, target: Path, content: str) -> None:
        if target.exists() and is_link_or_reparse(target):
            raise ProjectionError("OBSIDIAN_SYMLINK_ESCAPE", "A managed target is a symlink or junction.")
        temp = target.with_name(f".{target.name}.projectflow-{uuid.uuid4().hex}.tmp")
        raw = content.encode("utf-8")
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            with open(temp, "xb") as handle:
                handle.write(raw)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp, target)
        except OSError as error:
            raise ProjectionError("OBSIDIAN_WRITE_FAILED", "Atomic managed-file write failed.", retryable=True) from error
        finally:
            if temp.exists():
                try:
                    temp.unlink()
                except OSError:
                    pass
        self.writes += 1
        self.bytes_written += len(raw)


def is_link_or_reparse(path: Path) -> bool:
    try:
        value = path.lstat()
    except FileNotFoundError:
        return False
    if stat.S_ISLNK(value.st_mode):
        return True
    attributes = getattr(value, "st_file_attributes", 0)
    return bool(attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400))


class ObsidianProjection:
    def __init__(
        self,
        source: Any,
        vault: str | Path,
        managed_root: str = "ProjectFlow",
        profile: str = "CORE",
        now: Callable[[], str] = utc_now,
        interrupt_after_notes: int | None = None,
    ):
        self.source = source
        self.vault = Path(vault).absolute()
        self.managed_root_name = managed_root
        self.profile = profile.upper()
        self.now = now
        self.interrupt_after_notes = interrupt_after_notes
        if self.profile not in PROFILES:
            raise ProjectionError("OBSIDIAN_PROFILE_INVALID", "Projection profile must be CORE, EXTENDED, or FULL_FACTS.")
        self.root = self._validate_paths()
        self.advanced_uri_enabled = self._advanced_uri_enabled()
        self.projectflow_app_url = str(
            getattr(source, "app_url", os.getenv("PROJECTFLOW_APP_URL", "http://127.0.0.1:3000"))
        ).strip().rstrip("/")
        parsed_app_url = urllib.parse.urlparse(self.projectflow_app_url)
        if (parsed_app_url.scheme not in {"http", "https"} or not parsed_app_url.hostname
                or parsed_app_url.username is not None or parsed_app_url.password is not None
                or parsed_app_url.query or parsed_app_url.fragment
                or not GatewayClient._loopback(parsed_app_url.hostname)):
            raise ProjectionError(
                "PROJECTFLOW_APP_URL_INVALID",
                "ProjectFlow app URL must be a local loopback HTTP(S) URL without credentials, query, or fragment.",
            )

    def _validate_paths(self) -> Path:
        if not self.vault.exists() or not self.vault.is_dir():
            raise ProjectionError("OBSIDIAN_VAULT_MISSING", "Obsidian vault directory does not exist.")
        if is_link_or_reparse(self.vault):
            raise ProjectionError("OBSIDIAN_SYMLINK_ESCAPE", "Vault root cannot be a symlink or junction.")
        pure = PurePath(self.managed_root_name)
        if pure.is_absolute() or not pure.parts or any(part in {"", ".", ".."} for part in pure.parts):
            raise ProjectionError("OBSIDIAN_MANAGED_ROOT_INVALID", "Managed root must be a safe relative path without traversal.")
        root = self.vault.joinpath(*pure.parts)
        current = self.vault
        for part in pure.parts:
            current = current / part
            if current.exists() and is_link_or_reparse(current):
                raise ProjectionError("OBSIDIAN_SYMLINK_ESCAPE", "Managed root cannot traverse a symlink or junction.")
        try:
            if os.path.commonpath([str(self.vault.resolve()), str(root.resolve(strict=False))]) != str(self.vault.resolve()):
                raise ProjectionError("OBSIDIAN_PATH_ESCAPE", "Managed root escapes the configured vault.")
        except ValueError:
            raise ProjectionError("OBSIDIAN_PATH_ESCAPE", "Managed root escapes the configured vault.") from None
        return root

    def _advanced_uri_enabled(self) -> bool:
        config = self.vault / ".obsidian" / "community-plugins.json"
        plugin = self.vault / ".obsidian" / "plugins" / "obsidian-advanced-uri" / "manifest.json"
        if not config.is_file() or not plugin.is_file() or is_link_or_reparse(config) or is_link_or_reparse(plugin):
            return False
        try:
            enabled = json.loads(config.read_text(encoding="utf-8"))
            return isinstance(enabled, list) and "obsidian-advanced-uri" in enabled
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            return False

    def _obsidian_uri(self, relative: str, *, advanced: bool = False, heading: str = "") -> str:
        file_path = PurePath(self.managed_root_name, relative).as_posix()
        if advanced and self.advanced_uri_enabled:
            return obsidian_advanced_uri(self.vault.name, file_path, heading)
        return obsidian_open_uri(self.vault.name, file_path)

    def _projectflow_history_url(self, project_id: str, entity_type: str, entity_id: str = "") -> str:
        project = urllib.parse.quote(project_id, safe="")
        path = f"/projects/{project}/history"
        kind = {"CHAPTER": "chapter", "STORY": "story", "THREAD": "thread"}.get(entity_type)
        if not kind:
            return self.projectflow_app_url + path
        query = urllib.parse.urlencode({"type": kind, "id": entity_id})
        return self.projectflow_app_url + path + "?" + query

    def validate(self, project_id: str | None = None) -> dict[str, Any]:
        backend = "NOT_CHECKED"
        if project_id:
            data = self.source.collect(project_id)
            actual = str(((data.get("snapshot") or {}).get("project") or {}).get("projectId") or "")
            if actual != project_id:
                raise ProjectionError("PROJECT_SCOPE_MISMATCH", "Gateway returned a different project.")
            backend = "READY"
        parent = self.root if self.root.exists() else next((item for item in [*self.root.parents] if item.exists()), self.vault)
        return {
            "status": "READY" if os.access(parent, os.W_OK) else "READ_ONLY",
            "vault": "VALID",
            "managedRoot": self.managed_root_name.replace("\\", "/"),
            "managedRootExists": self.root.exists(),
            "backend": backend,
            "profile": self.profile,
        }

    def dry_run(self, project_id: str) -> dict[str, Any]:
        prepared = self._prepare(project_id)
        return self._result(prepared, executed=False, writer=AtomicWriter())

    def status(self, project_id: str) -> dict[str, Any]:
        prepared = self._prepare(project_id)
        result = self._result(prepared, executed=False, writer=AtomicWriter())
        result["lastSyncAt"] = prepared["manifest"].get("lastSyncAt")
        result["syncGeneration"] = prepared["manifest"].get("syncGeneration", 0)
        result["manifestRecovered"] = prepared["manifest_recovered"]
        return result

    def sync(self, project_id: str) -> dict[str, Any]:
        self.root.mkdir(parents=True, exist_ok=True)
        self._assert_contained(self.root)
        self._cleanup_temps()
        prepared = self._prepare(project_id)
        writer = AtomicWriter()
        note_writes = 0
        for item in prepared["plan"]:
            if item["action"] not in {"CREATED", "UPDATED", "REDIRECTED"}:
                continue
            note = prepared["desired_by_key"][item["key"]]
            existing = prepared["existing_by_key"].get(item["key"])
            target = self._safe_target(note.path)
            rendered = self._render_note(note, existing, project_id)
            writer.write(target, rendered)
            note_writes += 1
            if self.interrupt_after_notes is not None and note_writes >= self.interrupt_after_notes:
                raise ProjectionError("OBSIDIAN_SYNC_INTERRUPTED", "Injected interruption after an atomic note write.", retryable=True)

        manifest = self._next_manifest(project_id, prepared)
        conflict_state_changed = prepared["conflicts"] != (prepared["manifest"].get("conflicts") or [])
        manifest_changed = (prepared["manifest_recovered"] or prepared["path_reconciled"] or conflict_state_changed
                            or any(item["action"] != "UNCHANGED" for item in prepared["plan"]))
        old_manifest_path = self.root / MANIFEST_NAME
        if manifest_changed:
            if prepared["manifest_valid"] and old_manifest_path.exists():
                writer.write(self.root / MANIFEST_BACKUP_NAME, old_manifest_path.read_text(encoding="utf-8"))
            conflicts = manifest.get("conflicts") or []
            conflict_path = self.root / CONFLICT_NAME
            if conflicts or conflict_path.exists():
                writer.write(self.root / CONFLICT_NAME, json.dumps({"projectId": project_id, "conflicts": conflicts}, ensure_ascii=False, indent=2) + "\n")
            writer.write(old_manifest_path, json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
        prepared["note_writes"] = note_writes
        return self._result(prepared, executed=True, writer=writer)

    def _prepare(self, project_id: str) -> dict[str, Any]:
        if not UUID_RE.match(project_id):
            raise ProjectionError("PROJECT_ID_INVALID", "Project id must be a UUID.")
        data = self.source.collect(project_id)
        snapshot_project = ((data.get("snapshot") or {}).get("project") or {})
        if str(snapshot_project.get("projectId") or "") != project_id:
            raise ProjectionError("PROJECT_SCOPE_MISMATCH", "Gateway returned a different project.")
        manifest, manifest_valid, manifest_recovered = self._load_manifest(project_id)
        discovered, discovery_conflicts = self._discover_notes(project_id)
        desired = self._build_desired(project_id, data, manifest, discovered)
        desired_by_key = {note.key: note for note in desired}
        existing_by_key: dict[str, ExistingNote] = {}
        plan: list[dict[str, Any]] = []
        conflicts = list(discovery_conflicts)
        manifest_files = manifest.get("files") or {}
        for note in sorted(desired, key=lambda value: (value.path.casefold(), value.key)):
            entry = manifest_files.get(note.key) or {}
            target = self._safe_target(note.path)
            existing: ExistingNote | None = None
            if target.exists():
                try:
                    existing = self._read_note(target)
                except ProjectionError as error:
                    conflicts.append(self._conflict(note, note.path, error.code))
                    plan.append({"action": "CONFLICT", "key": note.key, "path": note.path, "reason": error.code})
                    continue
            if existing is not None:
                existing_by_key[note.key] = existing
                identity_error = self._identity_error(existing, project_id, note)
                stored_hash = str(entry.get("managedHash") or existing.metadata.get("content_hash") or "")
                current_hash = sha256_text(normalize_block(existing.managed_body))
                if identity_error or (stored_hash and stored_hash != current_hash):
                    reason = identity_error or "MANAGED_BLOCK_EDITED"
                    conflicts.append(self._conflict(note, note.path, reason))
                    plan.append({"action": "CONFLICT", "key": note.key, "path": note.path, "reason": reason})
                elif (current_hash == note.managed_hash
                      and str(existing.metadata.get("projection_version")) == PROJECTION_VERSION
                      and str(existing.metadata.get("source_version")) == note.source_version
                      and str(existing.metadata.get("obsidian_open_uri") or "") == self._obsidian_uri(note.path)
                      and str(existing.metadata.get("obsidian_advanced_uri") or "")
                          == (self._obsidian_uri(note.path, advanced=True) if self.advanced_uri_enabled else "")):
                    plan.append({"action": "UNCHANGED", "key": note.key, "path": note.path, "reason": "hash-match"})
                else:
                    action = "REDIRECTED" if note.redirected else "UPDATED"
                    plan.append({"action": action, "key": note.key, "path": note.path, "reason": "source-changed"})
            else:
                plan.append({"action": "CREATED", "key": note.key, "path": note.path, "reason": "missing"})
        for key, entry in sorted(manifest_files.items()):
            if key not in desired_by_key:
                already_archived = str((entry or {}).get("status") or "") == "ARCHIVED"
                plan.append({
                    "action": "UNCHANGED" if already_archived else "ARCHIVED", "key": key,
                    "path": str(entry.get("path") or ""), "reason": "already-archived" if already_archived else "source-absent",
                })
        path_reconciled = any(
            key in desired_by_key and str((entry or {}).get("path") or "") not in {"", desired_by_key[key].path}
            for key, entry in manifest_files.items()
        )
        return {
            "project_id": project_id, "data": data, "manifest": manifest, "manifest_valid": manifest_valid,
            "manifest_recovered": manifest_recovered, "desired": desired, "desired_by_key": desired_by_key,
            "existing_by_key": existing_by_key, "plan": plan, "conflicts": conflicts, "path_reconciled": path_reconciled,
        }

    def _load_manifest(self, project_id: str) -> tuple[dict[str, Any], bool, bool]:
        path = self.root / MANIFEST_NAME
        if not path.exists():
            return {"projectId": project_id, "files": {}, "conflicts": [], "syncGeneration": 0}, False, False
        if is_link_or_reparse(path):
            raise ProjectionError("OBSIDIAN_SYMLINK_ESCAPE", "Manifest cannot be a symlink or junction.")
        try:
            manifest = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(manifest, dict) or not isinstance(manifest.get("files"), dict):
                raise ValueError
            if manifest.get("projectId") not in {None, project_id}:
                raise ProjectionError("OBSIDIAN_PROJECT_CONFLICT", "Managed root belongs to a different ProjectFlow project.")
            return manifest, True, False
        except ProjectionError:
            raise
        except (OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError):
            return {"projectId": project_id, "files": {}, "conflicts": [], "syncGeneration": 0}, False, True

    def _discover_notes(self, project_id: str) -> tuple[dict[str, ExistingNote], list[dict[str, Any]]]:
        found: dict[str, ExistingNote] = {}
        conflicts: list[dict[str, Any]] = []
        if not self.root.exists():
            return found, conflicts
        for path in sorted(self.root.rglob("*.md"), key=lambda value: str(value).casefold()):
            if is_link_or_reparse(path):
                raise ProjectionError("OBSIDIAN_SYMLINK_ESCAPE", "Managed root contains a symlink or junction.")
            self._assert_contained(path)
            try:
                note = self._read_note(path)
            except ProjectionError:
                continue
            if note.metadata.get("projectflow_managed") is not True or str(note.metadata.get("projectflow_project_id")) != project_id:
                continue
            entity_type = str(note.metadata.get("entity_type") or "")
            entity_id = str(note.metadata.get("entity_id") or "")
            if not entity_type or not entity_id:
                continue
            key = f"{entity_type}:{entity_id}"
            relative = path.relative_to(self.root).as_posix()
            note.path = relative
            if key in found:
                conflicts.append({"key": key, "path": relative, "reason": "DUPLICATE_ENTITY_NOTE"})
            else:
                found[key] = note
        return found, conflicts

    def _path_for(self, key: str, default: str, manifest: dict[str, Any], discovered: dict[str, ExistingNote]) -> str:
        entry = (manifest.get("files") or {}).get(key) or {}
        candidate = str(entry.get("path") or "")
        if candidate and self._relative_safe(candidate) and self._safe_target(candidate).exists():
            return candidate
        if key in discovered:
            return discovered[key].path
        return default

    def _build_desired(
        self, project_id: str, data: dict[str, Any], manifest: dict[str, Any], discovered: dict[str, ExistingNote]
    ) -> list[DesiredNote]:
        snapshot = data.get("snapshot") or {}
        lifecycle_query = data.get("lifecycle") or {}
        lifecycle = lifecycle_query.get("lifecycle") or {}
        history_overview = data.get("historyOverview") or snapshot.get("projectHistory") or {}
        current_state = data.get("currentState") or {
            "stateRevision": "",
            "historyStatus": history_overview.get("status"),
            "currentness": (history_overview.get("coverage") or {}).get("currentness"),
            "confirmedState": (history_overview.get("overview") or {}).get("currentState"),
            "conflicts": (history_overview.get("overview") or {}).get("conflicts") or [],
            "unknowns": (history_overview.get("overview") or {}).get("unknowns") or [],
            "limitations": (history_overview.get("coverage") or {}).get("limitations") or [],
        }
        history_chapters = sorted(data.get("historyChapters") or [], key=lambda value: (str(value.get("from") or ""), str(value.get("id") or "")))
        history_stories = sorted(data.get("historyStories") or [], key=lambda value: (str(value.get("occurredFrom") or ""), str(value.get("id") or "")))
        history_threads = sorted(data.get("historyThreads") or [], key=lambda value: (str(value.get("subjectLabel") or ""), str(value.get("id") or "")))
        history_corrections = data.get("historyCorrections") or {}
        if isinstance(history_corrections, dict):
            history_overview = dict(history_overview)
            history_overview["presentationRevision"] = history_corrections.get("presentationRevision", "")
            history_overview["corrections"] = history_corrections.get("items") or []
        self._validate_history_projection(history_chapters, history_stories, history_threads)
        history_story_by_id = {str(story.get("id")): story for story in history_stories}
        small_history = len(history_stories) <= 60 and len(history_threads) <= 40

        def primary_story(story: dict[str, Any]) -> bool:
            return str(story.get("role") or "PRIMARY").upper() != "SUPPORTING"

        def important_supporting(story: dict[str, Any]) -> bool:
            return primary_story(story) or bool(
                story.get("pinned") or story.get("conflicts") or story.get("unknowns")
                or story.get("correctionConflicts") or story.get("presentationAuthority") == "USER_DECLARED_PRESENTATION"
            )

        if self.profile == "CORE" and not small_history:
            history_story_notes = [story for story in history_stories if important_supporting(story)]
            selected_story_ids = {str(story.get("id")) for story in history_story_notes}
            history_thread_notes = [
                thread for thread in history_threads
                if any(str(ref) in selected_story_ids for ref in thread.get("storyRefs") or [])
            ][:120]
        elif self.profile == "EXTENDED":
            history_story_notes = [story for story in history_stories if important_supporting(story)]
            selected_story_ids = {str(story.get("id")) for story in history_story_notes}
            history_thread_notes = [
                thread for thread in history_threads
                if any(str(ref) in selected_story_ids for ref in thread.get("storyRefs") or [])
            ]
        else:
            history_story_notes = history_stories
            selected_story_ids = {str(story.get("id")) for story in history_story_notes}
            history_thread_notes = history_threads
        visible_history_story_notes = [story for story in history_story_notes if not story.get("hiddenByDefault")]
        visible_story_ids = {str(story.get("id")) for story in visible_history_story_notes}
        months = sorted(data.get("months") or [], key=lambda value: str(value.get("periodKey") or ""))
        capabilities = data.get("capabilities") or []
        evolutions = data.get("evolutions") or {}
        all_facts = [fact for month in months for fact in ((month.get("facts") or {}).get("items") or [])]
        fact_by_id = {str(fact.get("factId")): fact for fact in all_facts}

        paths: dict[str, str] = {}
        paths["overview"] = self._path_for(f"PROJECT_OVERVIEW:{project_id}", "项目概览.md", manifest, discovered)
        paths["index:HISTORY_INDEX"] = self._path_for(
            f"HISTORY_INDEX:{project_id}", "项目历程/索引.md", manifest, discovered
        )
        for chapter in history_chapters:
            chapter_id = str(chapter.get("id") or "")
            default = f"项目历程/篇章/{filename(chapter.get('title'), '时间篇章')}--{stable_slug(chapter_id)}.md"
            paths[f"history-chapter:{chapter_id}"] = self._path_for(f"HISTORY_CHAPTER:{chapter_id}", default, manifest, discovered)
        for story in history_stories:
            story_id = str(story.get("id") or "")
            date_prefix = str(story.get("occurredFrom") or "")[:10]
            default = f"项目历程/变化故事/{filename(date_prefix, '时间')}-{filename(story.get('humanTitle'), '变化故事')}--{stable_slug(story_id)}.md"
            paths[f"history-story:{story_id}"] = self._path_for(f"HISTORY_STORY:{story_id}", default, manifest, discovered)
        for thread in history_threads:
            thread_id = str(thread.get("id") or "")
            default = f"项目历程/演变链/{filename(thread.get('subjectLabel'), '演变链')}--{stable_slug(thread_id)}.md"
            paths[f"history-thread:{thread_id}"] = self._path_for(f"HISTORY_THREAD:{thread_id}", default, manifest, discovered)
        for month in months:
            key = str(month.get("periodKey") or "unknown")
            paths[f"timeline:{key}"] = self._path_for(f"TIMELINE_MONTH:{project_id}:{key}", f"项目历程/{filename(key)}.md", manifest, discovered)
            paths[f"facts:{key}"] = self._path_for(f"FACT_INDEX_MONTH:{project_id}:{key}", f"项目事实/{filename(key)}.md", manifest, discovered)
        for capability in capabilities:
            cap_id = str(capability.get("capabilityId"))
            default = f"项目能力/{filename(capability.get('canonicalName'), '能力')}--{stable_slug(cap_id)}.md"
            paths[f"capability:{cap_id}"] = self._path_for(f"CAPABILITY:{cap_id}", default, manifest, discovered)
        index_defaults = {
            "CAPABILITY_INDEX": "索引/能力索引.md", "TIMELINE_INDEX": "索引/时间索引.md", "FACT_INDEX": "索引/事实索引.md",
        }
        for entity, default in index_defaults.items():
            paths[f"index:{entity}"] = self._path_for(f"{entity}:{project_id}", default, manifest, discovered)

        notes: list[DesiredNote] = []
        note_time = str(
            history_overview.get("updatedAt") or history_overview.get("latestEventAt")
            or (snapshot.get("health") or {}).get("latestRealChangeAt") or snapshot.get("latestFactAt") or ""
        )
        overview_body = self._overview_body(
            project_id, snapshot, history_overview, current_state, history_chapters, visible_history_story_notes, history_thread_notes,
            lifecycle, months, capabilities, evolutions, paths
        )
        notes.append(self._note(
            f"PROJECT_OVERVIEW:{project_id}", "PROJECT_OVERVIEW", project_id, paths["overview"],
            f"{history_overview.get('projectRevision', '')}:{current_state.get('stateRevision', '')}:"
            f"{history_overview.get('sourceEventCount', 0)}:"
            f"{snapshot.get('factCount', 0)}:{snapshot.get('activeCapabilityCount', 0)}",
            note_time, overview_body, {
                "projectflow_detail_url": self._projectflow_history_url(project_id, "OVERVIEW"),
            },
        ))

        notes.append(self._note(
            f"HISTORY_INDEX:{project_id}", "HISTORY_INDEX", project_id, paths["index:HISTORY_INDEX"],
            f"{history_overview.get('projectRevision', '')}:{current_state.get('stateRevision', '')}:"
            f"{len(history_chapters)}:{len(history_stories)}:{len(history_threads)}",
            note_time,
            self._history_index_body(
                project_id, history_overview, current_state, history_chapters, visible_history_story_notes,
                history_thread_notes, paths
            ),
            {"projectflow_detail_url": self._projectflow_history_url(project_id, "OVERVIEW")},
        ))
        for chapter in history_chapters:
            chapter_id = str(chapter.get("id") or "")
            chapter_stories = [history_story_by_id[ref] for ref in map(str, chapter.get("storyRefs") or [])
                               if ref in visible_story_ids and ref in history_story_by_id]
            notes.append(self._note(
                f"HISTORY_CHAPTER:{chapter_id}", "HISTORY_CHAPTER", chapter_id, paths[f"history-chapter:{chapter_id}"],
                sha256_text(json.dumps(chapter, ensure_ascii=False, sort_keys=True, separators=(",", ":"))),
                str(chapter.get("to") or chapter.get("from") or note_time),
                self._history_chapter_body(project_id, chapter, chapter_stories, paths),
                {
                    "history_chapter_id": chapter_id,
                    "occurred_from": chapter.get("from") or "",
                    "occurred_to": chapter.get("to") or "",
                    "projectflow_detail_url": self._projectflow_history_url(project_id, "CHAPTER", chapter_id),
                },
            ))
        for story in history_story_notes:
            story_id = str(story.get("id") or "")
            notes.append(self._note(
                f"HISTORY_STORY:{story_id}", "HISTORY_STORY", story_id, paths[f"history-story:{story_id}"],
                sha256_text(json.dumps(story, ensure_ascii=False, sort_keys=True, separators=(",", ":"))),
                str(story.get("occurredTo") or story.get("occurredFrom") or note_time),
                self._history_story_body(project_id, story, history_thread_notes, paths),
                {
                    "history_story_id": story_id,
                    "occurred_from": story.get("occurredFrom") or "",
                    "occurred_to": story.get("occurredTo") or "",
                    "projectflow_detail_url": self._projectflow_history_url(project_id, "STORY", story_id),
                },
            ))
        for thread in history_thread_notes:
            thread_id = str(thread.get("id") or "")
            thread_stories = [history_story_by_id[ref] for ref in map(str, thread.get("storyRefs") or [])
                              if ref in visible_story_ids and ref in history_story_by_id]
            notes.append(self._note(
                f"HISTORY_THREAD:{thread_id}", "HISTORY_THREAD", thread_id, paths[f"history-thread:{thread_id}"],
                sha256_text(json.dumps(thread, ensure_ascii=False, sort_keys=True, separators=(",", ":"))),
                str(thread_stories[-1].get("occurredTo") if thread_stories else note_time),
                self._history_thread_body(project_id, thread, thread_stories, paths),
                {
                    "history_thread_id": thread_id,
                    "projectflow_detail_url": self._projectflow_history_url(project_id, "THREAD", thread_id),
                },
            ))

        evolution_by_month: dict[str, list[dict[str, Any]]] = {}
        for values in evolutions.values():
            for evolution in values:
                for period in evolution.get("sourcePeriods") or []:
                    if re.match(r"^\d{4}-\d{2}$", str(period)):
                        evolution_by_month.setdefault(str(period), []).append(evolution)
        for month in months:
            period = str(month.get("periodKey") or "unknown")
            summary = month.get("summary") or {}
            version = f"{summary.get('generationVersion', 0)}:{month.get('sourceFactCount', 0)}:{month.get('coveredFactCount', 0)}"
            updated = str(summary.get("generatedAt") or month.get("periodEnd") or "")
            extra = {
                "period_key": period, "period_start": month.get("periodStart") or "", "period_end": month.get("periodEnd") or "",
                "timeline_zone": lifecycle_query.get("timelineZone") or "UTC",
            }
            notes.append(self._note(
                f"TIMELINE_MONTH:{project_id}:{period}", "TIMELINE_MONTH", f"{project_id}:{period}", paths[f"timeline:{period}"],
                version, updated, self._timeline_body(month, evolution_by_month.get(period, []), paths), extra,
            ))
            facts = (month.get("facts") or {}).get("items") or []
            latest = max((event_at(fact) for fact in facts), default="")
            notes.append(self._note(
                f"FACT_INDEX_MONTH:{project_id}:{period}", "FACT_INDEX_MONTH", f"{project_id}:{period}", paths[f"facts:{period}"],
                f"{len(facts)}:{latest}", latest, self._fact_index_body(period, facts, capabilities, paths), {"period_key": period},
            ))

        for capability in capabilities:
            cap_id = str(capability.get("capabilityId"))
            cap_evolutions = evolutions.get(cap_id) or []
            merged = str(capability.get("status") or "") == "MERGED" or bool(capability.get("mergedIntoCapabilityId"))
            target_id = str(capability.get("mergedIntoCapabilityId") or "")
            extra = {
                "capability_id": cap_id, "capability_status": capability.get("status") or "",
                "capability_version": capability.get("currentVersion") or 0,
            }
            if target_id:
                extra["redirect_target"] = target_id
            notes.append(self._note(
                f"CAPABILITY:{cap_id}", "CAPABILITY", cap_id, paths[f"capability:{cap_id}"],
                str(capability.get("currentVersion") or 0), str(capability.get("sourceUpdatedAt") or capability.get("lastEnhancedAt") or ""),
                self._capability_body(capability, cap_evolutions, fact_by_id, paths, target_id), extra, redirected=merged,
            ))

        notes.extend([
            self._note(f"CAPABILITY_INDEX:{project_id}", "CAPABILITY_INDEX", project_id, paths["index:CAPABILITY_INDEX"],
                       str(len(capabilities)), note_time, self._capability_index_body(capabilities, paths), {}),
            self._note(f"TIMELINE_INDEX:{project_id}", "TIMELINE_INDEX", project_id, paths["index:TIMELINE_INDEX"],
                       str(len(months)), note_time, self._timeline_index_body(months, paths), {}),
            self._note(f"FACT_INDEX:{project_id}", "FACT_INDEX", project_id, paths["index:FACT_INDEX"],
                       str(len(all_facts)), note_time, self._fact_global_index_body(months, paths), {}),
        ])

        if self.profile != "CORE":
            important = {str(fact_id) for values in evolutions.values() for evo in values for fact_id in (evo.get("sourceFactIds") or [])}
            for fact in all_facts:
                fact_id = str(fact.get("factId"))
                if self.profile == "EXTENDED" and fact_id not in important and fact.get("recordStatus") != "NEEDS_ATTENTION":
                    continue
                month = event_month(fact)
                default = f"重要事实/{filename(month)}/{filename(fact.get('title'), '事实')}--{stable_slug(fact_id)}.md"
                path = self._path_for(f"FACT:{fact_id}", default, manifest, discovered)
                notes.append(self._note(
                    f"FACT:{fact_id}", "FACT", fact_id, path, str((fact.get("time") or {}).get("recordedAt") or event_at(fact)),
                    str((fact.get("time") or {}).get("recordedAt") or event_at(fact)), self._fact_body(fact, capabilities, paths), {},
                ))
        return notes

    @staticmethod
    def _validate_history_projection(
        chapters: list[dict[str, Any]], stories: list[dict[str, Any]], threads: list[dict[str, Any]]
    ) -> None:
        story_by_id: dict[str, dict[str, Any]] = {}
        for story in stories:
            story_id = str(story.get("id") or "")
            if not story_id or story_id in story_by_id:
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History contains a missing or duplicate Story identity.",
                    retryable=True,
                )
            story_by_id[story_id] = story

        chapter_membership: set[str] = set()
        for chapter in chapters:
            refs = [str(ref) for ref in chapter.get("storyRefs") or []]
            if len(refs) != len(set(refs)) or any(ref not in story_by_id for ref in refs):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History Chapter membership contains a duplicate or unknown Story.",
                    retryable=True,
                )
            if chapter_membership.intersection(refs):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History assigns one Story to more than one Chapter.",
                    retryable=True,
                )
            chapter_membership.update(refs)
            declared_count = chapter.get("storyCount")
            if declared_count is not None and int(declared_count) != len(refs):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History Chapter count does not match its Story membership.",
                    retryable=True,
                )

        for thread in threads:
            refs = [str(ref) for ref in thread.get("storyRefs") or []]
            if len(refs) != len(set(refs)) or any(ref not in story_by_id for ref in refs):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History evolution chain contains a duplicate or unknown Story.",
                    retryable=True,
                )

        for story_id, story in story_by_id.items():
            role = str(story.get("role") or "PRIMARY").upper()
            primary_id = str(story.get("primaryStoryId") or "")
            supporting_refs = [str(ref) for ref in story.get("supportingChangeRefs") or []]
            merged_into = str(story.get("mergedIntoStoryId") or "")
            if merged_into and (merged_into == story_id or merged_into not in story_by_id):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History merge target is missing or self-referential.",
                    retryable=True,
                )
            if str(story.get("displayStatus") or "").upper() == "MERGED":
                continue
            if role == "SUPPORTING":
                primary = story_by_id.get(primary_id)
                if supporting_refs or primary is None or str(primary.get("role") or "PRIMARY").upper() == "SUPPORTING" \
                        or story_id not in [str(ref) for ref in primary.get("supportingChangeRefs") or []]:
                    raise ProjectionError(
                        "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                        "Project History Supporting relation is not bidirectional.",
                        retryable=True,
                    )
            elif primary_id or len(supporting_refs) != len(set(supporting_refs)):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History Primary relation contains an invalid reference.",
                    retryable=True,
                )
            elif any(
                ref not in story_by_id
                or str(story_by_id[ref].get("role") or "PRIMARY").upper() != "SUPPORTING"
                or str(story_by_id[ref].get("primaryStoryId") or "") != story_id
                for ref in supporting_refs
            ):
                raise ProjectionError(
                    "PROJECTFLOW_HISTORY_PROJECTION_INCONSISTENT",
                    "Project History Primary relation is not bidirectional.",
                    retryable=True,
                )

    @staticmethod
    def _note(
        key: str, entity_type: str, entity_id: str, path: str, source_version: str, source_updated_at: str,
        body: str, extra: dict[str, Any], redirected: bool = False,
    ) -> DesiredNote:
        return DesiredNote(key, entity_type, entity_id, path, source_version, source_updated_at, normalize_block(body), extra, redirected)

    def _overview_body(
        self,
        project_id: str,
        snapshot: dict[str, Any],
        history_overview: dict[str, Any],
        current_state: dict[str, Any],
        history_chapters: list[dict[str, Any]],
        history_stories: list[dict[str, Any]],
        history_threads: list[dict[str, Any]],
        lifecycle: dict[str, Any],
        months: list[dict[str, Any]],
        capabilities: list[dict[str, Any]],
        evolutions: dict[str, list[dict[str, Any]]],
        paths: dict[str, str],
    ) -> str:
        project = snapshot.get("project") or {}
        history = history_overview.get("overview") or {}
        coverage = history_overview.get("coverage") or {}
        warnings = list((snapshot.get("health") or {}).get("warnings") or [])
        warnings.extend(current_state.get("conflicts") or history.get("conflicts") or [])
        warnings.extend(current_state.get("unknowns") or history.get("unknowns") or [])
        warnings.extend(current_state.get("limitations") or [])
        corrections = history_overview.get("corrections") or []
        story_by_id = {str(story.get("id")): story for story in history_stories}
        lines = [
            f"# {compact_text(project.get('name'), 120) or '项目概览'}",
            "",
            compact_text(project.get("summary"), 800) or "当前没有项目定位摘要。",
            "",
            "## 项目历程",
            "",
            compact_text(current_state.get("confirmedState") or history.get("currentState"), 1200)
            or "尚未生成项目历程；普通 Markdown 与既有事实仍可阅读。",
            "",
            f"- 历程状态：{status_label(history_overview.get('status'))}",
            f"- 来源事件：{history_overview.get('sourceEventCount', 0)}",
            f"- 可确认范围：{history_overview.get('earliestEventAt') or '未知'} → {history_overview.get('latestEventAt') or '未知'}",
            f"- 当前性：{status_label(current_state.get('currentness') or coverage.get('currentness'))}",
            f"- 完整性：{'完整' if coverage.get('complete') else '存在明确缺口'}",
            f"- 展示修正：{len(corrections)} 条（只覆盖阅读层，不改变事实）",
            "",
            "### 最早可确认状态",
            "",
            compact_text(history.get("earliestConfirmedState"), 1200) or "未知。",
            "",
            "### 最近发生",
            "",
        ]
        recent_ids = [str(story.get("id")) for story in sorted(
            history_stories,
            key=lambda value: (bool(value.get("pinned")), str(value.get("occurredTo") or "")),
            reverse=True,
        )[:5]]
        for story_id in recent_ids:
            story = story_by_id[story_id]
            lines.append(
                f"- {str(story.get('occurredTo') or story.get('occurredFrom') or '')[:10]} "
                f"{note_link(paths[f'history-story:{story_id}'], compact_text(story.get('humanTitle'), 150))}"
            )
        if not recent_ids:
            lines.append("- 当前没有可下钻的变化故事。")
        lines += ["", "### 时间篇章", ""]
        for chapter in history_chapters[:8]:
            chapter_id = str(chapter.get("id") or "")
            lines.append(
                f"- {note_link(paths[f'history-chapter:{chapter_id}'], compact_text(chapter.get('title'), 150))}"
                f" · {chapter.get('storyCount', 0)} 个故事 · {chapter.get('rawEventCount', 0)} 个原始事件"
            )
        if not history_chapters:
            lines.append("- 尚无时间篇章。")
        if corrections:
            lines += ["", "### 用户展示声明", ""]
            for correction in corrections[:8]:
                lines.append(
                    f"- {status_label(correction.get('status'))}："
                    f"{compact_text(correction.get('difference'), 260) or '展示层有用户声明'}"
                )
        lines += ["", "## 当前事实与可选能力", ""]
        lines += [
            f"- 项目事实：{snapshot.get('factCount', 0)}（已记录 {snapshot.get('recordedFactCount', 0)}，需要关注 {snapshot.get('attentionFactCount', 0)}）",
            f"- Git 历史覆盖：{snapshot.get('coveredCommitCount', 0)} / {snapshot.get('totalCommitCount', 0)}",
            f"- 可选长期能力：{snapshot.get('activeCapabilityCount', 0)}",
        ]
        if capabilities:
            for capability in capabilities:
                cap_id = str(capability.get("capabilityId"))
                if capability.get("status") == "MERGED":
                    continue
                lines.append(
                    f"- {note_link(paths[f'capability:{cap_id}'], compact_text(capability.get('canonicalName'), 100))}："
                    f"{compact_text(capability.get('summary'), 180)}"
                )
        else:
            lines.append("- 当前项目没有适用的 Capability 视图；这不影响项目历程成立。")
        lines += [
            "",
            "## 双向跳转",
            "",
            f"- [在 ProjectFlow 中打开只读历程]({self._projectflow_history_url(project_id, 'OVERVIEW')})",
            f"- 当前 Note 官方 Obsidian URI：{self._obsidian_uri(paths['overview'])}",
            "",
            "## 导航",
            "",
            f"- {note_link(paths['index:HISTORY_INDEX'], '项目历程索引')}",
        ]
        if months:
            latest = str(months[-1].get("periodKey"))
            lines.append(f"- 兼容月度 Timeline：{note_link(paths[f'timeline:{latest}'], latest)}")
        lines += [
            f"- {note_link(paths['index:TIMELINE_INDEX'], '兼容时间索引')}",
            f"- {note_link(paths['index:FACT_INDEX'], '事实索引')}",
            f"- {note_link(paths['index:CAPABILITY_INDEX'], '可选能力索引')}",
        ]
        if snapshot.get("attentionFactCount", 0):
            warnings.append(f"有 {snapshot.get('attentionFactCount')} 条事实需要关注。")
        if warnings:
            lines += ["", "## 需要关注", "", *[f"- {compact_text(item, 240)}" for item in dict.fromkeys(warnings)]]
        return "\n".join(lines)

    def _history_index_body(
        self,
        project_id: str,
        history_overview: dict[str, Any],
        current_state: dict[str, Any],
        chapters: list[dict[str, Any]],
        stories: list[dict[str, Any]],
        threads: list[dict[str, Any]],
        paths: dict[str, str],
    ) -> str:
        coverage = history_overview.get("coverage") or {}
        lines = [
            "# 项目历程索引",
            "",
            "第一层先回答项目发生了什么；SHA、文件和 Evidence ID 只在下钻层出现。",
            "",
            f"- 时间篇章：{len(chapters)}",
            f"- 变化故事：{len(stories)}",
            f"- 演变链：{len(threads)}",
            f"- 原始事件：{history_overview.get('sourceEventCount', 0)}",
            f"- 当前性：{status_label(current_state.get('currentness') or coverage.get('currentness'))}",
            f"- 当前确认状态：{compact_text(current_state.get('confirmedState'), 300) or '未知'}",
            "",
            "## 时间篇章",
            "",
        ]
        for chapter in chapters:
            chapter_id = str(chapter.get("id") or "")
            lines.append(
                f"- {note_link(paths[f'history-chapter:{chapter_id}'], compact_text(chapter.get('title'), 160))}"
                f" · {chapter.get('storyCount', 0)} 个故事"
            )
        if not chapters:
            lines.append("- 尚无篇章。")
        lines += ["", "## 演变链", ""]
        for thread in threads:
            thread_id = str(thread.get("id") or "")
            lines.append(
                f"- {note_link(paths[f'history-thread:{thread_id}'], compact_text(thread.get('subjectLabel'), 140))}"
                f" · {len(thread.get('storyRefs') or [])} 个故事"
            )
        if not threads:
            lines.append("- 尚无演变链。")
        lines += ["", "## 最近变化故事", ""]
        for story in sorted(stories, key=lambda value: str(value.get("occurredTo") or ""), reverse=True)[:20]:
            story_id = str(story.get("id") or "")
            lines.append(
                f"- {str(story.get('occurredTo') or story.get('occurredFrom') or '')[:10]} "
                f"{note_link(paths[f'history-story:{story_id}'], compact_text(story.get('humanTitle'), 150))}"
            )
        if not stories:
            lines.append("- 尚无变化故事。")
        lines += [
            "",
            f"返回：{note_link(paths['overview'], '项目概览')}",
            f"ProjectFlow：[{self._projectflow_history_url(project_id, 'OVERVIEW')}]({self._projectflow_history_url(project_id, 'OVERVIEW')})",
        ]
        return "\n".join(lines)

    def _history_chapter_body(
        self, project_id: str, chapter: dict[str, Any], stories: list[dict[str, Any]], paths: dict[str, str]
    ) -> str:
        chapter_id = str(chapter.get("id") or "")
        lines = [
            f"# {compact_text(chapter.get('title'), 180) or '时间篇章'}",
            "",
            compact_text(chapter.get("summary"), 1500) or "当前没有篇章摘要。",
            "",
            f"- 时间范围：{chapter.get('from') or '未知'} → {chapter.get('to') or '未知'}",
            f"- 变化故事：{chapter.get('storyCount', len(stories))}",
            f"- 原始事件：{chapter.get('rawEventCount', 0)}",
            f"- 整理依据：{history_authority_label(chapter.get('authority'))}",
            f"- 覆盖：{history_coverage_label(chapter.get('coverage'))}",
            "",
            "## 变化故事",
            "",
        ]
        for story in stories:
            story_id = str(story.get("id") or "")
            lines.append(
                f"- {str(story.get('occurredFrom') or '')[:10]} "
                f"{note_link(paths[f'history-story:{story_id}'], compact_text(story.get('humanTitle'), 160))}"
            )
        if not stories:
            lines.append("- 当前篇章没有可读取的故事成员，覆盖可能不完整。")
        limitations = chapter.get("limitations") or []
        if limitations:
            lines += ["", "## 限制", "", *[f"- {compact_text(item, 300)}" for item in limitations]]
        lines += [
            "",
            f"ProjectFlow：[{self._projectflow_history_url(project_id, 'CHAPTER', chapter_id)}]({self._projectflow_history_url(project_id, 'CHAPTER', chapter_id)})",
            f"返回：{note_link(paths['index:HISTORY_INDEX'], '项目历程索引')} · {note_link(paths['overview'], '项目概览')}",
        ]
        return "\n".join(lines)

    def _history_story_body(
        self,
        project_id: str,
        story: dict[str, Any],
        threads: list[dict[str, Any]],
        paths: dict[str, str],
    ) -> str:
        story_id = str(story.get("id") or "")
        related_threads = [thread for thread in threads if story_id in map(str, thread.get("storyRefs") or [])]
        lines = [
            f"# {compact_text(story.get('humanTitle'), 180) or '变化故事'}",
            "",
            compact_text(story.get("oneSentenceSummary"), 1200) or "当前没有变化摘要。",
            "",
            f"- 发生时间：{story.get('occurredFrom') or '未知'} → {story.get('occurredTo') or '未知'}",
            f"- 整理依据：{history_authority_label(story.get('authority'))}",
            f"- 摘要状态：{history_summary_label(story.get('summaryStatus'))}",
            f"- 展示来源：{history_presentation_label(story.get('presentationAuthority'))} · 阅读位置：{history_role_label(story.get('role'))}",
            f"- 默认展示：{'否' if story.get('hiddenByDefault') else '是'} · 置顶：{'是' if story.get('pinned') else '否'}",
            f"- 覆盖：{history_coverage_label(story.get('coverage'))}",
            "",
            "## 原来状态",
            "",
            compact_text(story.get("beforeState"), 1500) or "未知。",
            "",
            "## 本次变化",
            "",
            compact_text(story.get("change"), 1800) or "未知。",
            "",
            "## 当前结果",
            "",
            compact_text(story.get("afterState"), 1500) or "未知。",
            "",
            "## 原因与后续",
            "",
            f"- 原因：{compact_text(story.get('reason'), 1000) or '未知（没有合法原因 Evidence）'}",
            f"- 后续结果：{compact_text(story.get('laterOutcome'), 1000) or '尚无后续来源'}",
            f"- 影响区域：{'、'.join(compact_text(item, 120) for item in story.get('affectedAreas') or []) or '未单独归类'}",
        ]
        if related_threads:
            lines += ["", "## 所属演变链", ""]
            for thread in related_threads:
                thread_id = str(thread.get("id") or "")
                lines.append(note_link(paths[f"history-thread:{thread_id}"], compact_text(thread.get("subjectLabel"), 140)))
        conflicts = story.get("conflicts") or []
        correction_conflicts = story.get("correctionConflicts") or []
        unknowns = story.get("unknowns") or []
        limitations = story.get("limitations") or []
        if conflicts or correction_conflicts or unknowns or limitations:
            lines += ["", "## 冲突、未知与限制", ""]
            lines += [f"- 冲突：{compact_text(item, 400)}" for item in conflicts]
            lines += [f"- 展示修正冲突：{compact_text(item, 400)}" for item in correction_conflicts]
            lines += [f"- 未知：{compact_text(item, 400)}" for item in unknowns]
            lines += [f"- 限制：{compact_text(item, 400)}" for item in limitations]
        technical = story.get("technicalDetails") or []
        atoms = story.get("technicalAtomRefs") or []
        commits = story.get("commitSummaries") or []
        if technical or atoms or commits:
            lines += ["", "## 工程下钻", ""]
            if atoms:
                lines.append(f"- Technical Atom：{', '.join(compact_text(item, 120) for item in atoms[:20])}")
            lines += [f"- {compact_text(item, 600)}" for item in technical[:12]]
            lines += [f"- Commit 摘要：{compact_text(item, 600)}" for item in commits[:12]]
        evidence = story.get("evidenceRefs") or []
        lines += ["", "## 证据下钻", ""]
        lines += [f"- `{compact_text(item, 300)}`" for item in evidence[:50]] or ["- 当前没有可投影的 Evidence 引用。"]
        lines += [
            "",
            f"ProjectFlow：[{self._projectflow_history_url(project_id, 'STORY', story_id)}]({self._projectflow_history_url(project_id, 'STORY', story_id)})",
            f"返回：{note_link(paths['index:HISTORY_INDEX'], '项目历程索引')} · {note_link(paths['overview'], '项目概览')}",
        ]
        return "\n".join(lines)

    def _history_thread_body(
        self, project_id: str, thread: dict[str, Any], stories: list[dict[str, Any]], paths: dict[str, str]
    ) -> str:
        thread_id = str(thread.get("id") or "")
        transitions = [status_label(item) for item in thread.get("transitions") or []]
        lines = [
            f"# {compact_text(thread.get('subjectLabel'), 180) or '项目演变链'}",
            "",
            f"当前结果：{compact_text(thread.get('currentOutcome'), 1200) or 'UNKNOWN'}",
            "",
            f"- 对象类型：{history_subject_type_label(thread.get('subjectType'))}",
            f"- 转换序列：{' → '.join(transitions) if transitions else '未知'}",
            f"- Evidence 数：{thread.get('evidenceCount', 0)}",
            "",
            "## 变化故事",
            "",
        ]
        for story in stories:
            story_id = str(story.get("id") or "")
            lines.append(
                f"- {str(story.get('occurredFrom') or '')[:10]} "
                f"{note_link(paths[f'history-story:{story_id}'], compact_text(story.get('humanTitle'), 160))}"
            )
        if not stories:
            lines.append("- 当前演变链没有可读取的故事成员。")
        if thread.get("gaps") or thread.get("conflicts") or thread.get("unknowns"):
            lines += ["", "## 缺口与未知", ""]
            lines += [f"- 缺口：{compact_text(item, 400)}" for item in thread.get("gaps") or []]
            lines += [f"- 冲突：{compact_text(item, 400)}" for item in thread.get("conflicts") or []]
            lines += [f"- 未知：{compact_text(item, 400)}" for item in thread.get("unknowns") or []]
        lines += [
            "",
            f"ProjectFlow：[{self._projectflow_history_url(project_id, 'THREAD', thread_id)}]({self._projectflow_history_url(project_id, 'THREAD', thread_id)})",
            f"返回：{note_link(paths['index:HISTORY_INDEX'], '项目历程索引')} · {note_link(paths['overview'], '项目概览')}",
        ]
        return "\n".join(lines)

    def _timeline_body(self, month: dict[str, Any], evolutions: list[dict[str, Any]], paths: dict[str, str]) -> str:
        period = str(month.get("periodKey") or "unknown")
        stats = month.get("stats") or {}
        summary = month.get("summary") or {}
        facts = (month.get("facts") or {}).get("items") or []
        themes = month.get("themes") or []
        lines = [
            f"# {period} 项目历程", "", "## 月度概览", "",
            compact_text(summary.get("summary"), 1600) or compact_text(summary.get("notice"), 500) or "本月事实可读，尚无自动摘要。",
            "", "## 确定性统计", "",
            f"- 事实：{stats.get('factCount', len(facts))}", f"- 提交：{stats.get('commitCount', 0)}", f"- 变更文件：{stats.get('fileCount', 0)}",
            f"- 需要关注：{stats.get('attentionCount', 0)}", "", "## 时间线主题", "",
        ]
        lines += [f"- {compact_text(theme.get('title'), 140)}：{compact_text(theme.get('summary'), 240)}（{theme.get('factCount', 0)} 条事实）" for theme in themes] or ["- 本月没有单独的派生主题。"]
        lines += ["", "## 主要事实", ""]
        for fact in facts[:30]:
            lines.append(f"- {event_at(fact)[:10]} {compact_text(fact.get('title'), 150)} · {note_link(paths[f'facts:{period}'], '事实', '#^fact-' + stable_slug(str(fact.get('factId'))))}")
        if len(facts) > 30:
            lines.append(f"- 其余 {len(facts) - 30} 条见 {note_link(paths[f'facts:{period}'], '本月事实索引')}。")
        lines += ["", "## 能力变化", ""]
        for evolution in sorted(evolutions, key=lambda value: str(value.get("occurredAt") or "")):
            cap_id = str(evolution.get("capabilityId"))
            lines.append(f"- {str(evolution.get('occurredAt') or '')[:10]} {note_link(paths.get(f'capability:{cap_id}', paths['index:CAPABILITY_INDEX']), compact_text(evolution.get('title'), 140))}")
        if not evolutions:
            lines.append("- 本月没有已记录的长期能力演进。")
        history = month.get("history") or {}
        if history.get("status") and history.get("status") != "COMPLETED":
            lines += ["", "> 历史补齐尚未完成；本页只展示 ProjectFlow 当前已覆盖的真实事实。"]
        lines += ["", f"返回：{note_link(paths['overview'], '项目概览')} · {note_link(paths['index:HISTORY_INDEX'], '项目历程索引')} · {note_link(paths['index:TIMELINE_INDEX'], '兼容时间索引')}"]
        return "\n".join(lines)

    def _fact_index_body(self, period: str, facts: list[dict[str, Any]], capabilities: list[dict[str, Any]], paths: dict[str, str]) -> str:
        cap_names = {str(cap.get("capabilityId")): compact_text(cap.get("canonicalName"), 100) for cap in capabilities}
        lines = [f"# {period} 项目事实", "", f"共 {len(facts)} 条，按真实发生时间排列。", ""]
        for fact in sorted(facts, key=event_at):
            fact_id = str(fact.get("factId"))
            caps = [note_link(paths[f"capability:{cap_id}"], cap_names.get(cap_id, "相关能力")) for cap_id in map(str, fact.get("relatedCapabilityIds") or []) if f"capability:{cap_id}" in paths]
            lines += [
                f"## {event_at(fact)[:10]} {compact_text(fact.get('title'), 150)}", "",
                compact_text(fact.get("summary"), 500) or "（无额外摘要）", "",
                f"- 状态：{status_label(fact.get('recordStatus'))}", f"- 稳定 Fact ID：`{fact_id}`",
                f"- 来源批次：`{fact.get('batchId') or '未知'}`", f"- 相关能力：{'、'.join(caps) if caps else '暂无长期能力关联'}",
                f"- 证据追溯：ProjectFlow Fact Trace `{fact_id}`", f"^fact-{stable_slug(fact_id)}", "",
            ]
        lines.append(f"返回：{note_link(paths['index:HISTORY_INDEX'], '项目历程索引')} · {note_link(paths[f'timeline:{period}'], '本月 Timeline')} · {note_link(paths['index:FACT_INDEX'], '事实索引')}")
        return "\n".join(lines)

    def _capability_body(
        self, capability: dict[str, Any], evolutions: list[dict[str, Any]], fact_by_id: dict[str, dict[str, Any]],
        paths: dict[str, str], target_id: str,
    ) -> str:
        cap_id = str(capability.get("capabilityId"))
        lines = [f"# {compact_text(capability.get('canonicalName'), 150) or '项目能力'}", ""]
        if target_id:
            target = paths.get(f"capability:{target_id}", paths["index:CAPABILITY_INDEX"])
            lines += [f"> 此能力已合并到 {note_link(target, '目标能力')}。旧 Note 与历史保留，避免双链断裂。", ""]
        aliases = [compact_text(item, 100) for item in capability.get("aliases") or []]
        lines += [
            "## 当前状态", "", compact_text(capability.get("summary"), 1000) or "当前没有能力摘要。", "",
            f"- 稳定能力 ID：`{cap_id}`", f"- 别名：{'、'.join(aliases) if aliases else '无'}",
            f"- 解决的问题：{compact_text(capability.get('problemSolved'), 500) or '未单独描述'}",
            f"- 长期价值：{compact_text(capability.get('longTermValue'), 500) or '未单独描述'}",
            f"- 成熟度：{status_label(capability.get('maturity'))}", f"- 成熟度依据：{compact_text(capability.get('maturityReason'), 500)}",
            f"- 首次形成：{str(capability.get('firstFormedAt') or '')[:10] or '未知'}", f"- 最近增强：{str(capability.get('lastEnhancedAt') or '')[:10] or '未知'}",
            f"- 当前版本：{capability.get('currentVersion', 0)}", f"- 事实 / 演进：{capability.get('factCount', 0)} / {capability.get('evolutionCount', len(evolutions))}",
            "", "## 演进历程", "",
        ]
        for evolution in sorted(evolutions, key=lambda value: (str(value.get("occurredAt") or ""), int(value.get("versionAfter") or 0))):
            periods = [note_link(paths[f"timeline:{period}"], period) for period in map(str, evolution.get("sourcePeriods") or []) if f"timeline:{period}" in paths]
            fact_links = []
            for fact_id in map(str, evolution.get("sourceFactIds") or []):
                fact = fact_by_id.get(fact_id) or {}
                month = event_month(fact) if fact else ""
                if f"facts:{month}" in paths:
                    fact_links.append(note_link(paths[f"facts:{month}"], compact_text(fact.get("title"), 80) or "事实", "#^fact-" + stable_slug(fact_id)))
            lines += [
                f"### v{evolution.get('versionAfter', 0)} · {str(evolution.get('occurredAt') or '')[:10]} · {status_label(evolution.get('type'))}", "",
                compact_text(evolution.get("summary"), 700) or compact_text(evolution.get("title"), 200), "",
                f"- 相关月份：{'、'.join(periods) if periods else '未单独归属'}",
                f"- 来源事实：{'、'.join(fact_links) if fact_links else str(evolution.get('sourceFactCount', 0)) + ' 条'}", "",
            ]
        if not evolutions:
            lines.append("- 尚无独立演进记录。")
        representative_ids = list(dict.fromkeys(
            str(fact_id) for evolution in evolutions for fact_id in (evolution.get("sourceFactIds") or [])
        ))
        if representative_ids:
            lines += ["", "## 代表事实与证据追溯", ""]
            for fact_id in representative_ids[:20]:
                fact = fact_by_id.get(fact_id) or {}
                month = event_month(fact) if fact else ""
                if f"facts:{month}" in paths:
                    link = note_link(paths[f"facts:{month}"], compact_text(fact.get("title"), 120) or "项目事实", "#^fact-" + stable_slug(fact_id))
                    lines.append(f"- {link} · Fact Trace `{fact_id}`")
                else:
                    lines.append(f"- Fact Trace `{fact_id}`")
        expressions = [("README", capability.get("readmeExpression")), ("简历", capability.get("resumeExpression")), ("面试", capability.get("interviewExpression"))]
        valuable = [(label, compact_text(value, 800)) for label, value in expressions if compact_text(value, 800)]
        if valuable:
            lines += ["", "## 可复用表达", ""]
            lines += [f"- {label}：{value}" for label, value in valuable]
        lines += ["", f"返回：{note_link(paths['overview'], '项目概览')} · {note_link(paths['index:HISTORY_INDEX'], '项目历程索引')} · {note_link(paths['index:CAPABILITY_INDEX'], '能力索引')}"]
        return "\n".join(lines)

    @staticmethod
    def _capability_index_body(capabilities: list[dict[str, Any]], paths: dict[str, str]) -> str:
        lines = ["# 能力索引", ""]
        for capability in capabilities:
            cap_id = str(capability.get("capabilityId"))
            lines.append(f"- {note_link(paths[f'capability:{cap_id}'], compact_text(capability.get('canonicalName'), 120))} · {status_label(capability.get('status'))} · {status_label(capability.get('maturity'))}")
        return "\n".join(lines)

    @staticmethod
    def _timeline_index_body(months: list[dict[str, Any]], paths: dict[str, str]) -> str:
        lines = ["# 时间索引", ""]
        for month in reversed(months):
            period = str(month.get("periodKey"))
            lines.append(f"- {note_link(paths[f'timeline:{period}'], period)} · {month.get('sourceFactCount', 0)} 条事实")
        return "\n".join(lines)

    @staticmethod
    def _fact_global_index_body(months: list[dict[str, Any]], paths: dict[str, str]) -> str:
        lines = ["# 事实索引", "", "默认按月汇总，避免一条事实生成一个文件。", ""]
        for month in reversed(months):
            period = str(month.get("periodKey"))
            count = len((month.get("facts") or {}).get("items") or [])
            lines.append(f"- {note_link(paths[f'facts:{period}'], period)} · {count} 条事实")
        return "\n".join(lines)

    def _fact_body(self, fact: dict[str, Any], capabilities: list[dict[str, Any]], paths: dict[str, str]) -> str:
        fact_id = str(fact.get("factId"))
        month = event_month(fact)
        cap_names = {str(cap.get("capabilityId")): cap.get("canonicalName") for cap in capabilities}
        caps = [note_link(paths[f"capability:{cap_id}"], compact_text(cap_names.get(cap_id), 100)) for cap_id in map(str, fact.get("relatedCapabilityIds") or []) if f"capability:{cap_id}" in paths]
        return "\n".join([
            f"# {compact_text(fact.get('title'), 160)}", "", compact_text(fact.get("summary"), 1200), "",
            f"- 真实发生时间：{event_at(fact)}", f"- 状态：{status_label(fact.get('recordStatus'))}", f"- Fact ID：`{fact_id}`",
            f"- 来源批次：`{fact.get('batchId') or '未知'}`", f"- 相关能力：{'、'.join(caps) if caps else '暂无'}",
            f"- 月度索引：{note_link(paths.get(f'facts:{month}', paths['index:FACT_INDEX']), month)}", "- 证据详情：请通过 ProjectFlow Fact Trace 按 ID 读取。",
        ])

    def _read_note(self, path: Path) -> ExistingNote:
        self._assert_contained(path)
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            raise ProjectionError("OBSIDIAN_NOTE_UNREADABLE", "Managed note cannot be read as UTF-8.") from None
        if not text.startswith("---\n") and not text.startswith("---\r\n"):
            raise ProjectionError("OBSIDIAN_FRONTMATTER_INVALID", "Managed note frontmatter is missing.")
        normalized = text.replace("\r\n", "\n")
        end_frontmatter = normalized.find("\n---\n", 4)
        if end_frontmatter < 0:
            raise ProjectionError("OBSIDIAN_FRONTMATTER_INVALID", "Managed note frontmatter is damaged.")
        frontmatter_text = normalized[4:end_frontmatter]
        remainder = normalized[end_frontmatter + 5:]
        begin = remainder.find(BEGIN_MARKER)
        end = remainder.find(END_MARKER)
        if begin < 0 or end < 0 or end < begin or remainder.find(BEGIN_MARKER, begin + 1) >= 0 or remainder.find(END_MARKER, end + 1) >= 0:
            raise ProjectionError("OBSIDIAN_MANAGED_MARKERS_INVALID", "Managed block markers are missing or damaged.")
        before = remainder[:begin]
        body = remainder[begin + len(BEGIN_MARKER):end]
        after = remainder[end + len(END_MARKER):]
        metadata: dict[str, Any] = {}
        user_lines: list[str] = []
        managed_keys = self._managed_metadata_keys()
        for line in frontmatter_text.split("\n"):
            match = re.match(r"^([A-Za-z0-9_]+):\s*(.*)$", line)
            if match:
                key, raw = match.groups()
                metadata[key] = parse_scalar(raw)
                if key not in managed_keys:
                    user_lines.append(line)
            else:
                user_lines.append(line)
        return ExistingNote(path.relative_to(self.root).as_posix(), metadata, user_lines, before, normalize_block(body), after)

    @staticmethod
    def _managed_metadata_keys() -> set[str]:
        return {
            "projectflow_managed", "projectflow_project_id", "entity_type", "entity_id", "source_version", "content_hash",
            "generated_at", "source_updated_at", "projection_version", "period_key", "period_start", "period_end", "timeline_zone",
            "capability_id", "capability_status", "capability_version", "redirect_target",
            "history_chapter_id", "history_story_id", "history_thread_id", "occurred_from", "occurred_to",
            "projectflow_detail_url", "obsidian_open_uri", "obsidian_advanced_uri",
        }

    def _render_note(self, note: DesiredNote, existing: ExistingNote | None, project_id: str) -> str:
        metadata = {
            "projectflow_managed": True, "projectflow_project_id": project_id, "entity_type": note.entity_type,
            "entity_id": note.entity_id, "source_version": note.source_version, "content_hash": note.managed_hash,
            "generated_at": self.now(), "source_updated_at": note.source_updated_at, "projection_version": PROJECTION_VERSION,
            "obsidian_open_uri": self._obsidian_uri(note.path),
            "obsidian_advanced_uri": self._obsidian_uri(note.path, advanced=True) if self.advanced_uri_enabled else "",
            **note.extra_metadata,
        }
        frontmatter = ["---", *[f"{key}: {yaml_value(value)}" for key, value in metadata.items()]]
        if existing:
            frontmatter.extend(line for line in existing.user_frontmatter if line.strip())
            before = existing.before_block
            after = existing.after_block
        else:
            before = ""
            after = "\n\n# 我的笔记\n\n"
        return "\n".join(frontmatter) + "\n---\n" + before + BEGIN_MARKER + "\n" + normalize_block(note.body) + END_MARKER + after

    @staticmethod
    def _identity_error(existing: ExistingNote, project_id: str, note: DesiredNote) -> str:
        meta = existing.metadata
        if meta.get("projectflow_managed") is not True:
            return "NOT_PROJECTFLOW_MANAGED"
        if str(meta.get("projectflow_project_id") or "") != project_id:
            return "PROJECT_ID_MISMATCH"
        if str(meta.get("entity_type") or "") != note.entity_type or str(meta.get("entity_id") or "") != note.entity_id:
            return "ENTITY_ID_MISMATCH"
        return ""

    @staticmethod
    def _conflict(note: DesiredNote, path: str, reason: str) -> dict[str, Any]:
        return {"key": note.key, "entityType": note.entity_type, "entityId": note.entity_id, "path": path, "reason": reason}

    def _next_manifest(self, project_id: str, prepared: dict[str, Any]) -> dict[str, Any]:
        previous = prepared["manifest"]
        files: dict[str, Any] = {}
        plan_by_key = {item["key"]: item for item in prepared["plan"]}
        for note in prepared["desired"]:
            action = plan_by_key[note.key]["action"]
            if action == "CONFLICT":
                old = (previous.get("files") or {}).get(note.key)
                if old:
                    files[note.key] = {**old, "status": "CONFLICT"}
                continue
            files[note.key] = {
                "path": note.path, "entityType": note.entity_type, "entityId": note.entity_id,
                "sourceVersion": note.source_version, "managedHash": note.managed_hash,
                "projectionVersion": PROJECTION_VERSION, "status": "REDIRECT" if note.redirected else "ACTIVE",
                "redirectTarget": note.extra_metadata.get("redirect_target", ""),
            }
        for item in prepared["plan"]:
            if item["key"] not in prepared["desired_by_key"] and item["action"] in {"ARCHIVED", "UNCHANGED"}:
                old = (previous.get("files") or {}).get(item["key"]) or {}
                files[item["key"]] = {**old, "status": "ARCHIVED"}
        return {
            "projectionVersion": PROJECTION_VERSION, "projectId": project_id, "profile": self.profile,
            "syncGeneration": int(previous.get("syncGeneration") or 0) + 1, "lastSyncAt": self.now(),
            "files": files, "redirects": {key: value.get("redirectTarget") for key, value in files.items() if value.get("redirectTarget")},
            "conflicts": prepared["conflicts"], "lastPlan": self._counts(prepared["plan"]),
        }

    def _result(self, prepared: dict[str, Any], executed: bool, writer: AtomicWriter) -> dict[str, Any]:
        links: dict[str, Any] = {}
        for label, key in (("overview", f"PROJECT_OVERVIEW:{prepared['project_id']}"), ("history", f"HISTORY_INDEX:{prepared['project_id']}")):
            note = prepared["desired_by_key"].get(key)
            if note is None:
                continue
            official = self._obsidian_uri(note.path)
            advanced = self._obsidian_uri(note.path, advanced=True) if self.advanced_uri_enabled else ""
            links[label] = {
                "preferred": advanced or official,
                "officialFallback": official,
                "advanced": advanced,
            }
        return {
            "status": "COMPLETED_WITH_CONFLICTS" if prepared["conflicts"] else "COMPLETED",
            "executed": executed, "projectId": prepared["project_id"], "profile": self.profile,
            "managedRoot": self.managed_root_name.replace("\\", "/"), "plan": self._counts(prepared["plan"]),
            "items": prepared["plan"], "conflicts": prepared["conflicts"], "noteWrites": prepared.get("note_writes", 0),
            "totalWrites": writer.writes, "bytesWritten": writer.bytes_written, "manifestRecovered": prepared["manifest_recovered"],
            "deepLinks": links,
            "integrationLevels": {
                "level0MarkdownAndOfficialUri": True,
                "level1AdvancedUri": self.advanced_uri_enabled,
                "level2LocalRestOrMcp": False,
                "level3DataviewOrBasesRequired": False,
            },
        }

    @staticmethod
    def _counts(plan: Iterable[dict[str, Any]]) -> dict[str, int]:
        counts = {key: 0 for key in ["CREATED", "UPDATED", "UNCHANGED", "REDIRECTED", "ARCHIVED", "CONFLICT", "ERROR"]}
        for item in plan:
            counts[item["action"]] = counts.get(item["action"], 0) + 1
        return counts

    def _safe_target(self, relative: str) -> Path:
        if not self._relative_safe(relative):
            raise ProjectionError("OBSIDIAN_PATH_ESCAPE", "Projection path is invalid or escapes the managed root.")
        target = self.root.joinpath(*PurePath(relative).parts)
        current = self.root
        for part in PurePath(relative).parts[:-1]:
            current = current / part
            if current.exists() and is_link_or_reparse(current):
                raise ProjectionError("OBSIDIAN_SYMLINK_ESCAPE", "Projection path traverses a symlink or junction.")
        self._assert_contained(target)
        return target

    @staticmethod
    def _relative_safe(relative: str) -> bool:
        pure = PurePath(relative)
        return bool(relative) and not pure.is_absolute() and all(part not in {"", ".", ".."} for part in pure.parts)

    def _assert_contained(self, path: Path) -> None:
        try:
            if os.path.commonpath([str(self.root.resolve(strict=False)), str(path.resolve(strict=False))]) != str(self.root.resolve(strict=False)):
                raise ProjectionError("OBSIDIAN_PATH_ESCAPE", "Projection path escapes the managed root.")
        except ValueError:
            raise ProjectionError("OBSIDIAN_PATH_ESCAPE", "Projection path escapes the managed root.") from None

    def _cleanup_temps(self) -> None:
        if not self.root.exists():
            return
        for path in self.root.rglob("*.tmp"):
            if ".projectflow-" not in path.name:
                continue
            self._assert_contained(path)
            if not is_link_or_reparse(path):
                try:
                    path.unlink()
                except OSError:
                    pass


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Project ProjectFlow memory into an Obsidian managed root.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ["validate", "dry-run", "sync", "status"]:
        item = subparsers.add_parser(command)
        item.add_argument("--vault", required=True, help="Existing Obsidian vault directory.")
        item.add_argument("--managed-root", default="ProjectFlow", help="Safe relative dedicated managed folder.")
        item.add_argument("--project-id", required=command != "validate", help="ProjectFlow project UUID.")
        item.add_argument("--profile", choices=sorted(PROFILES), default="CORE")
        item.add_argument("--base-url", default=os.getenv("PROJECTFLOW_BASE_URL", "http://127.0.0.1:8080"))
        item.add_argument("--app-url", default=os.getenv("PROJECTFLOW_APP_URL", "http://127.0.0.1:3000"))
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        client = GatewayClient(
            args.base_url,
            os.getenv("PROJECTFLOW_ACCESS_TOKEN", ""),
            float(os.getenv("PROJECTFLOW_OBSIDIAN_TIMEOUT_SECONDS", "20")),
            args.app_url,
        )
        projection = ObsidianProjection(client, args.vault, args.managed_root, args.profile)
        if args.command == "validate":
            result = projection.validate(args.project_id)
        elif args.command == "dry-run":
            result = projection.dry_run(args.project_id)
        elif args.command == "status":
            result = projection.status(args.project_id)
        else:
            result = projection.sync(args.project_id)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result.get("status") not in {"READ_ONLY"} else 2
    except ProjectionError as error:
        print(json.dumps(error.payload(), ensure_ascii=False), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
