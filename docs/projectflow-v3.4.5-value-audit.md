# ProjectFlow V3.4.5 Value Audit

## Audit boundary and evidence

The audit used a byte-for-byte copy of `.projectflow/local-data/projectflow.mv.db`; the audit process never opened the source H2 file. Before any read, source and copy were both 6,270,976 bytes with SHA-256 `489D059BB2B9D7BCB8133BF47E5FE593F9A39994B35A8C502FB2E43829436E0D`. It reviewed all 68 facts, all 18 active capabilities, all 22 evolutions, the eight available period/lifecycle summaries and 32 themes. The database contains only June and July 2026 facts, so the audit covered both available months plus lifecycle instead of inventing a third month.

The audit also exercised five bounded Gateway/Hermes question shapes: recent change, when, why, capability evidence and fact trace. The CORE Obsidian projection review covered Overview, both available monthly Timeline/Fact Index groups, navigation indexes and more than three capability notes. Legacy sediment and capability-card tables were read only.

## Current data

- 68 ProjectFacts across 16 batches, 216 commit references and 90 Agent-result references; 61 are recorded and seven need attention.
- Fact occurrence range: 2026-06-04 through 2026-07-18.
- Origins: 37 legacy segment migrations, 19 history backfills, five incremental records, plus attention rows from those paths.
- 18 active ProjectCapabilities, 22 Evolutions, eight Timeline summaries, 32 Timeline themes, six legacy sediments and 13 legacy capability cards.

## Findings by layer

ProjectFact is the strongest layer. V3.4.0 and later facts usually explain an observed behavior, why it matters, the user/developer-visible result and evidence boundary. Early migrated facts often collapse to generic titles such as “根据提交记录整理的变更” and summaries such as “这一组变化围绕 backend 展开”. Those rows remain traceable but add little beyond a commit summary. Seven attention facts correctly expose uncertain evidence instead of presenting it as certainty.

Timeline is useful for “when did this change and what else changed in the same period”. Complete-period coverage and stable fact IDs add more than a Git log. Its weak July themes repeat generic migrated facts and the fallback “其他项目变化”; this is source-quality propagation, not a reason to turn Timeline into a second fact source.

All active capabilities were checked. Durable IDs, evidence links and Evolutions are valuable. Several capabilities are too broad or promotional: one combines model reliability and launcher behavior, while a legacy confirmation workflow still reads like an active product ability. Maturity remains explainable, but current wording can overstate the evidence.

The 22 Evolutions usually preserve a useful “formed/enhanced/merged” history and fact linkage. Weak rows mirror weak source facts or describe evidence accumulation without a clear user-visible capability delta. They should be improved through future reconciliation, never rewritten as historical fact.

Gateway/Hermes answered the five typical question shapes faster than manually locating commits because it joins facts, time, capabilities and bounded trace links under ownership checks. It is lexical retrieval, not semantic truth synthesis: “why” quality cannot exceed stored wording.

CORE Obsidian is worth keeping as a curated reading surface. Overview, monthly Timeline, long-lived Capability notes and Fact Indexes provide useful navigation without one file per fact. It also faithfully exposes weak wording, confirming that projection is a consumer, not a cleanup database.

## Replacement and unique-value assessment

GitHub already owns commit, diff, branch, PR, issue and raw file history. Repeating those objects or generating a shallow commit summary is removable duplication. GitHub plus Obsidian plus an agent can also replace ad-hoc notes, manually copied summaries and one-off Q&A, so ProjectFlow should not compete on those surfaces.

The non-redundant core is persistent, evidence-backed interpretation across sessions: stable facts with occurrence time and trace; complete-period Timeline derivation; durable capability identity and evolution; bounded read semantics shared by Hermes and Obsidian; and automatic continuity when the developer writes no notes. Git remains objective evidence, the model remains an interpretation engine, and ProjectFlow owns durable project intelligence.

## Classification

KEEP_CORE: ProjectFact, attention semantics, complete-period Timeline, stable ProjectCapability/Evolution identities, Evidence Trace, bounded Project Memory Gateway, Hermes consumption and curated CORE Obsidian projection.

IMPROVE: future fact wording, capability scope, evolution delta quality, lexical ranking and evidence-currentness. These improvements must be evidence-preserving.

LEGACY_COMPAT: DevelopmentSegment migration source, ProjectChange/SedimentAction/ProjectSediment reads, ProjectCapabilityCard archive and old DevLog/Daily Review links. They must not re-enter the active fact chain.

REMOVE_OR_HIDE: generic duplicate summaries in primary surfaces, legacy candidate cards presented as current capability, and repeated dashboard summaries. No data was deleted in V3.4.5 because the audit did not prove any stored legacy type safe to erase.

NEEDS_FOLLOWUP: evidence reachability after revert/rebase/rewrite, capability currentness, explicit weak-fact quality markers and non-destructive capability reconciliation.

## Next-stage recommendation

V3.4.6 may implement Automatic Memory Maintenance only through the stable Analyze → Fact → derived-memory boundaries. It must compare current evidence reachability without deleting historical facts, pause cursor advancement on rewrite uncertainty and keep model calls bounded and observable.
