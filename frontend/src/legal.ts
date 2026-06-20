// Sprint 10.72 — legal/GDPR scaffold. App-specific TEMPLATE text for Privacy, Terms and Impressum, rendered by
// LegalModal. Croatian + English; other locales fall back to English. These are a starting point — the owner must
// review them with a lawyer and fill in the [...] placeholders (especially the Impressum) before a public launch.

export type LegalKey = 'privacy' | 'terms' | 'impressum';

export interface LegalSection {
  heading: string;
  body: string[];
}

export interface LegalDoc {
  title: string;
  updated: string;
  disclaimer?: string;
  sections: LegalSection[];
}

const HR: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Pravila privatnosti',
    updated: 'Zadnja izmjena: 20.06.2026.',
    disclaimer: 'Predložak — daj ga pravniku na pregled i ispuni podatke u Impressumu prije javnog lansiranja.',
    sections: [
      { heading: 'Tko smo', body: [
        'BudgetSpace je voditelj obrade tvojih osobnih podataka. Kontakt: [ispuni — email].',
      ] },
      { heading: 'Koje podatke prikupljamo', body: [
        'Ako se prijaviš Googleom: ime, e-mail i profilnu sliku koje nam Google proslijedi.',
        'Planove i unose (soba, budžet, stil) koje sam spremiš.',
        'Približnu državu iz CDN zaglavlja — samo da odaberemo ispravno tržište i valutu.',
        'Ako kupiš Plus: plaćanje obrađuje Stripe. Mi ne vidimo niti spremamo podatke o tvojoj kartici.',
        'Tekst koji upišeš AI asistentu šalje se davatelju AI usluge isključivo radi obrade tvog upita.',
      ] },
      { heading: 'Kolačići', body: [
        'Koristimo jedan nužni kolačić za prijavu (bs_auth) — bez njega prijava ne radi. Nemamo reklamne ni kolačiće za praćenje.',
      ] },
      { heading: 'S kim dijelimo podatke', body: [
        'Google (prijava), Stripe (naplata Plusa), davatelj AI usluge (obrada upita) i eBay (prikaz rabljenih oglasa). Tvoje podatke ne prodajemo.',
      ] },
      { heading: 'Koliko dugo čuvamo', body: [
        'Dok ne obrišeš račun ili dok ti sesija ne istekne. Račun i sve povezane podatke možeš obrisati u aplikaciji ("Obriši račun").',
      ] },
      { heading: 'Tvoja prava (GDPR)', body: [
        'Imaš pravo na pristup, ispravak, brisanje i prigovor. Brisanje računa dostupno je odmah u aplikaciji; za ostala prava nas kontaktiraj.',
      ] },
    ],
  },
  terms: {
    title: 'Uvjeti korištenja',
    updated: 'Zadnja izmjena: 20.06.2026.',
    disclaimer: 'Predložak — daj ga pravniku na pregled prije javnog lansiranja.',
    sections: [
      { heading: 'Što je usluga', body: [
        'BudgetSpace pomaže isplanirati opremanje sobe unutar zadanog budžeta. Nismo trgovina i ne prodajemo proizvode.',
        'Cijene i dostupnost su procjene i mogu biti zastarjele — provjeri kod trgovca prije kupnje. Partnerske linkove, ako ih dodamo, jasno ćemo označiti i nikad neće mijenjati koji je proizvod najbolji za tebe.',
      ] },
      { heading: 'Račun', body: [
        'Za spremanje i AI asistenta prijavljuješ se Google računom; odgovoran si za njegovu sigurnost.',
      ] },
      { heading: 'Plus pretplata', body: [
        'Plus je 5,99 €/mjesečno, naplata preko Stripea, obnavlja se mjesečno i možeš ga otkazati bilo kad. Primjenjuju se i Stripeovi uvjeti.',
      ] },
      { heading: 'Prihvatljivo korištenje', body: [
        'Ne zloupotrebljavaj uslugu — bez automatiziranog preopterećenja ni pokušaja zaobilaženja ograničenja.',
      ] },
      { heading: 'Bez jamstva i odgovornosti', body: [
        'Usluga se pruža "kakva jest". Ne jamčimo točnost cijena ni dostupnost i ne odgovaramo za odluke o kupnji donesene na temelju prijedloga.',
      ] },
      { heading: 'Izmjene i mjerodavno pravo', body: [
        'Uvjete možemo povremeno mijenjati. Primjenjuje se pravo Republike Hrvatske i EU.',
      ] },
    ],
  },
  impressum: {
    title: 'Impressum',
    updated: 'Zadnja izmjena: 20.06.2026.',
    disclaimer: 'U Hrvatskoj/EU je Impressum zakonski obavezan. Ispuni stvarne podatke prije lansiranja.',
    sections: [
      { heading: 'Pružatelj usluge', body: [
        '[Ime i prezime / naziv obrta ili tvrtke]',
        '[Adresa sjedišta]',
        '[OIB]',
        'E-mail: [kontakt e-mail]',
      ] },
      { heading: 'Odgovorna osoba', body: [
        '[Ime i prezime odgovorne osobe]',
      ] },
    ],
  },
};

const EN: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Privacy Policy',
    updated: 'Last updated: 2026-06-20',
    disclaimer: 'Template — have a lawyer review it and complete the Impressum details before a public launch.',
    sections: [
      { heading: 'Who we are', body: [
        'BudgetSpace is the controller of your personal data. Contact: [fill in — email].',
      ] },
      { heading: 'What we collect', body: [
        'If you sign in with Google: the name, email and profile picture Google passes to us.',
        'The plans and inputs (room, budget, style) you choose to save.',
        'An approximate country from the CDN header — only to pick the right market and currency.',
        'If you buy Plus: payment is handled by Stripe. We never see or store your card details.',
        'Text you type to the AI assistant is sent to the AI provider solely to process your request.',
      ] },
      { heading: 'Cookies', body: [
        'We use a single strictly-necessary sign-in cookie (bs_auth) — sign-in does not work without it. We use no advertising or tracking cookies.',
      ] },
      { heading: 'Who we share with', body: [
        'Google (sign-in), Stripe (Plus billing), the AI provider (processing prompts) and eBay (showing second-hand listings). We do not sell your data.',
      ] },
      { heading: 'How long we keep it', body: [
        'Until you delete your account or your session expires. You can delete your account and all related data in the app ("Delete account").',
      ] },
      { heading: 'Your rights (GDPR)', body: [
        'You have the right to access, correct, delete and object. Account deletion is available immediately in the app; contact us for the other rights.',
      ] },
    ],
  },
  terms: {
    title: 'Terms of Use',
    updated: 'Last updated: 2026-06-20',
    disclaimer: 'Template — have a lawyer review it before a public launch.',
    sections: [
      { heading: 'What the service is', body: [
        'BudgetSpace helps you plan furnishing a room within a budget. We are not a store and do not sell products.',
        'Prices and availability are estimates and may be out of date — check with the retailer before buying. Affiliate links, if added, will be clearly labelled and will never change which product is best for you.',
      ] },
      { heading: 'Account', body: [
        'Saving and the AI assistant use Google sign-in; you are responsible for your account’s security.',
      ] },
      { heading: 'Plus subscription', body: [
        'Plus is €5.99/month, billed via Stripe, renews monthly and can be cancelled at any time. Stripe’s terms also apply.',
      ] },
      { heading: 'Acceptable use', body: [
        'Do not abuse the service — no automated overload or attempts to bypass limits.',
      ] },
      { heading: 'No warranty / liability', body: [
        'The service is provided "as is". We do not guarantee price accuracy or availability and are not liable for purchasing decisions made from our suggestions.',
      ] },
      { heading: 'Changes and governing law', body: [
        'We may update these terms from time to time. The laws of Croatia and the EU apply.',
      ] },
    ],
  },
  impressum: {
    title: 'Impressum',
    updated: 'Last updated: 2026-06-20',
    disclaimer: 'An Impressum is legally required in Croatia/the EU. Fill in real details before launch.',
    sections: [
      { heading: 'Service provider', body: [
        '[Full name / trade or company name]',
        '[Registered address]',
        '[VAT / OIB]',
        'Email: [contact email]',
      ] },
      { heading: 'Responsible person', body: [
        '[Name of the responsible person]',
      ] },
    ],
  },
};

export function legalDoc(lang: string, key: LegalKey): LegalDoc {
  return (lang === 'hr' ? HR : EN)[key];
}
