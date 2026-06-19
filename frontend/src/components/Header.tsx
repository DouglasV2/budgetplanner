import { MARKETS } from '../markets';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

export function Header() {
  const { market, setMarket, t } = useLocale();
  const { user, loading, signOut, openSignIn } = useAuth();

  return (
    <header className="header shell">
      <a className="brand" href="#top" aria-label="BudgetSpace">
        <span className="brand-mark">B</span>
        <span>BudgetSpace</span>
      </a>
      <nav className="nav" aria-label={t('aria.mainNav')}>
        <a href="#how">{t('nav.how')}</a>
        <a href="#planner">{t('nav.planner')}</a>
      </nav>
      <div className="header-actions">
        <label className="market-select" aria-label={t('header.market')}>
          <span className="market-select-label">{t('header.market')}</span>
          <select value={market} onChange={(event) => setMarket(event.target.value)}>
            {MARKETS.map((option) => (
              <option key={option.code} value={option.code}>
                {option.flag} {option.label}
              </option>
            ))}
          </select>
        </label>
        {user ? (
          <div className="header-user">
            {user.pictureUrl
              ? <img className="header-avatar" src={user.pictureUrl} alt="" referrerPolicy="no-referrer" />
              : <span className="header-avatar header-avatar-fallback" aria-hidden="true">{(user.name || user.email || '?').slice(0, 1).toUpperCase()}</span>}
            <span className="header-user-name">{user.name || user.email}</span>
            <button type="button" className="header-signout" onClick={() => void signOut()}>{t('auth.signOut')}</button>
          </div>
        ) : (
          !loading && <button type="button" className="header-signin" onClick={openSignIn}>{t('auth.signIn')}</button>
        )}
        <a className="nav-cta" href="#planner">{t('nav.cta')}</a>
      </div>
    </header>
  );
}
