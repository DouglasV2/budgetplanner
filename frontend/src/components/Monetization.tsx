// Sprint 10.10 — value-first pricing. Deliberately discreet: the core AI plan is free, tiers are
// framed around "planning more rooms", and there is no real checkout yet (pilot pricing).
import { useLocale } from '../LocaleContext';

export function Monetization() {
  const { t } = useLocale();
  const tiers = [
    {
      name: 'Free',
      tagline: t('pricing.freeTagline'),
      price: '0 €',
      features: [t('pricing.freeF1'), t('pricing.freeF2'), t('pricing.freeF3')],
      note: t('pricing.freeNote'),
      highlight: false
    },
    {
      name: 'Pro',
      tagline: t('pricing.proTagline'),
      price: t('pricing.soon'),
      features: [t('pricing.proF1'), t('pricing.proF2'), t('pricing.proF3'), t('pricing.proF4')],
      note: t('pricing.pilotNote'),
      highlight: true
    },
    {
      name: 'Pro+',
      tagline: t('pricing.proPlusTagline'),
      price: t('pricing.soon'),
      features: [t('pricing.proPlusF1'), t('pricing.proPlusF2'), t('pricing.proPlusF3'), t('pricing.proPlusF4')],
      note: t('pricing.pilotNote'),
      highlight: false
    }
  ];

  return (
    <section className="section shell pricing-section" id="pricing">
      <div className="section-heading">
        <span className="eyebrow">{t('pricing.eyebrow')}</span>
        <h2>{t('pricing.heading')}</h2>
        <p>{t('pricing.sub')}</p>
      </div>
      <div className="pricing-grid">
        {tiers.map((tier) => (
          <article className={tier.highlight ? 'pricing-card featured' : 'pricing-card'} key={tier.name}>
            <div className="pricing-card-head">
              <h3>{tier.name}</h3>
              <span className="pricing-price">{tier.price}</span>
            </div>
            <p className="pricing-tagline">{tier.tagline}</p>
            <ul className="pricing-features">
              {tier.features.map((feature) => (
                <li key={feature}>{feature}</li>
              ))}
            </ul>
            <small className="pricing-note">{tier.note}</small>
          </article>
        ))}
      </div>
      <p className="pricing-footnote">{t('pricing.footnote')}</p>
    </section>
  );
}
