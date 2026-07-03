# Legal launch checklist

Derived from the 2026-07-03 legal/compliance audit. Verdict: a free public beta is **"go with fixes"**.
This tracks what has been fixed in code vs what still needs the **owner** (data you must provide, accounts
you must set up, a one-off lawyer review). See also [data-protection.md](data-protection.md).

Legend: ✅ done in code · ⚠️ needs owner action · ⏳ pre-charge / pre-feature gate.

---

## A. Before promoting the site publicly (applies even while free)

- ⚠️ **Fill the operator identity.** Open `frontend/src/legal.ts` → `OPERATOR.name` and enter a real full
  name (natural person) or registered entity. This is the single field that closes the Impressum **and**
  the GDPR "controller identity" gap at once (e-Commerce Dir. Art. 5 + GDPR Art. 13). Also update the
  controller name in `docs/data-protection.md` §1. *Nothing else in this section is truly done until this
  is filled — the code renders `[POPUNITI…]` until you do.*
- ✅ **German Impressum / legal docs.** DE/AT no longer get the English stub — `legal.ts` now ships a full
  German Datenschutzerklärung / Nutzungsbedingungen / Impressum, returned for the German locale. (Renders
  the same `OPERATOR` identity, so filling the name above covers DE too.)
- ✅ **Trademark / non-affiliation disclaimer.** Added to the Terms (HR/EN/DE) + a footer line: "not
  affiliated with IKEA/JYSK/eBay; trademarks belong to their owners."
- ✅ **Security headers + CSP** on the nginx-served frontend (clickjacking, MIME-sniff, HSTS, CSP).
- ✅ **Point-of-display price/availability disclaimer** shown on the results, not just buried in Terms.
- ✅ **Privacy Policy accuracy** — honest cookie/local-storage inventory (bs_auth, bs_oauth, localStorage),
  eBay described as an inbound source (not a recipient), Google/Gemini named, US-transfer basis stated,
  Art. 6 legal basis per purpose.
- ⚠️ **One-off lawyer review** of the Privacy Policy + Terms. The substance is complete and honest; this is
  cheap insurance. Recommended before broad promotion, required before charging.

## B. Before real traffic / before enabling the AI assistant

- ✅ **Beta-mode gates billing server-side.** `BillingController` now refuses checkout/confirm and no-ops
  the webhook while `BUDGETSPACE_BETA_MODE=true`, so keys-set-but-still-beta cannot charge.
- ✅ **EU AI Act Art. 50 notice** ("powered by AI…") at the prompt input + an "AI suggestions are estimates,
  not professional advice" disclaimer on the AI insight card.
- ✅ **AI provider — paid tier confirmed.** The billing account behind the Gemini key is on the **paid tier**
  ("Paid 2", prepay), so Google excludes training on prompts and processes them under its Data Processing
  Addendum — that IS the transfer/processor basis. (A free-tier key would do the opposite.) Only residual
  check: ensure the app's `GEMINI_API_KEY` is from a project under that paid billing account.
- ✅ **Honest User-Agent** on the live price probe (no more spoofed Chrome UA — matches the sourcing policy).
- ⏳ **Product images / IKEA-JYSK data → official/affiliate feeds.** Still hotlinked from retailer CDNs and
  fetched from public pages. Defensible for a free beta (takedown-on-notice), but migrate to official /
  affiliate feeds before charging (commercial use weakens the embedding defense). The `imageVerified`
  seam already lets you fall back to a generic illustration if a retailer objects.

## C. Before the first paid "Design Session" (all currently dormant)

- ⏳ **Reconcile the billing model.** Checkout still hard-codes `mode=subscription` while the Terms promise
  a one-time payment. Before charging, switch `BillingService.createCheckoutUrl` to `mode=payment` against
  a one-time price (and drop the subscription/dunning path from the live flow), or rewrite the Terms to
  disclose recurrence. Fix the `/mo` pricing copy to match.
- ⏳ **Complete the Impressum as a trader:** `OPERATOR.entity` + `OPERATOR.address` + OIB/VAT; registered
  obrt/d.o.o.; delete the "non-commercial / we do not charge" wording the moment billing flips on.
- ⏳ **Consumer rights (CRD):** add the 14-day right-of-withdrawal information + model form, and capture
  explicit (unchecked-by-default) consent to immediate performance + acknowledgement of losing the
  withdrawal right; send a durable-medium (email) confirmation.
- ⏳ **VAT / OSS:** confirm the EU €10k micro-threshold with an accountant; enable Stripe Tax; decide
  tax-inclusive vs exclusive pricing; handle non-EU markets (NO, GB); meet Croatian fiscalizacija.
- ⏳ **Invoicing/receipts:** enable Stripe invoice/receipt emailing; run the 3DS/SCA test-card smoke.
- ⏳ **Sponsored/affiliate disclosure.** The API now **forces `sponsored=false` / `affiliateUrl=null`** at
  the DTO boundary, so nothing paid can be shown undisclosed today. The change that first ships a paid
  placement MUST, in the same PR: render a visible "Sponsored/Oglas/Anzeige" badge, gate ranking so a paid
  item never outranks the best organic pick, and disclose the affiliate relationship at the link.

## D. Second-hand (eBay) — gates before re-enabling `SHOW_SECOND_HAND`

- ⏳ Record that the eBay Buy-API application was approved + the eBay API License / no-persist declaration
  accepted; **rotate the compromised PROD Cert** flagged in DEPLOY.md before any PROD key is set.
- ⏳ Add eBay attribution ("Results from eBay" + wordmark) on the listing card; keep the buyer-beware
  disclaimer; keep eBay keys blank until the section is actually shown.

## E. Done in code (GDPR / security hardening)

- ✅ **Erasure completeness** — account deletion now also erases the AI-usage ledger rows (`ai_usage_events`).
- ✅ **Retention** — scheduled purge for `product_clicks` / `plan_feedback` / `plus_interest` (18 months).
- ✅ **Cookie `Secure` fail-safe** — the session cookie is now `Secure` whenever the request arrives over
  HTTPS, even if the env flag was forgotten (never removes Secure).
- ✅ **CSRF defense-in-depth** — the client now sends a custom header on account-delete/checkout/confirm
  (on top of the existing SameSite=Lax + strict CORS preflight that already block cross-site forgery).
- ✅ **Dead code removed** — `GoogleSignInButton.tsx` (would have reintroduced a Google third-party
  cookie/consent duty if ever rendered).
- ✅ **Art. 30 record, DPIA screening, breach runbook** — see [data-protection.md](data-protection.md).

## F. Optional / low priority

- ⏳ **Self-host Google Fonts** (currently hotlinked from Google CDN → visitor IP leak; LG München pattern).
  Download the two woff2 fonts (or `@fontsource`) and drop the three `<link>` tags in `index.html`; then
  tighten the CSP `font-src`/`style-src` to `'self'`. Needs npm/network — an owner/dev task.
- ⏳ **Self-service data export** — a `GET /api/auth/account/export` endpoint + footer button (the manual
  email channel already satisfies the legal duty).
- ⏳ **Backend CSRF enforcement** — require the custom header server-side on the state-changing writes
  (currently client-only + preflight-protected).

---

> **Caveat:** this checklist is from an AI-assisted audit, not legal advice. The Section C items in
> particular should be confirmed with a Croatian/EU lawyer + accountant before charging money.
