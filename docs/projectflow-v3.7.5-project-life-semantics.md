# ProjectFlow V3.7.5 Project-life Semantics

V3.7.5 prepares stable semantics for later project-life reconstruction. It does not build the final narrative or GUI.

## Source-backed event contract

An event may expose source identity, source type, project ID, event timestamp, source/project Revision, current or historical scope, Evidence refs, objective category, replacement/invalidation relation and Evidence gaps. Missing fields remain unknown.

Raw events are preserved. They may be grouped by a real source or batch and folded for display, but a model score or summary cannot delete them. Every derived summary must resolve back to its source facts/events.

## Authority boundaries

| Material | Status/authority |
| --- | --- |
| Source-backed objective event | `OBSERVED` or independently `VERIFIED` |
| User milestone or phase | `DECLARED` |
| Model phase, highlight or importance | `INFERRED`, non-authoritative |
| Timeline model summary | `INFERRED`, `NON_AUTHORITATIVE` |
| Agent completion or test report | `PROCESS_EVIDENCE` until independent validation |
| Conflicting event sources | `CONFLICTED` |
| Missing history link | `UNKNOWN` or Evidence gap |

There is no automatic authoritative importance, key event, project phase, maturity, milestone, success judgment, historical reason, deprecation or technical-debt label.

## Timeline behavior

- Sort by occurrence/effective time, never by analysis or projection time.
- Keep original events even when the default display folds them.
- Aggregate only by real source, batch or explicit user choice.
- Preserve current/historical distinction and replacement/invalidation links.
- Show an empty view when Evidence is absent.
- Let users pin their own items without changing fact identity.
- Keep summaries replaceable and separate from ProjectFact.

Current Timeline and Gateway DTOs therefore label model summaries as `INFERRED` and `NON_AUTHORITATIVE`. Final V3.8 views must consume these fields rather than infer authority from display position.
