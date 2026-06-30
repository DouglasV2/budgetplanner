// Sprint 10.13 (#3): app-wide market + language. Holds the selected market, exposes a `t()`
// translator for the market's language, and keeps the currency formatter in sync.
// Sprint 10.152: a manual "read in English" override, INDEPENDENT of the market. A visitor on e.g. Germany can
// switch the whole UI to English (currency/market stay German). `lang` is the EFFECTIVE language (override or
// market); language decisions across the app key off it, not the raw market language.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { marketConfig, marketFromBrowser, regionToMarket, type MarketConfig } from './markets';
import { ensureLangLoaded, translate } from './i18n';
import { fetchGeoCountry } from './api/client';
import { setFormattingMarket } from './utils/planner';

const STORAGE_KEY = 'budgetspace.market';
const LANG_OVERRIDE_KEY = 'budgetspace.lang';

interface LocaleContextValue {
  market: string;
  config: MarketConfig;
  setMarket: (market: string) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
  /** The EFFECTIVE UI language ('en' when overridden, else the market's language). */
  lang: string;
  /** True when the user has forced English on a non-English market. */
  englishOverride: boolean;
  setEnglishOverride: (on: boolean) => void;
}

const LocaleContext = createContext<LocaleContextValue | null>(null);

function readInitialMarket(): string {
  if (typeof window === 'undefined') return 'HR';
  // Saved choice wins; otherwise guess from the browser locale region; otherwise default to HR.
  return window.localStorage.getItem(STORAGE_KEY) ?? marketFromBrowser() ?? 'HR';
}

function readLangOverride(): boolean {
  if (typeof window === 'undefined') return false;
  return window.localStorage.getItem(LANG_OVERRIDE_KEY) === 'en';
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [market, setMarketState] = useState<string>(readInitialMarket);
  const config = useMemo(() => marketConfig(market), [market]);
  const [englishOverride, setEnglishOverrideState] = useState<boolean>(readLangOverride);
  // The active language: the manual English override wins over the market's language.
  const lang = englishOverride ? 'en' : config.lang;
  // Sprint 10.40: the active language's overlay is lazy-loaded; bump this once its chunk arrives so every
  // t() consumer re-renders with the translated strings (until then they show the English fallback).
  const [langReady, setLangReady] = useState(0);
  // Sprint 10.42: did the visitor already have a saved market choice when the app loaded? If so we never
  // override it with geo. Captured once at first render, before the config→localStorage effect writes it.
  const hadSavedChoice = useRef(typeof window !== 'undefined' && !!window.localStorage.getItem(STORAGE_KEY));

  // Sprint 10.42: on a FRESH visit (no saved choice), upgrade the browser-locale guess to the visitor's real
  // country from the CDN geo header — so e.g. a French visitor with an English browser starts on France.
  useEffect(() => {
    if (hadSavedChoice.current) return;
    let cancelled = false;
    void fetchGeoCountry().then((country) => {
      if (cancelled || !country) return;
      const geoMarket = regionToMarket(country);
      if (geoMarket) setMarketState(geoMarket);
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    setFormattingMarket(config.code);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, config.code);
      window.document.documentElement.lang = lang;
    }
  }, [config, lang]);

  useEffect(() => {
    let cancelled = false;
    void ensureLangLoaded(lang).then(() => {
      if (!cancelled) setLangReady((version) => version + 1);
    });
    return () => { cancelled = true; };
  }, [lang]);

  const setMarket = useCallback((next: string) => {
    setMarketState(marketConfig(next).code);
  }, []);

  const setEnglishOverride = useCallback((on: boolean) => {
    setEnglishOverrideState(on);
    try {
      if (on) window.localStorage.setItem(LANG_OVERRIDE_KEY, 'en');
      else window.localStorage.removeItem(LANG_OVERRIDE_KEY);
    } catch {
      // private mode — in-memory state still applies for this session.
    }
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => translate(key, lang, params),
    // langReady is a deliberate dependency: re-create t (→ re-render consumers) once the overlay loads.
    [lang, langReady]
  );

  const value = useMemo<LocaleContextValue>(
    () => ({ market: config.code, config, setMarket, t, lang, englishOverride, setEnglishOverride }),
    [config, setMarket, t, lang, englishOverride, setEnglishOverride]
  );

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale(): LocaleContextValue {
  const ctx = useContext(LocaleContext);
  if (!ctx) {
    throw new Error('useLocale must be used within a LocaleProvider');
  }
  return ctx;
}
