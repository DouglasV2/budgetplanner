// Sprint 10.10 — value-first pricing. Deliberately discreet: the core AI plan is free, tiers are
// framed around "planning more rooms", and there is no real checkout yet (pilot pricing).
const tiers = [
  {
    name: 'Free',
    tagline: 'Probaj AI planiranje',
    price: '0 €',
    features: ['AI plan za tvoju sobu', 'Nekoliko AI generiranja mjesečno', 'Osnovne preporuke proizvoda'],
    note: 'Već koristiš',
    highlight: false
  },
  {
    name: 'Pro',
    tagline: 'Za one koji planiraju više soba',
    price: 'Uskoro',
    features: ['Više AI planova', 'Neograničeno spremanje planova', 'Usporedba proizvoda', 'PDF popis za kupnju'],
    note: 'Pilot — javi se za rani pristup',
    highlight: true
  },
  {
    name: 'Pro+',
    tagline: 'Za detaljnije planiranje',
    price: 'Uskoro',
    features: ['Planiranje više soba', 'Naprednija AI analiza', 'Prioritetne značajke', 'Analiza slike sobe (kasnije)'],
    note: 'Pilot — javi se za rani pristup',
    highlight: false
  }
];

export function Monetization() {
  return (
    <section className="section shell pricing-section" id="pricing">
      <div className="section-heading">
        <span className="eyebrow">Need more plans?</span>
        <h2>Osnovni AI plan je besplatan. Nadogradi kad planiraš više.</h2>
        <p>Spremi i usporedi više ideja za sobe, izvezi popis za kupnju ili planiraj više prostorija. Plaćanje još nije aktivno — ovo je pilot cijena.</p>
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
      <p className="pricing-footnote">
        Preporuke proizvoda biramo prema tvom budžetu i stilu — partnerski linkovi, kad ih bude, neće mijenjati koji je proizvod najbolji za tebe.
      </p>
    </section>
  );
}
