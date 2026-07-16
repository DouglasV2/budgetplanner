# Data protection — Art. 30 record, legal bases, subprocessors, retention, DPIA & breach runbook

This is the controller's internal data-protection record for BudgetSpace. It doubles as the GDPR
**Art. 30 record of processing** (the sub-250-employee Art. 30(5) exemption does not apply here because
the processing is not occasional — accounts, sessions and AI usage are processed continuously). Keep it
current when a processing purpose, subprocessor or retention period changes.

> Status: free, non-commercial **beta**. Billing (Stripe) and the AI assistant (Gemini) are **off by
> default** and are marked *dormant* below. The transfer/DPA items for those become live obligations the
> moment they are switched on — see [legal-launch-checklist.md](legal-launch-checklist.md).

## 1. Controller

- **Controller:** Bruno Pušić — see `frontend/src/legal.ts` → `OPERATOR`.
- **Contact:** budgetspace.ai@gmail.com
- **Supervisory authority:** Croatia — AZOP (Agencija za zaštitu osobnih podataka). For DE/AT users, the
  competent authority is their national/Land DPA.

## 2. Processing purposes, data categories & legal basis (Art. 6)

| # | Purpose | Data categories | Legal basis (Art. 6) | Notes |
|---|---------|-----------------|----------------------|-------|
| 1 | Account / sign-in | Google `sub`, name, email, picture | 6(1)(b) contract | Refreshed on each login (auto-rectification). |
| 2 | Saved plans | Room/budget/style inputs, plan contents, owner key | 6(1)(b) contract | Owner = `user:<id>` (account) or `guest:<browserId>`. |
| 3 | AI prompt parsing | Free-text prompt the user types | 6(1)(b) contract (on request) | **Dormant** (`BUDGETSPACE_AI_ENABLED=false`). Prompt sent to Gemini, **not persisted** locally. |
| 4 | Error monitoring | Exception/stacktrace (ERROR level only) | 6(1)(f) legitimate interest | Sentry; request bodies/prompts are **not** shipped. |
| 5 | Product-click & plan-feedback analytics | planId, productId, retailer, feedback value | 6(1)(f) legitimate interest | Pseudonymous — **no** user id, email or IP stored. |
| 6 | Plus/Design-Session waitlist | Optional email | 6(1)(a) consent | `plus_interest`. |
| 7 | Market/currency selection | Approximate country from CDN header | 6(1)(f) legitimate interest | IP is read from a header, **not stored**. |
| 8 | Billing (future) | Stripe customer/subscription id | 6(1)(b) contract | **Dormant** until a paid Design Session launches. |

## 3. Recipients / subprocessors

| Processor | Purpose | Country | Transfer safeguard | Status |
|-----------|---------|---------|--------------------|--------|
| Google (Identity) | Google Sign-In | US | Google DPA + EU-US DPF / SCCs | Live |
| Google (Gemini API) | AI prompt processing | US | **Paid-tier Gemini API — no training on prompts + Google Data Processing Addendum** | Live when AI is on — **paid tier confirmed** (billing account linked, prepay, "Paid 2"). Ensure the app's key is from a project under that billing account. |
| Stripe | Payment processing | US/EU | Stripe DPA + SCCs | Dormant |
| Sentry | Error monitoring | US | Sentry DPA + SCCs | Live (DSN blank ⇒ off) |
| eBay | Public used-listings **source** | — | **Not a recipient** — we send eBay no personal data | Dormant (feature hidden) |

**Gemini note:** the paid tier of the Gemini API (billing linked / prepaid) excludes training on your
prompts and processes them under Google's Data Processing Addendum (Google as processor) — this is the
GDPR transfer/processor basis. A **free-tier** key does the opposite (prompts used to improve models +
human review), so the only action is to confirm the project behind the key is on the **paid tier**, not to
switch products. Keep a note that you rely on Google's DPA + paid-tier data-use terms. See the checklist.

## 4. International transfers

Google, Stripe and Sentry process in the US. Transfers rely on the **EU-US Data Privacy Framework** (for
DPF-certified entities) and/or **Standard Contractual Clauses**. This is disclosed to users in the Privacy
Policy ("Who we share with"). Keep a copy of each provider's SCCs/DPF certification on file.

## 5. Retention

| Data | Retention | Mechanism |
|------|-----------|-----------|
| Auth sessions | Absolute TTL + idle timeout | `AuthSessionCleanupService` (scheduled) |
| AI usage ledger | ~45 days | `AiUsageTracker` prune (scheduled) |
| product_clicks / plan_feedback / plus_interest | 18 months | `RetentionCleanupService` (scheduled) |
| Account + saved plans + waitlist email | Until account deletion | In-app "Delete account" (GDPR Art. 17) |

Account deletion erases: saved plans, all sessions, Plus waitlist (by email),
the AI-usage rows (by owner key), and the account row.

## 6. Data-subject rights (Art. 15–22)

- **Erasure (Art. 17):** self-service "Delete account" in the app. Verified by `AuthServiceTest`.
- **Rectification (Art. 16):** profile fields auto-update on each Google sign-in; other corrections by email.
- **Access & portability (Art. 15/20):** on request by email — the per-user dataset is small (profile +
  saved plans). *Optional enhancement:* wire the `GET /api/auth/account/export`
  endpoint to a footer button for self-service (see checklist).
- **Objection / restriction (Art. 18/21):** by email.
- **Response deadline:** one month (Art. 12(3)).

## 7. DPIA screening (Art. 35)

A full DPIA is **not required**: no large-scale processing of special-category data, no systematic
large-scale monitoring, no automated decisions with legal/similar effect. The AI assistant parses a
free-text furnishing prompt into a structured intent; users are advised (in the Privacy Policy and at the
input) not to enter sensitive personal data. Re-screen if this changes (e.g. profiling, or enriching the
AI context with account data).

## 8. Personal-data-breach runbook (Art. 33/34)

1. **Detect** — Sentry ERROR alerts, host/DB monitoring, provider notices.
2. **Contain & assess** — what data, whose, how many, likely risk to individuals.
3. **Clock** — from *awareness*, notify AZOP within **72 hours** if the breach is likely to risk
   individuals' rights (Art. 33). Note the awareness timestamp.
4. **Notify users** (Art. 34) — without undue delay if the breach is likely to result in a **high** risk
   (e.g. exposed credentials/emails at scale).
5. **Log** — keep a breach record regardless of notification: what happened, when discovered, data
   categories, scope, remediation. AZOP contact: azop.hr.

Owner of this procedure: the controller named in §1.
