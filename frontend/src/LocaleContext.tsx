// Sprint 10.13 (#3): app-wide market + language. Holds the selected market, exposes a `t()`
// translator for the market's language, and keeps the currency formatter in sync.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { marketConfig, marketFromBrowser, regionToMarket, type MarketConfig } from './markets';
import { ensureLangLoaded, translate } from './i18n';
import { fetchGeoCountry } from './api/client';
import { setFormattingMarket } from './utils/planner';

const STORAGE_KEY = 'budgetspace.market';

interface LocaleContextValue {
  market: string;
  config: MarketConfig;
  setMarket: (market: string) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const LocaleContext = createContext<LocaleContextValue | null>(null);

function readInitialMarket(): string {
  if (typeof window === 'undefined') return 'HR';
  // Saved choice wins; otherwise guess from the browser locale region; otherwise default to HR.
  return window.localStorage.getItem(STORAGE_KEY) ?? marketFromBrowser() ?? 'HR';
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [market, setMarketState] = useState<string>(readInitialMarket);
  const config = useMemo(() => marketConfig(market), [market]);
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
      window.document.documentElement.lang = config.lang;
    }
  }, [config]);

  useEffect(() => {
    let cancelled = false;
    void ensureLangLoaded(config.lang).then(() => {
      if (!cancelled) setLangReady((version) => version + 1);
    });
    return () => { cancelled = true; };
  }, [config.lang]);

  const setMarket = useCallback((next: string) => {
    setMarketState(marketConfig(next).code);
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => translate(key, config.lang, params),
    // langReady is a deliberate dependency: re-create t (→ re-render consumers) once the overlay loads.
    [config.lang, langReady]
  );

  const value = useMemo<LocaleContextValue>(() => ({ market: config.code, config, setMarket, t }), [config, setMarket, t]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale(): LocaleContextValue {
  const ctx = useContext(LocaleContext);
  if (!ctx) {
    throw new Error('useLocale must be used within a LocaleProvider');
  }
  return ctx;
}
