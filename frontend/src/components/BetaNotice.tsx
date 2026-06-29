import { useLocale } from '../LocaleContext';
import { SparkIcon } from './icons';

// Sprint 10.105: slim, friendly notice for the free beta of the one-time "Design Session" model. Shown app-wide
// while betaMode is on — it tells users the premium Design Session features are temporarily free. No price,
// no checkout, no locked sections (those arrive when one-time payments are wired and betaMode is turned off).
// Sprint 10.142: the 🎉 emoji (an "AI build" tell) is now a restrained line-icon sparkle.
export function BetaNotice() {
  const { t } = useLocale();
  return (
    <div className="beta-notice" role="status">
      <span className="beta-notice-badge" aria-hidden="true"><SparkIcon size={16} /></span>
      <span className="beta-notice-text">{t('beta.notice')}</span>
    </div>
  );
}
