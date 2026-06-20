// Sprint 10.10 → 10.69 — value-first pricing. The core (rule-based plans) is free; Plus (€5.99/mo) is the thin
// margin tier whose real carrot is the AI assistant. When Stripe is configured the Plus CTA starts a real hosted
// Checkout; otherwise it's an honest waitlist that records willingness-to-pay (never a fake purchase).
import { useState } from 'react';
import { recordPlusInterest, startCheckout } from '../api/client';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

export function Monetization() {
  const { t } = useLocale();
  const { user, billingEnabled, openSignIn, justUpgraded } = useAuth();
  const isPlus = user?.plan === 'PLUS' || justUpgraded;
  const [email, setEmail] = useState('');
  const [joined, setJoined] = useState(false);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function joinWaitlist() {
    recordPlusInterest(email.trim() || undefined, 'pricing');
    setJoined(true);
  }

  async function upgrade() {
    setCheckoutBusy(true);
    setError(null);
    try {
      const { url } = await startCheckout();
      window.location.href = url; // redirect to Stripe's hosted checkout
    } catch {
      setError(t('pricing.checkoutError'));
      setCheckoutBusy(false);
    }
  }

  function plusCta() {
    if (isPlus) {
      return <div className="pricing-active">{justUpgraded ? t('pricing.welcome') : t('pricing.plusActive')}</div>;
    }
    if (billingEnabled && user) {
      return (
        <div className="pricing-cta">
          <button type="button" className="pricing-upgrade" onClick={() => void upgrade()} disabled={checkoutBusy}>
            {checkoutBusy ? t('pricing.redirecting') : t('pricing.upgradeCta')}
          </button>
          {error && <small className="pricing-error">{error}</small>}
        </div>
      );
    }
    if (billingEnabled) {
      // Stripe is on but the visitor is a guest — they need an account to subscribe.
      return <button type="button" className="pricing-upgrade" onClick={openSignIn}>{t('pricing.signInForPlus')}</button>;
    }
    // Stripe not configured → honest waitlist (no fake checkout).
    return joined ? (
      <div className="pricing-joined">{t('pricing.joined')}</div>
    ) : (
      <div className="pricing-waitlist">
        <input
          type="email"
          value={email}
          placeholder={t('pricing.waitlistEmail')}
          aria-label={t('pricing.waitlistEmail')}
          onChange={(event) => setEmail(event.target.value)}
        />
        <button type="button" onClick={joinWaitlist}>{t('pricing.waitlistCta')}</button>
      </div>
    );
  }

  return (
    <section className="section shell pricing-section" id="pricing">
      <div className="section-heading">
        <span className="eyebrow">{t('pricing.eyebrow')}</span>
        <h2>{t('pricing.heading')}</h2>
        <p>{t('pricing.sub')}</p>
      </div>
      <div className="pricing-grid two-tier">
        <article className="pricing-card">
          <div className="pricing-card-head">
            <h3>{t('pricing.freeName')}</h3>
            <span className="pricing-price">0 €</span>
          </div>
          <p className="pricing-tagline">{t('pricing.freeTagline')}</p>
          <ul className="pricing-features">
            <li>{t('pricing.freeF1')}</li>
            <li>{t('pricing.freeF2')}</li>
            <li>{t('pricing.freeF3')}</li>
            <li>{t('pricing.freeF4')}</li>
          </ul>
        </article>

        <article className="pricing-card featured">
          <div className="pricing-card-head">
            <h3>{t('pricing.plusName')}</h3>
            <span className="pricing-price">{t('pricing.plusPrice')}</span>
          </div>
          <p className="pricing-tagline">{t('pricing.plusTagline')}</p>
          <ul className="pricing-features">
            <li>{t('pricing.plusF1')}</li>
            <li>{t('pricing.plusF2')}</li>
            <li>{t('pricing.plusF3')}</li>
            <li>{t('pricing.plusF4')}</li>
          </ul>
          {plusCta()}
          {!billingEnabled && !isPlus && <small className="pricing-note">{t('pricing.plusNote')}</small>}
        </article>
      </div>
      <p className="pricing-footnote">{t('pricing.footnote')}</p>
    </section>
  );
}
