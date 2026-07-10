import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

// Sprint 10.172: a slim product-context strip directly under the header (owner mockup) — a clipboard mark,
// the "Planer za kupnju" label, a one-line tagline, and (during the free beta) a small BETA badge. Replaces
// the old full-width BetaNotice bar; the beta message is preserved on the badge's tooltip. No "learn more" link.
export function PlannerSubnav() {
  const { t } = useLocale();
  const { betaMode } = useAuth();
  return (
    <div className="planner-subnav">
      <div className="planner-subnav-inner shell">
        <span className="planner-subnav-label">
          <svg className="planner-subnav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="5" y="4.5" width="14" height="16.5" rx="2" />
            <path d="M9 4.5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 4.5V6H9z" />
            <path d="M9 11h6" />
            <path d="M9 15h4" />
          </svg>
          <strong>{t('planner.eyebrow')}</strong>
        </span>
        <span className="planner-subnav-sep" aria-hidden="true" />
        <span className="planner-subnav-tagline">{t('planner.subnavTagline')}</span>
        {betaMode && <span className="planner-subnav-beta" title={t('beta.notice')}>Beta</span>}
      </div>
    </div>
  );
}
