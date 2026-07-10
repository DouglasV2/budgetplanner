package ai.budgetspace.product;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProductTaxonomy {
    /** A product whose last check is older than this is shown as "provjeri u trgovini". */
    public static final int STALE_AFTER_DAYS = 14;

    public static final Set<String> SOURCE_TYPES = Set.of(
            "manual",
            "retailer-snapshot",
            "future-scraper",
            // Sprint 10.14: explicit import-source provenance (see CatalogSourcePolicy). Additive — the
            // pre-10.14 values above stay valid; "retailer-snapshot" == historical "public-product-page".
            CatalogSourcePolicy.SOURCE_MANUAL_VERIFIED,
            CatalogSourcePolicy.SOURCE_PUBLIC_PRODUCT_PAGE,
            CatalogSourcePolicy.SOURCE_OFFICIAL_FEED,
            CatalogSourcePolicy.SOURCE_AFFILIATE_FEED,
            // Sprint 10.21: second-hand marketplace listing delivered by a compliant feed/API (never
            // scraped). See CatalogSourcePolicy + docs/marketplace-sourcing.md.
            CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING
    );

    public static final Set<String> DATA_QUALITIES = Set.of(
            "complete",
            "partial",
            "needs-review"
    );

    public static final Set<String> SUPPORTED_RETAILERS = Set.of(
            "IKEA",
            "JYSK",
            "VVS Eksperten",
            // Sprint 10.178: Victorian Plumbing (victorianplumbing.co.uk) — GB sanitary-ware specialist; product
            // pages SSR og:title + JSON-LD Offer price (GBP) + og:image, so its toilets/baths/showers are web-verifiable.
            "Victorian Plumbing",
            "Pevex",
            "Emmezeta",
            "Decathlon",
            "Lesnina",
            // Sprint 10.16: additional EU retailers. Fetchable + hand-verified (have products):
            "Harvey Norman",
            "Namjestaj.hr",
            "Otto",
            "Segmüller",
            "Poco",
            // Sprint 10.36: France — Camif (camif.fr) is reachable + serves the price in static HTML
            // (JSON-LD offers.price / visible €) + og:image, so it is web-verifiable like IKEA/JYSK.
            "Camif",
            // Sprint 10.43: Spain — Kenay Home + Banak Importa serve the price in static HTML (JSON-LD /
            // visible €) on product pages, web-verifiable per product.
            "Kenay Home",
            "Banak Importa",
            // Sprint 10.44: Netherlands — Leen Bakker + Kwantum serve static prices on product pages.
            "Leen Bakker",
            "Kwantum",
            // Sprint 10.45: depth — Moviflor (moviflor.pt, PT) + Nábytok (nabytok.sk, SK) serve static
            // prices + og:image on product pages, web-verifiable per product like IKEA/JYSK.
            "Moviflor",
            "Nábytok",
            // Sprint 10.48: retail re-sweep — more verified static-priced retailers per market (JSON-LD /
            // PrestaShop itemprop / Shopify / visible €). Conforama (listed below) flips to verified for IT.
            "Svijetnamještaja",
            "Svetpohištva",
            "Interio",
            "Masku",
            "Lovely Meubles",
            "JOM",
            "Sítio do Móvel",
            "Miroytengo",
            "Merkamueble",
            "Muebles BOOM",
            "Pronto Wonen",
            "Drevona",
            "ASKO Nábytok",
            // Known/targeted but currently feed-required (403/anti-bot/JS-only or out-of-scope). Registered
            // so the system is aware of them and a feed can target them later; they carry no products yet.
            "Momax",
            "Prima Namještaj",
            "Perfecta Dreams",
            "Bauhaus",
            "FeroTerm",
            "Merkur",
            "Dipo",
            "Wayfair",
            "Home24",
            "Roller",
            "Kika",
            "Leiner",
            "XXXLutz",
            // Sprint 10.36: major French furniture chains probed 2026-06-18 — all anti-bot (DataDome /
            // Cloudflare 403) or JS-only, so feed-required. We never bypass the protection.
            "Conforama",
            "But",
            "Maisons du Monde",
            "La Redoute",
            "Fly",
            "Habitat",
            "Cdiscount",
            "Vente-unique",
            // Sprint 10.43: Spain — Muebles La Fábrica's product pages reset the connection (anti-bot),
            // though its homepage is reachable → feed-required.
            "Muebles La Fabrica",
            // Sprint 10.45: Finland — Sotka (sotka.fi) renders product prices client-side (JS-only); the
            // static HTML carries no price → feed-required.
            "Sotka",
            // Sprint 10.21: second-hand consumer marketplaces. Feed/API-only (OFFICIAL_FEED_REQUIRED) —
            // never scraped; carry no products until a compliant feed exists. See docs/marketplace-sourcing.md.
            "Njuškalo",
            "Facebook Marketplace",
            // Sprint 10.49: per-country second-hand marketplace placeholders — registered so each market has a
            // "Rabljeno" source slot ready to plug an official/partner/affiliate feed (never scraped). eBay has
            // a public Browse API (a real first source); the rest need a partner/affiliate/export agreement.
            "eBay",            // multi-market, public Browse API (used furniture, by location)
            "Bolha",           // SI
            "Willhaben",       // AT
            "Kleinanzeigen",   // DE
            "Subito",          // IT
            "Tori",            // FI
            "Leboncoin",       // FR
            "Marktplaats",     // NL
            "Bazoš",           // SK
            "Wallapop",        // ES
            "OLX",             // PT (and others)
            "Finn",            // NO (finn.no)
            "Blocket",         // SE
            "DBA"              // DK (Den Blå Avis)
    );

    public static final Set<String> AVAILABILITY_STATUSES = Set.of(
            "in-stock",
            "limited",
            "unavailable",
            "check-store"
    );

    public static final Set<String> KNOWN_CATEGORIES = Set.of(
            "sofa",
            "tv-unit",
            "table",
            "rug",
            "lighting",
            "storage",
            "decor",
            "bed",
            "mattress",
            "desk",
            "chair",
            "gym-equipment",
            // Sprint 10.7: new-room categories.
            "dining-table",
            "dining-chair",
            "kitchen-storage",
            "kitchen-cart",
            // Sprint 10.175 (kitchen Increment 1): a complete/modular kitchen unit (e.g. IKEA KNOXHULT/ENHET),
            // sold as one priced product. Used only by the complete-kitchen flow, not the freestanding room flow.
            "kitchen-set",
            // Sprint 10.176 (kitchen Increment 3): kitchen appliances. Added to the normal plan only when the user
            // asks for them (must-have), never forced into a generic kitchen plan.
            "oven",
            "hob",
            "cooker-hood",
            "fridge",
            "freezer",
            "dishwasher",
            "microwave",
            "nightstand",
            "wardrobe",
            "dresser",
            // Sprint 10.117: soft furnishings (curtains, cushions, throws).
            "textiles",
            // Sprint 10.169: bathroom fixtures / sanitary ware (Pevex HR) — the pieces a real bathroom is built
            // around, which IKEA/JYSK don't sell. bath-shower covers both bathtubs and shower enclosures.
            "toilet",
            "washbasin",
            "bath-shower"
    );

    public static final Set<String> KNOWN_STYLES = Set.of(
            "modern",
            "minimal",
            "cozy",
            "classic",
            "industrial",
            "boho"
    );

    public static final Set<String> KNOWN_ROOMS = Set.of(
            "living-room",
            "bedroom",
            "home-office",
            "home-gym",
            // Sprint 10.7: new rooms.
            "kitchen",
            "dining-room",
            "hallway",
            "bathroom",
            // Sprint 10.121: studio / one-room flat (combined living + bedroom).
            "studio"
    );

    private static final Map<String, String> CATEGORY_ALIASES = categoryAliases();
    private static final Map<String, String> STYLE_ALIASES = styleAliases();
    private static final Map<String, String> ROOM_ALIASES = roomAliases();
    private static final Map<String, List<String>> COLOR_KEYWORDS = colorKeywords();
    private static final Map<String, List<String>> MATERIAL_KEYWORDS = materialKeywords();

    private ProductTaxonomy() {
    }

    public static Optional<String> normalizeRetailer(String retailer) {
        if (retailer == null || retailer.isBlank()) return Optional.empty();
        String trimmed = retailer.trim();
        return SUPPORTED_RETAILERS.stream()
                .filter(supported -> supported.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public static Optional<String> normalizeCategory(String category) {
        if (category == null || category.isBlank()) return Optional.empty();
        return Optional.ofNullable(CATEGORY_ALIASES.get(normalizeKey(category)));
    }

    public static Optional<String> normalizeStyle(String style) {
        if (style == null || style.isBlank()) return Optional.empty();
        return Optional.ofNullable(STYLE_ALIASES.get(normalizeKey(style)));
    }

    public static Optional<String> normalizeRoom(String room) {
        if (room == null || room.isBlank()) return Optional.empty();
        return Optional.ofNullable(ROOM_ALIASES.get(normalizeKey(room)));
    }

    public static boolean isKnownCategory(String category) {
        return normalizeCategory(category).isPresent();
    }

    public static boolean isKnownStyle(String style) {
        return normalizeStyle(style).isPresent();
    }

    public static boolean isKnownRoom(String room) {
        return normalizeRoom(room).isPresent();
    }

    public static boolean isSupportedAvailability(String status) {
        return status != null && AVAILABILITY_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT));
    }

    public static String normalizeAvailability(String status) {
        if (status == null || status.isBlank()) return "in-stock";
        return status.trim().toLowerCase(Locale.ROOT);
    }

    public static Optional<String> normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return Optional.empty();
        String key = sourceType.trim().toLowerCase(Locale.ROOT);
        return SOURCE_TYPES.contains(key) ? Optional.of(key) : Optional.empty();
    }

    public static boolean isSupportedSourceType(String sourceType) {
        return normalizeSourceType(sourceType).isPresent();
    }

    /** The allowed source types as a stable, sorted, comma-separated string (for error messages). */
    public static String supportedSourceTypesText() {
        return SOURCE_TYPES.stream().sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    public static Optional<String> normalizeDataQuality(String dataQuality) {
        if (dataQuality == null || dataQuality.isBlank()) return Optional.empty();
        String key = dataQuality.trim().toLowerCase(Locale.ROOT);
        return DATA_QUALITIES.contains(key) ? Optional.of(key) : Optional.empty();
    }

    public static boolean isSupportedDataQuality(String dataQuality) {
        return normalizeDataQuality(dataQuality).isPresent();
    }

    /** True if the value can be read as a date (yyyy-MM-dd or an ISO date-time). */
    public static boolean isParseableDate(String value) {
        return parseDate(value) != null;
    }

    /**
     * Freshness v0: a product is "stale" when its last check is missing, unreadable, or
     * older than {@link #STALE_AFTER_DAYS}. Stale products still enter the plan, but the UI
     * tells the user to check price and availability in the store.
     */
    public static boolean isStale(String lastCheckedAt) {
        LocalDate checked = parseDate(lastCheckedAt);
        if (checked == null) return true;
        return checked.isBefore(LocalDate.now().minusDays(STALE_AFTER_DAYS));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toLocalDate();
            } catch (RuntimeException alsoIgnored) {
                return null;
            }
        }
    }

    public static boolean canEnterPlanner(Product product) {
        if (product == null) return false;
        if (!product.isInStock()) return false;
        if (product.getPrice() == null || product.getPrice().signum() <= 0) return false;
        String availability = normalizeAvailability(product.getAvailabilityStatus());
        if ("unavailable".equals(availability)) return false;
        // Sprint 9.2: products still marked for review (e.g. collected with weak data) must
        // not be used by the planner until they are fixed into a usable product.
        String quality = product.getDataQuality();
        if (quality != null && "needs-review".equalsIgnoreCase(quality.trim())) return false;
        if (product.getRoomTags() == null || product.getRoomTags().isBlank()) return false;
        // Style matching expects a style, so a product without one cannot enter a plan.
        return product.getStyleTags() != null && !product.getStyleTags().isBlank();
    }

    private static Map<String, String> categoryAliases() {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "sofa", "sofa", "kauč", "kauc", "couch");
        alias(aliases, "tv-unit", "tv-unit", "tv unit", "tv komoda", "komoda za tv");
        alias(aliases, "table", "table", "stolić", "stolic", "coffee table", "klub stolić", "klub stolic");
        alias(aliases, "rug", "rug", "tepih", "carpet");
        alias(aliases, "lighting", "lighting", "rasvjeta", "lampa", "lamp", "svjetiljka");
        alias(aliases, "storage", "storage", "ormar", "polica", "pohrana", "regal", "komoda");
        alias(aliases, "decor", "decor", "dekor", "dekoracije", "decoration", "ukrasi");
        alias(aliases, "bed", "bed", "krevet");
        alias(aliases, "mattress", "mattress", "madrac");
        alias(aliases, "desk", "desk", "stol", "radni stol", "pisaći stol", "pisaci stol", "office desk");
        alias(aliases, "chair", "chair", "stolica", "uredska stolica");
        alias(aliases, "gym-equipment", "gym-equipment", "gym equipment", "oprema za vježbanje", "oprema za vjezbanje", "fitness equipment");
        // Sprint 10.7: new-room categories.
        alias(aliases, "dining-table", "dining-table", "dining table", "blagovaonski stol", "trpezarijski stol", "stol za blagovanje");
        alias(aliases, "dining-chair", "dining-chair", "dining chair", "blagovaonska stolica", "trpezarijska stolica", "stolica za blagovanje");
        alias(aliases, "kitchen-storage", "kitchen-storage", "kitchen storage", "kuhinjski ormarić", "kuhinjski ormaric", "kuhinjska polica", "kuhinjsko spremanje");
        alias(aliases, "kitchen-cart", "kitchen-cart", "kitchen cart", "kuhinjska kolica", "servirna kolica");
        // Sprint 10.175: complete/modular kitchen sets.
        alias(aliases, "kitchen-set", "kitchen-set", "modular-kitchen", "complete-kitchen", "kitchen-package",
                "kompletna kuhinja", "modularna kuhinja", "cijela kuhinja", "modular kitchen", "complete kitchen");
        // Sprint 10.176: kitchen appliances.
        alias(aliases, "oven", "oven", "pecnica", "pećnica", "rerna", "backofen", "forno");
        alias(aliases, "hob", "hob", "ploca za kuhanje", "ploča za kuhanje", "kuhalo", "indukcijska ploca", "cooktop", "kochfeld");
        alias(aliases, "cooker-hood", "cooker-hood", "napa", "kuhinjska napa", "dunstabzug", "extractor hood", "cooker hood");
        alias(aliases, "fridge", "fridge", "hladnjak", "frizider", "frižider", "refrigerator", "kuhlschrank");
        alias(aliases, "freezer", "freezer", "zamrzivac", "zamrzivač", "skrinja", "gefrierschrank");
        alias(aliases, "dishwasher", "dishwasher", "perilica posuda", "perilica posuđa", "perilica sudja", "geschirrspuler");
        alias(aliases, "microwave", "microwave", "mikrovalna", "mikrovalna pecnica", "mikrowelle");
        alias(aliases, "nightstand", "nightstand", "noćni ormarić", "nocni ormaric", "noćni ormar");
        alias(aliases, "wardrobe", "wardrobe", "ormar za odjeću", "ormar za odjecu", "garderobni ormar", "plakar");
        alias(aliases, "dresser", "dresser", "komoda s ladicama", "ladičar", "ladicar");
        // Sprint 10.117: soft furnishings (curtains, cushions, throws/blankets).
        alias(aliases, "textiles", "textiles", "tekstil", "zavjese", "zavjesa", "jastuci", "jastuk", "ukrasni jastuk",
                "deke", "deka", "dekica", "pledovi", "pled", "prekrivač", "prekrivac", "curtains", "cushions", "throws", "blanket");
        // Sprint 10.169: bathroom fixtures (Pevex HR sanitary ware).
        alias(aliases, "toilet", "toilet", "wc", "wc školjka", "wc skoljka", "školjka", "skoljka", "zahod", "monoblok");
        alias(aliases, "washbasin", "washbasin", "umivaonik", "lavabo", "sink", "basin");
        alias(aliases, "bath-shower", "bath-shower", "bath shower", "kada", "bathtub", "tuš", "tus", "tuš kabina",
                "tus kabina", "tuš kada", "tuš stijena", "tuš vrata", "shower");
        return Map.copyOf(aliases);
    }

    private static Map<String, String> styleAliases() {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "modern", "modern", "moderno");
        alias(aliases, "minimal", "minimal", "minimalno", "simple", "clean", "jednostavno", "scandinavian", "skandinavski");
        alias(aliases, "cozy", "cozy", "toplo", "warm", "ugodno");
        alias(aliases, "classic", "classic", "klasično", "klasicno");
        alias(aliases, "industrial", "industrial", "industrijski");
        alias(aliases, "boho", "boho", "natural", "prirodno");
        return Map.copyOf(aliases);
    }

    private static Map<String, String> roomAliases() {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "living-room", "living-room", "living room", "dnevni boravak", "dnevni", "boravak");
        alias(aliases, "bedroom", "bedroom", "spavaća soba", "spavaca soba", "spavaća", "spavaca");
        alias(aliases, "home-office", "home-office", "home office", "radni kutak", "ured", "office");
        alias(aliases, "home-gym", "home-gym", "home gym", "kućna teretana", "kucna teretana", "teretana", "gym");
        // Sprint 10.7: new rooms.
        alias(aliases, "kitchen", "kitchen", "kuhinja", "kuhinju", "kuhinji");
        alias(aliases, "dining-room", "dining-room", "dining room", "dining", "blagovaonica", "blagovaonicu", "trpezarija");
        alias(aliases, "hallway", "hallway", "hodnik", "predsoblje", "ulazni prostor");
        alias(aliases, "bathroom", "bathroom", "kupaonica", "kupaonicu", "kupaona", "kupatilo");
        // Sprint 10.121: studio / one-room flat across the 15 markets' languages.
        alias(aliases, "studio", "studio", "garsonijera", "garsonjera", "garsonijeru", "studio apartman",
                "studio apartment", "studio flat", "bedsit", "monolocale", "einzimmerwohnung", "einzimmer",
                "garconniere", "garçonnière", "yksiö", "yksio", "estudio", "estúdio", "monoambiente", "kitnet",
                "garsónka", "garsonka", "ettromsleilighet", "etta", "etværelses", "etvaerelses", "studiootje",
                "eenkamerappartement", "studette");
        return Map.copyOf(aliases);
    }

    /**
     * Sprint 10.7: maps a canonical colour tag to accent-free Croatian/English substrings that,
     * when found in a product name/description, imply that colour. Used to populate
     * {@code Product.colorTags} at import time when a snapshot does not declare it.
     */
    private static Map<String, List<String>> colorKeywords() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("white", List.of("bijel", "white"));
        map.put("black", List.of("crn", "black"));
        map.put("grey", List.of("siv", "grey", "gray", "antracit"));
        map.put("beige", List.of("bezboj", "krem", "cream", "bjelokost", "ivory"));
        map.put("brown", List.of("smed", "braon", "brown"));
        map.put("green", List.of("zelen", "green", "maslinast"));
        map.put("blue", List.of("plav", "blue", "teget", "navy"));
        map.put("yellow", List.of("zut", "yellow", "oker"));
        map.put("red", List.of("crven", "red", "bordo"));
        map.put("pink", List.of("roza", "roze", "pink"));
        map.put("natural", List.of("natur", "prirodn", "hrast", "oak", "orah"));
        map.put("gold", List.of("zlatn", "gold", "mjed", "brass"));
        return Map.copyOf(map);
    }

    /** Sprint 10.7: canonical material tag → substrings implying that material (see {@link #colorKeywords()}). */
    private static Map<String, List<String>> materialKeywords() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("wood", List.of("drv", "hrast", "oak", "orah", "wood", "bambus", "bamboo", "iverica", "furnir"));
        map.put("metal", List.of("metal", "celik", "aluminij", "krom", "zeljezo"));
        map.put("glass", List.of("stakl", "glass"));
        map.put("fabric", List.of("tkanin", "tekstil", "pamuk", "platno", "fabric", "lan"));
        map.put("leather", List.of("koza", "kozn", "leather"));
        map.put("rattan", List.of("ratan", "rattan", "pleten"));
        map.put("marble", List.of("mramor", "marble"));
        map.put("velvet", List.of("barsun", "samt", "velvet", "plis"));
        return Map.copyOf(map);
    }

    /** Derives canonical colour tags from one or more free-text fields (e.g. product name). */
    public static List<String> deriveColorTags(String... texts) {
        return deriveTags(COLOR_KEYWORDS, texts);
    }

    /** Derives canonical material tags from one or more free-text fields (e.g. product name). */
    public static List<String> deriveMaterialTags(String... texts) {
        return deriveTags(MATERIAL_KEYWORDS, texts);
    }

    private static List<String> deriveTags(Map<String, List<String>> vocabulary, String... texts) {
        String haystack = normalizeText(Arrays.stream(texts)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a + " " + b));
        if (haystack.isBlank()) return List.of();
        List<String> found = new ArrayList<>();
        vocabulary.forEach((tag, keywords) -> {
            for (String keyword : keywords) {
                if (haystack.contains(keyword)) {
                    found.add(tag);
                    break;
                }
            }
        });
        return List.copyOf(found);
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static void alias(Map<String, String> aliases, String canonical, String... values) {
        aliases.put(normalizeKey(canonical), canonical);
        for (String value : values) {
            aliases.put(normalizeKey(value), canonical);
        }
    }

    private static String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalized = normalized.replace('-', ' ').replace('_', ' ');
        normalized = normalized.replaceAll("[^a-z0-9 ]", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }
}
