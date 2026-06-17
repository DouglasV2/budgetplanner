# HR catalog URL review — found during the image pass (Sprint 10.24)

> **STATUS (resolved 2026-06-17, Sprint 10.25):** All 16 dead rows (§A) flipped to `needs-review` (planner
> now excludes them). All 18 drifted rows (§B) had their `productUrl` refreshed to the canonical target and
> got a web-verified image. Canonicalising 2 of them exposed 2 hidden duplicate products (JYSK KRISTOF lamp,
> JYSK BOVRUP chair) — deduped (kept the row each test/file constraint required, dropped the redundant depth
> copy, merged roomTags/reviews). Remaining step-3 work: full price/stock re-verification of the HR catalog.

While fetching verified product images (step 4), the deterministic fetcher followed each HR product URL
and recorded where it landed. **34 of 270 reachable HR rows did not yield a verified image because their
stored `productUrl` no longer resolves to that product.** This is a **step-3 (re-verification)** signal:
contrary to "HR was just verified 16/17 Jun", these URLs have drifted or died. None were imaged (we never
attach an image to a dead/redirected product); they keep the labelled "ilustracija" placeholder.

Rule reminder: 403/anti-bot is never bypassed; we never fabricate an image/URL. Harvey Norman (14 rows) was
excluded from imaging entirely — its product pages serve a wrong/generic `og:image` (e.g. a "patton" page
returns a "plaza" image), so its images can't be trusted without manual picking.

## A. Truly dead — product gone, link lands on a category page → mark `needs-review` (15)

The stored URL 301-redirects to a category/listing, so "Otvori u trgovini" no longer reaches the product.
These should be flipped to `needs-review` (the planner's verified-only gate then excludes them) or re-sourced.

| externalId | redirects to |
|---|---|
| ikea-hr-besta-tv-klupa-bijela | /cat/svi-proizvodi-products/ |
| ikea-hr-morum-tepih-bez-160x230 | /cat/svi-proizvodi-products/ |
| ikea-hr-stoense-tepih-blijedoroza-170x240 | /cat/svi-proizvodi-products/ |
| ikea-hr-tiphede-tepih-prirodna-crna | /cat/svi-proizvodi-products/ |
| ikea-hr-arende-tepih-visoki-flor-krem | /cat/svi-proizvodi-products/ |
| ikea-hr-ringsta-skaftet-podna-lampa-bijela-mjed | /cat/svi-proizvodi-products/ |
| ikea-hr-strandad-podna-lampa-bijela-crna | /cat/svi-proizvodi-products/ |
| ikea-hr-nymane-podna-lampa-antracit | /cat/svi-proizvodi-products/ |
| ikea-hr-barlast-podna-lampa-crna-bijela | /cat/svi-proizvodi-products/ |
| ikea-hr-vittsjoe-komb-odlaganje-crno-smeda | /cat/svi-proizvodi-products/ |
| ikea-hr-vanderots-jastucnica-bijela | /cat/svi-proizvodi-products/ |
| ikea-hr-gurli-jastucnica-neizbijeljeno | /cat/navlake-za-jastuk-18903/ |
| ikea-hr-hallway-stoftmoln-led-stropna-zidna-24cm | /cat/svi-proizvodi-products/ |
| jysk-hr-trosjed-vejlby-tamnosiva | jysk.hr/dnevni-boravak/kauci |
| jysk-hr-stolic-trosterud-mramor-crna | jysk.hr/dnevni-boravak/stolici-za-kavu-i-pomocni-stolici |

Plus **ikea-hr-arstid-podna-lampa-mjed-bijela** → redirects to a *different* product
`/p/arstid-stolna-lampa-mjed-bijela-30321373/` (ÅRSTID **table** lamp, not the floor lamp). The floor-lamp
row is effectively dead/mislabeled → `needs-review` or re-point to the correct product.

## B. Alive but stored URL drifted — 301s to the live canonical product page (18)

The product still exists; the stored URL just redirects to a fuller/canonical slug, so the "Otvori u
trgovini" link **still works**. Low urgency. Refreshing each `productUrl` to its canonical form (below) in
step 3 also unlocks its verified image (re-run the image fetch afterwards).

| externalId | canonical URL |
|---|---|
| ikea-hr-roedeby-pladanj-bambus | /p/roedeby-pladanj-naslon-za-ruke-bambus-40417577/ |
| ikea-hr-kitchen-soendrum-zidni-sat-bijela-35cm | /p/soendrum-zidni-sat-bijela-60540864/ |
| jysk-hr-bovrup-natur-hrast-bez | /blagovaonica/blagovaonske-stolice/blagovaonska-stolica-bovrup-natur-hrast-bez-tkanina |
| jysk-hr-elverum-3-police-crna-celik | /hodnik/namjestaj-za-hodnik/stalci-za-kapute-i-cipele/regal-za-hodnik-elverum-3-police-crna-celik |
| jysk-hr-vedde-140x200-divlji-tamni-hrast | /spavaca-soba/kreveti/podnice-i-okviri-kreveta/okviri-kreveta/okvir-kreveta-vedde-140x200-divlji |
| jysk-hr-limfjorden-nocni-ormaric-svijetli-hrast | /spavaca-soba/nocni-ormarici/nocni-ormaric-limfjorden-2-ladice-svijetli-hrast-boja |
| jysk-hr-blagovaonski-stol-aabenraa-hrast-crna | /blagovaonica/blagovaonski-stolovi/blagovaonski-stol-aabenraa-80x120-topli-hrast-boja-crna |
| jysk-hr-ormar-limfjorden-180x200-hrast | /pohranjivanje/ormari/ormar-limfjorden-180x200-3-vrata-divlji-natur-hrast |
| jysk-hr-podna-lampa-kristof-v145-celik | /kucanstvo/rasvjeta/svjetiljke/podna-lampa-kristof-o35xv145-cm-celik |
| jysk-hr-hallway-belle-cabinet-white-oak | /hodnik/namjestaj-za-hodnik/stalci-za-kapute-i-cipele/ormar-za-hodnik-belle-2-vrata-bijela-boja |
| jysk-hr-hallway-cirkelhuse-rack-black | /hodnik/namjestaj-za-hodnik/vjesalice-za-kapute/vjesalica-cirkelhuse-crna-0 |
| jysk-hr-hallway-oldekrog-bench-white-oak | /hodnik/namjestaj-za-hodnik/klupe-za-hodnik/klupa-oldekrog-3-ladice-bijela-divlji-natur-hrast-boja |
| jysk-hr-dining-room-rindsholm-komoda-hrast | /blagovaonica/komode/komoda-rindsholm-2-vrata-3-ladice-svijetli-hrast |
| jysk-hr-dining-room-markskel-vitrina-bijela-hrast | /blagovaonica/vitrine/vitrina-markskel-2-vrata-bijela-dilvji-natur-hrast |
| emmezeta-hr-slave-tv-stalak-241 | /slave-tv-stalak-241x33x62-cm-25501.html |
| emmezeta-hr-retro-komoda-2vrata-2ladice | /retro-komoda-2-vrata-2-ladice-93-5x45x93-5-natur-sonoma-hrast-1206784.html |
| emmezeta-hr-hallway-sawa-shoe-cabinet-white | /sawa-ormaric-za-cipele-3-vrata-45x28x136-bijeli-1205384.html |
| emmezeta-hr-kitchen-magnolia-gornji-ormaric-60cm | /gornji-kuhinjski-ormaric-magnolia-2-vrata-60cm-natur-bijela-1205454.html |

## Coverage after step 4
- **236 / 270** reachable HR rows now carry a verified image (`imageVerified: true`): IKEA 112, JYSK 73,
  Emmezeta 38, Namjestaj.hr 13.
- Harvey Norman (14): no images (unreliable `og:image`) — placeholder kept.
- The 34 above: no images until their URLs are re-verified (step 3).
