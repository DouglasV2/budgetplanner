# Budget repair — primjeri i očekivano ponašanje

Kad plan prelazi budžet ili je jako blizu, aplikacija ne kaže samo „preko budžeta si”.
Daje konkretan prijedlog: što preskočiti, što prebaciti za kasnije, što zamijeniti
povoljnijim i koliko se time štedi.

Logika je u `PlannerService` (`repairBudget`, `buildBudgetRepairSuggestions`). Pravila su
namjerno jednostavna — ovo nije matematički optimizator.

## Redoslijed popravka

1. **Najvažnije se ne dira.** Glavni komadi (npr. kauč i TV komoda za dnevni boravak,
   krevet i madrac za spavaću) i sve što je korisnik izričito tražio ostaju.
2. **Pojeftini opcionalno.** Skuplji „za ugodniji prostor” / „može kasnije” proizvodi
   prvo se pokušaju zamijeniti povoljnijima iste kategorije.
3. **Prebaci za kasnije.** Ako plan i dalje probija budžet, „može kasnije” proizvodi
   (pa onda „za ugodniji prostor”) izlaze iz glavne kupnje. U sučelju se vide kao
   „Preskočeno za sada”.
4. **Ako ni to nije dovoljno**, plan jasno kaže da budžet nije dovoljan za kompletan
   plan i prikaže najbolju osnovnu kombinaciju (samo najvažnije).

## Polja u odgovoru plana

- `budgetRepairSuggestions` — do 3 kratka savjeta (prikazuju se samo kad je tijesno/preko).
- `overBudgetAmount` — koliko plan prelazi budžet (0 ako stane).
- `purchaseSummary` — 2–4 kratke rečenice iznad plana („Preporučena kombinacija”).
- `storeLimitNote` — kratko objašnjenje ako je za bolju cijenu dodana još jedna trgovina.

Stariji spremljeni planovi nemaju ova polja; sučelje tada pada na postojeći prikaz.

## Scenariji

### A) Plan je malo iznad budžeta
Npr. budžet 1000 €, plan 1080 €. Najskuplji „može kasnije” proizvod izlazi iz glavne
kupnje ili se zamijeni povoljnijim. Prijedlog: „Preskoči dekoracije i štedi 76 €.”
Rezultat staje u budžet.

### B) Plan je jako iznad budžeta
Npr. budžet 700 €, samo glavni komadi koštaju 900 €. Najvažnije se ne izbacuje, pa
`overBudgetAmount` pokaže 200 €. Poruka: „Kreni s najvažnijim stvarima, a ostalo dodaj
kad ostane budžeta.” Prikazuje se najbolja osnovna kombinacija.

### C) Opcionalno se prebacuje za kasnije
„Kompletno” opremanje doda dekoracije i tepih. Ako probiju budžet, prvo izlaze
dekoracije („može kasnije”), pa tepih („za ugodniji prostor”), dok glavni komadi ostaju.
Summary: „Tepih je prebačen u ‘Može kasnije’ da budžet ostane pod kontrolom.”

### D) Zamjena skupljeg proizvoda povoljnijim
Ako za skuplju lampu postoji jeftinija u istoj kategoriji i dovoljno dobra, predlaže se:
„Povoljnija lampa spušta plan za 34 €.” Glavni komadi se ne diraju.

### E) Osnovni plan kad kompletan nije moguć
Ako budžet ne pokriva ni glavne komade s dodacima, plan zadrži samo najvažnije i jasno
to kaže umjesto da tiho izbaci ono što je korisnik tražio.

## Ručna provjera

1. Pokreni backend i frontend.
2. Upiši npr. „Imam 700 € za dnevni boravak, kompletno.”
3. Iznad plana provjeri „Preporučena kombinacija” i blok „Budžet je tijesan” s 2–3 savjeta.
4. Provjeri da kauč i TV komoda ostaju, a dekoracije/tepih su prebačeni za kasnije.
