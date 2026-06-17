import { useLocale } from '../LocaleContext';

export function Hero() {
  const { t } = useLocale();
  return (
    <section className="hero shell" id="top">
      <div className="hero-copy">
        <div className="eyebrow">{t('hero.eyebrow')}</div>
        <h1>{t('hero.heading')}</h1>
        <p>{t('hero.subheading')}</p>
        <div className="hero-actions">
          <a className="primary-button" href="#planner">{t('hero.tryPlanner')}</a>
          <a className="secondary-button" href="#how">{t('hero.seeHow')}</a>
        </div>
        <div className="trust-row">
          <span>{t('hero.trustSpeed')}</span>
          <span>{t('hero.trustLinks')}</span>
          <span>{t('hero.trustTotal')}</span>
        </div>
      </div>
      <div className="hero-card" aria-label={t('hero.cardAria')}>
        <div className="floating-badge">{t('hero.recommendedPlan')}</div>
        <div className="room-preview">
          <div className="sofa" />
          <div className="rug" />
          <div className="table" />
          <div className="lamp" />
        </div>
        <div className="mini-plan">
          <div>
            <span>{t('hero.budget')}</span>
            <strong>1.500 €</strong>
          </div>
          <div>
            <span>{t('hero.total')}</span>
            <strong>1.427 €</strong>
          </div>
          <div>
            <span>{t('hero.match')}</span>
            <strong>94%</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
