# ProjectFlow V3.3.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make change analysis specific, stable, explainable, GitHub-aware, and capable of producing structured whole-project capability cards.

**Architecture:** Extend the existing scan, segment, sediment, analysis-job, and API boundaries. Persist diagnostics and capability cards as structured entities; keep GitHub optional and evidence-bound model output validated by backend rules.

**Tech Stack:** Java 17/Spring Boot/JPA, PostgreSQL/H2, TypeScript/React/Next.js, local Git and optional GitHub CLI.

---

### Task 1: Segment quality and evidence-rich atoms

**Files:** `DevelopmentSegmentationService`, `ModelSegmentEnricher`, validators and focused tests.

- [ ] Add failing tests for directory-level summaries and required 3-6 concrete changes.
- [ ] Add diff hints and source metadata to atoms and model input.
- [ ] Implement local human-readable summaries and backend quality classification.
- [ ] Run focused tests.

### Task 2: Stable scan diagnostics and GitHub enrichment

**Files:** scan entities/DTOs/repositories/services, `GitHubCliService`, scan tests.

- [ ] Add failing tests for remote relation, timeout degradation, fingerprint reuse, and commit URLs.
- [ ] Persist fingerprint, model/fallback state, worktree flag, remote state, and timing diagnostics.
- [ ] Reuse matching batches and expose diagnostics in scan responses.
- [ ] Run focused tests.

### Task 3: Structured project capability analysis

**Files:** capability entity/repository/service/controller/DTOs and analysis job integration.

- [ ] Add failing integration tests for whole-project generation and single-card confirmation.
- [ ] Generate 3-8 evidence-linked candidates from confirmed project sources with model/local fallback.
- [ ] Add list, analyze, update/confirm/ignore endpoints.
- [ ] Run focused tests.

### Task 4: Consistent review and suggestion actions

**Files:** `ProjectSedimentService`, project-change detail route, evidence route, API types.

- [ ] Add failing tests for inferred merge/evidence actions.
- [ ] Implement conservative suggestion inference.
- [ ] Replace legacy detail acceptance with the four-action flow and target selection.
- [ ] Separate evidence categories and remove bulk-new primary action.

### Task 5: Frontend workflow

**Files:** dashboard pending panel, capabilities page, tasks/detail pages, API client.

- [ ] Display GitHub status, scan diagnostics, concrete changes, quality and fallback reasons.
- [ ] Replace per-string interpretation with `[分析项目能力]` and independent collapsible cards.
- [ ] Keep legacy fields in a collapsed compatibility section.
- [ ] Build frontend.

### Task 6: Release artifacts and verification

**Files:** README, AGENTS, `.projectflow` protocol/context/result, launchers if needed.

- [ ] Update V3.3.2 documentation and Agent result requirements.
- [ ] Check launch scripts and migration/startup compatibility.
- [ ] Run full backend tests and frontend build.
- [ ] Review diff/status, commit, merge to master, verify merged tree, and push origin.
