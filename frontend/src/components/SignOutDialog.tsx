// Sprint 10.150: a custom in-app sign-out confirmation, replacing the native window.confirm popup (too "basic").
// Modeled on DeleteAccountDialog so it matches the app's modal look — but sign-out isn't destructive, so the
// confirm button is the neutral primary style, not the red one.
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

export function SignOutDialog({ onClose }: { onClose: () => void }) {
  const { t } = useLocale();
  const { signOut } = useAuth();

  return (
    <div className="legal-overlay" role="dialog" aria-modal="true" aria-label={t('auth.signOutConfirm')} onClick={onClose}>
      <div className="legal-modal confirm-modal" onClick={(event) => event.stopPropagation()}>
        <h2>{t('auth.signOutConfirm')}</h2>
        <p>{t('auth.signOutBody')}</p>
        <div className="confirm-actions">
          <button type="button" className="confirm-cancel" onClick={onClose}>{t('account.cancel')}</button>
          <button
            type="button"
            className="confirm-primary"
            onClick={() => { onClose(); void signOut(); }}
          >
            {t('auth.signOut')}
          </button>
        </div>
      </div>
    </div>
  );
}
