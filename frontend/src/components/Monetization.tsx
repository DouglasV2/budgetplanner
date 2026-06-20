// Sprint 10.10 → 10.68 — value-first pricing. The core (rule-based plans) is free; Plus (€5.99/mo) is the thin
// margin tier whose real carrot is the AI assistant. No checkout yet (Stripe lands later) — the Plus CTA is an
// honest waitlist that records willingness-to-pay, never a fake purchase.
import { useState } from 'react';
import { recordPlusInterest } from '../api/client';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

export function Monetization() {
  const { t } = useLocale();
  const { user } = useAuth();
  const isPlus = user?.plan === 'PLUS';
  const [email, setEmail] = useState('');
  const [joined, setJoined] = useState(false);

  function joinWaitlist() {
    recordPlusInterest(email.trim() || undefined, 'pricing');
    setJoined(true);
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
          {isPlus ? (
            <div className="pricing-active">{t('pricing.plusActive')}</div>
          ) : joined ? (
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
          )}
          <small className="pricing-note">{t('pricing.plusNote')}</small>
        </article>
      </div>
      <p className="pricing-footnote">{t('pricing.footnote')}</p>
    </section>
  );
}
