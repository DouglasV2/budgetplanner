// Sprint 10.13 (#3): EU market configuration (mirrors backend ai.budgetspace.product.Markets).
// Only EUR markets are offered in the UI for now, because the catalog prices are in EUR — adding a
// non-EUR market (PLN/CZK/…) requires its own verified, currency-correct catalog first.
// `available` = a real per-market catalog exists today; others are foundation/"coming soon".
export type Lang = 'hr' | 'en';

export interface MarketConfig {
  code: string;
  label: string;
  currency: string;
  locale: string;
  lang: Lang;
  available: boolean;
}

export const MARKETS: MarketConfig[] = [
  { code: 'HR', label: 'Hrvatska', currency: 'EUR', locale: 'hr-HR', lang: 'hr', available: true },
  { code: 'SI', label: 'Slovenija', currency: 'EUR', locale: 'sl-SI', lang: 'en', available: false },
  { code: 'AT', label: 'Österreich', currency: 'EUR', locale: 'de-AT', lang: 'en', available: false },
  { code: 'DE', label: 'Deutschland', currency: 'EUR', locale: 'de-DE', lang: 'en', available: false },
  { code: 'IT', label: 'Italia', currency: 'EUR', locale: 'it-IT', lang: 'en', available: false },
  { code: 'FI', label: 'Suomi', currency: 'EUR', locale: 'fi-FI', lang: 'en', available: false }
];

export function marketConfig(code?: string): MarketConfig {
  return MARKETS.find((market) => market.code === (code ?? 'HR')) ?? MARKETS[0];
}
