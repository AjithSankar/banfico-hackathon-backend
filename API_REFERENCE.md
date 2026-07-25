# API Reference — Fintech Backend

For the React frontend team. Covers everything implemented through **Phase 6** (auth, accounts,
balances, transactions, dashboard, insights, AI chat assistant).

## Base URL

```
http://localhost:9091
```

CORS is open for `http://localhost:5173` (Vite's default dev port) by default — configurable via
the `APP_CORS_ALLOWED_ORIGINS` env var (comma-separated) if your dev server runs elsewhere.

## Response envelope

Every endpoint returns the same wrapper:

```json
{ "success": true, "data": { ... }, "error": null }
```

On failure:

```json
{ "success": false, "data": null, "error": "human-readable message" }
```

`401` responses (missing/invalid/expired session token) also come back in this shape — treat any
`401` as "redirect to login", regardless of the `error` text.

Interactive docs/try-it-out: **`http://localhost:9091/swagger-ui.html`**.

---

## Authentication

There is no registration flow — the sandbox's test users are pre-provisioned (you'll be given a
username/password to test with).

### `POST /api/auth/login`

Request:
```json
{ "username": "nivas.ganesan+aihackathonteamd@banfico.com", "password": "..." }
```

Response:
```json
{
  "success": true,
  "data": { "sessionToken": "3f2a1c9e-...-uuid", "expiresInSeconds": 300 },
  "error": null
}
```

**`sessionToken` is what you store and send on every other request — never anything else.**
It's an opaque id, not a JWT you can decode; treat it as a bearer secret. `expiresInSeconds` is
informational only (reflects the backend's internal token lifetime with the banking sandbox) —
the backend refreshes it transparently, so the frontend doesn't need to track or renew it.

Store it in memory (a React context/store) or `sessionStorage`. Avoid `localStorage` if you can —
it survives tab close and is a slightly larger XSS blast radius, but either works for the
hackathon.

### Using the session token

Every other endpoint below requires:

```
Authorization: Bearer <sessionToken>
```

### `POST /api/auth/logout`

No body. Requires the `Authorization` header (you can't log out a session you don't hold).

```json
{ "success": true, "data": null, "error": null }
```

Clear your stored `sessionToken` after this call.

### Session expiry

A session token has no fixed expiry — it stays valid as long as it's used again within ~30
minutes of the last request. If a request 401s:
1. Clear the stored `sessionToken`.
2. Redirect to the login screen.

There is currently no distinction in the error body between "never logged in", "logged out", and
"expired from inactivity" — all three are a `401`. Handle them identically on the frontend.

---

## Accounts

### `GET /api/accounts`

Lists all accounts for the logged-in user, each with its current balance already resolved.

```json
{
  "success": true,
  "data": [
    {
      "id": "6a61ff92c47905bfc3f1961f",
      "nickname": "Bills",
      "type": "CACC",
      "currency": "GBP",
      "balance": 329.06,
      "maskedIdentification": "**********7092"
    }
  ],
  "error": null
}
```

`balance` is `null` if the sandbox has no balance record for that account yet (can happen for a
freshly-seeded demo account). `type` is the OBIE account type code (e.g. `CACC` = current
account) — there's currently only one type in the sandbox data, so don't build type-specific UI
branching on it yet.

### `GET /api/accounts/{accountId}`

Single account detail — same fields as the list plus a few extras.

```json
{
  "success": true,
  "data": {
    "id": "6a61ff92c47905bfc3f1961f",
    "nickname": "Bills",
    "type": "CACC",
    "accountCategory": "Personal",
    "status": "Enabled",
    "currency": "GBP",
    "balance": 329.06,
    "maskedIdentification": "**********7092",
    "openingDate": "2002-01-05T00:00:00.000Z",
    "servicerName": "ServicerName"
  },
  "error": null
}
```

### `GET /api/accounts/{accountId}/balance`

```json
{
  "success": true,
  "data": {
    "accountId": "6a61ff92c47905bfc3f1961f",
    "amount": 329.06,
    "currency": "GBP",
    "creditDebitIndicator": "Credit",
    "type": "CLAV",
    "asOf": "2023-04-05T10:43:07.000Z"
  },
  "error": null
}
```

`creditDebitIndicator` is `Credit` or `Debit`. `type` is the OBIE balance type code (`CLAV` =
closing available, etc.) — display as a tooltip/label, not something to branch UI logic on.

### `POST /api/accounts` — demo-seeding only, not a real user feature

Bonus pass-through to the sandbox's account-creation endpoint, mainly for seeding demo data
before a walkthrough. Skip this in the main app UI — if you want an "add account" admin/dev
tool, ask before building against it since the request body is the full raw OBIE shape (verbose,
not something to hand-build in a form).

---

## Transactions

### `GET /api/accounts/{accountId}/transactions`

Query params (all optional):

| param      | type                  | notes                                              |
|------------|-----------------------|-----------------------------------------------------|
| `category` | string                | exact match against the classified `category` field |
| `from`     | `YYYY-MM-DD`          | inclusive                                            |
| `to`       | `YYYY-MM-DD`          | inclusive                                            |
| `page`     | int, default `0`      | zero-indexed                                         |
| `size`     | int, default `20`     |                                                       |

Response:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "6a6447b5c47905bfc3f19830",
        "accountId": "6a61ff92c47905bfc3f1961f",
        "amount": 186.97,
        "currency": "GBP",
        "creditDebitIndicator": "Credit",
        "status": "PDNG",
        "bookingDateTime": "2026-07-25T05:20:53.668Z",
        "description": "Paid the gas bill",
        "merchantName": "Glover - Harris",
        "category": "Utilities"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3
  },
  "error": null
}
```

- `category` is derived server-side (merchant category code, falling back to keyword matching on
  the description) — current known values: `Groceries`, `Dining`, `Utilities`, `Home Services`,
  `Shopping`, `Transport`, `Entertainment`, `Subscriptions`, `Cash Withdrawal`, `Transfers`,
  `Housing`, `Income`, `Other`. Treat this as an open-ended string list, not a fixed enum — more
  values may be added.
- `status` is the raw OBIE status code (`PDNG` = pending, `BOOK` = booked/settled). Consider
  showing pending transactions with a visual distinction (e.g. italic/greyed).
- List is sorted newest-first already — no need to re-sort client-side.
- `totalElements` is the count *after* category/date filtering, so use it for pagination controls
  as-is.
- **Demo-data quirk:** transactions seeded through the Postman collection's demo-seed request all
  carry the same merchant category code (`1711`) regardless of their randomized description text
  ("Paid the gas bill", "Restaurant payment", etc.), so most seeded data classifies as
  `"Home Services"` even when the description suggests otherwise. That's expected — the category
  classifier prioritizes the real merchant category code over keyword matching, which is correct
  behavior; it's the sandbox's seed data that isn't varied. Don't be surprised if a demo account's
  category breakdown looks monotonous.

### `POST /api/accounts/{accountId}/transactions` — demo-seeding only, not a real user feature

Same caveat as account creation above — full raw OBIE request shape, meant for backend/demo use.

---

## Dashboard

### `GET /api/dashboard`

One aggregated call for the home screen — accounts (with balances) + total balance + the 10 most
recent transactions across *all* accounts, already merged and sorted newest-first.

```json
{
  "success": true,
  "data": {
    "accounts": [ /* same shape as GET /api/accounts */ ],
    "accountCount": 8,
    "totalBalance": 2140.55,
    "currency": "GBP",
    "recentTransactions": [ /* same shape as the transactions list, up to 10 items */ ]
  },
  "error": null
}
```

`totalBalance` sums every account's balance (nulls excluded). `currency` is taken from the first
account — fine for now since every sandbox account is GBP; flag it to the backend team if
multi-currency accounts show up later.

---

## Insights

Computed live from transactions across **all** accounts, fetched fresh on every call (no
persistence/caching yet — each of these endpoints re-fetches from the sandbox).

**Every endpoint below accepts an optional `accountId` query param.** Omit it and behavior is
unchanged (aggregated across all accounts, same as before). Pass an `accountId` (from
`GET /api/accounts`) and the same aggregation logic scopes to just that one account's
transactions instead — same response shape either way, just narrower data. Use this to power a
per-account view on the Insights page (e.g. an account selector) without a separate set of
endpoints.

> **Demo-data caveat, read before building charts against these:** the sandbox's seed data marks
> almost every transaction as `creditDebitIndicator: "Credit"`, so `totalExpense` will show `0`
> and everything looks like "income" until some `Debit` transactions exist. Seed data also mostly
> shares one merchant category code (`1711` → `"Home Services"`), so `category-breakdown` will
> look monotonous, and merchant names are randomly generated per transaction so `subscriptions`
> will likely return an empty list. None of this is a bug — it's what the current sandbox seed
> data looks like. Ask the backend team about seeding more varied demo data before a judging
> walkthrough if these need to visibly show something.

### `GET /api/insights/spending-summary?month=YYYY-MM&accountId=`

`month` is optional, defaults to the current month. `accountId` optional (see note above).

```json
{
  "success": true,
  "data": {
    "month": "2026-07",
    "currency": "GBP",
    "totalIncome": 320.85,
    "totalExpense": 0,
    "netChange": 320.85,
    "transactionCount": 4
  },
  "error": null
}
```

### `GET /api/insights/category-breakdown?month=YYYY-MM&accountId=`

`month` optional, defaults to current month. `accountId` optional (see note above).
`percentageOfTotal` is of that month's total transaction volume (within the scoped account if
`accountId` is given), sorted highest-first.

```json
{
  "success": true,
  "data": {
    "month": "2026-07",
    "currency": "GBP",
    "categories": [
      { "category": "Home Services", "totalAmount": 320.85, "transactionCount": 4, "percentageOfTotal": 100.00 }
    ]
  },
  "error": null
}
```

### `GET /api/insights/trend?months=6&accountId=`

`accountId` optional (see note above). Oldest-first, one entry per month, zero-filled for months
with no transactions — safe to feed straight into a bar/line chart without gap-filling
client-side.

```json
{
  "success": true,
  "data": {
    "currency": "GBP",
    "months": [
      { "month": "2026-02", "totalIncome": 0, "totalExpense": 0, "netChange": 0, "transactionCount": 0 },
      { "month": "2026-03", "totalIncome": 0, "totalExpense": 0, "netChange": 0, "transactionCount": 0 },
      { "month": "2026-07", "totalIncome": 320.85, "totalExpense": 0, "netChange": 320.85, "transactionCount": 4 }
    ]
  },
  "error": null
}
```

### `GET /api/insights/anomalies?accountId=`

`accountId` optional (see note above) — scopes the transaction history anomaly detection runs
against to just that account. Transactions that exceed their own category's mean +
2×standard-deviation. A category needs at least 3 transactions before it's considered at all
(otherwise stats are meaningless) — so this frequently returns `[]` on lightly-seeded accounts,
as it does today:

```json
{ "success": true, "data": [], "error": null }
```

Shape when non-empty: `{ transaction: <TransactionSummary>, categoryMean, categoryStdDev, deviationMultiple }`.

### `GET /api/insights/health-summary?accountId=`

Rule-based snapshot — no AI yet (Phase 6 layers AI-generated coaching on top of this same data).
`accountId` optional (see note above) — **when given, `totalBalance` and `currency` reflect
just that one account instead of the sum/first of all accounts** (the rest of the fields —
income/expense/net/savings-rate/top-category/observations — were already computed from that
account's transactions alone, same scoping as every other insights endpoint).

```json
{
  "success": true,
  "data": {
    "totalBalance": 20796.96,
    "currency": "GBP",
    "currentMonthIncome": 320.85,
    "currentMonthExpense": 0,
    "netChange": 320.85,
    "savingsRatePercent": 100.00,
    "topCategory": "Home Services",
    "observations": [
      "You're in the green this month — income covered expenses.",
      "Your top category this month is Home Services.",
      "Savings rate this month: 100.00%.",
      "No unusual spending detected."
    ]
  },
  "error": null
}
```

`savingsRatePercent` is `null` if there was no income that month (avoids a divide-by-zero).
`observations` is a plain string list meant to render directly — don't parse it for structured
data, that's what the other fields are for.

### `GET /api/insights/subscriptions?accountId=`

`accountId` optional (see note above). Recurring same-merchant, similarly-sized (±15%),
roughly-monthly (20-40 day interval) transactions. Empty today (see the demo-data caveat above):

```json
{ "success": true, "data": [], "error": null }
```

Shape when non-empty: `{ merchantName, averageAmount, currency, occurrenceCount, estimatedFrequencyDays, lastBookingDateTime }`.

---

## AI Assistant

Runs on a local Ollama model (`qwen2.5:7b`) — no external API key involved. Every call is
wrapped with fallback handling on the backend, so a slow/unreachable model degrades gracefully
(a friendly message for chat, rule-based tips for coaching) rather than erroring out.

### `POST /api/ai/chat`

```json
{ "message": "What is my total balance across all accounts?", "conversationId": null }
```

- `message` required.
- `conversationId` optional — **omit it and the backend defaults to your `sessionToken`**, so a
  logged-in user gets one continuous conversation thread without the frontend having to manage
  an id. Pass an explicit `conversationId` only if you want multiple separate chat threads per
  user (e.g. a "new conversation" button).

Response:
```json
{
  "success": true,
  "data": {
    "reply": "Your total balance across all 8 accounts is £20,796.96.",
    "conversationId": "d9634478-ab05-488c-acbd-84a0704d13c8"
  },
  "error": null
}
```

The model has live tool access to account balances, category/month-filtered transactions,
monthly spending summaries, anomaly data, and personalized recommendations (the same
rule-based data `GET /api/ai/recommendations` falls back to) — answers are grounded in real
numbers, not hallucinated. Send the returned `conversationId` back on the next call (or just
keep omitting it) to continue the same conversation; the backend remembers the last 20 messages
per conversation in memory (no persistence — lost on backend restart).

If the model itself fails or times out, `reply` will be an apologetic fallback string rather
than an HTTP error — check for that string if you want to show a "try again" affordance instead
of rendering it as a normal assistant message. (Exact fallback text may change; treat any AI
outage as non-fatal either way.)

### `GET /api/ai/coaching-tip`

No body/params. Returns 2-3 short actionable tips grounded in the user's current-month spending
summary, total balance, savings rate, top category, and anomaly count.

```json
{
  "success": true,
  "data": {
    "tips": [
      "You're saving 100% of your income this month — consider moving some of that surplus into your Savings account.",
      "Home Services made up all of your spending this month; keep an eye on it if that's not intentional.",
      "No unusual transactions detected — your spending pattern looks stable."
    ]
  },
  "error": null
}
```

If the AI call fails or returns no tips, `tips` falls back to the same rule-based observation
strings as `GET /api/insights/health-summary`'s `observations` field — always render `tips` as a
plain list either way, no need to branch on whether it came from the model or the fallback.

### `GET /api/ai/recommendations`

No body/params. The "Personalized AI Recommendations" feature — broader and deeper than
`coaching-tip`: instead of just the current month's snapshot, the prompt is grounded in a
**6-month income/expense (Credit vs Debit) trend** plus the **current month's category
breakdown**, so recommendations can reference trajectory ("income dropped two months running"),
not just a single point-in-time number. Returns 3-5 structured recommendations.

```json
{
  "success": true,
  "data": {
    "recommendations": [
      {
        "title": "Increase Income Streams",
        "description": "Income in the last two months has been significantly lower than the previous four, with a net loss of £11,690.52 in June and no income in April or May.",
        "category": "Income",
        "priority": "high"
      },
      {
        "title": "Boost Savings Rate",
        "description": "Your current savings rate is 51.68%, which is good, but there's room to increase it further given the high net income in July.",
        "category": "Savings",
        "priority": "medium"
      }
    ]
  },
  "error": null
}
```

- `category` is nullable — some recommendations (e.g. a general savings goal) aren't tied to one
  spending category.
- `priority` is `"high"` / `"medium"` / `"low"`.
- If the AI call fails or returns nothing, falls back to a rule-based recommendation per
  `health-summary` observation (title `"Financial health observation"`, `category` set to the
  user's top spending category, `priority` always `"medium"`) — same non-fatal-degradation
  contract as `coaching-tip`.
- **This same rule-based fallback logic is also exposed as a tool the `/api/ai/chat` assistant
  can call directly** (`getPersonalizedRecommendations`) — so asking the chat assistant something
  like *"can you give me some recommendations?"* is grounded in the same real data rather than
  the model inventing generic advice.

---

## Minimal React integration sketch

```js
const BASE_URL = "http://localhost:9091";

async function apiFetch(path, { token, ...options } = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  const body = await res.json();
  if (res.status === 401) {
    // clear stored token, redirect to /login
  }
  if (!body.success) {
    throw new Error(body.error ?? "Request failed");
  }
  return body.data;
}

// login
const { sessionToken } = await apiFetch("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ username, password }),
});

// authenticated calls
const dashboard = await apiFetch("/api/dashboard", { token: sessionToken });
```

All core and bonus backend phases (1-6) are implemented and verified live as of this writing.
Remaining work is cross-cutting polish and deliverables (README/architecture docs) — no new
endpoints expected, but check `IMPLEMENTATION_PLAN.md`'s Status section if something here seems
out of date.
