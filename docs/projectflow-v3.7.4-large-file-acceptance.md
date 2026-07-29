# ProjectFlow V3.7.4 Large-file Acceptance

## Scope

`LargeFileContentService` uses bounded Java 17 streaming and no new parser dependency. It records encoding/binary state, source hash, bytes, lines, headings, lexical symbols/markers, head/middle/tail and representative ranges, byte/line bounds, truncation, limitations and unread ranges. `ProjectEvidenceDiscoveryService` and `BoundedLocalAnalysisCapabilityProvider` reuse the same service.

## Generated evidence

The local archive contains six raw fixtures from 1.3 MB to 6.5 MB, including 80,000-line code, 80,004-line Markdown, a 90,000-line extensionless Chinese filename, oversized Agent result, JSON and YAML. The committed manifest hash is `eaae1174ca3b8d8e2fbfdc931b681aea8cdc41854a07bf11cb9e407492a061f3`. Raw giant files are not committed.

## Deterministic result

| Boundary | Result |
| --- | --- |
| 80,000-line code Content Map | PASS |
| 80,000-line Markdown/Text | PASS |
| Head/middle/tail fact locations | PASS |
| Old head plus explicit tail revision | PASS |
| Heading/symbol/marker ranges | PASS |
| Multiple ranges and duplicate suppression | PASS |
| Extensionless/weird-name content | PASS |
| Large structured text bounded read | PASS |
| Tail revision changes source identity | PASS |
| Cross-chunk merge retains Evidence refs | PASS |
| Unread/partial ranges disclosed | PASS |
| Secret redaction before prompt text | PASS |

The generator, fixture specification, hashes, sizes, line counts and expected locations are committed. Sanitized model observations and final recall values are recorded only after the formal two-model and Holdout runs.
