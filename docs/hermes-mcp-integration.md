# Hermes MCP Integration

## V3.8.5 corrected history

RC2 exposes the Gateway Story `includeHidden` option without adding write tools. Hermes returns the same corrected titles, summaries, role links and `presentationRevision` as the Gateway; enum-rich engineering detail remains opt-in. Local subprocess coverage is 10/10.

Hermes keeps the history read boundary model-free and now exposes `list_project_history_corrections` alongside the existing overview/chapter/story/thread/event/Evidence tools. Story results include user-readable text first, Primary/Supporting role, presentation authority/revision, correction conflicts and bounded technical drill-down. User declarations remain presentation-only and cannot be written through Hermes.

ProjectFlow provides a repository-local, dependency-free Python stdio MCP adapter at `integrations/hermes/projectflow_mcp.py`. It exposes the Project Memory Gateway, V3.8.0 Project History and Agent Context read boundaries only; it is not a general REST proxy and contains no write tool.

## Tools

The server exposes nineteen bounded, idempotent, read-only tools. Six V3.8.0 tools cover `get_project_history_overview`, `list_project_history_chapters`, `list_project_change_stories`, `list_project_evolution_threads`, `list_project_history_events` and `get_project_history_evidence`. The previous thirteen project, snapshot, search, recent, Timeline, Capability, Fact, brief, portfolio, context, Evidence and knowledge tools remain compatible.

History tools preserve filters, pagination, occurrence time, authority, epistemic status and rewrite state. Hermes starts with what happened, then drills into raw events and Evidence; it cannot refresh history, write a Fact, alter a Story or control Git/Obsidian.

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
