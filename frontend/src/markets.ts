// Sprint 10.13 (#3): EU market configuration (mirrors backend ai.budgetspace.product.Markets).
// Only EUR markets are offered in the UI for now, because the catalog prices are in EUR — adding a
// non-EUR market (PLN/CZK/…) requires its own verified, currency-correct catalog first.
// `available` = a real per-market catalog exists today; others are foundation/"coming soon".
export type Lang = 'hr' | 'en' | 'de' | 'it' | 'sl' | 'fi' | 'fr' | 'nl' | 'sk' | 'es' | 'pt';

export interface MarketConfig {
  code: string;
  label: string;
  currency: string;
  locale: string;
  lang: Lang;
  available: boolean;
  flag: string;
}

// Sprint 10.28: all six EUR markets are now exposed. HR is Croatian; the other markets render in English
// Sprint 10.32: each market now renders in its own language (HR Croatian, SI Slovenian, AT/DE German,
// IT Italian, FI Finnish), with English as the fallback for any missing key. Each has a verified,
// currency-correct EUR catalog. `flag` drives the country picker.
export const MARKETS: MarketConfig[] = [
  { code: 'HR', label: 'Hrvatska', currency: 'EUR', locale: 'hr-HR', lang: 'hr', available: true, flag: '🇭🇷' },
  { code: 'SI', label: 'Slovenija', currency: 'EUR', locale: 'sl-SI', lang: 'sl', available: true, flag: '🇸🇮' },
  { code: 'AT', label: 'Österreich', currency: 'EUR', locale: 'de-AT', lang: 'de', available: true, flag: '🇦🇹' },
  { code: 'DE', label: 'Deutschland', currency: 'EUR', locale: 'de-DE', lang: 'de', available: true, flag: '🇩🇪' },
  { code: 'IT', label: 'Italia', currency: 'EUR', locale: 'it-IT', lang: 'it', available: true, flag: '🇮🇹' },
  { code: 'FI', label: 'Suomi', currency: 'EUR', locale: 'fi-FI', lang: 'fi', available: true, flag: '🇫🇮' },
  // Sprint 10.35: France — EUR, fully French-localised, verified IKEA catalog (IKEA-only, no JYSK in FR).
  { code: 'FR', label: 'France', currency: 'EUR', locale: 'fr-FR', lang: 'fr', available: true, flag: '🇫🇷' },
  // Sprint 10.37: Netherlands — EUR, Dutch-localised, verified IKEA + JYSK catalog.
  { code: 'NL', label: 'Nederland', currency: 'EUR', locale: 'nl-NL', lang: 'nl', available: true, flag: '🇳🇱' },
  // Sprint 10.38: Slovakia — EUR, Slovak-localised, verified IKEA + JYSK catalog.
  { code: 'SK', label: 'Slovensko', currency: 'EUR', locale: 'sk-SK', lang: 'sk', available: true, flag: '🇸🇰' },
  // Sprint 10.39: Spain — EUR, Spanish-localised, verified IKEA catalog (IKEA-only).
  { code: 'ES', label: 'España', currency: 'EUR', locale: 'es-ES', lang: 'es', available: true, flag: '🇪🇸' },
  // Sprint 10.41: Portugal — EUR, Portuguese-localised, verified IKEA catalog (IKEA-only).
  { code: 'PT', label: 'Portugal', currency: 'EUR', locale: 'pt-PT', lang: 'pt', available: true, flag: '🇵🇹' }
];

// Sprint 10.30: major cities per market for the optional city picker (datalist suggestions; the user can
// always type a different city). Kept in sync with the prompt city-detection patterns below.
export const CITIES_BY_MARKET: Record<string, string[]> = {
  HR: ['Zagreb', 'Split', 'Rijeka', 'Osijek', 'Zadar', 'Pula', 'Slavonski Brod', 'Karlovac', 'Varaždin', 'Šibenik', 'Dubrovnik', 'Sisak'],
  SI: ['Ljubljana', 'Maribor', 'Celje', 'Kranj', 'Koper', 'Velenje', 'Novo Mesto', 'Ptuj'],
  AT: ['Wien', 'Graz', 'Linz', 'Salzburg', 'Innsbruck', 'Klagenfurt', 'Villach', 'Wels'],
  DE: ['Berlin', 'München', 'Hamburg', 'Köln', 'Frankfurt', 'Stuttgart', 'Düsseldorf', 'Leipzig', 'Dresden', 'Hannover'],
  IT: ['Roma', 'Milano', 'Napoli', 'Torino', 'Palermo', 'Genova', 'Bologna', 'Firenze', 'Venezia', 'Verona'],
  FI: ['Helsinki', 'Espoo', 'Tampere', 'Vantaa', 'Oulu', 'Turku', 'Jyväskylä', 'Lahti'],
  FR: ['Paris', 'Marseille', 'Lyon', 'Toulouse', 'Nice', 'Nantes', 'Strasbourg', 'Montpellier', 'Bordeaux', 'Lille'],
  NL: ['Amsterdam', 'Rotterdam', 'Den Haag', 'Utrecht', 'Eindhoven', 'Groningen', 'Tilburg', 'Almere', 'Breda', 'Nijmegen'],
  SK: ['Bratislava', 'Košice', 'Prešov', 'Žilina', 'Nitra', 'Banská Bystrica', 'Trnava', 'Trenčín', 'Martin', 'Poprad'],
  ES: ['Madrid', 'Barcelona', 'Valencia', 'Sevilla', 'Zaragoza', 'Málaga', 'Murcia', 'Palma', 'Bilbao', 'Alicante'],
  PT: ['Lisboa', 'Porto', 'Vila Nova de Gaia', 'Braga', 'Coimbra', 'Amadora', 'Funchal', 'Setúbal', 'Almada', 'Faro'],
};

export function citiesForMarket(code?: string): string[] {
  return CITIES_BY_MARKET[(code ?? 'HR').toUpperCase()] ?? [];
}

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
  { market: 'FR', pattern: /\b(francusk\w*|france|pariz\w*|paris|marseille|lyon|toulouse|\bnica\b|nice|nantes|strasbourg|bordeaux|lille|montpellier)\b/ },
  { market: 'NL', pattern: /\b(nizozemsk\w*|netherlands|holland|nederland|amsterdam|rotterdam|den haag|utrecht|eindhoven|groningen|tilburg|nijmegen)\b/ },
  { market: 'SK', pattern: /\b(slovack\w*|slovakia|slovensk\w*|bratislav\w*|kosic\w*|presov|zilina|nitra|banska bystrica|trnava|trencin|poprad)\b/ },
  { market: 'ES', pattern: /\b(spanjolsk\w*|spain|espana|espanol\w*|madrid|barcelon\w*|valencia|sevilla|zaragoza|malaga|bilbao|alicante|murcia)\b/ },
  { market: 'PT', pattern: /\b(portugalsk\w*|portugal|portugues\w*|lisbo\w*|lisbon|porto|braga|coimbra|funchal|faro|setubal|almada)\b/ },
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
