export function HowItWorks() {
  const steps = [
    {
      title: '1. Korisnik unese cilj',
      text: 'Budžet, prostorija, stil, veličina i grad. Nema dugog onboarding procesa.'
    },
    {
      title: '2. Engine filtrira katalog',
      text: 'Proizvodi se biraju prema cijeni, kategoriji, stilu, dostupnosti i kvaliteti.'
    },
    {
      title: '3. AI objasni plan',
      text: 'Korisnik dobije 3 opcije: Budget, Best Value i Stretch, s jasnim razlozima.'
    }
  ];

  return (
    <section className="section shell" id="how">
      <div className="section-heading">
        <span className="eyebrow">Kako radi</span>
        <h2>UX je jednostavan: od namjere do shopping liste.</h2>
        <p>Ovo je starter logika s mock proizvodima. Kasnije se spaja backend, scraperi i pravi retailer feedovi.</p>
      </div>
      <div className="steps-grid">
        {steps.map((step) => (
          <article className="step-card" key={step.title}>
            <h3>{step.title}</h3>
            <p>{step.text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
