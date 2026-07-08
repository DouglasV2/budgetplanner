// Sprint 10.63: the front door. Shown on entry when nobody is signed in and the visitor hasn't chosen to
// continue as a guest. It never blocks a shared /plan/<id> link (that bypass is decided in App). When Google
// sign-in isn't configured yet, the button is an honest disabled placeholder — no fake auth — but "continue as
// guest" always works, so the app is never truly locked.
// Sprint 10.149: "Continue with Google" is now a plain link to the SERVER-SIDE OAuth redirect flow (a full-page
// navigation), replacing the GIS One-Tap/FedCM button that Google silently throttled per-browser. The callback
// bounces back with ?login=error on failure, which we surface here.
import { useEffect, useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { googleStartUrl } from '../api/client';
import { BrandMark } from './BrandMark';
import { trackEvent } from '../utils/analytics';

export function AuthGate() {
  const { googleEnabled, continueAsGuest } = useAuth();
  const { t } = useLocale();
  const [error, setError] = useState<string | null>(null);

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

          {error && <p className="auth-gate-error" role="alert">{error}</p>}

          <button type="button" className="auth-gate-guest" onClick={() => { trackEvent('auth_continue_guest'); continueAsGuest(); }}>
            {t('auth.continueGuest')}
          </button>
          <small className="auth-gate-note">{t('auth.guestNote')}</small>
        </div>
      </div>
    </div>
  );
}
