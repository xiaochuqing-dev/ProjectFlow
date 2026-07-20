# Project Fact Memory

V3.4.1 adds the automatic Project Timeline as a temporal consumer of this fact layer. Facts remain append-oriented truth; day/week/month/lifecycle assignments, deterministic statistics, summaries and themes are derived views described in `docs/project-timeline.md`. A Timeline refresh never mutates ProjectFact.

## Product purpose

ProjectFlow V3.4.0 automatically maintains a project's long-term memory from its earliest available Git history to its latest development changes.

Git and GitHub are the objective source for commits, diffs, files, branches, and timestamps. They do not explain, in stable product language, which related changes formed one real development result, why it mattered to the user, or how that result connects to later work. ProjectFlow keeps the raw evidence references and adds a durable, evidence-validated fact layer.

The normal user flow is:

```text
Develop
→ Analyze new changes
→ Leave safely

ProjectFlow:
read evidence
→ create Development Segments
→ record Project Facts
→ persist by Change Batch
→ advance the Fact Cursor
→ rebuild uncovered history in bounded background chunks
```

Routine facts do not require human confirmation. Human attention is reserved for exceptional evidence or quality problems.

## Three-layer model

### Change Batch

A Change Batch is one analyzed range of incremental or historical development. It is the time, branch, scope, and evidence container. Batches are permanent even when some contained items need attention.

### Development Segment

A Development Segment is the analysis-layer semantic grouping produced from Git, worktree, Agent result, and optional GitHub evidence. It belongs to one batch and retains generation mode, quality markers, fallback reason, model diagnostics, and source references.

Segments are not renamed into facts because analysis diagnostics and long-term memory have different lifecycles.

### Project Fact

A Project Fact is a stable, evidence-backed record of something that actually happened. It keeps its source batch/segment, occurrence window, evidence identity, content source, quality, confidence, status, and fingerprint.

Facts are append-oriented. A later model analysis, timeline, capability run, or output generator must not delete or batch-replace old facts. Similar titles do not make two historical events the same fact.

## What a fact may say

Facts may describe completed changes such as a new integration, a reliability mechanism, a parameter-policy change, a recovery path, a read model, or a concrete bug fix.

Facts may not automatically claim that a change is the project's most important capability, prescribe how the user should work next, or create future roadmap requirements. Those are interpretations or decisions, not historical facts.

Occurrence time comes from commit and Agent-result evidence. `createdAt` is only the ingestion timestamp. When evidence time cannot be determined reliably, the system records a safe degraded diagnostic or Needs Attention instead of displaying today's date as the historical event date.

## Automatic ingestion

The scan front half remains stable:

```text
Pending change preparation
→ Git/worktree/Agent evidence
→ AnalysisInputSnapshot
→ Development segmentation
→ optional model enrichment and recovery
→ persisted batch and segments
```

The V3.4.0 back half is:

```text
persisted batch and segments
→ evidence and ownership validation
→ Project Fact ingestion
→ fact/attention statistics
→ batch fact-recording completion
→ Fact Cursor advancement
```

MODEL segments with valid evidence and usable content are recorded normally. Complete partial-model items may be recorded when the final fact boundary is reliable. LOCAL_RULE segments with Git evidence and Agent results bound to objective code evidence may also be recorded. Trust comes from evidence, not from generation mode alone.

Agent-result-only claims and evidence-free segments do not become strong `RECORDED` facts. Depending on available context, they remain analysis records, become `NEEDS_ATTENTION`, or are omitted from the fact layer.

## Needs Attention

`NEEDS_ATTENTION` is exceptional, not a universal review queue. Typical reasons are:

- no valid objective evidence;
- clearly conflicting commit, file, or Agent-result evidence;
- a partially recovered result whose fact boundary is incomplete;
- an explicit quality status that is unsafe for a normal fact;
- possible exact duplication that cannot be resolved from source identity;
- rewritten or abnormal Git history that prevents reliable range/time attribution.

Needs Attention does not block normal facts, batch completion, Fact Cursor advancement, or the next scan. It remains visible in Project Records with a concrete reason and evidence context.

## Fingerprint and idempotency

The fingerprint prioritizes stable source/evidence identity:

```text
project
+ source batch/segment identity
+ sorted commit references
+ sorted Agent-result references
+ sorted key evidence references
```

Generated title and summary are not the primary identity because the same evidence may be worded differently on a later run.

Idempotency applies to repeated ingestion, reusable batches, job retry, concurrent ingestion, service recovery, legacy migration, and history replay. Service-level lookup and a database uniqueness boundary work together. A reusable old batch with segments but no facts performs idempotent fact fill before returning.

## Incremental Fact Cursor

The Fact Cursor represents the latest incremental commit successfully converted into the project's fact record. Initialization uses:

1. an existing Fact Cursor;
2. otherwise the legacy Review Cursor as a one-time compatibility starting point;
3. otherwise the bounded first-scan policy.

The cursor advances only after batch and fact persistence succeeds. A transaction failure leaves it at the previous safe commit. Needs Attention does not block advancement because its evidence range has still been recorded and diagnosed.

The legacy Review Cursor is preserved for old data and links but no longer decides the normal scan range.

## History Backfill

Recent incremental analysis and historical reconstruction solve different problems, so their cursors remain separate.

After a successful recent analysis, ProjectFlow may calculate uncovered commits up to a stable upper-bound snapshot and start bounded background reconstruction. It processes oldest-first so the record grows in historical order. Each chunk has normal batch/segment/fact provenance.

Persistent history state records coverage counts, upper bound, last processed commit, current/completed chunks, last batch, lifecycle times, and safe error diagnostics. Completed chunks and facts survive cancellation or restart. Retry resumes from the checkpoint. Commits already covered by facts, migrated segments, or compatible legacy evidence are not sent to the model again.

History work shares the existing bounded job infrastructure and model budgets. It does not hold database transactions during Git/model waits, does not block the workbench, does not move the incremental Fact Cursor, and never sends the complete Git history in one request.

## Project Records

Project Records is the batch-oriented fact browser. The batch list is paged and grouped by month/time. It shows fact count, Needs Attention count, commit/file scope, source mode, and fact-recording status. The normal action is “查看批次记录”, not “继续处理 N 条”.

Batch detail shows all facts for that batch with expandable evidence and quality information. Normal facts have no create/merge/evidence-only/ignore controls. Legacy batches and sediments remain reachable through clearly labelled compatibility views.

## Project Memory read model

The V3.4.0 product meaning of Project Memory is the long-lived collection of Project Facts and source batches. Its overview can expose fact count, covered commits, earliest/latest fact, recent facts, Needs Attention count, and history coverage without loading every fact.

The existing `ProjectMemory` entity stores legacy subjective profile/archive fields. It remains readable but is not equivalent to the new factual memory. Future timeline, capability map, Hermes, Obsidian, and output consumers should use stable Project Fact read models rather than depend on `completedCapabilities` text.

Read APIs must be project-owned, paged, and stable. Aggregate values use count/min/max/latest/projection queries. Batch lists must not perform batch-to-fact N+1, and evidence arrays must not be loaded for all facts merely to filter or count in Java.

Current V3.4.0 read endpoints are:

- `GET /api/projects/{projectId}/facts` for paged/filterable fact summaries;
- `GET /api/project-facts/{factId}` for full fact content and evidence;
- `GET /api/projects/{projectId}/fact-memory-overview` for counts, commit coverage, and earliest/latest facts;
- `GET /api/projects/{projectId}/fact-history-state` for persistent backfill progress;
- `GET /api/projects/{projectId}/project-record-batches` for paged batch cards;
- `GET /api/project-record-batches/{batchId}` for a batch and its paged facts.

## Legacy compatibility

V3.4.0 does not delete ProjectChange, SedimentAction, ProjectSediment, ProjectReviewCursor, old Change Batch statuses, or ProjectMemory fields.

Existing Development Segments are the preferred fact migration source. A confirmed sediment with a source segment reuses that fact rather than creating another. A sediment without a segment may create a legacy-origin fact only when objective evidence exists. Evidence-free legacy content remains compatibility archive data. Old pending ProjectChanges never block the Fact Cursor.

## Trust and security

Every fact, batch, cursor, history-state, and memory read must verify both user and project ownership. API keys, Authorization, full prompts, raw Provider responses, reasoning text, and unsafe absolute paths are never persisted or returned as fact diagnostics.

The database is authoritative. React state and project-scoped sessionStorage snapshots accelerate rendering only. Dashboard Bootstrap stays database-only and never runs Git, GitHub CLI, filesystem scans, models, migrations, or history backfill.

## Explicit non-goals

V3.4.0 establishes the factual foundation. It does not implement:

- complete day/week/month/all-cycle timeline generation;
- the full lifecycle capability map;
- formal Hermes synchronization;
- formal Obsidian synchronization;
- a redesign of V3.3.7 job reliability;
- a redesign of the V3.3.8 model gateway.

Those later consumers must build on Project Facts without modifying the original fact record.

## V3.4.2 capability consumer

Capability Map is now a derived long-lived consumer of the full ProjectFact set. It may add ProjectCapability, immutable Evolution, fact relations, coverage and attention, but it never edits or deletes a fact, changes FactCursor, or treats Timeline Summary/Theme as factual input. Each source fact is classified for the current generation. Failed generation preserves the previous successful map. Formal Hermes and Obsidian integrations remain later consumers of the same facts, Timeline and capabilities.

## V3.4.3 external read semantics

Project Memory Gateway is the stable consumer boundary for Project Facts and their derived views. It keeps Fact as SOURCE and Timeline/Capability/Evolution as DERIVED, uses occurrence time for recent/history placement, and exposes evidence trace without internal or sensitive payloads. Hermes consumes this boundary through local stdio MCP. It cannot create, modify, merge or delete a fact, and its read/audit activity does not advance FactCursor or alter history coverage.
