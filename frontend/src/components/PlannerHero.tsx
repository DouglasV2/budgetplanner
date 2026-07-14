import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
// Owner-supplied hero visual — a full-bleed warm interior. Bundled + hashed by Vite.
import bannerImg from '../banner.png';

// Sprint 10.182: the header hero band (owner mockup) — a three-beat headline ("Tvoj prostor. Tvoj
// budžet. Gotov plan." — the payoff clause in clay) + subtitle + two actions.
// Sprint 10.184: the shopping-cart badge was removed at the owner's request (the app plans, it doesn't sell).
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
