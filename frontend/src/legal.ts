// App-specific Privacy, Terms and Impressum, rendered by LegalModal. Croatian + English + German
// (DE/AT are shipped markets and are the strictest Impressum jurisdictions, so they get their own
// language rather than an English fallback). Other locales fall back to English.
//
// OWNER ACTION before a public launch:
//   1. Fill in OPERATOR.name below (a real natural-person name, or a registered obrt/d.o.o.). This is
//      legally required: a publicly-reachable service that processes personal data must identify its
//      operator (EU e-Commerce Directive Art. 5 + GDPR Art. 13). The name renders in the Impressum AND
//      as the data controller in the Privacy Policy.
//   2. Before CHARGING or before a business-like DE/AT launch, also fill OPERATOR.entity + OPERATOR.address
//      (registered trader + geographic address; add OIB/VAT). A postal address is NOT required for the
//      free HR beta — do not rush a home address online.
//   3. Have this text lawyer-reviewed before charging money. The substance is complete and honest, but a
//      one-off review is cheap insurance.

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
// Single source of truth for who runs BudgetSpace. Rendered into the Impressum and the Privacy
// "controller" line in every language, so it is filled in exactly once.
const OPERATOR = {
  // REQUIRED before public launch — a real full name (natural person) or registered entity.
  name: 'Bruno Pušić',
  // Optional now; REQUIRED before charging: registered trader (obrt/d.o.o.) + OIB/VAT.
  entity: '',
  // Optional now; REQUIRED before charging / business-like DE-AT launch: geographic (postal) address.
  address: '',
  email: 'budgetspace.ai@gmail.com',
};

// Identity lines for the Impressum, skipping the optional fields that aren't filled in yet.
function operatorLines(labels: { entity: string; address: string; email: string }): string[] {
  const lines = [OPERATOR.name];
  if (OPERATOR.entity) lines.push(`${labels.entity}: ${OPERATOR.entity}`);
  if (OPERATOR.address) lines.push(`${labels.address}: ${OPERATOR.address}`);
  lines.push(`${labels.email}: ${OPERATOR.email}`);
  return lines;
}

const HR: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Pravila privatnosti',
    updated: 'Zadnja izmjena: 03.07.2026.',
    sections: [
      { heading: 'Tko smo', body: [
        `BudgetSpace je besplatan projekt (beta) koji vodi ${OPERATOR.name}, voditelj obrade tvojih osobnih podataka. Kontakt: ${OPERATOR.email}.`,
      ] },
      { heading: 'Koje podatke prikupljamo', body: [
        'Ako se prijaviš Googleom: ime, e-mail i profilnu sliku koje nam Google proslijedi.',
        'Planove i unose (soba, budžet, stil) koje sam spremiš.',
        'Približnu državu iz CDN zaglavlja — samo da odaberemo ispravno tržište i valutu (ne spremamo tvoju IP adresu).',
        'Anonimne, pseudonimne signale o korištenju (npr. na koji si proizvod kliknuo, povratna ocjena plana) — vezani uz plan, ne uz tebe; koristimo ih da poboljšamo prijedloge.',
        'Ako u budućnosti platiš Design Session (planirana jednokratna kupnja — besplatno u beti): plaćanje obrađuje Stripe. Mi ne vidimo niti spremamo podatke o tvojoj kartici.',
        'Tekst koji upišeš AI asistentu šalje se davatelju AI usluge isključivo radi obrade tvog upita.',
      ] },
      { heading: 'Kolačići i lokalna pohrana', body: [
        'bs_auth — nužni kolačić za prijavu (bez njega prijava ne radi).',
        'bs_oauth — kratkotrajni (10 min) sigurnosni kolačić tijekom prijave.',
        'Lokalna pohrana (localStorage): identifikator preglednika (za ograničenja AI korištenja i povezivanje tvojih spremljenih planova), nacrti unosa te odabir jezika/tržišta — sve funkcionalno.',
        'Nemamo reklamne ni kolačiće za praćenje.',
      ] },
      { heading: 'Pravna osnova (čl. 6 GDPR)', body: [
        'Ugovor (čl. 6(1)(b)): vođenje računa, spremanje planova i obrada tvog AI upita na tvoj zahtjev.',
        'Legitimni interes (čl. 6(1)(f)): praćenje tehničkih grešaka i anonimna analitika klikova/povratnih ocjena radi poboljšanja usluge.',
        'Privola (čl. 6(1)(a)): e-mail za obavijest o padu cijene (Price Watch) — spremamo ga samo uz tvoju izričitu privolu i odjava je u svakoj poruci.',
      ] },
      { heading: 'S kim dijelimo podatke', body: [
        'Google — prijava (Google Sign-In) i obrada AI upita (Google Gemini).',
        'Stripe — naplata Design Sessiona, kad se uvede.',
        'Sentry — praćenje tehničkih grešaka (šaljemo samo dnevnike grešaka, ne sadržaj tvojih upita).',
        'Neki od ovih pružatelja obrađuju podatke u SAD-u; prijenos se temelji na EU-US Data Privacy Frameworku i/ili standardnim ugovornim klauzulama (SCC). Za kopiju nas kontaktiraj.',
        'Tvoje podatke ne prodajemo. eBay nije primatelj tvojih podataka — vidi „Rabljeni oglasi" niže.',
      ] },
      { heading: 'Rabljeni oglasi (eBay)', body: [
        'Kad prikazujemo rabljene oglase, dohvaćamo javne oglase s eBaya prema kategoriji i tržištu; ne šaljemo eBayu nijedan tvoj osobni podatak. Klik na oglas vodi te na eBay, gdje vrijede njihova pravila.',
      ] },
      { heading: 'Koliko dugo čuvamo', body: [
        'Dok ne obrišeš račun ili dok ti sesija ne istekne. Račun i sve povezane podatke možeš obrisati u aplikaciji ("Obriši račun").',
      ] },
      { heading: 'Tvoja prava (GDPR)', body: [
        'Imaš pravo na pristup, ispravak, brisanje, prenosivost i prigovor, te pravo na pritužbu nadzornom tijelu (u RH: AZOP).',
        'Brisanje računa dostupno je odmah u aplikaciji; za kopiju tvojih podataka (pristup i prenosivost) ili ostala prava kontaktiraj nas na ' + OPERATOR.email + '.',
        'Podaci profila (ime, slika) automatski se osvježe pri svakoj Google prijavi.',
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
    updated: 'Zadnja izmjena: 03.07.2026.',
    disclaimer: 'BudgetSpace je trenutno besplatan projekt (beta) — ne prodajemo robu ni usluge i ne naplaćujemo. Kad uvedemo plaćeni Design Session, ovdje će biti potpuni podaci poslovnog subjekta (OIB/PDV, adresa).',
    sections: [
      { heading: 'Pružatelj usluge', body: operatorLines({ entity: 'Subjekt', address: 'Adresa', email: 'E-mail' }) },
    ],
  },
};

const EN: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Privacy Policy',
    updated: 'Last updated: 2026-07-03',
    sections: [
      { heading: 'Who we are', body: [
        `BudgetSpace is a free project (beta) run by ${OPERATOR.name}, the controller of your personal data. Contact: ${OPERATOR.email}.`,
      ] },
      { heading: 'What we collect', body: [
        'If you sign in with Google: the name, email and profile picture Google passes to us.',
        'The plans and inputs (room, budget, style) you choose to save.',
        'An approximate country from the CDN header — only to pick the right market and currency (we do not store your IP address).',
        'Anonymous, pseudonymous usage signals (e.g. which product you clicked, plan feedback) — tied to a plan, not to you; used to improve the suggestions.',
        'If you pay for a Design Session in the future (a planned one-time purchase — free during beta): payment is handled by Stripe. We never see or store your card details.',
        'Text you type to the AI assistant is sent to the AI provider solely to process your request.',
      ] },
      { heading: 'Cookies and local storage', body: [
        'bs_auth — a strictly-necessary sign-in cookie (sign-in does not work without it).',
        'bs_oauth — a short-lived (10 min) security cookie used during sign-in.',
        'Local storage (localStorage): a per-browser identifier (for AI usage limits and to link your saved plans), input drafts, and your language/market choice — all functional.',
        'We use no advertising or tracking cookies.',
      ] },
      { heading: 'Legal basis (Art. 6 GDPR)', body: [
        'Contract (Art. 6(1)(b)): running your account, saving plans, and processing your AI prompt at your request.',
        'Legitimate interests (Art. 6(1)(f)): technical error monitoring and anonymous click/feedback analytics to improve the service.',
        'Consent (Art. 6(1)(a)): the price-drop alert email (Price Watch) — stored only with your explicit consent, with one-click unsubscribe in every message.',
      ] },
      { heading: 'Who we share with', body: [
        'Google — sign-in (Google Sign-In) and AI prompt processing (Google Gemini).',
        'Stripe — Design Session billing, once introduced.',
        'Sentry — technical error monitoring (we send only error logs, never the content of your prompts).',
        'Some of these providers process data in the US; transfers rely on the EU-US Data Privacy Framework and/or Standard Contractual Clauses (SCCs). Contact us for a copy.',
        'We do not sell your data. eBay is not a recipient of your data — see "Second-hand listings" below.',
      ] },
      { heading: 'Second-hand listings (eBay)', body: [
        'When we show second-hand listings we fetch public eBay listings by category and market; we send eBay none of your personal data. Clicking a listing takes you to eBay, where their terms apply.',
      ] },
      { heading: 'How long we keep it', body: [
        'Until you delete your account or your session expires. You can delete your account and all related data in the app ("Delete account").',
      ] },
      { heading: 'Your rights (GDPR)', body: [
        'You have the right to access, rectify, delete, port and object, and to lodge a complaint with a data-protection supervisory authority (in the EU, your national DPA).',
        'Account deletion is available immediately in the app; for a copy of your data (access & portability) or the other rights, contact us at ' + OPERATOR.email + '.',
        'Profile data (name, picture) is refreshed automatically on each Google sign-in.',
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
    updated: 'Last updated: 2026-07-03',
    disclaimer: 'BudgetSpace is currently a free project (beta) — we do not sell goods or services and we do not charge. Full business-entity details (VAT/registration, address) will appear here once a paid Design Session launches.',
    sections: [
      { heading: 'Service provider', body: operatorLines({ entity: 'Entity', address: 'Address', email: 'Email' }) },
    ],
  },
};

// German — DE/AT are shipped markets and the strictest Impressum jurisdictions (§5 DDG), so they get a
// full German set rather than the English fallback.
const DE: Record<LegalKey, LegalDoc> = {
  privacy: {
    title: 'Datenschutzerklärung',
    updated: 'Zuletzt aktualisiert: 03.07.2026',
    sections: [
      { heading: 'Wer wir sind', body: [
        `BudgetSpace ist ein kostenloses Projekt (Beta), betrieben von ${OPERATOR.name}, dem Verantwortlichen für Ihre personenbezogenen Daten. Kontakt: ${OPERATOR.email}.`,
      ] },
      { heading: 'Welche Daten wir erheben', body: [
        'Bei der Anmeldung mit Google: Name, E-Mail und Profilbild, die Google an uns übermittelt.',
        'Die Pläne und Eingaben (Raum, Budget, Stil), die Sie selbst speichern.',
        'Ein ungefähres Land aus dem CDN-Header — nur um den richtigen Markt und die Währung zu wählen (wir speichern Ihre IP-Adresse nicht).',
        'Anonyme, pseudonyme Nutzungssignale (z. B. welches Produkt Sie angeklickt haben, Plan-Feedback) — mit einem Plan verknüpft, nicht mit Ihnen; zur Verbesserung der Vorschläge.',
        'Falls Sie künftig eine Design Session bezahlen (geplanter Einmalkauf — in der Beta kostenlos): Die Zahlung wickelt Stripe ab. Ihre Kartendaten sehen oder speichern wir nie.',
        'Text, den Sie dem KI-Assistenten schreiben, wird ausschließlich zur Bearbeitung Ihrer Anfrage an den KI-Anbieter gesendet.',
      ] },
      { heading: 'Cookies und lokaler Speicher', body: [
        'bs_auth — ein technisch notwendiges Anmelde-Cookie (ohne es funktioniert die Anmeldung nicht).',
        'bs_oauth — ein kurzlebiges (10 Min.) Sicherheits-Cookie während der Anmeldung.',
        'Lokaler Speicher (localStorage): eine Browser-Kennung (für KI-Nutzungslimits und zur Zuordnung Ihrer gespeicherten Pläne), Eingabe-Entwürfe sowie Ihre Sprach-/Marktauswahl — alles funktional.',
        'Wir verwenden keine Werbe- oder Tracking-Cookies.',
      ] },
      { heading: 'Rechtsgrundlage (Art. 6 DSGVO)', body: [
        'Vertrag (Art. 6 Abs. 1 lit. b): Betrieb Ihres Kontos, Speichern von Plänen und Verarbeitung Ihrer KI-Anfrage auf Ihren Wunsch.',
        'Berechtigtes Interesse (Art. 6 Abs. 1 lit. f): technische Fehlerüberwachung und anonyme Klick-/Feedback-Analyse zur Verbesserung des Dienstes.',
        'Einwilligung (Art. 6 Abs. 1 lit. a): die E-Mail für Preis-Alarm (Price Watch) — nur mit Ihrer ausdrücklichen Einwilligung gespeichert, mit Abmeldung in jeder Nachricht.',
      ] },
      { heading: 'An wen wir Daten weitergeben', body: [
        'Google — Anmeldung (Google Sign-In) und Verarbeitung der KI-Anfrage (Google Gemini).',
        'Stripe — Abrechnung der Design Session, sobald eingeführt.',
        'Sentry — technische Fehlerüberwachung (wir senden nur Fehlerprotokolle, nie den Inhalt Ihrer Anfragen).',
        'Einige dieser Anbieter verarbeiten Daten in den USA; die Übermittlung stützt sich auf das EU-US Data Privacy Framework und/oder Standardvertragsklauseln (SCC). Eine Kopie erhalten Sie auf Anfrage.',
        'Wir verkaufen Ihre Daten nicht. eBay ist kein Empfänger Ihrer Daten — siehe „Gebrauchtangebote" unten.',
      ] },
      { heading: 'Gebrauchtangebote (eBay)', body: [
        'Wenn wir Gebrauchtangebote zeigen, rufen wir öffentliche eBay-Angebote nach Kategorie und Markt ab; wir senden eBay keine personenbezogenen Daten von Ihnen. Ein Klick auf ein Angebot führt zu eBay, wo deren Bedingungen gelten.',
      ] },
      { heading: 'Speicherdauer', body: [
        'Bis Sie Ihr Konto löschen oder Ihre Sitzung abläuft. Sie können Ihr Konto und alle zugehörigen Daten in der App löschen („Konto löschen").',
      ] },
      { heading: 'Ihre Rechte (DSGVO)', body: [
        'Sie haben das Recht auf Auskunft, Berichtigung, Löschung, Datenübertragbarkeit und Widerspruch sowie auf Beschwerde bei einer Aufsichtsbehörde (in Österreich: DSB; in Deutschland: die zuständige Landesbehörde).',
        'Die Kontolöschung ist sofort in der App verfügbar; für eine Kopie Ihrer Daten (Auskunft und Übertragbarkeit) oder die übrigen Rechte kontaktieren Sie uns unter ' + OPERATOR.email + '.',
        'Profildaten (Name, Bild) werden bei jeder Google-Anmeldung automatisch aktualisiert.',
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
    updated: 'Zuletzt aktualisiert: 03.07.2026',
    disclaimer: 'BudgetSpace ist derzeit ein kostenloses Projekt (Beta) — wir verkaufen keine Waren oder Dienstleistungen und berechnen nichts. Vollständige Angaben zum Unternehmen (USt-IdNr./Registrierung, Anschrift) erscheinen hier, sobald eine kostenpflichtige Design Session startet.',
    sections: [
      { heading: 'Diensteanbieter', body: operatorLines({ entity: 'Unternehmen', address: 'Anschrift', email: 'E-Mail' }) },
    ],
  },
};

export function legalDoc(lang: string, key: LegalKey): LegalDoc {
  if (lang === 'hr') return HR[key];
  if (lang === 'de') return DE[key];
  return EN[key];
}
