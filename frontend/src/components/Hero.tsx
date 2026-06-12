export function Hero() {
  return (
    <section className="hero shell" id="top">
      <div className="hero-copy">
        <div className="eyebrow">AI planer za opremanje prostora</div>
        <h1>Opremi dnevni boravak unutar budžeta — bez izgubljenih sati po webshopovima.</h1>
        <p>
          Napiši što želiš opremiti, koliki ti je budžet i koje trgovine preferiraš. BudgetSpace AI složi konkretan popis za kupnju iz trgovina poput IKEA, JYSK, Pevex, Emmezeta, Decathlon, Lesnina i Lesnina.
        </p>
        <div className="hero-actions">
          <a className="primary-button" href="#planner">Isprobaj planer</a>
          <a className="secondary-button" href="#how">Vidi kako radi</a>
        </div>
        <div className="trust-row">
          <span>⚡ Plan u manje od minute</span>
          <span>🛒 Direktni linkovi na proizvode</span>
          <span>💶 Ukupna cijena odmah</span>
        </div>
      </div>
      <div className="hero-card" aria-label="Primjer plana za kupnju">
        <div className="floating-badge">Preporučeni plan</div>
        <div className="room-preview">
          <div className="sofa" />
          <div className="rug" />
          <div className="table" />
          <div className="lamp" />
        </div>
        <div className="mini-plan">
          <div>
            <span>Budžet</span>
            <strong>1.500 €</strong>
          </div>
          <div>
            <span>Ukupno</span>
            <strong>1.427 €</strong>
          </div>
          <div>
            <span>Poklapanje</span>
            <strong>94%</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
