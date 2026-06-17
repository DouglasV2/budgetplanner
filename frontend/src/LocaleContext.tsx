// Sprint 10.13 (#3): app-wide market + language. Holds the selected market, exposes a `t()`
// translator for the market's language, and keeps the currency formatter in sync.
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { marketConfig, marketFromBrowser, type MarketConfig } from './markets';
import { translate } from './i18n';
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

  useEffect(() => {
    setFormattingMarket(config.code);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, config.code);
      window.document.documentElement.lang = config.lang;
    }
  }, [config]);

  const setMarket = useCallback((next: string) => {
    setMarketState(marketConfig(next).code);
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => translate(key, config.lang, params),
    [config.lang]
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
