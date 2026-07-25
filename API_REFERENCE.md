# API Reference — Fintech Backend

For the React frontend team. Covers everything implemented through **Phase 5** (auth, accounts,
balances, transactions, dashboard, insights). The AI chat assistant (Phase 6) isn't built yet —
this doc will be extended when it lands.

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

> **Demo-data caveat, read before building charts against these:** the sandbox's seed data marks
> almost every transaction as `creditDebitIndicator: "Credit"`, so `totalExpense` will show `0`
> and everything looks like "income" until some `Debit` transactions exist. Seed data also mostly
> shares one merchant category code (`1711` → `"Home Services"`), so `category-breakdown` will
> look monotonous, and merchant names are randomly generated per transaction so `subscriptions`
> will likely return an empty list. None of this is a bug — it's what the current sandbox seed
> data looks like. Ask the backend team about seeding more varied demo data before a judging
> walkthrough if these need to visibly show something.

### `GET /api/insights/spending-summary?month=YYYY-MM`

`month` is optional, defaults to the current month.

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

### `GET /api/insights/category-breakdown?month=YYYY-MM`

`month` optional, defaults to current month. `percentageOfTotal` is of that month's total
transaction volume, sorted highest-first.

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

### `GET /api/insights/trend?months=6`

Oldest-first, one entry per month, zero-filled for months with no transactions — safe to feed
straight into a bar/line chart without gap-filling client-side.

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

### `GET /api/insights/anomalies`

Transactions that exceed their own category's mean + 2×standard-deviation. A category needs at
least 3 transactions before it's considered at all (otherwise stats are meaningless) — so this
frequently returns `[]` on lightly-seeded accounts, as it does today:

```json
{ "success": true, "data": [], "error": null }
```

Shape when non-empty: `{ transaction: <TransactionSummary>, categoryMean, categoryStdDev, deviationMultiple }`.

### `GET /api/insights/health-summary`

Rule-based snapshot — no AI yet (Phase 6 layers AI-generated coaching on top of this same data).

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

### `GET /api/insights/subscriptions`

Recurring same-merchant, similarly-sized (±15%), roughly-monthly (20-40 day interval)
transactions. Empty today (see the demo-data caveat above):

```json
{ "success": true, "data": [], "error": null }
```

Shape when non-empty: `{ merchantName, averageAmount, currency, occurrenceCount, estimatedFrequencyDays, lastBookingDateTime }`.

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

---

## Not yet available

- **AI chat assistant** (`/api/ai/chat`, `/api/ai/coaching-tip`) — Phase 6, not started.

This doc will be updated as that lands — check back before building against it.
