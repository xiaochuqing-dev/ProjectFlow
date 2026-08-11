# V3.8.5 Targeted Reuse Decisions

No new external architecture research or dependency was required. The change reuses ModelGatewayService, the existing prompt builder, stable history window/checkpoint execution, corrected presentation views and the standard Java library.

Two focused boundaries were added because existing code did not provide them: one converts technical source subjects into a bounded human display concept, and one validates narrative entailment against engineering-owned Evidence state. Protocol differences remain in existing adapters and workflow configuration; no GLM or DeepSeek business rule was added.

Raw paths, technical details and commit summaries were removed from the wording payload instead of adding another sanitizer layer. The existing deterministic fallback and one repair attempt were retained. No runtime dependency, parser, Provider SDK, schema migration, Tag or Release was added.

After the first Round 2 candidate exposed an indexed placeholder, the affected-only rerun reused the existing real scenario evaluator and correction path with a declared `correction` scope. It ran a bounded 64-Story/two-window fixture for both Providers, while the previously qualified 11-scenario, Dogfood and non-code baselines remained separate evidence. The resulting artifact records its scope and cannot be misreported as a complete rerun.
