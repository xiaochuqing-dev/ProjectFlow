# Hermes MCP Integration

ProjectFlow provides a repository-local, dependency-free Python stdio MCP adapter at `integrations/hermes/projectflow_mcp.py`. It exposes the Project Memory Gateway and the V3.7.5 Agent Context read boundary only; it is not a general REST proxy and contains no write tool.

## Tools

The server exposes thirteen bounded, idempotent, read-only tools: `list_projects`, `get_project_snapshot`, `search_project_memory`, `get_recent_changes`, `get_project_timeline`, `list_project_capabilities`, `get_capability_evolution`, `trace_project_fact`, `get_project_brief`, `search_project_portfolio`, `get_project_context_package`, `get_project_evidence`, and `get_project_knowledge`.

Context Package v2 accepts task, scope, revision preference, Evidence depth and size budget. It returns deterministic package revision, status partitions, safe ranges, currentness, conflicts, unknowns, limitations and unread scope without scanning the repository or calling a model. Hermes cannot submit Candidate Work Results or invoke local revalidation.

Tool descriptions state when to use each semantic view. Schemas bound pages, detail level, time filters, entity filters and brief size. Tool annotations declare read-only, non-destructive, idempotent and closed-world behavior.

## Local configuration

Start ProjectFlow normally, then register the adapter in Hermes with a local command equivalent to:

```yaml
mcp_servers:
  projectflow:
    command: python
    args:
      - <repository>\\integrations\\hermes\\projectflow_mcp.py
    env:
      PROJECTFLOW_BASE_URL: http://127.0.0.1:8080
    enabled: true
```

`run-projectflow-mcp.ps1` is a portable launcher when a host prefers a script entry. Python 3 is required; no package installation or global configuration is performed.

Optional process-scoped settings are `PROJECTFLOW_ACCESS_TOKEN`, `PROJECTFLOW_MCP_TIMEOUT_SECONDS`, and `PROJECTFLOW_MCP_MAX_RESULT_BYTES`. Do not place credentials in repository files. V3.4.3 accepts only localhost/loopback backend URLs; remote transport, remote authentication and Telegram setup are not implemented.

## Protocol and diagnostics

The adapter uses newline-delimited UTF-8 JSON-RPC on stdin/stdout and writes no non-protocol text to stdout. It handles initialize, ping, tools/list and tools/call. Backend HTTP errors are mapped to stable machine-readable error codes; timeouts/unavailable backend are retryable, while invalid configuration, cross-project access and oversized output are explicit failures.

Useful checks:

```powershell
python integrations/hermes/test_projectflow_mcp.py
hermes mcp test projectflow
```

The deterministic suite launches a real stdio subprocess against a fake HTTP Gateway and verifies discovery, all thirteen tools, Context Package parameters, auth forwarding, pagination, idempotence, concurrent reads, restart, timeout, remote rejection and credential redaction. Token, latency and model diagnostics are never treated as fact authority or projected as quality judgments.
