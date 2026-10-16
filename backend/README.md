# FlowBoard backend

Spring Boot 3.5 REST API for the FlowBoard kanban board, with JWT authentication and an AI layer built on
Spring AI. See the [project README](../README.md) for the big picture.

## Run

```powershell
# JAVA_HOME on the author's machine points at a folder that does not exist; set it for this terminal:
$env:JAVA_HOME = 'D:\java learning\javase-jdk'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

mvn spring-boot:run          # http://localhost:8081
mvn test                     # 56 tests
```

- Swagger UI: http://localhost:8081/swagger-ui.html. Call `POST /api/auth/register`, copy the `token`, click
  **Authorize** and paste it.
- Data lives in `backend/data/` (file-based H2 in PostgreSQL mode). Start the application from this folder, because
  the path is relative to the working directory. Delete the folder, with the application stopped, to start clean.

## Configuration

| Setting | Environment variable | Default | Notes |
|---|---|---|---|
| Server port | - | `8081` | `server.port` in `application.yml` |
| JWT secret | `JWT_SECRET` | development-only value | Must be at least 32 bytes. Always set it outside development |
| LLM API key | `LLM_API_KEY` | `not-configured` | Without a real key the AI endpoints answer 503 |
| LLM base URL | `LLM_BASE_URL` | `https://api.deepseek.com` | Any OpenAI-compatible provider |
| LLM model | `LLM_MODEL` | `deepseek-flash` | |
| Database (postgres profile) | `DB_URL`, `DB_USER`, `DB_PASSWORD` | `localhost:5432/flowboard` | `--spring.profiles.active=postgres`; never run on the author's machine |

## API

All endpoints except register and login need `Authorization: Bearer <token>`. Errors are
`application/problem+json` (RFC 9457).

| Method | Path | Description | Success | Typical errors |
|---|---|---|---|---|
| POST | `/api/auth/register` | `{username,email,password}` -> `{token,user}` | 201 | 400 validation, 409 duplicate |
| POST | `/api/auth/login` | `{email,password}` -> `{token,user}` | 200 | 401 |
| GET | `/api/auth/me` | The signed-in user; used to check a stored token | 200 | 401 |
| POST | `/api/boards` | `{name}` -> board | 201 | 400 |
| GET | `/api/boards` | My boards, newest first | 200 | |
| GET | `/api/boards/{id}` | Board with ordered columns and ordered cards | 200 | 404 |
| POST | `/api/boards/{id}/columns` | `{name}` -> column (appended on the right) | 201 | 404 |
| POST | `/api/tasks` | `{columnId,title,description?,priority?,dueDate?}` -> card (appended to the column) | 201 | 400, 404 |
| POST | `/api/tasks/bulk` | `{columnId,tasks:[{title,...}]}` -> cards, all or nothing | 201 | 400, 404 |
| PATCH | `/api/tasks/{id}` | Edit: full snapshot of the editable fields (`null` clears) | 200 | 400, 404 |
| PATCH | `/api/tasks/{id}/move` | `{targetColumnId,orderIndex}` -> card at that final position | 200 | 400, 404 |
| DELETE | `/api/tasks/{id}` | Delete a card and close the gap in its column | 204 | 404 |
| GET | `/api/ai/status` | `{configured, model}` | 200 | |
| POST | `/api/ai/chat` | `{conversationId?,message,boardId?,timeZone?}` -> `{conversationId,reply,toolCalls}` | 200 | 502, 503 |
| POST | `/api/ai/tasks/quick-add` | `{boardId,text,timeZone?}` -> created card + explanation + warnings | 201 | 404, 422, 502, 503 |
| POST | `/api/ai/tasks/{id}/split` | Suggested sub-tasks (a preview, nothing is created) | 200 | 404, 502, 503 |
| POST | `/api/ai/boards/{id}/standup` | `{timeZone?}` -> `{summary, stats}` | 200 | 404, 502, 503 |

A card or board that belongs to someone else is reported as `404`, never `403`.

## Tests

| Class | Kind | What it covers |
|---|---|---|
| `BoardFlowIntegrationTest` | HTTP + security + JPA | Full flow, edit, delete, bulk create, `/me`, errors, isolation between users |
| `TaskMoveServiceTest` | service | The ordering algorithm of `moveTask` |
| `AiEndpointsIntegrationTest` | HTTP | AI endpoints with no key: authentication, validation, 503 |
| `AiFakeModelIntegrationTest` | HTTP + fake model server | Chat with tools, quick add, split, stand-up, failure paths |
| `AiInputTest`, `TaskDraftResolveTest`, `TaskSplitSanitizeTest`, `StandupStatsTest` | unit | Validation of values that come from a model, stand-up facts |
