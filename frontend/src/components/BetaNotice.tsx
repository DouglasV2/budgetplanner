import { useLocale } from '../LocaleContext';

// Sprint 10.105: free-beta notice for the one-time "Design Session" model. Shown app-wide while betaMode is on.
// Sprint 10.145: reworked from a centered pill-with-emoji "announcement banner" (too conventional, founder-flagged)
// into a slim, left-aligned product note — a solid BETA tag + the message, no bubble.
export function BetaNotice() {
  const { t } = useLocale();
  return (
    <div className="beta-notice" role="status">
      <span className="beta-tag">Beta</span>
      <span className="beta-notice-text">{t('beta.notice')}</span>
    </div>
  );
}
