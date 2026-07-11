import { useEffect, useState } from 'react';
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

  // Sprint 10.178: on phones the country + read-in-English + sign-in controls used to crowd the bar (and overflow).
  // They now collapse behind a hamburger that opens a small dropdown. Closes on outside-click, Escape, or an action.
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => {
    if (!menuOpen) return;
    const onDocMouseDown = (event: MouseEvent) => {
      if (!(event.target as HTMLElement).closest('.header-inner')) setMenuOpen(false);
    };
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuOpen(false); };
    document.addEventListener('mousedown', onDocMouseDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocMouseDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);

  // On a tight mobile header the country <select> gets shrunk, and Android/Windows don't render the flag
  // emoji — so the selected country looked blank. Show the short ISO code on mobile (always legible text),
  // keep the full country name on wider screens.
  const [compactMarket, setCompactMarket] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 680px)').matches
  );
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 680px)');
    const onChange = (event: MediaQueryListEvent) => setCompactMarket(event.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  // Sprint 10.172: nav-track — the nav link for the section in view gets the active underline. The page is
  // short and the "how" section sits at the bottom, so an IntersectionObserver "most-visible" test can never
  // pick it; instead mark "how" active once its top crosses the lower viewport (works on scroll AND on a nav
  // click, which scrolls). Presentation only; defaults to "planner" (the landing view).
  const [activeSection, setActiveSection] = useState<'planner' | 'how'>('planner');
  useEffect(() => {
    const update = () => {
      const how = document.getElementById('how');
      const line = window.innerHeight * 0.6;
      const next: 'planner' | 'how' = how && how.getBoundingClientRect().top < line ? 'how' : 'planner';
      setActiveSection((prev) => (prev === next ? prev : next));
    };
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update, { passive: true });
    update();
    return () => {
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, []);

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
          {/* Sprint 10.160: two-tone lowercase wordmark — "space" in clay echoes the brand mark's clay fill
              ("budget filling a space"), so the logotype itself carries the product metaphor. */}
          <span className="brand-word">budget<span className="brand-word-accent">space</span></span>
        </a>
        <nav className="nav" aria-label={t('aria.mainNav')}>
          <a href="#planner" className={activeSection === 'planner' ? 'active' : undefined} aria-current={activeSection === 'planner' ? 'true' : undefined}>{t('nav.planner')}</a>
          <a href="#how" className={activeSection === 'how' ? 'active' : undefined} aria-current={activeSection === 'how' ? 'true' : undefined}>{t('nav.how')}</a>
        </nav>
        {/* Sprint 10.178: mobile hamburger — collapses the utility + sign-in controls on phones (hidden on desktop). */}
        <button
          type="button"
          className="header-menu-toggle"
          aria-label={t('header.menu')}
          aria-expanded={menuOpen}
          aria-controls="header-menu"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span aria-hidden="true"></span>
          <span aria-hidden="true"></span>
          <span aria-hidden="true"></span>
        </button>
        <div id="header-menu" className={menuOpen ? 'header-actions open' : 'header-actions'}>
          {/* Sprint 10.156: the header right side is now two clearly-grouped clusters split by a hairline —
              a subtle UTILITY group (country + read-in-English) and a PRIMARY group (sign-in + the CTA) —
              so it reads as an intentionally designed bar, not a row of mismatched controls. */}
          <div className="header-utility">
            <label className="market-select" aria-label={t('header.market')}>
              <select value={market} onChange={(event) => { setMarket(event.target.value); setMenuOpen(false); }}>
                {MARKETS.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.flag} {compactMarket ? option.code : option.label}
                  </option>
                ))}
              </select>
            </label>
            {/* Read-in-English toggle for visitors on a non-English market. Shows "EN" to switch to English,
                or the market's language code (e.g. "HR") to switch back. Currency/market are unaffected. */}
            {config.lang !== 'en' && (
              <button
                type="button"
                className={englishOverride ? 'lang-toggle active' : 'lang-toggle'}
                aria-label={t('header.language')}
                aria-pressed={englishOverride}
                title={englishOverride ? t('header.readNative') : t('header.readEnglish')}
                onClick={() => { setEnglishOverride(!englishOverride); setMenuOpen(false); }}
              >
                {englishOverride ? config.lang.toUpperCase() : 'EN'}
              </button>
            )}
          </div>
          <div className="header-primary">
            {user ? (
              <div className="header-user">
                {user.pictureUrl
                  ? <img className="header-avatar" src={user.pictureUrl} alt="" referrerPolicy="no-referrer" />
                  : <span className="header-avatar header-avatar-fallback" aria-hidden="true">{(user.name || user.email || '?').slice(0, 1).toUpperCase()}</span>}
                <span className="header-user-name">{user.name || user.email}</span>
                <button type="button" className="header-signout" onClick={() => setSigningOut(true)}>{t('auth.signOut')}</button>
              </div>
            ) : (
              !loading && <button type="button" className="header-signin" onClick={() => { startSignIn(); setMenuOpen(false); }}>{t('auth.signIn')}</button>
            )}
            <a className="nav-cta" href="#planner">{t('nav.cta')}<span className="nav-cta-arrow" aria-hidden="true">→</span></a>
          </div>
        </div>
      </div>
    </header>
    {signingOut && <SignOutDialog onClose={() => setSigningOut(false)} />}
    </>
  );
}
