// Sprint 10.13 (#3): lightweight i18n foundation. HR is the default; EN is used for the other
// (EUR) markets for now. This is a starter dictionary for the highest-visibility strings — extend it
// as more of the UI is localised (most planner copy is still Croatian and is a follow-up).
import type { Lang } from './markets';

type Entry = Record<Lang, string>;

const DICTIONARY: Record<string, Entry> = {
  'nav.how': { hr: 'Kako radi', en: 'How it works' },
  'nav.planner': { hr: 'Planer', en: 'Planner' },
  'nav.cta': { hr: 'Složi prostor', en: 'Plan a room' },
  'header.market': { hr: 'Država', en: 'Country' },

  'planner.eyebrow': { hr: 'Planer za kupnju', en: 'Shopping planner' },
  'planner.heading': { hr: 'Prvo napišeš želju. Desno dobiješ gotov plan.', en: 'Describe what you want. Get a ready plan.' },
  'planner.subheading': {
    hr: 'Nema komplicirane forme. Opiši prostor svojim riječima, a zatim po potrebi dotjeraj budžet, trgovine ili stvari koje već imaš.',
    en: 'No complicated form. Describe your space in your own words, then tweak budget, stores or what you already own.'
  },
  'planner.generate': { hr: 'Složi moj plan', en: 'Build my plan' },
  'planner.generating': { hr: 'Slažem plan...', en: 'Building plan...' },
  'planner.marketComingSoon': {
    hr: 'Katalog za ovu državu se još puni — prikazujemo opće prijedloge. Cijene su u eurima.',
    en: 'The catalog for this country is still being built — showing general suggestions. Prices are in euros.'
  },

  'pricing.eyebrow': { hr: 'Trebaš više planova?', en: 'Need more plans?' },
  'pricing.heading': {
    hr: 'Osnovni AI plan je besplatan. Nadogradi kad planiraš više.',
    en: 'The core AI plan is free. Upgrade when you plan more.'
  },
  'pricing.sub': {
    hr: 'Spremi i usporedi više ideja za sobe, izvezi popis za kupnju ili planiraj više prostorija. Plaćanje još nije aktivno — ovo je pilot cijena.',
    en: 'Save and compare more room ideas, export your shopping list, or plan multiple rooms. No checkout yet — this is pilot pricing.'
  },

  'product.reviews': { hr: 'Recenzije u trgovini', en: 'Reviews in store' }
};

export function translate(key: string, lang: Lang): string {
  const entry = DICTIONARY[key];
  if (!entry) return key;
  return entry[lang] ?? entry.hr ?? key;
}
