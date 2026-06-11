export function Monetization() {
  const items = [
    {
      title: 'Affiliate revenue',
      text: 'Korisnik klikne na proizvod i kupi. Ti uzimaš proviziju kroz affiliate/partner link.'
    },
    {
      title: 'Sponsored placements',
      text: 'Retaileri mogu platiti više prikaza kada imaš dokazani promet i klikove.'
    },
    {
      title: 'Premium planner',
      text: 'Napredni planovi, spremanje projekata, analiza slike sobe i personalizirani stilovi.'
    }
  ];

  return (
    <section className="section shell" id="pricing">
      <div className="section-heading">
        <span className="eyebrow">Business model</span>
        <h2>Monetizacija je vezana uz kupovnu namjeru.</h2>
        <p>Starter app je napravljen tako da kasnije lako prati klikove, planove i konverzije po retaileru.</p>
      </div>
      <div className="monetization-grid">
        {items.map((item) => (
          <article className="money-card" key={item.title}>
            <h3>{item.title}</h3>
            <p>{item.text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
