# AI Chat Assistant — Demo Prompts

A script of prompts to try against `POST /api/ai/chat` (and two related endpoints) during the
judging demo. Organized by capability so you can pick a subset that fits your time slot. Each
entry notes which tool it's expected to trigger — useful if you want to narrate "the assistant
is now calling `getSpendingTrend`..." while demoing, and for debugging if an answer looks off
(check the app console for `Tool <name> sessionId=...` debug lines to confirm the right tool fired).

## Before the demo

1. Ollama running locally with `qwen2.5:7b` pulled (`ollama pull qwen2.5:7b`).
2. Log in once, keep the `sessionToken` handy for the whole demo session (see `README.md`/
   `API_REFERENCE.md` for the login `curl`).
3. **Known demo-data quirks** (see `IMPLEMENTATION_PLAN.md` Phase 4/5 notes) — don't be caught
   off guard by these, they're expected:
   - Most transactions are `CreditDebitIndicator: Credit`, so "expense" numbers are often `0` —
     lean on category breakdown / balance questions rather than "how much did I spend" if your
     seed data hasn't had any Debit transactions added.
   - Most seeded transactions share one merchant category code (`1711` → `"Home Services"`), so
     category breakdown may look monotonous unless more varied demo data was seeded.
   - Only the most recent month or two typically has real transaction data — older months in a
     trend query will show zeros. This actually makes **overspending alerts** and **new spending
     category** detection look *more* interesting (everything in the one active month reads as
     "new" vs. an all-zero prior month), so that's a good one to lean on.
   - `anomalies` and `subscriptions` need enough transaction volume/history to say anything —
     they may legitimately return empty on lightly-seeded data. That's correct behavior, not a
     bug — mention this if asked, or seed a few more transactions first via the bonus
     `POST /api/accounts/{id}/transactions` endpoint (see `API_REFERENCE.md`).

---

## 1. Account balances & overview
*Triggers: `getAccountBalances`*

- "What is my total balance across all accounts?"
- "Which of my accounts has the highest balance?"
- "List all my accounts and their balances."
- "How many accounts do I have?"

## 2. Spending summary (income vs. expense)
*Triggers: `getSpendingSummary`*

- "What did I spend this month?"
- "How much income did I receive in July 2026?" *(use whatever month has real data)*
- "What's my net change this month — am I up or down?"

## 3. Category-wise spending
*Triggers: `getCategoryBreakdown`, `getTransactionsByCategory`*

- "Break down my spending by category this month."
- "What percentage of my spending went to Home Services?"
- "Show me all my transactions in the Groceries category."
- "What did I spend on Utilities last month?"

## 4. Spending trends over time
*Triggers: `getSpendingTrend`*

- "Show me my income and expense trend over the last 6 months."
- "Has my spending been going up or down recently?"
- "Compare my income this month to 3 months ago."

## 5. "Am I overspending?" (the headline ask)
*Triggers: `getOverspendingAlerts`, sometimes combined with `getSpendingTrend`/`getCategoryBreakdown`*

- "Am I overspending in any category compared to last month?"
- "Which spending categories increased the most recently?"
- "Did I start spending on anything new this month?"
- "Is my spending under control?"

## 6. Unusual transactions
*Triggers: `getAnomalies`*

- "Have there been any unusual transactions recently?"
- "Did anything look out of place compared to my normal spending?"

## 7. Subscriptions & recurring charges
*Triggers: `getSubscriptions`*

- "Do I have any subscriptions you can detect from my transaction history?"
- "Are there any recurring charges I should know about?"

## 8. Financial health & advice
*Triggers: `getHealthSummary`, `getPersonalizedRecommendations`*

- "How healthy is my financial situation right now?"
- "What's my savings rate this month?"
- "Give me some advice to improve my finances."
- "Can you give me some personalized recommendations?"
- "What's my top spending category and should I be worried about it?"

## 9. Multi-turn conversation (shows conversation memory)

Send these as separate `/api/ai/chat` calls **without** passing `conversationId` (it defaults to
your session, so it's remembered automatically):

1. "What is my total balance across all accounts?"
2. "Which one of those accounts has the highest balance?" *(references "those accounts" from
   the previous answer — proves memory works)*
3. "Break down the spending on that account by category." *("that account" — again referencing
   context from turn 2)*

## 10. Direct endpoints (outside the chat, for judges who want to see raw structured output)

- `GET /api/ai/coaching-tip` — 2-3 short AI tips from the current month's numbers.
- `GET /api/ai/recommendations` — 3-5 structured recommendations grounded in a 6-month trend.
- `GET /api/insights/overspending-alerts` — the raw data backing section 5 above.
- Add `?accountId=<id>` to any `/api/insights/*` endpoint (get an id from `GET /api/accounts`)
  to demo the per-account scoping — e.g. `GET /api/insights/health-summary?accountId=...`.

---

## If an answer looks generic or wrong

- Check the console for `Tool <name> sessionId=...` — if no tool log line appears, the model
  answered from its own reasoning instead of calling a tool (rare with a direct data question,
  more likely with vague prompts — try rephrasing more specifically, e.g. "this month" → an
  explicit month).
- If `/api/ai/chat`'s reply is the generic fallback ("Sorry, I couldn't process that just now
  ..."), Ollama likely isn't running or the model isn't pulled — check `ollama serve` /
  `ollama list`.
- `coaching-tip`/`recommendations` falling back to a `"Financial health observation"` /
  `"category": "..."` templated response instead of a natural-sounding one means the AI call
  failed and the rule-based fallback kicked in — check the console for the corresponding
  `AI ... call failed` error line.
