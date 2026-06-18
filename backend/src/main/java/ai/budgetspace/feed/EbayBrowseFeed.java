package ai.budgetspace.feed;

import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.CatalogSourcePolicy;
import ai.budgetspace.product.MarketplaceListingFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 10.51 — the real second-hand marketplace feed: eBay Browse API ({@code item_summary/search}) for
 * used furniture, by market. It is the first compliant "Rabljeno" source that fills the per-country
 * placeholders. It honours every rule in {@code docs/marketplace-sourcing.md}:
 *
 * <ul>
 *   <li><strong>Official API only — never scrape.</strong> All access is the documented Browse API with an
 *       OAuth client-credentials token. Credentials are read from the environment ({@link EbayBrowseFeedProperties});
 *       nothing is committed. With no credentials the feed is dormant — {@link #isConfigured()} is {@code false}
 *       and {@link #fetchSnapshot()} makes no network call and imports nothing.</li>
 *   <li><strong>Every row marked second-hand</strong> ({@code secondHand=true},
 *       {@code sourceType=marketplace-listing}) with the seller's stated condition + city, so the planner keeps
 *       it out of the budget total and the UI shows it in the separate section.</li>
 *   <li><strong>Sold/expired guard before return.</strong> Every candidate runs through
 *       {@link MarketplaceListingFilter#shouldDrop} so a {@code PRODANO}/reserved/expired ad is never imported.
 *       (The Browse search already returns only live items; this is belt-and-suspenders.)</li>
 *   <li><strong>No fabrication.</strong> A row with no concrete price, photo, condition, link, or a furniture
 *       type we cannot confidently classify, is dropped — honesty over coverage.</li>
 * </ul>
 *
 * <p>eBay runs local marketplaces only in {@link EbayBrowseFeedProperties#SUPPORTED_MARKETS} (DE/IT/AT/FR/NL/ES);
 * the other BudgetSpace markets keep their own placeholders. The category classification + Browse query are a
 * sensible first cut that is tuned against live responses once the owner's developer key is active (the key
 * gates the live smoke test, not this code — the mapping is unit-tested on a fixture).</p>
 */
public class EbayBrowseFeed implements MarketplaceFeed {

    private static final Logger log = LoggerFactory.getLogger(EbayBrowseFeed.class);

    private static final String RETAILER = "eBay";
    private static final String OAUTH_SCOPE = "https://api.ebay.com/oauth/api_scope";
    private static final Duration TOKEN_SAFETY_MARGIN = Duration.ofSeconds(60);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final EbayBrowseFeedProperties properties;
    private final EbayTransport transport;
    private final ObjectMapper objectMapper;

    // OAuth app token cache (the token is marketplace-agnostic — one token serves every market).
    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public EbayBrowseFeed(EbayBrowseFeedProperties properties) {
        this(properties, new HttpClientTransport(), new ObjectMapper());
    }

    // Package-private: lets a test inject a fake transport (and exercise the pure mapping) without a network.
    EbayBrowseFeed(EbayBrowseFeedProperties properties, EbayTransport transport, ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public String retailer() {
        return RETAILER;
    }

    @Override
    public String market() {
        return null; // multi-market — each row carries its own market code.
    }

    @Override
    public String sourceType() {
        return CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String statusReason() {
        if (isConfigured()) {
            return "eBay Browse API konfiguriran (tržišta=" + properties.markets() + ") — uvozi rabljeni "
                    + "namještaj (conditions=USED), svaki red kroz MarketplaceListingFilter.";
        }
        return "eBay placeholder — nema App ID/Cert ID u okolini "
                + "(budgetspace.marketplace-feeds.ebay.client-id/client-secret). Uvozi 0. Nikad se ne scrape-a.";
    }

    @Override
    public List<RetailerProductSnapshotDto> fetchSnapshot() {
        if (!isConfigured()) {
            return List.of(); // dormant — never touch the network without credentials.
        }
        String token;
        try {
            token = accessToken();
        } catch (Exception exception) {
            log.error("eBay Browse: OAuth token nije dohvaćen — preskačem feed (0 redaka).", exception);
            return List.of();
        }
        Instant now = Instant.now();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String market : properties.markets()) {
            try {
                String json = searchUsedFurniture(token, market);
                List<RetailerProductSnapshotDto> rows = mapSearchResponse(json, market, now);
                all.addAll(rows);
                log.info("eBay Browse [{}]: {} rabljenih redaka nakon filtra.", market, rows.size());
            } catch (Exception exception) {
                // A broken market must not take the feed (or the other markets) down.
                log.error("eBay Browse [{}]: dohvat nije uspio — preskačem ovo tržište, nastavljam.", market, exception);
            }
        }
        return all;
    }

    // --- OAuth client-credentials (one app token, cached until just before expiry) ---

    private synchronized String accessToken() throws IOException, InterruptedException {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = Map.of(
                "Authorization", "Basic " + basic,
                "Content-Type", "application/x-www-form-urlencoded",
                "Accept", "application/json");
        String body = "grant_type=client_credentials&scope=" + URLEncoder.encode(OAUTH_SCOPE, StandardCharsets.UTF_8);

        String response = transport.post(properties.apiBaseUrl() + "/identity/v1/oauth2/token", headers, body);
        JsonNode node = objectMapper.readTree(response == null ? "{}" : response);
        String token = node.path("access_token").asText("");
        long expiresIn = node.path("expires_in").asLong(7200L);
        if (token.isBlank()) {
            throw new IllegalStateException("eBay OAuth: odgovor bez access_token-a.");
        }
        cachedToken = token;
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn).minus(TOKEN_SAFETY_MARGIN);
        return token;
    }

    private String searchUsedFurniture(String token, String market) throws IOException, InterruptedException {
        // category_ids scopes to furniture (no per-language query needed); the filter keeps it used + local.
        String filter = URLEncoder.encode("conditions:{USED},itemLocationCountry:" + market, StandardCharsets.UTF_8);
        String url = properties.apiBaseUrl() + "/buy/browse/v1/item_summary/search"
                + "?category_ids=" + URLEncoder.encode(properties.furnitureCategoryId(), StandardCharsets.UTF_8)
                + "&filter=" + filter
                + "&limit=" + properties.limitPerMarket();
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + token,
                "X-EBAY-C-MARKETPLACE-ID", "EBAY_" + market,
                "Accept", "application/json");
        return transport.get(url, headers);
    }

    // --- Mapping (pure; unit-tested on a fixture, no network) ---

    /** Parses a Browse {@code item_summary/search} response into verified second-hand snapshot rows. */
    List<RetailerProductSnapshotDto> mapSearchResponse(String json, String market, Instant now) throws IOException {
        JsonNode root = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        JsonNode items = root.path("itemSummaries");
        if (!items.isArray()) {
            return List.of();
        }
        List<RetailerProductSnapshotDto> rows = new ArrayList<>();
        for (JsonNode item : items) {
            RetailerProductSnapshotDto row = toSnapshot(item, market, now);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private RetailerProductSnapshotDto toSnapshot(JsonNode item, String market, Instant now) {
        String title = text(item, "title");
        String itemId = text(item, "itemId");
        String url = text(item, "itemWebUrl");
        BigDecimal price = parsePrice(item.path("price"));
        String image = parseImage(item);
        String condition = firstNonBlank(text(item, "condition"), conditionFromId(text(item, "conditionId")));
        String location = parseSellerLocation(item.path("itemLocation"));
        String category = deriveCategory(title);

        // Honesty gates (docs §4): drop a row with no concrete price / photo / condition / link, or a
        // furniture type we cannot confidently classify. Better fewer correct rows than a guessed one.
        if (isBlank(title) || isBlank(itemId) || isBlank(url) || price == null
                || isBlank(image) || isBlank(condition) || category == null) {
            return null;
        }
        // Belt-and-suspenders sold/expired guard before a row can ever be imported.
        String nowIso = now.toString();
        if (MarketplaceListingFilter.shouldDrop(title, condition, nowIso, now)) {
            return null;
        }

        return new RetailerProductSnapshotDto(
                "ebay-" + itemId,                       // externalId — stable per listing, for dedup
                title,                                  // name
                RETAILER,                               // retailer
                category,                               // category (classified from the title)
                price,                                  // price (asking price; UI marks it "okvirno")
                url,                                    // productUrl — the live listing
                image,                                  // imageUrl (seller photo; not shown unless rights verified)
                "in-stock",                             // availabilityStatus
                null,                                   // deliveryNote (UI shows used-item copy instead)
                nowIso,                                 // lastCheckedAt — marketplace freshness window starts now
                categoryToRooms(category),              // roomTags
                deriveStyles(title),                    // styleTags
                null,                                   // priceTier (inferred)
                CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING, // sourceType
                RETAILER,                               // sourceName
                url,                                    // sourceReference — the listing URL
                "partial",                              // dataQuality (link-out, placeholder image)
                "eBay Browse API used listing",         // dataQualityNotes
                null,                                   // colorTags (derived from name on import)
                null,                                   // materialTags (derived from name on import)
                null,                                   // reviewCount (no reviews on a private listing)
                null,                                   // reviewsUrl
                market,                                 // market
                null,                                   // reviewRating
                false,                                  // imageVerified — seller photo, display rights unverified
                null,                                   // originalPrice (no verified regular price)
                null,                                   // saleEndsAt
                true,                                   // secondHand
                condition,                              // conditionLabel — the seller's stated condition
                location);                              // sellerLocation — city/region for pickup distance
    }

    // --- Furniture-type classification (multilingual, most-specific first; unmappable -> drop) ---

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = categoryKeywords();

    private static Map<String, List<String>> categoryKeywords() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("mattress", List.of("matratze", "materasso", "matelas", "colchon", "matras", "mattress"));
        map.put("nightstand", List.of("nachttisch", "nachtkastchen", "comodino", "table de chevet", "chevet",
                "mesita de noche", "nachtkastje", "nightstand"));
        map.put("wardrobe", List.of("kleiderschrank", "armadio", "armoire", "armario", "kledingkast", "wardrobe"));
        map.put("dresser", List.of("kommode", "cassettiera", "commode", "comoda", "ladekast", "dresser", "drawers"));
        map.put("desk", List.of("schreibtisch", "scrivania", "bureau", "escritorio", "desk"));
        map.put("dining-table", List.of("esstisch", "eettafel", "tavolo da pranzo", "table a manger", "mesa de comedor"));
        map.put("dining-chair", List.of("esszimmerstuhl", "sedia da pranzo", "silla de comedor", "eetkamerstoel"));
        map.put("tv-unit", List.of("tv schrank", "tv mobel", "mobile tv", "porta tv", "meuble tv", "mueble tv",
                "tv meubel", "lowboard"));
        map.put("sofa", List.of("sofa", "couch", "divano", "canape", "bankstel"));
        map.put("bed", List.of("bett", "letto", "cama", "bed", "lit"));
        map.put("rug", List.of("teppich", "tappeto", "tapis", "alfombra", "tapijt", "rug", "carpet"));
        map.put("lighting", List.of("lampe", "lampada", "lampara", "lamp", "leuchte", "luster", "kronleuchter",
                "plafoniera", "lampadaire"));
        map.put("table", List.of("couchtisch", "beistelltisch", "tisch", "tavolino", "tavolo", "table", "mesa",
                "tafel", "salontafel"));
        map.put("chair", List.of("stuhl", "sessel", "sedia", "poltrona", "chaise", "fauteuil", "silla", "stoel", "chair"));
        map.put("storage", List.of("regal", "bucherregal", "scaffale", "libreria", "etagere", "estanteria",
                "boekenkast", "vitrine", "sideboard", "anrichte", "credenza", "buffet", "schrank", "kast"));
        return map;
    }

    /** Classifies a listing title into a known BudgetSpace category, or {@code null} when unsure (-> drop). */
    private static String deriveCategory(String title) {
        String haystack = normalize(title);
        if (haystack.isBlank()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (haystack.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /** Rooms a used item of this category can match — generous so it surfaces for the relevant requests. */
    private static List<String> categoryToRooms(String category) {
        return switch (category) {
            case "bed", "mattress", "nightstand", "wardrobe", "dresser" -> List.of("bedroom");
            case "desk" -> List.of("home-office");
            case "dining-table", "dining-chair" -> List.of("dining-room");
            case "chair" -> List.of("home-office", "living-room", "dining-room");
            case "rug" -> List.of("living-room", "bedroom", "home-office");
            case "lighting" -> List.of("living-room", "bedroom", "home-office", "hallway", "dining-room");
            case "storage" -> List.of("living-room", "bedroom", "home-office", "hallway", "kitchen");
            default -> List.of("living-room"); // sofa, tv-unit, table
        };
    }

    /**
     * A coarse, conservative style tag for ranking only (never shown as a hard claim). Defaults to "modern";
     * a few high-confidence title cues map to classic/industrial/boho. Import requires a known style.
     */
    private static List<String> deriveStyles(String title) {
        String haystack = normalize(title);
        if (haystack.contains("vintage") || haystack.contains("antik") || haystack.contains("antico")
                || haystack.contains("ancien") || haystack.contains("antiguo") || haystack.contains("retro")
                || haystack.contains("shabby")) {
            return List.of("classic");
        }
        if (haystack.contains("industrial") || haystack.contains("industrie")) {
            return List.of("industrial");
        }
        if (haystack.contains("rattan") || haystack.contains("rotin") || haystack.contains("rotan")
                || haystack.contains("boho")) {
            return List.of("boho");
        }
        return List.of("modern");
    }

    // --- JSON helpers ---

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private static BigDecimal parsePrice(JsonNode priceNode) {
        String value = text(priceNode, "value");
        if (value.isBlank()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(value);
            return price.signum() > 0 ? price : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String parseImage(JsonNode item) {
        String main = text(item.path("image"), "imageUrl");
        if (!main.isBlank()) {
            return main;
        }
        JsonNode thumbs = item.path("thumbnailImages");
        if (thumbs.isArray() && thumbs.size() > 0) {
            return text(thumbs.get(0), "imageUrl");
        }
        return "";
    }

    private static String parseSellerLocation(JsonNode location) {
        String city = text(location, "city");
        String country = text(location, "country");
        if (!city.isBlank()) {
            return country.isBlank() ? city : city + ", " + country;
        }
        return country;
    }

    /** A minimal fallback when only eBay's numeric {@code conditionId} is present (not the label). */
    private static String conditionFromId(String id) {
        return switch (id == null ? "" : id.trim()) {
            case "2000", "2010", "2020", "2030" -> "Refurbished";
            case "2500" -> "Seller refurbished";
            case "3000" -> "Used";
            case "4000" -> "Very good";
            case "5000" -> "Good";
            case "6000" -> "Acceptable";
            case "7000" -> "For parts or not working";
            default -> "";
        };
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return stripped.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : (fallback == null ? "" : fallback);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // --- HTTP transport (injectable so the mapping can be tested without a network) ---

    /** Minimal HTTP seam: the two calls the feed needs (token POST + search GET). */
    interface EbayTransport {
        String get(String url, Map<String, String> headers) throws IOException, InterruptedException;

        String post(String url, Map<String, String> headers, String formBody) throws IOException, InterruptedException;
    }

    /** Default transport over the JDK {@link HttpClient}; throws on a non-2xx response (with the body). */
    static final class HttpClientTransport implements EbayTransport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

        @Override
        public String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET();
            headers.forEach(request::header);
            return send(request.build());
        }

        @Override
        public String post(String url, Map<String, String> headers, String formBody) throws IOException, InterruptedException {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));
            headers.forEach(request::header);
            return send(request.build());
        }

        private String send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("eBay API HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            return response.body();
        }

        private static String truncate(String body) {
            if (body == null) {
                return "";
            }
            return body.length() <= 300 ? body : body.substring(0, 300) + "…";
        }
    }
}
