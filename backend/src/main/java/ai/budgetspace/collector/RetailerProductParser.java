package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectedProductDto;
import ai.budgetspace.dto.CollectorDefaultsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads basic product data from a product page's HTML, in this order:
 *
 * <ol>
 *   <li><b>JSON-LD</b> {@code <script type="application/ld+json">} with a Product schema,</li>
 *   <li><b>OpenGraph / meta</b> tags as a fallback,</li>
 *   <li>the HTML {@code <title>} as a last resort for the name,</li>
 *   <li>the caller's <b>defaults</b> for category / room / style (a page rarely states these).</li>
 * </ol>
 *
 * <p>It does not render JavaScript. If a page is JS-heavy and exposes nothing structured,
 * the parser returns a partial / needs-review result (or a missing price, which the service
 * then skips) — it never reaches for a headless browser.</p>
 */
@Component
public class RetailerProductParser {
    private static final Pattern LD_JSON = Pattern.compile("(?is)<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>");
    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern TITLE_TAG = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern PRICE_NUMBER = Pattern.compile("([0-9]+(?:[.,][0-9]{1,2})?)");

    private final ObjectMapper objectMapper;
    private final IkeaProductParser ikeaProductParser = new IkeaProductParser();

    public RetailerProductParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedProduct parse(String html, String url, String retailer, CollectorDefaultsDto defaults) {
        String pageHtml = html == null ? "" : html;
        List<String> warnings = new ArrayList<>();

        String name = null;
        BigDecimal price = null;
        String image = null;
        String availability = null;
        String externalId = null;
        String nameSource = null;
        String priceSource = null;

        JsonNode product = findJsonLdProduct(pageHtml);
        boolean hasJsonLd = product != null;
        if (hasJsonLd) {
            name = blankToNull(text(product, "name"));
            if (name != null) nameSource = "json-ld";
            externalId = firstText(product, "sku", "productID", "mpn", "gtin13", "gtin");
            image = imageFrom(product.get("image"));
            price = priceFromOffers(product.get("offers"));
            if (price != null) priceSource = "json-ld";
            availability = availabilityFromOffers(product.get("offers"));
        }

        if (name == null) {
            String ogTitle = metaContent(pageHtml, "og:title");
            if (notBlank(ogTitle)) {
                name = ogTitle;
                nameSource = "opengraph";
            }
        }
        if (image == null) image = metaContent(pageHtml, "og:image");
        if (price == null) {
            BigDecimal ogPrice = parsePrice(firstNonBlank(
                    metaContent(pageHtml, "product:price:amount"),
                    metaContent(pageHtml, "og:price:amount")));
            if (ogPrice != null) {
                price = ogPrice;
                priceSource = "opengraph";
            }
        }
        if (availability == null) {
            availability = mapAvailability(firstNonBlank(
                    metaContent(pageHtml, "product:availability"),
                    metaContent(pageHtml, "og:availability")));
        }
        if (name == null) {
            name = blankToNull(cleanText(extractTitle(pageHtml)));
            if (name != null) nameSource = "title";
        }

        // Retailer-specific v1 (IKEA): clean the name and prefer a stable article-number id.
        if (ikeaProductParser.handles(retailer, url)) {
            name = ikeaProductParser.refineName(name);
            if (!notBlank(externalId)) {
                String article = ikeaProductParser.articleNumberExternalId(url);
                if (notBlank(article)) {
                    externalId = article;
                } else {
                    warnings.add("Specifični podaci trgovine nisu pronađeni, korišten je izvedeni identifikator.");
                }
            }
        }

        boolean availabilityCertain = availability != null;
        if (availability == null) {
            availability = "check-store";
            warnings.add("Dostupnost nije pronađena, postavljena na provjeru u trgovini.");
        }

        String category = defaults == null ? null : blankToNull(defaults.category());
        List<String> roomTags = defaults == null ? null : nonEmpty(defaults.roomTags());
        List<String> styleTags = defaults == null ? null : nonEmpty(defaults.styleTags());
        if (category != null || roomTags != null || styleTags != null) {
            warnings.add("Korišteni su zadani podaci za kategoriju/prostorije/stil iz zahtjeva.");
        }

        if ("opengraph".equals(nameSource) || "title".equals(nameSource) || "opengraph".equals(priceSource)) {
            warnings.add("Korišten je fallback (OpenGraph/naslov) jer strukturirani podaci nisu potpuni.");
        }
        if ("title".equals(nameSource)) warnings.add("Naziv je uzet iz naslova stranice.");
        if (image == null) warnings.add("Slika nije pronađena.");
        if ("opengraph".equals(priceSource)) warnings.add("Cijena je parsirana iz fallback izvora.");

        String dataQuality = computeDataQuality(name, price, url, image, category, roomTags, styleTags, hasJsonLd, availabilityCertain);
        if (!"complete".equals(dataQuality)) {
            warnings.add("Kvaliteta podataka nije potpuna (" + dataQuality + ").");
        }

        String sourceReference = defaults != null && notBlank(defaults.sourceReference())
                ? defaults.sourceReference().trim()
                : "collector-" + LocalDate.now();

        CollectedProductDto collected = new CollectedProductDto(
                notBlank(externalId) ? externalId.trim() : deriveExternalId(retailer, url),
                name,
                retailer,
                category,
                price,
                url,
                blankToNull(image),
                availability,
                "Prikupljeno automatski — provjeri cijenu i dostupnost u trgovini.",
                LocalDate.now().toString(),
                roomTags,
                styleTags,
                null,
                "future-scraper",
                retailer,
                sourceReference,
                dataQuality,
                "Prikupljeno automatski — podatke prije kupnje provjeri u trgovini."
        );
        return new ParsedProduct(collected, warnings);
    }

    private String computeDataQuality(String name, BigDecimal price, String url, String image,
                                      String category, List<String> roomTags, List<String> styleTags,
                                      boolean hasJsonLd, boolean availabilityCertain) {
        if (price == null || !notBlank(name)) return "needs-review";
        boolean allFields = notBlank(image) && notBlank(category) && notBlank(url)
                && roomTags != null && !roomTags.isEmpty()
                && styleTags != null && !styleTags.isEmpty();
        if (hasJsonLd && allFields && availabilityCertain) return "complete";
        return "partial";
    }

    private List<String> nonEmpty(List<String> values) {
        if (values == null) return null;
        List<String> filtered = values.stream().filter(this::notBlank).toList();
        return filtered.isEmpty() ? null : filtered;
    }

    /** The collected product plus any warnings the parser produced while reading the page. */
    public record ParsedProduct(CollectedProductDto product, List<String> warnings) {
    }

    // --- JSON-LD ---------------------------------------------------------------

    private JsonNode findJsonLdProduct(String html) {
        Matcher matcher = LD_JSON.matcher(html);
        while (matcher.find()) {
            String json = matcher.group(1).trim();
            if (json.isEmpty()) continue;
            try {
                JsonNode product = findProduct(objectMapper.readTree(json));
                if (product != null) return product;
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // A broken JSON-LD block must not break the whole parse.
            }
        }
        return null;
    }

    private JsonNode findProduct(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findProduct(child);
                if (found != null) return found;
            }
            return null;
        }
        if (node.has("@graph")) {
            JsonNode found = findProduct(node.get("@graph"));
            if (found != null) return found;
        }
        return isProductType(node.get("@type")) ? node : null;
    }

    private boolean isProductType(JsonNode typeNode) {
        if (typeNode == null) return false;
        if (typeNode.isTextual()) return typeNode.asText().equalsIgnoreCase("Product");
        if (typeNode.isArray()) {
            for (JsonNode value : typeNode) {
                if (value.isTextual() && value.asText().equalsIgnoreCase("Product")) return true;
            }
        }
        return false;
    }

    private BigDecimal priceFromOffers(JsonNode offers) {
        if (offers == null) return null;
        if (offers.isArray()) {
            for (JsonNode offer : offers) {
                BigDecimal price = priceFromOffer(offer);
                if (price != null) return price;
            }
            return null;
        }
        return priceFromOffer(offers);
    }

    private BigDecimal priceFromOffer(JsonNode offer) {
        if (offer == null) return null;
        if (offer.hasNonNull("price")) return parsePrice(offer.get("price").asText());
        JsonNode spec = offer.get("priceSpecification");
        if (spec != null && spec.hasNonNull("price")) return parsePrice(spec.get("price").asText());
        return null;
    }

    private String availabilityFromOffers(JsonNode offers) {
        if (offers == null) return null;
        JsonNode offer = offers.isArray() && !offers.isEmpty() ? offers.get(0) : offers;
        if (offer == null) return null;
        JsonNode availability = offer.get("availability");
        return availability == null ? null : mapAvailability(availability.asText());
    }

    private String imageFrom(JsonNode image) {
        if (image == null || image.isNull()) return null;
        if (image.isTextual()) return image.asText();
        if (image.isArray() && !image.isEmpty()) return imageFrom(image.get(0));
        if (image.hasNonNull("url")) return image.get("url").asText();
        return null;
    }

    // --- meta / title ----------------------------------------------------------

    private String metaContent(String html, String key) {
        Matcher matcher = META_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            if (key.equalsIgnoreCase(tagAttr(tag, "property")) || key.equalsIgnoreCase(tagAttr(tag, "name"))) {
                String content = tagAttr(tag, "content");
                if (notBlank(content)) return cleanText(content);
            }
        }
        return null;
    }

    private String tagAttr(String tag, String attribute) {
        Matcher matcher = Pattern.compile("(?is)\\b" + attribute + "\\s*=\\s*[\"']([^\"']*)[\"']").matcher(tag);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractTitle(String html) {
        Matcher matcher = TITLE_TAG.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    // --- helpers ---------------------------------------------------------------

    private String mapAvailability(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.toLowerCase(java.util.Locale.ROOT);
        // In stock / available
        if (value.contains("instock") || value.contains("in_stock") || value.contains("in stock") || value.contains("available")) {
            return "in-stock";
        }
        // Out of stock / unavailable / discontinued
        if (value.contains("outofstock") || value.contains("out_of_stock") || value.contains("out of stock")
                || value.contains("soldout") || value.contains("discontinued") || value.contains("unavailable")) {
            return "unavailable";
        }
        // Limited availability
        if (value.contains("limited")) {
            return "limited";
        }
        // Preorder / backorder / store-only or unknown
        if (value.contains("preorder") || value.contains("backorder") || value.contains("instoreonly") || value.contains("in store only") || value.contains("unknown")) {
            return "check-store";
        }
        return null;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Remove currency symbols and whitespace, keep only digits and common separators.
        String cleaned = raw.replaceAll("[^0-9.,]", "");
        if (cleaned.isBlank()) return null;
        // Determine which character acts as the decimal separator: choose the rightmost
        // occurrence of '.' or ','. Everything before it may include thousand separators.
        int lastDot = cleaned.lastIndexOf('.');
        int lastComma = cleaned.lastIndexOf(',');
        int sepIndex = Math.max(lastDot, lastComma);
        String normalized;
        if (sepIndex >= 0) {
            // Integer part: strip all separators; Fractional part: keep digits only.
            String intPart = cleaned.substring(0, sepIndex).replaceAll("[.,]", "");
            String fracPart = cleaned.substring(sepIndex + 1).replaceAll("[^0-9]", "");
            if (fracPart.isEmpty()) {
                normalized = intPart;
            } else {
                normalized = intPart + "." + fracPart;
            }
        } else {
            // No obvious decimal separator; remove any stray separators.
            normalized = cleaned.replaceAll("[.,]", "");
        }
        try {
            if (normalized.isBlank()) return null;
            BigDecimal price = new BigDecimal(normalized);
            return price.compareTo(BigDecimal.ZERO) > 0 ? price : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String deriveExternalId(String retailer, String url) {
        String slug = "";
        if (url != null) {
            String path = url.split("\\?", 2)[0];
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (notBlank(parts[i])) {
                    slug = parts[i].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
                    if (notBlank(slug)) break;
                }
            }
        }
        if (slug.isBlank()) slug = Integer.toHexString((url == null ? "" : url).hashCode());
        return "collected-" + retailer.toLowerCase(Locale.ROOT) + "-" + slug;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (notBlank(value)) return value;
        }
        return null;
    }

    private String cleanText(String value) {
        if (value == null) return null;
        return value.replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) return value;
        }
        return null;
    }

    private String blankToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
