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

export function isSupportedMarket(code?: string): boolean {
  return !!code && MARKETS.some((market) => market.code === code.toUpperCase());
}

// Sprint 10.13 (#3): smart default — derive the market from the browser locale region (e.g.
// "de-DE" -> DE, "sl-SI" -> SI) so a visitor starts on a sensible country without doing anything.
// Returns undefined if the region isn't a market we support (caller falls back to HR).
export function regionToMarket(region?: string): string | undefined {
  if (!region) return undefined;
  const code = region.toUpperCase();
  return isSupportedMarket(code) ? code : undefined;
}

export function marketFromBrowser(): string | undefined {
  if (typeof navigator === 'undefined') return undefined;
  const langs = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language];
  for (const lang of langs) {
    if (!lang) continue;
    const parts = lang.split('-');
    const region = parts.length > 1 ? parts[parts.length - 1] : undefined;
    const market = regionToMarket(region);
    if (market) return market;
  }
  return undefined;
}

// Sprint 10.13 (#3): detect the country from the user's prompt (city/country names) so people who
// just type "stan u Ljubljani" still get the right market. Diacritics are stripped before matching,
// so patterns are written in their plain-ASCII form. Only matches markets we actually support.
const MARKET_DETECTION: Array<{ market: string; pattern: RegExp }> = [
  { market: 'SI', pattern: /\b(slovenij\w*|slovenia|ljubljan\w*|maribor|celje|kranj|koper|velenje)\b/ },
  { market: 'AT', pattern: /\b(austrij\w*|osterreich|austria|wien|bec|vienna|graz|salzburg|linz|innsbruck)\b/ },
  { market: 'DE', pattern: /\b(njemack\w*|deutschland|germany|berlin|munchen|munich|hamburg|koln|cologne|frankfurt|stuttgart|dresden|leipzig)\b/ },
  { market: 'IT', pattern: /\b(italij\w*|italia|italy|\brim\b|roma|rome|milano|milan|napulj|napoli|torino|firenze|venecij\w*|venezia)\b/ },
  { market: 'FI', pattern: /\b(finsk\w*|finland|suomi|helsink\w*|espoo|tampere|turku|oulu)\b/ },
  { market: 'HR', pattern: /\b(hrvatsk\w*|croatia|zagreb\w*|split\w*|rijek\w*|osijek|zadar|pula|dubrovnik|varazdin|karlovac|sisak)\b/ }
];

function stripDiacritics(value: string): string {
  return value.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
}

export function detectMarketFromText(text?: string): string | undefined {
  if (!text) return undefined;
  const normalized = stripDiacritics(text);
  for (const entry of MARKET_DETECTION) {
    if (isSupportedMarket(entry.market) && entry.pattern.test(normalized)) {
      return entry.market;
    }
  }
  return undefined;
}
