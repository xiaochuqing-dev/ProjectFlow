# ProjectFlow Hermes MCP

This repository-local stdio adapter is a read-only consumer of the loopback ProjectFlow API. It does not become a fact source, run models, persist Agent answers, or accept remote backend URLs.

V3.8.5 provides 20 read-only tools. Project History is the primary route for what happened: overview, dynamic chapters, change stories, evolution threads, raw events and Evidence drill-down. Corrected presentation overlays are reflected consistently across these reads without mutating ProjectFact or source Evidence. Existing snapshot, search, Timeline, optional Capability, fact trace, brief, portfolio, Evidence, knowledge and Context Package v2 reads remain compatible. The server also exposes `projectflow://projects/{projectId}/context` resources for authorized projects.

The context tool forwards optional task description, scope, revision preference, Evidence depth and size budget. ProjectFlow returns the deterministic package revision, selected source ranges, currentness, conflicts, unknowns, limitations and unread scope; Hermes does not add model interpretation or factual authority.

Strong facts are only persisted `OBSERVED` or `VERIFIED` records. Declarations, inference, conflicts, unknowns, Timeline, Capabilities and Agent process evidence keep their source layers. The adapter never upgrades status or merges same-named facts across projects.

Authentication is forwarded only to the configured loopback API. Tool output is bounded and sanitized; stderr is operational diagnostics and stdout remains JSON-RPC. Keys, Authorization values, prompts, raw model responses, reasoning and absolute paths are never returned.

Run the focused subprocess suite:

```powershell
python integrations/hermes/test_projectflow_mcp.py
```
