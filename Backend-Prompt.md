# Backend Build Prompt — AI-Powered Fintech Dashboard (Banfico Hackathon)

## Context
We are building the backend for an AI-powered fintech dashboard. It integrates **live** with the provided **Hackathon Mock Bank sandbox** (a real UK Open Banking / OBIE AISP v4.0 API, reviewed from the attached Postman collection) and layers financial insights + a GenAI assistant on top.

**No persistence layer for this phase.** Everything (accounts, balances, transactions) is fetched live from the sandbox on demand. We may add a database later (e.g. for caching, user preferences, chat history) — don't build JPA/H2/entities now; keep the codebase easy to extend with a persistence layer later by isolating all sandbox I/O behind a clean client interface.

Judging weighs: Innovation 25%, UX 20%, Technical Implementation 20%, AI Usage 20%, API usage 10%, Presentation 5% — so **prioritize a working end-to-end vertical slice** (login → accounts → transactions → insights → chatbot) over exhaustive edge-case coverage.

## What the Postman collection actually revealed
This is a real Open Banking sandbox, not a fake mock:
- **Auth**: Keycloak-style OAuth2 **Resource Owner Password Credentials** grant.
  `POST https://auth.{domain}/auth/realms/{tenant}/protocol/openid-connect/token`
  Form-urlencoded body: `client_id`, `client_secret`, `username`, `password`, `grant_type=password`
  Response: `{ access_token, refresh_token, ... }` (standard Keycloak token response — also has `expires_in`, `refresh_expires_in`).
  Defaults seen in the collection: `domain=obiebank-sbx.banfico.io`, `tenant=provider`, `client_id=corebank-spa`, `client_secret=corebank-spa-password`. `username`/`password` are the **end customer's bank sandbox credentials** — these will be supplied by the hackathon organizers per test user, or you register/seed one via the sandbox if that's supported. Treat `client_id`/`client_secret`/`domain`/`tenant` as backend config (env vars), and `username`/`password` as what the end user types into our app's login form.
- **Core Banking API** — base `https://core-api.{domain}/api/obie-aisp/v4.0`, all calls need `Authorization: Bearer {access_token}`:
    - `POST /accounts` — create an account (OBIE schema: `Nickname`, `StatusUpdateDateTime`, `OpeningDate`, `Status`, `AccountCategory`, `AccountTypeCode`, `Balance`, `Currency`, `Account[]` with `SchemeName`/`Identification`/`Name`/`SecondaryIdentification`, `Servicer`, `StatementFrequencyAndFormat`, `InternationalAccount`)
    - `GET /accounts?type=domestic` — list accounts → `{ Data: { Account: [ { AccountId, ... } ] } }`
    - `GET /accounts/{accountId}` — single account detail
    - `GET /accounts/{accountId}/balances` — balance(s) for that account
    - `POST /accounts/{accountId}/transactions` — create a transaction (OBIE schema: `TransactionReference`, `Amount{Amount,Currency}`, `CreditDebitIndicator` (Credit/Debit), `Status`, `BookingDateTime`, `ValueDateTime`, `TransactionInformation`, `BankTransactionCode`, `MerchantDetails{MerchantName,MerchantCategoryCode}`, `CreditorAgent`/`DebtorAgent`, etc.)
    - `GET /accounts/{accountId}/transactions` — transaction history for that account

There is **no top-level "list all balances"** or "list all transactions across accounts" endpoint — our backend has to fan out per-account and aggregate for the dashboard.

The `POST /accounts` and `POST /accounts/{accountId}/transactions` endpoints exist mainly to **seed the sandbox with demo data** — expose them in our backend as an admin/demo-seeding capability (bonus), not as a core end-user-facing "add transaction" feature, unless we want a "log a manual transaction" bonus UX.

---

## Tech Stack (fixed — do not substitute)
- Java 25 (records, pattern matching for switch, virtual threads for fanning out concurrent sandbox calls)
- Spring Boot 4.1.0
- Spring AI 2.0.0 (ChatClient + tool/function calling)
- Maven, single module
- Spring Web (`RestClient`/`WebClient`), Spring Security (for securing *our* API, not JPA), Bean Validation
- **No Spring Data / no database.** Use `ConcurrentHashMap`-based or Caffeine in-memory stores only, and only for short-lived session/token caching — not for domain data persistence.
- Lombok
- springdoc-openapi for Swagger UI
- Resilience4j (or Spring's built-in retry) for the sandbox HTTP calls — this is a real external dependency, treat it like one (timeouts, retries, circuit breaker on the token endpoint especially)

---

## Step 1 — Project Scaffold
1. Generate a Spring Boot 4.1.0 / Java 25 Maven project named `fintech-ai-backend`, package `com.banfico.fintech`.
2. Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-ai-starter-model-anthropic` (confirm provider with me before wiring — see Step 5), `lombok`, `springdoc-openapi-starter-webmvc-ui`, `resilience4j-spring-boot3`.
3. Package structure:
```
com.banfico.fintech
 ├── auth/              (LoginController, SessionService, AppJwtService — our app's own short-lived session token)
 ├── sandbox/            (SandboxTokenService — Keycloak token exchange + refresh + cache;
 │                        SandboxAisClient — typed wrapper over the OBIE endpoints;
 │                        dto/ — OBIE request/response records: ObieAccount, ObieBalance, ObieTransaction, etc.)
 ├── account/           (AccountController, AccountService — maps OBIE DTOs -> clean frontend-facing DTOs)
 ├── transaction/       (TransactionController, TransactionService, Category classifier)
 ├── insights/          (InsightsService — computed live from fetched transactions, InsightsController)
 ├── ai/                (Spring AI config, FinancialAssistantService, tool/function definitions, ChatController)
 ├── dashboard/         (DashboardController — one aggregated call for the frontend home screen, fans out via virtual threads/structured concurrency)
 ├── common/            (GlobalExceptionHandler, ApiResponse<T>, RestClient/WebClient config with timeouts+retry)
 └── config/            (SecurityConfig, OpenApiConfig, CorsConfig, SandboxProperties)
```
4. `application.yml`: externalize `sandbox.domain`, `sandbox.tenant`, `sandbox.client-id`, `sandbox.client-secret` (env-overridable — `SANDBOX_CLIENT_SECRET` etc.), CORS allowing `http://localhost:5173`.
5. Confirm project builds and runs with an empty health-check endpoint before proceeding.

---

## Step 2 — Sandbox Integration Layer (replaces "domain model & seed data")
This is the most important step — get this right and everything else is straightforward mapping.

1. **`SandboxTokenService`**:
    - Exchanges `username`/`password` (passed in from our login endpoint) + configured `client_id`/`client_secret`/`grant_type=password` for `{access_token, refresh_token, expires_in}` by POSTing form-urlencoded to `https://auth.{domain}/auth/realms/{tenant}/protocol/openid-connect/token`.
    - Caches the token bundle **in-memory, keyed by our own session id**, with expiry tracked from `expires_in`.
    - Provides `refreshIfNeeded(sessionId)` using the `refresh_token` grant so a logged-in user isn't forced to re-enter credentials every ~5-10 minutes (typical Keycloak access-token lifetime).
    - Never logs or returns the raw `access_token`/`refresh_token` to the frontend — the frontend only ever sees **our own** session token (see Step 3).
2. **`SandboxAisClient`** — one method per OBIE endpoint listed above, using `RestClient` with the bearer token injected from `SandboxTokenService`, sane connect/read timeouts, and a retry (Resilience4j) on transient 5xx/timeout — but **not** on 401 (that should trigger a token refresh-and-retry-once, then surface an auth error).
3. Model the OBIE payloads as Java **records** matching the exact JSON shapes from the collection (nested `Data.Account[]`, `Amount{Amount,Currency}`, etc.) — don't invent a simplified shape at this layer; keep the raw-shape DTOs faithful to the sandbox, and do the simplification/flattening in the `account`/`transaction` service layer for our own API responses.
4. Write one quick standalone test/log line hitting the real sandbox token endpoint with a test user (I will supply real sandbox credentials) to confirm connectivity before building on top of it.

---

## Step 3 — Our Own Auth (thin session layer, no user DB)
- `POST /api/auth/login` — `{ username, password }` → calls `SandboxTokenService`, on success creates an in-memory session (UUID sessionId → token bundle), and returns **our own** short-lived JWT (or opaque session id) to the frontend to use as `Authorization: Bearer` on subsequent calls to *our* API.
- `POST /api/auth/logout` — drops the in-memory session entry.
- Security filter on `/api/**` (except `/api/auth/login`, `/swagger-ui/**`, `/v3/api-docs/**`) validates our own session token and resolves the associated sandbox token bundle for downstream calls.
- No registration endpoint — the end user must already exist in the sandbox (hackathon organizers provide test credentials). If self-serve sandbox user creation turns out to be possible/needed, flag it to me rather than guessing at an endpoint that isn't in the collection.

---

## Step 4 — Accounts, Balances, Transactions (Core Requirement #1)
All of these call `SandboxAisClient` live — no caching required initially, but structure the code so a cache (Caffeine, TTL ~30-60s) could be dropped in later without touching controllers.
- `GET /api/accounts` — list accounts (flattened: `id, nickname, type, currency, balance, maskedIdentification`)
- `GET /api/accounts/{id}` — single account detail
- `GET /api/accounts/{id}/balance` — current balance for that account
- `GET /api/accounts/{id}/transactions?category=&from=&to=` — transaction history for that account, filterable **client-side in our service layer** since the sandbox likely returns the full history per account (confirm actual response size when you test against it; add pagination in our own API regardless so the frontend contract doesn't need to change later)
- `GET /api/dashboard` — aggregates **all** accounts + their balances + recent transactions in one shaped response for the frontend home page. Fan out the per-account balance/transaction calls concurrently (Java 25 virtual threads / `StructuredTaskScope`) rather than sequentially — this is a real opportunity to demonstrate solid concurrent design, not just a hackathon shortcut.
- (Bonus) `POST /api/accounts` and `POST /api/accounts/{id}/transactions` — thin pass-through to the sandbox's create endpoints, useful for demo-seeding or a "log a transaction" UI feature.

---

## Step 5 — Financial Insights (Core Requirement #2)
Since there's no persistence, `InsightsService` computes everything **on the fly** from the transactions fetched live for the relevant accounts (fetch once per request, share across the calculations in that request — don't refetch per metric):
- `GET /api/insights/spending-summary?month=YYYY-MM`
- `GET /api/insights/category-breakdown?month=YYYY-MM` — you'll need a simple merchant/description → category classifier since the sandbox doesn't provide a clean category field (use `MerchantDetails.MerchantCategoryCode` if present, falling back to keyword-matching on `TransactionInformation`/merchant name)
- `GET /api/insights/trend?months=6`
- `GET /api/insights/anomalies` — outlier detection (mean + 2×stddev per category) computed on whatever history the sandbox returns
- `GET /api/insights/health-summary`
- `GET /api/insights/subscriptions` — detect recurring same-merchant/similar-amount monthly transactions

Flag to me if the sandbox's actual transaction history per account turns out to be too short/sparse for any of these to be meaningful (e.g., anomaly detection needs enough history) — we may need the seeding endpoint from Step 4 to backfill demo data before the live demo.

---

## Step 6 — AI Layer with Spring AI (AI Usage — 20% of score)
Before wiring this, **ask me which model provider** (Anthropic via `spring-ai-anthropic` or OpenAI) and confirm the API key will be supplied via env var — never hardcode it.

1. **Conversational financial assistant** — `POST /api/ai/chat` `{ message, conversationId }`, using Spring AI `ChatClient` with **tool/function calling** wired to the real service methods so answers are grounded in live sandbox data:
    - `getAccountBalances(sessionId)`
    - `getTransactionsByCategory(sessionId, category, month)`
    - `getSpendingSummary(sessionId, month)`
    - `getAnomalies(sessionId)`

   The tools need the caller's `sessionId` threaded through so they hit the sandbox with the right user's token — pass this via Spring AI's tool context, not a global.
2. **AI financial coaching** — `GET /api/ai/coaching-tip` — feeds live spending-summary + anomalies + health-summary into a prompt, returns 2-3 short actionable tips as structured JSON.
3. In-memory conversation memory only (per `conversationId`, cleared on session expiry) — no persistent chat history for this phase.
4. Wrap every AI and sandbox call with fallback handling — judges will interact live; a slow/flaky external call shouldn't crash the demo.

> **Addendum (added after initial Step 6 build) — Personalized AI Recommendations.**
> `coaching-tip` above only reasons over the *current month's* snapshot. A separate
> **`GET /api/ai/recommendations`** endpoint was added on top, grounded in a broader view: a
> 6-month income/expense (Credit vs Debit) **trend** plus the current month's **category
> breakdown**, so it can surface trajectory-level observations ("income dropped two months
> running") rather than only a single point-in-time number. It returns 3-5 structured
> recommendations (`title`, `description`, nullable `category`, `priority`), with the same
> rule-based fallback contract as `coaching-tip` if the model call fails.
>
> A `getPersonalizedRecommendations` tool was also added to the tool-calling surface from
> point 1 above, so the conversational `/api/ai/chat` assistant can answer "give me some
> recommendations" grounded in real data too — that tool intentionally returns deterministic
> rule-based data rather than triggering a second nested AI call from inside a tool invocation;
> the outer chat call's own single LLM turn phrases the natural-language reply.
>
> **A real bug was found and fixed while building this:** the original `ChatClientConfig`
> registered the chat-memory advisor as a **default** advisor on the shared `ChatClient` bean.
> That advisor requires a `conversationId` on every call it wraps — fine for the conversational
> `chat()` flow, but `coaching-tip` (and the new `recommendations`) are single-shot stateless
> prompts that never set one, so every call to them threw
> `IllegalArgumentException: conversationId cannot be null` internally. Their try/catch fallback
> silently absorbed this, and the fallback text was plausible enough that it went unnoticed
> through initial manual testing — the earlier "coaching-tip is working" confirmation was
> actually the fallback path the whole time, not real model output. Fixed by removing the
> advisor from the client's defaults and adding it only per-call inside `chat()`, where a
> `conversationId` genuinely exists. See `IMPLEMENTATION_PLAN.md` Phase 6 for the full account.

---

## Step 7 — Cross-Cutting Concerns
- `GlobalExceptionHandler` → consistent JSON error shape, and **specifically** map sandbox auth failures (expired/invalid token) to a clean 401 the frontend can react to (redirect to login) rather than leaking a raw Keycloak error body.
- Timeouts + retry + circuit breaker on all sandbox HTTP calls (Resilience4j) — this is a live third-party dependency during judging, treat reliability seriously.
- Bean Validation on request DTOs.
- CORS for the Vite dev server.
- OpenAPI/Swagger UI at `/swagger-ui.html`.
- `.env.example` documenting `SANDBOX_CLIENT_ID`, `SANDBOX_CLIENT_SECRET`, `SANDBOX_DOMAIN`, `SANDBOX_TENANT`, AI provider key.


---

## Step 8 — Deliverables Housekeeping
- `README.md` — setup, required env vars, how to obtain sandbox test credentials, Swagger URL.
- `ARCHITECTURE.md` — mermaid diagram: Controller → Service → SandboxAisClient (live HTTP) / AI layer, plus a short note on why persistence was deliberately deferred and where it would slot in later (e.g., caching layer in front of `SandboxAisClient`, or a chat-history/user-preferences store).

## Working Style
- Build and verify Step 2 (sandbox integration) first and get a real token + real account list back before writing anything else — everything downstream depends on this working.
- After Step 4, show me the actual `/api/dashboard` JSON so we can confirm the shape matches what the frontend expects before building insights/AI on top.
- Flag any place the real sandbox response doesn't match what the Postman collection implied (this happens often with sandboxes) rather than silently coding around it.