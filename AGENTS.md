# ProjectFlow Local Rules

For substantial ProjectFlow work, read `PROJECT_CONTEXT.md` first, then inspect only the task-relevant docs and source files.

Keep changes aligned with the current direction: ProjectFlow is a developer workbench for project understanding, agent result review, project profile maintenance, daily review, and asset output. Do not drift it toward a generic Kanban/admin app.

## Ponytail-Inspired Redundancy Control

Reference: `DietrichGebert/ponytail` on GitHub. Use this as an instruction pattern, not as a project dependency.

Before adding code, stop at the first rule that works:

1. Does this need to exist at all? If not, skip it.
2. Can existing project code, standard library, browser/native platform, or installed dependency do it? Reuse that.
3. Can the change be one focused line or one focused helper? Prefer that over new abstractions.
4. Only then write the smallest implementation that satisfies the current requirement.

For ProjectFlow specifically:

- Prefer deleting or consolidating duplicated cards, DTO mappings, API wrappers, and page-local helpers before adding new ones.
- Do not create new services, components, hooks, entities, or dependencies unless existing boundaries cannot cleanly handle the requirement.
- Keep detailed information in focused detail pages; do not duplicate the same summary across dashboard, project profile, tasks, and outputs.
- Mark intentional shortcuts with a `ponytail:` comment only when there is a real ceiling and a clear upgrade path.
- This rule never overrides security, ownership checks, trust-boundary validation, data-loss prevention, accessibility, model-failure fallback, or explicit user requirements.
- Non-trivial new logic still needs the smallest useful test or runnable check.
