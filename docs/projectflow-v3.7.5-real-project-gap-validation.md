# ProjectFlow V3.7.5 Real-project Gap Validation

## Selection boundary

The Existing Coverage Matrix already covers No-Git, unusual names, extensionless text, Chinese paths, large-file middle/tail, 80k-line code and oversized documents. Repeating those cases with real models would add no information. V3.7.5 therefore selected only three local projects with materially different evidence structures instead of filling the suggested six-to-eight count.

| Project | New evidence structure | Planned model requests / Token | Two models | Product E2E |
| --- | --- | --- | --- | --- |
| Agent mail bridge | code, human documentation, configuration, runtime data and sensitive metadata in one live project | 0 / 0 | No | No |
| Agent Eyes | Python desktop code plus configuration and generated/build-oriented material | 0 / 0 | No | No |
| Universal exam-prep skill | documentation, templates, JSON and one script; a non-traditional project without a conventional application architecture | 0 / 0 | No | No |

Success required bounded intake, structure fallback and Evidence Discovery; category diversity; no model call; no absolute path or content artifact; and a stable repeat fingerprint when the source did not change. These checks used `RepositoryIntakeBenchmarkTest` with an explicit local repository property.

## Actual observations

| Project | Result | Bounded observation | Added information |
| --- | --- | --- | --- |
| Agent mail bridge | Partial, source changed during scan | LARGE; 4,423 files; 266 source files; 944 documents; 80 Scout Evidence; 11 selected categories; no truncation; 0 model requests | ProjectFlow bounded the mixed repository, reused all 4,423 warm inventory entries and kept category coverage at 1.000. A live SQLite write-ahead log changed between the cold scan and repeat fingerprint, so the stable-revision assertion correctly failed. The result is volatile/partial and is not reported as a stable snapshot. No database content was copied into the report. |
| Agent Eyes | Passed | SMALL; 91 files; 53 source files; 28 documents; 44 Scout Evidence; 7 selected categories; no truncation; 0 model requests | Generated/build-oriented material did not crowd out source and document categories. The warm scan reused all 91 inventory entries and the repeat fingerprint matched. |
| Universal exam-prep skill | Passed | SMALL; 9 files; 1 source file; 7 documents; 8 Scout Evidence; 3 selected categories; no truncation; 0 model requests | A document/template-dominant skill remained a small mixed project. It was not forced into a backend/frontend/database template, and the repeat fingerprint matched. |

## Evaluation against the product contract

- Actual material was discovered without assuming Git, README, backend, database, capability or project phase.
- The two stable projects passed bounded cold/warm inventory and deterministic fingerprint checks.
- The live project exposed a currentness boundary instead of being silently declared stable.
- No project required a real model, so GLM/DeepSeek calls were not spent on evidence structures already decidable by engineering code.
- No importance, maturity, milestone or project phase was generated.
- No raw project content, database body, key, prompt, response, reasoning or absolute path is retained in this report.

The volatile-source result is not a V3.7.5 product blocker: the product already records revision/currentness and must revalidate before promotion. It is retained as a limitation and as evidence that real-project acceptance must distinguish source mutation from fingerprint algorithm failure.
