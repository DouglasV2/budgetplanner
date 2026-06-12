export function Header() {
  return (
    <header className="header shell">
      <a className="brand" href="#top" aria-label="BudgetSpace">
        <span className="brand-mark">B</span>
        <span>BudgetSpace</span>
      </a>
      <nav className="nav" aria-label="Main navigation">
        <a href="#how">Kako radi</a>
        <a href="#planner">Planer</a>
      </nav>
      <a className="nav-cta" href="#planner">Složi prostor</a>
    </header>
  );
}
