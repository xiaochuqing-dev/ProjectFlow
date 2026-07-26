# ADR: ProjectFlow-only Internal Model Evaluation

Status: Accepted

## Decision

Use test-only Java code under `backend/src/test` and a versioned JSON Ground Truth under `backend/src/test/resources/projectflow-eval`. Default JSON and Markdown run artifacts are written under `backend/target/projectflow-eval`, already ignored by Git.

The harness supports fixed observations and a real Provider through Model Gateway V2. It has no controller, entity, repository, migration, UI route or public API.

## Dataset boundary

The 18 cases cover empty, blank, strange document, script, frontend, backend, desktop, fullstack, monorepo, no Git, short/long history, stale/conflicting README, Agent result, token usage, ProjectFlow itself and a large repository. Each case records source, bounded Stage 1 context, optional independently supplied Tool Evidence, expected multi-label shapes, must-find evidence, must-not-claim statements, expected/forbidden tools, expected/forbidden views, unknowns, conflicts, history mode and deep-read targets.

This is a stage-specific human standard. It is not a universal benchmark.

## Recorded run data

Each normalized observation records case/model/protocol/prompt/code/project revision/run index, request/token/latency/retry metadata, tool plan, deep-read targets, evidence used, claims needing review, must-not violations, expected view matches, conflict labels, final/degradation status and optional dated cost.

It never records a key, Authorization, complete prompt, raw response, secret, chain-of-thought or private reasoning.

## Release thresholds

For the selected Provider, prompt version and dated dataset:

- failure rate at most 5%;
- Critical Evidence Recall at least 85%;
- Unsupported Claim Rate at most 5%;
- critical-case must-not-claim violations equal zero;
- Tool Selection Recall at least 80% and unnecessary-tool rate at most 15%;
- Dynamic View Recall at least 90%;
- key-output repeatability at least 80%;
- deep-read cases show measurable second-stage evidence, unsupported-claim or view gain;
- empty/blank remain zero-model and all logical flows remain within 0/1/2 stages;
- no confirmed secret or product-metric leakage;
- degradation, zero-model and forbidden-tool tests pass;
- manual review finds no release-blocking semantic violation.

These thresholds are release gates for this representative set, not marketing claims. A future dataset/prompt/model change must retain its own versioned result.

## Rejected alternatives

- A generic eval service or dashboard would create product drift.
- Persisting eval results in the user database would contaminate project memory.
- Saving raw responses would enlarge privacy and reasoning-retention risk.
- Adding promptfoo, DeepEval, Ragas or LangSmith as a dependency is unnecessary for the current fixed ProjectFlow task and would add runtime/tooling scope.
