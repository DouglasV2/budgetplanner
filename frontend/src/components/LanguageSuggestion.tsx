import { useState } from 'react';
import { useLocale } from '../LocaleContext';
import { languageEndonym } from '../markets';

// Sprint 10.188: a one-time, dismissible prompt that discovers the existing "read in English" toggle for
// visitors whose BROWSER is English but who landed on a non-English market (e.g. a German market renders in
// German). It offers to switch the UI to English — market/currency stay put, exactly like the header EN button
// (setEnglishOverride). Shown once; the choice is remembered so it never nags. The copy is intentionally in
// English (we are addressing an English-reading visitor), which is why it needs no i18n overlay.
const DISMISS_KEY = 'budgetspace.langSuggest';

function browserPrefersEnglish(): boolean {
  if (typeof navigator === 'undefined') return false;
  const langs = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language];
  return langs.some((lang) => !!lang && lang.toLowerCase().startsWith('en'));
}

export function LanguageSuggestion() {
  const { config, englishOverride, setEnglishOverride } = useLocale();
  const [dismissed, setDismissed] = useState(
    () => typeof window === 'undefined' || window.localStorage.getItem(DISMISS_KEY) === '1'
  );

  // Suggest English only to an English-browser visitor currently seeing a non-English UI, once, until dismissed.
  const show = !dismissed && !englishOverride && config.lang !== 'en' && browserPrefersEnglish();
  if (!show) return null;

  const remember = () => {
    try {
      window.localStorage.setItem(DISMISS_KEY, '1');
    } catch {
      // private mode — the in-memory dismissal below still hides it for this session.
    }
    setDismissed(true);
  };
  const switchToEnglish = () => {
    setEnglishOverride(true);
    remember();
  };

  return (
    <div className="lang-suggest" role="status">
      <span className="lang-suggest-text">Your browser is set to English. Use BudgetSpace in English?</span>
      <div className="lang-suggest-actions">
        <button type="button" className="lang-suggest-keep" onClick={remember}>
          Keep {languageEndonym(config.lang)}
        </button>
        <button type="button" className="lang-suggest-switch" onClick={switchToEnglish}>
          Switch to English
        </button>
      </div>
    </div>
  );
}
