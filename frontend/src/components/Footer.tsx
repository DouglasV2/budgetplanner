import { lazy, Suspense, useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import type { LegalKey } from '../legal';

// Sprint 10.76 (perf): the legal docs (legal.ts ~8KB) and the modals only ever render on a footer click, so
// lazy-load them off the critical path — the landing/hero shell no longer ships this code on first load.
const LegalModal = lazy(() => import('./LegalModal').then((m) => ({ default: m.LegalModal })));
const DeleteAccountDialog = lazy(() => import('./DeleteAccountDialog').then((m) => ({ default: m.DeleteAccountDialog })));

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
      {legal && (
        <Suspense fallback={null}>
          <LegalModal docKey={legal} onClose={() => setLegal(null)} />
        </Suspense>
      )}
      {deleting && (
        <Suspense fallback={null}>
          <DeleteAccountDialog onClose={() => setDeleting(false)} />
        </Suspense>
      )}
    </footer>
  );
}
