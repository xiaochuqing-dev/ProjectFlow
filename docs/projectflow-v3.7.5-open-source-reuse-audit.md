# ProjectFlow V3.7.5 Open-source Reuse Audit

V3.7.5 applied reuse-before-reinvention before adding any component. No new runtime or test dependency was introduced.

| Problem | Existing/internal or platform solution | External alternative considered | License/security/integration | Decision |
| --- | --- | --- | --- | --- |
| Deterministic package revision | JDK `MessageDigest`, Jackson canonical DTO identity | Guava hashing, content-addressed libraries | Extra dependency adds no value | Reuse JDK/Jackson |
| Task-related Context ranking | Existing persisted facts, Evidence refs, Structure Index and bounded tokenization | Lucene/vector DB/embedding service | Larger index, new data store and model cost; unnecessary for bounded v2 | Small deterministic ranking over existing sources |
| Source ranges | Existing Structure Index occurrences and `LargeFileContentService` | New parser/LSP/source-map library | Would duplicate existing provider boundary | Reuse existing precise/fallback ranges |
| Changed-file validation | `LocalProjectPathGuard`, `LargeFileContentService`, `SensitiveContentRedactor` | New file watcher or agent runtime | Watcher/runtime is out of scope | Bounded on-demand re-read |
| Git verification | Existing `LocalCommandExecutor` with fixed argument lists | JGit | Current project already standardizes bounded Git CLI; JGit adds weight and a second semantic path | Reuse fixed Git commands |
| API validation | Jakarta Validation and existing `AppException` envelope | Custom validator framework | Existing stack is sufficient | Reuse Jakarta/Spring |
| Structured model output | Existing official OpenAI/Anthropic SDK adapters and Model Gateway repair | New schema/agent framework | Would fork retry, cancellation and fact semantics | Keep Gateway adapters |
| MCP delivery | Existing repository-local Python stdio adapter | New MCP framework/runtime | Current adapter is small, tested and dependency-free | Extend existing Hermes tool schema |
| Timeline authority metadata | Existing DTO/read models | New event-store or narrative engine | V3.8 narrative work is deferred | Add explicit status/authority fields only |
| Secret handling | Existing redactor plus CI committed-secret scan | New secret-scanning dependency | Existing behavioral and tracked-content gates cover changed inputs | Reuse and rerun current gates |

## License and attribution result

No third-party code was copied and no dependency version was added by V3.7.5. Existing Apache/MIT/BSD-compatible dependencies remain documented in `THIRD_PARTY_NOTICES.md`. No GPL/AGPL code or external project source was incorporated.

## Rejected expansion

Lucene, vector stores, workflow engines, Agent memory frameworks, parser platforms, watchers, JGit and new MCP runtimes were rejected because the current repository and Java/Python platforms already satisfy the bounded requirement with lower security and maintenance cost.
