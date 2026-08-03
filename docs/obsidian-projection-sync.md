# Obsidian Projection and Sync

## V3.8.0 real workflow

CORE now makes `项目概览` and `项目历程` the primary navigation. It adds bounded chapter, story and evolution-thread notes while retaining existing Timeline, Fact and Capability notes for compatibility. Existing managed roots are migrated deterministically: old notes are not deleted, stable metadata and redirects remain valid, and user frontmatter plus content outside the managed block is preserved.

Level 0 requires no plugin. Notes include correctly encoded official `obsidian://open?vault=...&file=...` URIs and stable reverse links to the local ProjectFlow history preview. A user move or rename is reconciled through stable entity metadata and refreshes the URI without replacing authored content.

Level 1 optionally emits Advanced URI links for heading/block/workspace targeting. When disabled or unavailable, the official URI remains usable. Level 2 Local REST/MCP and Level 3 Dataview/Bases remain optional, loopback/minimum-permission designs and are not required by the projection. ProjectFlow does not inject DataviewJS or expose a Vault to the public network.

The real temporary-Vault tests cover URI encoding, official fallback, reverse links, user moves, no-op incremental sync, conflicts, manifest recovery, atomic writes, traversal/symlink protection and legacy Capability compatibility.

## Purpose

Obsidian is a curated long-term knowledge projection of ProjectFlow, not a database mirror and not a source of truth. The projection reads Project History, Snapshot, Timeline, Capability, Evolution and Fact semantics only through Project Memory Gateway. It never calls a model and never writes back ProjectFact, History, Timeline, Capability or Evolution.

V3.7.5 does not change the projection storage format. Only `OBSERVED` and independently validated `VERIFIED` are strong facts. `DECLARED`, `INFERRED`, `CONFLICTED`, `UNKNOWN` and `PROCESS_EVIDENCE` retain their authority metadata; Timeline model summaries are `INFERRED` and `NON_AUTHORITATIVE`. Agent candidates, work-result bodies, Context Package task text, model Token/latency diagnostics and reasoning-control settings are not projected as facts or quality judgments.

## Repository-local command

Start ProjectFlow locally, create or select an existing test Vault, then run the PowerShell wrapper from the repository root:

```powershell
.\run-projectflow-obsidian.ps1 validate --vault <VAULT_PATH> --managed-root ProjectFlow
.\run-projectflow-obsidian.ps1 dry-run --vault <VAULT_PATH> --managed-root ProjectFlow --project-id <PROJECT_UUID>
.\run-projectflow-obsidian.ps1 sync --vault <VAULT_PATH> --managed-root ProjectFlow --project-id <PROJECT_UUID>
.\run-projectflow-obsidian.ps1 status --vault <VAULT_PATH> --managed-root ProjectFlow --project-id <PROJECT_UUID>
```

The default backend is `http://127.0.0.1:8080`. A different loopback endpoint can be supplied with `--base-url`. V3.4.4 rejects non-loopback ProjectFlow URLs. If application authentication is enabled, provide `PROJECTFLOW_ACCESS_TOKEN` only as a process environment variable; never place it in the Vault, manifest or command examples.

`validate` checks that the Vault exists, the managed-root path is contained and writable, and optionally verifies project scope. `dry-run` reads the Gateway and returns the deterministic plan without creating the managed root. `status` reports the same pending plan plus last successful manifest state. `sync` executes one plan. Automatic watchers and frontend configuration are intentionally absent.

## Default folder structure

```text
ProjectFlow/
  项目概览.md
  项目历程/
    YYYY-MM.md
  项目能力/
    <canonical-name>--<stable-id>.md
  项目事实/
    YYYY-MM.md
  索引/
    能力索引.md
    时间索引.md
    事实索引.md
  .projectflow-manifest.json
```

The three profiles are:

- `CORE`, the default: Overview, monthly Timeline, Capability notes, monthly Fact indexes and three navigation indexes. It does not create one file per fact.
- `EXTENDED`: CORE plus facts referenced by capability evolution or marked Needs Attention.
- `FULL_FACTS`: CORE plus an individual note for every fact. It must be selected explicitly and can create many files.

Switching to a smaller profile archives removed projection entries in the manifest without deleting their notes. Subsequent runs remain no-op.

## Note semantics

Overview is the entry point: project identity and positioning, factual/history coverage, earliest and latest real change, current capabilities, recent evolution and changes, lifecycle summary, attention and navigation.

Timeline notes are assigned from `eventAt`/`occurredAt`, never analysis, record or sync time. They contain deterministic month statistics, the existing derived summary and themes, principal facts, capability changes, evidence references and history-coverage warnings.

Capability notes keep the stable capability ID, aliases, current meaning, problem and long-term value, deterministic maturity and reason, formation/enhancement dates, current version and counts, chronological evolutions, related months, representative Fact Trace IDs and optional reusable expressions. A merge retains the old note, history and redirect target.

Monthly Fact indexes provide title, occurrence time, compact summary, status, stable Fact ID, source batch, related capabilities and a Fact Trace reference. Raw diffs, source files, Agent result bodies, model prompts/responses/reasoning, job state, internal fingerprints, credentials and absolute paths are not projected.

## Stable metadata and managed content

Every managed note contains:

```yaml
projectflow_managed: true
projectflow_project_id: <PROJECT_UUID>
entity_type: <TYPE>
entity_id: <STABLE_ID>
source_version: <SOURCE_VERSION>
content_hash: <SHA256>
generated_at: <UTC_TIME>
source_updated_at: <SOURCE_TIME>
projection_version: "1"
```

Timeline notes also contain period key/start/end and Timeline zone. Capability notes also contain capability ID/status/version and an optional merge redirect target.

Only the following region is generated:

```text
<!-- PROJECTFLOW:BEGIN -->
generated content
<!-- PROJECTFLOW:END -->
```

Unknown user frontmatter and all authored text outside this region are retained; a managed update may normalize line endings while preserving the text. New notes include a `我的笔记` area after the generated block.

## Incremental plan and manifest

The plan counts `CREATED`, `UPDATED`, `UNCHANGED`, `REDIRECTED`, `ARCHIVED`, `CONFLICT` and `ERROR`. Entity identity, source version, managed-content hash, projection version and `.projectflow-manifest.json` determine each action. An unchanged run performs zero file writes.

The manifest records project/profile, entity paths and types, source versions, managed hashes, projection version, sync generation, last sync, redirects and conflicts. It lives only inside the managed root, is written atomically, and is backed up before replacement. It is not authoritative: if corrupt, ProjectFlow reconstructs it from Gateway data and valid managed-note metadata without rewriting unchanged notes.

Each update writes a unique temporary file, flushes and fsyncs it, then uses an atomic replace. A crash can leave at most a recognizable temporary file, which the next run removes before rebuilding the deterministic plan. ProjectFlow never deletes and recreates the managed tree.

## Rename, move, merge and conflict behavior

Stable entity metadata discovers a user-moved managed note and reconciles the manifest. Navigation notes are updated only where links changed. A capability name change retains its established path. Sanitized names include a stable-ID suffix, preventing Unicode, reserved-name and case-insensitive collisions.

Capability merge writes the old note as a redirect while preserving its identity and evolution history. Source disappearance or a profile downgrade creates a non-destructive archived manifest entry.

ProjectFlow refuses overwrite when a managed block hash changed, markers are missing/doubled, project/entity identity differs, or duplicate notes claim one entity. The original file remains intact and the manifest plus `.projectflow-conflicts.json` report the reason. After the user resolves the condition, a later sync clears the active conflict report.

## Path safety and recovery

The Vault must already exist. The managed root must be a safe relative path under it. Absolute paths, `..`, symlink or Windows junction traversal, reparse-point targets, reserved device names and illegal filename characters are rejected or sanitized. The tool never searches for a Vault, writes outside the configured managed root, recursively clears a directory, or changes operating-system/global configuration.

`OBSIDIAN_VAULT_MISSING`, `OBSIDIAN_PATH_ESCAPE`, `OBSIDIAN_SYMLINK_ESCAPE`, `OBSIDIAN_MANAGED_MARKERS_INVALID`, `MANAGED_BLOCK_EDITED` and `OBSIDIAN_WRITE_FAILED` identify the principal failure classes. Backend/network and result-bound failures use the same machine-readable Gateway errors as the Hermes adapter. Correct the path or conflict, keep the user file, then rerun `dry-run` before `sync`.

## Verification

The repository test runs a real temporary Vault and real CLI/HTTP Gateway boundary. It covers first/no-op/incremental sync, 7/17 occurrence analyzed 8/20, user content, conflicts, move/rename/merge, filename/path controls, interrupted and manifest recovery, profile archive behavior, and 5000 facts over 36 months with 100 capabilities and 1000 evolutions. Actual release measurements and environment-specific H2/PostgreSQL/CI status are recorded in the V3.4.4 implementation report.
