export function Monetization() {
  const items = [
    {
      title: 'Provizija od kupnje',
      text: 'Korisnik otvori proizvod iz plana i kupi ga u trgovini. Kasnije se tu mogu ubaciti partnerski linkovi.'
    },
    {
      title: 'Plaćeni prikazi trgovina',
      text: 'Kad planer ima dovoljno korisnika, trgovine mogu platiti da budu češće prikazane u relevantnim planovima.'
    },
    {
      title: 'Premium opcije',
      text: 'Naprednije spremanje planova, analiza slike sobe, više stilova i detaljnije usporedbe.'
    }
  ];

  return (
    <section className="section shell" id="pricing">
      <div className="section-heading">
        <span className="eyebrow">Kako može zarađivati</span>
        <h2>Vrijednost je u tome što korisnik već planira kupnju.</h2>
        <p>Aplikacija sada prati spremljene planove, reakcije korisnika i klikove na proizvode — to su podaci koji kasnije pomažu monetizaciji.</p>
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
