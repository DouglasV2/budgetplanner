// Sprint 10.63: the front door. Shown on entry when nobody is signed in and the visitor hasn't chosen to
// continue as a guest. It never blocks a shared /plan/<id> link (that bypass is decided in App). When Google
// sign-in isn't configured yet, the button is an honest disabled placeholder — no fake auth — but "continue as
// guest" always works, so the app is never truly locked.
// Sprint 10.149: "Continue with Google" is now a plain link to the SERVER-SIDE OAuth redirect flow (a full-page
// navigation), replacing the GIS One-Tap/FedCM button that Google silently throttled per-browser. The callback
// bounces back with ?login=error on failure, which we surface here.
import { lazy, Suspense, useEffect, useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { googleStartUrl } from '../api/client';
import type { LegalKey } from '../legal';
import { BrandMark } from './BrandMark';
import { trackEvent } from '../utils/analytics';

// Sprint 10.188: the Terms/Privacy modal is lazy-loaded (as it is in the footer), so the gate stays light until
// a visitor actually opens a document.
const LegalModal = lazy(() => import('./LegalModal').then((m) => ({ default: m.LegalModal })));

export function AuthGate() {
  const { googleEnabled, continueAsGuest } = useAuth();
  const { t } = useLocale();
  const [error, setError] = useState<string | null>(null);
  // Sprint 10.188: which legal doc (terms/privacy) the sign-in notice opened, if any.
  const [legal, setLegal] = useState<LegalKey | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('login') === 'error') {
      setError(t('auth.signInError'));
      params.delete('login');
      const qs = params.toString();
      window.history.replaceState({}, '', window.location.pathname + (qs ? `?${qs}` : '') + window.location.hash);
    }
  }, [t]);

  return (
    <div className="auth-gate" role="dialog" aria-modal="true" aria-labelledby="auth-gate-title">
      <div className="auth-gate-card">
        <div className="auth-gate-brand">
          <BrandMark />
          <span>BudgetSpace</span>
        </div>
        <h1 id="auth-gate-title">{t('auth.gateTitle')}</h1>
        <p className="auth-gate-sub">{t('auth.gateSubtitle')}</p>

        <div className="auth-gate-actions">
          {googleEnabled ? (
            <a className="google-signin-button google-signin-link" href={googleStartUrl()} onClick={() => trackEvent('auth_google_start')}>
              <span className="g-mark" aria-hidden="true">G</span>
              <span>{t('account.signInGoogle')}</span>
            </a>
          ) : (
            <button type="button" className="google-signin-button" disabled title={t('account.signInTooltip')}>
              <span className="g-mark" aria-hidden="true">G</span>
              <span>{t('account.signInGoogle')}</span>
              <span className="soon-badge">{t('account.soonBadge')}</span>
            </button>
          )}

          {/* Sprint 10.188: legal notice directly under the Google button — tells the visitor, right before an
              account is created, that continuing accepts the Terms and confirms they have read the Privacy
              Policy. Information, not a gate: no checkbox, and deliberately NOT wired to analytics/cookie
              consent (a separate decision). The links open the same LegalModal the footer uses. */}
          <p className="auth-legal-notice">
            {t('auth.legalNotice').split(/(\{terms\}|\{privacy\})/).map((part, index) => {
              if (part === '{terms}') {
                return <button key={index} type="button" className="auth-legal-link" onClick={() => setLegal('terms')}>{t('auth.legalTerms')}</button>;
              }
              if (part === '{privacy}') {
                return <button key={index} type="button" className="auth-legal-link" onClick={() => setLegal('privacy')}>{t('auth.legalPrivacy')}</button>;
              }
              return <span key={index}>{part}</span>;
            })}
          </p>

          {error && <p className="auth-gate-error" role="alert">{error}</p>}

          <button type="button" className="auth-gate-guest" onClick={() => { trackEvent('auth_continue_guest'); continueAsGuest(); }}>
            {t('auth.continueGuest')}
          </button>
          <small className="auth-gate-note">{t('auth.guestNote')}</small>
        </div>
      </div>
      {legal && (
        <Suspense fallback={null}>
          <LegalModal docKey={legal} onClose={() => setLegal(null)} />
        </Suspense>
      )}
    </div>
  );
}
