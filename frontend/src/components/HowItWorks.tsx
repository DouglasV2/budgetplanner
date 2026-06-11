export function HowItWorks() {
  const steps = [
    {
      title: '1. Opišeš što želiš',
      text: 'Napišeš prostoriju, budžet, stil i trgovine koje želiš. Nema dugog ispunjavanja forme.'
    },
    {
      title: '2. Aplikacija složi kombinacije',
      text: 'Proizvodi se biraju prema cijeni, kategoriji, stilu, dostupnosti i tome koliko se uklapaju u tvoje želje.'
    },
    {
      title: '3. Dobiješ popis za kupnju',
      text: 'Vidiš ukupnu cijenu, trošak po trgovini, proizvode i linkove gdje ih možeš otvoriti.'
    }
  ];

  return (
    <section className="section shell" id="how">
      <div className="section-heading">
        <span className="eyebrow">Kako radi</span>
        <h2>Od ideje do shopping liste u par klikova.</h2>
        <p>Ova verzija već radi s bazom proizvoda na serveru. Sljedeći korak su stvarni katalozi trgovina i pametnije preporuke.</p>
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
