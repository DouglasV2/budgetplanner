package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 10.4 / 10.5 — loads the real IKEA HR + JYSK HR living-room catalog into the runtime
 * {@code products} table on startup, so the planner recommends real products with real product
 * URLs instead of the sample seed rows.
 *
 * <p>Runs after {@code data.sql} (which still seeds non-living-room / other-retailer sample data)
 * and, when enabled:</p>
 * <ol>
 *   <li>imports every real living-room snapshot resource through the existing, validated import
 *       pipeline ({@link RetailerSnapshotImportService}); imports are idempotent because the
 *       pipeline dedupes by {@code externalId} (existing rows are updated, never duplicated), and</li>
 *   <li>retires the {@code living-room} tag from the legacy sample products (which have no
 *       {@code sourceReference}), so a living-room plan is built only from the real catalog — even
 *       if the import itself fails, the fake homepage-link products are never served to users.</li>
 * </ol>
 *
 * <p>The seed is controlled by {@code budgetspace.real-catalog.seed-enabled}
 * (env {@code BUDGETSPACE_REAL_CATALOG_SEED_ENABLED}). It defaults to {@code true} so local/dev
 * runs work out of the box; set it to {@code false} to keep the sample catalog untouched.</p>
 *
 * <p>Best-effort and safe: a failure is logged and the application keeps running.</p>
 */
@Component
@Order(100)
public class RealCatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealCatalogSeeder.class);
    private static final String LIVING_ROOM = "living-room";

    /** Real catalog resources, imported in order. Add new approved snapshots here. */
    private static final List<String> SNAPSHOT_RESOURCES = List.of(
            "/catalog/real-ikea-jysk-hr-living-room.json",
            "/catalog/real-ikea-jysk-hr-living-room-expansion.json",
            // Sprint 10.7/10.9: verified JYSK HR products for the new rooms (dining-room, kitchen, hallway).
            "/catalog/real-jysk-hr-new-rooms.json",
            // Sprint 10.11: verified IKEA + JYSK HR products covering bedroom, home-office and bathroom.
            "/catalog/real-ikea-jysk-hr-rooms-expansion.json",
            // Sprint 10.12: catalog depth — lighting, rugs, decor, extra chairs/mattress across rooms.
            "/catalog/real-ikea-jysk-hr-depth.json",
            // Sprint 10.13: third HR retailer — verified Emmezeta products (living-room + bedroom).
            "/catalog/real-emmezeta-hr.json",
            // Sprint 10.13 (#3, go-wide): first non-HR market — verified IKEA Slovenia (SI) catalog
            // across living-room, home-office, bedroom and kitchen (EUR prices + real review counts).
            "/catalog/real-ikea-si.json",
            // Sprint 10.14 (go-wide): second EU market — verified IKEA Austria (AT) catalog across
            // living-room, home-office, bedroom and kitchen. Prices verified per-market on ikea.com/at
            // (they genuinely differ from HR/SI), reviews are the near-global aggregate. market="AT".
            "/catalog/real-ikea-at.json",
            // Sprint 10.14 (go-wide): third EU market — verified IKEA Germany (DE) catalog. Prices
            // verified per-market on ikea.com/de (they differ again from HR/SI/AT). market="DE".
            "/catalog/real-ikea-de.json",
            // Sprint 10.15 (production-depth): web-verified catalog depth across retailers and markets
            // (beds, mattresses, wardrobes, dining, office, more living-room) so the rule-based planner
            // has rich data before any LLM spend. Each row's name + EUR price verified on the live
            // public product page; sourceType=public-product-page.
            "/catalog/real-ikea-si-depth.json",
            "/catalog/real-ikea-at-depth.json",
            "/catalog/real-ikea-de-depth.json",
            "/catalog/real-jysk-hr-depth.json",
            "/catalog/real-jysk-si.json",
            "/catalog/real-jysk-at.json",
            "/catalog/real-jysk-de.json",
            "/catalog/real-emmezeta-hr-depth.json",
            // Sprint 10.16: HR kitchen depth + additional verified retailers (Harvey Norman HR/SI,
            // Namjestaj.hr, Otto/Segmüller/Poco DE). Other named retailers (Momax, Bauhaus, FeroTerm,
            // Prima, Perfecta, Merkur, Dipo, Wayfair, Home24, Roller, Kika, Leiner, XXXLutz) are
            // registered as OFFICIAL_FEED_REQUIRED (403/anti-bot/JS-only) and carry no products yet.
            "/catalog/real-hr-kitchen.json",
            "/catalog/real-harvey-norman.json",
            "/catalog/real-namjestaj-hr.json",
            "/catalog/real-de-new-retailers.json",
            // Sprint 10.17: HR depth for the thinnest rooms — bathroom (IKEA storage/lighting/decor),
            // hallway (IKEA/JYSK/Emmezeta shoe storage, racks, benches, rugs, mirrors) and more kitchen
            // depth (IKEA carts/rails/pendants + Emmezeta cabinets). Each row name + EUR price verified
            // on the live public product page on 2026-06-16; sourceType=public-product-page.
            "/catalog/real-hr-bathroom.json",
            "/catalog/real-hr-hallway.json",
            "/catalog/real-hr-kitchen-depth.json",
            // Sprint 10.18: SI/AT/DE depth for bathroom + hallway + kitchen (previously ~0 there). Ported
            // the verified HR IKEA SKUs to each market via IKEA's number-based URL redirect; the EUR price
            // was re-verified per market on ikea.com/<cc> (prices genuinely differ, e.g. STENSTORP cart
            // 229 SI vs 149 DE; TORNVIKEN island 379 SI vs 349 DE vs 529 AT) — never copied across markets.
            "/catalog/real-ikea-si-rooms.json",
            "/catalog/real-ikea-at-rooms.json",
            "/catalog/real-ikea-de-rooms.json",
            // Sprint 10.19: JYSK SI/AT/DE hallway + kitchen depth (those markets previously had JYSK only
            // for living-room/bedroom/dining/office). SI + DE verified on jysk.si/jysk.de. JYSK AT was
            // SKIPPED: jysk.at single-product pages gate stock behind JS and render "Vorübergehend
            // ausverkauft" in the static HTML WebFetch sees, so availability can't be honestly confirmed
            // (needs an official feed/API or headless render — out of scope). See catalog-sourcing notes.
            "/catalog/real-jysk-si-rooms.json",
            "/catalog/real-jysk-de-rooms.json",
            // Sprint 10.20: first catalog for two new EUR markets — Italy (IT) and Finland (FI). IKEA
            // core+rooms ported via the number-trick to /it/it/ and /fi/fi/ (per-market EUR prices
            // re-verified; e.g. KIVIK 599 IT vs 749 FI), plus JYSK FI hallway/kitchen. Non-EUR EU markets
            // (PL/CZ/HU/RO/SE/DK) are deferred until the UI handles their currency. JYSK IT has no stores.
            "/catalog/real-ikea-it-rooms.json",
            "/catalog/real-ikea-fi-rooms.json",
            "/catalog/real-jysk-fi-rooms.json",
            // Sprint 10.22 (road-to-production step 2): HR catalog maximization. Gap-fill across the thin
            // room/category cells (dining-room lighting/storage/decor, home-office storage/rug/decor,
            // kitchen storage/decor, hallway lighting, bathroom mirrors) + non-IKEA breadth (Emmezeta,
            // Harvey Norman, Namjestaj.hr) for price/style diversity. All web-verified 2026-06-17.
            "/catalog/real-hr-max-10-22.json",
            // Sprint 10.26: HR catalog breadth — more options per anchor category (IKEA HR beds/mattresses/
            // wardrobes/nightstands/dressers — previously absent — plus more desks/office chairs/sofas/coffee
            // tables/TV units; JYSK + Emmezeta beds/wardrobes/dining/dressers). Each row web-verified 2026-06-17
            // (name + EUR price from JSON-LD for IKEA/JYSK, spot-checked for Emmezeta) with a verified og:image.
            "/catalog/real-hr-breadth-10-26.json",
            // Sprint 10.29: EU depth — fill the IT + FI dining-room gap (both were 0). IKEA dining tables +
            // chairs ported via the global article-number trick to /it/it/ and /fi/fi/; each row's localized
            // name + per-market EUR price + verified og:image confirmed on ikea.com on 2026-06-17.
            "/catalog/real-eu-dining-10-29.json",
            // Sprint 10.31: EU depth — fill the thin IT + FI bedroom + home-office cells. IKEA beds/mattresses
            // /nightstands/wardrobes/dressers/desks/chairs/storage ported via the global article-number trick
            // to /it/it/ and /fi/fi/; localized name + per-market EUR price + verified og:image (2026-06-17).
            "/catalog/real-eu-bedroom-office-10-31.json",
            // Sprint 10.35: France (FR) — first IKEA FR catalog, ported from the IKEA IT set via the global
            // article-number trick to /fr/fr/. Each row's French name (og:title) + per-market EUR price
            // (JSON-LD) + verified og:image read off ikea.com/fr on 2026-06-18. IKEA-only (no JYSK in FR).
            "/catalog/real-ikea-fr-rooms.json",
            // Sprint 10.36: France non-IKEA breadth — Camif (camif.fr), the one major FR chain that serves
            // the price in static HTML (JSON-LD / visible €). 46 web-verified rows across all core rooms;
            // every other big FR chain (Conforama/But/Maisons du Monde/La Redoute/…) is anti-bot → feed-required.
            "/catalog/real-camif-fr.json",
            // Sprint 10.37: Netherlands — IKEA NL ported from the IKEA IT set via the article-number trick to
            // /nl/nl/ (Dutch name + per-market EUR price + verified og:image, ikea.com/nl 2026-06-18), plus
            // JYSK NL (jysk.nl is reachable + serves static prices, unlike jysk.at). NL = IKEA + JYSK.
            "/catalog/real-ikea-nl-rooms.json",
            "/catalog/real-jysk-nl-rooms.json",
            // Sprint 10.44: Netherlands depth — Leen Bakker + Kwantum (static-priced product pages, verified).
            "/catalog/real-nl-retailers.json",
            // Sprint 10.38: Slovakia — IKEA SK (IT-set number-trick → /sk/sk/) + JYSK SK (jysk.sk, same static
            // price structure as jysk.nl/hr). Slovak names + per-market EUR price + verified og:image, 2026-06-18.
            "/catalog/real-ikea-sk-rooms.json",
            "/catalog/real-jysk-sk-rooms.json",
            // Sprint 10.45: Slovakia depth — Nábytok (nabytok.sk, static-priced product pages + og:image, verified).
            "/catalog/real-sk-retailers.json",
            // Sprint 10.39: Spain — IKEA ES (IT-set number-trick → /es/es/, Spanish name + per-market EUR price
            // + verified og:image, 2026-06-18). IKEA-only (no JYSK in Spain), like FR/IT.
            "/catalog/real-ikea-es-rooms.json",
            // Sprint 10.43: Spain depth — Kenay Home + Banak Importa (static-priced product pages, verified).
            // Muebles La Fábrica's product pages reset the connection (anti-bot) → feed-required, not sourced.
            "/catalog/real-es-retailers.json",
            // Sprint 10.41: Portugal — IKEA PT (IT-set number-trick → /pt/pt/, Portuguese name + per-market EUR
            // price + verified og:image, 2026-06-18). IKEA-only (no JYSK in Portugal).
            "/catalog/real-ikea-pt-rooms.json",
            // Sprint 10.45: Portugal depth — Moviflor (moviflor.pt, static-priced product pages + og:image,
            // verified; page is windows-1252 so the sourcer decodes per the declared charset).
            "/catalog/real-pt-retailers.json",
            // Sprint 10.46: Scandinavia (non-EUR). IKEA NO/SE/DK via the number-trick (→ /no/no/, /se/sv/,
            // /dk/da/) with per-market JSON-LD price + priceCurrency (NOK/SEK/DKK) + verified og:image; JYSK
            // NO/SE/DK from static product pages (priceAmount=regular, JSON-LD price=current; sale only when a
            // priceValidUntil window confirms it). Prices are in the national currency, formatted per market.
            "/catalog/real-ikea-no-rooms.json",
            "/catalog/real-jysk-no-rooms.json",
            "/catalog/real-ikea-se-rooms.json",
            "/catalog/real-jysk-se-rooms.json",
            "/catalog/real-ikea-dk-rooms.json",
            "/catalog/real-jysk-dk-rooms.json",
            // Sprint 10.48: retail re-sweep — more verified static-priced retailers per market (JSON-LD /
            // PrestaShop itemprop / Shopify / visible €). Scandi non-IKEA + Westwing dropped (JS-rendered →
            // unreliable prices, never shipped). HR Svijetnamještaja, SI Svetpohištva, IT Conforama,
            // AT Interio, FI Masku, FR Lovely Meubles, PT JOM + Sítio do Móvel, ES Miroytengo + Merkamueble +
            // Muebles BOOM, NL Pronto Wonen, SK Drevona + ASKO Nábytok.
            "/catalog/real-hr-retailers-2.json",
            "/catalog/real-si-retailers-2.json",
            "/catalog/real-it-retailers-2.json",
            "/catalog/real-at-retailers-2.json",
            "/catalog/real-fi-retailers-2.json",
            "/catalog/real-fr-retailers-2.json",
            "/catalog/real-pt-retailers-2.json",
            "/catalog/real-es-retailers-2.json",
            "/catalog/real-nl-retailers-2.json",
            "/catalog/real-sk-retailers-2.json",
            // Sprint 10.55: United Kingdom — first GB catalog. IKEA GB ported via the article-number trick to
            // /gb/en/ (English name + GBP price + verified og:image, read off ikea.com/gb/en 2026-06-19). 48
            // products across all rooms, deduped, every planner cell >=1. IKEA-only for now (JYSK has no UK
            // stores); eBay runs a real EBAY_GB site, so "Rabljeno" can cover the UK once the eBay key is set.
            "/catalog/real-ikea-gb-rooms.json",
            // Sprint 10.77: kitchen depth for Spain (the thinnest kitchen market). Verified IKEA ES carts +
            // pendant, EUR prices + og:image read off ikea.com/es on 2026-06-21 (reviews left null — not verified).
            "/catalog/real-ikea-es-kitchen.json",
            // Sprint 10.79: kitchen-storage depth — verified IKEA ES (TORNVIKEN/KUNGSFORS shelves) + a full PT
            // kitchen (carts + storage). Names + EUR prices + og:image read off ikea.com/{es,pt} on 2026-06-22.
            "/catalog/real-ikea-kitchen-storage-10-79.json",
            // Sprint 10.80: RÅSKOG + NISSAFORS carts + TORNVIKEN wall shelf across 12 thin kitchen markets
            // (AT/DE/IT/FR/NL/GB/SK/SI/FI/SE/NO/DK). Each row's name + local-currency price was fetched, then
            // independently re-fetched by a second agent (33 confirmed, 0 rejected). 3 SI images not re-loaded
            // (imageVerified=false). Verified on ikea.com/* on 2026-06-22; re-check before production.
            "/catalog/real-ikea-kitchen-carts-10-80.json",
            // Sprint 10.175 (kitchen Increment 1): real IKEA KNOXHULT modular kitchen SETS (category kitchen-set)
            // for DE/AT/HR — the "complete kitchen" mode's honest, priced results. Each row's name + EUR price +
            // og:image was fetched, then independently re-fetched by a second agent (14 confirmed, 0 rejected).
            // Verified on ikea.com/{de,at,hr} on 2026-07-10; re-check before production.
            "/catalog/real-ikea-kitchen-sets-10-175.json",
            // Sprint 10.176 (kitchen Increment 3): real IKEA kitchen APPLIANCES (oven/hob/cooker-hood/fridge/
            // freezer/dishwasher/microwave) for HR/AT/DE — added to the normal plan only when the user asks. Each
            // row's name + EUR price + og:image fetched then independently re-fetched (32 confirmed, 1 rejected for
            // a price mismatch). Verified on ikea.com/{hr,at,de} on 2026-07-10; re-check before production.
            "/catalog/real-ikea-kitchen-appliances-10-176.json",
            // Sprint 10.87: depth for the thinnest hallway/home-office/dining cells — verified IKEA staples
            // (BISSA/STÄLL/MACKAPÄR/TRONES/NISSEDAL, ALEX desk+drawers, MARKUS, DANDERYD, ODGER) into DK/GB/SE/
            // DE/SI/AT. Each row's name + local-currency price was fetched, then independently re-fetched by a
            // second agent (21 confirmed, 1 rejected for an article/URL mismatch). Verified on ikea.com/* 2026-06-22.
            "/catalog/real-ikea-rooms-depth-10-87.json",
            // Sprint 10.100: kitchen LIGHTING for the 6 markets that had zero (AT/FI/FR/GB/NO/SK) — so a kitchen
            // plan there ships a pendant, not a lit-less room. SKURUP (all 6) + RANARP (AT/FR/GB/SK; FI+NO RANARP
            // resolved to a category page → skipped, not forced). Localized name + local-currency price + verified
            // og:image read live off ikea.com/<cc> on 2026-06-23 via the global article-number trick (80407114 /
            // 20390970).
            "/catalog/real-ikea-kitchen-lighting-10-100.json",
            // Sprint 10.117: HR soft furnishings (curtains/cushions/throws), IKEA + JYSK, web-verified live.
            "/catalog/real-hr-textiles.json",
            // Sprint 10.155: textiles for the 12 non-HR markets (were 0). 93 IKEA curtains/cushions/throws ported
            // via the global article-number trick to each market's IKEA site, then EACH re-fetched live (localized
            // name + per-market price/currency + verified og:image + real /p/ product URL); anything that 404'd or
            // redirected to a category page was dropped (no fabrication). Fills the biggest catalog gap the
            // 15-market sweep surfaced (non-HR comfort plans never got a textile).
            "/catalog/real-eu-textiles-10-155.json",
            // Sprint 10.126: catalog DEEPENING for the 4 thinnest markets (FR/IT/FI/PT), skewed mid-to-upper
            // price so plans there can actually fill a bigger budget (premium 89 / standard 71 / budget 23).
            // Discovered by a web-search workflow, then EVERY product deterministically re-fetched: live JSON-LD
            // price (authoritative — agent prices discarded), og:image identity-checked + resolved, deduped vs
            // the existing catalog (16 already-present dropped). IKEA via the global article trick; JYSK FR live.
            "/catalog/real-ikea-fr-deepen-10-126.json",
            "/catalog/real-fr-deepen-10-126.json",
            "/catalog/real-ikea-it-deepen-10-126.json",
            "/catalog/real-ikea-fi-deepen-10-126.json",
            "/catalog/real-ikea-pt-deepen-10-126.json",
            // Sprint 10.127: JYSK depth for FI/IT/PT (JYSK operates in all three; product pages serve a static
            // JSON-LD price + og:image). Sourced from JYSK category pages, every product deterministically
            // re-fetched (live price + name from og:title + og:image resolved), deduped vs the catalog. Adds
            // value/variety (budget-standard) to complement the IKEA premium depth above.
            "/catalog/real-fi-deepen-10-127.json",
            "/catalog/real-it-deepen-10-127.json",
            "/catalog/real-pt-deepen-10-127.json",
            // Sprint 10.128: JYSK DK depth (49 real products, all images verified, DKK price tiers). DK product
            // pages nest directly under the category path; NO/SE/SI nest one level deeper (their top "bed/sofa"
            // pages are sub-category listings, not products) so they were correctly dropped here (the missing
            // og:image flagged them) and need a deeper crawl next round.
            "/catalog/real-dk-deepen-10-128.json",
            // Sprint 10.130: JYSK depth for NO/SE/SI via their SITEMAPS (their category tree nests products a
            // level deeper than DK/FI, so the category-crawl missed them; the sitemap lists product URLs). Every
            // product re-fetched + verified (live JSON-LD price in NOK/SEK/EUR, og:image required so category +
            // SEO-guide pages were dropped, name from og:title). 240 products incl. wardrobes (NO 90 / SE 83 /
            // SI 67). GB/AT remain IKEA-only (no usable JYSK).
            "/catalog/real-no-deepen-10-130.json",
            "/catalog/real-se-deepen-10-130.json",
            "/catalog/real-si-deepen-10-130.json",
            // Sprint 10.132: GB IKEA depth (89 products) by porting plain global article numbers we already had
            // in other markets to /gb/en/p/-{article}/ (curl, no agents); each re-fetched + verified (live GBP
            // price + og:image). GB has no JYSK (not in UK), so IKEA-only; fills thin GB categories (storage,
            // decor, lighting, dressers, tables). Combo s-articles skipped (need a slug); GB already has those.
            "/catalog/real-ikea-gb-deepen-10-132.json",
            // Sprint 10.133: AT IKEA depth (152 products) via the same global-article port as GB — jysk.at is
            // JS-gated so AT is IKEA-only; the port fills all 17 categories (incl. premium beds/mattresses/sofas).
            // AT 107 -> ~259. Curl, no agents.
            "/catalog/real-ikea-at-deepen-10-133.json",
            // Sprint 10.159: kitchen + bathroom TARGETED deepen. A depth measurement showed kitchen-cart /
            // kitchen-storage / bathroom-storage cores are well covered, but kitchen LIGHTING was thin (PT had
            // 0) and lacked tiering, and a few bathroom cells were sparse. Filled the real gaps by porting
            // well-stocked IKEA kitchen/bathroom global articles (RANARP/SKURUP/NYMÅNE kitchen pendants,
            // STENSTORP islands, FRIHULT bath lamp, ...) to the markets missing them — bare-article trick where
            // it resolves + workflow-discovered canonical slugs where it redirects to /cat/. EVERY row
            // deterministically re-fetched (live JSON-LD price+currency + product og:image + model-token guard),
            // dropped on 404 / category redirect / currency mismatch. Curl, no trusted agent data.
            "/catalog/real-eu-kb-deepen-10-159.json",
            // Sprint 10.167: cross-market IKEA port — 2955 products. Every known IKEA article present in some
            // market was ported to each OTHER market via the bare-article URL (/p/-{article}/); only rows that
            // resolved to a real product page there survived, and each row's price + priceCurrency + og:image was
            // re-read LIVE off ikea.com/<cc> (currency-matched to the market; agent-free, curl only). Fills the
            // 15-market grid where an article is sold but was previously catalogued only elsewhere. No fabrication.
            "/catalog/real-ikea-cross-market-port-10-167.json",
            // Sprint 10.167: 141 non-IKEA products (Interio AT, JYSK AT/DK, Camif FR, Lovely Meubles FR, Poco DE,
            // Harvey Norman HR) discovered by a workflow that returned only product URLs; every price + currency +
            // og:image was then read LIVE off each page by a deterministic curl pass (agents supplied no prices),
            // currency-matched to the market. Rows without a readable JSON-LD price/image were dropped, not guessed.
            "/catalog/real-discovery-retailers-10-167.json",
            // Sprint 10.168: JYSK HR sofa refresh — the full current jysk.hr/dnevni-boravak/kauci range
            // (60 web-verified couches: name + JSON-LD ItemList price + og:image, read LIVE 2026-07-06 via
            // curl, agent-free), deduped vs the existing catalog. Closes the "plan shows a sofa JYSK no longer
            // sells" trust gap: a discontinued product URL 200-bounces to the category listing, so the shopper
            // saw "all couches" instead of the one in the plan. This gives the planner today's real options.
            "/catalog/real-jysk-hr-sofa-refresh-10-168.json",
            // Sprint 10.168: JYSK cross-market DEEPEN — 1015 web-verified products across all 13 JYSK markets
            // (AT/DE/DK/FI/FR/HR/IT/NL/NO/PT/SE/SI/SK). Each market's category pages were fetched, their JSON-LD
            // ItemList read (name + local-currency price + product URL; the newer markets nest the current price
            // under offers.price.unformatted.minSingle), og:image resolved, deduped vs the existing catalog, and
            // added up to +8 price-diverse rows per (market, category) cell (hard cap 35/cell) so plans gain real
            // breadth without bloat. Deterministic curl, agent-free; nothing fabricated.
            "/catalog/real-jysk-deepen-10-168.json",
            // Sprint 10.168: IKEA cross-market DEEPEN — 850 web-verified products across all 15 IKEA markets.
            // IKEA category ItemLists expose product URLs but NO price, so for each new URL the product page was
            // fetched and its JSON-LD price + priceCurrency (currency-matched to the market: EUR/GBP/NOK/SEK/DKK)
            // + og:image read live; anything without a matching-currency price or product image was dropped.
            // Curated core categories (sofa/bed/wardrobe/desk/dining/rug/nightstand/dresser/tv/table/storage/chair)
            // × 15 markets, +6 per (market, category) cell (cap 35). Deterministic curl, agent-free, no fabrication.
            "/catalog/real-ikea-deepen-10-168.json",
            // Sprint 10.169: bathroom FIXTURES from Pevex HR (sanitary ware IKEA/JYSK don't sell) — 35 web-verified
            // WC školjke, umivaonici and kade/tuševi (name + itemprop=price EUR + og:image read live off pevex.hr).
            // New categories toilet/washbasin/bath-shower; HR only (Pevex is Croatia-only). Pevex flipped to
            // MANUAL_VERIFIED_ONLY in CatalogSourcePolicy so these are planner-eligible.
            "/catalog/real-pevex-hr-bathroom-10-169.json",
            // Sprint 10.169: bathroom fixtures for DK — VVS Eksperten (sanitary-ware specialist), 42 web-verified
            // toilets/washbasins/baths+showers (JSON-LD price in DKK + og:image, read live off vvs-eksperten.dk).
            "/catalog/real-vvs-eksperten-dk-bathroom-10-169.json",
            // Sprint 10.177 (P1 bathroom depth): IKEA bathroom textiles (bath mats + shower curtains — the biggest
            // catalog gap: bathroom textiles were 0 in ALL 15 markets) + storage (bins/laundry baskets/shelves/
            // towel rails/hooks/mirrors/accessories) + bath wall lights, across all 15 markets. Harvested from each
            // market's OWN live IKEA category listing (category-by-id cross-market redirect), then EACH product page
            // re-read live: JSON-LD price + priceCurrency (currency-matched to the market) + og:image + og:title.
            // Bounced / currency-mismatched / dup rows dropped; internal METOD fittings pruned; a 24-agent
            // multilingual judge pass removed mis-categorized/non-standalone items. Deterministic curl, no fabrication.
            "/catalog/real-ikea-bathroom-depth-10-177.json",
            // Sprint 10.177 (P1/P2 furniture depth): IKEA core-furniture depth for the thin (market×category) cells
            // the coverage map surfaced — desk / mattress / chair / dining-chair / nightstand + kitchen-storage /
            // kitchen-cart, across all 15 markets. Same technique: each market's live category listing → per-product
            // live JSON-LD price+currency + og:image; dedup vs the existing catalog concentrates additions in the
            // thinnest markets. METOD/UTRUSTA cabinet components pruned; judge-verified. No fabrication.
            "/catalog/real-ikea-furniture-depth-10-177.json",
            // Sprint 10.178 (P1 bathroom fixtures for every market): IKEA wash-basin / vanity units (category
            // `washbasin`) harvested from each market's OWN live "bathroom vanities" listing (global category id
            // 20719 via the /cat/x-{id}/ cross-market redirect), then EACH product page re-read live: JSON-LD price +
            // priceCurrency (currency-matched to the market: EUR/GBP/NOK/SEK/DKK) + og:image + og:title. Non-fixture
            // items (shelves) pruned by a multilingual wash-basin name guard; budget→premium price spread, ~8-9/market.
            // Fills the washbasin core that was 0 in 13 markets (only HR/DK had it, via Pevex/VVS). Deterministic curl,
            // no fabrication, all image-verified. Re-check before production.
            "/catalog/real-ikea-bathroom-washbasin-10-178.json",
            // Sprint 10.178: IKEA bathroom MIRRORS (category `decor`) per market (global cat ids 20490/20498/48006),
            // same live re-read pipeline, multilingual "mirror" name guard, ~6/market ×15. Pairs with the planner
            // change that promoted bathroom `decor` to a comfort category so a mirror actually surfaces in the plan.
            "/catalog/real-ikea-bathroom-mirror-10-178.json",
            // Sprint 10.178 (P2 kitchen): IKEA complete/modular kitchen SETS (category `kitchen-set` — KNOXHULT /
            // SUNNERSTA / ÄSPINGE) for the 12 markets that had 0 (HR/AT/DE already had them). Harvested from the
            // complete-unit categories (48978/48986/700404) via the cross-market redirect, per-product live price +
            // currency + og:image, multilingual "kitchen" name guard, 8/market. Feeds the "Kompletna kuhinja" mode.
            "/catalog/real-ikea-kitchen-sets-eu-10-178.json",
            // Sprint 10.178 (P2 kitchen): IKEA kitchen APPLIANCES (oven/hob/cooker-hood/fridge/freezer/dishwasher/
            // microwave) ported from the HR/AT/DE set to the 12 other markets via the bare global-article trick
            // (/p/-{article}/); each row re-read live (price + currency-match + og:image), rows that 404 / bounce to
            // /cat/ / mismatch currency dropped (so GB is honestly thin — IKEA barely stocks appliances there). Added
            // to a plan only on explicit request (must-have), never forced — same behaviour as HR/AT/DE.
            "/catalog/real-ikea-kitchen-appliances-eu-10-178.json",
            // Sprint 10.178 (P3 GB core): IKEA GB depth for the below-floor cells — sofa 10→21, bed 11→20,
            // mattress 12→17 — harvested from GB sofa/bed/mattress category listings, live price + og:image, price
            // spread. GB is IKEA-only (no JYSK in the UK), so this is the available diversity lever.
            "/catalog/real-ikea-gb-core-10-178.json",
            // Sprint 10.178 (GB bathroom FIXTURES): Victorian Plumbing (victorianplumbing.co.uk) — the one clean-SSR
            // GB sanitary retailer (DE/IT sanitary sites probed 403/JS-gated, per the 10.169b finding). 6 toilets +
            // 28 baths/showers (category toilet / bath-shower), name (og:title) + GBP price (JSON-LD Offer) + og:image
            // read live per product. Gives GB the WC/bath fixtures IKEA doesn't sell (GB had 0). Re-check before production.
            "/catalog/real-vp-gb-bathroom-10-178.json"
    );

    /**
     * The catalog resources this seeder imports, exposed (package-private) for build-time guard tests
     * such as the duplicate-{@code productUrl} check — so a future catalog file is covered automatically.
     */
    static List<String> snapshotResources() {
        return SNAPSHOT_RESOURCES;
    }

    private final ProductRepository productRepository;
    private final RetailerSnapshotImportService snapshotImportService;
    private final ObjectMapper objectMapper;
    private final boolean seedEnabled;

    public RealCatalogSeeder(ProductRepository productRepository,
                             RetailerSnapshotImportService snapshotImportService,
                             ObjectMapper objectMapper,
                             @Value("${budgetspace.real-catalog.seed-enabled:true}") boolean seedEnabled) {
        this.productRepository = productRepository;
        this.snapshotImportService = snapshotImportService;
        this.objectMapper = objectMapper;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Real catalog seed: disabled via budgetspace.real-catalog.seed-enabled=false; keeping existing catalog as-is.");
            return;
        }
        log.info("Real catalog seed: starting (resources={}).", SNAPSHOT_RESOURCES);
        int importedCount = 0;
        try {
            List<RetailerProductSnapshotDto> snapshot = loadAllSnapshots();
            if (snapshot.isEmpty()) {
                log.warn("Real catalog seed: no snapshot rows found on classpath; living-room will rely on whatever remains after legacy cleanup.");
            } else {
                ImportSummaryDto summary = snapshotImportService.importSnapshot(snapshot);
                importedCount = summary.created() + summary.updated();
                log.info("Real catalog seed: imported real products (received={}, created={}, updated={}, skipped={}).",
                        summary.totalReceived(), summary.created(), summary.updated(), summary.skipped());
                if (!summary.errors().isEmpty()) {
                    log.warn("Real catalog seed: {} snapshot row(s) rejected by validation and not imported.", summary.errors().size());
                }
            }
        } catch (Exception exception) {
            log.error("Real catalog seed: import failed; legacy sample living-room data will still be retired so users are never shown placeholder links.", exception);
        }
        // Always retire fake living-room when real-catalog mode is enabled, even on import failure.
        int retired = retireLegacyLivingRoomProducts();
        log.info("Real catalog seed: retired {} legacy sample living-room product(s).", retired);
        logCatalogSummary();
        log.info("Real catalog seed: done (realProductsImported={}).", importedCount);
    }

    private List<RetailerProductSnapshotDto> loadAllSnapshots() throws Exception {
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : SNAPSHOT_RESOURCES) {
            List<RetailerProductSnapshotDto> rows = loadSnapshot(resource);
            if (rows.isEmpty()) {
                log.warn("Real catalog seed: resource {} missing or empty; skipping.", resource);
            } else {
                log.info("Real catalog seed: loaded {} row(s) from {}.", rows.size(), resource);
                all.addAll(rows);
            }
        }
        return all;
    }

    private List<RetailerProductSnapshotDto> loadSnapshot(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            return objectMapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }

    /**
     * Legacy sample rows (from {@code data.sql}) have no {@code sourceReference}. Remove their
     * {@code living-room} tag so the planner only sees the real imported products for the living
     * room; delete a sample product that has no other room left. Idempotent: a sample product that
     * has already lost its {@code living-room} tag is skipped on subsequent runs.
     *
     * @return how many legacy products were retired or trimmed.
     */
    private int retireLegacyLivingRoomProducts() {
        int affected = 0;
        for (Product product : productRepository.findAll()) {
            if (notBlank(product.getSourceReference())) continue; // real / imported product, keep
            if (!hasLivingRoom(product.getRoomTags())) continue;
            String remaining = stripLivingRoomTag(product.getRoomTags());
            if (remaining.isBlank()) {
                productRepository.delete(product);
            } else {
                product.setRoomTags(remaining);
                productRepository.save(product);
            }
            affected++;
        }
        return affected;
    }

    /** Logs the resulting catalog mix so an operator can confirm the seed worked. */
    private void logCatalogSummary() {
        List<Product> all = productRepository.findAll();
        long ikea = all.stream().filter(product -> "IKEA".equalsIgnoreCase(product.getRetailer())).count();
        long jysk = all.stream().filter(product -> "JYSK".equalsIgnoreCase(product.getRetailer())).count();
        long usableLivingRoom = all.stream()
                .filter(product -> hasLivingRoom(product.getRoomTags()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .count();
        log.info("Real catalog summary: totalProducts={}, IKEA={}, JYSK={}, usableLivingRoom={}.",
                all.size(), ikea, jysk, usableLivingRoom);
        if (usableLivingRoom == 0) {
            log.warn("Real catalog summary: no usable living-room products after seeding; the planner will return an empty/insufficient result for living-room requests.");
        }
    }

    /** Removes the {@code living-room} tag from a comma-separated room-tag string. */
    static String stripLivingRoomTag(String roomTags) {
        if (roomTags == null || roomTags.isBlank()) return "";
        return Arrays.stream(roomTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .filter(tag -> !tag.equalsIgnoreCase(LIVING_ROOM))
                .collect(Collectors.joining(","));
    }

    private boolean hasLivingRoom(String roomTags) {
        if (roomTags == null) return false;
        return Arrays.stream(roomTags.split(","))
                .map(String::trim)
                .anyMatch(tag -> tag.equalsIgnoreCase(LIVING_ROOM));
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
