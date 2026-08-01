from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


SCRIPT = Path(__file__).with_name("projectflow_mcp.py")
PROJECT_ID = "11111111-1111-1111-1111-111111111111"


class State:
    lock = threading.Lock()
    requests: list[dict] = []
    active = 0
    max_active = 0
    sleep_seconds = 0.0

    @classmethod
    def reset(cls) -> None:
        with cls.lock:
            cls.requests = []
            cls.active = 0
            cls.max_active = 0
            cls.sleep_seconds = 0.0


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        with State.lock:
            State.active += 1
            State.max_active = max(State.max_active, State.active)
            State.requests.append({
                "path": parsed.path,
                "query": parse_qs(parsed.query),
                "authorization": self.headers.get("Authorization", ""),
                "caller": self.headers.get("X-ProjectFlow-Caller", ""),
            })
            delay = State.sleep_seconds
        try:
            if delay:
                time.sleep(delay)
            if "22222222-2222-2222-2222-222222222222" in parsed.path:
                self._json(404, {"error": {"code": "PROJECT_NOT_FOUND", "message": "项目不存在"}})
                return
            if parsed.path == "/api/project-memory/portfolio":
                data = {
                    "items": [{
                        "projectId": PROJECT_ID, "name": "ProjectFlow", "latestRevision": "git:abc",
                        "strongFactCount": 42, "unknownCount": 1, "conflictCount": 0,
                    }],
                    "total": 1,
                }
            elif parsed.path == "/api/project-memory/portfolio/search":
                query = parse_qs(parsed.query)
                data = {
                    "query": query.get("query", [""])[0],
                    "items": [{"projectId": PROJECT_ID, "entityType": "FACT", "status": "OBSERVED"}],
                    "searchedProjectCount": 1,
                    "truncated": False,
                }
            elif parsed.path.endswith("/context-package"):
                data = {
                    "packageVersion": "projectflow-agent-context-v2", "packageRevision": "sha256:context",
                    "projectId": PROJECT_ID,
                    "currentStrongFacts": [{"itemId": "fact:1", "epistemicStatus": "OBSERVED"}],
                    "unknowns": [], "conflicts": [], "provenance": ["fact:1"],
                }
            elif parsed.path.endswith("/snapshot"):
                data = {"project": {"projectId": PROJECT_ID, "name": "ProjectFlow"}, "factCount": 42}
            elif parsed.path.endswith("/search"):
                query = parse_qs(parsed.query)
                data = {
                    "items": [{"entityType": "FACT", "entityId": "33333333-3333-3333-3333-333333333333", "title": query.get("query", [""])[0]}],
                    "page": int(query.get("page", [0])[0]),
                    "size": int(query.get("size", [10])[0]),
                    "hasMore": True,
                }
            else:
                data = {"ok": True, "path": parsed.path}
            self._json(200, {"data": data, "message": "OK"})
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass
        finally:
            with State.lock:
                State.active -= 1

    def _json(self, status: int, payload: dict) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, *_: object) -> None:
        return


class McpProcess:
    def __init__(self, base_url: str, **extra_env: str):
        env = os.environ.copy()
        env.update({"PROJECTFLOW_BASE_URL": base_url, **extra_env})
        self.process = subprocess.Popen(
            [sys.executable, str(SCRIPT)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            env=env,
        )
        self.next_id = 1

    def request(self, method: str, params: dict | None = None) -> dict:
        request_id = self.next_id
        self.next_id += 1
        payload = {"jsonrpc": "2.0", "id": request_id, "method": method}
        if params is not None:
            payload["params"] = params
        assert self.process.stdin is not None
        assert self.process.stdout is not None
        self.process.stdin.write(json.dumps(payload, separators=(",", ":")) + "\n")
        self.process.stdin.flush()
        line = self.process.stdout.readline()
        if not line:
            stderr = self.process.stderr.read() if self.process.stderr else ""
            raise AssertionError(f"MCP process ended without a response: {stderr}")
        response = json.loads(line)
        self.assert_clean(response, request_id)
        return response

    @staticmethod
    def assert_clean(response: dict, request_id: int) -> None:
        if response.get("id") != request_id:
            raise AssertionError(f"Unexpected response id: {response}")

    def close(self) -> None:
        if self.process.stdin:
            self.process.stdin.close()
        try:
            self.process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait(timeout=3)
        if self.process.stdout:
            self.process.stdout.close()
        if self.process.stderr:
            self.process.stderr.close()

    def __enter__(self) -> "McpProcess":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()


class MCPProjectHistoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.base_url = f"http://127.0.0.1:{cls.server.server_port}"
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def setUp(self) -> None:
        State.reset()

    def test_initialize_discovery_is_stable_bounded_and_read_only(self) -> None:
        started = time.perf_counter()
        with McpProcess(self.base_url) as mcp:
            initialized = mcp.request("initialize", {
                "protocolVersion": "2025-11-25", "capabilities": {},
                "clientInfo": {"name": "contract-test", "version": "1"},
            })
            self.assertEqual("2025-11-25", initialized["result"]["protocolVersion"])
            tools = mcp.request("tools/list")["result"]["tools"]
        print(f"MCP_METRIC startup_and_discovery_ms={(time.perf_counter() - started) * 1000:.1f} tools={len(tools)}")
        self.assertEqual(13, len(tools))
        self.assertEqual([
            "list_projects", "get_project_snapshot", "search_project_memory", "get_recent_changes",
            "get_project_timeline", "list_project_capabilities", "get_capability_evolution",
            "trace_project_fact", "get_project_brief", "search_project_portfolio",
            "get_project_context_package", "get_project_evidence", "get_project_knowledge",
        ], [tool["name"] for tool in tools])
        self.assertTrue(all(len(tool["description"]) > 70 for tool in tools))
        self.assertTrue(all(tool["annotations"]["readOnlyHint"] for tool in tools))
        self.assertFalse(any(any(word in tool["name"] for word in ("create", "update", "delete", "merge", "run_shell")) for tool in tools))

    def test_project_context_resources_are_listed_and_read_with_provenance(self) -> None:
        with McpProcess(self.base_url, PROJECTFLOW_ACCESS_TOKEN="example-token") as mcp:
            resources = mcp.request("resources/list")["result"]["resources"]
            self.assertEqual(1, len(resources))
            uri = resources[0]["uri"]
            self.assertEqual(f"projectflow://projects/{PROJECT_ID}/context", uri)
            result = mcp.request("resources/read", {"uri": uri})["result"]
        content = json.loads(result["contents"][0]["text"])
        self.assertEqual("projectflow-agent-context-v2", content["packageVersion"])
        self.assertEqual("OBSERVED", content["currentStrongFacts"][0]["epistemicStatus"])
        self.assertEqual(["fact:1"], content["provenance"])

    def test_tool_call_forwards_auth_pagination_and_is_idempotent(self) -> None:
        with McpProcess(self.base_url, PROJECTFLOW_ACCESS_TOKEN="example-token") as mcp:
            params = {"name": "search_project_memory", "arguments": {
                "projectId": PROJECT_ID, "query": "FactCursor", "entityTypes": ["FACT", "CAPABILITY"],
                "page": 2, "size": 7, "detailLevel": "compact",
            }}
            started = time.perf_counter()
            first = mcp.request("tools/call", params)["result"]
            first_latency = (time.perf_counter() - started) * 1000
            second = mcp.request("tools/call", params)["result"]
        print(f"MCP_METRIC tool_call_ms={first_latency:.1f} page=2 size=7")
        self.assertFalse(first["isError"])
        self.assertEqual(first["structuredContent"], second["structuredContent"])
        self.assertEqual(2, first["structuredContent"]["page"])
        self.assertEqual(7, first["structuredContent"]["size"])
        with State.lock:
            request = State.requests[-1]
        self.assertEqual("Bearer example-token", request["authorization"])
        self.assertEqual("hermes-stdio", request["caller"])
        self.assertEqual(["FACT,CAPABILITY"], request["query"]["entityTypes"])

    def test_context_package_forwards_task_scope_revision_and_depth(self) -> None:
        with McpProcess(self.base_url) as mcp:
            result = mcp.request("tools/call", {"name": "get_project_context_package", "arguments": {
                "projectId": PROJECT_ID,
                "taskDescription": "improve mail retry",
                "scope": ["backend/mail", "docs/spec.md"],
                "revisionPreference": "LATEST_AVAILABLE",
                "evidenceDepth": "DEEP",
                "sizeBudget": 12000,
            }})["result"]
        self.assertFalse(result["isError"])
        self.assertEqual("projectflow-agent-context-v2", result["structuredContent"]["packageVersion"])
        with State.lock:
            request = State.requests[-1]
        self.assertEqual(["improve mail retry"], request["query"]["taskDescription"])
        self.assertEqual(["backend/mail", "docs/spec.md"], request["query"]["scope"])
        self.assertEqual(["LATEST_AVAILABLE"], request["query"]["revisionPreference"])
        self.assertEqual(["DEEP"], request["query"]["evidenceDepth"])

    def test_backend_unavailable_and_cross_project_errors_are_machine_readable(self) -> None:
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
        sock.close()
        with McpProcess(f"http://127.0.0.1:{port}") as mcp:
            unavailable = mcp.request("tools/call", {"name": "get_project_snapshot", "arguments": {"projectId": PROJECT_ID}})["result"]
        self.assertTrue(unavailable["isError"])
        self.assertEqual("PROJECTFLOW_BACKEND_UNAVAILABLE", json.loads(unavailable["content"][0]["text"])["error"]["code"])

        with McpProcess(self.base_url) as mcp:
            denied = mcp.request("tools/call", {"name": "get_project_snapshot", "arguments": {
                "projectId": "22222222-2222-2222-2222-222222222222"
            }})["result"]
        self.assertTrue(denied["isError"])
        self.assertEqual("PROJECT_NOT_FOUND", json.loads(denied["content"][0]["text"])["error"]["code"])

    def test_process_restart_and_concurrent_reads_recover_cleanly(self) -> None:
        def one_call(_: int) -> int:
            with McpProcess(self.base_url) as mcp:
                result = mcp.request("tools/call", {"name": "get_project_snapshot", "arguments": {"projectId": PROJECT_ID}})["result"]
                return result["structuredContent"]["factCount"]

        self.assertEqual(42, one_call(0))
        self.assertEqual(42, one_call(1))
        State.sleep_seconds = 0.08
        started = time.perf_counter()
        with ThreadPoolExecutor(max_workers=6) as executor:
            counts = list(executor.map(one_call, range(6)))
        elapsed = (time.perf_counter() - started) * 1000
        print(f"MCP_METRIC concurrent_reads_ms={elapsed:.1f} requests=6 max_active={State.max_active}")
        self.assertEqual([42] * 6, counts)
        self.assertGreaterEqual(State.max_active, 2)

    def test_timeout_and_remote_default_fail_without_credential_leak(self) -> None:
        State.sleep_seconds = 1.2
        with McpProcess(self.base_url, PROJECTFLOW_MCP_TIMEOUT_SECONDS="1", PROJECTFLOW_ACCESS_TOKEN="example-token") as mcp:
            result = mcp.request("tools/call", {"name": "get_project_snapshot", "arguments": {"projectId": PROJECT_ID}})["result"]
        rendered = json.dumps(result)
        self.assertTrue(result["isError"])
        self.assertIn("PROJECTFLOW_BACKEND_UNAVAILABLE", rendered)
        self.assertNotIn("example-token", rendered)

        env = os.environ.copy()
        env.update({"PROJECTFLOW_BASE_URL": "https://example.com", "PROJECTFLOW_ACCESS_TOKEN": "example-token"})
        completed = subprocess.run(
            [sys.executable, str(SCRIPT)], input="", text=True, encoding="utf-8",
            capture_output=True, env=env, timeout=5,
        )
        self.assertEqual(2, completed.returncode)
        self.assertIn("PROJECTFLOW_REMOTE_DISABLED", completed.stderr)
        self.assertNotIn("example-token", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
