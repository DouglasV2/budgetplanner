// Sprint 10.63: the front door. Shown on entry when nobody is signed in and the visitor hasn't chosen to
// continue as a guest. It never blocks a shared /plan/<id> link (that bypass is decided in App). When Google
// sign-in isn't configured yet, the button is an honest disabled placeholder — no fake auth — but "continue as
// guest" always works, so the app is never truly locked.
import { useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { GoogleSignInButton } from './GoogleSignInButton';

export function AuthGate() {
  const { googleEnabled, googleClientId, continueAsGuest } = useAuth();
  const { t } = useLocale();
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="auth-gate" role="dialog" aria-modal="true" aria-labelledby="auth-gate-title">
      <div className="auth-gate-card">
        <div className="auth-gate-brand">
          <span className="brand-mark">B</span>
          <span>BudgetSpace</span>
        </div>
        <h1 id="auth-gate-title">{t('auth.gateTitle')}</h1>
        <p className="auth-gate-sub">{t('auth.gateSubtitle')}</p>

        <div className="auth-gate-actions">
          {googleEnabled && googleClientId ? (
            <GoogleSignInButton clientId={googleClientId} onError={setError} />
          ) : (
            <button type="button" className="google-signin-button" disabled title={t('account.signInTooltip')}>
              <span className="g-mark" aria-hidden="true">G</span>
              <span>{t('account.signInGoogle')}</span>
              <span className="soon-badge">{t('account.soonBadge')}</span>
            </button>
          )}

          {error && <p className="auth-gate-error" role="alert">{error}</p>}

          <button type="button" className="auth-gate-guest" onClick={continueAsGuest}>
            {t('auth.continueGuest')}
          </button>
          <small className="auth-gate-note">{t('auth.guestNote')}</small>
        </div>
      </div>
    </div>
  );
}
