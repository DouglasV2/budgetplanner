import { useState } from 'react';
import { MARKETS } from '../markets';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { googleStartUrl } from '../api/client';
import { BrandMark } from './BrandMark';
import { SignOutDialog } from './SignOutDialog';

// Sprint 10.146: the header was a floating rounded "pill bar" (border-radius + shadow + float gap) with a
// gray-pill nav — both classic AI/template tells (founder-flagged). Reworked into a clean full-bleed flush
// header: a full-width sticky bar with a subtle bottom hairline, contained content (.header-inner shell), and
// plain-text nav links (no segmented pill).
export function Header() {
  const { market, setMarket, t, config, englishOverride, setEnglishOverride } = useLocale();
  const { user, loading, googleEnabled, openSignIn } = useAuth();
  // Sprint 10.150: a custom in-app confirm dialog for sign-out (replaces the native window.confirm).
  const [signingOut, setSigningOut] = useState(false);

  // Sprint 10.150: the header "Prijava" goes STRAIGHT to the Google redirect when sign-in is configured — no
  // dependency on the gate's open/close state (which is what could get stuck after a sign-in/out cycle). Falls
  // back to opening the gate (with its guest option / disabled placeholder) when Google isn't configured.
  function startSignIn() {
    if (googleEnabled) {
      window.location.href = googleStartUrl();
    } else {
      openSignIn();
    }
  }

  return (
    <>
    <header className="header">
      <div className="header-inner shell">
        <a className="brand" href="#top" aria-label="BudgetSpace">
          <BrandMark />
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
          {/* Sprint 10.152: read-in-English toggle for visitors on a non-English market. Shows "EN" to switch to
              English, or the market's language code (e.g. "HR") to switch back. Currency/market are unaffected. */}
          {config.lang !== 'en' && (
            <button
              type="button"
              className={englishOverride ? 'lang-toggle active' : 'lang-toggle'}
              aria-label={t('header.language')}
              aria-pressed={englishOverride}
              title={englishOverride ? t('header.readNative') : t('header.readEnglish')}
              onClick={() => setEnglishOverride(!englishOverride)}
            >
              {englishOverride ? config.lang.toUpperCase() : 'EN'}
            </button>
          )}
          {user ? (
            <div className="header-user">
              {user.pictureUrl
                ? <img className="header-avatar" src={user.pictureUrl} alt="" referrerPolicy="no-referrer" />
                : <span className="header-avatar header-avatar-fallback" aria-hidden="true">{(user.name || user.email || '?').slice(0, 1).toUpperCase()}</span>}
              <span className="header-user-name">{user.name || user.email}</span>
              <button type="button" className="header-signout" onClick={() => setSigningOut(true)}>{t('auth.signOut')}</button>
            </div>
          ) : (
            !loading && <button type="button" className="header-signin" onClick={startSignIn}>{t('auth.signIn')}</button>
          )}
          <a className="nav-cta" href="#planner">{t('nav.cta')}</a>
        </div>
      </div>
    </header>
    {signingOut && <SignOutDialog onClose={() => setSigningOut(false)} />}
    </>
  );
}
