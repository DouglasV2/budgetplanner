import { useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { LegalModal } from './LegalModal';
import { DeleteAccountDialog } from './DeleteAccountDialog';
import type { LegalKey } from '../legal';

export function Footer() {
  const { t } = useLocale();
  const { user } = useAuth();
  const [legal, setLegal] = useState<LegalKey | null>(null);
  const [deleting, setDeleting] = useState(false);

  return (
    <footer className="footer shell">
      <div>
        <strong>BudgetSpace</strong>
        <p>{t('footer.tagline')}</p>
      </div>
      <nav className="footer-legal" aria-label={t('footer.legalNav')}>
        <button type="button" onClick={() => setLegal('privacy')}>{t('legal.privacy')}</button>
        <button type="button" onClick={() => setLegal('terms')}>{t('legal.terms')}</button>
        <button type="button" onClick={() => setLegal('impressum')}>{t('legal.impressum')}</button>
        {/* Sprint 10.72: GDPR self-service deletion, only for signed-in accounts (a guest has nothing to delete). */}
        {user && (
          <button type="button" className="footer-delete" onClick={() => setDeleting(true)}>{t('account.delete')}</button>
        )}
        <a href="#top">{t('footer.backToTop')}</a>
      </nav>
      <LegalModal docKey={legal} onClose={() => setLegal(null)} />
      {deleting && <DeleteAccountDialog onClose={() => setDeleting(false)} />}
    </footer>
  );
}
