// Sprint 10.72 — GDPR "delete account" confirmation. Calls AuthContext.deleteAccount (DELETE /api/auth/account),
// which erases the account + saved plans server-side and drops the local user. Destructive, so it requires an
// explicit confirm.
import { useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

export function DeleteAccountDialog({ onClose }: { onClose: () => void }) {
  const { t } = useLocale();
  const { deleteAccount } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);

  async function confirm() {
    setBusy(true);
    setError(false);
    try {
      await deleteAccount();
      onClose(); // local user is now cleared; the dialog (rendered from the footer) just closes
    } catch {
      setError(true);
      setBusy(false);
    }
  }

  return (
    <div className="legal-overlay" role="dialog" aria-modal="true" aria-label={t('account.deleteTitle')} onClick={onClose}>
      <div className="legal-modal confirm-modal" onClick={(event) => event.stopPropagation()}>
        <h2>{t('account.deleteTitle')}</h2>
        <p>{t('account.deleteWarning')}</p>
        {error && <p className="pricing-error">{t('account.deleteError')}</p>}
        <div className="confirm-actions">
          <button type="button" className="confirm-cancel" onClick={onClose} disabled={busy}>{t('account.cancel')}</button>
          <button type="button" className="confirm-delete" onClick={() => void confirm()} disabled={busy}>
            {busy ? t('account.deleting') : t('account.deleteConfirm')}
          </button>
        </div>
      </div>
    </div>
  );
}
