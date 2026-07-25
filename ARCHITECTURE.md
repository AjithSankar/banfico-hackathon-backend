# Architecture

A backend that aggregates live account, balance, and transaction data from the Hackathon Mock
Bank sandbox (a real UK Open Banking / OBIE AISP v4.0 API), computes financial insights on the
fly, and layers a tool-calling AI assistant on top — no database, everything fetched live.

**Quick stats:** 17 REST endpoints across 6 domains · 10 AI chat tools · verified live against
the real sandbox and local Ollama at every phase (see `IMPLEMENTATION_PLAN.md`).

## Contents

1. [Tech stack](#tech-stack)
2. [System diagram](#system-diagram)
3. [Request flow: the two-token model](#request-flow-the-two-token-model)
4. [Package map](#package-map)
5. [Feature highlights](#feature-highlights)
6. [Key design decisions](#key-design-decisions)
7. [Why no persistence](#why-no-persistence-and-where-it-would-slot-in)
8. [Appendix: issues found and fixed during development](#appendix-issues-found-and-fixed-during-development)

---

## Tech stack

| Layer | Choice |
|---|---|
| Language / runtime | Java 25, Spring Boot 4.1.0, Maven |
| Web / security | Spring Web, Spring Security (custom session-token auth, not Spring's own login) |
| AI | Spring AI 2.0.0 + Ollama (local, `qwen2.5:7b`) — tool-calling + structured output |
| Resilience | Resilience4j (retry only — see [Key design decisions](#key-design-decisions)) |
| Docs | springdoc-openapi (Swagger UI) |
| Persistence | **None by design** — see [below](#why-no-persistence-and-where-it-would-slot-in) |

---

## System diagram

```
 React frontend (localhost:5173)
          |
          |  Authorization: Bearer <sessionToken>
          v
 +--------------------------------------------------------------------+
 |  Spring Boot backend (localhost:9091)                               |
 |                                                                      |
 |  CorsFilter -> RequestLoggingFilter -> SessionAuthFilter             |
 |  (security filter chain — see "two-token model" below)               |
 |                                                                      |
 |  Controllers                                                        |
 |   LoginController   AccountController   TransactionController       |
 |   DashboardController   InsightsController   ChatController         |
 |        |                  |                     |                   |
 |        v                  v                     v                   |
 |  Services                                                            |
 |   AccountService   TransactionService   DashboardService             |
 |   InsightsService   FinancialAssistantService (+ FinancialTools)     |
 |        |                                          |                  |
 |        v                                          v                  |
 |  sandbox/ package                          Spring AI ChatClient      |
 |   SandboxTokenService   SandboxAisClient           |                  |
 |   (Resilience4j retry)                             |                  |
 +--------------------|-------------------------------|------------------+
                       |                               |
                       v                               v
          +-------------------------+       +----------------------+
          | Keycloak token endpoint  |       | Ollama (local)        |
          | auth.{domain}            |       | qwen2.5:7b            |
          +-------------------------+       +----------------------+
                       |
                       v
          +-------------------------+
          | OBIE AISP v4.0 API        |
          | core-api.{domain}         |
          +-------------------------+
```

Every request follows the same shape: **Controller → Service → `SandboxAisClient` (live HTTP) /
AI layer.** No caching, no database in the middle — whatever the sandbox returns right now is
what the response is built from.

---

## Request flow: the two-token model

The frontend never sees the sandbox's real OAuth2 tokens — that's the one security boundary
worth explaining carefully in a demo.

| # | Step |
|---|---|
| 1 | Frontend sends `POST /api/auth/login` with `{ username, password }` — the end customer's real sandbox credentials. |
| 2 | `LoginController` generates a new opaque `sessionId` (a UUID) and calls `SandboxTokenService.login(sessionId, username, password)`. |
| 3 | `SandboxTokenService` exchanges the credentials with Keycloak (`grant_type=password`) and caches `{ access_token, refresh_token, expires_in }` in memory, keyed by `sessionId`. |
| 4 | `LoginController` returns `{ sessionToken: sessionId, expiresInSeconds }` to the frontend. **The real sandbox tokens never leave the backend.** |
| 5 | Frontend calls any protected endpoint (e.g. `GET /api/dashboard`) with `Authorization: Bearer <sessionToken>`. |
| 6 | `SessionAuthFilter` extracts that token, calls `SandboxTokenService.getAccessToken(sessionId)` (transparently refreshing the sandbox access token first if it's expiring soon), and — if valid — stores `sessionId` as the authenticated principal in Spring Security's `SecurityContextHolder`. |
| 7 | The controller's service layer calls `SandboxAisClient`, which resolves the **real** access token from `SandboxTokenService` and calls the OBIE API with `Authorization: Bearer <real access_token>`. |
| 8 | If the sandbox responds `401`, `SandboxAisClient` refreshes once via `SandboxTokenService` and retries once; if it still fails, `GlobalExceptionHandler` maps it to a clean `401` (never a raw Keycloak error body). |

`CurrentSession.sessionId()` is how every controller/service reads the authenticated session id
back out of `SecurityContextHolder` (set in step 6) — nobody re-parses the `Authorization`
header themselves.

---

## Package map

| Package | Responsibility | Key classes |
|---|---|---|
| `auth/` | Our own thin session layer — no user DB | `LoginController`, `SessionAuthFilter`, `CurrentSession` |
| `sandbox/` | Sandbox integration (blocking dependency for everything else) | `SandboxTokenService`, `SandboxAisClient`, `sandbox/dto/*` (faithful raw OBIE records) |
| `account/` | Flattened account/balance API | `AccountService`, `AccountController` |
| `transaction/` | Transaction history + categorization | `TransactionService`, `TransactionCategoryClassifier`, `TransactionController` |
| `dashboard/` | Aggregated home-screen endpoint | `DashboardService`, `DashboardController` |
| `insights/` | Financial insights, all computed live | `InsightsService`, `InsightsController` |
| `ai/` | Chat assistant, coaching tips, recommendations | `ChatClientConfig`, `FinancialTools`, `FinancialAssistantService`, `ChatController` |
| `common/` | Cross-cutting concerns | `ApiResponse<T>`, `GlobalExceptionHandler`, `RequestLoggingFilter`, `Masking`, `Concurrency`, `PagedResult<T>` |
| `config/` | Application configuration | `SecurityConfig`, `CorsConfig`, `SandboxProperties` |

<details>
<summary>Full class-level detail per package (click to expand)</summary>

**`auth/`**
- `LoginController` — `POST /api/auth/login`, `POST /api/auth/logout`
- `LoginRequest` / `LoginResponse` — DTOs (`sessionToken`, `expiresInSeconds`)
- `SessionAuthFilter` — validates the bearer `sessionToken` on every `/api/**` request except
  `/api/auth/login`; bypasses `OPTIONS` (CORS preflight) explicitly
- `CurrentSession` — static helper reading the authenticated `sessionId` from `SecurityContextHolder`

**`sandbox/`**
- `SandboxTokenService` — Keycloak password/refresh grant exchange, in-memory session→token cache
- `SandboxAisClient` — typed wrapper over every OBIE endpoint (get/create accounts, balances,
  transactions); 401 triggers refresh-and-retry-once; Resilience4j retry
- `TokenBundle` — access/refresh token + expiry record
- `sandbox/dto/` — faithful raw OBIE request/response records matching the sandbox's exact JSON
  field names: `ObieAccount`, `ObieAccountData`, `ObieAccountsResponse`,
  `ObieAccountIdentification`, `ObieServicer`, `ObieStatementFrequency`, `ObieDeliveryAddress`,
  `ObieBalance`, `ObieBalanceData`, `ObieBalancesResponse`, `ObieTransaction`,
  `ObieTransactionData`, `ObieTransactionsResponse`, `ObieBankTransactionCode`,
  `ObieMerchantDetails`, `ObieTransactionBalance`, `ObieAmount`, `TokenResponse`, plus
  create-request DTOs (`ObieAccountCreateRequest`, `ObieTransactionCreateRequest`, `ObieAgent`,
  `ObieAccountRef`, `ObieUltimateParty`, `ObieCardInstrument`,
  `ObieProprietaryBankTransactionCode`, `ObieExtendedProprietaryCode`, `ObiePostalAddress`)

**`account/`**
- `AccountService` — maps raw OBIE DTOs to our own shapes; fans out per-account balance calls
  concurrently for `listAccounts`
- `AccountController` — `GET /api/accounts`, `GET /api/accounts/{id}`,
  `GET /api/accounts/{id}/balance`, `POST /api/accounts` (bonus demo-seeding)
- `AccountSummary` / `AccountDetail` / `BalanceResponse` — response DTOs

**`transaction/`**
- `TransactionCategoryClassifier` — MCC-code-first, keyword-fallback category classifier
- `TransactionService` — per-account history fetch + classify + filter (category/date) + sort
- `TransactionController` — `GET /api/accounts/{id}/transactions` (category/from/to/page/size),
  `POST /api/accounts/{id}/transactions` (bonus demo-seeding)
- `TransactionSummary` — response DTO

**`dashboard/`**
- `DashboardService` — accounts (with balances) + total balance + 10 most recent transactions
  across all accounts, built from concurrent per-account fan-out
- `DashboardController` — `GET /api/dashboard`
- `DashboardResponse` — response DTO

**`insights/`**
- `InsightsService` — spending summary, category breakdown, N-month trend, anomaly detection
  (mean + 2×stddev per category), rule-based health summary, subscription (recurring-merchant)
  detection, month-over-month overspending alerts, and category+month transaction lookup for
  the AI tool layer. Every metric has an `accountId`-scoped overload (`null` = all accounts).
- `InsightsController` — `GET /api/insights/spending-summary`, `/category-breakdown`, `/trend`,
  `/anomalies`, `/health-summary`, `/subscriptions`, `/overspending-alerts` — all accept an
  optional `accountId` query param
- DTOs — `SpendingSummary`, `CategoryBreakdown`/`CategoryBreakdownResponse`,
  `MonthlyTrend`/`TrendResponse`, `AnomalyTransaction`, `HealthSummary`, `SubscriptionCandidate`,
  `OverspendingAlert`

**`ai/`**
- `ChatClientConfig` — `ChatClient` bean (Ollama-backed) with a grounded system prompt, plus a
  separate `ChatMemory` bean (`MessageWindowChatMemory`, in-memory, 20-message window)
- `FinancialTools` — 10 `@Tool`-annotated methods (`getAccountBalances`,
  `getTransactionsByCategory`, `getSpendingSummary`, `getCategoryBreakdown`, `getSpendingTrend`,
  `getAnomalies`, `getOverspendingAlerts`, `getSubscriptions`, `getHealthSummary`,
  `getPersonalizedRecommendations`), each reading `sessionId` from `ToolContext` and delegating
  straight to `AccountService`/`InsightsService` — see `CHAT_ASSISTANT_PROMPTS.md` for example
  prompts per tool
- `FinancialAssistantService` — `chat(...)`, `coachingTip(...)`, `recommendations(...)`, all
  wrapped in try/catch fallback so a slow/unreachable model degrades gracefully
- `ChatController` — `POST /api/ai/chat`, `GET /api/ai/coaching-tip`, `GET /api/ai/recommendations`
- DTOs — `ChatRequest`, `ChatResponse`, `CoachingTipsResponse`, `Recommendation`,
  `RecommendationsResponse`

**`common/`**
- `ApiResponse<T>` — the `{ success, data, error }` envelope every endpoint returns
- `GlobalExceptionHandler` — maps `SandboxAuthException`→401, validation errors→400, everything
  else→500, with logging
- `RequestLoggingFilter` — logs method/path/status/duration for every request, stamps a
  correlation id into SLF4J's MDC (`reqId`) so one request's log lines can be grepped together
- `Masking` — truncates session ids before logging (they're bearer credentials); sandbox
  access/refresh tokens are never logged at all
- `Concurrency` — `mapConcurrently(items, mapper)`, fans a list out across virtual threads
- `PagedResult<T>` — generic in-memory pagination wrapper
- `RestClientConfig` — two `RestClient` beans (auth + AIS base URLs) with connect/read timeouts
- `common/exception/SandboxAuthException` — thrown on sandbox auth failure

**`config/`**
- `SecurityConfig` — wires `SessionAuthFilter`, `RequestLoggingFilter`, and CORS into the
  security filter chain
- `CorsConfig` — exposes the `CorsConfigurationSource` bean consumed by `SecurityConfig`
- `SandboxProperties` — `@ConfigurationProperties(prefix = "sandbox")` record

</details>

---

## Feature highlights

| Capability | Endpoint(s) | Notes |
|---|---|---|
| Login / session | `POST /api/auth/login`, `/logout` | Opaque `sessionToken`, never the raw sandbox token |
| Accounts & balances | `GET /api/accounts`, `/{id}`, `/{id}/balance` | Concurrent per-account balance fetch |
| Transactions | `GET /api/accounts/{id}/transactions` | Category/date filters, pagination, auto-classified category |
| Dashboard | `GET /api/dashboard` | One aggregated call: accounts + balances + 10 most recent transactions |
| Spending insights | `GET /api/insights/*` (7 endpoints) | Summary, category breakdown, trend, anomalies, health, subscriptions, overspending alerts — all optionally scoped to one `accountId` |
| AI chat assistant | `POST /api/ai/chat` | 10-tool tool-calling, grounded in live data, remembers conversation |
| AI coaching tip | `GET /api/ai/coaching-tip` | 2-3 tips from current-month snapshot |
| Personalized recommendations | `GET /api/ai/recommendations` | 3-5 structured recommendations from a 6-month trend |
| Demo seeding (bonus) | `POST /api/accounts`, `POST /api/accounts/{id}/transactions` | Pass-through to sandbox create endpoints |

Full request/response examples: **`API_REFERENCE.md`**. Demo script for the AI assistant:
**`CHAT_ASSISTANT_PROMPTS.md`**.

---

## Key design decisions

**Concurrency — virtual threads for per-account fan-out.** Per-account sandbox calls (balances,
transactions) run concurrently rather than looped sequentially: `common/Concurrency.mapConcurrently`
submits one virtual thread per item to `Executors.newVirtualThreadPerTaskExecutor()` and joins
results back in input order. Used by `AccountService.listAccounts`, `DashboardService.buildDashboard`,
and every multi-account `InsightsService` call.

**Resilience — retry only, no circuit breaker.** Every sandbox HTTP call goes through
Resilience4j retry (`application.yaml`, instances `sandboxToken`/`sandboxAis`): retries on
timeouts/5xx, never on 401 (a 401 instead triggers one token-refresh-and-retry). A circuit
breaker was tried and removed — see the [appendix](#appendix-issues-found-and-fixed-during-development)
for why. The AI layer wraps every Ollama call in its own try/catch fallback (apologetic message
for chat, rule-based tips for coaching) so a slow/unreachable local model can't crash a demo.

**Security — our own session layer, not Spring Security's.** `SessionAuthFilter` authenticates
by hand and writes straight into `SecurityContextHolder`; `UserDetailsServiceAutoConfiguration`
is explicitly excluded since it's otherwise dead weight (an unused in-memory user with a random
generated password). CORS is registered through `HttpSecurity.cors(...)`, not a bare
`WebMvcConfigurer`, so Spring Security's own `CorsFilter` runs first in the chain — see the
appendix for why that ordering matters.

---

## Why no persistence (and where it would slot in)

The hackathon brief prioritizes a working end-to-end vertical slice over exhaustive data
modeling, and every domain object (accounts, balances, transactions) already exists as the
source of truth in the sandbox — duplicating it into our own database would just be a cache with
extra steps, and one more thing to keep in sync live during judging. So for this phase:

- No JPA/H2/entities.
- The only in-memory state is `SandboxTokenService`'s session→token-bundle map (necessary — the
  frontend can't hold a real sandbox token) and Spring AI's windowed conversation memory.
- All sandbox I/O is isolated behind `SandboxAisClient`/`SandboxTokenService`, so a cache or
  database can be introduced later **without touching any controller** — the seam is already there.

Where persistence would slot in next, if this became a real product:

| Addition | Where it plugs in |
|---|---|
| Short-TTL cache (Caffeine, ~30-60s) | In front of `SandboxAisClient` — cuts sandbox round trips without changing any `account`/`transaction`/`insights` method signature |
| Persistent chat history | Swap `ChatClientConfig`'s `InMemoryChatMemoryRepository` for Spring AI's JDBC-backed `ChatMemoryRepository` (`spring-ai-starter-model-chat-memory-repository-jdbc`) — one bean change |
| User preferences / budgets / goals | A new table alongside the above, once users need saved state beyond what's computed live per request |

---

## Appendix: issues found and fixed during development

Kept for context — useful if asked "what went wrong along the way" during Q&A.

**Circuit breaker removed.** A real incident: the sandbox token endpoint had a run of
intermittent failures during team testing, which tripped the Resilience4j circuit breaker on
`sandboxToken` open — and once open, it immediately threw `CallNotPermittedException` for every
subsequent call for the configured 10s window, even after the sandbox recovered, surfacing as a
generic 500. Conclusion: a circuit breaker isn't the right fit at hackathon-demo call volumes —
its benefit (stop hammering a dying downstream service) doesn't apply, but its downside (a
guaranteed self-inflicted outage window after any transient blip) does. Removed entirely; kept
retry, which has no such downside.

**CORS preflight was being 401'd.** CORS was originally registered via a plain `WebMvcConfigurer`,
which runs too late — Spring Security's own filter chain (`SessionAuthFilter`) intercepted the
browser's `OPTIONS` preflight request first and rejected it (no `Authorization` header on a
preflight), which the browser reported as a CORS error. Fixed by registering a
`CorsConfigurationSource` through `HttpSecurity.cors(...)` instead, so Spring Security's own
`CorsFilter` runs first, ahead of every other filter — plus a defensive `OPTIONS` bypass directly
in `SessionAuthFilter` as a second line of defense.

**A wrongly-scoped chat memory advisor broke non-conversational AI calls.** `ChatClientConfig`
originally registered `MessageChatMemoryAdvisor` as a **default** advisor on the shared
`ChatClient` bean. That advisor requires a `conversationId` on every call it wraps — fine for the
conversational `chat()` flow, but `coachingTip()`/`recommendations()` are single-shot stateless
prompts that never set one, so every call to them threw
`IllegalArgumentException: conversationId cannot be null` internally. Their try/catch fallback
silently absorbed this, and the fallback text was plausible enough that it went unnoticed through
initial testing — an early "coaching-tip is working" confirmation was actually the fallback path
the whole time, not real model output. Fixed by removing the advisor from the client's defaults
and adding it only per-call inside `chat()`, where a `conversationId` genuinely exists.
