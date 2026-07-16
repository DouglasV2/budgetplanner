// App-specific Privacy, Terms and Impressum, rendered by LegalModal. Croatian + English + German (DE/AT are
// shipped markets and the strictest Impressum jurisdictions, so they get their own language; other locales fall
// back to English).
//
// Sprint 10.185 (privacy/consent/legal hardening): the text below is written to match what the app ACTUALLY
// does (see the Phase-0 audit). If you change a data flow, update the matching section here.
//
// OPERATOR STATUS: BudgetSpace is run by an individual (a natural person) as a free beta. It is NOT a registered
// company / obrt / d.o.o., so there is deliberately no OIB, VAT id or court-registry number — do NOT invent one.
// If a business is registered later, or before charging money, add the entity + OIB/VAT here and have the text
// lawyer-reviewed.

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

// ── Operator identity ────────────────────────────────────────────────────────────────────────────
// Single source of truth for who runs BudgetSpace — an individual operator, rendered into the Impressum and the
// Privacy "controller" line in every language. The full postal address appears ONLY in these legal contexts
// (Impressum + Privacy controller), never in marketing, navigation or the planner UI.
const OPERATOR = {
  name: 'Bruno Pušić',
  street: 'Ulica Emanuela Vidovića 28',
  postalCity: '10360 Sesvete',
  email: 'budgetspaceai@gmail.com',
};

const LAST_UPDATED_HR = 'Zadnja izmjena: 14.07.2026.';
const LAST_UPDATED_EN = 'Last updated: 2026-07-14';
const LAST_UPDATED_DE = 'Zuletzt aktualisiert: 14.07.2026';

const HR: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Pravila privatnosti',
    updated: LAST_UPDATED_HR,
    sections: [
      { heading: 'Tko vodi BudgetSpace', body: [
        `BudgetSpace je besplatan projekt (beta) koji kao fizička osoba vodi ${OPERATOR.name}. On je voditelj obrade tvojih osobnih podataka.`,
        `Adresa: ${OPERATOR.street}, ${OPERATOR.postalCity}, Hrvatska. Kontakt: ${OPERATOR.email}.`,
      ] },
      { heading: 'Koje podatke obrađujemo i zašto', body: [
        'Prijava Googleom: ime, e-mail, profilna slika i tvoj Google identifikator (sub) — da imaš račun i da spremljeni planovi budu tvoji.',
        'Planovi i unosi koje spremiš: soba, budžet, stil, tekst koji upišeš (tvoj opis) i grad — da složimo i dotjeramo plan.',
        'Približna država iz zaglavlja CDN-a — samo da odaberemo ispravno tržište i valutu.',
        'IP adresa — privremeno, za ograničavanje broja zahtjeva i sigurnost (vidi „IP adrese i sigurnost").',
        'Pseudonimni signali korištenja: na koji si proizvod kliknuo i ocjena plana — vezani uz plan, ne uz tvoje ime.',
        'Google Analytics (samo ako pristaneš) — da razumijemo kako se aplikacija koristi (vidi zaseban odjeljak).',
        'Tekst koji upišeš, kad je AI uključen, šalje se davatelju AI usluge da protumači tvoj upit (vidi „AI asistent").',
        'E-mail za listu čekanja — samo ako ga sam ostaviš.',
      ] },
      { heading: 'Pravna osnova (čl. 6 GDPR)', body: [
        'Ugovor (čl. 6(1)(b)): vođenje računa, spremanje planova i obrada tvog upita na tvoj zahtjev.',
        'Legitimni interes (čl. 6(1)(f)): sigurnost i sprječavanje zloupotrebe, praćenje tehničkih grešaka te pseudonimna analitika klikova/ocjena radi poboljšanja usluge.',
        'Privola (čl. 6(1)(a)): Google Analytics. Privolu možeš povući u svakom trenutku.',
      ] },
      { heading: 'Google Analytics', body: [
        'Google Analytics je neobavezan i pokreće se tek nakon što ga izričito prihvatiš. Dok ne odlučiš, ne učitava se i ne šalje nijedan podatak.',
        'Ako odbiješ, BudgetSpace radi jednako — ništa ne gubiš. Odluku možeš promijeniti kad god želiš preko „Postavke privatnosti" u podnožju.',
        'Kad ga prihvatiš, Google Analytics može obrađivati mrežne identifikatore, podatke o uređaju/pregledniku, približnu lokaciju i događaje korištenja. Koristimo ga da shvatimo kako se aplikacija koristi, gdje korisnici zapnu i koje su funkcije korisne.',
        'Kolačići koje tada postavlja: _ga i _ga_<id> (identifikatori mjerenja). Postavljaju se tek nakon prihvaćanja; ako povučeš privolu, uklonimo ih koliko tehnički možemo.',
        'Ne koristimo Google Ads, remarketing ni personalizaciju oglasa. Ne tvrdimo da su podaci Google Analyticsa potpuno anonimni.',
      ] },
      { heading: 'Kolačići i lokalna pohrana', body: [
        'Nužno: bs_auth — kolačić za prijavu (do 30 dana); bs_oauth — kratkotrajni (10 min) sigurnosni kolačić tijekom prijave.',
        'Nužno (lokalna pohrana): bs-session-id (identifikator preglednika za ograničenja AI korištenja i povezivanje planova), budgetspace.market / budgetspace.lang (država i jezik), budgetspace.plannerDraft / budgetspace.moveInDraft (nacrti unosa) i budgetspace.consent (tvoja odluka o analitici).',
        'Neobavezno: kolačići Google Analyticsa — samo nakon što ih prihvatiš.',
        'Nemamo reklamne kolačiće ni kolačiće za remarketing.',
      ] },
      { heading: 'Prijava Googleom', body: [
        'Prijava ide preko Googlea (OAuth). Od Googlea dobivamo i spremamo tvoje ime, e-mail, profilnu sliku i Google identifikator; ne spremamo tvoju Google lozinku ni Google tokene.',
        'Podaci profila (ime, slika) osvježe se pri svakoj Google prijavi.',
      ] },
      { heading: 'Unosi, spremljeni i dijeljeni planovi', body: [
        'Kad spremiš plan, uz njega se spremaju i tvoj tekstualni opis (upit) i grad koji si upisao. Nemoj u opis upisivati osjetljive osobne podatke.',
        'Spremljeni plan ima privatnu poveznicu koju je teško pogoditi. Svatko tko dobije tu poveznicu može otvoriti plan i vidjeti proizvode, budžet, sobu i tekst spremljen uz plan (tvoj opis i grad) — ali ne tvoje ime ni e-mail. Poveznica nije „privatna" samo zato što ju je teško pogoditi.',
        'Dijeljenje se povlači brisanjem plana u aplikaciji — to je jedini način da poveznica prestane raditi. Spremljeni planovi nemaju automatski rok isteka.',
      ] },
      { heading: 'AI asistent', body: [
        'BudgetSpace može koristiti AI da protumači tvoj slobodni opis. Kad je AI nedostupan ili isključen, koristi se deterministička (pravilima vođena) zamjenska logika — ne nastaje svaki plan pomoću AI-ja.',
        'Kad se koristi, davatelj u produkciji je Google Gemini. Šalje se tvoj tekstualni opis i kontekst (soba, budžet, stil) — ne šaljemo tvoje ime, e-mail ni identifikator računa.',
        'BudgetSpace ne pohranjuje tvoj upit ni AI odgovor izvan onoga što sam spremiš u plan. Google kao obrađivač obrađuje tekst prema svojim uvjetima i ugovoru o obradi podataka.',
        'Prijedlozi AI-ja su procjene — prije kupnje provjeri cijenu, dimenzije i dostupnost kod trgovca. Nemoj unositi osjetljive osobne podatke.',
      ] },
      { heading: 'IP adrese, sigurnost i sprječavanje zloupotrebe', body: [
        'Tvoju IP adresu obrađujemo privremeno radi ograničavanja broja zahtjeva i sigurnosti; drži se u memoriji poslužitelja i ne spremamo je u tvoj profil.',
        'Kad je AI asistent uključen, IP adresa gosta može se čuvati do 45 dana kao ključ za dnevno ograničenje korištenja (prijavljeni korisnici umjesto toga koriste ključ računa).',
        'Poslužitelj i pružatelj infrastrukture mogu bilježiti IP adrese u sigurnosnim/tehničkim zapisima prema svojim zadanim postavkama.',
      ] },
      { heading: 'Praćenje grešaka (Sentry)', body: [
        'Tehničke greške šaljemo servisu Sentry radi otklanjanja kvarova. Šalju se samo poruke i tragovi grešaka; slanje osobnih podataka je isključeno (sendDefaultPii=false), pa se ne šalje sadržaj tvojih upita, tvoje ime ni e-mail.',
      ] },
      { heading: 'Hosting, baza i primatelji podataka', body: [
        'Aplikacija je smještena kod pružatelja usluge u oblaku (npr. Railway/Render/Fly) uz PostgreSQL bazu podataka.',
        'Google — prijava (Google Sign-In) i, kad je AI uključen, obrada upita (Google Gemini). Sentry — praćenje grešaka.',
        'Stripe — naplata Design Sessiona; trenutno neaktivno (besplatna beta), pa se sada ne obrađuju podaci o plaćanju.',
        'Tvoje podatke ne prodajemo. eBay nije primatelj tvojih podataka — dohvaćamo javne oglase po kategoriji i tržištu, a eBayu ne šaljemo nijedan tvoj osobni podatak.',
      ] },
      { heading: 'Međunarodni prijenosi', body: [
        'Neki pružatelji (Google, Sentry, Stripe) mogu obrađivati podatke izvan EU-a, npr. u SAD-u. Prijenos se temelji na standardnim ugovornim klauzulama (SCC) i/ili EU-US Data Privacy Frameworku. Za kopiju odgovarajućih mjera kontaktiraj nas.',
      ] },
      { heading: 'Koliko dugo čuvamo podatke', body: [
        'Račun i spremljeni planovi: dok ih sam ne obrišeš (nema automatskog isteka).',
        'Prijave (sesije): do 30 dana (uz mirovanje od 7 dana), zatim se brišu.',
        'Metapodaci o AI korištenju (uključujući privremeni IP ključ gosta): 45 dana.',
        'Pseudonimni klikovi/ocjene i e-mail liste čekanja: 18 mjeseci.',
        'Podaci za ograničenje zahtjeva: privremeno u memoriji (minute).',
        'Zapisi grešaka (Sentry) i Google Analytics: prema roku čuvanja postavljenom kod tih pružatelja (za Analytics postavljamo najkraći ponuđeni rok). Poslužiteljski zapisi i sigurnosne kopije: prema zadanim postavkama pružatelja infrastrukture.',
      ] },
      { heading: 'Tvoja prava i kako ih ostvariti', body: [
        'Imaš pravo na pristup, ispravak, brisanje, ograničenje, prigovor i prenosivost te pravo povući privolu (npr. za Analytics preko „Postavke privatnosti").',
        'Račun i sve povezane podatke možeš obrisati odmah u aplikaciji („Obriši račun"): brišu se račun, spremljeni planovi, zapisi o AI korištenju te lista čekanja vezana uz tvoj e-mail. U privremenim tehničkim zapisima kratko može ostati stavka; Stripe (ako se ikad uvede) i Sentry čuvaju podatke prema svojim uvjetima.',
        'Ako ne možeš pristupiti računu ili želiš ostvariti druga prava, javi nam se na ' + OPERATOR.email + '.',
        'Imaš pravo na pritužbu nadzornom tijelu — u Hrvatskoj je to Agencija za zaštitu osobnih podataka (AZOP), azop.hr.',
      ] },
      { heading: 'Izmjene i kontakt', body: [
        'Ova pravila možemo povremeno ažurirati; datum zadnje izmjene naveden je na vrhu. Za sva pitanja o privatnosti piši na ' + OPERATOR.email + '.',
      ] },
    ],
  },
  terms: {
    title: 'Uvjeti korištenja',
    updated: 'Zadnja izmjena: 03.07.2026.',
    sections: [
      { heading: 'Što je usluga', body: [
        'BudgetSpace pomaže isplanirati opremanje sobe unutar zadanog budžeta. Nismo trgovina i ne prodajemo proizvode.',
        'Cijene i dostupnost su procjene prikupljene od trgovaca i mogu biti zastarjele — uvijek provjeri kod trgovca prije kupnje.',
      ] },
      { heading: 'Zaštitni znakovi i neovisnost', body: [
        'IKEA, JYSK, eBay te sva ostala imena trgovaca i proizvoda zaštitni su znakovi svojih vlasnika. Koristimo ih isključivo da označimo stvarne proizvode i trgovine kod kojih se mogu kupiti.',
        'BudgetSpace je neovisan alat za planiranje i nije povezan s tim trgovcima, niti ga oni sponzoriraju ili podržavaju. Partnerske linkove, ako ih dodamo, jasno ćemo označiti i nikad neće mijenjati koji je proizvod najbolji za tebe.',
      ] },
      { heading: 'AI asistent', body: [
        'Prijedlozi AI asistenta su procjene za lakše planiranje, a ne stručni savjet. Provjeri cijene, dimenzije i dostupnost kod trgovca prije kupnje.',
      ] },
      { heading: 'Račun', body: [
        'Za spremanje i AI asistenta prijavljuješ se Google računom; odgovoran si za njegovu sigurnost.',
      ] },
      { heading: 'Design Session', body: [
        'Trenutno je usluga potpuno besplatna (rana beta) — sve funkcije su otključane. Nema pretplate ni mjesečne naplate. U budućnosti planiramo jednokratnu naplatu po "Design Sessionu" (jedno plaćanje, bez obnove); cijena i uvjeti bit će jasno prikazani prije bilo kakve naplate.',
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
    updated: LAST_UPDATED_HR,
    disclaimer: 'BudgetSpace vodi fizička osoba kao besplatan projekt (beta). Nije registrirani obrt ni trgovačko društvo, pa nema OIB-a za javni prikaz ni PDV broja. Ako se u budućnosti uvede naplata, ovdje ćemo dodati podatke poslovnog subjekta.',
    sections: [
      { heading: 'Pružatelj usluge', body: [
        OPERATOR.name,
        OPERATOR.street,
        `${OPERATOR.postalCity}, Hrvatska`,
        `E-mail: ${OPERATOR.email}`,
      ] },
    ],
  },
};

const EN: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Privacy Policy',
    updated: LAST_UPDATED_EN,
    sections: [
      { heading: 'Who operates BudgetSpace', body: [
        `BudgetSpace is a free project (beta) run by ${OPERATOR.name} as an individual (a natural person), the controller of your personal data.`,
        `Address: ${OPERATOR.street}, ${OPERATOR.postalCity}, Croatia. Contact: ${OPERATOR.email}.`,
      ] },
      { heading: 'What we process and why', body: [
        'Google sign-in: your name, email, profile picture and Google id (sub) — so you have an account and your saved plans are yours.',
        'Plans and inputs you save: room, budget, style, the text you type (your description) and city — to build and refine the plan.',
        'An approximate country from the CDN header — only to pick the right market and currency.',
        'Your IP address — temporarily, for rate limiting and security (see "IP addresses and security").',
        'Pseudonymous usage signals: which product you clicked and plan feedback — tied to a plan, not to your name.',
        'Google Analytics (only if you accept) — to understand how the app is used (see the dedicated section).',
        'The text you type, when AI is enabled, is sent to the AI provider to interpret your request (see "AI assistant").',
        'A waitlist email — only if you choose to leave one.',
      ] },
      { heading: 'Legal basis (Art. 6 GDPR)', body: [
        'Contract (Art. 6(1)(b)): running your account, saving plans, and processing your request on your behalf.',
        'Legitimate interests (Art. 6(1)(f)): security and abuse prevention, technical error monitoring, and pseudonymous click/feedback analytics to improve the service.',
        'Consent (Art. 6(1)(a)): Google Analytics. You can withdraw consent at any time.',
      ] },
      { heading: 'Google Analytics', body: [
        'Google Analytics is optional and only runs after you explicitly accept it. Until you decide, it is not loaded and sends no data.',
        'If you decline, BudgetSpace works exactly the same — you lose nothing. You can change your choice at any time via "Privacy settings" in the footer.',
        'When accepted, Google Analytics may process online identifiers, device/browser information, approximate location and usage events. We use it to understand how the app is used, where users get stuck, and which features are useful.',
        'Cookies it then sets: _ga and _ga_<id> (measurement identifiers). They are set only after acceptance; if you withdraw consent we remove them as far as technically possible.',
        'We do not use Google Ads, remarketing or ad personalization. We do not claim Google Analytics data is fully anonymous.',
      ] },
      { heading: 'Cookies and local storage', body: [
        'Necessary: bs_auth — the sign-in cookie (up to 30 days); bs_oauth — a short-lived (10 min) security cookie during sign-in.',
        'Necessary (local storage): bs-session-id (a per-browser id for AI usage limits and linking your plans), budgetspace.market / budgetspace.lang (country and language), budgetspace.plannerDraft / budgetspace.moveInDraft (input drafts), and budgetspace.consent (your analytics choice).',
        'Optional: Google Analytics cookies — only after you accept them.',
        'We use no advertising or remarketing cookies.',
      ] },
      { heading: 'Account and Google sign-in', body: [
        'Sign-in goes through Google (OAuth). From Google we receive and store your name, email, profile picture and Google id; we do not store your Google password or Google tokens.',
        'Profile data (name, picture) is refreshed on each Google sign-in.',
      ] },
      { heading: 'Inputs, saved and shared plans', body: [
        'When you save a plan, your text description (prompt) and the city you entered are saved with it. Do not put sensitive personal information in the description.',
        'A saved plan has a private, hard-to-guess link. Anyone who receives that link can open the plan and see the products, budget, room and the text saved with it (your description and city) — but not your name or email. A link is not "private" merely because it is hard to guess.',
        'Sharing is revoked by deleting the plan in the app — that is the only way to make the link stop working. Saved plans have no automatic expiry.',
      ] },
      { heading: 'AI assistant', body: [
        'BudgetSpace may use AI to interpret your free-text request. When AI is unavailable or disabled, a deterministic (rule-based) fallback is used — not every plan is generated by AI.',
        'When used, the production provider is Google Gemini. We send your text description plus context (room, budget, style) — we do not send your name, email or account id.',
        'BudgetSpace does not store your prompt or the AI response beyond what you choose to save in a plan. Google, as a processor, handles the text under its own terms and data-processing agreement.',
        'AI suggestions are estimates — check the price, dimensions and availability with the retailer before buying. Do not enter sensitive personal information.',
      ] },
      { heading: 'IP addresses, security and abuse prevention', body: [
        'We process your IP address temporarily for rate limiting and security; it is held in server memory and not stored in your profile.',
        'When the AI assistant is enabled, a guest device’s IP may be kept for up to 45 days as a daily usage-limit key (signed-in users use an account key instead).',
        'Our server and hosting provider may record IP addresses in security/technical logs under their default settings.',
      ] },
      { heading: 'Error monitoring (Sentry)', body: [
        'We send technical errors to Sentry to fix faults. Only error messages and stack traces are sent; sending personal data is disabled (sendDefaultPii=false), so the content of your prompts, your name and your email are not sent.',
      ] },
      { heading: 'Hosting, database and recipients', body: [
        'The app is hosted with a cloud provider (e.g. Railway/Render/Fly) with a PostgreSQL database.',
        'Google — sign-in (Google Sign-In) and, when AI is enabled, request processing (Google Gemini). Sentry — error monitoring.',
        'Stripe — Design Session billing; currently inactive (free beta), so no payment data is processed today.',
        'We do not sell your data. eBay is not a recipient of your data — we fetch public listings by category and market and send eBay none of your personal data.',
      ] },
      { heading: 'International transfers', body: [
        'Some providers (Google, Sentry, Stripe) may process data outside the EU, e.g. in the US. Transfers rely on Standard Contractual Clauses (SCCs) and/or the EU-US Data Privacy Framework. Contact us for a copy of the relevant safeguards.',
      ] },
      { heading: 'How long we keep data', body: [
        'Account and saved plans: until you delete them (no automatic expiry).',
        'Sign-in sessions: up to 30 days (7-day idle), then pruned.',
        'AI usage metadata (including a guest’s temporary IP key): 45 days.',
        'Pseudonymous clicks/feedback and waitlist emails: 18 months.',
        'Rate-limit data: transient, in memory (minutes).',
        'Error logs (Sentry) and Google Analytics: per the retention set with those providers (for Analytics we set the shortest option offered). Server logs and backups: per the hosting provider’s defaults.',
      ] },
      { heading: 'Your rights and how to use them', body: [
        'You have the right to access, rectification, erasure, restriction, objection and portability, and the right to withdraw consent (e.g. for Analytics via "Privacy settings").',
        'You can delete your account and all related data immediately in the app ("Delete account"): this erases your account, saved plans, AI usage records, and the waitlist entries tied to your email. A transient technical log may briefly retain an entry; Stripe (if ever introduced) and Sentry keep data under their own terms.',
        'If you cannot access your account or want to exercise other rights, contact us at ' + OPERATOR.email + '.',
        'You have the right to lodge a complaint with a supervisory authority — in Croatia, the Personal Data Protection Agency (AZOP, azop.hr); otherwise your national data-protection authority.',
      ] },
      { heading: 'Changes and contact', body: [
        'We may update this policy from time to time; the last-updated date is shown at the top. For any privacy questions, write to ' + OPERATOR.email + '.',
      ] },
    ],
  },
  terms: {
    title: 'Terms of Use',
    updated: 'Last updated: 2026-07-03',
    sections: [
      { heading: 'What the service is', body: [
        'BudgetSpace helps you plan furnishing a room within a budget. We are not a store and do not sell products.',
        'Prices and availability are estimates gathered from retailers and may be out of date — always check with the retailer before buying.',
      ] },
      { heading: 'Trademarks and independence', body: [
        'IKEA, JYSK, eBay and all other retailer and product names are trademarks of their respective owners. We use them only to identify the real products and the stores that sell them.',
        'BudgetSpace is an independent planning tool and is not affiliated with, endorsed by, or sponsored by any of these retailers. Affiliate links, if added, will be clearly labelled and will never change which product is best for you.',
      ] },
      { heading: 'AI assistant', body: [
        'The AI assistant’s suggestions are estimates to help you plan, not professional advice. Check prices, dimensions and availability with the retailer before buying.',
      ] },
      { heading: 'Account', body: [
        'Saving and the AI assistant use Google sign-in; you are responsible for your account’s security.',
      ] },
      { heading: 'Design Session', body: [
        'The service is currently entirely free (early beta) — all features are unlocked. There is no subscription and no recurring billing. In the future we plan a one-time charge per "Design Session" (a single payment, no renewal); the price and terms will be shown clearly before any charge.',
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
    updated: LAST_UPDATED_EN,
    disclaimer: 'BudgetSpace is run by an individual (a natural person) as a free project (beta). It is not a registered company or sole trader, so there is no public company registration or VAT id. Full business-entity details will be added here if charging is introduced.',
    sections: [
      { heading: 'Service provider', body: [
        OPERATOR.name,
        OPERATOR.street,
        `${OPERATOR.postalCity}, Croatia`,
        `Email: ${OPERATOR.email}`,
      ] },
    ],
  },
};

// German — DE/AT are shipped markets and the strictest Impressum jurisdictions (§5 DDG), so they get a full
// German set rather than the English fallback.
const DE: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Datenschutzerklärung',
    updated: LAST_UPDATED_DE,
    sections: [
      { heading: 'Wer BudgetSpace betreibt', body: [
        `BudgetSpace ist ein kostenloses Projekt (Beta), betrieben von ${OPERATOR.name} als Privatperson (natürliche Person), dem Verantwortlichen für Ihre personenbezogenen Daten.`,
        `Anschrift: ${OPERATOR.street}, ${OPERATOR.postalCity}, Kroatien. Kontakt: ${OPERATOR.email}.`,
      ] },
      { heading: 'Welche Daten wir verarbeiten und warum', body: [
        'Anmeldung mit Google: Name, E-Mail, Profilbild und Ihre Google-ID (sub) — damit Sie ein Konto haben und Ihre gespeicherten Pläne Ihnen gehören.',
        'Pläne und Eingaben, die Sie speichern: Raum, Budget, Stil, der von Ihnen eingegebene Text (Ihre Beschreibung) und Stadt — um den Plan zu erstellen und anzupassen.',
        'Ein ungefähres Land aus dem CDN-Header — nur um den richtigen Markt und die Währung zu wählen.',
        'Ihre IP-Adresse — vorübergehend, zur Ratenbegrenzung und Sicherheit (siehe „IP-Adressen und Sicherheit").',
        'Pseudonyme Nutzungssignale: welches Produkt Sie angeklickt haben und Plan-Feedback — mit einem Plan verknüpft, nicht mit Ihrem Namen.',
        'Google Analytics (nur mit Ihrer Zustimmung) — um zu verstehen, wie die App genutzt wird (siehe eigener Abschnitt).',
        'Der von Ihnen eingegebene Text wird, wenn die KI aktiviert ist, an den KI-Anbieter gesendet, um Ihre Anfrage auszuwerten (siehe „KI-Assistent").',
        'Eine Warteliste-E-Mail — nur wenn Sie sie hinterlassen.',
      ] },
      { heading: 'Rechtsgrundlage (Art. 6 DSGVO)', body: [
        'Vertrag (Art. 6 Abs. 1 lit. b): Betrieb Ihres Kontos, Speichern von Plänen und Verarbeitung Ihrer Anfrage in Ihrem Auftrag.',
        'Berechtigtes Interesse (Art. 6 Abs. 1 lit. f): Sicherheit und Missbrauchsvermeidung, technische Fehlerüberwachung sowie pseudonyme Klick-/Feedback-Analyse zur Verbesserung des Dienstes.',
        'Einwilligung (Art. 6 Abs. 1 lit. a): Google Analytics. Sie können die Einwilligung jederzeit widerrufen.',
      ] },
      { heading: 'Google Analytics', body: [
        'Google Analytics ist optional und läuft erst, nachdem Sie ausdrücklich zugestimmt haben. Bis zu Ihrer Entscheidung wird es nicht geladen und sendet keine Daten.',
        'Wenn Sie ablehnen, funktioniert BudgetSpace genau gleich — Sie verlieren nichts. Sie können Ihre Wahl jederzeit über „Datenschutz-Einstellungen" im Fußbereich ändern.',
        'Bei Zustimmung kann Google Analytics Online-Kennungen, Geräte-/Browser-Informationen, den ungefähren Standort und Nutzungsereignisse verarbeiten. Wir nutzen es, um zu verstehen, wie die App genutzt wird, wo Nutzer hängen bleiben und welche Funktionen nützlich sind.',
        'Cookies, die dann gesetzt werden: _ga und _ga_<id> (Mess-Kennungen). Sie werden erst nach Zustimmung gesetzt; widerrufen Sie die Einwilligung, entfernen wir sie, soweit technisch möglich.',
        'Wir nutzen kein Google Ads, kein Remarketing und keine Werbepersonalisierung. Wir behaupten nicht, dass die Daten von Google Analytics vollständig anonym sind.',
      ] },
      { heading: 'Cookies und lokaler Speicher', body: [
        'Notwendig: bs_auth — das Anmelde-Cookie (bis zu 30 Tage); bs_oauth — ein kurzlebiges (10 Min.) Sicherheits-Cookie während der Anmeldung.',
        'Notwendig (lokaler Speicher): bs-session-id (eine Browser-Kennung für KI-Nutzungslimits und die Zuordnung Ihrer Pläne), budgetspace.market / budgetspace.lang (Land und Sprache), budgetspace.plannerDraft / budgetspace.moveInDraft (Eingabe-Entwürfe) und budgetspace.consent (Ihre Analytics-Entscheidung).',
        'Optional: Google-Analytics-Cookies — nur nachdem Sie sie akzeptiert haben.',
        'Wir verwenden keine Werbe- oder Remarketing-Cookies.',
      ] },
      { heading: 'Konto und Google-Anmeldung', body: [
        'Die Anmeldung erfolgt über Google (OAuth). Von Google erhalten und speichern wir Ihren Namen, Ihre E-Mail, Ihr Profilbild und Ihre Google-ID; wir speichern weder Ihr Google-Passwort noch Google-Tokens.',
        'Profildaten (Name, Bild) werden bei jeder Google-Anmeldung aktualisiert.',
      ] },
      { heading: 'Eingaben, gespeicherte und geteilte Pläne', body: [
        'Wenn Sie einen Plan speichern, werden Ihre Textbeschreibung (Eingabe) und die eingegebene Stadt mitgespeichert. Geben Sie keine sensiblen personenbezogenen Daten in die Beschreibung ein.',
        'Ein gespeicherter Plan hat einen privaten, schwer zu erratenden Link. Wer diesen Link erhält, kann den Plan öffnen und Produkte, Budget, Raum und den mitgespeicherten Text (Ihre Beschreibung und Stadt) sehen — aber nicht Ihren Namen oder Ihre E-Mail. Ein Link ist nicht „privat", nur weil er schwer zu erraten ist.',
        'Die Freigabe wird durch Löschen des Plans in der App widerrufen — nur so wird der Link unbrauchbar. Gespeicherte Pläne haben kein automatisches Ablaufdatum.',
      ] },
      { heading: 'KI-Assistent', body: [
        'BudgetSpace kann KI nutzen, um Ihre Freitext-Anfrage auszuwerten. Ist die KI nicht verfügbar oder deaktiviert, wird eine deterministische (regelbasierte) Ersatzlogik verwendet — nicht jeder Plan wird per KI erstellt.',
        'Wenn genutzt, ist der Anbieter in der Produktion Google Gemini. Wir senden Ihre Textbeschreibung plus Kontext (Raum, Budget, Stil) — wir senden nicht Ihren Namen, Ihre E-Mail oder Konto-ID.',
        'BudgetSpace speichert Ihre Eingabe oder die KI-Antwort nicht über das hinaus, was Sie selbst in einem Plan speichern. Google verarbeitet den Text als Auftragsverarbeiter gemäß seinen Bedingungen und dem Auftragsverarbeitungsvertrag.',
        'KI-Vorschläge sind Schätzungen — prüfen Sie Preis, Maße und Verfügbarkeit vor dem Kauf beim Händler. Geben Sie keine sensiblen personenbezogenen Daten ein.',
      ] },
      { heading: 'IP-Adressen, Sicherheit und Missbrauchsvermeidung', body: [
        'Wir verarbeiten Ihre IP-Adresse vorübergehend zur Ratenbegrenzung und Sicherheit; sie wird im Serverspeicher gehalten und nicht in Ihrem Profil gespeichert.',
        'Wenn der KI-Assistent aktiviert ist, kann die IP-Adresse eines Gastes bis zu 45 Tage als Schlüssel für das tägliche Nutzungslimit aufbewahrt werden (angemeldete Nutzer verwenden stattdessen einen Konto-Schlüssel).',
        'Unser Server und der Hosting-Anbieter können IP-Adressen in Sicherheits-/Technikprotokollen gemäß ihren Standardeinstellungen erfassen.',
      ] },
      { heading: 'Fehlerüberwachung (Sentry)', body: [
        'Wir senden technische Fehler an Sentry, um Störungen zu beheben. Es werden nur Fehlermeldungen und Stack-Traces gesendet; das Senden personenbezogener Daten ist deaktiviert (sendDefaultPii=false), sodass der Inhalt Ihrer Eingaben, Ihr Name und Ihre E-Mail nicht gesendet werden.',
      ] },
      { heading: 'Hosting, Datenbank und Empfänger', body: [
        'Die App wird bei einem Cloud-Anbieter (z. B. Railway/Render/Fly) mit einer PostgreSQL-Datenbank gehostet.',
        'Google — Anmeldung (Google Sign-In) und, wenn die KI aktiviert ist, Verarbeitung der Anfrage (Google Gemini). Sentry — Fehlerüberwachung.',
        'Stripe — Abrechnung der Design Session; derzeit inaktiv (kostenlose Beta), es werden daher aktuell keine Zahlungsdaten verarbeitet.',
        'Wir verkaufen Ihre Daten nicht. eBay ist kein Empfänger Ihrer Daten — wir rufen öffentliche Angebote nach Kategorie und Markt ab und senden eBay keine personenbezogenen Daten.',
      ] },
      { heading: 'Internationale Übermittlungen', body: [
        'Einige Anbieter (Google, Sentry, Stripe) können Daten außerhalb der EU verarbeiten, z. B. in den USA. Die Übermittlung stützt sich auf Standardvertragsklauseln (SCC) und/oder das EU-US Data Privacy Framework. Eine Kopie der Garantien erhalten Sie auf Anfrage.',
      ] },
      { heading: 'Wie lange wir Daten speichern', body: [
        'Konto und gespeicherte Pläne: bis Sie sie löschen (kein automatisches Ablaufdatum).',
        'Anmeldesitzungen: bis zu 30 Tage (7 Tage Inaktivität), dann gelöscht.',
        'KI-Nutzungs-Metadaten (einschließlich des temporären IP-Schlüssels eines Gastes): 45 Tage.',
        'Pseudonyme Klicks/Feedback und Warteliste-E-Mails: 18 Monate.',
        'Ratenbegrenzungsdaten: vorübergehend im Speicher (Minuten).',
        'Fehlerprotokolle (Sentry) und Google Analytics: gemäß der bei diesen Anbietern eingestellten Aufbewahrung (für Analytics wählen wir die kürzeste angebotene Option). Serverprotokolle und Backups: gemäß den Standardeinstellungen des Hosting-Anbieters.',
      ] },
      { heading: 'Ihre Rechte und wie Sie sie ausüben', body: [
        'Sie haben das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung, Widerspruch und Datenübertragbarkeit sowie das Recht, die Einwilligung zu widerrufen (z. B. für Analytics über „Datenschutz-Einstellungen").',
        'Sie können Ihr Konto und alle zugehörigen Daten sofort in der App löschen („Konto löschen"): gelöscht werden Ihr Konto, gespeicherte Pläne, KI-Nutzungsdaten sowie die mit Ihrer E-Mail verknüpften Warteliste-Einträge. Ein vorübergehendes technisches Protokoll kann kurz einen Eintrag behalten; Stripe (falls je eingeführt) und Sentry speichern Daten gemäß ihren eigenen Bedingungen.',
        'Wenn Sie nicht auf Ihr Konto zugreifen können oder andere Rechte ausüben möchten, kontaktieren Sie uns unter ' + OPERATOR.email + '.',
        'Sie haben das Recht auf Beschwerde bei einer Aufsichtsbehörde — in Österreich die Datenschutzbehörde (DSB, dsb.gv.at), in Deutschland die zuständige Landesbehörde; in Kroatien AZOP.',
      ] },
      { heading: 'Änderungen und Kontakt', body: [
        'Wir können diese Erklärung gelegentlich aktualisieren; das Datum der letzten Änderung steht oben. Bei Fragen zum Datenschutz schreiben Sie an ' + OPERATOR.email + '.',
      ] },
    ],
  },
  terms: {
    title: 'Nutzungsbedingungen',
    updated: 'Zuletzt aktualisiert: 03.07.2026',
    sections: [
      { heading: 'Was der Dienst ist', body: [
        'BudgetSpace hilft, die Einrichtung eines Raums innerhalb eines Budgets zu planen. Wir sind kein Geschäft und verkaufen keine Produkte.',
        'Preise und Verfügbarkeit sind Schätzungen von Händlern und können veraltet sein — prüfen Sie sie vor dem Kauf beim Händler.',
      ] },
      { heading: 'Marken und Unabhängigkeit', body: [
        'IKEA, JYSK, eBay und alle anderen Händler- und Produktnamen sind Marken ihrer jeweiligen Inhaber. Wir verwenden sie nur, um die echten Produkte und die Händler zu benennen, die sie verkaufen.',
        'BudgetSpace ist ein unabhängiges Planungswerkzeug und steht in keiner Verbindung zu diesen Händlern und wird von ihnen weder unterstützt noch gesponsert. Affiliate-Links, falls hinzugefügt, werden klar gekennzeichnet und ändern nie, welches Produkt für Sie das beste ist.',
      ] },
      { heading: 'KI-Assistent', body: [
        'Die Vorschläge des KI-Assistenten sind Schätzungen zur Planungshilfe, keine fachliche Beratung. Prüfen Sie Preise, Maße und Verfügbarkeit vor dem Kauf beim Händler.',
      ] },
      { heading: 'Konto', body: [
        'Für das Speichern und den KI-Assistenten melden Sie sich mit Google an; Sie sind für die Sicherheit Ihres Kontos verantwortlich.',
      ] },
      { heading: 'Design Session', body: [
        'Der Dienst ist derzeit vollständig kostenlos (frühe Beta) — alle Funktionen sind freigeschaltet. Es gibt kein Abonnement und keine wiederkehrende Abrechnung. Künftig planen wir eine einmalige Gebühr pro „Design Session" (eine Zahlung, keine Verlängerung); Preis und Bedingungen werden vor jeder Zahlung klar angezeigt.',
      ] },
      { heading: 'Zulässige Nutzung', body: [
        'Missbrauchen Sie den Dienst nicht — keine automatisierte Überlastung und keine Versuche, Limits zu umgehen.',
      ] },
      { heading: 'Keine Gewährleistung / Haftung', body: [
        'Der Dienst wird „wie besehen" bereitgestellt. Wir garantieren weder die Preisgenauigkeit noch die Verfügbarkeit und haften nicht für Kaufentscheidungen auf Grundlage unserer Vorschläge.',
      ] },
      { heading: 'Änderungen und anwendbares Recht', body: [
        'Wir können diese Bedingungen gelegentlich ändern. Es gilt das Recht der Republik Kroatien und der EU.',
      ] },
    ],
  },
  impressum: {
    title: 'Impressum',
    updated: LAST_UPDATED_DE,
    disclaimer: 'BudgetSpace wird von einer Privatperson (natürlichen Person) als kostenloses Projekt (Beta) betrieben. Es handelt sich nicht um ein eingetragenes Unternehmen; es gibt daher keine öffentliche Handelsregister- oder USt-IdNr. Vollständige Unternehmensangaben werden hier ergänzt, sobald eine Bezahlfunktion eingeführt wird.',
    sections: [
      { heading: 'Diensteanbieter', body: [
        OPERATOR.name,
        OPERATOR.street,
        `${OPERATOR.postalCity}, Kroatien`,
        `E-Mail: ${OPERATOR.email}`,
      ] },
    ],
  },
};

const IT: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Informativa sulla privacy",
    updated: "Ultimo aggiornamento: 2026-07-14",
    sections: [
      { heading: "Chi gestisce BudgetSpace", body: [
          `BudgetSpace è un progetto gratuito (beta) gestito da ${OPERATOR.name} in qualità di individuo (persona fisica), titolare del trattamento dei tuoi dati personali.`,
          `Indirizzo: ${OPERATOR.street}, ${OPERATOR.postalCity}, Croazia. Contatto: ${OPERATOR.email}.`,
        ] },
      { heading: "Quali dati trattiamo e perché", body: [
          "Accesso con Google: il tuo nome, l'indirizzo email, l'immagine del profilo e l'id Google (sub) — affinché tu abbia un account e i tuoi piani salvati siano tuoi.",
          "I piani e i dati che salvi: stanza, budget, stile, il testo che digiti (la tua descrizione) e la città — per costruire e perfezionare il piano.",
          "Un Paese approssimativo ricavato dall'intestazione della CDN — solo per selezionare il mercato e la valuta corretti.",
          "Il tuo indirizzo IP — temporaneamente, per la limitazione della frequenza (rate limiting) e la sicurezza (vedi \"Indirizzi IP e sicurezza\").",
          "Segnali d'uso pseudonimi: quale prodotto hai cliccato e il feedback sul piano — collegati a un piano, non al tuo nome.",
          "Google Analytics (solo se lo accetti) — per capire come viene utilizzata l'app (vedi la sezione dedicata).",
          "Il testo che digiti, quando l'IA è abilitata, viene inviato al fornitore di IA per interpretare la tua richiesta (vedi \"Assistente IA\").",
          "Un'email per la lista d'attesa — solo se scegli di lasciarne una.",
        ] },
      { heading: "Base giuridica (Art. 6 GDPR)", body: [
          "Contratto (Art. 6(1)(b)): gestire il tuo account, salvare i piani ed elaborare la tua richiesta per tuo conto.",
          "Legittimi interessi (Art. 6(1)(f)): sicurezza e prevenzione degli abusi, monitoraggio degli errori tecnici e analisi pseudonime dei clic/feedback per migliorare il servizio.",
          "Consenso (Art. 6(1)(a)): Google Analytics. Puoi revocare il consenso in qualsiasi momento.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics è facoltativo e viene eseguito solo dopo che lo hai esplicitamente accettato. Finché non decidi, non viene caricato e non invia alcun dato.",
          "Se rifiuti, BudgetSpace funziona esattamente allo stesso modo — non perdi nulla. Puoi modificare la tua scelta in qualsiasi momento tramite \"Impostazioni sulla privacy\" nel piè di pagina.",
          "Se accettato, Google Analytics può trattare identificatori online, informazioni sul dispositivo/browser, la posizione approssimativa e gli eventi d'uso. Lo utilizziamo per capire come viene usata l'app, dove gli utenti incontrano difficoltà e quali funzionalità sono utili.",
          "Cookie che imposta di conseguenza: _ga e _ga_<id> (identificatori di misurazione). Vengono impostati solo dopo l'accettazione; se revochi il consenso li rimuoviamo per quanto tecnicamente possibile.",
          "Non utilizziamo Google Ads, il remarketing o la personalizzazione degli annunci. Non sosteniamo che i dati di Google Analytics siano del tutto anonimi.",
        ] },
      { heading: "Cookie e archiviazione locale", body: [
          "Necessari: bs_auth — il cookie di accesso (fino a 30 giorni); bs_oauth — un cookie di sicurezza di breve durata (10 min) durante l'accesso.",
          "Necessari (archiviazione locale): bs-session-id (un id per browser per i limiti d'uso dell'IA e per collegare i tuoi piani), budgetspace.market / budgetspace.lang (Paese e lingua), budgetspace.plannerDraft / budgetspace.moveInDraft (bozze dei dati inseriti) e budgetspace.consent (la tua scelta sull'analitica).",
          "Facoltativi: i cookie di Google Analytics — solo dopo che li hai accettati.",
          "Non utilizziamo cookie pubblicitari o di remarketing.",
        ] },
      { heading: "Account e accesso con Google", body: [
          "L'accesso avviene tramite Google (OAuth). Da Google riceviamo e memorizziamo il tuo nome, l'indirizzo email, l'immagine del profilo e l'id Google; non memorizziamo la tua password Google né i token Google.",
          "I dati del profilo (nome, immagine) vengono aggiornati a ogni accesso con Google.",
        ] },
      { heading: "Dati inseriti, piani salvati e condivisi", body: [
          "Quando salvi un piano, la tua descrizione testuale (prompt) e la città che hai inserito vengono salvate insieme ad esso. Non inserire informazioni personali sensibili nella descrizione.",
          "Un piano salvato ha un link privato e difficile da indovinare. Chiunque riceva quel link può aprire il piano e vedere i prodotti, il budget, la stanza e il testo salvato con esso (la tua descrizione e la città) — ma non il tuo nome o la tua email. Un link non è \"privato\" solo perché è difficile da indovinare.",
          "La condivisione viene revocata eliminando il piano nell'app — è l'unico modo per far smettere di funzionare il link. I piani salvati non hanno una scadenza automatica.",
        ] },
      { heading: "Assistente IA", body: [
          "BudgetSpace può utilizzare l'IA per interpretare la tua richiesta in testo libero. Quando l'IA non è disponibile o è disabilitata, viene utilizzato un meccanismo di ripiego deterministico (basato su regole) — non tutti i piani sono generati dall'IA.",
          "Quando viene utilizzato, il fornitore in produzione è Google Gemini. Inviamo la tua descrizione testuale più il contesto (stanza, budget, stile) — non inviamo il tuo nome, l'email o l'id dell'account.",
          "BudgetSpace non memorizza il tuo prompt né la risposta dell'IA oltre a ciò che scegli di salvare in un piano. Google, in qualità di responsabile del trattamento, gestisce il testo secondo i propri termini e il proprio accordo sul trattamento dei dati.",
          "I suggerimenti dell'IA sono stime — verifica il prezzo, le dimensioni e la disponibilità con il rivenditore prima di acquistare. Non inserire informazioni personali sensibili.",
        ] },
      { heading: "Indirizzi IP, sicurezza e prevenzione degli abusi", body: [
          "Trattiamo il tuo indirizzo IP temporaneamente per la limitazione della frequenza (rate limiting) e la sicurezza; viene conservato nella memoria del server e non viene memorizzato nel tuo profilo.",
          "Quando l'assistente IA è abilitato, l'IP di un dispositivo ospite può essere conservato fino a 45 giorni come chiave per il limite d'uso giornaliero (gli utenti che hanno effettuato l'accesso usano invece una chiave dell'account).",
          "Il nostro server e il nostro fornitore di hosting possono registrare gli indirizzi IP nei log di sicurezza/tecnici secondo le loro impostazioni predefinite.",
        ] },
      { heading: "Monitoraggio degli errori (Sentry)", body: [
          "Inviamo gli errori tecnici a Sentry per correggere i guasti. Vengono inviati solo i messaggi di errore e le tracce dello stack; l'invio di dati personali è disabilitato (sendDefaultPii=false), quindi il contenuto dei tuoi prompt, il tuo nome e la tua email non vengono inviati.",
        ] },
      { heading: "Hosting, database e destinatari", body: [
          "L'app è ospitata presso un fornitore cloud (ad es. Railway/Render/Fly) con un database PostgreSQL.",
          "Google — accesso (Google Sign-In) e, quando l'IA è abilitata, elaborazione della richiesta (Google Gemini). Sentry — monitoraggio degli errori.",
          "Stripe — fatturazione della Design Session; attualmente inattivo (beta gratuita), quindi oggi non viene trattato alcun dato di pagamento.",
          "Non vendiamo i tuoi dati. eBay non è un destinatario dei tuoi dati — recuperiamo gli annunci pubblici per categoria e mercato e non inviamo a eBay alcun tuo dato personale.",
        ] },
      { heading: "Trasferimenti internazionali", body: [
          "Alcuni fornitori (Google, Sentry, Stripe) possono trattare i dati al di fuori dell'UE, ad es. negli Stati Uniti. I trasferimenti si basano sulle Clausole Contrattuali Standard (SCCs) e/o sull'EU-US Data Privacy Framework. Contattaci per una copia delle relative garanzie.",
        ] },
      { heading: "Per quanto tempo conserviamo i dati", body: [
          "Account e piani salvati: finché non li elimini (nessuna scadenza automatica).",
          "Sessioni di accesso: fino a 30 giorni (7 giorni di inattività), poi eliminate.",
          "Metadati d'uso dell'IA (inclusa la chiave IP temporanea di un ospite): 45 giorni.",
          "Clic/feedback pseudonimi ed email della lista d'attesa: 18 mesi.",
          "Dati di rate limiting: transitori, in memoria (minuti).",
          "Log degli errori (Sentry) e Google Analytics: secondo la conservazione impostata con tali fornitori (per Analytics impostiamo l'opzione più breve offerta). Log del server e backup: secondo le impostazioni predefinite del fornitore di hosting.",
        ] },
      { heading: "I tuoi diritti e come esercitarli", body: [
          "Hai il diritto di accesso, rettifica, cancellazione, limitazione, opposizione e portabilità, nonché il diritto di revocare il consenso (ad es. per Analytics tramite \"Impostazioni sulla privacy\").",
          "Puoi eliminare il tuo account e tutti i dati correlati immediatamente nell'app (\"Elimina account\"): questo cancella il tuo account, i piani salvati, i registri d'uso dell'IA e le voci della lista d'attesa collegate alla tua email. Un log tecnico transitorio può conservare brevemente una voce; Stripe (se mai introdotto) e Sentry conservano i dati secondo i propri termini.",
          `Se non riesci ad accedere al tuo account o desideri esercitare altri diritti, contattaci all'indirizzo ${OPERATOR.email}.`,
          "Hai il diritto di presentare un reclamo a un'autorità di controllo — in Croazia, l'Agenzia per la protezione dei dati personali (AZOP, azop.hr); altrimenti la tua autorità nazionale per la protezione dei dati.",
        ] },
      { heading: "Modifiche e contatti", body: [
          `Potremmo aggiornare questa informativa di tanto in tanto; la data dell'ultimo aggiornamento è riportata in alto. Per qualsiasi domanda sulla privacy, scrivi a ${OPERATOR.email}.`,
          "Questa è una traduzione fornita per tua comodità. In caso di discrepanza tra le versioni linguistiche, prevale la versione inglese di questo documento.",
        ] },
    ],
  },
  terms: {
    title: "Termini di utilizzo",
    updated: "Ultimo aggiornamento: 2026-07-03",
    sections: [
      { heading: "Cos'è il servizio", body: [
          "BudgetSpace ti aiuta a pianificare l'arredamento di una stanza rispettando un budget. Non siamo un negozio e non vendiamo prodotti.",
          "I prezzi e la disponibilità sono stime raccolte dai rivenditori e potrebbero non essere aggiornati — verifica sempre con il rivenditore prima di acquistare.",
        ] },
      { heading: "Marchi e indipendenza", body: [
          "IKEA, JYSK, eBay e tutti gli altri nomi di rivenditori e prodotti sono marchi dei rispettivi proprietari. Li utilizziamo solo per identificare i prodotti reali e i negozi che li vendono.",
          "BudgetSpace è uno strumento di pianificazione indipendente e non è affiliato, approvato o sponsorizzato da nessuno di questi rivenditori. I link di affiliazione, se aggiunti, saranno chiaramente etichettati e non modificheranno mai quale prodotto sia il migliore per te.",
        ] },
      { heading: "Assistente IA", body: [
          "I suggerimenti dell'assistente IA sono stime per aiutarti a pianificare, non una consulenza professionale. Verifica prezzi, dimensioni e disponibilità con il rivenditore prima di acquistare.",
        ] },
      { heading: "Account", body: [
          "Il salvataggio e l'assistente IA utilizzano l'accesso con Google; sei responsabile della sicurezza del tuo account.",
        ] },
      { heading: "Design Session", body: [
          "Il servizio è attualmente del tutto gratuito (beta iniziale) — tutte le funzionalità sono sbloccate. Non c'è alcun abbonamento né alcuna fatturazione ricorrente. In futuro prevediamo un addebito una tantum per \"Design Session\" (un singolo pagamento, senza rinnovo); il prezzo e i termini saranno mostrati chiaramente prima di qualsiasi addebito.",
        ] },
      { heading: "Uso accettabile", body: [
          "Non abusare del servizio — nessun sovraccarico automatizzato né tentativi di aggirare i limiti.",
        ] },
      { heading: "Nessuna garanzia / responsabilità", body: [
          "Il servizio è fornito \"così com'è\". Non garantiamo l'accuratezza dei prezzi o la disponibilità e non siamo responsabili per le decisioni di acquisto prese sulla base dei nostri suggerimenti.",
        ] },
      { heading: "Modifiche e legge applicabile", body: [
          "Potremmo aggiornare questi termini di tanto in tanto. Si applicano le leggi della Croazia e dell'UE.",
          "Questa è una traduzione fornita per tua comodità. In caso di discrepanza tra le versioni linguistiche, prevale la versione inglese di questo documento.",
        ] },
    ],
  },
};

const SL: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Politika zasebnosti",
    updated: "Zadnja posodobitev: 2026-07-14",
    sections: [
      { heading: "Kdo upravlja BudgetSpace", body: [
          `BudgetSpace je brezplačen projekt (beta), ki ga vodi ${OPERATOR.name} kot posameznik (fizična oseba), upravljavec vaših osebnih podatkov.`,
          `Naslov: ${OPERATOR.street}, ${OPERATOR.postalCity}, Hrvaška. Kontakt: ${OPERATOR.email}.`,
        ] },
      { heading: "Kaj obdelujemo in zakaj", body: [
          "Prijava z Google: vaše ime, e-poštni naslov, profilna slika in Google id (sub) — da imate račun in da so shranjeni načrti vaši.",
          "Načrti in vnosi, ki jih shranite: soba, proračun, slog, besedilo, ki ga vnesete (vaš opis), in mesto — za izdelavo in izpopolnitev načrta.",
          "Približna država iz glave CDN — samo za izbiro pravega trga in valute.",
          "Vaš naslov IP — začasno, za omejevanje pogostosti zahtev in varnost (glejte »Naslovi IP in varnost«).",
          "Psevdonimni signali uporabe: kateri izdelek ste kliknili in povratne informacije o načrtu — vezani na načrt, ne na vaše ime.",
          "Google Analytics (samo če sprejmete) — za razumevanje, kako se aplikacija uporablja (glejte poseben razdelek).",
          "Besedilo, ki ga vnesete, se, kadar je umetna inteligenca omogočena, pošlje ponudniku umetne inteligence za razlago vaše zahteve (glejte »Pomočnik z umetno inteligenco«).",
          "E-poštni naslov za čakalno listo — samo če se odločite, da ga pustite.",
        ] },
      { heading: "Pravna podlaga (Art. 6 GDPR)", body: [
          "Pogodba (Art. 6(1)(b)): vodenje vašega računa, shranjevanje načrtov in obdelava vaše zahteve v vašem imenu.",
          "Zakoniti interesi (Art. 6(1)(f)): varnost in preprečevanje zlorab, spremljanje tehničnih napak ter psevdonimna analitika klikov/povratnih informacij za izboljšanje storitve.",
          "Privolitev (Art. 6(1)(a)): Google Analytics. Privolitev lahko kadar koli prekličete.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics je neobvezen in se zažene šele po tem, ko ga izrecno sprejmete. Dokler se ne odločite, se ne naloži in ne pošilja nobenih podatkov.",
          "Če zavrnete, BudgetSpace deluje popolnoma enako — ničesar ne izgubite. Svojo izbiro lahko kadar koli spremenite prek »Nastavitve zasebnosti« v nogi strani.",
          "Ko je sprejet, lahko Google Analytics obdeluje spletne identifikatorje, informacije o napravi/brskalniku, približno lokacijo in dogodke uporabe. Uporabljamo ga za razumevanje, kako se aplikacija uporablja, kje se uporabniki zataknejo in katere funkcije so uporabne.",
          "Piškotki, ki jih nato nastavi: _ga in _ga_<id> (identifikatorji merjenja). Nastavijo se šele po sprejemu; če prekličete privolitev, jih odstranimo, kolikor je to tehnično mogoče.",
          "Ne uporabljamo Google Ads, ponovnega trženja ali personalizacije oglasov. Ne trdimo, da so podatki Google Analytics popolnoma anonimni.",
        ] },
      { heading: "Piškotki in lokalno shranjevanje", body: [
          "Nujni: bs_auth — piškotek za prijavo (do 30 dni); bs_oauth — kratkotrajni (10 min) varnostni piškotek med prijavo.",
          "Nujni (lokalno shranjevanje): bs-session-id (id za posamezni brskalnik za omejitve uporabe umetne inteligence in povezovanje vaših načrtov), budgetspace.market / budgetspace.lang (država in jezik), budgetspace.plannerDraft / budgetspace.moveInDraft (osnutki vnosov) in budgetspace.consent (vaša izbira glede analitike).",
          "Neobvezni: piškotki Google Analytics — samo po tem, ko jih sprejmete.",
          "Ne uporabljamo oglaševalskih piškotkov ali piškotkov za ponovno trženje.",
        ] },
      { heading: "Račun in prijava z Google", body: [
          "Prijava poteka prek Google (OAuth). Od Google prejmemo in shranimo vaše ime, e-poštni naslov, profilno sliko in Google id; ne shranjujemo vašega gesla Google ali žetonov Google.",
          "Podatki profila (ime, slika) se osvežijo ob vsaki prijavi z Google.",
        ] },
      { heading: "Vnosi, shranjeni in deljeni načrti", body: [
          "Ko shranite načrt, se z njim shranita vaš besedilni opis (poziv) in mesto, ki ste ga vnesli. V opis ne vnašajte občutljivih osebnih podatkov.",
          "Shranjeni načrt ima zasebno povezavo, ki jo je težko uganiti. Vsak, ki prejme to povezavo, lahko odpre načrt in vidi izdelke, proračun, sobo in besedilo, shranjeno z njim (vaš opis in mesto) — ne pa vašega imena ali e-poštnega naslova. Povezava ni »zasebna« zgolj zato, ker jo je težko uganiti.",
          "Deljenje se prekliče z brisanjem načrta v aplikaciji — to je edini način, da povezava preneha delovati. Shranjeni načrti nimajo samodejnega poteka veljavnosti.",
        ] },
      { heading: "Pomočnik z umetno inteligenco", body: [
          "BudgetSpace lahko uporablja umetno inteligenco za razlago vaše zahteve v prostem besedilu. Kadar umetna inteligenca ni na voljo ali je onemogočena, se uporabi deterministična (na pravilih temelječa) nadomestna rešitev — ni vsak načrt ustvarjen z umetno inteligenco.",
          "Ko se uporablja, je produkcijski ponudnik Google Gemini. Pošljemo vaš besedilni opis in kontekst (soba, proračun, slog) — ne pošljemo vašega imena, e-poštnega naslova ali id računa.",
          "BudgetSpace ne shranjuje vašega poziva ali odgovora umetne inteligence dlje, kot izberete za shranitev v načrtu. Google kot obdelovalec obravnava besedilo v skladu s svojimi pogoji in pogodbo o obdelavi podatkov.",
          "Predlogi umetne inteligence so ocene — pred nakupom preverite ceno, dimenzije in razpoložljivost pri trgovcu. Ne vnašajte občutljivih osebnih podatkov.",
        ] },
      { heading: "Naslovi IP, varnost in preprečevanje zlorab", body: [
          "Vaš naslov IP obdelujemo začasno za omejevanje pogostosti zahtev in varnost; hrani se v pomnilniku strežnika in se ne shranjuje v vašem profilu.",
          "Ko je pomočnik z umetno inteligenco omogočen, se lahko naslov IP naprave gosta hrani do 45 dni kot ključ za dnevno omejitev uporabe (prijavljeni uporabniki namesto tega uporabljajo ključ računa).",
          "Naš strežnik in ponudnik gostovanja lahko beležita naslove IP v varnostnih/tehničnih dnevnikih v skladu s svojimi privzetimi nastavitvami.",
        ] },
      { heading: "Spremljanje napak (Sentry)", body: [
          "Tehnične napake pošiljamo v Sentry za odpravljanje težav. Pošiljajo se samo sporočila o napakah in sledi sklada; pošiljanje osebnih podatkov je onemogočeno (sendDefaultPii=false), zato se vsebina vaših pozivov, vaše ime in vaš e-poštni naslov ne pošiljajo.",
        ] },
      { heading: "Gostovanje, baza podatkov in prejemniki", body: [
          "Aplikacija gostuje pri ponudniku v oblaku (npr. Railway/Render/Fly) z bazo podatkov PostgreSQL.",
          "Google — prijava (Google Sign-In) in, kadar je umetna inteligenca omogočena, obdelava zahtev (Google Gemini). Sentry — spremljanje napak.",
          "Stripe — obračunavanje za Design Session; trenutno neaktivno (brezplačna beta), zato se danes ne obdelujejo nobeni plačilni podatki.",
          "Vaših podatkov ne prodajamo. eBay ni prejemnik vaših podatkov — javne oglase pridobivamo po kategoriji in trgu ter eBayu ne pošiljamo nobenih vaših osebnih podatkov.",
        ] },
      { heading: "Mednarodni prenosi", body: [
          "Nekateri ponudniki (Google, Sentry, Stripe) lahko obdelujejo podatke zunaj EU, npr. v ZDA. Prenosi temeljijo na Standard Contractual Clauses (SCCs) in/ali EU-US Data Privacy Framework. Za kopijo ustreznih zaščitnih ukrepov nas kontaktirajte.",
        ] },
      { heading: "Kako dolgo hranimo podatke", body: [
          "Račun in shranjeni načrti: dokler jih ne izbrišete (brez samodejnega poteka veljavnosti).",
          "Seje prijave: do 30 dni (7 dni nedejavnosti), nato se počistijo.",
          "Metapodatki o uporabi umetne inteligence (vključno z začasnim ključem IP gosta): 45 dni.",
          "Psevdonimni kliki/povratne informacije in e-poštni naslovi za čakalno listo: 18 mesecev.",
          "Podatki o omejevanju pogostosti zahtev: prehodni, v pomnilniku (minute).",
          "Dnevniki napak (Sentry) in Google Analytics: v skladu z obdobjem hrambe, nastavljenim pri teh ponudnikih (za Analytics nastavimo najkrajšo ponujeno možnost). Strežniški dnevniki in varnostne kopije: v skladu s privzetimi nastavitvami ponudnika gostovanja.",
        ] },
      { heading: "Vaše pravice in kako jih uveljaviti", body: [
          "Imate pravico do dostopa, popravka, izbrisa, omejitve, ugovora in prenosljivosti ter pravico do preklica privolitve (npr. za Analytics prek »Nastavitve zasebnosti«).",
          "Svoj račun in vse povezane podatke lahko takoj izbrišete v aplikaciji (»Izbriši račun«): to izbriše vaš račun, shranjene načrte, evidence o uporabi umetne inteligence in vnose na čakalno listo, vezane na vaš e-poštni naslov. Prehodni tehnični dnevnik lahko na kratko ohrani vnos; Stripe (če bo kdaj uveden) in Sentry hranita podatke v skladu s svojimi pogoji.",
          `Če ne morete dostopati do svojega računa ali želite uveljaviti druge pravice, nas kontaktirajte na ${OPERATOR.email}.`,
          "Imate pravico vložiti pritožbo pri nadzornem organu — na Hrvaškem pri Agenciji za varstvo osebnih podatkov (AZOP, azop.hr); sicer pri svojem nacionalnem organu za varstvo podatkov.",
        ] },
      { heading: "Spremembe in kontakt", body: [
          `To politiko lahko občasno posodobimo; datum zadnje posodobitve je prikazan na vrhu. Za vsa vprašanja o zasebnosti pišite na ${OPERATOR.email}.`,
          "To je prevod, ki je na voljo zaradi vaše lažje uporabe. V primeru kakršnega koli neskladja med jezikovnimi različicami prevlada angleška različica tega dokumenta.",
        ] },
    ],
  },
  terms: {
    title: "Pogoji uporabe",
    updated: "Zadnja posodobitev: 2026-07-03",
    sections: [
      { heading: "Kaj je storitev", body: [
          "BudgetSpace vam pomaga načrtovati opremljanje sobe v okviru proračuna. Nismo trgovina in ne prodajamo izdelkov.",
          "Cene in razpoložljivost so ocene, zbrane od trgovcev, in so lahko zastarele — pred nakupom vedno preverite pri trgovcu.",
        ] },
      { heading: "Blagovne znamke in neodvisnost", body: [
          "IKEA, JYSK, eBay in vsa druga imena trgovcev in izdelkov so blagovne znamke njihovih lastnikov. Uporabljamo jih samo za identifikacijo resničnih izdelkov in trgovin, ki jih prodajajo.",
          "BudgetSpace je neodvisno orodje za načrtovanje in ni povezan s katerim koli od teh trgovcev, jih ne podpira in ga ti ne sponzorirajo. Partnerske povezave, če bodo dodane, bodo jasno označene in nikoli ne bodo spremenile, kateri izdelek je najboljši za vas.",
        ] },
      { heading: "Pomočnik z umetno inteligenco", body: [
          "Predlogi pomočnika z umetno inteligenco so ocene, ki vam pomagajo pri načrtovanju, in ne strokovni nasvet. Pred nakupom preverite cene, dimenzije in razpoložljivost pri trgovcu.",
        ] },
      { heading: "Račun", body: [
          "Shranjevanje in pomočnik z umetno inteligenco uporabljata prijavo z Google; za varnost svojega računa ste odgovorni sami.",
        ] },
      { heading: "Design Session", body: [
          "Storitev je trenutno popolnoma brezplačna (zgodnja beta) — vse funkcije so odklenjene. Ni naročnine in ni ponavljajočega se obračunavanja. V prihodnosti načrtujemo enkratno plačilo za posamezno »Design Session« (enkratno plačilo, brez obnove); cena in pogoji bodo jasno prikazani pred kakršnim koli obračunom.",
        ] },
      { heading: "Sprejemljiva uporaba", body: [
          "Ne zlorabljajte storitve — brez samodejnega preobremenjevanja ali poskusov obhoda omejitev.",
        ] },
      { heading: "Brez jamstva / odgovornost", body: [
          "Storitev je zagotovljena »takšna, kot je«. Ne jamčimo za točnost cen ali razpoložljivost in nismo odgovorni za nakupne odločitve, sprejete na podlagi naših predlogov.",
        ] },
      { heading: "Spremembe in veljavno pravo", body: [
          "Te pogoje lahko občasno posodobimo. Veljata pravo Hrvaške in EU.",
          "To je prevod, ki je na voljo zaradi vaše lažje uporabe. V primeru kakršnega koli neskladja med jezikovnimi različicami prevlada angleška različica tega dokumenta.",
        ] },
    ],
  },
};

const FI: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Tietosuojaseloste",
    updated: "Päivitetty viimeksi: 2026-07-14",
    sections: [
      { heading: "Kuka ylläpitää BudgetSpacea", body: [
          `BudgetSpace on ilmainen projekti (beta), jota ylläpitää ${OPERATOR.name} yksityishenkilönä (luonnollisena henkilönä), henkilötietojesi rekisterinpitäjänä.`,
          `Osoite: ${OPERATOR.street}, ${OPERATOR.postalCity}, Kroatia. Yhteystiedot: ${OPERATOR.email}.`,
        ] },
      { heading: "Mitä käsittelemme ja miksi", body: [
          "Google-kirjautuminen: nimesi, sähköpostiosoitteesi, profiilikuvasi ja Google-tunnisteesi (sub) — jotta sinulla on tili ja tallentamasi suunnitelmat ovat sinun.",
          "Tallentamasi suunnitelmat ja syötteet: huone, budjetti, tyyli, kirjoittamasi teksti (kuvauksesi) ja kaupunki — suunnitelman rakentamiseksi ja tarkentamiseksi.",
          "Likimääräinen maa CDN-otsakkeesta — vain oikean markkinan ja valuutan valitsemiseksi.",
          "IP-osoitteesi — väliaikaisesti pyyntömäärän rajoittamista ja tietoturvaa varten (katso \"IP-osoitteet ja tietoturva\").",
          "Pseudonymisoidut käyttösignaalit: mitä tuotetta klikkasit ja suunnitelmapalaute — sidottuna suunnitelmaan, ei nimeesi.",
          "Google Analytics (vain jos hyväksyt) — ymmärtääksemme, miten sovellusta käytetään (katso oma osionsa).",
          "Kirjoittamasi teksti lähetetään tekoälyn ollessa käytössä tekoälypalveluntarjoajalle pyyntösi tulkitsemiseksi (katso \"Tekoälyavustaja\").",
          "Odotuslistan sähköpostiosoite — vain jos päätät jättää sellaisen.",
        ] },
      { heading: "Oikeusperuste (Art. 6 GDPR)", body: [
          "Sopimus (Art. 6(1)(b)): tilisi ylläpitäminen, suunnitelmien tallentaminen ja pyyntösi käsittely puolestasi.",
          "Oikeutetut edut (Art. 6(1)(f)): tietoturva ja väärinkäytösten estäminen, teknisten virheiden valvonta sekä pseudonymisoitu klikkaus-/palauteanalytiikka palvelun parantamiseksi.",
          "Suostumus (Art. 6(1)(a)): Google Analytics. Voit peruuttaa suostumuksen milloin tahansa.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics on valinnainen ja käynnistyy vasta, kun nimenomaisesti hyväksyt sen. Kunnes teet päätöksen, sitä ei ladata eikä se lähetä tietoja.",
          "Jos kieltäydyt, BudgetSpace toimii täsmälleen samalla tavalla — et menetä mitään. Voit muuttaa valintaasi milloin tahansa alatunnisteen kohdasta \"Tietosuoja-asetukset\".",
          "Kun hyväksyt, Google Analytics voi käsitellä verkkotunnisteita, laite-/selaintietoja, likimääräistä sijaintia ja käyttötapahtumia. Käytämme sitä ymmärtääksemme, miten sovellusta käytetään, missä käyttäjät juuttuvat ja mitkä ominaisuudet ovat hyödyllisiä.",
          "Evästeet, jotka se tällöin asettaa: _ga ja _ga_<id> (mittaustunnisteet). Ne asetetaan vasta hyväksynnän jälkeen; jos peruutat suostumuksesi, poistamme ne niin pitkälti kuin teknisesti mahdollista.",
          "Emme käytä Google Adsia, uudelleenmarkkinointia tai mainosten personointia. Emme väitä, että Google Analytics -tiedot olisivat täysin anonyymejä.",
        ] },
      { heading: "Evästeet ja paikallinen tallennustila", body: [
          "Välttämättömät: bs_auth — kirjautumiseväste (enintään 30 päivää); bs_oauth — lyhytikäinen (10 min) tietoturvaeväste kirjautumisen aikana.",
          "Välttämättömät (paikallinen tallennustila): bs-session-id (selainkohtainen tunniste tekoälyn käyttörajoituksia ja suunnitelmiesi yhdistämistä varten), budgetspace.market / budgetspace.lang (maa ja kieli), budgetspace.plannerDraft / budgetspace.moveInDraft (syöteluonnokset) ja budgetspace.consent (analytiikkavalintasi).",
          "Valinnaiset: Google Analytics -evästeet — vasta kun hyväksyt ne.",
          "Emme käytä mainos- tai uudelleenmarkkinointievästeitä.",
        ] },
      { heading: "Tili ja Google-kirjautuminen", body: [
          "Kirjautuminen tapahtuu Googlen kautta (OAuth). Googlelta vastaanotamme ja tallennamme nimesi, sähköpostiosoitteesi, profiilikuvasi ja Google-tunnisteesi; emme tallenna Google-salasanaasi emmekä Google-tunnuksia (tokeneita).",
          "Profiilitiedot (nimi, kuva) päivitetään jokaisen Google-kirjautumisen yhteydessä.",
        ] },
      { heading: "Syötteet, tallennetut ja jaetut suunnitelmat", body: [
          "Kun tallennat suunnitelman, tekstikuvauksesi (kehote) ja syöttämäsi kaupunki tallennetaan sen mukana. Älä laita arkaluonteisia henkilötietoja kuvaukseen.",
          "Tallennetulla suunnitelmalla on yksityinen, vaikeasti arvattava linkki. Kuka tahansa, joka saa kyseisen linkin, voi avata suunnitelman ja nähdä tuotteet, budjetin, huoneen ja sen mukana tallennetun tekstin (kuvauksesi ja kaupungin) — mutta ei nimeäsi tai sähköpostiosoitettasi. Linkki ei ole \"yksityinen\" pelkästään siksi, että se on vaikea arvata.",
          "Jakaminen perutaan poistamalla suunnitelma sovelluksessa — se on ainoa tapa saada linkki lakkaamaan toimimasta. Tallennetuilla suunnitelmilla ei ole automaattista vanhenemista.",
        ] },
      { heading: "Tekoälyavustaja", body: [
          "BudgetSpace voi käyttää tekoälyä vapaamuotoisen pyyntösi tulkitsemiseen. Kun tekoäly ei ole käytettävissä tai se on poistettu käytöstä, käytetään deterministististä (sääntöpohjaista) varajärjestelmää — kaikkia suunnitelmia ei luoda tekoälyllä.",
          "Kun sitä käytetään, tuotantopalveluntarjoaja on Google Gemini. Lähetämme tekstikuvauksesi sekä kontekstin (huone, budjetti, tyyli) — emme lähetä nimeäsi, sähköpostiosoitettasi tai tilitunnistettasi.",
          "BudgetSpace ei tallenna kehotettasi tai tekoälyn vastausta enempää kuin mitä päätät tallentaa suunnitelmaan. Google käsittelijänä käsittelee tekstin omien ehtojensa ja tietojenkäsittelysopimuksensa mukaisesti.",
          "Tekoälyn ehdotukset ovat arvioita — tarkista hinta, mitat ja saatavuus jälleenmyyjältä ennen ostamista. Älä syötä arkaluonteisia henkilötietoja.",
        ] },
      { heading: "IP-osoitteet, tietoturva ja väärinkäytösten estäminen", body: [
          "Käsittelemme IP-osoitettasi väliaikaisesti pyyntömäärän rajoittamiseksi ja tietoturvaa varten; sitä säilytetään palvelimen muistissa eikä sitä tallenneta profiiliisi.",
          "Kun tekoälyavustaja on käytössä, vieraslaitteen IP-osoitetta voidaan säilyttää enintään 45 päivää päivittäisen käyttörajan avaimena (kirjautuneet käyttäjät käyttävät sen sijaan tiliavainta).",
          "Palvelimemme ja isännöintipalveluntarjoajamme voivat kirjata IP-osoitteita tietoturva-/teknisiin lokeihin oletusasetustensa mukaisesti.",
        ] },
      { heading: "Virheiden valvonta (Sentry)", body: [
          "Lähetämme tekniset virheet Sentryyn vikojen korjaamiseksi. Vain virheilmoitukset ja pinolistaukset lähetetään; henkilötietojen lähettäminen on poistettu käytöstä (sendDefaultPii=false), joten kehotteidesi sisältöä, nimeäsi ja sähköpostiosoitettasi ei lähetetä.",
        ] },
      { heading: "Isännöinti, tietokanta ja vastaanottajat", body: [
          "Sovellusta isännöi pilvipalveluntarjoaja (esim. Railway/Render/Fly), ja siinä on PostgreSQL-tietokanta.",
          "Google — kirjautuminen (Google Sign-In) ja, kun tekoäly on käytössä, pyyntöjen käsittely (Google Gemini). Sentry — virheiden valvonta.",
          "Stripe — Design Session -laskutus; tällä hetkellä ei käytössä (ilmainen beta), joten maksutietoja ei käsitellä tällä hetkellä.",
          "Emme myy tietojasi. eBay ei ole tietojesi vastaanottaja — haemme julkisia ilmoituksia kategorian ja markkinan mukaan emmekä lähetä eBaylle mitään henkilötietojasi.",
        ] },
      { heading: "Kansainväliset tiedonsiirrot", body: [
          "Jotkin palveluntarjoajat (Google, Sentry, Stripe) voivat käsitellä tietoja EU:n ulkopuolella, esim. Yhdysvalloissa. Siirrot perustuvat sopimuksen vakiolausekkeisiin (Standard Contractual Clauses, SCCs) ja/tai EU-US Data Privacy Framework -kehykseen. Ota meihin yhteyttä saadaksesi kopion asianmukaisista suojatoimista.",
        ] },
      { heading: "Kuinka kauan säilytämme tietoja", body: [
          "Tili ja tallennetut suunnitelmat: kunnes poistat ne (ei automaattista vanhenemista).",
          "Kirjautumisistunnot: enintään 30 päivää (7 päivän käyttämättömyys), sitten poistetaan.",
          "Tekoälyn käytön metatiedot (mukaan lukien vieraan väliaikainen IP-avain): 45 päivää.",
          "Pseudonymisoidut klikkaukset/palautteet ja odotuslistan sähköpostiosoitteet: 18 kuukautta.",
          "Pyyntömäärän rajoitustiedot: väliaikaisia, muistissa (minuutteja).",
          "Virhelokit (Sentry) ja Google Analytics: kyseisten palveluntarjoajien kanssa asetetun säilytysajan mukaisesti (Analyticsin osalta asetamme lyhyimmän tarjotun vaihtoehdon). Palvelinlokit ja varmuuskopiot: isännöintipalveluntarjoajan oletusasetusten mukaisesti.",
        ] },
      { heading: "Oikeutesi ja miten käytät niitä", body: [
          "Sinulla on oikeus saada pääsy tietoihin, oikaista, poistaa, rajoittaa käsittelyä, vastustaa käsittelyä ja siirtää tiedot järjestelmästä toiseen, sekä oikeus peruuttaa suostumus (esim. Analyticsin osalta kohdasta \"Tietosuoja-asetukset\").",
          "Voit poistaa tilisi ja kaikki siihen liittyvät tiedot välittömästi sovelluksessa (\"Poista tili\"): tämä poistaa tilisi, tallennetut suunnitelmat, tekoälyn käyttötietueet ja sähköpostiosoitteeseesi sidotut odotuslistamerkinnät. Väliaikainen tekninen loki voi säilyttää merkinnän hetken; Stripe (jos se joskus otetaan käyttöön) ja Sentry säilyttävät tietoja omien ehtojensa mukaisesti.",
          `Jos et pääse tilillesi tai haluat käyttää muita oikeuksiasi, ota meihin yhteyttä osoitteessa ${OPERATOR.email}.`,
          "Sinulla on oikeus tehdä valitus valvontaviranomaiselle — Kroatiassa henkilötietojen suojaviranomaiselle (AZOP, azop.hr); muutoin oman maasi tietosuojaviranomaiselle.",
        ] },
      { heading: "Muutokset ja yhteystiedot", body: [
          `Voimme päivittää tätä selostetta ajoittain; viimeisin päivityspäivä näkyy yläreunassa. Tietosuojaa koskevissa kysymyksissä kirjoita osoitteeseen ${OPERATOR.email}.`,
          "Tämä on käännös, joka tarjotaan avuksesi. Jos kieliversioiden välillä on ristiriita, tämän asiakirjan englanninkielinen versio on ratkaiseva.",
        ] },
    ],
  },
  terms: {
    title: "Käyttöehdot",
    updated: "Päivitetty viimeksi: 2026-07-03",
    sections: [
      { heading: "Mikä palvelu on", body: [
          "BudgetSpace auttaa sinua suunnittelemaan huoneen kalustamista budjetin puitteissa. Emme ole kauppa emmekä myy tuotteita.",
          "Hinnat ja saatavuus ovat jälleenmyyjiltä kerättyjä arvioita ja voivat olla vanhentuneita — tarkista aina jälleenmyyjältä ennen ostamista.",
        ] },
      { heading: "Tavaramerkit ja riippumattomuus", body: [
          "IKEA, JYSK, eBay ja kaikki muut jälleenmyyjien ja tuotteiden nimet ovat omistajiensa tavaramerkkejä. Käytämme niitä vain tunnistaaksemme todelliset tuotteet ja niitä myyvät kaupat.",
          "BudgetSpace on riippumaton suunnittelutyökalu, eikä se ole sidoksissa mihinkään näistä jälleenmyyjistä eivätkä ne suosittele tai sponsoroi sitä. Kumppanuuslinkit, jos niitä lisätään, merkitään selkeästi eivätkä koskaan muuta sitä, mikä tuote on sinulle paras.",
        ] },
      { heading: "Tekoälyavustaja", body: [
          "Tekoälyavustajan ehdotukset ovat suunnittelun tueksi tarkoitettuja arvioita, eivät ammattilaisneuvontaa. Tarkista hinnat, mitat ja saatavuus jälleenmyyjältä ennen ostamista.",
        ] },
      { heading: "Tili", body: [
          "Tallentaminen ja tekoälyavustaja käyttävät Google-kirjautumista; olet vastuussa tilisi turvallisuudesta.",
        ] },
      { heading: "Design Session", body: [
          "Palvelu on tällä hetkellä täysin ilmainen (varhainen beta) — kaikki ominaisuudet ovat käytettävissä. Tilausta tai toistuvaa laskutusta ei ole. Tulevaisuudessa suunnittelemme kertaveloitusta jokaisesta \"Design Sessionista\" (yksi maksu, ei uusiutumista); hinta ja ehdot näytetään selkeästi ennen mahdollista veloitusta.",
        ] },
      { heading: "Hyväksyttävä käyttö", body: [
          "Älä käytä palvelua väärin — ei automaattista ylikuormitusta tai yrityksiä kiertää rajoituksia.",
        ] },
      { heading: "Ei takuuta / vastuuta", body: [
          "Palvelu tarjotaan \"sellaisenaan\". Emme takaa hintojen paikkansapitävyyttä tai saatavuutta emmekä ole vastuussa ehdotustemme perusteella tehdyistä ostopäätöksistä.",
        ] },
      { heading: "Muutokset ja sovellettava laki", body: [
          "Voimme päivittää näitä ehtoja ajoittain. Sovelletaan Kroatian ja EU:n lakeja.",
          "Tämä on käännös, joka tarjotaan avuksesi. Jos kieliversioiden välillä on ristiriita, tämän asiakirjan englanninkielinen versio on ratkaiseva.",
        ] },
    ],
  },
};

const FR: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Politique de confidentialité",
    updated: "Dernière mise à jour : 2026-07-14",
    sections: [
      { heading: "Qui exploite BudgetSpace", body: [
          `BudgetSpace est un projet gratuit (bêta) mené par ${OPERATOR.name} en tant que particulier (une personne physique), responsable du traitement de vos données personnelles.`,
          `Adresse : ${OPERATOR.street}, ${OPERATOR.postalCity}, Croatie. Contact : ${OPERATOR.email}.`,
        ] },
      { heading: "Ce que nous traitons et pourquoi", body: [
          "Connexion Google : votre nom, votre adresse e-mail, votre photo de profil et votre identifiant Google (sub) — afin que vous disposiez d'un compte et que vos plans enregistrés vous appartiennent.",
          "Plans et données saisies que vous enregistrez : pièce, budget, style, le texte que vous saisissez (votre description) et ville — pour élaborer et affiner le plan.",
          "Un pays approximatif issu de l'en-tête du CDN — uniquement pour choisir le bon marché et la bonne devise.",
          "Votre adresse IP — temporairement, pour la limitation de débit et la sécurité (voir « Adresses IP et sécurité »).",
          "Des signaux d'utilisation pseudonymisés : le produit sur lequel vous avez cliqué et les retours sur le plan — liés à un plan, non à votre nom.",
          "Google Analytics (uniquement si vous l'acceptez) — pour comprendre comment l'application est utilisée (voir la section dédiée).",
          "Le texte que vous saisissez, lorsque l'IA est activée, est envoyé au fournisseur d'IA afin d'interpréter votre demande (voir « Assistant IA »).",
          "Une adresse e-mail pour la liste d'attente — uniquement si vous choisissez d'en laisser une.",
        ] },
      { heading: "Base légale (Art. 6 GDPR)", body: [
          "Contrat (Art. 6(1)(b)) : la gestion de votre compte, l'enregistrement des plans et le traitement de votre demande en votre nom.",
          "Intérêts légitimes (Art. 6(1)(f)) : la sécurité et la prévention des abus, la surveillance des erreurs techniques, ainsi que les analyses pseudonymisées des clics et des retours afin d'améliorer le service.",
          "Consentement (Art. 6(1)(a)) : Google Analytics. Vous pouvez retirer votre consentement à tout moment.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics est facultatif et ne se déclenche qu'après que vous l'ayez explicitement accepté. Tant que vous n'avez pas décidé, il n'est pas chargé et n'envoie aucune donnée.",
          "Si vous refusez, BudgetSpace fonctionne exactement de la même manière — vous ne perdez rien. Vous pouvez modifier votre choix à tout moment via « Paramètres de confidentialité » dans le pied de page.",
          "Une fois accepté, Google Analytics peut traiter des identifiants en ligne, des informations relatives à l'appareil et au navigateur, une localisation approximative et des événements d'utilisation. Nous l'utilisons pour comprendre comment l'application est utilisée, où les utilisateurs rencontrent des difficultés et quelles fonctionnalités sont utiles.",
          "Les cookies qu'il place alors : _ga et _ga_<id> (identifiants de mesure). Ils ne sont placés qu'après acceptation ; si vous retirez votre consentement, nous les supprimons dans la mesure du possible techniquement.",
          "Nous n'utilisons pas Google Ads, le remarketing ni la personnalisation publicitaire. Nous ne prétendons pas que les données de Google Analytics sont totalement anonymes.",
        ] },
      { heading: "Cookies et stockage local", body: [
          "Nécessaires : bs_auth — le cookie de connexion (jusqu'à 30 jours) ; bs_oauth — un cookie de sécurité de courte durée (10 min) pendant la connexion.",
          "Nécessaires (stockage local) : bs-session-id (un identifiant propre au navigateur pour les limites d'utilisation de l'IA et le rattachement de vos plans), budgetspace.market / budgetspace.lang (pays et langue), budgetspace.plannerDraft / budgetspace.moveInDraft (brouillons de saisie) et budgetspace.consent (votre choix concernant les analyses).",
          "Facultatifs : les cookies de Google Analytics — uniquement après que vous les ayez acceptés.",
          "Nous n'utilisons aucun cookie de publicité ou de remarketing.",
        ] },
      { heading: "Compte et connexion Google", body: [
          "La connexion s'effectue via Google (OAuth). De Google, nous recevons et conservons votre nom, votre adresse e-mail, votre photo de profil et votre identifiant Google ; nous ne conservons ni votre mot de passe Google ni vos jetons Google.",
          "Les données de profil (nom, photo) sont actualisées à chaque connexion Google.",
        ] },
      { heading: "Saisies, plans enregistrés et partagés", body: [
          "Lorsque vous enregistrez un plan, votre description en texte libre (le prompt) et la ville que vous avez saisie sont enregistrées avec celui-ci. Ne saisissez pas d'informations personnelles sensibles dans la description.",
          "Un plan enregistré possède un lien privé et difficile à deviner. Toute personne qui reçoit ce lien peut ouvrir le plan et voir les produits, le budget, la pièce et le texte enregistré avec lui (votre description et votre ville) — mais ni votre nom ni votre adresse e-mail. Un lien n'est pas « privé » du simple fait qu'il est difficile à deviner.",
          "Le partage est révoqué en supprimant le plan dans l'application — c'est le seul moyen de rendre le lien inopérant. Les plans enregistrés n'expirent pas automatiquement.",
        ] },
      { heading: "Assistant IA", body: [
          "BudgetSpace peut recourir à l'IA pour interpréter votre demande en texte libre. Lorsque l'IA est indisponible ou désactivée, une solution de repli déterministe (fondée sur des règles) est utilisée — tous les plans ne sont pas générés par l'IA.",
          "Lorsqu'elle est utilisée, le fournisseur en production est Google Gemini. Nous envoyons votre description textuelle ainsi que le contexte (pièce, budget, style) — nous n'envoyons ni votre nom, ni votre adresse e-mail, ni votre identifiant de compte.",
          "BudgetSpace ne conserve ni votre prompt ni la réponse de l'IA au-delà de ce que vous choisissez d'enregistrer dans un plan. Google, en qualité de sous-traitant, traite le texte selon ses propres conditions et son accord de traitement des données.",
          "Les suggestions de l'IA sont des estimations — vérifiez le prix, les dimensions et la disponibilité auprès du détaillant avant d'acheter. Ne saisissez pas d'informations personnelles sensibles.",
        ] },
      { heading: "Adresses IP, sécurité et prévention des abus", body: [
          "Nous traitons votre adresse IP temporairement pour la limitation de débit et la sécurité ; elle est conservée dans la mémoire du serveur et n'est pas enregistrée dans votre profil.",
          "Lorsque l'assistant IA est activé, l'adresse IP d'un appareil invité peut être conservée jusqu'à 45 jours comme clé de limite d'utilisation quotidienne (les utilisateurs connectés utilisent plutôt une clé de compte).",
          "Notre serveur et notre hébergeur peuvent enregistrer des adresses IP dans des journaux de sécurité ou techniques selon leurs paramètres par défaut.",
        ] },
      { heading: "Surveillance des erreurs (Sentry)", body: [
          "Nous envoyons les erreurs techniques à Sentry afin de corriger les défauts. Seuls les messages d'erreur et les traces de pile sont envoyés ; l'envoi de données personnelles est désactivé (sendDefaultPii=false), de sorte que le contenu de vos prompts, votre nom et votre adresse e-mail ne sont pas envoyés.",
        ] },
      { heading: "Hébergement, base de données et destinataires", body: [
          "L'application est hébergée chez un fournisseur cloud (par exemple Railway/Render/Fly) avec une base de données PostgreSQL.",
          "Google — la connexion (Google Sign-In) et, lorsque l'IA est activée, le traitement des demandes (Google Gemini). Sentry — la surveillance des erreurs.",
          "Stripe — la facturation des Design Session ; actuellement inactif (bêta gratuite), aucune donnée de paiement n'est donc traitée à ce jour.",
          "Nous ne vendons pas vos données. eBay n'est pas destinataire de vos données — nous récupérons des annonces publiques par catégorie et par marché et n'envoyons à eBay aucune de vos données personnelles.",
        ] },
      { heading: "Transferts internationaux", body: [
          "Certains fournisseurs (Google, Sentry, Stripe) peuvent traiter des données en dehors de l'UE, par exemple aux États-Unis. Les transferts reposent sur les clauses contractuelles types (SCCs) et/ou sur le EU-US Data Privacy Framework. Contactez-nous pour obtenir une copie des garanties applicables.",
        ] },
      { heading: "Durée de conservation des données", body: [
          "Compte et plans enregistrés : jusqu'à ce que vous les supprimiez (pas d'expiration automatique).",
          "Sessions de connexion : jusqu'à 30 jours (7 jours d'inactivité), puis élaguées.",
          "Métadonnées d'utilisation de l'IA (y compris la clé IP temporaire d'un invité) : 45 jours.",
          "Clics et retours pseudonymisés et adresses e-mail de la liste d'attente : 18 mois.",
          "Données de limitation de débit : transitoires, en mémoire (quelques minutes).",
          "Journaux d'erreurs (Sentry) et Google Analytics : selon la durée de conservation définie avec ces fournisseurs (pour Analytics, nous choisissons l'option la plus courte proposée). Journaux serveur et sauvegardes : selon les paramètres par défaut de l'hébergeur.",
        ] },
      { heading: "Vos droits et comment les exercer", body: [
          "Vous disposez d'un droit d'accès, de rectification, d'effacement, de limitation, d'opposition et de portabilité, ainsi que du droit de retirer votre consentement (par exemple pour Analytics via « Paramètres de confidentialité »).",
          "Vous pouvez supprimer votre compte et toutes les données associées immédiatement dans l'application (« Supprimer le compte ») : cela efface votre compte, vos plans enregistrés, vos enregistrements d'utilisation de l'IA et les entrées de la liste d'attente liées à votre adresse e-mail. Un journal technique transitoire peut brièvement conserver une entrée ; Stripe (s'il est un jour introduit) et Sentry conservent des données selon leurs propres conditions.",
          `Si vous ne pouvez pas accéder à votre compte ou si vous souhaitez exercer d'autres droits, contactez-nous à ${OPERATOR.email}.`,
          "Vous avez le droit d'introduire une réclamation auprès d'une autorité de contrôle — en Croatie, l'Agence pour la protection des données personnelles (AZOP, azop.hr) ; sinon, l'autorité de protection des données de votre pays.",
        ] },
      { heading: "Modifications et contact", body: [
          `Nous pouvons mettre à jour cette politique de temps à autre ; la date de dernière mise à jour est indiquée en haut. Pour toute question relative à la confidentialité, écrivez à ${OPERATOR.email}.`,
          "Ceci est une traduction fournie à titre de commodité. En cas de divergence entre les versions linguistiques, la version anglaise du présent document prévaut.",
        ] },
    ],
  },
  terms: {
    title: "Conditions d'utilisation",
    updated: "Dernière mise à jour : 2026-07-03",
    sections: [
      { heading: "En quoi consiste le service", body: [
          "BudgetSpace vous aide à planifier l'aménagement d'une pièce dans le respect d'un budget. Nous ne sommes pas un magasin et ne vendons pas de produits.",
          "Les prix et la disponibilité sont des estimations recueillies auprès des détaillants et peuvent être obsolètes — vérifiez toujours auprès du détaillant avant d'acheter.",
        ] },
      { heading: "Marques et indépendance", body: [
          "IKEA, JYSK, eBay et tous les autres noms de détaillants et de produits sont des marques de leurs propriétaires respectifs. Nous les utilisons uniquement pour identifier les produits réels et les magasins qui les vendent.",
          "BudgetSpace est un outil de planification indépendant et n'est ni affilié à, ni approuvé, ni sponsorisé par aucun de ces détaillants. Les liens d'affiliation, s'ils sont ajoutés, seront clairement signalés et ne modifieront jamais le choix du produit le mieux adapté à vous.",
        ] },
      { heading: "Assistant IA", body: [
          "Les suggestions de l'assistant IA sont des estimations destinées à vous aider à planifier, et non des conseils professionnels. Vérifiez les prix, les dimensions et la disponibilité auprès du détaillant avant d'acheter.",
        ] },
      { heading: "Compte", body: [
          "L'enregistrement et l'assistant IA utilisent la connexion Google ; vous êtes responsable de la sécurité de votre compte.",
        ] },
      { heading: "Design Session", body: [
          "Le service est actuellement entièrement gratuit (bêta précoce) — toutes les fonctionnalités sont débloquées. Il n'y a ni abonnement ni facturation récurrente. À l'avenir, nous prévoyons un paiement unique par « Design Session » (un paiement unique, sans renouvellement) ; le prix et les conditions seront clairement affichés avant tout paiement.",
        ] },
      { heading: "Utilisation acceptable", body: [
          "N'abusez pas du service — aucune surcharge automatisée ni tentative de contourner les limites.",
        ] },
      { heading: "Absence de garantie / responsabilité", body: [
          "Le service est fourni « en l'état ». Nous ne garantissons ni l'exactitude des prix ni la disponibilité et ne sommes pas responsables des décisions d'achat prises à partir de nos suggestions.",
        ] },
      { heading: "Modifications et droit applicable", body: [
          "Nous pouvons mettre à jour ces conditions de temps à autre. Le droit de la Croatie et de l'UE s'applique.",
          "Ceci est une traduction fournie à titre de commodité. En cas de divergence entre les versions linguistiques, la version anglaise du présent document prévaut.",
        ] },
    ],
  },
};

const NL: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Privacybeleid",
    updated: "Laatst bijgewerkt: 2026-07-14",
    sections: [
      { heading: "Wie BudgetSpace exploiteert", body: [
          `BudgetSpace is een gratis project (bèta) dat wordt gerund door ${OPERATOR.name} als particulier (een natuurlijke persoon), de verwerkingsverantwoordelijke voor uw persoonsgegevens.`,
          `Adres: ${OPERATOR.street}, ${OPERATOR.postalCity}, Kroatië. Contact: ${OPERATOR.email}.`,
        ] },
      { heading: "Wat wij verwerken en waarom", body: [
          "Google-aanmelding: uw naam, e-mailadres, profielfoto en Google-id (sub) — zodat u een account heeft en uw opgeslagen plannen van u zijn.",
          "Plannen en invoer die u opslaat: kamer, budget, stijl, de tekst die u typt (uw beschrijving) en stad — om het plan op te bouwen en te verfijnen.",
          "Een benaderend land uit de CDN-header — uitsluitend om de juiste markt en valuta te kiezen.",
          "Uw IP-adres — tijdelijk, voor snelheidsbeperking en beveiliging (zie \"IP-adressen en beveiliging\").",
          "Pseudonieme gebruikssignalen: welk product u heeft aangeklikt en feedback op plannen — gekoppeld aan een plan, niet aan uw naam.",
          "Google Analytics (alleen als u dit accepteert) — om te begrijpen hoe de app wordt gebruikt (zie de speciale sectie).",
          "De tekst die u typt wordt, wanneer AI is ingeschakeld, naar de AI-aanbieder gestuurd om uw verzoek te interpreteren (zie \"AI-assistent\").",
          "Een wachtlijst-e-mailadres — alleen als u ervoor kiest er een achter te laten.",
        ] },
      { heading: "Rechtsgrondslag (Art. 6 GDPR)", body: [
          "Overeenkomst (Art. 6(1)(b)): het beheren van uw account, het opslaan van plannen en het verwerken van uw verzoek namens u.",
          "Gerechtvaardigde belangen (Art. 6(1)(f)): beveiliging en het voorkomen van misbruik, monitoring van technische fouten, en pseudonieme klik-/feedbackanalyse om de dienst te verbeteren.",
          "Toestemming (Art. 6(1)(a)): Google Analytics. U kunt uw toestemming te allen tijde intrekken.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics is optioneel en wordt pas uitgevoerd nadat u dit uitdrukkelijk heeft geaccepteerd. Totdat u een keuze maakt, wordt het niet geladen en verzendt het geen gegevens.",
          "Als u weigert, werkt BudgetSpace precies hetzelfde — u verliest niets. U kunt uw keuze te allen tijde wijzigen via \"Privacyinstellingen\" in de voettekst.",
          "Wanneer u accepteert, kan Google Analytics online-identificatoren, apparaat-/browserinformatie, benaderende locatie en gebruiksgebeurtenissen verwerken. Wij gebruiken het om te begrijpen hoe de app wordt gebruikt, waar gebruikers vastlopen en welke functies nuttig zijn.",
          "Cookies die het vervolgens plaatst: _ga en _ga_<id> (meet-identificatoren). Ze worden pas na acceptatie geplaatst; als u uw toestemming intrekt, verwijderen wij ze voor zover technisch mogelijk.",
          "Wij gebruiken geen Google Ads, remarketing of advertentiepersonalisatie. Wij beweren niet dat de gegevens van Google Analytics volledig anoniem zijn.",
        ] },
      { heading: "Cookies en lokale opslag", body: [
          "Noodzakelijk: bs_auth — de aanmeldingscookie (tot 30 dagen); bs_oauth — een kortlevende (10 min) beveiligingscookie tijdens het aanmelden.",
          "Noodzakelijk (lokale opslag): bs-session-id (een id per browser voor AI-gebruikslimieten en het koppelen van uw plannen), budgetspace.market / budgetspace.lang (land en taal), budgetspace.plannerDraft / budgetspace.moveInDraft (invoerconcepten), en budgetspace.consent (uw analytics-keuze).",
          "Optioneel: Google Analytics-cookies — alleen nadat u ze heeft geaccepteerd.",
          "Wij gebruiken geen advertentie- of remarketingcookies.",
        ] },
      { heading: "Account en Google-aanmelding", body: [
          "Het aanmelden verloopt via Google (OAuth). Van Google ontvangen en bewaren wij uw naam, e-mailadres, profielfoto en Google-id; wij bewaren uw Google-wachtwoord of Google-tokens niet.",
          "Profielgegevens (naam, foto) worden bij elke Google-aanmelding vernieuwd.",
        ] },
      { heading: "Invoer, opgeslagen en gedeelde plannen", body: [
          "Wanneer u een plan opslaat, worden uw tekstbeschrijving (prompt) en de stad die u heeft ingevoerd ermee opgeslagen. Zet geen gevoelige persoonsgegevens in de beschrijving.",
          "Een opgeslagen plan heeft een privé, moeilijk te raden link. Iedereen die die link ontvangt, kan het plan openen en de producten, het budget, de kamer en de daarbij opgeslagen tekst zien (uw beschrijving en stad) — maar niet uw naam of e-mailadres. Een link is niet \"privé\" louter omdat hij moeilijk te raden is.",
          "Het delen wordt ingetrokken door het plan in de app te verwijderen — dat is de enige manier om de link te laten stoppen met werken. Opgeslagen plannen hebben geen automatische vervaldatum.",
        ] },
      { heading: "AI-assistent", body: [
          "BudgetSpace kan AI gebruiken om uw vrije-tekstverzoek te interpreteren. Wanneer AI niet beschikbaar of uitgeschakeld is, wordt een deterministische (op regels gebaseerde) terugval gebruikt — niet elk plan wordt door AI gegenereerd.",
          "Wanneer het wordt gebruikt, is de productieaanbieder Google Gemini. Wij sturen uw tekstbeschrijving plus context (kamer, budget, stijl) — wij sturen uw naam, e-mailadres of account-id niet.",
          "BudgetSpace bewaart uw prompt of de AI-respons niet verder dan wat u ervoor kiest om in een plan op te slaan. Google verwerkt de tekst, als verwerker, onder zijn eigen voorwaarden en verwerkersovereenkomst.",
          "AI-suggesties zijn schattingen — controleer de prijs, afmetingen en beschikbaarheid bij de winkelier voordat u koopt. Voer geen gevoelige persoonsgegevens in.",
        ] },
      { heading: "IP-adressen, beveiliging en het voorkomen van misbruik", body: [
          "Wij verwerken uw IP-adres tijdelijk voor snelheidsbeperking en beveiliging; het wordt in het servergeheugen bewaard en niet in uw profiel opgeslagen.",
          "Wanneer de AI-assistent is ingeschakeld, kan het IP-adres van een gastapparaat tot 45 dagen worden bewaard als sleutel voor de dagelijkse gebruikslimiet (aangemelde gebruikers gebruiken in plaats daarvan een accountsleutel).",
          "Onze server en hostingaanbieder kunnen IP-adressen registreren in beveiligings-/technische logboeken onder hun standaardinstellingen.",
        ] },
      { heading: "Foutmonitoring (Sentry)", body: [
          "Wij sturen technische fouten naar Sentry om storingen te verhelpen. Alleen foutmeldingen en stacktraces worden verzonden; het verzenden van persoonsgegevens is uitgeschakeld (sendDefaultPii=false), dus de inhoud van uw prompts, uw naam en uw e-mailadres worden niet verzonden.",
        ] },
      { heading: "Hosting, database en ontvangers", body: [
          "De app wordt gehost bij een cloudaanbieder (bijv. Railway/Render/Fly) met een PostgreSQL-database.",
          "Google — aanmelding (Google Sign-In) en, wanneer AI is ingeschakeld, verzoekverwerking (Google Gemini). Sentry — foutmonitoring.",
          "Stripe — facturering van Design Session; momenteel inactief (gratis bèta), zodat er vandaag geen betalingsgegevens worden verwerkt.",
          "Wij verkopen uw gegevens niet. eBay is geen ontvanger van uw gegevens — wij halen openbare aanbiedingen op per categorie en markt en sturen eBay geen van uw persoonsgegevens.",
        ] },
      { heading: "Internationale doorgiften", body: [
          "Sommige aanbieders (Google, Sentry, Stripe) kunnen gegevens buiten de EU verwerken, bijv. in de VS. Doorgiften steunen op Standard Contractual Clauses (SCCs) en/of het EU-US Data Privacy Framework. Neem contact met ons op voor een kopie van de relevante waarborgen.",
        ] },
      { heading: "Hoe lang wij gegevens bewaren", body: [
          "Account en opgeslagen plannen: totdat u ze verwijdert (geen automatische vervaldatum).",
          "Aanmeldingssessies: tot 30 dagen (7 dagen inactief), daarna opgeschoond.",
          "AI-gebruiksmetadata (inclusief de tijdelijke IP-sleutel van een gast): 45 dagen.",
          "Pseudonieme klikken/feedback en wachtlijst-e-mailadressen: 18 maanden.",
          "Snelheidsbeperkingsgegevens: vluchtig, in het geheugen (minuten).",
          "Foutlogboeken (Sentry) en Google Analytics: volgens de bij die aanbieders ingestelde bewaartermijn (voor Analytics stellen wij de kortst aangeboden optie in). Serverlogboeken en back-ups: volgens de standaardinstellingen van de hostingaanbieder.",
        ] },
      { heading: "Uw rechten en hoe u ze uitoefent", body: [
          "U heeft recht op inzage, rectificatie, wissing, beperking, bezwaar en overdraagbaarheid, en het recht om uw toestemming in te trekken (bijv. voor Analytics via \"Privacyinstellingen\").",
          "U kunt uw account en alle gerelateerde gegevens onmiddellijk in de app verwijderen (\"Account verwijderen\"): hiermee wist u uw account, opgeslagen plannen, AI-gebruiksgegevens en de wachtlijstvermeldingen die aan uw e-mailadres zijn gekoppeld. Een vluchtig technisch logboek kan kort een vermelding bewaren; Stripe (indien ooit ingevoerd) en Sentry bewaren gegevens onder hun eigen voorwaarden.",
          `Als u geen toegang tot uw account heeft of andere rechten wilt uitoefenen, neem dan contact met ons op via ${OPERATOR.email}.`,
          "U heeft het recht om een klacht in te dienen bij een toezichthoudende autoriteit — in Kroatië het Bureau voor de Bescherming van Persoonsgegevens (AZOP, azop.hr); anders bij uw nationale gegevensbeschermingsautoriteit.",
        ] },
      { heading: "Wijzigingen en contact", body: [
          `Wij kunnen dit beleid van tijd tot tijd bijwerken; de datum van laatste bijwerking staat bovenaan. Voor vragen over privacy kunt u schrijven naar ${OPERATOR.email}.`,
          "Dit is een vertaling die voor uw gemak wordt aangeboden. Bij enige discrepantie tussen de taalversies prevaleert de Engelse versie van dit document.",
        ] },
    ],
  },
  terms: {
    title: "Gebruiksvoorwaarden",
    updated: "Laatst bijgewerkt: 2026-07-03",
    sections: [
      { heading: "Wat de dienst is", body: [
          "BudgetSpace helpt u bij het plannen van de inrichting van een kamer binnen een budget. Wij zijn geen winkel en verkopen geen producten.",
          "Prijzen en beschikbaarheid zijn schattingen die bij winkeliers zijn verzameld en kunnen verouderd zijn — controleer altijd bij de winkelier voordat u koopt.",
        ] },
      { heading: "Handelsmerken en onafhankelijkheid", body: [
          "IKEA, JYSK, eBay en alle andere winkelier- en productnamen zijn handelsmerken van hun respectieve eigenaren. Wij gebruiken ze uitsluitend om de echte producten en de winkels die ze verkopen te identificeren.",
          "BudgetSpace is een onafhankelijk planningshulpmiddel en is niet gelieerd aan, onderschreven door of gesponsord door een van deze winkeliers. Affiliatelinks, indien toegevoegd, worden duidelijk gelabeld en veranderen nooit welk product het beste voor u is.",
        ] },
      { heading: "AI-assistent", body: [
          "De suggesties van de AI-assistent zijn schattingen om u te helpen plannen, geen professioneel advies. Controleer prijzen, afmetingen en beschikbaarheid bij de winkelier voordat u koopt.",
        ] },
      { heading: "Account", body: [
          "Voor het opslaan en de AI-assistent wordt Google-aanmelding gebruikt; u bent verantwoordelijk voor de beveiliging van uw account.",
        ] },
      { heading: "Design Session", body: [
          "De dienst is momenteel volledig gratis (vroege bèta) — alle functies zijn ontgrendeld. Er is geen abonnement en geen terugkerende facturering. In de toekomst zijn wij van plan een eenmalige kosten per \"Design Session\" te hanteren (één enkele betaling, geen verlenging); de prijs en voorwaarden worden duidelijk getoond vóór enige afschrijving.",
        ] },
      { heading: "Aanvaardbaar gebruik", body: [
          "Misbruik de dienst niet — geen geautomatiseerde overbelasting of pogingen om limieten te omzeilen.",
        ] },
      { heading: "Geen garantie / aansprakelijkheid", body: [
          "De dienst wordt geleverd \"as is\". Wij garanderen geen nauwkeurigheid van prijzen of beschikbaarheid en zijn niet aansprakelijk voor aankoopbeslissingen die op basis van onze suggesties worden genomen.",
        ] },
      { heading: "Wijzigingen en toepasselijk recht", body: [
          "Wij kunnen deze voorwaarden van tijd tot tijd bijwerken. De wetten van Kroatië en de EU zijn van toepassing.",
          "Dit is een vertaling die voor uw gemak wordt aangeboden. Bij enige discrepantie tussen de taalversies prevaleert de Engelse versie van dit document.",
        ] },
    ],
  },
};

const SK: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Zásady ochrany osobných údajov",
    updated: "Posledná aktualizácia: 2026-07-14",
    sections: [
      { heading: "Kto prevádzkuje BudgetSpace", body: [
          `BudgetSpace je bezplatný projekt (beta), ktorý prevádzkuje ${OPERATOR.name} ako jednotlivec (fyzická osoba), prevádzkovateľ vašich osobných údajov.`,
          `Adresa: ${OPERATOR.street}, ${OPERATOR.postalCity}, Chorvátsko. Kontakt: ${OPERATOR.email}.`,
        ] },
      { heading: "Aké údaje spracúvame a prečo", body: [
          "Prihlásenie cez Google: vaše meno, e-mail, profilová fotka a Google id (sub) — aby ste mali účet a vaše uložené plány patrili vám.",
          "Plány a vstupy, ktoré ukladáte: miestnosť, rozpočet, štýl, text, ktorý napíšete (váš opis) a mesto — na vytvorenie a spresnenie plánu.",
          "Približná krajina z hlavičky CDN — len na výber správneho trhu a meny.",
          "Vaša IP adresa — dočasne, na obmedzenie počtu požiadaviek a bezpečnosť (pozri „IP adresy a bezpečnosť“).",
          "Pseudonymizované signály o používaní: na ktorý produkt ste klikli a spätná väzba k plánu — viazané na plán, nie na vaše meno.",
          "Google Analytics (len ak súhlasíte) — na pochopenie, ako sa aplikácia používa (pozri samostatnú sekciu).",
          "Text, ktorý napíšete, sa pri zapnutej AI odosiela poskytovateľovi AI na interpretáciu vašej požiadavky (pozri „AI asistent“).",
          "E-mail do zoznamu čakateľov — len ak sa ho rozhodnete zanechať.",
        ] },
      { heading: "Právny základ (Art. 6 GDPR)", body: [
          "Zmluva (Art. 6(1)(b)): prevádzka vášho účtu, ukladanie plánov a spracovanie vašej požiadavky vo vašom mene.",
          "Oprávnené záujmy (Art. 6(1)(f)): bezpečnosť a prevencia zneužitia, monitorovanie technických chýb a pseudonymizovaná analytika kliknutí/spätnej väzby na zlepšenie služby.",
          "Súhlas (Art. 6(1)(a)): Google Analytics. Súhlas môžete kedykoľvek odvolať.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics je voliteľná a spustí sa až po tom, ako ju výslovne prijmete. Kým sa nerozhodnete, nenačíta sa a neodosiela žiadne údaje.",
          "Ak odmietnete, BudgetSpace funguje úplne rovnako — o nič neprídete. Svoju voľbu môžete kedykoľvek zmeniť cez „Nastavenia súkromia“ v pätičke.",
          "Po prijatí môže Google Analytics spracúvať online identifikátory, informácie o zariadení/prehliadači, približnú polohu a udalosti používania. Používame ju na pochopenie, ako sa aplikácia používa, kde sa používatelia zaseknú a ktoré funkcie sú užitočné.",
          "Súbory cookie, ktoré potom nastaví: _ga a _ga_<id> (identifikátory merania). Nastavujú sa až po prijatí; ak súhlas odvoláte, odstránime ich v technicky možnej miere.",
          "Nepoužívame Google Ads, remarketing ani personalizáciu reklamy. Netvrdíme, že údaje Google Analytics sú úplne anonymné.",
        ] },
      { heading: "Súbory cookie a lokálne úložisko", body: [
          "Nevyhnutné: bs_auth — prihlasovací súbor cookie (až 30 dní); bs_oauth — krátkodobý (10 min) bezpečnostný súbor cookie počas prihlásenia.",
          "Nevyhnutné (lokálne úložisko): bs-session-id (id pre jednotlivý prehliadač na limity používania AI a prepojenie vašich plánov), budgetspace.market / budgetspace.lang (krajina a jazyk), budgetspace.plannerDraft / budgetspace.moveInDraft (koncepty vstupov) a budgetspace.consent (vaša voľba analytiky).",
          "Voliteľné: súbory cookie Google Analytics — len po ich prijatí.",
          "Nepoužívame žiadne reklamné ani remarketingové súbory cookie.",
        ] },
      { heading: "Účet a prihlásenie cez Google", body: [
          "Prihlásenie prebieha cez Google (OAuth). Od Google dostávame a ukladáme vaše meno, e-mail, profilovú fotku a Google id; neukladáme vaše heslo Google ani tokeny Google.",
          "Profilové údaje (meno, fotka) sa obnovujú pri každom prihlásení cez Google.",
        ] },
      { heading: "Vstupy, uložené a zdieľané plány", body: [
          "Keď uložíte plán, spolu s ním sa uloží váš textový opis (prompt) a mesto, ktoré ste zadali. Do opisu neuvádzajte citlivé osobné údaje.",
          "Uložený plán má súkromný, ťažko uhádnuteľný odkaz. Ktokoľvek, kto tento odkaz dostane, môže plán otvoriť a vidieť produkty, rozpočet, miestnosť a text uložený s ním (váš opis a mesto) — nie však vaše meno ani e-mail. Odkaz nie je „súkromný“ len preto, že je ťažko uhádnuteľný.",
          "Zdieľanie sa zruší odstránením plánu v aplikácii — to je jediný spôsob, ako odkaz prestane fungovať. Uložené plány nemajú automatické vypršanie platnosti.",
        ] },
      { heading: "AI asistent", body: [
          "BudgetSpace môže na interpretáciu vašej voľnej textovej požiadavky použiť AI. Keď je AI nedostupná alebo vypnutá, použije sa deterministické (na pravidlách založené) záložné riešenie — nie každý plán generuje AI.",
          "Ak sa použije, produkčným poskytovateľom je Google Gemini. Odosielame váš textový opis a kontext (miestnosť, rozpočet, štýl) — neodosielame vaše meno, e-mail ani id účtu.",
          "BudgetSpace neukladá váš prompt ani odpoveď AI nad rámec toho, čo sa rozhodnete uložiť v pláne. Google ako sprostredkovateľ spracúva text podľa vlastných podmienok a zmluvy o spracovaní údajov.",
          "Návrhy AI sú odhady — pred nákupom si overte cenu, rozmery a dostupnosť u predajcu. Nezadávajte citlivé osobné údaje.",
        ] },
      { heading: "IP adresy, bezpečnosť a prevencia zneužitia", body: [
          "Vašu IP adresu spracúvame dočasne na obmedzenie počtu požiadaviek a bezpečnosť; uchováva sa v pamäti servera a neukladá sa do vášho profilu.",
          "Keď je AI asistent zapnutý, IP adresa hosťovského zariadenia sa môže uchovávať až 45 dní ako kľúč denného limitu používania (prihlásení používatelia namiesto toho používajú kľúč účtu).",
          "Náš server a poskytovateľ hostingu môžu zaznamenávať IP adresy v bezpečnostných/technických protokoloch podľa svojich predvolených nastavení.",
        ] },
      { heading: "Monitorovanie chýb (Sentry)", body: [
          "Technické chyby odosielame do Sentry na odstránenie porúch. Odosielajú sa len chybové hlásenia a výpisy zásobníka; odosielanie osobných údajov je vypnuté (sendDefaultPii=false), takže obsah vašich promptov, vaše meno a e-mail sa neodosielajú.",
        ] },
      { heading: "Hosting, databáza a príjemcovia", body: [
          "Aplikácia je hostovaná u poskytovateľa cloudu (napr. Railway/Render/Fly) s databázou PostgreSQL.",
          "Google — prihlásenie (Google Sign-In) a pri zapnutej AI spracovanie požiadaviek (Google Gemini). Sentry — monitorovanie chýb.",
          "Stripe — fakturácia Design Session; momentálne neaktívna (bezplatná beta), takže dnes sa nespracúvajú žiadne platobné údaje.",
          "Vaše údaje nepredávame. eBay nie je príjemcom vašich údajov — načítavame verejné ponuky podľa kategórie a trhu a eBay neposielame žiadne vaše osobné údaje.",
        ] },
      { heading: "Medzinárodné prenosy", body: [
          "Niektorí poskytovatelia (Google, Sentry, Stripe) môžu spracúvať údaje mimo EÚ, napr. v USA. Prenosy sa opierajú o Standard Contractual Clauses (SCCs) a/alebo EU-US Data Privacy Framework. Pre kópiu príslušných záruk nás kontaktujte.",
        ] },
      { heading: "Ako dlho uchovávame údaje", body: [
          "Účet a uložené plány: kým ich neodstránite (bez automatického vypršania platnosti).",
          "Prihlasovacie relácie: až 30 dní (7 dní nečinnosti), potom sa odstránia.",
          "Metadáta o používaní AI (vrátane dočasného IP kľúča hosťa): 45 dní.",
          "Pseudonymizované kliknutia/spätná väzba a e-maily v zozname čakateľov: 18 mesiacov.",
          "Údaje o obmedzení požiadaviek: dočasné, v pamäti (minúty).",
          "Protokoly chýb (Sentry) a Google Analytics: podľa doby uchovávania nastavenej u týchto poskytovateľov (pri Analytics nastavujeme najkratšiu ponúkanú možnosť). Protokoly servera a zálohy: podľa predvolených nastavení poskytovateľa hostingu.",
        ] },
      { heading: "Vaše práva a ako ich uplatniť", body: [
          "Máte právo na prístup, opravu, vymazanie, obmedzenie, namietanie a prenosnosť a právo odvolať súhlas (napr. pre Analytics cez „Nastavenia súkromia“).",
          "Svoj účet a všetky súvisiace údaje môžete okamžite odstrániť v aplikácii („Odstrániť účet“): tým sa vymaže váš účet, uložené plány, záznamy o používaní AI a záznamy v zozname čakateľov viazané na váš e-mail. Dočasný technický protokol môže krátko uchovať záznam; Stripe (ak sa niekedy zavedie) a Sentry uchovávajú údaje podľa vlastných podmienok.",
          `Ak sa nemôžete dostať do svojho účtu alebo chcete uplatniť iné práva, kontaktujte nás na ${OPERATOR.email}.`,
          "Máte právo podať sťažnosť dozornému orgánu — v Chorvátsku je to Agentúra na ochranu osobných údajov (AZOP, azop.hr); inak váš národný orgán na ochranu údajov.",
        ] },
      { heading: "Zmeny a kontakt", body: [
          `Tieto zásady môžeme z času na čas aktualizovať; dátum poslednej aktualizácie je uvedený hore. So všetkými otázkami týkajúcimi sa súkromia píšte na ${OPERATOR.email}.`,
          "Toto je preklad poskytnutý pre vaše pohodlie. V prípade akéhokoľvek rozporu medzi jazykovými verziami má prednosť anglická verzia tohto dokumentu.",
        ] },
    ],
  },
  terms: {
    title: "Podmienky používania",
    updated: "Posledná aktualizácia: 2026-07-03",
    sections: [
      { heading: "Čo je táto služba", body: [
          "BudgetSpace vám pomáha naplánovať zariadenie miestnosti v rámci rozpočtu. Nie sme obchod a nepredávame produkty.",
          "Ceny a dostupnosť sú odhady získané od predajcov a môžu byť neaktuálne — pred nákupom si to vždy overte u predajcu.",
        ] },
      { heading: "Ochranné známky a nezávislosť", body: [
          "IKEA, JYSK, eBay a všetky ostatné názvy predajcov a produktov sú ochrannými známkami ich príslušných vlastníkov. Používame ich len na identifikáciu skutočných produktov a obchodov, ktoré ich predávajú.",
          "BudgetSpace je nezávislý plánovací nástroj a nie je prepojený, podporovaný ani sponzorovaný žiadnym z týchto predajcov. Affiliate odkazy, ak budú pridané, budú jasne označené a nikdy nezmenia, ktorý produkt je pre vás najlepší.",
        ] },
      { heading: "AI asistent", body: [
          "Návrhy AI asistenta sú odhady, ktoré vám majú pomôcť pri plánovaní, nie odborná rada. Pred nákupom si overte ceny, rozmery a dostupnosť u predajcu.",
        ] },
      { heading: "Účet", body: [
          "Ukladanie a AI asistent používajú prihlásenie cez Google; za bezpečnosť svojho účtu zodpovedáte vy.",
        ] },
      { heading: "Design Session", body: [
          "Služba je momentálne úplne bezplatná (skorá beta) — všetky funkcie sú odomknuté. Neexistuje žiadne predplatné ani opakovaná fakturácia. V budúcnosti plánujeme jednorazový poplatok za „Design Session“ (jedna platba, bez obnovenia); cena a podmienky budú jasne zobrazené pred akýmkoľvek poplatkom.",
        ] },
      { heading: "Prijateľné používanie", body: [
          "Nezneužívajte službu — žiadne automatizované preťažovanie ani pokusy o obídenie limitov.",
        ] },
      { heading: "Bez záruky / zodpovednosti", body: [
          "Služba sa poskytuje „tak, ako je“. Nezaručujeme presnosť cien ani dostupnosť a nezodpovedáme za rozhodnutia o nákupe uskutočnené na základe našich návrhov.",
        ] },
      { heading: "Zmeny a rozhodné právo", body: [
          "Tieto podmienky môžeme z času na čas aktualizovať. Uplatňujú sa právne predpisy Chorvátska a EÚ.",
          "Toto je preklad poskytnutý pre vaše pohodlie. V prípade akéhokoľvek rozporu medzi jazykovými verziami má prednosť anglická verzia tohto dokumentu.",
        ] },
    ],
  },
};

const ES: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Política de Privacidad",
    updated: "Última actualización: 2026-07-14",
    sections: [
      { heading: "Quién opera BudgetSpace", body: [
          `BudgetSpace es un proyecto gratuito (beta) gestionado por ${OPERATOR.name} como particular (persona física), responsable del tratamiento de sus datos personales.`,
          `Dirección: ${OPERATOR.street}, ${OPERATOR.postalCity}, Croacia. Contacto: ${OPERATOR.email}.`,
        ] },
      { heading: "Qué tratamos y por qué", body: [
          "Inicio de sesión con Google: su nombre, correo electrónico, foto de perfil e identificador de Google (sub), para que disponga de una cuenta y sus planes guardados sean suyos.",
          "Planes y datos que guarda: la estancia, el presupuesto, el estilo, el texto que escribe (su descripción) y la ciudad, para elaborar y perfeccionar el plan.",
          "Un país aproximado obtenido de la cabecera de la CDN, únicamente para elegir el mercado y la moneda correctos.",
          "Su dirección IP, de forma temporal, para la limitación de solicitudes y la seguridad (véase «Direcciones IP y seguridad»).",
          "Señales de uso seudonimizadas: en qué producto hizo clic y la valoración del plan, vinculadas a un plan y no a su nombre.",
          "Google Analytics (solo si lo acepta), para comprender cómo se utiliza la aplicación (véase la sección específica).",
          "El texto que escribe, cuando la IA está habilitada, se envía al proveedor de IA para interpretar su solicitud (véase «Asistente de IA»).",
          "Un correo electrónico de lista de espera, únicamente si decide dejar uno.",
        ] },
      { heading: "Base jurídica (Art. 6 GDPR)", body: [
          "Contrato (Art. 6(1)(b)): gestión de su cuenta, guardado de planes y tratamiento de su solicitud en su nombre.",
          "Intereses legítimos (Art. 6(1)(f)): seguridad y prevención de abusos, supervisión de errores técnicos y analítica seudonimizada de clics/valoraciones para mejorar el servicio.",
          "Consentimiento (Art. 6(1)(a)): Google Analytics. Puede retirar su consentimiento en cualquier momento.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics es opcional y solo se ejecuta después de que usted lo acepte de forma expresa. Hasta que lo decida, no se carga ni envía dato alguno.",
          "Si lo rechaza, BudgetSpace funciona exactamente igual: no pierde nada. Puede modificar su elección en cualquier momento a través de «Configuración de privacidad» en el pie de página.",
          "Cuando se acepta, Google Analytics puede tratar identificadores en línea, información del dispositivo/navegador, ubicación aproximada y eventos de uso. Lo utilizamos para comprender cómo se utiliza la aplicación, dónde se atascan los usuarios y qué funciones resultan útiles.",
          "Cookies que entonces instala: _ga y _ga_<id> (identificadores de medición). Se instalan únicamente tras la aceptación; si retira su consentimiento, las eliminamos en la medida en que sea técnicamente posible.",
          "No utilizamos Google Ads, remarketing ni personalización de anuncios. No afirmamos que los datos de Google Analytics sean totalmente anónimos.",
        ] },
      { heading: "Cookies y almacenamiento local", body: [
          "Necesarias: bs_auth, la cookie de inicio de sesión (hasta 30 días); bs_oauth, una cookie de seguridad de corta duración (10 min) durante el inicio de sesión.",
          "Necesarias (almacenamiento local): bs-session-id (un identificador por navegador para los límites de uso de la IA y para vincular sus planes), budgetspace.market / budgetspace.lang (país e idioma), budgetspace.plannerDraft / budgetspace.moveInDraft (borradores de datos introducidos) y budgetspace.consent (su elección sobre la analítica).",
          "Opcionales: cookies de Google Analytics, únicamente después de que las acepte.",
          "No utilizamos cookies de publicidad ni de remarketing.",
        ] },
      { heading: "Cuenta e inicio de sesión con Google", body: [
          "El inicio de sesión se realiza a través de Google (OAuth). De Google recibimos y almacenamos su nombre, correo electrónico, foto de perfil e identificador de Google; no almacenamos su contraseña de Google ni sus tokens de Google.",
          "Los datos de perfil (nombre, foto) se actualizan en cada inicio de sesión con Google.",
        ] },
      { heading: "Datos introducidos, planes guardados y compartidos", body: [
          "Cuando guarda un plan, su descripción de texto (prompt) y la ciudad que ha introducido se guardan junto con él. No incluya información personal sensible en la descripción.",
          "Un plan guardado tiene un enlace privado y difícil de adivinar. Cualquier persona que reciba ese enlace puede abrir el plan y ver los productos, el presupuesto, la estancia y el texto guardado con él (su descripción y ciudad), pero no su nombre ni su correo electrónico. Un enlace no es «privado» por el mero hecho de ser difícil de adivinar.",
          "El uso compartido se revoca eliminando el plan en la aplicación: esa es la única manera de que el enlace deje de funcionar. Los planes guardados no caducan automáticamente.",
        ] },
      { heading: "Asistente de IA", body: [
          "BudgetSpace puede utilizar IA para interpretar su solicitud en texto libre. Cuando la IA no está disponible o está desactivada, se utiliza un mecanismo alternativo determinista (basado en reglas): no todos los planes son generados por IA.",
          "Cuando se utiliza, el proveedor en producción es Google Gemini. Enviamos su descripción de texto junto con el contexto (estancia, presupuesto, estilo); no enviamos su nombre, correo electrónico ni identificador de cuenta.",
          "BudgetSpace no almacena su prompt ni la respuesta de la IA más allá de lo que usted decida guardar en un plan. Google, como encargado del tratamiento, gestiona el texto conforme a sus propios términos y a su acuerdo de tratamiento de datos.",
          "Las sugerencias de la IA son estimaciones: compruebe el precio, las dimensiones y la disponibilidad con el minorista antes de comprar. No introduzca información personal sensible.",
        ] },
      { heading: "Direcciones IP, seguridad y prevención de abusos", body: [
          "Tratamos su dirección IP de forma temporal para la limitación de solicitudes y la seguridad; se conserva en la memoria del servidor y no se almacena en su perfil.",
          "Cuando el asistente de IA está habilitado, la IP del dispositivo de un invitado puede conservarse hasta 45 días como clave del límite de uso diario (los usuarios registrados utilizan en su lugar una clave de cuenta).",
          "Nuestro servidor y proveedor de alojamiento pueden registrar direcciones IP en los registros de seguridad/técnicos conforme a su configuración predeterminada.",
        ] },
      { heading: "Supervisión de errores (Sentry)", body: [
          "Enviamos los errores técnicos a Sentry para corregir fallos. Solo se envían mensajes de error y trazas de la pila; el envío de datos personales está desactivado (sendDefaultPii=false), de modo que el contenido de sus prompts, su nombre y su correo electrónico no se envían.",
        ] },
      { heading: "Alojamiento, base de datos y destinatarios", body: [
          "La aplicación se aloja con un proveedor en la nube (p. ej. Railway/Render/Fly) con una base de datos PostgreSQL.",
          "Google: inicio de sesión (Google Sign-In) y, cuando la IA está habilitada, tratamiento de la solicitud (Google Gemini). Sentry: supervisión de errores.",
          "Stripe: facturación de la Design Session; actualmente inactiva (beta gratuita), por lo que hoy no se tratan datos de pago.",
          "No vendemos sus datos. eBay no es destinatario de sus datos: obtenemos anuncios públicos por categoría y mercado y no enviamos a eBay ninguno de sus datos personales.",
        ] },
      { heading: "Transferencias internacionales", body: [
          "Algunos proveedores (Google, Sentry, Stripe) pueden tratar datos fuera de la UE, p. ej. en EE. UU. Las transferencias se basan en las Cláusulas Contractuales Tipo (SCCs) o en el EU-US Data Privacy Framework. Contáctenos para obtener una copia de las garantías correspondientes.",
        ] },
      { heading: "Durante cuánto tiempo conservamos los datos", body: [
          "Cuenta y planes guardados: hasta que los elimine (sin caducidad automática).",
          "Sesiones de inicio de sesión: hasta 30 días (7 días de inactividad), tras lo cual se depuran.",
          "Metadatos de uso de la IA (incluida la clave IP temporal de un invitado): 45 días.",
          "Clics/valoraciones seudonimizados y correos electrónicos de la lista de espera: 18 meses.",
          "Datos de limitación de solicitudes: transitorios, en memoria (minutos).",
          "Registros de errores (Sentry) y Google Analytics: conforme al período de conservación establecido con esos proveedores (en Analytics fijamos la opción más corta ofrecida). Registros del servidor y copias de seguridad: conforme a la configuración predeterminada del proveedor de alojamiento.",
        ] },
      { heading: "Sus derechos y cómo ejercerlos", body: [
          "Tiene derecho de acceso, rectificación, supresión, limitación, oposición y portabilidad, así como derecho a retirar el consentimiento (p. ej., para Analytics a través de «Configuración de privacidad»).",
          "Puede eliminar su cuenta y todos los datos relacionados de inmediato en la aplicación («Eliminar cuenta»): esto borra su cuenta, los planes guardados, los registros de uso de la IA y las entradas de la lista de espera vinculadas a su correo electrónico. Un registro técnico transitorio puede conservar brevemente una entrada; Stripe (si alguna vez se introduce) y Sentry conservan datos conforme a sus propios términos.",
          `Si no puede acceder a su cuenta o desea ejercer otros derechos, contáctenos en ${OPERATOR.email}.`,
          "Tiene derecho a presentar una reclamación ante una autoridad de control: en Croacia, la Agencia de Protección de Datos Personales (AZOP, azop.hr); en caso contrario, ante su autoridad nacional de protección de datos.",
        ] },
      { heading: "Cambios y contacto", body: [
          `Podemos actualizar esta política de vez en cuando; la fecha de última actualización se muestra en la parte superior. Para cualquier consulta sobre privacidad, escriba a ${OPERATOR.email}.`,
          "Esta es una traducción proporcionada para su comodidad. Si existe alguna discrepancia entre las versiones lingüísticas, prevalecerá la versión en inglés de este documento.",
        ] },
    ],
  },
  terms: {
    title: "Condiciones de Uso",
    updated: "Última actualización: 2026-07-03",
    sections: [
      { heading: "Qué es el servicio", body: [
          "BudgetSpace le ayuda a planificar el amueblamiento de una estancia dentro de un presupuesto. No somos una tienda ni vendemos productos.",
          "Los precios y la disponibilidad son estimaciones recopiladas de los minoristas y pueden estar desactualizados: compruébelos siempre con el minorista antes de comprar.",
        ] },
      { heading: "Marcas comerciales e independencia", body: [
          "IKEA, JYSK, eBay y todos los demás nombres de minoristas y productos son marcas comerciales de sus respectivos titulares. Los utilizamos únicamente para identificar los productos reales y las tiendas que los venden.",
          "BudgetSpace es una herramienta de planificación independiente y no está afiliada a ninguno de estos minoristas, ni cuenta con su respaldo o patrocinio. Los enlaces de afiliación, si se añaden, se etiquetarán con claridad y nunca cambiarán cuál es el producto más adecuado para usted.",
        ] },
      { heading: "Asistente de IA", body: [
          "Las sugerencias del asistente de IA son estimaciones para ayudarle a planificar, no asesoramiento profesional. Compruebe los precios, las dimensiones y la disponibilidad con el minorista antes de comprar.",
        ] },
      { heading: "Cuenta", body: [
          "El guardado y el asistente de IA utilizan el inicio de sesión con Google; usted es responsable de la seguridad de su cuenta.",
        ] },
      { heading: "Design Session", body: [
          "El servicio es actualmente totalmente gratuito (beta inicial): todas las funciones están desbloqueadas. No hay suscripción ni facturación recurrente. En el futuro tenemos previsto un cargo único por «Design Session» (un pago único, sin renovación); el precio y las condiciones se mostrarán con claridad antes de cualquier cargo.",
        ] },
      { heading: "Uso aceptable", body: [
          "No abuse del servicio: sin sobrecargas automatizadas ni intentos de eludir los límites.",
        ] },
      { heading: "Sin garantía / responsabilidad", body: [
          "El servicio se presta «tal cual». No garantizamos la exactitud de los precios ni la disponibilidad y no somos responsables de las decisiones de compra tomadas a partir de nuestras sugerencias.",
        ] },
      { heading: "Cambios y legislación aplicable", body: [
          "Podemos actualizar estas condiciones de vez en cuando. Se aplican las leyes de Croacia y de la UE.",
          "Esta es una traducción proporcionada para su comodidad. Si existe alguna discrepancia entre las versiones lingüísticas, prevalecerá la versión en inglés de este documento.",
        ] },
    ],
  },
};

const PT: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Política de Privacidade",
    updated: "Última atualização: 2026-07-14",
    sections: [
      { heading: "Quem opera a BudgetSpace", body: [
          `A BudgetSpace é um projeto gratuito (beta) gerido por ${OPERATOR.name} na qualidade de particular (pessoa singular), o responsável pelo tratamento dos seus dados pessoais.`,
          `Endereço: ${OPERATOR.street}, ${OPERATOR.postalCity}, Croácia. Contacto: ${OPERATOR.email}.`,
        ] },
      { heading: "O que tratamos e porquê", body: [
          "Início de sessão com a Google: o seu nome, endereço de e-mail, foto de perfil e identificador Google (sub) — para que tenha uma conta e os seus planos guardados sejam seus.",
          "Planos e dados que introduz e guarda: divisão, orçamento, estilo, o texto que escreve (a sua descrição) e a cidade — para criar e aperfeiçoar o plano.",
          "Um país aproximado obtido a partir do cabeçalho da CDN — apenas para escolher o mercado e a moeda corretos.",
          "O seu endereço IP — temporariamente, para limitação de pedidos e segurança (ver «Endereços IP e segurança»).",
          "Sinais de utilização pseudonimizados: em que produto clicou e o feedback sobre o plano — associados a um plano, não ao seu nome.",
          "Google Analytics (apenas se aceitar) — para compreender como a aplicação é utilizada (ver a secção dedicada).",
          "O texto que escreve, quando a IA está ativada, é enviado ao fornecedor de IA para interpretar o seu pedido (ver «Assistente de IA»).",
          "Um e-mail para a lista de espera — apenas se optar por deixá-lo.",
        ] },
      { heading: "Fundamento jurídico (Art. 6 GDPR)", body: [
          "Contrato (Art. 6(1)(b)): gerir a sua conta, guardar planos e processar o seu pedido em seu nome.",
          "Interesses legítimos (Art. 6(1)(f)): segurança e prevenção de abusos, monitorização de erros técnicos e análise pseudonimizada de cliques/feedback para melhorar o serviço.",
          "Consentimento (Art. 6(1)(a)): Google Analytics. Pode retirar o consentimento a qualquer momento.",
        ] },
      { heading: "Google Analytics", body: [
          "O Google Analytics é opcional e só é executado depois de o aceitar explicitamente. Até que decida, não é carregado e não envia quaisquer dados.",
          "Se recusar, a BudgetSpace funciona exatamente da mesma forma — não perde nada. Pode alterar a sua escolha a qualquer momento através de «Definições de privacidade» no rodapé.",
          "Quando aceite, o Google Analytics pode tratar identificadores online, informações do dispositivo/navegador, localização aproximada e eventos de utilização. Utilizamo-lo para compreender como a aplicação é usada, onde os utilizadores ficam bloqueados e que funcionalidades são úteis.",
          "Cookies que então define: _ga e _ga_<id> (identificadores de medição). São definidos apenas após a aceitação; se retirar o consentimento, removemo-los na medida do tecnicamente possível.",
          "Não utilizamos o Google Ads, remarketing ou personalização de anúncios. Não afirmamos que os dados do Google Analytics são totalmente anónimos.",
        ] },
      { heading: "Cookies e armazenamento local", body: [
          "Necessários: bs_auth — o cookie de início de sessão (até 30 dias); bs_oauth — um cookie de segurança de curta duração (10 min) durante o início de sessão.",
          "Necessários (armazenamento local): bs-session-id (um identificador por navegador para os limites de utilização de IA e para associar os seus planos), budgetspace.market / budgetspace.lang (país e idioma), budgetspace.plannerDraft / budgetspace.moveInDraft (rascunhos de dados introduzidos) e budgetspace.consent (a sua escolha quanto à análise).",
          "Opcionais: cookies do Google Analytics — apenas depois de os aceitar.",
          "Não utilizamos quaisquer cookies de publicidade ou remarketing.",
        ] },
      { heading: "Conta e início de sessão com a Google", body: [
          "O início de sessão é feito através da Google (OAuth). Da Google recebemos e armazenamos o seu nome, endereço de e-mail, foto de perfil e identificador Google; não armazenamos a sua palavra-passe da Google nem os tokens da Google.",
          "Os dados de perfil (nome, foto) são atualizados a cada início de sessão com a Google.",
        ] },
      { heading: "Dados introduzidos, planos guardados e partilhados", body: [
          "Quando guarda um plano, a sua descrição em texto (prompt) e a cidade que introduziu são guardadas com ele. Não coloque informações pessoais sensíveis na descrição.",
          "Um plano guardado tem uma ligação privada e difícil de adivinhar. Qualquer pessoa que receba essa ligação pode abrir o plano e ver os produtos, o orçamento, a divisão e o texto guardado com ele (a sua descrição e cidade) — mas não o seu nome ou e-mail. Uma ligação não é «privada» apenas por ser difícil de adivinhar.",
          "A partilha é revogada eliminando o plano na aplicação — essa é a única forma de fazer com que a ligação deixe de funcionar. Os planos guardados não têm caducidade automática.",
        ] },
      { heading: "Assistente de IA", body: [
          "A BudgetSpace pode utilizar IA para interpretar o seu pedido em texto livre. Quando a IA está indisponível ou desativada, é utilizada uma alternativa determinística (baseada em regras) — nem todos os planos são gerados por IA.",
          "Quando utilizada, o fornecedor de produção é o Google Gemini. Enviamos a sua descrição em texto mais o contexto (divisão, orçamento, estilo) — não enviamos o seu nome, e-mail ou identificador de conta.",
          "A BudgetSpace não armazena o seu prompt nem a resposta da IA para além do que optar por guardar num plano. A Google, na qualidade de subcontratante, trata o texto ao abrigo dos seus próprios termos e acordo de tratamento de dados.",
          "As sugestões da IA são estimativas — verifique o preço, as dimensões e a disponibilidade junto do retalhista antes de comprar. Não introduza informações pessoais sensíveis.",
        ] },
      { heading: "Endereços IP, segurança e prevenção de abusos", body: [
          "Tratamos o seu endereço IP temporariamente para limitação de pedidos e segurança; é mantido na memória do servidor e não é armazenado no seu perfil.",
          "Quando o assistente de IA está ativado, o IP de um dispositivo de convidado pode ser mantido até 45 dias como chave do limite diário de utilização (os utilizadores com sessão iniciada usam uma chave de conta em vez disso).",
          "O nosso servidor e o fornecedor de alojamento podem registar endereços IP em registos de segurança/técnicos ao abrigo das respetivas configurações predefinidas.",
        ] },
      { heading: "Monitorização de erros (Sentry)", body: [
          "Enviamos erros técnicos para o Sentry para corrigir falhas. Apenas mensagens de erro e rastreios de pilha são enviados; o envio de dados pessoais está desativado (sendDefaultPii=false), pelo que o conteúdo dos seus prompts, o seu nome e o seu e-mail não são enviados.",
        ] },
      { heading: "Alojamento, base de dados e destinatários", body: [
          "A aplicação está alojada num fornecedor de nuvem (por exemplo, Railway/Render/Fly) com uma base de dados PostgreSQL.",
          "Google — início de sessão (Google Sign-In) e, quando a IA está ativada, o processamento de pedidos (Google Gemini). Sentry — monitorização de erros.",
          "Stripe — faturação da Design Session; atualmente inativo (beta gratuito), pelo que hoje não são tratados quaisquer dados de pagamento.",
          "Não vendemos os seus dados. O eBay não é destinatário dos seus dados — obtemos anúncios públicos por categoria e mercado e não enviamos ao eBay quaisquer dos seus dados pessoais.",
        ] },
      { heading: "Transferências internacionais", body: [
          "Alguns fornecedores (Google, Sentry, Stripe) podem tratar dados fora da UE, por exemplo nos EUA. As transferências baseiam-se em Cláusulas Contratuais-Tipo (SCCs) e/ou no EU-US Data Privacy Framework. Contacte-nos para obter uma cópia das salvaguardas aplicáveis.",
        ] },
      { heading: "Durante quanto tempo conservamos os dados", body: [
          "Conta e planos guardados: até os eliminar (sem caducidade automática).",
          "Sessões de início de sessão: até 30 dias (7 dias de inatividade), sendo depois removidas.",
          "Metadados de utilização de IA (incluindo a chave de IP temporária de um convidado): 45 dias.",
          "Cliques/feedback pseudonimizados e e-mails da lista de espera: 18 meses.",
          "Dados de limitação de pedidos: transitórios, em memória (minutos).",
          "Registos de erros (Sentry) e Google Analytics: de acordo com o período de conservação definido junto desses fornecedores (para o Analytics definimos a opção mais curta oferecida). Registos do servidor e cópias de segurança: de acordo com as predefinições do fornecedor de alojamento.",
        ] },
      { heading: "Os seus direitos e como exercê-los", body: [
          "Tem o direito de acesso, retificação, apagamento, limitação, oposição e portabilidade, bem como o direito de retirar o consentimento (por exemplo, para o Analytics através de «Definições de privacidade»).",
          "Pode eliminar a sua conta e todos os dados relacionados imediatamente na aplicação («Eliminar conta»): isto apaga a sua conta, os planos guardados, os registos de utilização de IA e as entradas da lista de espera associadas ao seu e-mail. Um registo técnico transitório pode reter brevemente uma entrada; o Stripe (se alguma vez for introduzido) e o Sentry conservam dados ao abrigo dos seus próprios termos.",
          `Se não conseguir aceder à sua conta ou pretender exercer outros direitos, contacte-nos através de ${OPERATOR.email}.`,
          "Tem o direito de apresentar uma reclamação junto de uma autoridade de controlo — na Croácia, a Agência de Proteção de Dados Pessoais (AZOP, azop.hr); caso contrário, a autoridade nacional de proteção de dados do seu país.",
        ] },
      { heading: "Alterações e contacto", body: [
          `Podemos atualizar esta política periodicamente; a data da última atualização é apresentada no topo. Para quaisquer questões de privacidade, escreva para ${OPERATOR.email}.`,
          "Esta é uma tradução fornecida para sua conveniência. Em caso de qualquer discrepância entre as versões linguísticas, prevalece a versão em inglês deste documento.",
        ] },
    ],
  },
  terms: {
    title: "Termos de Utilização",
    updated: "Última atualização: 2026-07-03",
    sections: [
      { heading: "Em que consiste o serviço", body: [
          "A BudgetSpace ajuda-o a planear a mobília de uma divisão dentro de um orçamento. Não somos uma loja e não vendemos produtos.",
          "Os preços e a disponibilidade são estimativas recolhidas junto de retalhistas e podem estar desatualizados — verifique sempre junto do retalhista antes de comprar.",
        ] },
      { heading: "Marcas registadas e independência", body: [
          "IKEA, JYSK, eBay e todos os outros nomes de retalhistas e produtos são marcas registadas dos respetivos titulares. Utilizamo-los apenas para identificar os produtos reais e as lojas que os vendem.",
          "A BudgetSpace é uma ferramenta de planeamento independente e não está afiliada, patrocinada nem apoiada por nenhum destes retalhistas. As ligações de afiliação, se adicionadas, serão claramente identificadas e nunca alterarão qual o produto que é melhor para si.",
        ] },
      { heading: "Assistente de IA", body: [
          "As sugestões do assistente de IA são estimativas para o ajudar a planear, não aconselhamento profissional. Verifique os preços, as dimensões e a disponibilidade junto do retalhista antes de comprar.",
        ] },
      { heading: "Conta", body: [
          "A gravação e o assistente de IA utilizam o início de sessão com a Google; é responsável pela segurança da sua conta.",
        ] },
      { heading: "Design Session", body: [
          "O serviço é atualmente totalmente gratuito (beta inicial) — todas as funcionalidades estão desbloqueadas. Não existe subscrição nem faturação recorrente. No futuro, planeamos uma cobrança única por «Design Session» (um único pagamento, sem renovação); o preço e os termos serão apresentados claramente antes de qualquer cobrança.",
        ] },
      { heading: "Utilização aceitável", body: [
          "Não abuse do serviço — sem sobrecarga automatizada nem tentativas de contornar os limites.",
        ] },
      { heading: "Ausência de garantia / responsabilidade", body: [
          "O serviço é fornecido «tal como está». Não garantimos a exatidão dos preços nem a disponibilidade e não somos responsáveis por decisões de compra tomadas com base nas nossas sugestões.",
        ] },
      { heading: "Alterações e legislação aplicável", body: [
          "Podemos atualizar estes termos periodicamente. Aplicam-se as leis da Croácia e da UE.",
          "Esta é uma tradução fornecida para sua conveniência. Em caso de qualquer discrepância entre as versões linguísticas, prevalece a versão em inglês deste documento.",
        ] },
    ],
  },
};

const NO: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Personvernerklæring",
    updated: "Sist oppdatert: 2026-07-14",
    sections: [
      { heading: "Hvem driver BudgetSpace", body: [
          `BudgetSpace er et gratis prosjekt (beta) som drives av ${OPERATOR.name} som privatperson (en fysisk person), behandlingsansvarlig for dine personopplysninger.`,
          `Adresse: ${OPERATOR.street}, ${OPERATOR.postalCity}, Kroatia. Kontakt: ${OPERATOR.email}.`,
        ] },
      { heading: "Hva vi behandler og hvorfor", body: [
          "Google-innlogging: ditt navn, din e-postadresse, ditt profilbilde og din Google-id (sub) — slik at du har en konto og dine lagrede planer er dine.",
          "Planer og inndata du lagrer: rom, budsjett, stil, teksten du skriver (din beskrivelse) og by — for å bygge og forbedre planen.",
          "Et omtrentlig land fra CDN-headeren — kun for å velge riktig marked og valuta.",
          "Din IP-adresse — midlertidig, for hastighetsbegrensning og sikkerhet (se «IP-adresser og sikkerhet»).",
          "Pseudonyme brukssignaler: hvilket produkt du klikket på og tilbakemelding på planen — knyttet til en plan, ikke til navnet ditt.",
          "Google Analytics (kun hvis du godtar det) — for å forstå hvordan appen brukes (se det egne avsnittet).",
          "Teksten du skriver, når AI er aktivert, sendes til AI-leverandøren for å tolke forespørselen din (se «AI-assistent»).",
          "En venteliste-e-post — kun hvis du velger å legge igjen en.",
        ] },
      { heading: "Rettslig grunnlag (Art. 6 GDPR)", body: [
          "Avtale (Art. 6(1)(b)): drift av kontoen din, lagring av planer og behandling av forespørselen din på dine vegne.",
          "Berettigede interesser (Art. 6(1)(f)): sikkerhet og forebygging av misbruk, overvåking av tekniske feil og pseudonym klikk-/tilbakemeldingsanalyse for å forbedre tjenesten.",
          "Samtykke (Art. 6(1)(a)): Google Analytics. Du kan trekke tilbake samtykket når som helst.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics er valgfritt og kjører kun etter at du uttrykkelig har godtatt det. Inntil du bestemmer deg, lastes det ikke inn og sender ingen data.",
          "Hvis du avslår, fungerer BudgetSpace nøyaktig likt — du mister ingenting. Du kan endre valget ditt når som helst via «Personverninnstillinger» i bunnteksten.",
          "Når det godtas, kan Google Analytics behandle nettidentifikatorer, informasjon om enhet/nettleser, omtrentlig plassering og brukshendelser. Vi bruker det til å forstå hvordan appen brukes, hvor brukere står fast og hvilke funksjoner som er nyttige.",
          "Informasjonskapsler det da setter: _ga og _ga_<id> (måleidentifikatorer). De settes kun etter godkjenning; hvis du trekker tilbake samtykket, fjerner vi dem så langt det er teknisk mulig.",
          "Vi bruker ikke Google Ads, remarketing eller annonsepersonalisering. Vi hevder ikke at Google Analytics-data er fullstendig anonyme.",
        ] },
      { heading: "Informasjonskapsler og lokal lagring", body: [
          "Nødvendige: bs_auth — innloggingskapselen (inntil 30 dager); bs_oauth — en kortlivet (10 min) sikkerhetskapsel under innlogging.",
          "Nødvendige (lokal lagring): bs-session-id (en id per nettleser for AI-bruksgrenser og for å knytte sammen planene dine), budgetspace.market / budgetspace.lang (land og språk), budgetspace.plannerDraft / budgetspace.moveInDraft (utkast til inndata) og budgetspace.consent (ditt analysevalg).",
          "Valgfrie: Google Analytics-kapsler — kun etter at du har godtatt dem.",
          "Vi bruker ingen informasjonskapsler for annonsering eller remarketing.",
        ] },
      { heading: "Konto og Google-innlogging", body: [
          "Innlogging skjer via Google (OAuth). Fra Google mottar og lagrer vi ditt navn, din e-postadresse, ditt profilbilde og din Google-id; vi lagrer ikke ditt Google-passord eller dine Google-tokens.",
          "Profildata (navn, bilde) oppdateres ved hver Google-innlogging.",
        ] },
      { heading: "Inndata, lagrede og delte planer", body: [
          "Når du lagrer en plan, lagres tekstbeskrivelsen din (forespørselen) og byen du skrev inn sammen med den. Ikke legg inn sensitive personopplysninger i beskrivelsen.",
          "En lagret plan har en privat, vanskelig å gjette lenke. Alle som mottar den lenken kan åpne planen og se produktene, budsjettet, rommet og teksten som er lagret med den (din beskrivelse og by) — men ikke navnet eller e-postadressen din. En lenke er ikke «privat» bare fordi den er vanskelig å gjette.",
          "Deling oppheves ved å slette planen i appen — det er den eneste måten å få lenken til å slutte å fungere på. Lagrede planer har ingen automatisk utløpstid.",
        ] },
      { heading: "AI-assistent", body: [
          "BudgetSpace kan bruke AI til å tolke din fritekstforespørsel. Når AI er utilgjengelig eller deaktivert, brukes en deterministisk (regelbasert) reserveløsning — ikke hver plan genereres av AI.",
          "Når den brukes, er produksjonsleverandøren Google Gemini. Vi sender tekstbeskrivelsen din pluss kontekst (rom, budsjett, stil) — vi sender ikke navnet ditt, e-postadressen din eller konto-id-en din.",
          "BudgetSpace lagrer ikke forespørselen din eller AI-svaret utover det du velger å lagre i en plan. Google, som databehandler, håndterer teksten under sine egne vilkår og databehandleravtale.",
          "AI-forslag er estimater — sjekk pris, mål og tilgjengelighet med forhandleren før du kjøper. Ikke legg inn sensitive personopplysninger.",
        ] },
      { heading: "IP-adresser, sikkerhet og forebygging av misbruk", body: [
          "Vi behandler IP-adressen din midlertidig for hastighetsbegrensning og sikkerhet; den holdes i serverminnet og lagres ikke i profilen din.",
          "Når AI-assistenten er aktivert, kan en gjesteenhets IP oppbevares i inntil 45 dager som en nøkkel for daglig bruksgrense (innloggede brukere bruker en kontonøkkel i stedet).",
          "Serveren vår og hostingleverandøren kan registrere IP-adresser i sikkerhets-/tekniske logger under sine standardinnstillinger.",
        ] },
      { heading: "Feilovervåking (Sentry)", body: [
          "Vi sender tekniske feil til Sentry for å rette opp feil. Kun feilmeldinger og stakksporinger sendes; sending av personopplysninger er deaktivert (sendDefaultPii=false), slik at innholdet i forespørslene dine, navnet ditt og e-postadressen din ikke sendes.",
        ] },
      { heading: "Hosting, database og mottakere", body: [
          "Appen hostes hos en skyleverandør (f.eks. Railway/Render/Fly) med en PostgreSQL-database.",
          "Google — innlogging (Google Sign-In) og, når AI er aktivert, behandling av forespørsler (Google Gemini). Sentry — feilovervåking.",
          "Stripe — fakturering for Design Session; for øyeblikket inaktiv (gratis beta), så ingen betalingsdata behandles i dag.",
          "Vi selger ikke dataene dine. eBay er ikke mottaker av dataene dine — vi henter offentlige oppføringer etter kategori og marked og sender eBay ingen av dine personopplysninger.",
        ] },
      { heading: "Internasjonale overføringer", body: [
          "Noen leverandører (Google, Sentry, Stripe) kan behandle data utenfor EU, f.eks. i USA. Overføringer baseres på Standard Contractual Clauses (SCCs) og/eller EU-US Data Privacy Framework. Kontakt oss for en kopi av de relevante garantiene.",
        ] },
      { heading: "Hvor lenge vi oppbevarer data", body: [
          "Konto og lagrede planer: til du sletter dem (ingen automatisk utløpstid).",
          "Innloggingsøkter: inntil 30 dager (7 dagers inaktivitet), deretter fjernet.",
          "AI-bruksmetadata (inkludert en gjests midlertidige IP-nøkkel): 45 dager.",
          "Pseudonyme klikk/tilbakemeldinger og venteliste-e-poster: 18 måneder.",
          "Data for hastighetsbegrensning: forbigående, i minnet (minutter).",
          "Feillogger (Sentry) og Google Analytics: i henhold til oppbevaringstiden som er angitt hos disse leverandørene (for Analytics angir vi det korteste alternativet som tilbys). Serverlogger og sikkerhetskopier: i henhold til hostingleverandørens standardinnstillinger.",
        ] },
      { heading: "Dine rettigheter og hvordan du bruker dem", body: [
          "Du har rett til innsyn, retting, sletting, begrensning, innsigelse og dataportabilitet, samt rett til å trekke tilbake samtykke (f.eks. for Analytics via «Personverninnstillinger»).",
          "Du kan slette kontoen din og alle tilknyttede data umiddelbart i appen («Slett konto»): dette sletter kontoen din, lagrede planer, AI-bruksregistreringer og venteliste-oppføringene knyttet til e-postadressen din. En forbigående teknisk logg kan kort beholde en oppføring; Stripe (hvis noen gang innført) og Sentry oppbevarer data under sine egne vilkår.",
          `Hvis du ikke får tilgang til kontoen din eller ønsker å utøve andre rettigheter, kontakt oss på ${OPERATOR.email}.`,
          "Du har rett til å klage til en tilsynsmyndighet — i Kroatia, Personopplysningsvernbyrået (AZOP, azop.hr); ellers din nasjonale datatilsynsmyndighet.",
        ] },
      { heading: "Endringer og kontakt", body: [
          `Vi kan oppdatere denne erklæringen fra tid til annen; datoen for siste oppdatering vises øverst. For eventuelle personvernspørsmål, skriv til ${OPERATOR.email}.`,
          "Dette er en oversettelse som tilbys for din bekvemmelighet. Ved eventuelt avvik mellom språkversjonene, går den engelske versjonen av dette dokumentet foran.",
        ] },
    ],
  },
  terms: {
    title: "Bruksvilkår",
    updated: "Sist oppdatert: 2026-07-03",
    sections: [
      { heading: "Hva tjenesten er", body: [
          "BudgetSpace hjelper deg med å planlegge møblering av et rom innenfor et budsjett. Vi er ikke en butikk og selger ikke produkter.",
          "Priser og tilgjengelighet er estimater samlet inn fra forhandlere og kan være utdaterte — sjekk alltid med forhandleren før du kjøper.",
        ] },
      { heading: "Varemerker og uavhengighet", body: [
          "IKEA, JYSK, eBay og alle andre forhandler- og produktnavn er varemerker som tilhører sine respektive eiere. Vi bruker dem kun for å identifisere de virkelige produktene og butikkene som selger dem.",
          "BudgetSpace er et uavhengig planleggingsverktøy og er ikke tilknyttet, godkjent av eller sponset av noen av disse forhandlerne. Affiliate-lenker, hvis de legges til, vil være tydelig merket og vil aldri endre hvilket produkt som er best for deg.",
        ] },
      { heading: "AI-assistent", body: [
          "AI-assistentens forslag er estimater for å hjelpe deg med å planlegge, ikke profesjonell rådgivning. Sjekk priser, mål og tilgjengelighet med forhandleren før du kjøper.",
        ] },
      { heading: "Konto", body: [
          "Lagring og AI-assistenten bruker Google-innlogging; du er ansvarlig for sikkerheten til kontoen din.",
        ] },
      { heading: "Design Session", body: [
          "Tjenesten er for øyeblikket helt gratis (tidlig beta) — alle funksjoner er låst opp. Det finnes ingen abonnement og ingen løpende fakturering. I fremtiden planlegger vi en engangsbetaling per «Design Session» (en enkelt betaling, ingen fornyelse); prisen og vilkårene vil vises tydelig før eventuell betaling.",
        ] },
      { heading: "Akseptabel bruk", body: [
          "Ikke misbruk tjenesten — ingen automatisert overbelastning eller forsøk på å omgå grenser.",
        ] },
      { heading: "Ingen garanti / ansvar", body: [
          "Tjenesten leveres «som den er». Vi garanterer ikke prisnøyaktighet eller tilgjengelighet og er ikke ansvarlige for kjøpsbeslutninger tatt på grunnlag av våre forslag.",
        ] },
      { heading: "Endringer og gjeldende lov", body: [
          "Vi kan oppdatere disse vilkårene fra tid til annen. Lovene i Kroatia og EU gjelder.",
          "Dette er en oversettelse som tilbys for din bekvemmelighet. Ved eventuelt avvik mellom språkversjonene, går den engelske versjonen av dette dokumentet foran.",
        ] },
    ],
  },
};

const SV: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Integritetspolicy",
    updated: "Senast uppdaterad: 2026-07-14",
    sections: [
      { heading: "Vem som driver BudgetSpace", body: [
          `BudgetSpace är ett gratis projekt (beta) som drivs av ${OPERATOR.name} som privatperson (en fysisk person), personuppgiftsansvarig för dina personuppgifter.`,
          `Adress: ${OPERATOR.street}, ${OPERATOR.postalCity}, Kroatien. Kontakt: ${OPERATOR.email}.`,
        ] },
      { heading: "Vad vi behandlar och varför", body: [
          "Google-inloggning: ditt namn, din e-postadress, din profilbild och ditt Google-id (sub) — så att du har ett konto och att dina sparade planer är dina.",
          "Planer och uppgifter du sparar: rum, budget, stil, texten du skriver (din beskrivning) och stad — för att bygga och förfina planen.",
          "Ett ungefärligt land från CDN-huvudet — enbart för att välja rätt marknad och valuta.",
          "Din IP-adress — tillfälligt, för hastighetsbegränsning och säkerhet (se \"IP-adresser och säkerhet\").",
          "Pseudonymiserade användningssignaler: vilken produkt du klickade på och feedback på planen — kopplat till en plan, inte till ditt namn.",
          "Google Analytics (endast om du godkänner) — för att förstå hur appen används (se det särskilda avsnittet).",
          "Texten du skriver skickas, när AI är aktiverat, till AI-leverantören för att tolka din förfrågan (se \"AI-assistent\").",
          "En e-postadress för väntelista — endast om du väljer att lämna en.",
        ] },
      { heading: "Rättslig grund (Art. 6 GDPR)", body: [
          "Avtal (Art. 6(1)(b)): att driva ditt konto, spara planer och behandla din förfrågan för din räkning.",
          "Berättigade intressen (Art. 6(1)(f)): säkerhet och förhindrande av missbruk, teknisk felövervakning och pseudonymiserad klick-/feedbackanalys för att förbättra tjänsten.",
          "Samtycke (Art. 6(1)(a)): Google Analytics. Du kan återkalla ditt samtycke när som helst.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics är valfritt och körs endast efter att du uttryckligen godkänt det. Tills du bestämmer dig laddas det inte och skickar inga data.",
          "Om du avböjer fungerar BudgetSpace exakt likadant — du förlorar ingenting. Du kan ändra ditt val när som helst via \"Integritetsinställningar\" i sidfoten.",
          "När det godkänts kan Google Analytics behandla onlineidentifierare, information om enhet/webbläsare, ungefärlig plats och användningshändelser. Vi använder det för att förstå hur appen används, var användare fastnar och vilka funktioner som är användbara.",
          "Cookies som det då sätter: _ga och _ga_<id> (mätidentifierare). De sätts endast efter godkännande; om du återkallar ditt samtycke tar vi bort dem så långt det är tekniskt möjligt.",
          "Vi använder inte Google Ads, remarketing eller annonspersonalisering. Vi påstår inte att Google Analytics-data är helt anonyma.",
        ] },
      { heading: "Cookies och lokal lagring", body: [
          "Nödvändiga: bs_auth — inloggningscookien (upp till 30 dagar); bs_oauth — en kortlivad (10 min) säkerhetscookie under inloggning.",
          "Nödvändiga (lokal lagring): bs-session-id (ett id per webbläsare för AI-användningsgränser och för att koppla dina planer), budgetspace.market / budgetspace.lang (land och språk), budgetspace.plannerDraft / budgetspace.moveInDraft (utkast till inmatning) och budgetspace.consent (ditt analysval).",
          "Valfria: Google Analytics-cookies — endast efter att du godkänt dem.",
          "Vi använder inga annons- eller remarketingcookies.",
        ] },
      { heading: "Konto och Google-inloggning", body: [
          "Inloggning sker via Google (OAuth). Från Google tar vi emot och lagrar ditt namn, din e-postadress, din profilbild och ditt Google-id; vi lagrar inte ditt Google-lösenord eller dina Google-tokens.",
          "Profildata (namn, bild) uppdateras vid varje Google-inloggning.",
        ] },
      { heading: "Inmatning, sparade och delade planer", body: [
          "När du sparar en plan sparas din textbeskrivning (prompt) och staden du angav tillsammans med den. Lägg inte in känsliga personuppgifter i beskrivningen.",
          "En sparad plan har en privat, svårgissad länk. Alla som får den länken kan öppna planen och se produkterna, budgeten, rummet och texten som sparats med den (din beskrivning och stad) — men inte ditt namn eller din e-postadress. En länk är inte \"privat\" bara för att den är svår att gissa.",
          "Delning återkallas genom att radera planen i appen — det är det enda sättet att få länken att sluta fungera. Sparade planer har ingen automatisk utgång.",
        ] },
      { heading: "AI-assistent", body: [
          "BudgetSpace kan använda AI för att tolka din fritextförfrågan. När AI är otillgängligt eller inaktiverat används en deterministisk (regelbaserad) reservlösning — inte varje plan genereras av AI.",
          "När det används är produktionsleverantören Google Gemini. Vi skickar din textbeskrivning plus kontext (rum, budget, stil) — vi skickar inte ditt namn, din e-postadress eller ditt konto-id.",
          "BudgetSpace lagrar inte din prompt eller AI-svaret utöver vad du väljer att spara i en plan. Google, som personuppgiftsbiträde, hanterar texten enligt sina egna villkor och sitt personuppgiftsbiträdesavtal.",
          "AI-förslag är uppskattningar — kontrollera priset, måtten och tillgängligheten hos återförsäljaren innan du köper. Ange inte känsliga personuppgifter.",
        ] },
      { heading: "IP-adresser, säkerhet och förhindrande av missbruk", body: [
          "Vi behandlar din IP-adress tillfälligt för hastighetsbegränsning och säkerhet; den hålls i serverns minne och lagras inte i din profil.",
          "När AI-assistenten är aktiverad kan en gästenhets IP-adress behållas i upp till 45 dagar som nyckel för daglig användningsgräns (inloggade användare använder i stället en kontonyckel).",
          "Vår server och vår värdleverantör kan registrera IP-adresser i säkerhets-/tekniska loggar enligt sina standardinställningar.",
        ] },
      { heading: "Felövervakning (Sentry)", body: [
          "Vi skickar tekniska fel till Sentry för att åtgärda brister. Endast felmeddelanden och stackspårningar skickas; sändning av personuppgifter är inaktiverad (sendDefaultPii=false), så innehållet i dina prompter, ditt namn och din e-postadress skickas inte.",
        ] },
      { heading: "Värdtjänst, databas och mottagare", body: [
          "Appen driftas hos en molnleverantör (t.ex. Railway/Render/Fly) med en PostgreSQL-databas.",
          "Google — inloggning (Google Sign-In) och, när AI är aktiverat, behandling av förfrågningar (Google Gemini). Sentry — felövervakning.",
          "Stripe — fakturering för Design Session; för närvarande inaktiv (gratis beta), så inga betalningsuppgifter behandlas i dag.",
          "Vi säljer inte dina data. eBay är inte mottagare av dina data — vi hämtar offentliga annonser efter kategori och marknad och skickar inga av dina personuppgifter till eBay.",
        ] },
      { heading: "Internationella överföringar", body: [
          "Vissa leverantörer (Google, Sentry, Stripe) kan behandla data utanför EU, t.ex. i USA. Överföringar bygger på Standard Contractual Clauses (SCCs) och/eller EU-US Data Privacy Framework. Kontakta oss för en kopia av de relevanta skyddsåtgärderna.",
        ] },
      { heading: "Hur länge vi behåller data", body: [
          "Konto och sparade planer: tills du raderar dem (ingen automatisk utgång).",
          "Inloggningssessioner: upp till 30 dagar (7 dagars inaktivitet), sedan gallras de bort.",
          "Metadata om AI-användning (inklusive en gästs tillfälliga IP-nyckel): 45 dagar.",
          "Pseudonymiserade klick/feedback och e-postadresser för väntelista: 18 månader.",
          "Data för hastighetsbegränsning: tillfälliga, i minnet (minuter).",
          "Felloggar (Sentry) och Google Analytics: enligt den lagringstid som ställts in hos dessa leverantörer (för Analytics väljer vi det kortaste alternativ som erbjuds). Serverloggar och säkerhetskopior: enligt värdleverantörens standardinställningar.",
        ] },
      { heading: "Dina rättigheter och hur du använder dem", body: [
          "Du har rätt till tillgång, rättelse, radering, begränsning, invändning och dataportabilitet samt rätt att återkalla samtycke (t.ex. för Analytics via \"Integritetsinställningar\").",
          "Du kan radera ditt konto och alla tillhörande data omedelbart i appen (\"Radera konto\"): detta raderar ditt konto, dina sparade planer, dina AI-användningsposter och de väntelisteposter som är kopplade till din e-postadress. En tillfällig teknisk logg kan kortvarigt behålla en post; Stripe (om det någonsin införs) och Sentry behåller data enligt sina egna villkor.",
          `Om du inte kan komma åt ditt konto eller vill utöva andra rättigheter, kontakta oss på ${OPERATOR.email}.`,
          "Du har rätt att lämna in ett klagomål till en tillsynsmyndighet — i Kroatien Byrån för skydd av personuppgifter (AZOP, azop.hr); i annat fall din nationella dataskyddsmyndighet.",
        ] },
      { heading: "Ändringar och kontakt", body: [
          `Vi kan uppdatera denna policy då och då; datumet för senaste uppdatering visas överst. För eventuella integritetsfrågor, skriv till ${OPERATOR.email}.`,
          "Detta är en översättning som tillhandahålls för din bekvämlighet. Om det finns någon avvikelse mellan språkversionerna har den engelska versionen av detta dokument företräde.",
        ] },
    ],
  },
  terms: {
    title: "Användarvillkor",
    updated: "Senast uppdaterad: 2026-07-03",
    sections: [
      { heading: "Vad tjänsten är", body: [
          "BudgetSpace hjälper dig att planera möbleringen av ett rum inom en budget. Vi är inte en butik och säljer inga produkter.",
          "Priser och tillgänglighet är uppskattningar som samlats in från återförsäljare och kan vara inaktuella — kontrollera alltid med återförsäljaren innan du köper.",
        ] },
      { heading: "Varumärken och oberoende", body: [
          "IKEA, JYSK, eBay och alla andra återförsäljar- och produktnamn är varumärken som tillhör respektive ägare. Vi använder dem endast för att identifiera de verkliga produkterna och de butiker som säljer dem.",
          "BudgetSpace är ett oberoende planeringsverktyg och är inte anslutet till, rekommenderat av eller sponsrat av någon av dessa återförsäljare. Affiliatelänkar kommer, om de läggs till, att tydligt markeras och kommer aldrig att ändra vilken produkt som är bäst för dig.",
        ] },
      { heading: "AI-assistent", body: [
          "AI-assistentens förslag är uppskattningar för att hjälpa dig att planera, inte professionell rådgivning. Kontrollera priser, mått och tillgänglighet hos återförsäljaren innan du köper.",
        ] },
      { heading: "Konto", body: [
          "Sparande och AI-assistenten använder Google-inloggning; du ansvarar för säkerheten för ditt konto.",
        ] },
      { heading: "Design Session", body: [
          "Tjänsten är för närvarande helt gratis (tidig beta) — alla funktioner är upplåsta. Det finns ingen prenumeration och ingen återkommande fakturering. I framtiden planerar vi en engångsavgift per \"Design Session\" (en enda betalning, ingen förnyelse); priset och villkoren kommer att visas tydligt före eventuell debitering.",
        ] },
      { heading: "Godtagbar användning", body: [
          "Missbruka inte tjänsten — ingen automatiserad överbelastning eller försök att kringgå gränser.",
        ] },
      { heading: "Ingen garanti / ansvar", body: [
          "Tjänsten tillhandahålls \"i befintligt skick\". Vi garanterar inte prisriktighet eller tillgänglighet och ansvarar inte för köpbeslut som fattats utifrån våra förslag.",
        ] },
      { heading: "Ändringar och tillämplig lag", body: [
          "Vi kan uppdatera dessa villkor då och då. Kroatiens och EU:s lagar gäller.",
          "Detta är en översättning som tillhandahålls för din bekvämlighet. Om det finns någon avvikelse mellan språkversionerna har den engelska versionen av detta dokument företräde.",
        ] },
    ],
  },
};

const DA: Partial<Record<LegalKey, LegalDoc>> = {
  privacy: {
    title: "Privatlivspolitik",
    updated: "Sidst opdateret: 2026-07-14",
    sections: [
      { heading: "Hvem driver BudgetSpace", body: [
          `BudgetSpace er et gratis projekt (beta), der drives af ${OPERATOR.name} som privatperson (en fysisk person), den dataansvarlige for dine personoplysninger.`,
          `Adresse: ${OPERATOR.street}, ${OPERATOR.postalCity}, Kroatien. Kontakt: ${OPERATOR.email}.`,
        ] },
      { heading: "Hvad vi behandler, og hvorfor", body: [
          "Google-login: dit navn, din e-mail, dit profilbillede og dit Google-id (sub) — så du har en konto, og dine gemte planer er dine.",
          "Planer og input, du gemmer: rum, budget, stil, den tekst, du skriver (din beskrivelse), og by — for at opbygge og forfine planen.",
          "Et omtrentligt land fra CDN-headeren — kun for at vælge det rette marked og den rette valuta.",
          "Din IP-adresse — midlertidigt, til hastighedsbegrænsning og sikkerhed (se \"IP-adresser og sikkerhed\").",
          "Pseudonyme brugssignaler: hvilket produkt du klikkede på, og feedback på planen — knyttet til en plan, ikke til dit navn.",
          "Google Analytics (kun hvis du accepterer) — for at forstå, hvordan appen bruges (se det særskilte afsnit).",
          "Den tekst, du skriver, sendes — når AI er aktiveret — til AI-udbyderen for at fortolke din anmodning (se \"AI-assistent\").",
          "En e-mail til ventelisten — kun hvis du vælger at efterlade en.",
        ] },
      { heading: "Retsgrundlag (Art. 6 GDPR)", body: [
          "Kontrakt (Art. 6(1)(b)): drift af din konto, lagring af planer og behandling af din anmodning på dine vegne.",
          "Legitime interesser (Art. 6(1)(f)): sikkerhed og forebyggelse af misbrug, teknisk fejlovervågning og pseudonym klik-/feedbackanalyse for at forbedre tjenesten.",
          "Samtykke (Art. 6(1)(a)): Google Analytics. Du kan til enhver tid trække dit samtykke tilbage.",
        ] },
      { heading: "Google Analytics", body: [
          "Google Analytics er valgfrit og kører kun, efter du udtrykkeligt har accepteret det. Indtil du beslutter dig, indlæses det ikke og sender ingen data.",
          "Hvis du afviser, fungerer BudgetSpace nøjagtig på samme måde — du mister intet. Du kan til enhver tid ændre dit valg via \"Privatlivsindstillinger\" i sidefoden.",
          "Når det accepteres, kan Google Analytics behandle onlineidentifikatorer, enheds-/browseroplysninger, omtrentlig placering og brugshændelser. Vi bruger det til at forstå, hvordan appen bruges, hvor brugerne sidder fast, og hvilke funktioner der er nyttige.",
          "Cookies, som det derefter sætter: _ga og _ga_<id> (måleidentifikatorer). De sættes først efter accept; hvis du trækker dit samtykke tilbage, fjerner vi dem, så vidt det er teknisk muligt.",
          "Vi bruger ikke Google Ads, remarketing eller annoncepersonalisering. Vi hævder ikke, at Google Analytics-data er fuldstændig anonyme.",
        ] },
      { heading: "Cookies og lokal lagring", body: [
          "Nødvendige: bs_auth — login-cookien (op til 30 dage); bs_oauth — en kortvarig (10 min.) sikkerhedscookie under login.",
          "Nødvendige (lokal lagring): bs-session-id (et id pr. browser til AI-forbrugsgrænser og sammenkædning af dine planer), budgetspace.market / budgetspace.lang (land og sprog), budgetspace.plannerDraft / budgetspace.moveInDraft (udkast til input) og budgetspace.consent (dit analysevalg).",
          "Valgfri: Google Analytics-cookies — først efter du har accepteret dem.",
          "Vi bruger ingen reklame- eller remarketingcookies.",
        ] },
      { heading: "Konto og Google-login", body: [
          "Login foregår via Google (OAuth). Fra Google modtager og gemmer vi dit navn, din e-mail, dit profilbillede og dit Google-id; vi gemmer ikke din Google-adgangskode eller dine Google-tokens.",
          "Profildata (navn, billede) opdateres ved hvert Google-login.",
        ] },
      { heading: "Input, gemte og delte planer", body: [
          "Når du gemmer en plan, gemmes din tekstbeskrivelse (prompt) og den by, du indtastede, sammen med den. Anfør ikke følsomme personoplysninger i beskrivelsen.",
          "En gemt plan har et privat link, der er svært at gætte. Enhver, der modtager dette link, kan åbne planen og se produkterne, budgettet, rummet og den tekst, der er gemt sammen med den (din beskrivelse og by) — men ikke dit navn eller din e-mail. Et link er ikke \"privat\", blot fordi det er svært at gætte.",
          "Deling ophæves ved at slette planen i appen — det er den eneste måde at få linket til at holde op med at fungere på. Gemte planer udløber ikke automatisk.",
        ] },
      { heading: "AI-assistent", body: [
          "BudgetSpace kan bruge AI til at fortolke din fritekstanmodning. Når AI ikke er tilgængelig eller er deaktiveret, anvendes en deterministisk (regelbaseret) reserveløsning — ikke alle planer genereres af AI.",
          "Når det anvendes, er produktionsudbyderen Google Gemini. Vi sender din tekstbeskrivelse plus kontekst (rum, budget, stil) — vi sender ikke dit navn, din e-mail eller dit konto-id.",
          "BudgetSpace gemmer ikke din prompt eller AI-svaret ud over det, du vælger at gemme i en plan. Google behandler som databehandler teksten i henhold til sine egne vilkår og databehandleraftale.",
          "AI-forslag er skøn — kontrollér prisen, målene og tilgængeligheden hos forhandleren, før du køber. Indtast ikke følsomme personoplysninger.",
        ] },
      { heading: "IP-adresser, sikkerhed og forebyggelse af misbrug", body: [
          "Vi behandler din IP-adresse midlertidigt til hastighedsbegrænsning og sikkerhed; den opbevares i serverens hukommelse og gemmes ikke i din profil.",
          "Når AI-assistenten er aktiveret, kan en gæsteenheds IP opbevares i op til 45 dage som en daglig forbrugsgrænsenøgle (loggede brugere anvender i stedet en kontonøgle).",
          "Vores server og hostingudbyder kan registrere IP-adresser i sikkerheds-/tekniske logfiler i henhold til deres standardindstillinger.",
        ] },
      { heading: "Fejlovervågning (Sentry)", body: [
          "Vi sender tekniske fejl til Sentry for at rette fejl. Kun fejlmeddelelser og stack traces sendes; afsendelse af personoplysninger er deaktiveret (sendDefaultPii=false), så indholdet af dine prompts, dit navn og din e-mail sendes ikke.",
        ] },
      { heading: "Hosting, database og modtagere", body: [
          "Appen hostes hos en cloududbyder (f.eks. Railway/Render/Fly) med en PostgreSQL-database.",
          "Google — login (Google Sign-In) og, når AI er aktiveret, behandling af anmodninger (Google Gemini). Sentry — fejlovervågning.",
          "Stripe — fakturering af Design Session; i øjeblikket inaktiv (gratis beta), så der behandles ingen betalingsdata i dag.",
          "Vi sælger ikke dine data. eBay er ikke modtager af dine data — vi henter offentlige annoncer efter kategori og marked og sender ingen af dine personoplysninger til eBay.",
        ] },
      { heading: "Internationale overførsler", body: [
          "Nogle udbydere (Google, Sentry, Stripe) kan behandle data uden for EU, f.eks. i USA. Overførsler er baseret på Standard Contractual Clauses (SCCs) og/eller EU-US Data Privacy Framework. Kontakt os for at få en kopi af de relevante garantier.",
        ] },
      { heading: "Hvor længe vi opbevarer data", body: [
          "Konto og gemte planer: indtil du sletter dem (intet automatisk udløb).",
          "Login-sessioner: op til 30 dage (7 dages inaktivitet), derefter fjernet.",
          "AI-forbrugsmetadata (herunder en gæsts midlertidige IP-nøgle): 45 dage.",
          "Pseudonyme klik/feedback og e-mails til ventelisten: 18 måneder.",
          "Hastighedsbegrænsningsdata: flygtige, i hukommelsen (minutter).",
          "Fejllogfiler (Sentry) og Google Analytics: i henhold til den opbevaring, der er fastsat hos disse udbydere (for Analytics vælger vi den korteste mulighed, der tilbydes). Serverlogfiler og backups: i henhold til hostingudbyderens standardindstillinger.",
        ] },
      { heading: "Dine rettigheder, og hvordan du bruger dem", body: [
          "Du har ret til indsigt, berigtigelse, sletning, begrænsning, indsigelse og dataportabilitet samt ret til at trække dit samtykke tilbage (f.eks. for Analytics via \"Privatlivsindstillinger\").",
          "Du kan slette din konto og alle relaterede data med det samme i appen (\"Slet konto\"): dette sletter din konto, gemte planer, AI-forbrugsregistreringer og de ventelisteposter, der er knyttet til din e-mail. En flygtig teknisk log kan kortvarigt bevare en post; Stripe (hvis det nogensinde indføres) og Sentry opbevarer data i henhold til deres egne vilkår.",
          `Hvis du ikke kan få adgang til din konto eller ønsker at udøve andre rettigheder, kan du kontakte os på ${OPERATOR.email}.`,
          "Du har ret til at indgive en klage til en tilsynsmyndighed — i Kroatien er det databeskyttelsesmyndigheden (AZOP, azop.hr); ellers din nationale databeskyttelsesmyndighed.",
        ] },
      { heading: "Ændringer og kontakt", body: [
          `Vi kan opdatere denne politik fra tid til anden; datoen for seneste opdatering vises øverst. Har du spørgsmål om privatliv, kan du skrive til ${OPERATOR.email}.`,
          "Dette er en oversættelse, der stilles til rådighed for din bekvemmelighed. Hvis der er uoverensstemmelse mellem sprogversionerne, har den engelske version af dette dokument forrang.",
        ] },
    ],
  },
  terms: {
    title: "Brugsvilkår",
    updated: "Sidst opdateret: 2026-07-03",
    sections: [
      { heading: "Hvad tjenesten er", body: [
          "BudgetSpace hjælper dig med at planlægge indretning af et rum inden for et budget. Vi er ikke en butik og sælger ikke produkter.",
          "Priser og tilgængelighed er skøn indsamlet fra forhandlere og kan være forældede — kontrollér altid hos forhandleren, før du køber.",
        ] },
      { heading: "Varemærker og uafhængighed", body: [
          "IKEA, JYSK, eBay og alle andre forhandler- og produktnavne er varemærker tilhørende deres respektive ejere. Vi bruger dem kun til at identificere de faktiske produkter og de butikker, der sælger dem.",
          "BudgetSpace er et uafhængigt planlægningsværktøj og er ikke tilknyttet, godkendt af eller sponsoreret af nogen af disse forhandlere. Affiliate-links vil, hvis de tilføjes, være tydeligt mærket og vil aldrig ændre, hvilket produkt der er bedst for dig.",
        ] },
      { heading: "AI-assistent", body: [
          "AI-assistentens forslag er skøn, der skal hjælpe dig med at planlægge, ikke professionel rådgivning. Kontrollér priser, mål og tilgængelighed hos forhandleren, før du køber.",
        ] },
      { heading: "Konto", body: [
          "Lagring og AI-assistenten bruger Google-login; du er ansvarlig for din kontos sikkerhed.",
        ] },
      { heading: "Design Session", body: [
          "Tjenesten er i øjeblikket helt gratis (tidlig beta) — alle funktioner er låst op. Der er intet abonnement og ingen tilbagevendende fakturering. I fremtiden planlægger vi en engangsbetaling pr. \"Design Session\" (en enkelt betaling, ingen fornyelse); prisen og vilkårene vil blive vist tydeligt før enhver betaling.",
        ] },
      { heading: "Acceptabel brug", body: [
          "Misbrug ikke tjenesten — ingen automatiseret overbelastning eller forsøg på at omgå grænser.",
        ] },
      { heading: "Ingen garanti / ansvar", body: [
          "Tjenesten leveres \"som den er\". Vi garanterer ikke prisnøjagtighed eller tilgængelighed og er ikke ansvarlige for købsbeslutninger truffet på baggrund af vores forslag.",
        ] },
      { heading: "Ændringer og gældende ret", body: [
          "Vi kan opdatere disse vilkår fra tid til anden. Kroatiens og EU's love gælder.",
          "Dette er en oversættelse, der stilles til rådighed for din bekvemmelighed. Hvis der er uoverensstemmelse mellem sprogversionerne, har den engelske version af dette dokument forrang.",
        ] },
    ],
  },
};

// Sprint 10.188: Privacy + Terms translated into all shipped locales (it/sl/fi/fr/nl/sk/es/pt/no/sv/da). These
// carry privacy + terms only; the Impressum falls back to English (per-key), which is fine since only DE/AT
// legally require a localized Impressum and both already have a full German set.
const DOCS: Partial<Record<string, Partial<Record<LegalKey, LegalDoc>>>> = {
  hr: HR, en: EN, de: DE,
  it: IT, sl: SL, fi: FI, fr: FR, nl: NL, sk: SK, es: ES, pt: PT, no: NO, sv: SV, da: DA,
};

export function legalDoc(lang: string, key: LegalKey): LegalDoc {
  return DOCS[lang]?.[key] ?? EN[key];
}
