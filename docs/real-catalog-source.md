# Real catalog source v1

Sprint 9.0. Ovo je sloj za rad s **pravijim proizvodima iz stvarnih trgovina, bez live
scrapinga.**

**Ovo nije scraper.** Nema Playwrighta, Seleniuma, browser automatizacije ni poziva na
žive stranice trgovina. Ovo je priprema: stabilan i proširiv format izvora koji kasnije
može puniti scraper, ručni export ili partnerski feed — bez ikakve promjene u planneru.

Ključna ideja: proizvod nosi **odakle je došao** (izvor), **kad je zadnji put provjeren**
i **koliko su podaci potpuni**, uz stvarne URL-ove, slike, cijene i dostupnost. Te
informacije su za backend/dev/docs — korisniku se u glavnom UI-u ne prikazuju tehnički.

## Kako to ulazi u sustav

Real catalog source koristi **postojeći** retailer snapshot import — nema paralelnog
sustava. Snapshot proizvod sada može nositi i source metadata i `lastCheckedAt`.

```
real catalog JSON
        │
        ▼
RetailerCatalogAdapter   →  preslika u interni ImportProductDto (+ source metadata)
        │
        ▼
ProductImportService     →  ista validacija, taksonomija i dedup po externalId
        │
        ▼
Product (planner katalog)
```

## Format

JSON lista proizvoda. Polja:

| Polje | Obavezno | Napomena |
| --- | --- | --- |
| `externalId` | da | Jedinstveni ključ trgovine. Po njemu se radi dedup. |
| `name` | da | Naziv proizvoda. |
| `retailer` | da | IKEA, JYSK, Pevex, Emmezeta, Decathlon, Lesnina. |
| `category` | da | Canonical ili hrvatski sinonim (vidi product-import.md). |
| `price` | da | Veći od 0. |
| `roomTags` | da | npr. `living-room`, `bedroom`, `home-office`, `home-gym`. |
| `styleTags` | da | npr. `modern`, `minimal`, `cozy`, `classic`, `industrial`, `boho`. |
| `productUrl` | ne | Ako postoji, mora izgledati kao URL. |
| `imageUrl` | ne | Ako postoji, mora izgledati kao URL. |
| `availabilityStatus` | ne | `in-stock`, `limited`, `unavailable`, `check-store`. Default `in-stock`. |
| `deliveryNote` | ne | Slobodan tekst. |
| `lastCheckedAt` | ne | Datum (`2026-06-01`) ili ISO vrijeme. Mora biti parsabilan ako postoji. |
| `priceTier` | ne | `budget`, `standard`, `premium`. Inače se izračuna iz cijene. |
| `reviewCount` | ne | Sprint 10.13 (#2). Broj recenzija u trgovini. Prikaže se agregat tek kad je zadan — ocjene se ne izmišljaju. |
| `reviewsUrl` | ne | Sprint 10.13 (#2). Link na recenzije; ako fali, UI koristi `productUrl`. |
| `market` | ne | Sprint 10.13 (#3). Tržište/država (`HR`, `SI`, `AT`, `DE`…). Prazno = globalno (vidi se na svim tržištima). Default `HR` pri importu. |

### Source metadata

| Polje | Vrijednosti | Default |
| --- | --- | --- |
| `sourceType` | `manual`, `retailer-snapshot`, `future-scraper` | `retailer-snapshot` (za snapshot import) |
| `sourceName` | npr. IKEA, JYSK… | retailer |
| `sourceReference` | slobodan tekst (npr. naziv exporta) | prazno |
| `dataQuality` | `complete`, `partial`, `needs-review` | izračuna se (vidi dolje) |
| `dataQualityNotes` | slobodan tekst | prazno |

`importedAt` postavlja sustav na svaki import — ne šalje se u JSON-u.

Ako `dataQuality` nije zadan, sustav ga procijeni: proizvod sa stvarnim linkom, slikom i
nedavnom provjerom je `complete`, inače `partial`.

Gotov primjer s 40 proizvoda (6 trgovina, sve sobe, miješana dostupnost i kvaliteta) je
[sample-real-catalog-source.json](sample-real-catalog-source.json).

## Status izvora po trgovini (Sprint 10.13)

Dodajemo trgovine **samo kad možemo provjeriti naziv, cijenu i URL**. Ne izmišljamo podatke.

| Trgovina | Status | Napomena |
| --- | --- | --- |
| IKEA | ✅ verificirano | Cijene se daju web-provjeriti (raniji „no price” slučajevi su ugašeni proizvodi). |
| JYSK | ✅ verificirano | Pokriva većinu soba u HR. |
| Emmezeta | ✅ verificirano | `real-emmezeta-hr.json` (4 provjerena proizvoda). |
| Decathlon | ⛔ blokirano | Stranice vraćaju HTTP 403 na naš fetch → cijene/URL-ovi neprovjerljivi. Treba službeni feed/partner. Zbog toga home-gym ostaje na sample podacima. |
| Pevex | ⛔ blokirano | HTTP 403, isto kao gore. |
| Lesnina (xxxlesnina.hr) | ⛔ blokirano | HTTP 403, isto kao gore. |

Blokirane trgovine ulaze tek kad dobiju isti JSON oblik iz službenog feeda/partnerskog pristupa
(vidi „Kako kasnije spojiti pravi scraper”) — ne kroz HTML fetch koji vraća 403.

## Tržišta i prijevodi (Sprint 10.13, #3)

`market` na proizvodu određuje na kojem se tržištu vidi i u kojoj se valuti prikazuje. Trenutno su
svi pravi proizvodi `HR` (EUR); ostala EU tržišta zato dobivaju samo globalne/sample proizvode.
Frontend zato u biraču nudi **samo EUR tržišta** i prikazuje napomenu da se katalog za tu državu još
puni. Prije uključivanja ne-EUR tržišta treba dodati verificirane kataloge s ispravnom valutom.

## Import preko curl-a

Pokreni backend, zatim iz root foldera projekta:

```bash
curl -X POST http://localhost:8080/api/products/import/retailer-snapshot \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-real-catalog-source.json
```

## Primjer import summary responsea

Loš proizvod ne ruši import — preskoči se, ostatak se importira ili ažurira po `externalId`.

```json
{
  "created": 40,
  "updated": 0,
  "skipped": 0,
  "totalReceived": 40,
  "products": [
    { "externalId": "rc-ikea-lr-sofa-light-01", "name": "Dvosjed svijetli tekstil", "retailer": "IKEA", "category": "sofa", "price": 299.0, "availabilityStatus": "in-stock" }
  ],
  "errors": []
}
```

## Validacija (strogo, ali korisno)

Proizvod se preskače (ne ruši import) ako:

- `externalId`, `name`, `retailer`, `category`, `price`, `styleTags` ili `roomTags` fale ili nisu valjani,
- `productUrl`/`imageUrl` postoji ali ne izgleda kao URL,
- `lastCheckedAt` postoji ali nije parsabilan datum,
- `availabilityStatus` nije jedan od dozvoljenih,
- `sourceType` nije jedan od dozvoljenih,
- `dataQuality` nije jedna od dozvoljenih,
- `sourceType` je postavljen, a `sourceName` fali.

## lastCheckedAt i svježina

Ako je `lastCheckedAt` stariji od 14 dana (ili fali/nije čitljiv), proizvod **i dalje može
ući u plan**, ali UI korisniku kaže „Provjeri u trgovini” / „Provjeri cijenu i dostupnost
u trgovini.” Tehnički datum se ne gura svugdje.

## Planner i kvaliteta podataka

Planner blago preferira proizvode s linkom, slikom, nedavnom provjerom i `complete`
kvalitetom kad su inače slični. To je samo tie-breaker — nikad ne nadjača stil, prostor ili
cijenu i **nikad se ne prikazuje kao „score”**. `unavailable` proizvodi nikad ne ulaze.

## Kako kasnije spojiti pravi scraper

Scraper (ili partner feed) treba samo proizvesti isti JSON oblik (ili `RetailerProductSnapshotDto`)
sa `sourceType: "future-scraper"` i napuniti `lastCheckedAt`. Ide kroz isti
`RetailerCatalogAdapter` → `ProductImportService`. Planner, validacija i dedup ostaju
nepromijenjeni — mijenja se samo izvor podataka.
