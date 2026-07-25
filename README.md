# Banfico Hackathon — Fintech AI Backend

An AI-powered fintech backend built for the Banfico hackathon. It aggregates live account,
balance, and transaction data from the Hackathon Mock Bank sandbox (a real UK Open Banking /
OBIE AISP v4.0 API), layers financial insights on top, and exposes a conversational AI assistant
grounded in that live data.

**No database.** Everything is fetched live from the sandbox on every request — see
`ARCHITECTURE.md` for why and where a persistence layer would slot in later.

## Tech stack

- Java 25, Spring Boot 4.1.0, Maven
- Spring Security (our own thin session-token auth, not Spring Security's login/UserDetails)
- Spring AI 2.0.0 + Ollama (local, `qwen2.5:7b`) for the chat assistant and coaching tips
- Resilience4j for retry/circuit-breaking on sandbox HTTP calls
- springdoc-openapi for Swagger UI
- Lombok (`@Slf4j` for logging)

## Prerequisites

1. **Java 25** and the included Maven wrapper (`./mvnw` / `mvnw.cmd`) — no separate Maven install needed.
2. **Ollama**, running locally with the model pulled:
   ```
   ollama pull qwen2.5:7b
   ```
   (Ollama must be running — `ollama serve`, or as a background service — before you hit
   `/api/ai/chat` or `/api/ai/coaching-tip`. Every other endpoint works without it.)
3. **Sandbox test credentials** — a username/password for a pre-provisioned Hackathon Mock Bank
   sandbox customer. There's no self-registration; get these from the hackathon organizers /
   whoever manages the sandbox tenant for your team.

## Setup

1. Copy the env template and fill in real values:
   ```
   cp .env.example .env
   ```
   Edit `.env`:
   - `SANDBOX_DOMAIN`, `SANDBOX_TENANT`, `SANDBOX_CLIENT_ID`, `SANDBOX_CLIENT_SECRET` — backend
     config for the sandbox's Keycloak tenant. The defaults in `.env.example` match the values
     seen in the provided Postman collection; only change them if your team was given different
     ones.
   - `SANDBOX_TEST_USERNAME` / `SANDBOX_TEST_PASSWORD` — **your own** sandbox test customer
     credentials, used only for the optional live integration test (see below) and for manual
     `curl`/Postman testing. These are never used by the running app itself — end users submit
     their own credentials via `POST /api/auth/login`.
   - `APP_CORS_ALLOWED_ORIGINS` — comma-separated frontend origins, defaults to
     `http://localhost:5173` (Vite's default dev port).

   **`.env` is gitignored — never commit real credentials.** `.env.example` must stay
   placeholder-only.

2. Load `.env` into your shell and run the app:
   ```bash
   set -a && source .env && set +a
   ./mvnw spring-boot:run
   ```
   (PowerShell: load the vars however you prefer, e.g. a small script that does
   `$env:NAME = "value"` per line, then `.\mvnw.cmd spring-boot:run`.)

   The app starts on **port 9091**.

3. Confirm it's up: `curl http://localhost:9091/actuator/health` → `{"status":"UP"}`.

## Trying it out

See **`API_REFERENCE.md`** for the full endpoint contract (request/response shapes, example
payloads) — written for the frontend team but equally useful for manual testing.

Quick smoke test:
```bash
set -a && source .env && set +a
TOKEN=$(curl -s -X POST http://localhost:9091/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$SANDBOX_TEST_USERNAME\",\"password\":\"$SANDBOX_TEST_PASSWORD\"}" \
  | sed -n 's/.*"sessionToken":"\([^"]*\)".*/\1/p')

curl -s http://localhost:9091/api/dashboard -H "Authorization: Bearer $TOKEN"
```

## Swagger / OpenAPI

Interactive docs, no auth required to view: **http://localhost:9091/swagger-ui/index.html**
(raw spec at `/v3/api-docs`). You still need a `sessionToken` (via `/api/auth/login`) to
authorize "Try it out" calls against protected endpoints — use the "Authorize" button in the UI.

## Running tests

```bash
./mvnw test
```

Most tests need no external dependencies. One exception:

- **`SandboxIntegrationTest`** (`src/test/java/.../sandbox/`) makes real calls against the live
  sandbox to prove `SandboxTokenService`/`SandboxAisClient` work end-to-end. It's gated on
  `SANDBOX_TEST_USERNAME`/`SANDBOX_TEST_PASSWORD` being set as environment variables — without
  them it's silently skipped, so a plain `./mvnw test` never breaks for someone without sandbox
  credentials. To actually run it: `set -a && source .env && set +a && ./mvnw test`.

## Logging

Every request gets a short correlation id (`reqId`) shown in the console log
(`[reqId=abcd1234]`), stamped by `RequestLoggingFilter` and propagated via SLF4J's MDC — grep
the logs for one `reqId` to see everything that happened while handling a single request,
including deep sandbox/AI calls. Session ids and sandbox tokens are never logged in full (see
`common/Masking`) — session ids are truncated, and the real sandbox access/refresh tokens are
never logged at all.

## Project docs

- **`IMPLEMENTATION_PLAN.md`** — phase-by-phase build plan, current status, and the
  method-level contracts each phase built for the next one to reuse.
- **`API_REFERENCE.md`** — full endpoint contract for frontend integration.
- **`ARCHITECTURE.md`** — system diagram and design rationale (this file's companion).
- **`Backend-Prompt.md`** / **`hackathon_requirement.md`** — original source specs this build
  followed.
