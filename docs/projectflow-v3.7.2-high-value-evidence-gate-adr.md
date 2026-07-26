# ADR: High-value Evidence Gate

Status: Accepted

## Problem

V3.7.1 treated every non-empty validated tool prompt as high-value. A short message, duplicate output or clean-worktree metadata could spend the second and final logical model call.

## Decision

`HighValueEvidenceGate` evaluates validated tool evidence after Provider execution. Its result is:

- `secondStageTriggered`
- `triggerReasons`
- `skippedReasons`
- `evidenceIds`

A trigger requires the two-call plan budget plus at least one substantive, non-duplicate item in one of these categories:

- deep document/manifest/Agent-result content;
- Git history or Tag anchor;
- non-clean worktree change detail;
- conflict/currentness evidence.

It skips missing evidence, content below the bounded semantic minimum, duplicate normalized content, content identical to a Stage 1 Source Map summary, clean-worktree metadata, unrecognized/process-metadata capability and plans capped at one call.

## Boundary

The gate is deliberately deterministic and auditable. It does not invent a model score or claim semantic equivalence. False negatives leave Stage 1 usable; false positives remain bounded by two logical calls. Future policy changes require a result-version change and tests.

## Verification

Tests cover substantive deep content, duplicate output, metadata, process metadata, model budget and the end-to-end second-stage decision.
