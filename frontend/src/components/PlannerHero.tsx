import { useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

// Sprint 10.182: the header hero band (owner mockup). Replaces the slim "Planer za kupnju" subnav strip with a
// two-column hero: a cart badge + three-beat headline ("Tvoj prostor. Tvoj budžet. Gotov plan." — the payoff
// clause in clay) + subtitle + two actions (a new plan / my plans), and a visual on the right. The BETA badge is
// preserved. The visual is a drop-in slot: put an image at /hero.jpg (frontend/public/hero.jpg) and it appears;
// until then an on-brand illustrated placeholder shows, so the empty state still reads as finished.
export function PlannerHero() {
  const { t } = useLocale();
  const { betaMode } = useAuth();
  const [heroImage, setHeroImage] = useState<'loading' | 'ok' | 'failed'>('loading');

  return (
    <section className="planner-hero" id="top" aria-label={t('planner.eyebrow')}>
      <div className="planner-hero-inner shell">
        <div className="planner-hero-copy">
          <span className="planner-hero-badge" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 5h2l1.6 10.2a1.5 1.5 0 0 0 1.5 1.3h7.8a1.5 1.5 0 0 0 1.5-1.2L20.5 8H6.4" />
              <circle cx="9.5" cy="20" r="1.1" />
              <circle cx="18" cy="20" r="1.1" />
            </svg>
          </span>
          <div className="planner-hero-text">
            {betaMode && <span className="planner-hero-beta" title={t('beta.notice')}>Beta</span>}
            <h1 className="planner-hero-title">
              {t('planner.heroLead')} <span className="planner-hero-title-accent">{t('planner.heroAccent')}</span>
            </h1>
            <p className="planner-hero-sub">{t('planner.heroSub')}</p>
            <div className="planner-hero-actions">
              <a className="planner-hero-btn primary" href="#planner">
                <span aria-hidden="true">+</span> {t('planner.newPlan')}
              </a>
              <a className="planner-hero-btn ghost" href="#saved">{t('saved.title')}</a>
            </div>
          </div>
        </div>

        <div className="planner-hero-visual" aria-hidden="true">
          {heroImage !== 'failed' && (
            <img
              className={heroImage === 'ok' ? 'planner-hero-photo is-loaded' : 'planner-hero-photo'}
              src="/hero.jpg"
              alt=""
              onLoad={() => setHeroImage('ok')}
              onError={() => setHeroImage('failed')}
            />
          )}
          {/* On-brand placeholder shown until /hero.jpg is added — a settee on a floor line, echoing the brand mark. */}
          <svg className="planner-hero-placeholder" viewBox="0 0 220 150" fill="none" aria-hidden="true" focusable="false">
            <path d="M28 118 H192" stroke="var(--accent)" strokeWidth="2" strokeLinecap="round" opacity="0.35" />
            <g stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" opacity="0.85">
              <path d="M58 96 L52 116" />
              <path d="M92 96 L89 116" />
              <path d="M128 96 L131 116" />
              <path d="M162 96 L168 116" />
            </g>
            <rect x="54" y="44" width="112" height="30" rx="12" fill="var(--accent)" opacity="0.9" />
            <rect x="54" y="70" width="112" height="28" rx="11" fill="var(--accent)" />
            <rect x="40" y="58" width="18" height="40" rx="9" fill="var(--accent)" />
            <rect x="162" y="58" width="18" height="40" rx="9" fill="var(--accent)" />
            <rect x="150" y="52" width="20" height="20" rx="6" fill="var(--sage)" opacity="0.9" transform="rotate(8 160 62)" />
          </svg>
        </div>
      </div>
    </section>
  );
}
