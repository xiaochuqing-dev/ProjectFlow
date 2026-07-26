# ProjectFlow V3.7.2 Current-state Audit

Audit date: 2026-07-26

Baseline: `origin/master` at `86632ff13db3b23b41856575c32accf6d758399b`, after merged V3.7.1 PR #4.

## Confirmed V3.7.1 state

- Discover → Scout → Plan → Execute → Validate → conditional Synthesize exists and uses the registered Model Gateway task.
- FILESYSTEM and SCIP are reused; DOC_READER, MANIFEST, AGENT_RESULT, GIT_HISTORY, GIT_TAG and WORKTREE use one bounded local Provider.
- Context packing builds complete JSON before serialization and records packing diagnostics.
- Evidence discovery has diversity quotas, duplicate compression, bounded samples, signature caches and secret redaction.
- Historical Coverage separates Git metadata, Facts, Tags, documents, Agent results, structural snapshots and remote collaboration.
- GET understanding/structure/evolution paths are persistence-only reads.
- The repository has no product hallucination, accuracy or model-score endpoint/UI.

## Gaps found

1. `highValueEvidenceProduced` was true for any non-empty tool prompt, so short or metadata-only output could force a second model call.
2. Analysis execution cache identity contained only source revision and requested capability names.
3. A Final Synthesis failure escaped the semantic block. With a prior snapshot it marked that old snapshot stale; without one it saved only deterministic model-failure output. Stage 1 and validated tool evidence were lost.
4. Semantic prompt rules did not explicitly separate Agent Result process evidence, token usage process metadata, current source and historical evidence.
5. Real Provider tests covered generic gateway entry points but not ProjectFlow shape/evidence/tool/view/conflict quality.
6. There was no ProjectFlow-specific human Ground Truth, metric calculator or sanitized JSON/Markdown eval artifact.
7. There was no shared External Evidence Envelope or three-adapter boundary.
8. README and context still described real semantic quality as unverified and retained an obsolete “Automatic Memory Maintenance next” roadmap statement.

## Provider audit

A read-only copy of the user-local Provider database was inspected without selecting or printing the key. The configured default was a DeepSeek-family Provider using OPENAI_CHAT_COMPLETIONS, protocol-default authentication, HTTPS endpoint, configured model, configured output ceiling, and a present key. Real evaluation uses that copied database only inside the test process and never persists the key, prompt, raw response or reasoning.

Current local Provider keys remain stored in the application database for compatibility. DTO/log/artifact exclusion is implemented, but OS-backed secret storage remains a separate productization requirement.

## Scope decision

V3.7.2 changes only project-understanding quality semantics, internal evaluation, cache identity, adapter contracts, tests, version metadata and required documentation. It adds no production dependency, database schema, route, UI benchmark, external network integration, agent runtime, watcher, parser, SCIP producer, tag or release.
