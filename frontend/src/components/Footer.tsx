import { lazy, Suspense, useState } from 'react';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';
import { useConsent } from '../ConsentContext';
import type { LegalKey } from '../legal';

// Sprint 10.76 (perf): the legal docs (legal.ts ~8KB) and the modals only ever render on a footer click, so
// lazy-load them off the critical path — the landing/hero shell no longer ships this code on first load.
const LegalModal = lazy(() => import('./LegalModal').then((m) => ({ default: m.LegalModal })));
const DeleteAccountDialog = lazy(() => import('./DeleteAccountDialog').then((m) => ({ default: m.DeleteAccountDialog })));

export function Footer() {
  const { t } = useLocale();
  const { user } = useAuth();
  const { configured: analyticsConfigured, openSettings } = useConsent();
  const [legal, setLegal] = useState<LegalKey | null>(null);
  const [deleting, setDeleting] = useState(false);

  return (
    <footer className="footer shell">
      <div>
        <strong>BudgetSpace</strong>
        <p>{t('footer.tagline')}</p>
        {/* Sprint 10.163 (trademark / non-affiliation): a small muted echo that we are independent of the retailers. */}
        <small className="field-help footer-independence">{t('footer.independence')}</small>
      </div>
      <nav className="footer-legal" aria-label={t('footer.legalNav')}>
        <button type="button" onClick={() => setLegal('privacy')}>{t('legal.privacy')}</button>
        <button type="button" onClick={() => setLegal('terms')}>{t('legal.terms')}</button>
        <button type="button" onClick={() => setLegal('impressum')}>{t('legal.impressum')}</button>
        {/* Sprint 10.185: reopen the analytics-consent panel. Only shown when GA is actually configured, so
            there is always a real choice to change. */}
        {analyticsConfigured && (
          <button type="button" onClick={openSettings}>{t('consent.settings')}</button>
        )}
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
