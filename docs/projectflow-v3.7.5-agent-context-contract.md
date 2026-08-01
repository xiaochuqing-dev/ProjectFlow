# ProjectFlow V3.7.5 Agent Context Contract

## Purpose

Context Package v2 gives an authorized Agent the smallest useful, traceable subset of persisted project state for a task. It reduces repeated scanning but never tells the Agent to skip reading the source it will modify.

## Request

| Field | Meaning |
| --- | --- |
| `projectId` | Owned project identifier |
| `taskDescription` | Redacted task intent, maximum 1,000 characters |
| `scope` | Optional project-relative paths, maximum 20 |
| `revisionPreference` | `CURRENT_SNAPSHOT`, `LATEST_AVAILABLE` or `ANY_PERSISTED` |
| `evidenceDepth` | `COMPACT`, `STANDARD` or `DEEP` |
| `sizeBudget` | 4,000 to 32,000 serialized characters |
| authorization | Normal ProjectFlow user identity |

Absolute paths and parent traversal are rejected. Task text is redacted before ranking or returning.

## Response

The response contains resolved project identity, persisted source Revision, task and scope, strong facts, declarations/process evidence, inference, conflicts, unknowns, key Evidence, verified and historical records, related source ranges, trust guidance, historical coverage, coverage disclosure, unread scope, limitations, deep-read targets, provenance, generation metadata and a deterministic `packageRevision`.

`packageRevision` is SHA-256 over a canonical identity containing project, source Revision, normalized task/scope, depth, status partitions, Evidence/ranges, unread scope and limitations. `generatedAt` is not part of the identity, so identical persisted inputs produce the same revision.

GET generation is `PERSISTED_ONLY`: no model call, repository scan, Git execution or Fact mutation occurs.

## Retrieval and trust

Task terms rank statements, Evidence refs and structure symbols. Scope is scored separately so generic path fragments such as `src` do not contaminate semantic matching. Conflict and UNKNOWN items remain eligible even when task terms do not match.

Trust groups are guidance, not absolute authority:

- Generally reusable: current `VERIFIED` items whose Revision and validation state match and whose package is not degraded.
- Quick verify: current `OBSERVED` items and source locations relevant to the task.
- Must revalidate: declarations, inference, conflict, unknown, process evidence, old Revision, partial/degraded content and high-risk prerequisites.

Coverage disclosure reports matched and available item counts. An unread scope entry means the package did not establish that the requested area was fully read.

## Local revalidation

`POST /api/projects/{projectId}/project-memory/revalidate` supports only:

- `VERIFY_FACT`: re-check bounded file and commit Evidence already linked to a Fact.
- `REFRESH_EVIDENCE`: re-read a safe project-relative source range for one persisted Evidence ID.
- `REREAD_RANGE`: read an explicit line range, capped at 200 lines and 16,000 characters.
- `VALIDATE_CURRENTNESS`: compare a Fact or Evidence Revision with the local Git HEAD when available.
- `RESOLVE_PACKAGE_LATEST`: regenerate the persisted package and compare its source Revision with local HEAD.

The service uses fixed Git commands and `LargeFileContentService`; it never accepts a model-built command, reads a sensitive-file body, runs a full analysis or modifies ProjectFact.

## Candidate Work Result

`POST /api/projects/{projectId}/agent-candidates/work-results` accepts changed files, behavior claims, commands, test claims, commit/PR refs, limitations, unresolved items, Evidence refs, candidate facts, conflicts and UNKNOWN-resolution candidates.

Safe changed files are re-read inside the bound project and receive a source hash. Sensitive or binary files remain metadata-only. Valid local commits may be checked with a fixed `git cat-file` command.

The Work Result itself is `PROCESS_EVIDENCE`. Candidate facts may only use `DECLARED`, `INFERRED`, `CONFLICTED`, `UNKNOWN` or `PROCESS_EVIDENCE`. Any `OBSERVED` or `VERIFIED` input rejects the whole batch before the first candidate is saved.

## API and Hermes

REST context endpoint:

`GET /api/projects/{projectId}/project-memory/context-package`

Hermes forwards the same task, scope, revision, depth and budget fields through its read-only Context Package tool. Candidate writes and local revalidation remain authenticated REST operations and are not exposed as remote MCP write tools.

## Security and continuity

Every read/write validates user and project ownership. Returned data excludes raw prompts/responses, reasoning, credentials and absolute paths. The package is model-neutral and Agent-neutral because it is built from persisted ProjectFlow state; switching model or Agent does not change the fact contract.
