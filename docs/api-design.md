# API Design

Base path: `/api`

## V3.8.5 presentation corrections

History reads remain persisted-data-only and project-owned. The refresh endpoint is still the only source-discovery/model entry. The following presentation-only endpoints persist auditable `USER_DECLARED_PRESENTATION` declarations; they never change ProjectFact, raw events, Commit metadata or Evidence:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects/{projectId}/history/corrections` | Read active/reverted/conflicted declarations, automatic/applied values and presentation revision. |
| POST | `/projects/{projectId}/history/corrections` | Create rename, summary, merge/split, Primary/Supporting, hide/pin or declared Chapter correction with optimistic source/presentation checks. |
| POST | `/projects/{projectId}/history/corrections/{correctionId}/revert` | Revert one declaration and restore the automatic presentation. |

The same correction list is available through Project Memory Gateway. Gateway, Agent Context, Hermes, frontend and Obsidian use the same stable IDs, source revision and presentation revision.

## V3.8.0 Project History

History refresh is the only mutating entry. All other endpoints read persisted results and enforce authenticated project ownership.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/projects/{projectId}/history/refresh` | Start or reuse a persistent history refresh job; optional `force` ignores completed cache only, never duplicates an active equivalent job. |
| GET | `/projects/{projectId}/history/overview` | Read status, revision, earliest/current state, representative chapters, recent changes, coverage, gaps and diagnostics. |
| GET | `/projects/{projectId}/history/chapters` | Page dynamic chapters. |
| GET | `/projects/{projectId}/history/chapters/{chapterId}` | Read one chapter and its stories. |
| GET | `/projects/{projectId}/history/stories` | Page stories by subject, attention and occurrence range. |
| GET | `/projects/{projectId}/history/stories/{storyId}` | Read Before/Change/After, raw events and related threads. |
| GET | `/projects/{projectId}/history/threads` | Page evolution threads by subject. |
| GET | `/projects/{projectId}/history/threads/{threadId}` | Read one thread and ordered stories. |
| GET | `/projects/{projectId}/history/events` | Page raw events by source, category, transition, authority, epistemic status, rewrite state, subject, attention and time. |
| GET | `/projects/{projectId}/history/events/{eventId}` | Read one normalized event. |
| GET | `/projects/{projectId}/history/events/{eventId}/evidence` | Read bounded currentness/revision/validation/limitation/deep-link Evidence. |
| GET | `/projects/{projectId}/history/filters` | Read supported filter values. |

The same bounded reads are available under `/projects/{projectId}/project-memory/history/*` for Gateway consumers. Page size is clamped to 1–100. Responses never include raw diff, complete source payload, absolute local path, Prompt, raw model response, reasoning, Key or Authorization.

All protected endpoints require:

```http
Authorization: Bearer <access-token>
```

## Response Shape

Successful responses:

```json
{
  "data": {},
  "message": "OK"
}
```

Error responses:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": []
  }
}
```

## V3.7 Universal Evidence Understanding

## V3.7.5 Agent context, candidate and revalidation boundary

All endpoints require normal authentication and preserve not-found ownership semantics.

| Purpose | Method and path |
| --- | --- |
| Authorized project catalog | `GET /api/project-memory/portfolio` |
| Bounded cross-project search | `GET /api/project-memory/portfolio/search?query=&size=` |
| Project evidence by safe ID | `GET /api/projects/{projectId}/project-memory/evidence/{evidenceId}` |
| Status-partitioned knowledge | `GET /api/projects/{projectId}/project-memory/knowledge?size=` |
| Task-relevant Context Package v2 | `GET /api/projects/{projectId}/project-memory/context-package?taskDescription=&scope=&revisionPreference=&evidenceDepth=&sizeBudget=` |
| Submit validation candidate | `POST /api/projects/{projectId}/agent-candidates` |
| Submit bounded Agent work-result candidates | `POST /api/projects/{projectId}/agent-candidates/work-results` |
| List validation candidates | `GET /api/projects/{projectId}/agent-candidates?page=&size=` |
| Run one bounded local revalidation action | `POST /api/projects/{projectId}/project-memory/revalidate` |

Context Package v2 contains task/scope metadata, deterministic package revision, current strong facts, declarations, inference candidates, conflicts, unknowns, provenance, safe ranges, revision/currentness, limitations and unread scope. It excludes prompts, raw model responses, reasoning, credentials and absolute paths. Agent candidate submission accepts only `DECLARED`, `INFERRED`, `CONFLICTED`, `UNKNOWN` or `PROCESS_EVIDENCE`; direct `OBSERVED`/`VERIFIED` writes are rejected before persistence.

Work-result submission accepts changed relative files, behavior claims, commands/tests, Evidence refs, conflicts and UNKNOWN-resolution candidates. Engineering code re-resolves the project root, rejects path escape and sensitive content, performs bounded re-reads and binds source hashes; Agent claims remain candidates. Revalidation accepts only `VERIFY_FACT`, `REFRESH_EVIDENCE`, `REREAD_RANGE`, `VALIDATE_CURRENTNESS` or `RESOLVE_PACKAGE_LATEST`. It returns validation/currentness diagnostics and never mutates facts or triggers a model.

The existing understanding endpoints are unchanged and still validate authenticated project ownership:

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/projects/{projectId}/understanding/refresh` | Create or reuse the durable evidence discovery, Scout, plan and profile refresh job |
| GET | `/projects/{projectId}/understanding` | Read the persisted replaceable V3.7 understanding snapshot |
| GET | `/projects/{projectId}/structure-index` | Read the persisted Structure Index V2 |
| GET | `/projects/{projectId}/evolution-bridges?page=0&size=20` | Read bounded evidence-backed before/change/after bridge rows |

The understanding response retains V3.6 compatibility fields and adds:

- `sourceMap`: discovered/candidate/scout/deep-read/skipped counts, category totals and bounded relative source summaries.
- `semanticScout`: evidence-bound shape hypotheses, source assessments, applicable dimensions, validated tool requests, unknowns, conflicts and currentness warnings.
- `analysisPlan`: detected shapes, applicable/skipped dimensions, evidence priorities, tools, deep-read targets, semantic budgets, structure/history strategy and expected outputs.
- `dynamicProfile`: summary, project shapes, applicable/unavailable views and ordered evidence-bound sections.
- `historicalCoverage`: availability, evidence range, commit/Fact/Tag counts, covered/gap periods, coverage and limitations.
- `evolutionPreview`: current-state, early-project, milestone-window or long-history clustering strategy.
- `analysisMetrics`: evidence, tool, model/token, repository-size, duration, cache and coverage measurements. Token, request and duration values are process diagnostics only; clients must not present them as quality defects or use them to infer a lower reasoning tier.

The API never returns source samples, full documents, prompts, raw responses, reasoning, credentials or absolute paths. A stored V3.6 snapshot may have the new fields as null until the user explicitly refreshes it.

## V3.6 Project Understanding and Evolution Bridge

All endpoints validate authenticated user ownership of `projectId`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/projects/{projectId}/understanding/refresh` | Create or reuse the durable current-understanding refresh job |
| GET | `/projects/{projectId}/understanding` | Read the persisted replaceable understanding snapshot |
| GET | `/projects/{projectId}/structure-index` | Read the persisted Structure Index V2 |
| GET | `/projects/{projectId}/evolution-bridges?page=0&size=20` | Read bounded evidence-backed before/change/after bridge rows |

Structure Index V2 exposes bounded file/module data plus `symbols`, `definitions`, `references`, `importantNodes`, `functionalAreas`, `providerDiagnostics`, `metrics`, `coverage`, `unsupportedAreas`, `delta`, source revision, and index version. SCIP identity remains opaque; paths are repository-relative.

Evolution Bridge rows contain real before/after revisions, structure versions, meaningful change, affected area, epistemic status, confidence, source Fact IDs, source commit refs, changed relative paths, and evidence refs. The API never returns source text, full diffs, absolute paths, prompts, raw model responses, reasoning, keys, or Authorization.

All three GET paths are persisted reads. They do not scan the repository, run Git, invoke a model, create Project Facts, or refresh derived layers.

## Auth

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Create account |
| POST | `/auth/login` | Login and return JWT |
| GET | `/auth/me` | Current user profile |

### Register Request

```json
{
  "username": "xiaochuqing",
  "email": "user@example.com",
  "password": "local-dev-password"
}
```

### Login Response

```json
{
  "data": {
    "accessToken": "jwt-token",
    "user": {
      "id": "uuid",
      "username": "xiaochuqing",
      "email": "user@example.com"
    }
  },
  "message": "OK"
}
```

## Projects

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects` | List current user's projects |
| POST | `/projects` | Create project |
| GET | `/projects/{projectId}` | Get project detail |
| PUT | `/projects/{projectId}` | Update project |
| DELETE | `/projects/{projectId}` | Archive or delete project |

### Project Fields

```json
{
  "name": "InsightWrite 2.0",
  "description": "Full-stack AI English writing and learning product.",
  "status": "BUILDING",
  "techStack": ["Vue 3", "Spring Boot", "MySQL", "DeepSeek"],
  "repoUrl": "https://github.com/xiaochuqing-dev/insightwrite-2.0",
  "startDate": "2026-05-01",
  "endDate": null
}
```

## Tasks

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects/{projectId}/tasks` | List project tasks |
| POST | `/projects/{projectId}/tasks` | Create task |
| GET | `/tasks/{taskId}` | Get task detail |
| PUT | `/tasks/{taskId}` | Update task |
| PATCH | `/tasks/{taskId}/status` | Move task status |
| DELETE | `/tasks/{taskId}` | Delete task |

### Task Status

Allowed statuses:

- `BACKLOG`
- `TODO`
- `IN_PROGRESS`
- `REVIEW`
- `DONE`

Initial V1 allowed transitions:

| From | To |
| --- | --- |
| BACKLOG | TODO |
| TODO | IN_PROGRESS |
| IN_PROGRESS | REVIEW |
| REVIEW | DONE |
| REVIEW | IN_PROGRESS |
| DONE | REVIEW |

## Dev Logs

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects/{projectId}/dev-logs` | List project logs |
| POST | `/projects/{projectId}/dev-logs` | Create log |
| GET | `/dev-logs/{logId}` | Get log detail |
| PUT | `/dev-logs/{logId}` | Update log |
| DELETE | `/dev-logs/{logId}` | Delete log |

### Dev Log Fields

```json
{
  "date": "2026-06-04",
  "title": "Daily Dev Log",
  "completed": ["Improved README structure."],
  "bugsFixed": ["Fixed missing environment variable validation."],
  "decisions": ["Keep V1 local-first."],
  "problems": ["Need a clearer dashboard layout."],
  "nextSteps": ["Add architecture diagram."],
  "reflection": "The project should emphasize engineering clarity.",
  "rawMarkdown": "# Daily Dev Log..."
}
```

## Markdown Imports

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/imports/preview` | Parse Markdown and return preview |
| POST | `/imports/confirm` | Save parsed result as dev log |
| GET | `/projects/{projectId}/imports` | List import records |

### Preview Request

```json
{
  "projectId": "uuid",
  "markdown": "---\nproject: InsightWrite 2.0\n..."
}
```

### Preview Response

```json
{
  "data": {
    "frontMatter": {
      "project": "InsightWrite 2.0",
      "date": "2026-06-04",
      "type": "daily-log",
      "source": "codex",
      "relatedRepo": "xiaochuqing-dev/insightwrite-2.0"
    },
    "sections": {
      "completed": [],
      "bugsFixed": [],
      "decisions": [],
      "problems": [],
      "nextSteps": [],
      "reflection": ""
    },
    "warnings": []
  },
  "message": "OK"
}
```

## AI Outputs

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/projects/{projectId}/ai-outputs` | Generate AI output |
| GET | `/projects/{projectId}/ai-outputs` | List AI outputs |
| GET | `/ai-outputs/{outputId}` | Get AI output detail |
| POST | `/ai-outputs/{outputId}/regenerate` | Regenerate with confirmation |

### Output Types

- `WEEKLY_REPORT`
- `PROJECT_SUMMARY`
- `RESUME_BULLET`
- `README_SECTION`

### Generate Request

```json
{
  "type": "WEEKLY_REPORT",
  "fromDate": "2026-06-01",
  "toDate": "2026-06-07"
}
```

## V3.2 Project Intelligence Flow

These endpoints support the current evidence-to-growth loop.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/project-imports/zip` | Import a complete project zip and create or update the base project profile |
| PATCH | `/projects/{projectId}/memory/local-path` | Save the real local project folder path |
| POST | `/projects/{projectId}/agent-bridge/protocol` | Write or refresh `.projectflow` protocol files |
| POST | `/projects/{projectId}/agent-bridge/scan` | Scan `.projectflow/inbox` agent results into reviewable changes |
| POST | `/projects/{projectId}/scan` | Scan bound local Git evidence into work sessions |
| GET | `/projects/{projectId}/work-sessions` | List detected work sessions |
| POST | `/work-sessions/{sessionId}/evidence-bundles` | Create or update the evidence bundle for a work session |
| GET | `/projects/{projectId}/evidence-bundles` | List evidence bundles with lifecycle state |
| POST | `/evidence-bundles/{bundleId}/draft-changes` | Generate or update a structured project change from evidence |
| GET | `/projects/{projectId}/changes` | List structured project changes |
| PATCH | `/project-changes/{changeId}` | Edit a structured project change before acceptance |
| POST | `/project-changes/{changeId}/accept` | Accept a change into project memory and fact sources |
| POST | `/project-changes/{changeId}/ignore` | Remove a change from the review queue without deleting source evidence |
| GET | `/projects/{projectId}/fact-sources` | List field-level project memory sources |
| GET | `/projects/{projectId}/evolution-records` | List project growth records |
| POST | `/projects/{projectId}/context/sync` | Sync confirmed project context back to `.projectflow/context` |

### Evidence Bundle Response

```json
{
  "id": "uuid",
  "projectId": "uuid",
  "workSessionId": "uuid",
  "agentType": "CODEX",
  "taskIntent": "Improve project profile flow",
  "branchName": "main",
  "attributionConfidence": "medium",
  "changedFiles": 3,
  "addedLines": 120,
  "deletedLines": 12,
  "files": ["frontend/src/app/dashboard/page.tsx"],
  "objectiveEvidence": ["Git commit or worktree evidence"],
  "agentClaims": [],
  "sources": [],
  "status": "READY_FOR_CHANGE",
  "nextAction": "GENERATE_CHANGE",
  "changeId": null,
  "createdAt": "2026-06-21T00:00:00",
  "updatedAt": "2026-06-21T00:00:00"
}
```

Lifecycle values:

- `READY_FOR_CHANGE`: evidence exists and can generate a structured change.
- `CHANGE_DRAFTED`: a candidate change exists and should be reviewed.
- `CHANGE_ACCEPTED`: the related change has been accepted into project memory.
- `ARCHIVED`: the related change was ignored or no longer needs action.

`nextAction` is the UI routing hint: `GENERATE_CHANGE`, `REVIEW_CHANGE`, `VIEW_MEMORY`, or `NO_ACTION`.

### Structured Change Acceptance

Accepting a Project Change writes confirmed facts to `ProjectMemory`, records field-level `ProjectFactSource`, and makes the accepted content available to daily review, output generation, and context sync. Unaccepted evidence and candidate changes must not be treated as official project facts.

## Health

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Lightweight API health check |
| GET | `/actuator/health` | Spring Boot actuator health |

## V3.7.1 Understanding Diagnostics

The existing understanding routes and ownership checks are unchanged:

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/projects/{projectId}/understanding/refresh` | Create or reuse the persisted explicit refresh job |
| GET | `/projects/{projectId}/understanding` | Read the last persisted understanding snapshot only |
| GET | `/projects/{projectId}/structure-index` | Read the last persisted structure index only |
| GET | `/projects/{projectId}/evolution-bridges` | Read evidence-backed derived bridges only |

V3.7.1 adds compatible optional JSON fields inside the understanding snapshot:

- `sourceMap.diversityMetrics`: selected categories, quota drops, duplicate compression, category coverage, current/history counts and sample-cache hits.
- `analysisExecution`: requested, executed and reused capabilities, evidence, per-capability diagnostics, cache identity, duration and budget state.
- `contextPacking`: global/category character use, selected/dropped item counts, truncation reasons and complete-JSON validation.
- `historicalCoverage.breakdown`: seven coverage dimensions, bounded Git sample state and per-period counts/confidence.
- `analysisMetrics.inventoryFilesRead`, `inventoryCacheHits`, `sampleCacheHits`: current refresh I/O diagnostics.

Old V3.7 snapshot JSON may omit these fields and remains readable. The next explicit refresh safely reconstructs current derived diagnostics. No schema migration or bulk Fact rewrite is required.

## V3.7.2 Understanding Trust State

Routes and ownership rules remain unchanged. V3.7.2 adds only compatible trust fields:

- `analysisExecution.secondStageDecision.secondStageTriggered`
- `analysisExecution.secondStageDecision.triggerReasons`
- `analysisExecution.secondStageDecision.skippedReasons`
- `analysisExecution.secondStageDecision.evidenceIds`
- `finalSynthesisStatus`: `NOT_APPLICABLE`, `PENDING`, `SKIPPED_NO_HIGH_VALUE_EVIDENCE`, `SUCCEEDED` or `FAILED_DEGRADED`

These fields explain whether new evidence justified Final Synthesis and whether the current result is degraded. They are not accuracy/hallucination scores. Internal eval metrics have no controller, DTO, Snapshot field or UI route.

## V3.7.3 Long-running Understanding Contract

The refresh route and ownership check remain unchanged. Its request body is optional:

```json
{
  "deadlineMode": "AUTO",
  "maxAnalysisDurationSeconds": null,
  "qualityMode": "QUALITY_FIRST"
}
```

`deadlineMode` accepts `AUTO`, `FINITE` or `UNLIMITED`. FINITE uses `maxAnalysisDurationSeconds` with a 60-second minimum. AUTO and UNLIMITED have no ProjectFlow overall deadline; connection and Provider request timeout remain independently finite. Unknown values safely normalize to AUTO/QUALITY_FIRST.

`ProjectAnalysisJobResponse` adds `analysisDeadlineMode`, `qualityMode` and `overallDeadlineEnabled`. Existing clients that send no body and ignore the added fields remain compatible. The route still only creates/reuses a persisted job; GET routes never run scans, Git, tools or models.

Plan/Scout JSON adds compatible optional fields:

- `evidenceSourceAssessments[].informationGap`
- `evidenceSourceAssessments[].affectedDimensions`
- `semanticScout.toolRequests[]`: capability, information gap, expected value, target Evidence IDs and insufficiency reason
- `analysisPlan.eligibleCapabilities`
- `analysisPlan.eligibleViews`
- `analysisPlan.toolSelectionRationales`

They expose evidence reasoning and objective availability, not benchmark scores. Unknown Evidence, unavailable Capability/View, commands, parameters and absolute paths remain invalid.
