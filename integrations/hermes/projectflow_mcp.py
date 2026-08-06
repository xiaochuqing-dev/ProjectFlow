#!/usr/bin/env python3
"""ProjectFlow read-only MCP adapter for local stdio hosts such as Hermes."""

from __future__ import annotations

import ipaddress
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


SERVER_NAME = "projectflow-project-memory"
SERVER_VERSION = "3.8.5"
PROTOCOL_VERSIONS = {"2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"}
DEFAULT_PROTOCOL_VERSION = "2025-11-25"


class AdapterError(Exception):
    def __init__(self, code: str, message: str, *, retryable: bool = False, status: int | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = retryable
        self.status = status

    def payload(self) -> dict[str, Any]:
        return {
            "error": {
                "code": self.code,
                "message": self.message,
                "retryable": self.retryable,
                **({"httpStatus": self.status} if self.status is not None else {}),
            }
        }


@dataclass(frozen=True)
class Settings:
    base_url: str
    access_token: str
    timeout_seconds: float
    max_result_bytes: int

    @classmethod
    def load(cls) -> "Settings":
        base_url = os.getenv("PROJECTFLOW_BASE_URL", "http://127.0.0.1:8080").strip().rstrip("/")
        parsed = urllib.parse.urlparse(base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise AdapterError("PROJECTFLOW_BASE_URL_INVALID", "ProjectFlow backend URL is invalid.")
        if not _is_loopback(parsed.hostname):
            raise AdapterError(
                "PROJECTFLOW_REMOTE_DISABLED",
                "Remote ProjectFlow MCP access is not implemented in this release; use a localhost backend.",
            )
        timeout = _bounded_float(os.getenv("PROJECTFLOW_MCP_TIMEOUT_SECONDS", "15"), 1.0, 60.0, 15.0)
        maximum = _bounded_int(os.getenv("PROJECTFLOW_MCP_MAX_RESULT_BYTES", "200000"), 16_384, 524_288, 200_000)
        return cls(base_url, os.getenv("PROJECTFLOW_ACCESS_TOKEN", "").strip(), timeout, maximum)


def _is_loopback(hostname: str) -> bool:
    if hostname.lower() == "localhost":
        return True
    try:
        return ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        return False


def _bounded_int(value: str, minimum: int, maximum: int, fallback: int) -> int:
    try:
        return max(minimum, min(maximum, int(value)))
    except (TypeError, ValueError):
        return fallback


def _bounded_float(value: str, minimum: float, maximum: float, fallback: float) -> float:
    try:
        return max(minimum, min(maximum, float(value)))
    except (TypeError, ValueError):
        return fallback


def _schema(properties: dict[str, Any], required: list[str] | None = None) -> dict[str, Any]:
    result: dict[str, Any] = {"type": "object", "properties": properties, "additionalProperties": False}
    if required:
        result["required"] = required
    return result


PROJECT_ID = {"type": "string", "format": "uuid", "description": "ProjectFlow project UUID returned by list_projects."}
DETAIL = {"type": "string", "enum": ["compact", "detailed"], "default": "compact"}
PAGE = {"type": "integer", "minimum": 0, "default": 0}
SIZE = {"type": "integer", "minimum": 1, "maximum": 100, "default": 10}
TIME = {"type": "string", "format": "date-time", "description": "Inclusive real occurrence-time boundary in ISO-8601."}
EVENT_ID = {"type": "string", "format": "uuid", "description": "Project History event UUID returned by list_project_history_events."}


def _tool(name: str, description: str, input_schema: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": name,
        "description": description,
        "inputSchema": input_schema,
        "annotations": {
            "readOnlyHint": True,
            "destructiveHint": False,
            "idempotentHint": True,
            "openWorldHint": False,
        },
    }


TOOLS = [
    _tool(
        "list_projects",
        "List ProjectFlow projects visible to the current local user. Use this first when the project UUID is unknown. Read-only and bounded.",
        _schema({}),
    ),
    _tool(
        "get_project_snapshot",
        "Get a compact current project snapshot led by what happened in Project History, then fact coverage, optional capabilities, freshness, and warnings. Use for overview questions.",
        _schema({"projectId": PROJECT_ID}, ["projectId"]),
    ),
    _tool(
        "get_project_history_overview",
        "Read the persisted Project History overview: earliest confirmed state, current state, recent changes, dynamic chapter summaries, conflicts, unknowns, coverage, and currentness. GET never scans Git or calls a model.",
        _schema({"projectId": PROJECT_ID}, ["projectId"]),
    ),
    _tool(
        "list_project_history_chapters",
        "List paged dynamic Project History chapters with real time ranges, readable summaries, story counts, raw-event counts, authority, coverage, and limitations. Chapters are derived groupings rather than milestones.",
        _schema({"projectId": PROJECT_ID, "page": PAGE, "size": SIZE}, ["projectId"]),
    ),
    _tool(
        "list_project_history_corrections",
        "Read the durable user presentation declarations for Project History, including automatic and applied values, differences, conflicts, target presence, and the current presentation revision. Read-only; declarations never mutate facts or raw events.",
        _schema({"projectId": PROJECT_ID, "page": PAGE, "size": SIZE}, ["projectId"]),
    ),
    _tool(
        "list_project_change_stories",
        "List paged evidence-bound change stories with before, change, after, later outcome, conflicts, unknowns, authority, and raw-event references. Supports subject, time, and attention filters.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "subject": {"type": "string", "maxLength": 200},
                "attentionOnly": {"type": "boolean", "default": False},
                "from": TIME,
                "until": TIME,
                "page": PAGE,
                "size": SIZE,
            },
            ["projectId"],
        ),
    ),
    _tool(
        "list_project_evolution_threads",
        "List paged evolution threads that connect repeated creation, modification, removal, restoration, replacement, split, merge, revert, and reapply transitions for a stable project subject.",
        _schema(
            {"projectId": PROJECT_ID, "subject": {"type": "string", "maxLength": 200}, "page": PAGE, "size": SIZE},
            ["projectId"],
        ),
    ),
    _tool(
        "list_project_history_events",
        "List paged normalized raw history events without dropping stale or invalidated records. Filter by source, category, transition, authority, epistemic status, rewrite state, subject, attention, or occurrence time.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "sourceType": {"type": "string", "enum": ["GIT", "GITHUB", "FILESYSTEM", "PROJECT_FACT", "AGENT_RESULT", "DOCUMENT", "USER", "EXTERNAL"]},
                "category": {"type": "string", "enum": ["COMMIT", "MERGE", "PULL_REQUEST", "ISSUE", "TAG", "FILE_CHANGE", "DOCUMENT_VERSION", "AGENT_RESULT", "VALIDATION", "USER_DECLARATION", "PROJECT_FACT", "EXTERNAL"]},
                "transition": {"type": "string", "enum": ["CREATED", "MODIFIED", "REMOVED", "RESTORED", "RENAMED", "MOVED", "REPLACED", "SPLIT", "MERGED", "REVERTED", "REAPPLIED", "UNKNOWN_TRANSITION"]},
                "authority": {"type": "string", "enum": ["SOURCE_BACKED", "FACTUAL_SOURCE", "DECLARED", "PROCESS_EVIDENCE", "INFERRED_NON_AUTHORITATIVE", "UNKNOWN"]},
                "epistemicStatus": {"type": "string", "enum": ["OBSERVED", "VERIFIED", "DECLARED", "INFERRED", "CONFLICTED", "UNKNOWN", "PROCESS_EVIDENCE"]},
                "rewriteState": {"type": "string", "enum": ["CURRENT", "STALE", "INVALIDATED"]},
                "subject": {"type": "string", "maxLength": 200},
                "attentionOnly": {"type": "boolean", "default": False},
                "from": TIME,
                "until": TIME,
                "page": PAGE,
                "size": SIZE,
            },
            ["projectId"],
        ),
    ),
    _tool(
        "get_project_history_evidence",
        "Read the bounded evidence drill-down for one Project History event, preserving currentness, revision, validation, coverage, limitations, and safe deep links without exposing private paths or raw payloads.",
        _schema({"projectId": PROJECT_ID, "eventId": EVENT_ID}, ["projectId", "eventId"]),
    ),
    _tool(
        "search_project_memory",
        "Search facts, timeline periods/themes, long-lived capabilities, and capability evolutions while keeping each semantic layer explicit. Use for why/history/design questions; results explain matched fields.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "query": {"type": "string", "minLength": 1, "maxLength": 500},
                "from": TIME,
                "until": TIME,
                "entityTypes": {
                    "type": "array",
                    "items": {"type": "string", "enum": ["FACT", "TIMELINE", "CAPABILITY", "EVOLUTION"]},
                    "uniqueItems": True,
                },
                "page": PAGE,
                "size": SIZE,
                "detailLevel": DETAIL,
            },
            ["projectId", "query"],
        ),
    ),
    _tool(
        "get_recent_changes",
        "Read recent Project Facts ordered and filtered by when changes actually occurred, never by analysis, recording, or sync time. Supports attention inclusion and pagination.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "from": TIME,
                "until": TIME,
                "includeAttention": {"type": "boolean", "default": True},
                "page": PAGE,
                "size": SIZE,
                "detailLevel": DETAIL,
            },
            ["projectId"],
        ),
    ),
    _tool(
        "get_project_timeline",
        "Read DAY, ISO WEEK, MONTH, or LIFECYCLE project history with deterministic statistics, fact coverage, themes, and summaries. Failed summaries still return facts and statistics.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "granularity": {"type": "string", "enum": ["DAY", "WEEK", "MONTH", "LIFECYCLE"], "default": "MONTH"},
                "periodKey": {"type": "string", "description": "YYYY-MM-DD, YYYY-Www, or YYYY-MM. Omit to list periods."},
                "from": {"type": "string", "description": "Optional period-key lower bound when listing periods."},
                "until": {"type": "string", "description": "Optional period-key upper bound when listing periods."},
                "page": PAGE,
                "size": SIZE,
                "detailLevel": DETAIL,
            },
            ["projectId"],
        ),
    ),
    _tool(
        "list_project_capabilities",
        "List stable long-lived Project Capabilities with aliases, deterministic maturity and reasons, formation/enhancement times, evidence counts, stale state, and merge redirects.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "activeOnly": {"type": "boolean", "default": True},
                "maturity": {"type": "string", "enum": ["FORMING", "FORMED", "CONTINUOUSLY_ENHANCED", "LONG_TERM_STABLE"]},
                "search": {"type": "string", "maxLength": 200},
                "page": PAGE,
                "size": SIZE,
                "detailLevel": DETAIL,
            },
            ["projectId"],
        ),
    ),
    _tool(
        "get_capability_evolution",
        "Read one stable capability's chronological versioned evolution with real occurrence times, source facts, source periods, and merge history.",
        _schema(
            {"projectId": PROJECT_ID, "capabilityId": PROJECT_ID, "page": PAGE, "size": SIZE, "detailLevel": DETAIL},
            ["projectId", "capabilityId"],
        ),
    ),
    _tool(
        "trace_project_fact",
        "Explain why a factual conclusion exists by tracing one Project Fact to its bounded batch, commits, files, Agent results, evidence, and related capabilities. Never returns diffs, prompts, reasoning, secrets, or absolute paths.",
        _schema({"projectId": PROJECT_ID, "factId": PROJECT_ID, "detailLevel": DETAIL}, ["projectId", "factId"]),
    ),
    _tool(
        "get_project_brief",
        "Build a tightly budgeted project brief led by what happened, earliest and current confirmed state, recent change stories and chapters, then facts and optional capabilities with explicit coverage warnings.",
        _schema(
            {"projectId": PROJECT_ID, "sizeBudget": {"type": "integer", "minimum": 2000, "maximum": 12000, "default": 6000}},
            ["projectId"],
        ),
    ),
    _tool(
        "search_project_portfolio",
        "Search facts and derived history across every project authorized to the current user while preserving projectId, source layer, epistemic status, and currentness.",
        _schema(
            {
                "query": {"type": "string", "minLength": 1, "maxLength": 500},
                "size": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
            },
            ["query"],
        ),
    ),
    _tool(
        "get_project_context_package",
        "Read a task-relevant, revision-aware, provenance-preserving context package from persisted strong facts, declarations, inferences, evidence, ranges, conflicts, unknowns, coverage, and trust guidance without calling a model.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "taskDescription": {"type": "string", "maxLength": 1000},
                "scope": {
                    "type": "array", "maxItems": 20,
                    "items": {"type": "string", "maxLength": 200},
                },
                "revisionPreference": {
                    "type": "string", "enum": ["CURRENT_SNAPSHOT", "LATEST_AVAILABLE", "ANY_PERSISTED"],
                    "default": "CURRENT_SNAPSHOT",
                },
                "evidenceDepth": {
                    "type": "string", "enum": ["COMPACT", "STANDARD", "DEEP"], "default": "STANDARD",
                },
                "sizeBudget": {"type": "integer", "minimum": 4000, "maximum": 32000, "default": 8000},
            },
            ["projectId"],
        ),
    ),
    _tool(
        "get_project_evidence",
        "Read one persisted Evidence descriptor by ID without exposing complete files, absolute local paths, prompts, raw responses, reasoning, or credentials.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "evidenceId": {"type": "string", "minLength": 1, "maxLength": 200},
            },
            ["projectId", "evidenceId"],
        ),
    ),
    _tool(
        "get_project_knowledge",
        "Read bounded strong facts, declared material, inferred candidates, conflicts, unknowns, and process evidence without merging their epistemic states.",
        _schema(
            {
                "projectId": PROJECT_ID,
                "size": {"type": "integer", "minimum": 1, "maximum": 300, "default": 100},
            },
            ["projectId"],
        ),
    ),
]


class ProjectFlowClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        query = {key: value for key, value in (params or {}).items() if value is not None and value != ""}
        url = self.settings.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(query, doseq=True)
        headers = {"Accept": "application/json", "X-ProjectFlow-Caller": "hermes-stdio"}
        if self.settings.access_token:
            headers["Authorization"] = "Bearer " + self.settings.access_token
        request = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=self.settings.timeout_seconds) as response:
                raw = response.read(self.settings.max_result_bytes + 1)
        except urllib.error.HTTPError as error:
            raw = error.read(64_000)
            code, message = _api_error(raw, "PROJECTFLOW_REQUEST_FAILED", f"ProjectFlow returned HTTP {error.code}.")
            raise AdapterError(code, message, status=error.code, retryable=error.code >= 500) from None
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            reason = "timed out" if isinstance(error, TimeoutError) else "is unavailable"
            raise AdapterError(
                "PROJECTFLOW_BACKEND_UNAVAILABLE",
                f"ProjectFlow backend {reason} at the configured local URL.",
                retryable=True,
            ) from None
        if len(raw) > self.settings.max_result_bytes:
            raise AdapterError(
                "PROJECTFLOW_RESULT_TOO_LARGE",
                "ProjectFlow result exceeded the MCP output bound; request a smaller page or compact detail level.",
            )
        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise AdapterError("PROJECTFLOW_RESPONSE_INVALID", "ProjectFlow returned an invalid JSON response.", retryable=True) from None
        if not isinstance(body, dict) or "data" not in body:
            raise AdapterError("PROJECTFLOW_RESPONSE_INVALID", "ProjectFlow response did not contain the expected data envelope.", retryable=True)
        return body["data"]


def _api_error(raw: bytes, fallback_code: str, fallback_message: str) -> tuple[str, str]:
    try:
        payload = json.loads(raw.decode("utf-8"))
        error = payload.get("error", {})
        return str(error.get("code") or fallback_code), str(error.get("message") or fallback_message)
    except (UnicodeDecodeError, json.JSONDecodeError, AttributeError):
        return fallback_code, fallback_message


def _argument(arguments: dict[str, Any], name: str, default: Any = None) -> Any:
    value = arguments.get(name, default)
    if value is None:
        return default
    return value


def _project(arguments: dict[str, Any]) -> str:
    value = str(_argument(arguments, "projectId", "")).strip()
    if not value:
        raise AdapterError("MCP_ARGUMENT_INVALID", "projectId is required.")
    return urllib.parse.quote(value, safe="")


def call_tool(client: ProjectFlowClient, name: str, arguments: dict[str, Any]) -> Any:
    if name == "list_projects":
        return client.get("/api/project-memory/portfolio")
    if name == "search_project_portfolio":
        return client.get("/api/project-memory/portfolio/search", {
            "query": _argument(arguments, "query", ""),
            "size": _argument(arguments, "size", 20),
        })
    project = _project(arguments)
    base = f"/api/projects/{project}/project-memory"
    if name == "get_project_snapshot":
        return client.get(base + "/snapshot")
    if name == "get_project_history_overview":
        return client.get(base + "/history/overview")
    if name == "list_project_history_chapters":
        return client.get(base + "/history/chapters", {
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
        })
    if name == "list_project_history_corrections":
        return client.get(base + "/history/corrections", {
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 50),
        })
    if name == "list_project_change_stories":
        return client.get(base + "/history/stories", {
            "subject": _argument(arguments, "subject"),
            "attentionOnly": str(bool(_argument(arguments, "attentionOnly", False))).lower(),
            "from": _argument(arguments, "from"), "to": _argument(arguments, "until"),
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
        })
    if name == "list_project_evolution_threads":
        return client.get(base + "/history/threads", {
            "subject": _argument(arguments, "subject"),
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
        })
    if name == "list_project_history_events":
        return client.get(base + "/history/events", {
            "sourceType": _argument(arguments, "sourceType"),
            "category": _argument(arguments, "category"),
            "transition": _argument(arguments, "transition"),
            "authority": _argument(arguments, "authority"),
            "epistemicStatus": _argument(arguments, "epistemicStatus"),
            "rewriteState": _argument(arguments, "rewriteState"),
            "subject": _argument(arguments, "subject"),
            "attentionOnly": str(bool(_argument(arguments, "attentionOnly", False))).lower(),
            "from": _argument(arguments, "from"), "to": _argument(arguments, "until"),
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
        })
    if name == "get_project_history_evidence":
        event_id = urllib.parse.quote(str(_argument(arguments, "eventId", "")).strip(), safe="")
        if not event_id:
            raise AdapterError("MCP_ARGUMENT_INVALID", "eventId is required.")
        return client.get(base + f"/history/events/{event_id}/evidence")
    if name == "search_project_memory":
        entity_types = _argument(arguments, "entityTypes")
        return client.get(base + "/search", {
            "query": _argument(arguments, "query", ""), "from": _argument(arguments, "from"),
            "to": _argument(arguments, "until"), "entityTypes": ",".join(entity_types) if entity_types else None,
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
            "detailLevel": _argument(arguments, "detailLevel", "compact"),
        })
    if name == "get_recent_changes":
        return client.get(base + "/recent-changes", {
            "from": _argument(arguments, "from"), "to": _argument(arguments, "until"),
            "includeAttention": str(bool(_argument(arguments, "includeAttention", True))).lower(),
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
            "detailLevel": _argument(arguments, "detailLevel", "compact"),
        })
    if name == "get_project_timeline":
        return client.get(base + "/timeline", {
            "granularity": _argument(arguments, "granularity", "MONTH"),
            "periodKey": _argument(arguments, "periodKey"), "from": _argument(arguments, "from"),
            "to": _argument(arguments, "until"), "page": _argument(arguments, "page", 0),
            "size": _argument(arguments, "size", 10), "detailLevel": _argument(arguments, "detailLevel", "compact"),
        })
    if name == "list_project_capabilities":
        return client.get(base + "/capabilities", {
            "activeOnly": str(bool(_argument(arguments, "activeOnly", True))).lower(),
            "maturity": _argument(arguments, "maturity"), "search": _argument(arguments, "search"),
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
            "detailLevel": _argument(arguments, "detailLevel", "compact"),
        })
    if name == "get_capability_evolution":
        capability = urllib.parse.quote(str(_argument(arguments, "capabilityId", "")).strip(), safe="")
        if not capability:
            raise AdapterError("MCP_ARGUMENT_INVALID", "capabilityId is required.")
        return client.get(base + f"/capabilities/{capability}/evolution", {
            "page": _argument(arguments, "page", 0), "size": _argument(arguments, "size", 10),
            "detailLevel": _argument(arguments, "detailLevel", "compact"),
        })
    if name == "trace_project_fact":
        fact = urllib.parse.quote(str(_argument(arguments, "factId", "")).strip(), safe="")
        if not fact:
            raise AdapterError("MCP_ARGUMENT_INVALID", "factId is required.")
        return client.get(base + f"/facts/{fact}/trace", {"detailLevel": _argument(arguments, "detailLevel", "compact")})
    if name == "get_project_brief":
        return client.get(base + "/brief", {"sizeBudget": _argument(arguments, "sizeBudget", 6000)})
    if name == "get_project_context_package":
        return client.get(base + "/context-package", {
            "taskDescription": _argument(arguments, "taskDescription"),
            "scope": _argument(arguments, "scope"),
            "revisionPreference": _argument(arguments, "revisionPreference", "CURRENT_SNAPSHOT"),
            "evidenceDepth": _argument(arguments, "evidenceDepth", "STANDARD"),
            "sizeBudget": _argument(arguments, "sizeBudget", 8000),
        })
    if name == "get_project_evidence":
        evidence_id = urllib.parse.quote(str(_argument(arguments, "evidenceId", "")).strip(), safe="")
        if not evidence_id:
            raise AdapterError("MCP_ARGUMENT_INVALID", "evidenceId is required.")
        return client.get(base + f"/evidence/{evidence_id}")
    if name == "get_project_knowledge":
        return client.get(base + "/knowledge", {"size": _argument(arguments, "size", 100)})
    raise AdapterError("MCP_TOOL_NOT_FOUND", f"Unknown ProjectFlow tool: {name}")


def list_resources(client: ProjectFlowClient) -> dict[str, Any]:
    catalog = client.get("/api/project-memory/portfolio")
    resources = []
    for item in catalog.get("items", []) if isinstance(catalog, dict) else []:
        project_id = str(item.get("projectId", "")).strip()
        if not project_id:
            continue
        resources.append({
            "uri": f"projectflow://projects/{project_id}/context",
            "name": str(item.get("name") or project_id),
            "description": "Versioned ProjectFlow context package with separated strong facts, candidates, conflicts, unknowns, and provenance.",
            "mimeType": "application/json",
        })
    return {"resources": resources}


def read_resource(client: ProjectFlowClient, uri: str) -> dict[str, Any]:
    parsed = urllib.parse.urlparse(uri)
    parts = [part for part in parsed.path.split("/") if part]
    if parsed.scheme != "projectflow" or parsed.netloc != "projects" or len(parts) != 2 or parts[1] != "context":
        raise AdapterError("MCP_RESOURCE_INVALID", "Unsupported ProjectFlow resource URI.")
    project_id = urllib.parse.quote(parts[0], safe="")
    payload = client.get(f"/api/projects/{project_id}/project-memory/context-package", {"sizeBudget": 8000})
    return {
        "contents": [{
            "uri": uri,
            "mimeType": "application/json",
            "text": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        }]
    }


def _tool_result(payload: Any, *, is_error: bool = False) -> dict[str, Any]:
    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    result: dict[str, Any] = {"content": [{"type": "text", "text": text}], "isError": is_error}
    if not is_error and isinstance(payload, dict):
        result["structuredContent"] = payload
    return result


def handle(message: dict[str, Any], client: ProjectFlowClient) -> dict[str, Any] | None:
    method = message.get("method")
    request_id = message.get("id")
    if request_id is None:
        return None
    if method == "initialize":
        requested = str(message.get("params", {}).get("protocolVersion", ""))
        protocol = requested if requested in PROTOCOL_VERSIONS else DEFAULT_PROTOCOL_VERSION
        return _response(request_id, {
            "protocolVersion": protocol,
            "capabilities": {
                "tools": {"listChanged": False},
                "resources": {"subscribe": False, "listChanged": False},
            },
            "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
            "instructions": "Use list_projects when needed, then start with Project History for what happened and drill down to stories, threads, raw events, and Evidence. Only OBSERVED/VERIFIED records are strong facts; chapters, stories, Timeline, Capabilities, candidates, conflicts, and unknowns keep distinct source layers.",
        })
    if method == "ping":
        return _response(request_id, {})
    if method == "tools/list":
        return _response(request_id, {"tools": TOOLS})
    if method == "resources/list":
        try:
            return _response(request_id, list_resources(client))
        except AdapterError as error:
            return _error(request_id, -32002, error.message)
    if method == "resources/read":
        try:
            return _response(request_id, read_resource(client, str((message.get("params") or {}).get("uri", ""))))
        except AdapterError as error:
            return _error(request_id, -32002, error.message)
    if method == "tools/call":
        params = message.get("params") or {}
        try:
            payload = call_tool(client, str(params.get("name", "")), params.get("arguments") or {})
            return _response(request_id, _tool_result(payload))
        except AdapterError as error:
            return _response(request_id, _tool_result(error.payload(), is_error=True))
        except Exception:
            return _response(request_id, _tool_result({
                "error": {"code": "PROJECTFLOW_MCP_INTERNAL_ERROR", "message": "The ProjectFlow MCP adapter failed safely.", "retryable": False}
            }, is_error=True))
    return _error(request_id, -32601, "Method not found")


def _response(request_id: Any, result: Any) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def _error(request_id: Any, code: int, message: str) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def main() -> int:
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        client = ProjectFlowClient(Settings.load())
    except AdapterError as error:
        print(json.dumps(error.payload(), ensure_ascii=False), file=sys.stderr, flush=True)
        return 2
    for raw in sys.stdin:
        line = raw.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
            if not isinstance(message, dict):
                raise ValueError
            response = handle(message, client)
        except (json.JSONDecodeError, ValueError):
            response = _error(None, -32700, "Parse error")
        if response is not None:
            sys.stdout.write(json.dumps(response, ensure_ascii=False, separators=(",", ":")) + "\n")
            sys.stdout.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
