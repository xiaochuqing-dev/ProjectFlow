# Dev Log Markdown Format

ProjectFlow V1 imports structured Markdown logs through paste input. File upload can be added later without changing the parser contract.

## Required Format

```markdown
---
project: InsightWrite 2.0
date: 2026-06-04
type: daily-log
source: codex
related_repo: xiaochuqing-dev/insightwrite-2.0
---

# Daily Dev Log

## Completed
- Improved GitHub README structure.
- Added product poster to documentation.

## Bugs Fixed
- Fixed missing environment variable validation.

## Decisions
- Keep the project as an open-source portfolio instead of public SaaS deployment.

## Problems
- AI API cost makes public deployment unsuitable for V1.

## Next Steps
- Add architecture diagram.
- Improve screenshot presentation.

## Reflection
- The project should present both product thinking and engineering quality.
```

## Front Matter

| Field | Required | Notes |
| --- | --- | --- |
| project | Yes | Must match or be mapped to a selected project |
| date | Yes | ISO date, `YYYY-MM-DD` |
| type | Yes | V1 supports `daily-log` |
| source | No | codex, claude, gpt, manual |
| related_repo | No | GitHub owner/repo or URL |

## Sections

The parser recognizes these headings:

- `## Completed`
- `## Bugs Fixed`
- `## Decisions`
- `## Problems`
- `## Next Steps`
- `## Reflection`

Section names are case-insensitive in V1, but the exported template should use the exact names above.

## Parsing Rules

- Front matter must appear at the top of the Markdown.
- Unknown front matter fields are preserved only in preview metadata and are not required for V1 persistence.
- Missing required front matter fields produce a preview error.
- Missing sections produce empty arrays or an empty reflection string.
- List sections parse `- item` lines into arrays.
- Reflection can be either plain text or bullet items joined as Markdown text.
- The original Markdown is stored in `dev_logs.raw_markdown`.

## Preview Behavior

The import preview must show:

- Matched project.
- Parsed date and source.
- Parsed section content.
- Warnings for empty optional sections.
- Blocking errors for invalid date, missing project, or unsupported type.

## Confirm Behavior

The confirm step saves:

- A `dev_logs` row.
- An `import_records` row with status `IMPORTED`.
- The hash of the raw Markdown for duplicate detection.

If saving fails, the system returns a safe error and does not create partial records.

