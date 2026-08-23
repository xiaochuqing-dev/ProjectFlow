# V3.8.5 Human Narrative Contract

The first reading layer must tell an ordinary reader what changed, to what understandable object, and what evidence-supported state now exists. Paths, class names, file names, SHAs, Evidence IDs, internal slugs and truncated fixture tokens remain in engineering drill-down only.

For a Story, the model-owned output is limited to storyId, humanTitle, oneSentenceSummary, beforeWording, changeWording, afterWording, reason, reasonEvidenceRefs and unknownWording. Legacy unknowns are accepted only as an input compatibility field. The engineering layer owns Story identity, claim state, role graph, event and Evidence membership, time, transition, conflicts, currentness, later outcome and correction authority.

humanTitle states action, understandable object and result. oneSentenceSummary adds scope or impact instead of restating the title. Before describes only the earlier state, Change only the action in this stage, and After only the resulting state. These five fields must be complete and materially distinct. A Provider pair such as “编写某功能代码” plus “涵盖代码创建与修改” lacks an explicit result; Story v12 retains the deterministic pair instead and exposes the fallback status/count for audit.

The prompt receives a bounded human display concept and a claim-level entailment envelope. Direct support is summarized separately from indirect context; indirect context is explicitly marked as unable to promote the Claim. A bounded, redacted reason context can accompany eligible reason references, but it cannot authorize raw tokens in the first reading layer. Affected paths, technical details, commit summaries, role links and general Evidence lists are not serialized into the wording request. Absolute paths are omitted rather than replaced with a model-visible placeholder.

A broad engineering area can be described only as an observed area or skeleton change. It cannot be narrated as an implemented feature merely because implementation files exist somewhere in that area. A precise feature subject may use IMPLEMENTED wording only when its directly attributed implementation Evidence matches the same subject and action.

Explicit USER_DECLARED_PRESENTATION wording remains auditable and reversible and is not silently overwritten by automatic reconstruction. It never changes factual authority or Claim state, and correction validation rejects wording that promotes the frozen state.
