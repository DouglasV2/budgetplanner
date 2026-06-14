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

    public static final Map<String, List<String>> REQUIRED_BY_ROOM = Map.of(
            "living-room", List.of("sofa", "tv-unit"),
            "bedroom", List.of("bed", "mattress"),
            "home-office", List.of("desk", "chair"),
            "home-gym", List.of("gym-equipment")
    );

    public static final Map<String, List<String>> RECOMMENDED_BY_ROOM = Map.of(
            "living-room", List.of("table", "rug", "lighting"),
            "bedroom", List.of("storage", "lighting", "rug"),
            "home-office", List.of("lighting", "storage"),
            "home-gym", List.of("storage", "lighting")
    );

    public static final List<String> ROOMS = List.of("living-room", "bedroom", "home-office", "home-gym");

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
            default -> category;
        };
    }
}
