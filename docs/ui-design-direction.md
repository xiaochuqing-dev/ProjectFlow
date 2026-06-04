# UI Design Direction

## Reference Image Reading

Two local UI references were reviewed:

- Reference image 1: a professional desktop admin tool with a persistent left sidebar, page title, horizontal tab group, structured form content, and a right-side summary panel.
- Reference image 2: a modern AI UI kit dashboard with a persistent left sidebar, top search and category navigation, spacious card grid, light background, blue accent, and clear icon-led navigation.

## Design Position

ProjectFlow should look like a focused developer operations workspace:

- Professional and restrained.
- Light theme first.
- High clarity over decoration.
- Dense enough for repeated daily use.
- More Linear, Notion, GitHub, and admin console than landing page.
- Chinese-first product interface, with English technical terms used where they improve clarity.
- GitHub and portfolio-facing copy remains English-first.

## Login Background Direction

The local login background image is a dark blue ProjectFlow hero visual with a large left-side product signal and a cleaner right-side area. The login page should use the image as the full-screen background and place a coordinated professional card on the right side:

- Use a translucent dark card with a subtle blue border and blur.
- Keep form labels and helper copy in Chinese.
- Match the card to the background's blue, cyan, and white palette.
- Avoid covering the product title and dashboard visual on the left.
- Keep the login card compact, serious, and tool-like.

## Layout Rules

- Authenticated pages always show the global left sidebar.
- The left sidebar owns product-level navigation:
  - Overview
  - Projects
  - Tasks
  - Dev Logs
  - Imports
  - AI Outputs
  - Settings
- Pages may add a top navigation bar when scoped navigation is useful.
- Top navigation can contain page tabs, filters, search, date range controls, and primary actions.
- Some simple pages can omit page-level top navigation.
- Avoid nested cards. Use cards only for repeated items, modals, and framed tools.
- Keep content areas full-width with constrained inner grids where needed.

## Page-Specific Structure

| Page | Left Sidebar | Page Top Navigation | Notes |
| --- | --- | --- | --- |
| Dashboard | Required | Optional summary filters | Project stats, recent logs, pending tasks |
| Project List | Required | Search, status filter, create button | Card or table hybrid |
| Project Detail | Required | Tabs: Overview, Tasks, Logs, AI Outputs | Strong candidate for page-level navigation |
| Task Board | Required | Project selector, priority filter, create task | Kanban columns with controlled density |
| Dev Logs | Required | Project selector, date filter, import action | Timeline and structured detail |
| Markdown Import | Required | Minimal or none | Paste editor, parser preview, confirmation |
| AI Outputs | Required | Output type tabs | Generated reports and export actions |
| Settings | Required | Section tabs if needed | Account and local preferences |

## Visual System

- Primary accent: clear blue, used for selected nav states and primary actions.
- Supporting colors: neutral grays, subtle borders, white surfaces, restrained status colors.
- Status colors:
  - Backlog: gray
  - Todo: blue
  - In Progress: amber
  - Review: violet or indigo
  - Done: green
- Typography should be compact and readable. Avoid oversized hero-style text inside the app.
- Icon buttons should use lucide icons when the frontend is implemented.
- Buttons should have clear hierarchy: primary, secondary, ghost, danger.
- Motion should be subtle: hover states, selected states, panel transitions, no decorative animation overload.

## Component Priorities

V1 needs these UI components early:

- App sidebar.
- Page header with optional tabs/actions.
- Project card and project table row.
- Kanban column and task card.
- Structured dev log section block.
- Markdown paste editor and parsed preview.
- AI output panel with copy/download actions.
- Empty states and error states.

## What To Avoid

- Marketing landing-page composition inside the app.
- Purple-heavy gradient themes.
- Decorative blobs or large background ornaments.
- Chat-first AI layout.
- Overly playful or toy-like styling.
- Text that explains how the UI works inside the app.
