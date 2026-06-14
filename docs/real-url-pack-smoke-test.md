# Real URL pack smoke test

Ovaj dokument opisuje kako ručno provjeriti da BudgetSpace radi sa stvarnim URL-ovima
proizvoda. Test je namijenjen developerima i QA-u; nema sučelja za krajnje korisnike.

## 1. Pripremi pack

Odaberi jedan JSON iz `docs/pilot-packs` ili `docs/pilot-packs/real-*.json` i
zamijeni placeholder URL-ove stvarnim product URL-ovima koje smiješ dohvaćati. Svaki
item ima vlastiti `category`, `roomTags` i `styleTags` – prilagodi po potrebi.

Primjer:

```json
{
  "retailer": "IKEA",
  "items": [
    { "url": "https://www.ikea.com/hr/hr/p/dvosjed-landsberg-svijetli-30200001/", "defaults": { "category": "sofa", "roomTags": ["living-room"], "styleTags": ["modern"] } },
    { "url": "https://www.ikea.com/hr/hr/p/klub-stolic-crni-30200020/", "defaults": { "category": "table", "roomTags": ["living-room"], "styleTags": ["industrial"] } }
    // … još 10–20 URL-ova
  ]
}
```

## 2. Pokreni collector

Pošalji pack backendu preko `/api/products/collect/retailer-urls`:

```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/pilot-packs/real-ikea-hr-living-room-pilot.json
```

Spreman stvarni IKEA HR pilot pack je
[pilot-packs/real-ikea-hr-living-room-pilot.json](pilot-packs/real-ikea-hr-living-room-pilot.json)
(12 stvarnih `www.ikea.com/hr/hr` URL-ova). **Prvo probaj s 5–6 URL-ova**, pa ostatak. Ovo je
dev-only; ne koristi za masovno skidanje, poštuj `robots.txt`/ToS, i ako te stranica blokira
(403/429) **ne pokušavaj bypass**. U `products` sažetku čitaj status po URL-u: `imported`,
`updated`, `skipped`, `needs-review`.

Response sadrži sažetak: koliko URL-ova je dohvaćeno, koliko je uvezeno, koliko treba
pregledati (`needs-review`) te eventualne greške. Ako je neki URL loš (nema cijenu,
nedostaju room/style tagovi), collector ga označava i vraća `retryRequest` sa samo
tim URL-ovima.

## 3. Pregledaj i ponovi

Ako u summaryju vidiš `needs-review`, otvori `retryRequest`, dopuni `defaults`
(npr. `category`, `roomTags`, `styleTags`) i ponovi korak 2. Isti `externalId` ažurira
postojeći proizvod, ne stvara duplikat.

## 4. Provjeri katalog

Kada su svi URL-ovi prošli import, pozovi `/api/products/catalog-health`:

```bash
curl http://localhost:8080/api/products/catalog-health | jq
```

Provjeri `plannerReadiness` za sobu (npr. `living-room`) – mora imati `ready: true` i
bez kritičnih `weakCategories`. Ako katalog nije spreman, dodaj još proizvoda ručno.

## 5. Složi plan

Pokreni frontend (`npm --prefix frontend run dev`) i upiši prompt, npr.:

```text
Imam 1500 € za dnevni boravak, moderno, najviše IKEA, već imam TV i tepih.
```

Provjeri da proizvodi iz tvojeg URL packa ulaze u plan. Cijena mora biti ispravna,
link „Otvori u trgovini“ vodi na pravi proizvod, a nedostupni proizvodi ne ulaze. Kako si
rekao da već imaš TV i tepih, planner ne smije dodati TV komodu ni tepih, a „najviše IKEA”
znači da preferira IKEA proizvode.

## 6. Nema crawlera

Ovaj smoke test koristi **ručni pack**. Discovery endpoint može pomoći da pronađeš
linkove s jedne listing stranice, ali za ozbiljnije importove koristi službene feedove.