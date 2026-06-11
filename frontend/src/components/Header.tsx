export function Header() {
  return (
    <header className="header shell">
      <a className="brand" href="#top" aria-label="BudgetSpace AI home">
        <span className="brand-mark">B</span>
        <span>BudgetSpace AI</span>
      </a>
      <nav className="nav" aria-label="Main navigation">
        <a href="#how">Kako radi</a>
        <a href="#planner">Planner</a>
        <a href="#pricing">Monetizacija</a>
      </nav>
      <a className="nav-cta" href="#planner">Složi prostor</a>
    </header>
  );
}
