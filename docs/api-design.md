# API Design

Base path: `/api`

All protected endpoints require:

```http
Authorization: Bearer <access-token>
```

## Response Shape

Successful responses:

```json
{
  "data": {},
  "message": "OK"
}
```

Error responses:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": []
  }
}
```

## Auth

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Create account |
| POST | `/auth/login` | Login and return JWT |
| GET | `/auth/me` | Current user profile |

### Register Request

```json
{
  "username": "xiaochuqing",
  "email": "user@example.com",
  "password": "local-dev-password"
}
```

### Login Response

```json
{
  "data": {
    "accessToken": "jwt-token",
    "user": {
      "id": "uuid",
      "username": "xiaochuqing",
      "email": "user@example.com"
    }
  },
  "message": "OK"
}
```

## Projects

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects` | List current user's projects |
| POST | `/projects` | Create project |
| GET | `/projects/{projectId}` | Get project detail |
| PUT | `/projects/{projectId}` | Update project |
| DELETE | `/projects/{projectId}` | Archive or delete project |

### Project Fields

```json
{
  "name": "InsightWrite 2.0",
  "description": "Full-stack AI English writing and learning product.",
  "status": "BUILDING",
  "techStack": ["Vue 3", "Spring Boot", "MySQL", "DeepSeek"],
  "repoUrl": "https://github.com/xiaochuqing-dev/insightwrite-2.0",
  "startDate": "2026-05-01",
  "endDate": null
}
```

## Tasks

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects/{projectId}/tasks` | List project tasks |
| POST | `/projects/{projectId}/tasks` | Create task |
| GET | `/tasks/{taskId}` | Get task detail |
| PUT | `/tasks/{taskId}` | Update task |
| PATCH | `/tasks/{taskId}/status` | Move task status |
| DELETE | `/tasks/{taskId}` | Delete task |

### Task Status

Allowed statuses:

- `BACKLOG`
- `TODO`
- `IN_PROGRESS`
- `REVIEW`
- `DONE`

Initial V1 allowed transitions:

| From | To |
| --- | --- |
| BACKLOG | TODO |
| TODO | IN_PROGRESS |
| IN_PROGRESS | REVIEW |
| REVIEW | DONE |
| REVIEW | IN_PROGRESS |
| DONE | REVIEW |

## Dev Logs

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/projects/{projectId}/dev-logs` | List project logs |
| POST | `/projects/{projectId}/dev-logs` | Create log |
| GET | `/dev-logs/{logId}` | Get log detail |
| PUT | `/dev-logs/{logId}` | Update log |
| DELETE | `/dev-logs/{logId}` | Delete log |

### Dev Log Fields

```json
{
  "date": "2026-06-04",
  "title": "Daily Dev Log",
  "completed": ["Improved README structure."],
  "bugsFixed": ["Fixed missing environment variable validation."],
  "decisions": ["Keep V1 local-first."],
  "problems": ["Need a clearer dashboard layout."],
  "nextSteps": ["Add architecture diagram."],
  "reflection": "The project should emphasize engineering clarity.",
  "rawMarkdown": "# Daily Dev Log..."
}
```

## Markdown Imports

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/imports/preview` | Parse Markdown and return preview |
| POST | `/imports/confirm` | Save parsed result as dev log |
| GET | `/projects/{projectId}/imports` | List import records |

### Preview Request

```json
{
  "projectId": "uuid",
  "markdown": "---\nproject: InsightWrite 2.0\n..."
}
```

### Preview Response

```json
{
  "data": {
    "frontMatter": {
      "project": "InsightWrite 2.0",
      "date": "2026-06-04",
      "type": "daily-log",
      "source": "codex",
      "relatedRepo": "xiaochuqing-dev/insightwrite-2.0"
    },
    "sections": {
      "completed": [],
      "bugsFixed": [],
      "decisions": [],
      "problems": [],
      "nextSteps": [],
      "reflection": ""
    },
    "warnings": []
  },
  "message": "OK"
}
```

## AI Outputs

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/projects/{projectId}/ai-outputs` | Generate AI output |
| GET | `/projects/{projectId}/ai-outputs` | List AI outputs |
| GET | `/ai-outputs/{outputId}` | Get AI output detail |
| POST | `/ai-outputs/{outputId}/regenerate` | Regenerate with confirmation |

### Output Types

- `WEEKLY_REPORT`
- `PROJECT_SUMMARY`
- `RESUME_BULLET`
- `README_SECTION`

### Generate Request

```json
{
  "type": "WEEKLY_REPORT",
  "fromDate": "2026-06-01",
  "toDate": "2026-06-07"
}
```

## Health

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Lightweight API health check |
| GET | `/actuator/health` | Spring Boot actuator health |

