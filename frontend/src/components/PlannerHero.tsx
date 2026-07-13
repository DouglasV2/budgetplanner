import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
// Owner-supplied hero visual — a full-bleed warm interior. Bundled + hashed by Vite.
import bannerImg from '../banner.png';

// Sprint 10.182: the header hero band (owner mockup) — a cart badge + three-beat headline ("Tvoj prostor. Tvoj
// budžet. Gotov plan." — the payoff clause in clay) + subtitle + two actions.
// Sprint 10.183: banner.png is now the full-bleed BACKGROUND of the whole band (was a separate kauc.png image
// card on the right). The content layers over the image's open centre; a restrained warm overlay (in CSS) keeps
// the text readable without muddying the picture. The BETA badge is preserved.
export function PlannerHero() {
  const { t } = useLocale();
  const { betaMode } = useAuth();

  return (
    <section
      className="planner-hero has-banner"
      id="top"
      aria-label={t('planner.eyebrow')}
      style={{ backgroundImage: `url(${bannerImg})` }}
    >
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
      </div>
    </section>
  );
}
