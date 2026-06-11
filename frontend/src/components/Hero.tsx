export function Hero() {
  return (
    <section className="hero shell" id="top">
      <div className="hero-copy">
        <div className="eyebrow">AI shopping planner za opremanje prostora</div>
        <h1>Opremi dnevni boravak unutar budžeta — bez izgubljenih sati po webshopovima.</h1>
        <p>
          Unesi budžet, stil i veličinu prostorije. BudgetSpace AI složi konkretan shopping plan iz trgovina poput IKEA, JYSK, Pevex, Decathlon i Emmezeta.
        </p>
        <div className="hero-actions">
          <a className="primary-button" href="#planner">Isprobaj demo</a>
          <a className="secondary-button" href="#how">Vidi kako radi</a>
        </div>
        <div className="trust-row">
          <span>⚡ Plan u manje od minute</span>
          <span>🛒 Direktni product linkovi</span>
          <span>💶 Total cijena odmah</span>
        </div>
      </div>
      <div className="hero-card" aria-label="Example generated plan">
        <div className="floating-badge">Best value</div>
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
            <span>Procjena</span>
            <strong>1.427 €</strong>
          </div>
          <div>
            <span>Fit score</span>
            <strong>94%</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
