# Persistent Change Scan Job Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the dashboard change scan survive refresh, navigation, and backend restart by reusing the persisted analysis-job workflow.

**Architecture:** Extend `ProjectAnalysisJob` with a `WORK_SESSION_SCAN` type and serialized `WorkSessionScanResponse`. Add a new asynchronous scan endpoint while retaining the synchronous endpoint, then reuse the existing frontend job polling hook to restore progress and results.

**Tech Stack:** Java 17, Spring Boot, JPA, MockMvc, Next.js 16, React 19, TypeScript.

---

### Task 1: Lock down the asynchronous scan contract

**Files:**
- Modify: `backend/src/test/java/com/projectflow/WorkSessionScanControllerTest.java`

- [x] Add an integration test that creates a bound Git project, posts to `/api/projects/{projectId}/scan/jobs`, and asserts a persisted `WORK_SESSION_SCAN` job is returned.
- [x] Poll `/api/analysis-jobs/{jobId}` until terminal state and assert `SUCCEEDED`, a non-null `workSessionScanResult`, and persisted segments.
- [x] Reload `/api/projects/{projectId}/analysis-jobs` and assert the same result is recoverable after the initiating request.
- [x] Verify another authenticated user receives `404` for the job.
- [x] Run `mvn.cmd -q -Dtest=WorkSessionScanControllerTest#startsPersistentChangeScanJobAndRestoresItsResult test` and confirm it fails because the endpoint does not exist.

### Task 2: Persist and execute change scan jobs

**Files:**
- Modify: `backend/src/main/java/com/projectflow/entity/ProjectAnalysisJobType.java`
- Modify: `backend/src/main/java/com/projectflow/dto/V2ProjectDtos.java`
- Modify: `backend/src/main/java/com/projectflow/service/ProjectAnalysisJobService.java`
- Modify: `backend/src/main/java/com/projectflow/service/ProjectAnalysisJobRunner.java`
- Modify: `backend/src/main/java/com/projectflow/controller/WorkSessionScanController.java`

- [x] Add `WORK_SESSION_SCAN` and a nullable `workSessionScanResult` field to the existing job response.
- [x] Add `startWorkSessionScan`, using the existing active-job lookup so repeated clicks return one active task.
- [x] Execute `WorkSessionScanService.scan` from the async runner and serialize its result into the job record.
- [x] Deserialize scan results when listing or reading jobs; preserve project ownership checks and safe error messages.
- [x] Expose `POST /api/projects/{projectId}/scan/jobs` while keeping `POST /api/projects/{projectId}/scan` unchanged.
- [x] Re-run the focused test and confirm it passes.

### Task 3: Restore scan state on the dashboard

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/use-project-analysis-jobs.ts`
- Modify: `frontend/src/app/dashboard/page.tsx`

- [x] Extend the job type with `WORK_SESSION_SCAN` and `workSessionScanResult`.
- [x] Add the async scan request and an `enqueueWorkSessionScan` action to the existing polling hook.
- [x] Derive dashboard scanning state and the latest successful result from persisted jobs instead of component-local state.
- [x] On terminal transition, surface failures and refresh project context after success without starting duplicate requests.
- [x] Run `npm.cmd run build` and fix only errors caused by this change.

### Task 4: Verify and record the result

**Files:**
- Create: `.projectflow/agent-results/<timestamp>-persistent-change-scan/result.json`
- Optionally create: `.projectflow/agent-results/<timestamp>-persistent-change-scan/summary.md`

- [x] Run the focused backend regression test.
- [x] Run the full backend test suite with `mvn.cmd -q test`.
- [x] Run the frontend production build with `npm.cmd run build`.
- [x] Run `git diff --check` and inspect `git status --short` to preserve unrelated worktree changes.
- [x] Write the protocol result using repository-relative paths and actual verification outcomes.
