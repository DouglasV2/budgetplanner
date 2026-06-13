package ai.budgetspace.collector;

import ai.budgetspace.dto.CollectedProductDto;
import ai.budgetspace.dto.CollectorDefaultsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public CollectedProductDto parse(String html, String url, String retailer, CollectorDefaultsDto defaults) {
        String pageHtml = html == null ? "" : html;
        String name = null;
        BigDecimal price = null;
        String image = null;
        String availability = null;
        String externalId = null;
        String method = "title";

        JsonNode product = findJsonLdProduct(pageHtml);
        if (product != null) {
            method = "json-ld";
            name = blankToNull(text(product, "name"));
            externalId = firstText(product, "sku", "productID", "mpn", "gtin13", "gtin");
            image = imageFrom(product.get("image"));
            price = priceFromOffers(product.get("offers"));
            availability = availabilityFromOffers(product.get("offers"));
        }

        if (name == null) {
            String ogTitle = metaContent(pageHtml, "og:title");
            if (notBlank(ogTitle)) {
                name = ogTitle;
                if (method.equals("title")) method = "opengraph";
            }
        }
        if (image == null) image = metaContent(pageHtml, "og:image");
        if (price == null) {
            BigDecimal ogPrice = parsePrice(firstNonBlank(
                    metaContent(pageHtml, "product:price:amount"),
                    metaContent(pageHtml, "og:price:amount")));
            if (ogPrice != null) {
                price = ogPrice;
                if (method.equals("title")) method = "opengraph";
            }
        }
        if (availability == null) {
            availability = mapAvailability(firstNonBlank(
                    metaContent(pageHtml, "product:availability"),
                    metaContent(pageHtml, "og:availability")));
        }
        if (name == null) name = blankToNull(cleanText(extractTitle(pageHtml)));

        if ("IKEA".equalsIgnoreCase(retailer)) {
            name = ikeaProductParser.refineName(name);
        }

        String category = defaults == null ? null : blankToNull(defaults.category());
        List<String> roomTags = defaults == null ? null : defaults.roomTags();
        List<String> styleTags = defaults == null ? null : defaults.styleTags();
        String sourceReference = defaults != null && notBlank(defaults.sourceReference())
                ? defaults.sourceReference().trim()
                : "collector-" + LocalDate.now();
        String dataQuality = method.equals("json-ld") ? "partial" : "needs-review";

        return new CollectedProductDto(
                notBlank(externalId) ? externalId.trim() : deriveExternalId(retailer, url),
                name,
                retailer,
                category,
                price,
                url,
                blankToNull(image),
                availability == null ? "check-store" : availability,
                "Prikupljeno automatski — provjeri cijenu i dostupnost u trgovini.",
                LocalDate.now().toString(),
                roomTags,
                styleTags,
                null,
                "future-scraper",
                retailer,
                sourceReference,
                dataQuality,
                "Prikupljeno automatski (" + method + ") — podatke prije kupnje provjeri u trgovini."
        );
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
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.contains("instock") || value.contains("in_stock") || value.contains("in stock")) return "in-stock";
        if (value.contains("outofstock") || value.contains("out_of_stock") || value.contains("soldout") || value.contains("discontinued")) return "unavailable";
        if (value.contains("limited")) return "limited";
        if (value.contains("preorder") || value.contains("backorder") || value.contains("instoreonly")) return "check-store";
        return null;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = PRICE_NUMBER.matcher(raw.trim());
        if (!matcher.find()) return null;
        try {
            BigDecimal price = new BigDecimal(matcher.group(1).replace(',', '.'));
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
