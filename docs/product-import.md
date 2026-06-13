# Product import

Sprint 8.3 učvršćuje import proizvoda tako da katalog može stvarno pomoći planneru. Ovo nije korisnički ekran i nije zamjena za buduće scrapere. Služi za ručni/dev unos stvarnijih proizvoda dok ne spojimo trgovine.

> Za import proizvoda u formatu koji izgleda kao izvoz iz trgovine vidi [retailer-snapshot-import.md](retailer-snapshot-import.md). Taj import koristi isti pipeline i istu validaciju opisanu ovdje.

## JSON import

Pokreni backend, zatim iz root foldera projekta:

```bash
curl -X POST http://localhost:8080/api/products/import \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-products-import.json
```

Body je lista proizvoda:

```json
[
  {
    "externalId": "sample-ikea-living-sofa-light-textile",
    "name": "Dvosjed svijetli tekstil",
    "retailer": "IKEA",
    "category": "sofa",
    "price": 249,
    "styleTags": ["modern", "minimal"],
    "roomTags": ["living-room"],
    "productUrl": "https://example.com/products/sample-ikea-living-sofa-light-textile",
    "imageUrl": "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=900&q=80",
    "availabilityStatus": "in-stock"
  }
]
```

## CSV import

Pokreni backend, zatim:

```bash
curl -X POST http://localhost:8080/api/products/import/csv \
  -H "Content-Type: text/csv" \
  --data-binary @docs/sample-products-import.csv
```

CSV mora imati header. Prazne linije se preskaču, vrijednosti se trimaju, a `styleTags` i `roomTags` mogu biti zapisani kao `modern|cozy` ili kao CSV vrijednost `"modern,cozy"`.

Primjer:

```csv
externalId,name,retailer,category,price,styleTags,roomTags,productUrl,imageUrl,availabilityStatus
csv-pevex-office-lamp-black,Stolna lampa crna,Pevex,lampa,24.99,modern|industrial,home-office,https://example.com/products/csv-pevex-office-lamp-black,https://example.com/image.jpg,check-store
```

Ako koristiš zarez unutar `styleTags` ili `roomTags`, najbolje je staviti navodnike oko polja:

```csv
csv-jysk-living-rug-warm,Tepih topli ton,JYSK,tepih,74.99,"cozy,natural","living-room,bedroom",https://example.com/products/csv-jysk-living-rug-warm,https://example.com/image.jpg,limited
```

Podržani su i neki hrvatski headeri, npr. `naziv`, `trgovina`, `kategorija`, `cijena`, `stilovi`, `prostorije`, `link` i `slika`.

## Obavezna polja

Svaki proizvod mora imati:

- `externalId`
- `name`
- `retailer`
- `category`
- `price`
- `styleTags`
- `roomTags`

`price` mora biti veći od 0. `roomTags` i `styleTags` ne smiju biti prazni jer planner bez njih ne zna u koji plan proizvod smije ući.

## Podržane trgovine

Import prihvaća samo ove trgovine:

- IKEA
- JYSK
- Pevex
- Emmezeta
- Decathlon
- Lesnina

## Kategorije i sinonimi

Import sprema kategorije u canonical oblik koji koristi planner:

| Sinonimi u importu | Spremljena kategorija |
| --- | --- |
| `kauč`, `kauc`, `sofa`, `couch` | `sofa` |
| `tv komoda`, `komoda za tv`, `tv unit` | `tv-unit` |
| `stolić`, `stolic`, `coffee table` | `table` |
| `tepih`, `rug` | `rug` |
| `rasvjeta`, `lampa`, `lighting` | `lighting` |
| `storage`, `ormar`, `polica` | `storage` |
| `dekor`, `dekoracije`, `decor` | `decor` |
| `krevet`, `bed` | `bed` |
| `madrac`, `mattress` | `mattress` |
| `stol`, `desk` | `desk` |
| `stolica`, `chair` | `chair` |
| `gym equipment`, `oprema za vježbanje` | `gym-equipment` |

Posebno: `stolica` ide u `chair`, a `stolić` ide u `table`. `stol` se tretira kao `desk`.

## Stilovi i sinonimi

| Sinonimi u importu | Spremljeni stil |
| --- | --- |
| `moderno`, `modern` | `modern` |
| `minimalno`, `simple`, `clean` | `minimal` |
| `toplo`, `cozy`, `warm` | `cozy` |
| `klasično`, `classic` | `classic` |
| `industrijski`, `industrial` | `industrial` |
| `boho`, `natural` | `boho` |

## Dostupnost

`availabilityStatus` je opcionalan. Ako ga nema, import koristi `in-stock`.

Ako postoji, mora biti jedan od:

- `in-stock`
- `limited`
- `unavailable`
- `check-store`

Planner ne smije birati `unavailable` proizvode. `limited` i `check-store` smiju ući u plan, ali UI korisniku kaže: “Provjeri u trgovini”.

## Import summary response

Import ne pada ako je jedan red loš. Loši proizvodi se preskaču, a ostatak se importira ili ažurira po `externalId`.

Primjer odgovora:

```json
{
  "created": 30,
  "updated": 1,
  "skipped": 1,
  "totalReceived": 32,
  "products": [
    {
      "externalId": "sample-ikea-living-sofa-light-textile",
      "name": "Dvosjed svijetli tekstil",
      "retailer": "IKEA",
      "category": "sofa",
      "price": 249
    }
  ],
  "errors": [
    {
      "row": 7,
      "externalId": "ikea-bad-123",
      "message": "Cijena mora biti veća od 0."
    }
  ]
}
```

`row` je 1-based. Kod JSON importa to je pozicija u listi. Kod CSV importa to je stvarni broj retka u datoteci, uključujući header.

## Deduplikacija

Ako importani proizvod ima `externalId` koji već postoji, postojeći proizvod se ažurira. Ne stvara se novi red.

## Napomena

Ovo je priprema za stvarne trgovine i buduće adaptere. Ne dodavati admin dashboard ni veliki UI za import dok proizvod nema stvarnu potrebu za tim.
