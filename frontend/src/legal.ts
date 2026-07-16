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

export function legalDoc(lang: string, key: LegalKey): LegalDoc {
  if (lang === 'hr') return HR[key];
  if (lang === 'de') return DE[key];
  return EN[key];
}
