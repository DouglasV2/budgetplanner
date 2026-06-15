import { MARKETS } from '../markets';
import { useLocale } from '../LocaleContext';

export function Header() {
  const { market, setMarket, t } = useLocale();

  return (
    <header className="header shell">
      <a className="brand" href="#top" aria-label="BudgetSpace">
        <span className="brand-mark">B</span>
        <span>BudgetSpace</span>
      </a>
      <nav className="nav" aria-label="Main navigation">
        <a href="#how">{t('nav.how')}</a>
        <a href="#planner">{t('nav.planner')}</a>
      </nav>
      <div className="header-actions">
        <label className="market-select" aria-label={t('header.market')}>
          <span className="market-select-label">{t('header.market')}</span>
          <select value={market} onChange={(event) => setMarket(event.target.value)}>
            {MARKETS.map((option) => (
              <option key={option.code} value={option.code}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <a className="nav-cta" href="#planner">{t('nav.cta')}</a>
      </div>
    </header>
  );
}
