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
    updated: 'Zadnja izmjena: 23.06.2026.',
    disclaimer: 'Predložak — daj ga pravniku na pregled i ispuni podatke u Impressumu prije javnog lansiranja.',
    sections: [
      { heading: 'Tko smo', body: [
        'BudgetSpace je besplatan, nekomercijalni projekt (beta) i voditelj obrade tvojih osobnih podataka. Kontakt: budgetspace.ai@gmail.com.',
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
    updated: 'Zadnja izmjena: 23.06.2026.',
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
        'Trenutno je usluga potpuno besplatna. Plus (planiran: 5,99 €/mjesečno preko Stripea, mjesečna obnova, otkaz bilo kad) tek je u pripremi; kad postane dostupan, primjenjivat će se i Stripeovi uvjeti.',
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
    updated: 'Zadnja izmjena: 23.06.2026.',
    disclaimer: 'BudgetSpace je trenutno besplatan, nekomercijalni projekt (beta) — ne prodajemo robu ni usluge i ne naplaćujemo. Kontakt je naveden niže. Kad uvedemo plaćeni Plus, ovdje će biti potpuni podaci poslovnog subjekta.',
    sections: [
      { heading: 'Pružatelj usluge', body: [
        'BudgetSpace — besplatan, nekomercijalni projekt (beta).',
        'E-mail: budgetspace.ai@gmail.com',
      ] },
    ],
  },
};

const EN: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Privacy Policy',
    updated: 'Last updated: 2026-06-23',
    disclaimer: 'Template — have a lawyer review it and complete the Impressum details before a public launch.',
    sections: [
      { heading: 'Who we are', body: [
        'BudgetSpace is a free, non-commercial project (beta) and the controller of your personal data. Contact: budgetspace.ai@gmail.com.',
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
    updated: 'Last updated: 2026-06-23',
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
        'The service is currently entirely free. Plus (planned: €5.99/month via Stripe, monthly renewal, cancel anytime) is not available yet; once it launches, Stripe’s terms will also apply.',
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
    updated: 'Last updated: 2026-06-23',
    disclaimer: 'BudgetSpace is currently a free, non-commercial project (beta) — we do not sell goods or services and we do not charge. Contact is below. Full business-entity details will appear here once paid Plus launches.',
    sections: [
      { heading: 'Service provider', body: [
        'BudgetSpace — a free, non-commercial project (beta).',
        'Email: budgetspace.ai@gmail.com',
      ] },
    ],
  },
};

export function legalDoc(lang: string, key: LegalKey): LegalDoc {
  return (lang === 'hr' ? HR : EN)[key];
}
