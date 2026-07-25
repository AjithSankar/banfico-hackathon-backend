# Implementation Plan — AI-Powered Fintech Backend

Source spec: `Backend-Prompt.md` (detailed) and `hackathon_requirement.md` (challenge brief).
This file breaks that spec into phases so the team can branch off `master` and work
independently once the blocking phases are done.

## Status
- **Phase 1 (scaffold): done.** Package renamed to `com.banfico.fintech`, dependencies added,
  package skeleton in place, builds clean.
- **Phase 2 (sandbox integration): done and verified live.** `SandboxTokenService` +
  `SandboxAisClient` confirmed end-to-end against the real sandbox — real token, 8 real
  accounts, real balances and transactions came back and deserialize cleanly into the DTOs in
  `sandbox/dto`. See `src/test/java/.../sandbox/SandboxIntegrationTest.java` (gated on
  `SANDBOX_TEST_USERNAME`/`SANDBOX_TEST_PASSWORD` env vars — copy `.env.example` to `.env` and
  fill in real sandbox test credentials to run it; `.env` is gitignored, never commit it).
- **Phase 3 (auth/session layer): done and verified live.** `POST /api/auth/login` /
  `POST /api/auth/logout` work end-to-end against the real sandbox; `SessionAuthFilter` 401s
  requests with a missing/invalid/invalidated session token. See "Contracts agreed" under
  Phase 3 below for how the frontend should use the returned token.
- **Phase 4 (accounts/balances/transactions/dashboard): done and verified live.**
  `/api/accounts`, `/api/accounts/{id}`, `/api/accounts/{id}/balance`,
  `/api/accounts/{id}/transactions`, `/api/dashboard` all confirmed against the real sandbox —
  8 accounts, correct balance sum in the dashboard, correct newest-first sort and filtering. See
  `API_REFERENCE.md` for the full frontend-facing contract with example payloads.
  **Known demo-data quirk:** most seeded transactions carry a hardcoded MCC (`1711`) from the
  Postman collection's seed request regardless of their random description text, so category
  breakdown (Phase 5) will skew toward `"Home Services"` for that data — not a classifier bug.
- **Phase 5 (financial insights): done and verified live.** All 6 endpoints
  (`spending-summary`, `category-breakdown`, `trend`, `anomalies`, `health-summary`,
  `subscriptions`) confirmed against the real sandbox — see `API_REFERENCE.md` for the full
  contract with real example payloads. Live results confirmed the demo-data caveats predicted
  in advance: category breakdown was 100% `"Home Services"` (the MCC-1711 quirk), and
  `anomalies`/`subscriptions` both returned empty (too little history per category / merchant
  names essentially never repeat since they're randomly generated per seed transaction). None of
  this indicates a bug — seed more varied demo transactions before a live judging walkthrough if
  these need to visibly show something.
- **Phases 6–8: not started.** Open for the team to pick up on feature branches per the
  dependency map below.

## Confirmed decisions (Phase 0)
- **AI provider**: Ollama, local (`spring-ai-starter-model-ollama`, already in `pom.xml`).
  No API key needed; requires a local Ollama server (`SPRING_AI_OLLAMA_BASE_URL`, default
  `http://localhost:11434`, model `qwen2.5:7b`). Each dev running Phase 6 work needs Ollama
  running locally with that model pulled.
- **Package**: `com.banfico.fintech` (renamed from `dev.ak.ai`).
- **No persistence layer** for this phase — everything is fetched live from the sandbox on
  every request. All sandbox I/O is isolated behind `SandboxAisClient` / `SandboxTokenService`
  so a cache or DB can be dropped in later without touching controllers.

## Branch / dependency map

```
Phase 1 (scaffold)                         — solo, blocking, done by whoever sets up the repo
   └─ Phase 2 (sandbox integration)        — BLOCKING for everything below. Do this first,
                                              verify against the real sandbox before continuing.
        ├─ Phase 3 (auth/session layer)    — depends only on Phase 2
        │     └─ Phase 4 (accounts/balances/transactions/dashboard) — depends on Phase 3
        │           ├─ Phase 5 (insights)  — depends on Phase 4 (needs transaction data)
        │           └─ Phase 6 (AI layer)  — depends on Phase 4/5 (tools call those services)
        └─ Phase 7 (cross-cutting)         — starts alongside Phase 3/4, refined continuously
Phase 8 (deliverables: README/ARCHITECTURE) — draft early, finalize last
```

Suggested feature branches once Phase 2 lands on `master`:
- `feature/auth-session` (Phase 3)
- `feature/accounts-dashboard` (Phase 4)
- `feature/insights` (Phase 5)
- `feature/ai-chat` (Phase 6)
- `feature/cross-cutting` (Phase 7 — exception handling, resilience, CORS, OpenAPI)

Phases 3–6 each depend on the previous one's *service layer/interfaces* existing, not on the
whole phase being merged — agree on method signatures early (see each phase's "Contracts to
agree on") so branches don't block each other on merge.

---

## Phase 1 — Project Scaffold
**Owner:** whoever sets this up first (blocking for all).

- Rename package `dev.ak.ai` → `com.banfico.fintech`.
- Add dependencies: `spring-boot-starter-security`, `spring-boot-starter-validation`,
  `springdoc-openapi-starter-webmvc-ui`, `resilience4j-spring-boot3` (keep the existing
  `spring-boot-starter-actuator`, `spring-boot-starter-webmvc`, `spring-ai-starter-model-ollama`,
  `lombok`).
- Package structure:
  ```
  com.banfico.fintech
   ├── auth/        (LoginController, SessionAuthFilter, CurrentSession — opaque UUID session
   │                 id doubles as the bearer token, no separate JWT service needed)
   ├── sandbox/      (SandboxTokenService, SandboxAisClient, dto/)
   ├── account/     (AccountController, AccountService)
   ├── transaction/ (TransactionController, TransactionService, Category classifier)
   ├── insights/    (InsightsService, InsightsController)
   ├── ai/          (Spring AI config, FinancialAssistantService, tool definitions, ChatController)
   ├── dashboard/   (DashboardController)
   ├── common/      (GlobalExceptionHandler, ApiResponse<T>, RestClient/WebClient config)
   └── config/      (SecurityConfig, OpenApiConfig, CorsConfig, SandboxProperties)
  ```
- `application.yaml`: `sandbox.domain`, `sandbox.tenant`, `sandbox.client-id`,
  `sandbox.client-secret` as env-overridable properties; CORS allowing `http://localhost:5173`.
- `.env.example` documenting `SANDBOX_CLIENT_ID`, `SANDBOX_CLIENT_SECRET`, `SANDBOX_DOMAIN`,
  `SANDBOX_TENANT`, `SPRING_AI_OLLAMA_BASE_URL`.
- **Exit criteria:** project builds (`mvnw compile`), app starts, `/actuator/health` returns UP.

---

## Phase 2 — Sandbox Integration Layer (BLOCKING — do this before anything else)
**Why blocking:** every other phase reads live data through this layer. Get the shapes right
here and downstream work is straightforward mapping.

- `SandboxTokenService`:
  - Exchanges `username`/`password` + configured `client_id`/`client_secret`/
    `grant_type=password` for `{access_token, refresh_token, expires_in}` via
    `POST https://auth.{domain}/auth/realms/{tenant}/protocol/openid-connect/token`
    (form-urlencoded).
  - In-memory cache keyed by our own session id, tracks expiry from `expires_in`.
  - `refreshIfNeeded(sessionId)` using the refresh_token grant.
  - Never returns raw `access_token`/`refresh_token` to the frontend.
- `SandboxAisClient` — one method per OBIE endpoint (`RestClient`, bearer token injected,
  timeouts, Resilience4j retry on 5xx/timeout only — not 401, which should trigger a
  refresh-and-retry-once).
- Model OBIE payloads as Java records matching the exact JSON shapes (nested `Data.Account[]`,
  `Amount{Amount,Currency}`, etc.) — faithful raw DTOs; simplification happens in
  `account`/`transaction` service layers.
- **Exit criteria: MET.** Verified live — real token, 8 real accounts, real balances and
  transactions, all matching the DTOs as modeled (no shape surprises vs. the OBIE spec).

**Contracts agreed for Phase 3+ to build on:**
- `SandboxTokenService.login(String sessionId, String username, String password): TokenBundle`
- `SandboxTokenService.getAccessToken(String sessionId): String` (transparently refreshes if expiring soon)
- `SandboxTokenService.refresh(String sessionId): TokenBundle`
- `SandboxTokenService.invalidate(String sessionId): void`
- `SandboxAisClient.getAccounts(String sessionId): ObieAccountsResponse`
- `SandboxAisClient.getAccount(String sessionId, String accountId): ObieAccountsResponse`
- `SandboxAisClient.getBalances(String sessionId, String accountId): ObieBalancesResponse`
- `SandboxAisClient.getTransactions(String sessionId, String accountId): ObieTransactionsResponse`
- `SandboxAisClient.createAccount(...)` / `createTransaction(...)` — bonus demo-seeding, same pattern
- All raw OBIE DTOs live in `sandbox/dto/` (`ObieAccount`, `ObieBalance`, `ObieTransaction`, etc.)
  with exact PascalCase field names matching the sandbox JSON — map/flatten to clean DTOs in
  `account`/`transaction` service layers, don't reuse these raw records as API response bodies.

---

## Phase 3 — Our Own Auth (thin session layer, no user DB)
- `POST /api/auth/login` → `SandboxTokenService` → in-memory session (UUID → token bundle) →
  our own opaque session id returned to the frontend as `sessionToken`.
- `POST /api/auth/logout` — drops the session entry (requires a valid session token itself).
- `SessionAuthFilter` guards every `/api/**` request except `/api/auth/login` (Swagger/actuator
  paths aren't under `/api/**` so they're untouched). Missing/invalid/expired token → 401 with
  an `ApiResponse` error body, written directly by the filter.
- No registration endpoint — sandbox test users are pre-provisioned.
- **Exit criteria: MET.** Verified live: login with real sandbox credentials returns a
  `sessionToken`; a protected endpoint (`/api/auth/logout`) 401s without it and with an
  invalidated one; succeeds with a valid one.

**Frontend contract — read this before building Phase 4 controllers:**
- The frontend only ever holds and sends **`sessionToken`** (the opaque UUID from the login
  response) as `Authorization: Bearer <sessionToken>` on every call to *our* API. It never sees
  the real sandbox `access_token`/`refresh_token` — those stay server-side in
  `SandboxTokenService`, keyed by that same session id, and are injected into `SandboxAisClient`
  calls automatically (refreshed transparently when expiring).
- `expiresInSeconds` in the login response reflects the underlying sandbox access token's
  lifetime (~5 min) — informational only, the frontend doesn't need to act on it since refresh
  is automatic. A `sessionToken` effectively stays valid indefinitely as long as it's used again
  before the sandbox refresh token expires (~30 min of inactivity); past that, any call 401s and
  the frontend should redirect to login.
- **Contract for Phase 4+ controllers:** call `CurrentSession.sessionId()`
  (`com.banfico.fintech.auth.CurrentSession`) to get the current request's session id — don't
  re-parse the `Authorization` header yourself, the filter already validated it and put it in
  the `SecurityContext`.

---

## Phase 4 — Accounts, Balances, Transactions, Dashboard (Core Requirement #1)
All calls go live through `SandboxAisClient` — no caching yet, but structured so a cache can be
added later without touching controllers.
- `GET /api/accounts` — flattened list: `id, nickname, type, currency, balance, maskedIdentification`.
- `GET /api/accounts/{id}` — single account detail.
- `GET /api/accounts/{id}/balance`.
- `GET /api/accounts/{id}/transactions?category=&from=&to=` — filtered in our service layer,
  paginated in our own API contract.
- `GET /api/dashboard` — aggregates all accounts + balances + recent transactions in one
  response; fan out per-account calls concurrently via virtual threads/`StructuredTaskScope`.
- (Bonus) `POST /api/accounts`, `POST /api/accounts/{id}/transactions` — pass-through to
  sandbox create endpoints for demo-seeding.
- **Exit criteria: MET.** Live `/api/dashboard` JSON reviewed — see `API_REFERENCE.md` for the
  full shape with a real example. All endpoints implemented in `account/`, `transaction/`,
  `dashboard/` packages: `AccountService`/`AccountController`, `TransactionCategoryClassifier`/
  `TransactionService`/`TransactionController`, `DashboardService`/`DashboardController`.
  Per-account balance and transaction fetches fan out concurrently via
  `common/Concurrency.mapConcurrently` (virtual threads).

**Contracts agreed for Phase 5+ to build on:**
- `AccountService.listAccounts(sessionId): List<AccountSummary>` — balances already resolved
- `TransactionService.listTransactions(sessionId, accountId, category, from, to): List<TransactionSummary>`
  — full history for one account, already classified/sorted; Phase 5 will need to call this
  per-account (fan out via `Concurrency.mapConcurrently` again) and aggregate across accounts
- `TransactionCategoryClassifier.classify(ObieTransaction): String` — reusable if Phase 5 needs
  to classify at a different layer
- `common/PagedResult<T>` and `common/Concurrency` are generic — reuse them, don't reinvent

---

## Phase 5 — Financial Insights (Core Requirement #2)
Computed on the fly from transactions fetched once per request and shared across calculations
(don't refetch per metric).
- `GET /api/insights/spending-summary?month=YYYY-MM`
- `GET /api/insights/category-breakdown?month=YYYY-MM` — classifier using
  `MerchantDetails.MerchantCategoryCode` where present, falling back to keyword-matching on
  `TransactionInformation`/merchant name.
- `GET /api/insights/trend?months=6`
- `GET /api/insights/anomalies` — outlier detection (mean ± 2×stddev per category).
- `GET /api/insights/health-summary`
- `GET /api/insights/subscriptions` — recurring same-merchant/similar-amount detection.
- **Flag early** if sandbox transaction history is too sparse for anomaly/trend detection — may
  need Phase 4's seeding endpoints to backfill demo data before the live demo.
- **Exit criteria: MET.** All 6 endpoints implemented in `insights/InsightsService` +
  `InsightsController`, verified live. Confirmed sparse-data behavior: `anomalies` requires
  ≥3 transactions in a category to compute mean/stddev at all (categories with fewer are
  skipped, not flagged); `subscriptions` requires ≥2 same-merchant transactions with similar
  amounts (±15%) roughly 20-40 days apart — both returned empty on current seed data, as
  expected given the caveats above.

**Contracts agreed for Phase 6+ to build on:**
- `InsightsService.spendingSummary(sessionId, YearMonth)`, `.categoryBreakdown(sessionId, YearMonth)`,
  `.trend(sessionId, months)`, `.anomalies(sessionId)`, `.healthSummary(sessionId)`,
  `.subscriptions(sessionId)` — these are exactly the tool-calling surface Phase 6's
  `ChatClient` tools should wrap (per `Backend-Prompt.md` Step 6: `getSpendingSummary`,
  `getAnomalies`, etc.) — call these methods directly, don't reimplement the logic.
  `InsightsService` already fetches transactions once per call and shares them across whatever
  sub-metrics that call needs, so Phase 6 tools calling multiple of these back-to-back should be
  aware each call still re-fetches from the sandbox (no cross-call caching yet).
- All money fields are `BigDecimal`; all category names are open-ended strings (not an enum) —
  see the current known set in `API_REFERENCE.md`.

---

## Phase 6 — AI Layer with Spring AI (AI Usage — 20% of score)
Uses Ollama (local) per Phase 0 decision.
- `POST /api/ai/chat` `{ message, conversationId }` — Spring AI `ChatClient` with tool/function
  calling wired to live service methods:
  - `getAccountBalances(sessionId)`
  - `getTransactionsByCategory(sessionId, category, month)`
  - `getSpendingSummary(sessionId, month)`
  - `getAnomalies(sessionId)`
  `sessionId` threaded through Spring AI's tool context (not a global) so tools hit the sandbox
  with the right user's token.
- `GET /api/ai/coaching-tip` — feeds live spending-summary + anomalies + health-summary into a
  prompt, returns 2-3 short actionable tips as structured JSON.
- In-memory conversation memory only (per `conversationId`, cleared on session expiry).
- Fallback handling on every AI/sandbox call — a slow/flaky call must not crash the live demo.
- **Dev setup note:** requires local Ollama running with `qwen2.5:7b` pulled
  (`ollama pull qwen2.5:7b`).

---

## Phase 7 — Cross-Cutting Concerns
Can start as soon as Phase 3/4 controllers exist; refine continuously rather than as one block.
- `GlobalExceptionHandler` — consistent JSON error shape; map sandbox auth failures (expired/
  invalid token) to a clean 401, never leak raw Keycloak error bodies. **Done** (minimal version
  landed in Phase 3, now with logging — see below).
- Resilience4j timeouts + retry + circuit breaker on all sandbox HTTP calls. **Done** (Phase 2).
- Bean Validation on request DTOs. **Done** (`LoginRequest`).
- CORS for the Vite dev server. **Done** (Phase 1).
- OpenAPI/Swagger UI at `/swagger-ui.html`. **Done** (dependency added Phase 1, works out of the box).
- **Request logging: done.** `common/RequestLoggingFilter` logs method/path/status/duration for
  every request, wrapped around the whole security chain, with a per-request correlation id
  (`MDC` key `requestId`, shown as `[reqId=...]` in the console pattern) so every log line for
  one request — including deep in `SandboxTokenService`/`SandboxAisClient` — can be grepped
  together. Service-level logging added to `LoginController`, `SandboxTokenService`,
  `SandboxAisClient`, `SessionAuthFilter`, `GlobalExceptionHandler`, `DashboardService`. Session
  ids are truncated via `common/Masking` before logging (they're bearer credentials for our own
  API) — access/refresh tokens are never logged, not even truncated.
- Also excluded Boot's `UserDetailsServiceAutoConfiguration` (`Application.java`) — we never use
  Spring Security's `UserDetailsService`/`AuthenticationManager` (`SessionAuthFilter` is the sole
  auth mechanism), so the auto-generated random password/user was dead weight and noisy at
  startup.

---

## Phase 8 — Deliverables Housekeeping
- `README.md` — setup, required env vars, how to obtain sandbox test credentials, Swagger URL.
- `ARCHITECTURE.md` — mermaid diagram: Controller → Service → SandboxAisClient (live HTTP) / AI
  layer, plus a note on why persistence was deliberately deferred and where it would slot in
  later (caching layer in front of `SandboxAisClient`, or a chat-history/user-preferences store).

---

## Working style reminders (from Backend-Prompt.md)
- Build and verify **Phase 2** first — get a real token + real account list back before writing
  anything else.
- After **Phase 4**, share the actual `/api/dashboard` JSON so the shape is confirmed before
  insights/AI build on top of it.
- Flag any place the real sandbox response doesn't match what the Postman collection implied,
  rather than silently coding around it.
