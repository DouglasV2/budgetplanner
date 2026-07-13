package ai.budgetspace.planner;

import java.util.List;
import java.util.Map;

/**
 * Centralised "is the catalog good enough for the planner" rules (Sprint 9.6).
 *
 * <p>For each room we list the <b>required</b> categories (a plan without them is only
 * partial) and the <b>recommended</b> ones (nice to have; if thin, the room is weak). Both
 * {@code CatalogHealthService} and {@code PlannerService} use this single source of truth, so
 * the health report and the planner's "partial plan" warning always agree.</p>
 */
public final class PlannerReadiness {

    public static final Map<String, List<String>> REQUIRED_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", List.of("sofa", "tv-unit")),
            Map.entry("bedroom", List.of("bed", "mattress")),
            Map.entry("home-office", List.of("desk", "chair")),
            Map.entry("home-gym", List.of("gym-equipment")),
            // Sprint 10.7: new rooms. Kitchen's required core is the movable kitchen cart/shelf
            // (we don't sell built-in kitchens); dedicated kitchen storage stays recommended.
            Map.entry("kitchen", List.of("kitchen-cart")),
            Map.entry("dining-room", List.of("dining-table", "dining-chair")),
            Map.entry("hallway", List.of("storage")),
            Map.entry("bathroom", List.of("storage")),
            // Sprint 10.168: a studio (garsonijera) is a combined living+sleeping room — a plan with no bed is
            // partial. Only "bed" is required (lowest false-positive risk); the rest are recommended below.
            Map.entry("studio", List.of("bed"))
    );

    public static final Map<String, List<String>> RECOMMENDED_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", List.of("table", "rug", "lighting")),
            Map.entry("bedroom", List.of("storage", "lighting", "rug", "nightstand", "wardrobe", "dresser")),
            Map.entry("home-office", List.of("lighting", "storage")),
            Map.entry("home-gym", List.of("storage", "lighting")),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", List.of("kitchen-storage", "lighting", "storage")),
            Map.entry("dining-room", List.of("lighting", "rug", "storage")),
            Map.entry("hallway", List.of("lighting", "decor", "rug")),
            Map.entry("bathroom", List.of("lighting", "decor")),
            Map.entry("studio", List.of("mattress", "sofa", "table", "storage", "lighting"))
    );

    public static final List<String> ROOMS = List.of(
            "living-room", "bedroom", "home-office", "home-gym",
            "kitchen", "dining-room", "hallway", "bathroom");

    private PlannerReadiness() {
    }

    public static List<String> requiredCategories(String room) {
        return REQUIRED_BY_ROOM.getOrDefault(room, List.of());
    }

    public static List<String> recommendedCategories(String room) {
        return RECOMMENDED_BY_ROOM.getOrDefault(room, List.of());
    }

    /** Human (Croatian) label for a category, used in the user-facing partial-plan message. */
    public static String categoryLabel(String category) {
        return switch (category == null ? "" : category) {
            case "sofa" -> "kauč";
            case "tv-unit" -> "TV komoda";
            case "table" -> "stolić";
            case "chair" -> "stolica";
            case "rug" -> "tepih";
            case "lighting" -> "rasvjeta";
            case "storage" -> "spremanje";
            case "decor" -> "dekoracije";
            case "desk" -> "radni stol";
            case "bed" -> "krevet";
            case "mattress" -> "madrac";
            case "gym-equipment" -> "oprema za vježbanje";
            case "dining-table" -> "blagovaonski stol";
            case "dining-chair" -> "blagovaonska stolica";
            case "kitchen-storage" -> "kuhinjsko spremanje";
            case "kitchen-cart" -> "kuhinjska kolica";
            case "nightstand" -> "noćni ormarić";
            case "wardrobe" -> "ormar za odjeću";
            case "dresser" -> "komoda s ladicama";
            // Sprint 10.181: bathroom fixtures — used by the degraded-capacity warning when the selected
            // market can't supply an explicitly requested fixture (e.g. NL has no toilet/bathtub).
            case "toilet" -> "WC školjka";
            case "washbasin" -> "umivaonik";
            case "bathtub" -> "kada";
            case "shower" -> "tuš";
            case "bath-shower" -> "kada ili tuš";
            case "textiles" -> "tekstil";
            default -> category;
        };
    }
}
