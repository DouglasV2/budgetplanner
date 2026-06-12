package ai.budgetspace.product;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProductTaxonomy {
    public static final Set<String> SUPPORTED_RETAILERS = Set.of(
            "IKEA",
            "JYSK",
            "Pevex",
            "Emmezeta",
            "Decathlon",
            "Lesnina"
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
            "gym-equipment"
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
            "home-gym"
    );

    private static final Map<String, String> CATEGORY_ALIASES = categoryAliases();
    private static final Map<String, String> STYLE_ALIASES = styleAliases();
    private static final Map<String, String> ROOM_ALIASES = roomAliases();

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

    public static boolean canEnterPlanner(Product product) {
        if (product == null) return false;
        if (!product.isInStock()) return false;
        String availability = normalizeAvailability(product.getAvailabilityStatus());
        if ("unavailable".equals(availability)) return false;
        return product.getRoomTags() != null && !product.getRoomTags().isBlank();
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
        return Map.copyOf(aliases);
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
