# Controlled discovery of product URLs (dev-only)

Sprint 10.0 donosi prvi pilot za kontrolirano izvlačenje URL-ova proizvoda iz jedne
kategorije/listing stranice. Ova funkcionalnost je **isključivo za razvoj** – nema
korisničkog sučelja, ne slijedi paginaciju i ne služi za masovno scrapiranje. Ako
stranica ne dopušta automatizirano dohvaćanje, **nemoj koristiti discovery**.

## Što discovery radi

- Daješ joj URL kategorije (npr. „kauči za dnevni boravak“ na ikea.com) i naziv
  trgovine.
- Backend dohvaća **samo tu jednu stranicu** i traži HTML linkove koji izgledaju kao
  proizvodi (heuristika: sadrže `/p/`).
- Rezultat je lista pronađenih product URL-ova (deduplicirano), lista preskočenih URL-ova
  (npr. drugi domene) te eventualna upozorenja ili greške.
- Možeš navesti limit (max 20) – discovery vraća najviše toliko linkova.
- Ako postaviš `collect: true`, discovery odmah šalje pronađene URL-ove u postojeći
  collector/import pipeline (sa zadanim `defaults`). Inače vraća samo URL-ove.

## Kako koristiti

Primjer zahtjeva:

```bash
curl -X POST http://localhost:8080/api/products/collect/discover-product-urls \
  -H "Content-Type: application/json" \
  --data-raw '{
    "retailer": "IKEA",
    "categoryUrl": "https://www.example-ikea.com/hr/hr/c/dnevni-boravak-sofe",
    "limit": 10,
    "collect": false,
    "defaults": {
      "category": "sofa",
      "roomTags": ["living-room"],
      "styleTags": ["modern"],
      "sourceReference": "pilot-run"
    }
  }'
```

Polja:

- `retailer` – podržane trgovine (IKEA, JYSK, …). Obavezno.
- `categoryUrl` – URL listing stranice. Obavezno. Mora biti na dopuštenoj domeni.
- `limit` – koliko linkova vratiti (1–20). Ako izostaviš, default je 10.
- `collect` – ako `true`, pronađeni URL-ovi automatski se importaju kroz collector.
  Ako `false` ili izostavljeno, samo se vraća lista URL-ova.
- `defaults` – isti format kao u collectoru (`category`, `roomTags`, `styleTags`,
  `sourceReference`). Koristi se samo kod `collect: true`.

## Ograničenja

- Samo jedna stranica: nema paginacije, nema dubinskog crawlanja.
- Heuristika `/p/` možda neće pokriti sve proizvode; ručno ispitaj rezultate.
- Podržane su samo domene na allowlisti za odabranu trgovinu. Drugi linkovi se
  preskaču.
- Ne koristi Playwright/Selenium – discovery čita samo statički HTML.
- Ne prati `robots.txt` automatski – kao developer provjeri možeš li dohvatiti stranicu.
- Za produkciju preferiraj službene feedove/API-je.

## Kada **ne** koristiti discovery

- Kad stranica eksplicitno zabranjuje automatizirano dohvaćanje.
- Kad trebaš pretraživati ili paginirati tisuće proizvoda.
- Za više trgovina odjednom. Discovery v0 podržava samo jedan retailer per zahtjev.

Ako ti discovery ne uspije vratiti linkove (npr. stranica je JS-only), koristi
ručno pripremljen URL pack kao u pilot paketu.