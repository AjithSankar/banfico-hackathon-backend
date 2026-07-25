# Architecture

## Overview

Every request flows `Controller → Service → SandboxAisClient (live HTTP) / AI layer`. There is
no database — account, balance, and transaction data is fetched from the Hackathon Mock Bank
sandbox fresh on every request, and financial insights are computed on the fly from whatever
transactions come back. See [Why no persistence](#why-no-persistence-and-where-it-would-slot-in)
for the reasoning and where a cache/DB would go later.

```
 React frontend (localhost:5173)
          |
          |  Authorization: Bearer <sessionToken>
          v
 +--------------------------------------------------------------------+
 |  Spring Boot backend (localhost:9091)                               |
 |                                                                      |
 |  CorsFilter -> RequestLoggingFilter -> SessionAuthFilter             |
 |  (security filter chain — see "Two-token model" below)              |
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
 |   (Resilience4j retry + circuit breaker)           |                  |
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

## Two-token model (why `sessionToken` ≠ sandbox `access_token`)

The frontend never sees the sandbox's real OAuth2 tokens. `SessionAuthFilter` and
`SandboxTokenService` together keep that boundary. Step by step:

1. Frontend sends `POST /api/auth/login` with `{ username, password }` (the end customer's real
   sandbox credentials).
2. `LoginController` generates a new opaque `sessionId` (a UUID) and calls
   `SandboxTokenService.login(sessionId, username, password)`.
3. `SandboxTokenService` exchanges the credentials with Keycloak (`grant_type=password`) and
   caches the returned `{ access_token, refresh_token, expires_in }` in memory, keyed by
   `sessionId` — a `ConcurrentHashMap<String, TokenBundle>`.
4. `LoginController` returns `{ sessionToken: sessionId, expiresInSeconds }` to the frontend.
   **The real sandbox tokens never leave the backend.**
5. Frontend calls any protected endpoint (e.g. `GET /api/dashboard`) with
   `Authorization: Bearer <sessionToken>`.
6. `SessionAuthFilter` extracts that token, calls `SandboxTokenService.getAccessToken(sessionId)`
   — which transparently refreshes the sandbox access token first if it's expiring soon — and,
   if valid, stores `sessionId` as the authenticated principal in Spring Security's
   `SecurityContextHolder` for the rest of the request.
7. The controller's service layer calls `SandboxAisClient`, which resolves the **real** access
   token from `SandboxTokenService` and calls the OBIE API with
   `Authorization: Bearer <real access_token>`.
8. If the sandbox responds `401`, `SandboxAisClient` calls `SandboxTokenService.refresh(sessionId)`
   once and retries the same call once; if it still fails, a `SandboxAuthException` propagates
   up and `GlobalExceptionHandler` maps it to a clean `401` (never a raw Keycloak error body).

`CurrentSession.sessionId()` is how every controller/service reads the authenticated session id
back out of `SecurityContextHolder` (set in step 6) — nobody re-parses the `Authorization` header
themselves.

## Components built

### `auth/` — our own thin session layer (no user DB)
- **`LoginController`** — `POST /api/auth/login`, `POST /api/auth/logout`
- **`LoginRequest` / `LoginResponse`** — request/response DTOs (`sessionToken`, `expiresInSeconds`)
- **`SessionAuthFilter`** — validates the bearer `sessionToken` on every `/api/**` request except
  `/api/auth/login`; bypasses `OPTIONS` (CORS preflight) explicitly
- **`CurrentSession`** — static helper to read the authenticated `sessionId` from `SecurityContextHolder`

### `sandbox/` — sandbox integration layer (blocking dependency for everything else)
- **`SandboxTokenService`** — Keycloak password/refresh grant exchange, in-memory session→token cache
- **`SandboxAisClient`** — typed wrapper over every OBIE endpoint (get/create accounts, balances,
  transactions), 401-triggers-refresh-and-retry-once, Resilience4j retry + circuit breaker
- **`TokenBundle`** — access/refresh token + expiry record
- **`sandbox/dto/`** — faithful raw OBIE request/response records matching the sandbox's exact
  JSON field names: `ObieAccount`, `ObieAccountData`, `ObieAccountsResponse`,
  `ObieAccountIdentification`, `ObieServicer`, `ObieStatementFrequency`, `ObieDeliveryAddress`,
  `ObieBalance`, `ObieBalanceData`, `ObieBalancesResponse`, `ObieTransaction`,
  `ObieTransactionData`, `ObieTransactionsResponse`, `ObieBankTransactionCode`,
  `ObieMerchantDetails`, `ObieTransactionBalance`, `ObieAmount`, `TokenResponse`, plus the
  create-request DTOs (`ObieAccountCreateRequest`, `ObieTransactionCreateRequest`,
  `ObieAgent`, `ObieAccountRef`, `ObieUltimateParty`, `ObieCardInstrument`,
  `ObieProprietaryBankTransactionCode`, `ObieExtendedProprietaryCode`, `ObiePostalAddress`)

### `account/` — flattened account/balance API
- **`AccountService`** — maps raw OBIE DTOs to our own shapes; fans out per-account balance
  calls concurrently for `listAccounts`
- **`AccountController`** — `GET /api/accounts`, `GET /api/accounts/{id}`,
  `GET /api/accounts/{id}/balance`, `POST /api/accounts` (bonus demo-seeding)
- **`AccountSummary` / `AccountDetail` / `BalanceResponse`** — response DTOs

### `transaction/` — transaction history + categorization
- **`TransactionCategoryClassifier`** — MCC-code-first, keyword-fallback category classifier
- **`TransactionService`** — per-account history fetch + classify + filter (category/date) + sort
- **`TransactionController`** — `GET /api/accounts/{id}/transactions` (category/from/to/page/size),
  `POST /api/accounts/{id}/transactions` (bonus demo-seeding)
- **`TransactionSummary`** — response DTO

### `dashboard/` — aggregated home-screen endpoint
- **`DashboardService`** — accounts (with balances) + total balance + 10 most recent
  transactions across all accounts, built from concurrent per-account fan-out
- **`DashboardController`** — `GET /api/dashboard`
- **`DashboardResponse`** — response DTO

### `insights/` — financial insights (all computed live, nothing cached)
- **`InsightsService`** — spending summary, category breakdown, N-month trend, anomaly
  detection (mean + 2×stddev per category), rule-based health summary, subscription
  (recurring-merchant) detection, and category+month transaction lookup for the AI tool layer
- **`InsightsController`** — `GET /api/insights/spending-summary`, `/category-breakdown`,
  `/trend`, `/anomalies`, `/health-summary`, `/subscriptions`
- **DTOs** — `SpendingSummary`, `CategoryBreakdown`/`CategoryBreakdownResponse`,
  `MonthlyTrend`/`TrendResponse`, `AnomalyTransaction`, `HealthSummary`, `SubscriptionCandidate`

### `ai/` — Spring AI chat assistant, coaching tips, and personalized recommendations
- **`ChatClientConfig`** — `ChatClient` bean (Ollama-backed) with a grounded system prompt, plus
  a separate `ChatMemory` bean (`MessageWindowChatMemory`, in-memory, 20-message window). The
  memory advisor is deliberately **not** a default advisor on the `ChatClient` — see the bug
  note in `IMPLEMENTATION_PLAN.md` Phase 6 (a default memory advisor requires a `conversationId`
  on every call, which broke the single-shot `coachingTip`/`recommendations` prompts). It's
  added per-call in `FinancialAssistantService.chat()` instead, where a `conversationId`
  genuinely exists.
- **`FinancialTools`** — `@Tool`-annotated methods (`getAccountBalances`,
  `getTransactionsByCategory`, `getSpendingSummary`, `getAnomalies`,
  `getPersonalizedRecommendations`) reading `sessionId` from `ToolContext` per call, delegating
  straight to `AccountService`/`InsightsService`. `getPersonalizedRecommendations` returns
  deterministic rule-based recommendations (built from `InsightsService.healthSummary`) rather
  than triggering a second nested AI call from within a tool — the outer chat call's own single
  LLM invocation phrases the natural-language reply using that data.
- **`FinancialAssistantService`** — `chat(...)` (tool-calling + per-call conversation memory,
  defaults `conversationId` to the session id), `coachingTip(...)` (structured-output prompt
  from the current month's live insights data), and `recommendations(...)` (structured-output
  prompt from a 6-month income/expense trend + current-month category breakdown — broader
  context than `coachingTip`). All three AI calls are wrapped in try/catch fallback so a
  slow/unreachable model degrades gracefully instead of erroring; `recommendations`' fallback
  reuses `FinancialTools.buildRuleBasedRecommendations` (the same method backing the
  `getPersonalizedRecommendations` tool) rather than duplicating the logic.
- **`ChatController`** — `POST /api/ai/chat`, `GET /api/ai/coaching-tip`,
  `GET /api/ai/recommendations`
- **DTOs** — `ChatRequest`, `ChatResponse`, `CoachingTipsResponse`, `Recommendation`,
  `RecommendationsResponse`

### `common/` — cross-cutting concerns
- **`ApiResponse<T>`** — the `{ success, data, error }` envelope every endpoint returns
- **`GlobalExceptionHandler`** — maps `SandboxAuthException`→401, validation errors→400,
  everything else→500, with logging
- **`RequestLoggingFilter`** — logs method/path/status/duration for every request, stamps a
  short correlation id into SLF4J's MDC (`reqId`) so one request's log lines can be grepped
  together across services
- **`Masking`** — truncates session ids before they're logged (they're bearer credentials for
  our own API); sandbox access/refresh tokens are never logged at all, not even truncated
- **`Concurrency`** — `mapConcurrently(items, mapper)`, fans a list out across virtual threads
  and joins results back in input order
- **`PagedResult<T>`** — generic in-memory pagination wrapper
- **`RestClientConfig`** — two `RestClient` beans (sandbox auth + AIS core API base URLs) with
  connect/read timeouts via `JdkClientHttpRequestFactory`
- **`common/exception/SandboxAuthException`** — thrown on sandbox auth failure

### `config/` — application configuration
- **`SecurityConfig`** — wires `SessionAuthFilter`, `RequestLoggingFilter`, and CORS into the
  Spring Security filter chain (CORS registered via `HttpSecurity.cors(...)`, not a plain
  `WebMvcConfigurer`, so preflight requests are answered before `SessionAuthFilter` ever runs)
- **`CorsConfig`** — exposes the `CorsConfigurationSource` bean consumed by `SecurityConfig`
- **`SandboxProperties`** — `@ConfigurationProperties(prefix = "sandbox")` record for
  domain/tenant/client-id/client-secret

## Concurrency

Per-account sandbox calls (balances, transactions) are fanned out across **virtual threads**
rather than looped sequentially — `common/Concurrency.mapConcurrently` submits one virtual
thread per item to `Executors.newVirtualThreadPerTaskExecutor()` and joins the results back in
input order. Used by:
- `AccountService.listAccounts` — one balance call per account, concurrently
- `DashboardService.buildDashboard` — one transaction-history call per account, concurrently
- `InsightsService` — same pattern, shared across whichever sub-metric a given insights endpoint needs

## Resilience

Every sandbox HTTP call goes through Resilience4j retry + circuit breaker (`application.yaml`,
instances `sandboxToken` and `sandboxAis`): retries on `ResourceAccessException`/
`HttpServerErrorException` (timeouts, 5xx) but **never** on 401 — a 401 instead triggers exactly
one token-refresh-and-retry in `SandboxAisClient`, then surfaces as a clean auth error if it
still fails. The AI layer (`FinancialAssistantService`) wraps every Ollama call in a try/catch
with a graceful fallback (an apologetic message for chat, rule-based tips for coaching) since a
slow/unreachable local model must not crash a live demo.

## Why no persistence (and where it would slot in)

The hackathon brief prioritizes a working end-to-end vertical slice over exhaustive data
modeling, and every domain object (accounts, balances, transactions) already exists as the
source of truth in the sandbox — duplicating it into our own database would just be a cache with
extra steps, and one more thing to keep in sync live during judging. So for this phase:

- No JPA/H2/entities.
- The only in-memory state is `SandboxTokenService`'s session→token-bundle map (necessary — the
  frontend can't hold a real sandbox token) and Spring AI's windowed conversation memory.
- All sandbox I/O is isolated behind `SandboxAisClient`/`SandboxTokenService`, so a cache or
  database can be introduced later **without touching any controller** — the seam is already
  there.

Where persistence would slot in next, if this became a real product:
- **A short-TTL cache (Caffeine, ~30-60s) in front of `SandboxAisClient`** — cuts sandbox round
  trips for repeated dashboard/insights calls within a session, without changing any public
  method signature in `account`/`transaction`/`insights`.
- **A chat-history store** — `ai/ChatClientConfig`'s `MessageWindowChatMemory` is currently
  backed by `InMemoryChatMemoryRepository`; Spring AI ships a JDBC-backed `ChatMemoryRepository`
  (`spring-ai-starter-model-chat-memory-repository-jdbc`) as a drop-in replacement if
  conversations need to survive a restart — swap the one bean in `ChatClientConfig`, nothing
  else changes. Similarly, a user-preferences table would sit alongside this once individual
  users need saved budgets/goals rather than everything being computed live per request.
