# ProjectFlow V3.7.2 Integration Boundary

## ProjectFlow owns

- normalized project evidence and evidence references;
- ProjectFact as the factual source;
- current Project Understanding and trust state;
- Historical Coverage and evidence-backed evolution reconstruction;
- lifecycle Capability semantics derived from Facts;
- bounded read Gateway semantics and ProjectFlow presentation.

## External tools own

- coding/agent execution and session orchestration;
- repository hosting, review, issue and CI systems;
- model account/provider switching and billing;
- note-taking/vault UX;
- secret scanning and credential validation;
- language parsing/index production;
- updater/runtime/version management;
- generic retrieval, vector, workflow and automation platforms.

## Allowed integration shapes

1. Evidence Source Adapter: imports bounded normalized evidence with project binding and provenance.
2. Intelligence Provider Adapter: interprets normalized evidence but cannot create Facts or execute arbitrary commands.
3. Projection Adapter: consumes ProjectFlow views and writes to an external presentation target without becoming truth.

Hermes and Obsidian remain existing projection/read consumers. GitHub remains optional evidence enrichment. Codex/Claude/other agents may supply Agent Result process evidence. None of them become a fact source merely by being connected.

## Prohibited drift

Do not build a Coding Agent, Agent Manager, Provider Switcher, token dashboard, model leaderboard, GitHub/GitLab/Obsidian/Hermes replacement, generic RAG/workflow platform, parser/SCIP producer, updater, CLI version manager or generic tool control center.

## Data rules

External input must be project-bound, bounded, source-revisioned, relative-locator-only, redacted and raw-payload-free. Duplicate identity is deterministic. Missing binding, malformed identity, unsafe paths or raw payload retention is rejected. Accepted envelopes remain evidence candidates and require the normal Fact trust boundary.
