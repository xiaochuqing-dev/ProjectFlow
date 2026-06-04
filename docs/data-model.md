# Data Model

Primary IDs use UUIDs. Timestamps use UTC at the database layer and are displayed in the user's local timezone in the frontend.

## Entity Overview

```mermaid
erDiagram
    users ||--o{ projects : owns
    projects ||--o{ tasks : contains
    projects ||--o{ dev_logs : records
    projects ||--o{ import_records : has
    projects ||--o{ ai_outputs : generates
    dev_logs ||--o{ ai_outputs : may_source
```

## users

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary key |
| username | varchar(80) | Required |
| email | varchar(255) | Required, unique |
| password_hash | varchar(255) | BCrypt hash |
| created_at | timestamptz | Required |
| updated_at | timestamptz | Required |

## projects

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary key |
| user_id | UUID | FK to users |
| name | varchar(160) | Required |
| description | text | Optional |
| status | varchar(40) | PLANNING, BUILDING, PAUSED, COMPLETED, ARCHIVED |
| tech_stack | jsonb | Array of strings |
| repo_url | varchar(500) | Optional |
| start_date | date | Optional |
| end_date | date | Optional |
| created_at | timestamptz | Required |
| updated_at | timestamptz | Required |

## tasks

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary key |
| project_id | UUID | FK to projects |
| title | varchar(200) | Required |
| description | text | Optional |
| status | varchar(40) | BACKLOG, TODO, IN_PROGRESS, REVIEW, DONE |
| priority | varchar(20) | LOW, MEDIUM, HIGH |
| due_date | date | Optional |
| tags | jsonb | Array of strings |
| created_at | timestamptz | Required |
| updated_at | timestamptz | Required |

## dev_logs

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary key |
| project_id | UUID | FK to projects |
| log_date | date | Required |
| title | varchar(200) | Required |
| completed | jsonb | Array of strings |
| bugs_fixed | jsonb | Array of strings |
| decisions | jsonb | Array of strings |
| problems | jsonb | Array of strings |
| next_steps | jsonb | Array of strings |
| reflection | text | Optional |
| raw_markdown | text | Optional |
| source | varchar(80) | manual, codex, claude, gpt, imported |
| created_at | timestamptz | Required |
| updated_at | timestamptz | Required |

## import_records

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary key |
| project_id | UUID | FK to projects |
| dev_log_id | UUID | FK to dev_logs, nullable until confirm |
| source_type | varchar(40) | PASTE, FILE |
| filename | varchar(255) | Optional |
| status | varchar(40) | PREVIEWED, IMPORTED, FAILED |
| error_message | text | Safe parse error |
| raw_markdown_hash | varchar(128) | Used for duplicate detection |
| created_at | timestamptz | Required |

## ai_outputs

| Field | Type | Notes |
| --- | --- | --- |
| id | UUID | Primary key |
| project_id | UUID | FK to projects |
| dev_log_id | UUID | Optional FK to dev_logs |
| type | varchar(40) | WEEKLY_REPORT, PROJECT_SUMMARY, RESUME_BULLET, README_SECTION |
| status | varchar(40) | PENDING, RUNNING, SUCCEEDED, FAILED |
| content | text | Generated Markdown |
| model | varchar(120) | AI model or mock model name |
| prompt_version | varchar(40) | Internal prompt version |
| error_message | text | Safe error, no secrets |
| from_date | date | Optional source range |
| to_date | date | Optional source range |
| created_at | timestamptz | Required |
| updated_at | timestamptz | Required |

## Indexes

| Table | Index |
| --- | --- |
| users | unique email |
| projects | user_id, status |
| tasks | project_id, status |
| dev_logs | project_id, log_date desc |
| import_records | project_id, created_at desc |
| ai_outputs | project_id, type, created_at desc |

## Ownership Rule

All resource access is scoped through `projects.user_id`. A user can access a task, dev log, import record, or AI output only if the parent project belongs to that user.

