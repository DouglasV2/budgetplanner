package ai.budgetspace.planner;

import ai.budgetspace.dto.*;
import ai.budgetspace.feed.EbayBrowseFeed;
import ai.budgetspace.product.CatalogSourcePolicy;
import ai.budgetspace.product.MarketplaceListingFilter;
import ai.budgetspace.product.Markets;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private static final List<String> RETAILERS = List.of("IKEA", "JYSK", "Pevex", "VVS Eksperten", "Victorian Plumbing", "Emmezeta", "Decathlon", "Lesnina",
            // Sprint 10.16: additional retailers that have verified products (HR/SI/DE).
            "Harvey Norman", "Namjestaj.hr", "Otto", "Segmüller", "Poco",
            // Sprint 10.36: France — Camif (verified products).
            "Camif",
            // Sprint 10.43: Spain — Kenay Home + Banak Importa (verified products).
            "Kenay Home", "Banak Importa",
            // Sprint 10.44: Netherlands — Leen Bakker + Kwantum (verified products).
            "Leen Bakker", "Kwantum",
            // Sprint 10.45: depth — Moviflor (PT) + Nábytok (SK) (verified products).
            "Moviflor", "Nábytok",
            // Sprint 10.48: retail re-sweep — more verified retailers across HR/SI/IT/AT/FI/FR/PT/ES/NL/SK.
            "Svijetnamještaja", "Svetpohištva", "Conforama", "Interio", "Masku", "Lovely Meubles", "JOM",
            "Sítio do Móvel", "Miroytengo", "Merkamueble", "Muebles BOOM", "Pronto Wonen", "Drevona", "ASKO Nábytok");

    private static final Map<String, List<String>> CATEGORY_FLOW_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", List.of("sofa", "tv-unit", "table", "rug", "lighting", "storage", "textiles", "decor")),
            Map.entry("home-office", List.of("desk", "chair", "lighting", "storage", "decor", "rug")),
            Map.entry("bedroom", List.of("bed", "mattress", "nightstand", "wardrobe", "dresser", "storage", "lighting", "rug", "textiles", "decor")),
            Map.entry("home-gym", List.of("gym-equipment", "storage", "lighting", "decor", "rug")),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", List.of("kitchen-cart", "kitchen-storage", "lighting", "storage", "decor")),
            Map.entry("dining-room", List.of("dining-table", "dining-chair", "lighting", "rug", "storage", "decor")),
            Map.entry("hallway", List.of("storage", "lighting", "rug", "decor")),
            // Sprint 10.169: bathroom fixtures first (toilet + washbasin + a bath/shower are what a bathroom is
            // built around; Pevex HR), then the IKEA/JYSK cabinet/mirror/lighting/textiles/decor around them.
            // Sprint 10.177: textiles (bath mat / shower curtain) added — now sourced for every market, they belong
            // in a real bathroom plan (a comfort item, after storage + lighting).
            Map.entry("bathroom", List.of("toilet", "washbasin", "bath-shower", "storage", "lighting", "textiles", "decor")),
            // Sprint 10.121: studio/one-room flat = living + bedroom combined (you sleep AND live here), so the
            // flow carries the essentials of both, sleeping pieces first. Products come from the living-room AND
            // bedroom catalog pools (see ROOM_CATALOG_TAGS / matchesRoom).
            Map.entry("studio", List.of("bed", "mattress", "sofa", "dining-table", "wardrobe", "table", "storage", "lighting", "tv-unit", "rug", "nightstand", "textiles")),
            // Sprint 10.179: utility rooms — furnished from the shared storage/lighting pool (see ROOM_CATALOG_TAGS).
            // We have no garage/pantry/laundry-specific stock, so these reuse existing shelving/lighting/desk/textiles
            // (and, for laundry, real IKEA laundry baskets). Honest partial coverage; ROOM_COVERAGE_NOTE flags the gaps.
            Map.entry("garage", List.of("storage", "desk", "lighting", "decor")),
            Map.entry("pantry", List.of("storage", "kitchen-storage", "lighting", "decor")),
            Map.entry("laundry", List.of("storage", "lighting", "textiles", "decor")),
            Map.entry("attic", List.of("storage", "lighting", "decor")),
            Map.entry("basement", List.of("storage", "lighting", "decor"))
    );

    // Najvažnije (buy-first) po prostoru.
    private static final Map<String, Set<String>> CORE_CATEGORIES_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", Set.of("sofa", "tv-unit")),
            Map.entry("home-office", Set.of("desk", "chair")),
            Map.entry("bedroom", Set.of("bed", "mattress")),
            Map.entry("home-gym", Set.of("gym-equipment")),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", Set.of("kitchen-cart")),
            Map.entry("dining-room", Set.of("dining-table", "dining-chair")),
            Map.entry("hallway", Set.of("storage")),
            Map.entry("bathroom", Set.of("toilet", "washbasin", "bath-shower")),
            Map.entry("studio", Set.of("bed", "mattress", "sofa")),
            // Sprint 10.179: utility rooms — shelving is the essential.
            Map.entry("garage", Set.of("storage")),
            Map.entry("pantry", Set.of("storage")),
            Map.entry("laundry", Set.of("storage")),
            Map.entry("attic", Set.of("storage")),
            Map.entry("basement", Set.of("storage"))
    );

    // Za ugodniji prostor (add-comfort) po prostoru. Sve ostalo u flowu je "može kasnije".
    private static final Map<String, Set<String>> COMFORT_CATEGORIES_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", Set.of("table", "rug", "lighting", "storage", "textiles")),
            Map.entry("home-office", Set.of("lighting", "storage")),
            Map.entry("bedroom", Set.of("nightstand", "wardrobe", "dresser", "storage", "lighting", "rug", "textiles")),
            Map.entry("home-gym", Set.of("storage", "lighting")),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", Set.of("kitchen-storage", "lighting", "storage")),
            Map.entry("dining-room", Set.of("lighting", "rug", "storage")),
            Map.entry("hallway", Set.of("lighting", "rug")),
            // Sprint 10.178: decor promoted from "later" (complete-only) to comfort, so bathroom mirrors and
            // accessories (category `decor`) surface at the default comfort level — a real bathroom shows a mirror.
            Map.entry("bathroom", Set.of("storage", "lighting", "textiles", "decor")),
            Map.entry("studio", Set.of("dining-table", "wardrobe", "table", "storage", "lighting", "rug", "textiles")),
            // Sprint 10.179: utility rooms.
            Map.entry("garage", Set.of("desk", "lighting")),
            Map.entry("pantry", Set.of("kitchen-storage", "lighting")),
            Map.entry("laundry", Set.of("lighting", "textiles")),
            Map.entry("attic", Set.of("lighting")),
            Map.entry("basement", Set.of("lighting"))
    );

    // Sprint 10.109 (Move-In): relative "how much furnishing this room typically needs" weights — they only set
    // PROPORTIONS for splitting a whole-apartment budget's leftover (after each room's core floor is reserved).
    private static final Map<String, Double> MOVE_IN_WEIGHTS = Map.ofEntries(
            Map.entry("living-room", 1.4), Map.entry("bedroom", 1.2), Map.entry("kitchen", 1.0),
            Map.entry("dining-room", 1.0), Map.entry("home-office", 0.9), Map.entry("home-gym", 0.9),
            Map.entry("hallway", 0.5), Map.entry("bathroom", 0.5),
            // Sprint 10.179: utility rooms (small share of a whole-apartment budget if selected).
            Map.entry("garage", 0.5), Map.entry("pantry", 0.4), Map.entry("laundry", 0.4),
            Map.entry("attic", 0.4), Map.entry("basement", 0.4)
    );

    private static final Map<String, String> ROOM_LABELS = Map.ofEntries(
            Map.entry("living-room", "dnevni boravak"),
            Map.entry("home-office", "radni kutak"),
            Map.entry("bedroom", "spavaća soba"),
            Map.entry("home-gym", "kućna teretana"),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", "kuhinja"),
            Map.entry("dining-room", "blagovaonica"),
            Map.entry("hallway", "hodnik"),
            Map.entry("bathroom", "kupaonica"),
            Map.entry("studio", "garsonijera"),
            // Sprint 10.179: utility rooms.
            Map.entry("garage", "garaža"),
            Map.entry("pantry", "ostava"),
            Map.entry("laundry", "praonica"),
            Map.entry("attic", "tavan"),
            Map.entry("basement", "podrum")
    );

    // Sprint 10.121: which catalog room-tags a roomType draws products from. Studio is a combined living+bedroom,
    // so it pulls from both pools; every other room maps to itself.
    private static final Map<String, List<String>> ROOM_CATALOG_TAGS = Map.ofEntries(
            Map.entry("studio", List.of("living-room", "bedroom", "dining-room")),
            // Sprint 10.179: utility rooms have no dedicated catalog tag — they draw from the pools where the shared
            // storage / shelving / lighting / desk / textiles actually live (like studio). `laundry` also lists the
            // derived `laundry` tag (real baskets) + bathroom (where they were harvested in 10.177).
            Map.entry("garage", List.of("hallway", "home-office", "living-room", "bedroom", "kitchen")),
            Map.entry("pantry", List.of("kitchen", "hallway", "living-room", "bedroom")),
            Map.entry("laundry", List.of("laundry", "bathroom", "hallway", "kitchen", "bedroom")),
            Map.entry("attic", List.of("hallway", "living-room", "bedroom", "home-office")),
            Map.entry("basement", List.of("hallway", "living-room", "bedroom", "home-office"))
    );

    // Sprint 10.179: utility rooms are FUNCTIONAL, not splurge rooms — they skip the value/stretch spend-up target
    // (which would drop a designer lamp into a garage or a pricey mirror cabinet into a laundry), so they floor to
    // appropriate, cheaper pieces and a real laundry basket wins its storage slot in every tier.
    private static final Set<String> UTILITY_ROOMS = Set.of("garage", "pantry", "laundry", "attic", "basement");

    private final ProductRepository productRepository;
    // Sprint 10.64: the live, request-time eBay source for "Rabljeno" (used items are never persisted). Nullable
    // — tests that don't exercise second-hand build the planner without it, and then no used items are surfaced.
    private final EbayBrowseFeed ebayMarketplace;
    private final PlannerIntentExtractor intentExtractor = new PlannerIntentExtractor();
    // Sprint 10.175 (kitchen Increment 1): deterministic complete/component/kitchenware intent routing.
    private final KitchenIntentClassifier kitchenClassifier = new KitchenIntentClassifier();
    private static final int MAX_KITCHEN_SETS = 6;

    public PlannerService(ProductRepository productRepository) {
        this(productRepository, null);
    }

    @Autowired
    public PlannerService(ProductRepository productRepository, EbayBrowseFeed ebayMarketplace) {
        this.productRepository = productRepository;
        this.ebayMarketplace = ebayMarketplace;
    }

    public PlanGenerationResponse generate(PlannerInputDto rawInput) {
        // Rule-based path: parse the prompt with the deterministic extractor, then plan. Never focused —
        // the deterministic extractor doesn't classify item-vs-room intent (the AI path does).
        return buildResponse(intentExtractor.enrich(rawInput == null ? null : rawInput.normalized()), false);
    }

    /**
     * Sprint 10.10: the input has already been resolved by the AI prompt-intelligence layer, so the
     * rule-based extractor is NOT re-run here — the resolved fields are authoritative. The planner
     * still picks only real products from the catalog.
     */
    public PlanGenerationResponse generateResolved(PlannerInputDto resolvedInput) {
        return generateResolved(resolvedInput, false);
    }

    // Sprint 10.114: the AI-resolved path can flag a specific-item request (specificItemsOnly) → a focused plan
    // (only the named pieces, in the room they belong to) instead of the room's full core kit.
    public PlanGenerationResponse generateResolved(PlannerInputDto resolvedInput, boolean focused) {
        return buildResponse(resolvedInput == null ? intentExtractor.enrich(null) : resolvedInput.normalized(), focused);
    }

    // Sprint 10.109 (Move-In / "Cijeli stan"): split ONE total budget across several rooms, then build a normal
    // plan per room. The split is catalog-floor-aware — each room first reserves the cheapest available core
    // pieces (so no room is starved below its essentials), then the remainder is shared by room weight. If the
    // total can't even cover every room's core, we say so honestly (apartmentPartial + shortfall) instead of
    // silently handing back starved rooms. Reuses the EXISTING single-room engine (buildResponse) per room.
    public MoveInResponse generateMoveIn(MoveInRequestDto request) {
        int total = request == null ? 0 : Math.max(0, request.totalBudget());
        if (request == null || request.rooms() == null || request.rooms().isEmpty()) {
            return new MoveInResponse(List.of(), BigDecimal.ZERO, total, false, BigDecimal.ZERO);
        }
        PlannerInputDto base = (request.base() == null
                ? new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi", null, "best-value",
                        "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0)
                : request.base()).normalized();

        // Known rooms only, de-duplicated, original order preserved.
        List<String> rooms = new ArrayList<>(new LinkedHashSet<>(request.rooms().stream()
                .map(room -> room == null ? "" : room.trim().toLowerCase(Locale.ROOT))
                .filter(CATEGORY_FLOW_BY_ROOM::containsKey)
                .toList()));
        if (rooms.isEmpty()) {
            return new MoveInResponse(List.of(), BigDecimal.ZERO, total, false, BigDecimal.ZERO);
        }

        double[] floors = new double[rooms.size()];
        double sumFloors = 0;
        for (int i = 0; i < rooms.size(); i++) {
            floors[i] = coreFloor(base.withRoomType(rooms.get(i)));
            sumFloors += floors[i];
        }
        boolean apartmentPartial = total > 0 && sumFloors > total;
        double shortfall = apartmentPartial ? sumFloors - total : 0;

        int[] alloc = allocateMoveIn(total, rooms, floors, sumFloors, apartmentPartial);

        // Sprint 10.158 (Move-In fill): the weight-only split parked money in rooms whose catalog can't absorb
        // it (a DE kitchen was allocated 1658 and could spend ~110, stranding the difference), so cap every
        // room at its catalog capacity and re-share the excess up front. Skipped in the infeasible case —
        // there is no excess to move when even the core floors don't fit.
        double[] capacities = new double[rooms.size()];
        if (!apartmentPartial) {
            for (int i = 0; i < rooms.size(); i++) {
                capacities[i] = roomCapacity(moveInRoomInput(base, rooms.get(i), 1));
            }
            alloc = capAllocationsToCapacity(alloc, rooms, floors, capacities);
        }

        // Use the SAME path as /generate-fast (the rule-based extractor) — its enrichment is what fills the
        // full category set, so a room gets a COMPLETE plan (buildResponse alone returns a sparse one). The
        // roomType is explicit and the prompt is just the room label, so the room is never mis-parsed.
        PlanGenerationResponse[] responses = new PlanGenerationResponse[rooms.size()];
        double[] spent = new double[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            responses[i] = generate(moveInRoomInput(base, rooms.get(i), alloc[i]));
            spent[i] = valueTierTotal(responses[i]);
        }

        // Sprint 10.158, second pass: the value tier deliberately spends below its budget, so after the first
        // pass a slice of the total is still unspent. Re-share the REALIZED leftover among the rooms that
        // proved they can absorb it (used most of their allocation and still have catalog headroom) and
        // regenerate just those. Each boosted budget is the room's realized spend + its leftover share (the
        // boost never spends the same euro twice), and a boosted plan is kept only if it spends more AND stays
        // within its boosted budget — repairBudget's value cap has an honest-over escape hatch when even the
        // cheapest essentials overrun, and accepting such a plan here could push the grand total past the
        // user's total.
        if (!apartmentPartial && total > 0) {
            double leftover = total - Arrays.stream(spent).sum();
            if (leftover >= Math.max(100, total * 0.05)) {
                boolean[] absorber = new boolean[rooms.size()];
                double sumWeights = 0;
                for (int i = 0; i < rooms.size(); i++) {
                    absorber[i] = spent[i] >= 0.6 * alloc[i] && capacities[i] > alloc[i] + 1;
                    if (absorber[i]) sumWeights += MOVE_IN_WEIGHTS.getOrDefault(rooms.get(i), 1.0);
                }
                for (int i = 0; i < rooms.size() && sumWeights > 0; i++) {
                    if (!absorber[i]) continue;
                    double share = leftover * MOVE_IN_WEIGHTS.getOrDefault(rooms.get(i), 1.0) / sumWeights;
                    int boostedBudget = (int) Math.min(Math.floor(spent[i] + share), moveInCap(capacities[i], floors[i]));
                    if (boostedBudget <= alloc[i]) continue;
                    PlanGenerationResponse boosted = generate(moveInRoomInput(base, rooms.get(i), boostedBudget));
                    double boostedTotal = valueTierTotal(boosted);
                    if (boostedTotal > spent[i] && boostedTotal <= boostedBudget) {
                        responses[i] = boosted;
                        alloc[i] = boostedBudget;
                        spent[i] = boostedTotal;
                    }
                }
            }
        }

        List<MoveInRoomDto> roomDtos = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (int i = 0; i < rooms.size(); i++) {
            PlanGenerationResponse resp = responses[i];
            FurnishingPlanDto best = resp.plans().isEmpty() ? null : resp.plans().get(0);
            grandTotal = grandTotal.add(best == null ? BigDecimal.ZERO : best.total());
            roomDtos.add(new MoveInRoomDto(rooms.get(i), alloc[i], resp.plans(), resp.partialPlan()));
        }
        return new MoveInResponse(roomDtos, grandTotal, total, apartmentPartial, BigDecimal.valueOf(Math.round(shortfall)));
    }

    // One room's planner input inside a Move-In request — the room label as the prompt, the room's slice of the
    // budget, everything else inherited from the base request. Sprint 10.161: propagate alreadyHaveCategories to
    // every room (was hardcoded empty), so a whole-apartment shopper who already owns e.g. a bed/sofa is not
    // told to re-buy it — the biggest trust-killer the scenario sweep found. mustHave/locked stay per-request
    // empty: a single global must-have would wrongly force that piece into EVERY room (a sofa in the bedroom).
    private PlannerInputDto moveInRoomInput(PlannerInputDto base, String room, int budget) {
        return new PlannerInputDto(
                ROOM_LABELS.getOrDefault(room, room), Math.max(1, budget), room,
                base.style(), base.location(), base.size(), base.retailerMode(), base.selectedRetailers(),
                base.optimizationGoal(), base.furnishingLevel(), List.of(), base.alreadyHaveCategories(), List.of(),
                base.preferredRetailers(), base.excludedRetailers(), base.maxStores(),
                base.colorPreferences(), base.materialPreferences(), base.market()
        ).normalized();
    }

    private double valueTierTotal(PlanGenerationResponse resp) {
        FurnishingPlanDto best = resp.plans().isEmpty() ? null : resp.plans().get(0);
        return best == null ? 0 : best.total().doubleValue();
    }

    // Cheapest available core pieces for a room+market — the minimum to give the room a complete core. A core
    // category with no product contributes 0 (the room simply comes back partial from the planner).
    private double coreFloor(PlannerInputDto roomInput) {
        Set<String> core = CORE_CATEGORIES_BY_ROOM.getOrDefault(roomInput.roomType(), Set.of());
        if (core.isEmpty()) return 0;
        List<Product> catalog = marketCatalog(roomInput).stream()
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> hasTag(product.getRoomTags(), roomInput.roomType()))
                .toList();
        double floor = 0;
        for (String category : core) {
            // Sprint 10.158: × default quantity, so a dining room's floor counts the 4 chairs the plan will
            // actually buy — otherwise apartmentPartial/shortfall are optimistic and the room can be allocated
            // below its real minimum.
            floor += catalog.stream()
                    .filter(product -> category.equalsIgnoreCase(product.getCategory()))
                    .mapToDouble(product -> product.getPrice().doubleValue())
                    .min().orElse(0) * quantityFor(roomInput, category);
        }
        return floor;
    }

    // Sprint 10.158 (Move-In fill): the most a room's plan could PLAUSIBLY spend in this market — the priciest
    // planner-eligible product per desired category (× default quantity). Mirrors desiredCategories/matchesRoom,
    // i.e. exactly the set a room plan shops from, so it is a true upper bound on the room's value-tier spend.
    // Used to stop the apartment allocator from parking budget in a room whose catalog can't absorb it.
    private double roomCapacity(PlannerInputDto roomInput) {
        // Same retailer allow-list pickBest enforces — otherwise a retailer-constrained request would get a
        // capacity (and thus a cap) measured over stores its plan is not allowed to shop from.
        List<String> allowedRetailers = selectedRetailers(roomInput);
        List<Product> catalog = marketCatalog(roomInput).stream()
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> matchesRoom(product, roomInput.roomType()))
                .filter(product -> allowedRetailers.contains(product.getRetailer()))
                .toList();
        double capacity = 0;
        for (String category : desiredCategories(roomInput)) {
            capacity += catalog.stream()
                    .filter(product -> category.equalsIgnoreCase(product.getCategory()))
                    .mapToDouble(product -> product.getPrice().doubleValue())
                    .max().orElse(0) * quantityFor(roomInput, category);
        }
        return capacity;
    }

    // The tiers plan with a slice of the allocation (value 0.98, budget 0.82 of it), so a room capped EXACTLY
    // at its capacity could no longer afford the very pieces the capacity was measured from (value tier of a
    // 90€-capacity room gets 88€ → empty plan). 1.25 covers the deepest slice (1/0.82 ≈ 1.22) + repair slack.
    private static final double MOVE_IN_CAPACITY_HEADROOM = 1.25;

    // A room's allocation ceiling: its catalog capacity (never below the core floor) + tier headroom.
    private static int moveInCap(double capacity, double floor) {
        return (int) Math.ceil(Math.max(capacity, floor) * MOVE_IN_CAPACITY_HEADROOM);
    }

    // Sprint 10.158: cap each room's allocation at what its catalog can absorb; the excess is re-shared (by
    // room weight) among the rooms that still have headroom, so a thin room (kitchen in a sparse market) no
    // longer strands a big slice of the apartment budget it can never spend. A capped room never drops below
    // its core floor, and if EVERY room is capped the leftover simply stays unallocated — the market genuinely
    // can't absorb the budget, and honesty beats inflating picks. Pure math, package-private for
    // MoveInAllocationTest.
    static int[] capAllocationsToCapacity(int[] alloc, List<String> rooms, double[] floors, double[] capacities) {
        int n = alloc.length;
        int[] out = alloc.clone();
        boolean[] capped = new boolean[n];
        for (int round = 0; round < n; round++) {
            long excess = 0;
            for (int i = 0; i < n; i++) {
                if (capped[i]) continue;
                int cap = moveInCap(capacities[i], floors[i]);
                if (out[i] > cap) {
                    excess += out[i] - cap;
                    out[i] = cap;
                    capped[i] = true;
                }
            }
            if (excess == 0) break;
            double sumWeights = 0;
            for (int i = 0; i < n; i++) {
                if (!capped[i]) sumWeights += MOVE_IN_WEIGHTS.getOrDefault(rooms.get(i), 1.0);
            }
            if (sumWeights <= 0) break;
            long distributed = 0;
            int last = -1;
            for (int i = 0; i < n; i++) {
                if (capped[i]) continue;
                int add = (int) Math.floor(excess * MOVE_IN_WEIGHTS.getOrDefault(rooms.get(i), 1.0) / sumWeights);
                out[i] += add;
                distributed += add;
                last = i;
            }
            if (last >= 0) out[last] += (int) (excess - distributed);
        }
        return out;
    }

    // Integer per-room budgets that sum EXACTLY to total. Feasible: floor + a weighted share of the remainder.
    // Infeasible (floors can't all be met): split proportional to floors so every room still moves toward core.
    // Package-private + static so it can be unit-tested without a catalog (MoveInAllocationTest).
    static int[] allocateMoveIn(int total, List<String> rooms, double[] floors, double sumFloors, boolean infeasible) {
        int n = rooms.size();
        double[] ideal = new double[n];
        double[] weights = new double[n];
        double sumWeights = 0;
        for (int i = 0; i < n; i++) {
            weights[i] = MOVE_IN_WEIGHTS.getOrDefault(rooms.get(i), 1.0);
            sumWeights += weights[i];
        }
        if (sumWeights <= 0) sumWeights = n;

        if (infeasible && sumFloors > 0) {
            for (int i = 0; i < n; i++) ideal[i] = total * floors[i] / sumFloors;
        } else if (sumFloors > 0 && sumFloors <= total) {
            double leftover = total - sumFloors;
            for (int i = 0; i < n; i++) ideal[i] = floors[i] + leftover * weights[i] / sumWeights;
        } else {
            for (int i = 0; i < n; i++) ideal[i] = total * weights[i] / sumWeights;
        }

        int[] out = new int[n];
        int used = 0;
        double[] frac = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = (int) Math.floor(ideal[i]);
            used += out[i];
            frac[i] = ideal[i] - Math.floor(ideal[i]);
        }
        int remainder = total - used;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(frac[b], frac[a]));
        for (int k = 0; k < remainder && n > 0; k++) out[order[k % n]]++;
        return out;
    }

    private PlanGenerationResponse buildResponse(PlannerInputDto rawInput, boolean focused) {
        // Sprint 10.114: focused mode. When the AI flags a specific-item request (not a whole-room description),
        // plan AROUND those pieces — switch to the room they belong to and include only them — instead of
        // forcing the room's full core kit and burying the requested item as a cheap afterthought.
        PlannerInputDto input = focused ? withInferredRoom(rawInput) : rawInput;
        List<FurnishingPlanDto> plans = List.of(
                buildPlan(input, "value", focused),
                buildPlan(input, "budget", focused),
                buildPlan(input, "stretch", focused)
        );
        List<String> missingImportant = missingImportantCategories(input);
        boolean partial = !missingImportant.isEmpty();
        String catalogWarning = partial ? buildCatalogWarning(missingImportant) : null;
        List<ProductDto> secondHand = secondHandSuggestions(input);
        logPlanSummary(input, plans, missingImportant);
        PlanGenerationResponse response = new PlanGenerationResponse(input, plans, partial, missingImportant, catalogWarning, secondHand);
        // Sprint 10.175: a complete-kitchen prompt also gets a "Kompletna kuhinja" section of real modular sets
        // (browse-only; it never touches the freestanding plan above). Only COMPLETE intent attaches it.
        KitchenIntentClassifier.KitchenBrief kitchenBrief = kitchenClassifier.classify(input.prompt());
        if (kitchenBrief.intent() == KitchenIntentClassifier.KitchenIntent.COMPLETE) {
            response = response.withCompleteKitchen(buildCompleteKitchen(input, kitchenBrief));
        }
        return response;
    }

    // Sprint 10.175 (kitchen Increment 1): select real modular kitchen SETS (category kitchen-set) for the
    // market within budget, ranked by the same "value" score the planner trusts. Browse-only, no plan mutation.
    // An empty list is an honest "no set fits" state (the UI still shows the modular note). Catalog only —
    // marketCatalog already excludes second-hand/marketplace listings, so used items never leak in.
    public CompleteKitchenDto buildCompleteKitchen(PlannerInputDto rawInput, KitchenIntentClassifier.KitchenBrief brief) {
        PlannerInputDto input = rawInput.normalized();
        List<String> allowed = selectedRetailers(input);
        List<ProductDto> sets = marketCatalog(input).stream()
                .filter(product -> "kitchen-set".equalsIgnoreCase(product.getCategory()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> allowed.contains(product.getRetailer()))
                .filter(product -> product.getPrice().doubleValue() <= input.budget())
                .sorted(Comparator.comparingDouble((Product product) -> scoreProduct(product, input, "value", Set.of(), 0)).reversed())
                .limit(MAX_KITCHEN_SETS)
                .map(ProductDto::from)
                .toList();
        return new CompleteKitchenDto(sets, shapeKey(brief.shape()), brief.includeAppliances(), true);
    }

    // Sprint 10.175: attach the complete-kitchen section from an EXPLICIT prompt. The AI path clears the resolved
    // input's prompt (analysis is authoritative), so buildResponse's own classification sees nothing there — the
    // controller calls this with the ORIGINAL prompt. No-op if a section is already present (the rule-based path
    // attached it) or the original prompt isn't a complete-kitchen ask.
    public PlanGenerationResponse maybeAttachCompleteKitchen(PlanGenerationResponse response, String originalPrompt, PlannerInputDto input) {
        if (response == null || response.completeKitchen() != null) return response;
        KitchenIntentClassifier.KitchenBrief brief = kitchenClassifier.classify(originalPrompt);
        if (brief.intent() != KitchenIntentClassifier.KitchenIntent.COMPLETE) return response;
        return response.withCompleteKitchen(buildCompleteKitchen(input, brief));
    }

    private String shapeKey(KitchenIntentClassifier.KitchenShape shape) {
        return switch (shape) {
            case SINGLE_WALL -> "single-wall";
            case L_SHAPED -> "l-shaped";
            case U_SHAPED -> "u-shaped";
            case GALLEY -> "galley";
            case ISLAND -> "island";
            default -> "unknown";
        };
    }

    // Sprint 10.51: the separate "Rabljeno" block. Used marketplace listings matched to the request's room +
    // market, kept entirely out of every plan (CatalogSourcePolicy.isPlannerEligible already excludes
    // second-hand) and therefore out of every total. Freshness uses the SHORT marketplace window
    // (MarketplaceListingFilter, 24h) — a used listing goes stale fast — not the 14-day retail window. We
    // require a real listing URL + a stated condition (never guessed) and a sourceReference (so no sample
    // rows). Empty until a marketplace feed (e.g. eBay Browse) is configured. See docs/marketplace-sourcing.md §5.
    private static final int MAX_SECOND_HAND_SUGGESTIONS = 6;

    private List<ProductDto> secondHandSuggestions(PlannerInputDto input) {
        if (input == null) return List.of();
        String market = Markets.normalize(input.market());
        String room = input.roomType();
        Instant now = Instant.now();
        // Sprint 10.64: used items come LIVE from eBay (transient, never persisted) — not from the catalog DB.
        // The same downstream filters still apply (room match, freshness, condition, link), defensively.
        List<Product> usedListings = ebayMarketplace == null ? List.of() : ebayMarketplace.findUsedFurniture(market);
        return usedListings.stream()
                .filter(Product::isSecondHand)
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> product.getMarket() == null || product.getMarket().isBlank()
                        || product.getMarket().equalsIgnoreCase(market))
                .filter(product -> hasTag(product.getRoomTags(), room))
                .filter(product -> hasContent(product.getSourceReference()))
                .filter(product -> hasContent(product.getProductUrl()))
                .filter(product -> hasContent(product.getConditionLabel()))
                .filter(product -> !MarketplaceListingFilter.isStale(product.getLastCheckedAt(), now))
                .sorted(Comparator
                        .comparingInt((Product product) -> styleMatches(product, input.style()) ? 1 : 0).reversed()
                        .thenComparing(Product::getPrice))
                .limit(MAX_SECOND_HAND_SUGGESTIONS)
                .map(ProductDto::from)
                .toList();
    }

    // Observability: one line per generated plan so an operator can see the planner is producing
    // real, priced items with a sane retailer mix. No user PII is logged (only the room and budget).
    private void logPlanSummary(PlannerInputDto input, List<FurnishingPlanDto> plans, List<String> missingImportant) {
        FurnishingPlanDto primary = plans.isEmpty() ? null : plans.get(0);
        int itemCount = primary == null ? 0 : primary.items().size();
        String total = primary == null ? "0" : String.valueOf(primary.total());
        String retailerMix = primary == null || primary.retailersUsed().isEmpty()
                ? "none" : String.join("+", primary.retailersUsed());
        log.info("Plan generated: room={}, budget={} EUR, primaryItems={}, primaryTotal={} EUR, retailers=[{}], missingImportant={}.",
                input.roomType(), input.budget(), itemCount, total, retailerMix, missingImportant);
        if (itemCount == 0) {
            log.warn("Plan generated with 0 items for room={}, budget={} EUR — catalog likely has no usable products for this request.",
                    input.roomType(), input.budget());
        }
    }

    // Required categories for the room that the catalog cannot supply (and the user did not
    // already have). The planner never invents products to fill the gap — it surfaces it.
    private List<String> missingImportantCategories(PlannerInputDto input) {
        Set<String> usableCategories = marketCatalog(input).stream()
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> matchesRoom(product, input.roomType()))
                .map(product -> product.getCategory() == null ? "" : product.getCategory().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> alreadyHave = new LinkedHashSet<>(input.alreadyHaveCategories());
        return PlannerReadiness.requiredCategories(input.roomType()).stream()
                .filter(category -> !alreadyHave.contains(category))
                .filter(category -> !usableCategories.contains(category))
                .toList();
    }

    private String buildCatalogWarning(List<String> missingImportant) {
        String base = "Nemamo još dovoljno proizvoda za kompletan plan, ali ovo je najbolja dostupna kombinacija.";
        if (missingImportant.isEmpty()) return base;
        String labels = missingImportant.stream()
                .map(PlannerReadiness::categoryLabel)
                .collect(Collectors.joining(", "));
        return base + " Još nedostaje dobar izbor za: " + labels + ".";
    }

    public FurnishingPlanDto replaceProduct(ReplaceProductRequest request) {
        if (request == null || request.plan() == null || request.input() == null || request.productId() == null) {
            throw new IllegalArgumentException("plan, input and productId are required");
        }

        FurnishingPlanDto plan = request.plan();
        PlannerInputDto input = request.input().normalized();
        PlanItemDto itemToReplace = plan.items().stream()
                .filter(item -> item.product().id().equals(request.productId()))
                .findFirst()
                .orElse(null);

        if (itemToReplace == null) return plan;

        Set<String> usedIds = plan.items().stream()
                .map(item -> item.product().id())
                .filter(id -> !id.equals(request.productId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> currentRetailers = plan.items().stream()
                .filter(item -> !item.product().id().equals(request.productId()))
                .map(item -> item.product().retailer())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String changeType = normalizeChangeType(request.changeType());
        String mode = normalizeMode(plan.id());

        if ("remove".equals(changeType)) {
            List<PlanItemDto> nextItems = plan.items().stream()
                    .filter(item -> !item.product().id().equals(request.productId()))
                    .map(item -> enrichExistingItem(item, input))
                    .toList();
            return recalculate(plan, input, orderItemsForShopping(input, nextItems));
        }

        double currentTotalWithoutItem = plan.items().stream()
                .filter(item -> !item.product().id().equals(request.productId()))
                .mapToDouble(item -> item.product().price().doubleValue())
                .sum();
        double remainingBudget = Math.max(0, input.budget() - currentTotalWithoutItem);

        Product alternative = pickReplacement(
                itemToReplace,
                input,
                remainingBudget,
                mode,
                changeType,
                usedIds,
                currentRetailers
        );

        if (alternative == null) return plan;

        List<PlanItemDto> nextItems = plan.items().stream()
                .map(item -> item.product().id().equals(request.productId())
                        ? createPlanItem(alternative, input, mode, replacementPrefix(changeType, item.product().name()))
                        : enrichExistingItem(item, input))
                .toList();

        return recalculate(plan, input, orderItemsForShopping(input, nextItems));
    }

    // Sprint 10.173 (P0 — similar-item + budget-option discovery): given an anchor product and a budget cap,
    // return up to three DISTINCT alternatives from the SAME market's verified catalog (same category + room,
    // within the cap): a cheapest "budget pick", the balanced-score "best value" pick, and a "nicer" step up.
    // Browse-only — this never mutates a plan. No fabrication: an empty pool yields empty buckets. Catalog only:
    // marketCatalog already excludes second-hand/marketplace listings, so used items never leak in here.
    public SimilarItemsResponse findSimilar(SimilarItemsRequest request) {
        if (request == null || request.product() == null || request.input() == null) {
            throw new IllegalArgumentException("product and input are required");
        }
        ProductDto anchor = request.product();
        PlannerInputDto input = request.input().normalized();
        String currency = Markets.currencyFor(input.market());
        String category = anchor.category();
        if (category == null || category.isBlank()) {
            return SimilarItemsResponse.empty(Math.max(1, request.budgetCap()), currency);
        }
        // Clamp the cap into a sane [1, per-currency ceiling] band so a bogus or huge value can't distort results.
        int cap = Math.max(1, Math.min(request.budgetCap(), Markets.budgetCeiling(currency)));
        String anchorId = anchor.id();
        List<String> allowedRetailers = selectedRetailers(input);

        List<Product> pool = marketCatalog(input).stream()
                // Sprint 10.181: for a fixture anchor (bathtub/shower) stay within the SAME fixture family AND facet,
                // so "find similar" for a bathtub returns other bathtubs, not shower enclosures.
                .filter(product -> sameFixtureFamilyAndFacet(product, category, anchor.name()))
                .filter(product -> matchesRoom(product, input.roomType()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> anchorId == null || !anchorId.equalsIgnoreCase(product.getId()))
                .filter(product -> allowedRetailers.contains(product.getRetailer()))
                .filter(product -> product.getPrice().doubleValue() <= cap)
                .toList();

        if (pool.isEmpty()) {
            return SimilarItemsResponse.empty(cap, currency);
        }

        // Pick each bucket in turn, excluding what an earlier bucket already claimed, so the three cards are
        // DISTINCT products where the catalog allows (and degrade gracefully to 2 or 1 when it's thin).
        Set<String> used = new LinkedHashSet<>();

        // Best value = the balanced score winner (the hero), using the same "value" scoring the planner trusts.
        Product bestValue = pool.stream()
                .max(Comparator.comparingDouble(product -> scoreProduct(product, input, "value", Set.of(), 0)))
                .orElse(null);
        if (bestValue != null) used.add(bestValue.getId());

        // Budget pick = the cheapest remaining option — a genuine lower-cost alternative to compare against.
        Product budgetPick = pool.stream()
                .filter(product -> !used.contains(product.getId()))
                .min(Comparator.comparing(Product::getPrice))
                .orElse(null);
        if (budgetPick != null) used.add(budgetPick.getId());

        // Nicer = a real step up: the best-scoring remaining option priced STRICTLY above the best-value pick
        // (still within the cap), rating-weighted. Honestly empty when the cap leaves no room above the hero.
        double stepFloor = bestValue != null ? bestValue.getPrice().doubleValue() : 0;
        Product nicer = pool.stream()
                .filter(product -> !used.contains(product.getId()))
                .filter(product -> product.getPrice().doubleValue() > stepFloor)
                .max(Comparator.comparingDouble(product ->
                        scoreProduct(product, input, "stretch", Set.of(), 0) + product.getRating() * 4))
                .orElse(null);

        return new SimilarItemsResponse(
                budgetPick == null ? null : ProductDto.from(budgetPick),
                bestValue == null ? null : ProductDto.from(bestValue),
                nicer == null ? null : ProductDto.from(nicer),
                cap,
                currency
        );
    }

    private FurnishingPlanDto buildPlan(PlannerInputDto input, String mode, boolean focused) {
        double multiplier = switch (mode) {
            case "stretch" -> 1.12;
            case "value" -> 0.98;
            default -> 0.82;
        };
        double planBudget = input.budget() * multiplier;
        List<String> categories = new ArrayList<>(focused ? focusedCategories(input) : desiredCategories(input));
        Set<String> picked = new LinkedHashSet<>();
        Set<String> currentRetailers = new LinkedHashSet<>();
        // Sprint 10.178 (B): the colours already picked in THIS plan — used to self-coordinate later picks.
        Set<String> currentColors = new LinkedHashSet<>();
        List<PlanItemDto> items = new ArrayList<>();
        double total = 0;

        Set<String> lockedIds = new LinkedHashSet<>(input.lockedProductIds());
        if (!lockedIds.isEmpty()) {
            List<String> lockedOrder = new ArrayList<>(lockedIds);
            List<Product> lockedProducts = marketCatalog(input).stream()
                    .filter(product -> lockedIds.contains(product.getId()))
                    .filter(ProductTaxonomy::canEnterPlanner)
                    .filter(product -> matchesRoom(product, input.roomType()))
                    .sorted(Comparator.comparingInt(product -> lockedOrder.indexOf(product.getId())))
                    .toList();

            for (Product lockedProduct : lockedProducts) {
                if (picked.contains(lockedProduct.getId())) continue;
                picked.add(lockedProduct.getId());
                currentRetailers.add(lockedProduct.getRetailer());
                currentColors.addAll(splitCsv(lockedProduct.getColorTags()));
                total += lockedProduct.getPrice().doubleValue();
                categories.removeIf(category -> category.equalsIgnoreCase(lockedProduct.getCategory()));
                items.add(new PlanItemDto(
                        ProductDto.from(lockedProduct),
                        "Zadržano iz prethodnog plana — ovaj proizvod ostaje, a ostatak plana slažemo oko njega.",
                        priorityForCategory(input.roomType(), lockedProduct.getCategory()),
                        roleForCategory(input.roomType(), lockedProduct.getCategory()),
                        stepForCategory(input.roomType(), lockedProduct.getCategory())
                ));
            }
        }

        if (prefersFewStores(input) && currentRetailers.isEmpty()) {
            preferredRetailerForFewStores(input, categories, mode).ifPresent(currentRetailers::add);
        }

        // Sprint 10.118: when the user asked for a specific good item (focused) or signalled quality,
        // the plan should SPEND toward the budget on nicer pieces instead of flooring to the cheapest.
        boolean preferQuality = prefersQuality(input, focused);
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            double remaining = planBudget - total;
            // Give this category a weighted, fair share of what's left (core pieces get more), so a
            // full room stays complete while a focused 1–2 item request gets a properly nice piece.
            // Sprint 10.119: the VALUE tier always aims to USE the budget (so "Najbolji izbor" is a real
            // step up from "Najjeftinije", not identical to it); the STRETCH tier only spends up when the
            // user signalled quality/focused (its own price bias still drives the balanced splurge tier).
            double perItemTarget = 0;
            // Sprint 10.155: the STRETCH tier now ALSO uses a per-item spend target (not the old uncapped
            // "most-expensive-wins" price bias). That bias picked the single priciest SKU per category and then
            // repairBudget stripped every optional chasing the budget, leaving a degenerate 2-3 item plan at 2x
            // budget (15-market sweep: NO 40796/20000, HR 3953/1800). A target makes stretch a NICER but COMPLETE
            // room near/just-over budget. budget mode still floors to cheapest (no target).
            if (mode.equals("value") || mode.equals("stretch") || (preferQuality && !mode.equals("budget"))) {
                double totalWeight = 0;
                for (int j = i; j < categories.size(); j++) totalWeight += categoryWeight(input.roomType(), categories.get(j));
                perItemTarget = spendTarget(remaining, categoryWeight(input.roomType(), category), totalWeight, mode, preferQuality);
            }
            // Sprint 10.120: honour a requested count (e.g. "6 dining chairs"). The per-UNIT budget and target
            // are the category share divided by the count, so N units fit the budget instead of one item being
            // picked and shipped alone. The line total is unit price * count.
            int qty = quantityFor(input, category);
            double remainingPerUnit = qty > 1 ? remaining / qty : remaining;
            double perUnitTarget = qty > 1 ? perItemTarget / qty : perItemTarget;
            // Sprint 10.157: even in a focused plan, try the item in its OWN room FIRST (so a home-office "chair"
            // resolves to an office chair, not the priciest lounge armchair the anyRoom pool offered), and fall
            // back to any room only when the item genuinely isn't in the plan's room (so a cross-room "bed + sofa"
            // ask still finds the sofa). Whole-room plans (focused=false) stay strictly room-scoped as before.
            Product product = pickBest(category, input, remainingPerUnit, mode, picked, currentRetailers, currentColors, perUnitTarget, false);
            if (product == null && focused) {
                product = pickBest(category, input, remainingPerUnit, mode, picked, currentRetailers, currentColors, perUnitTarget, true);
            }
            // Sprint 10.122: the user explicitly asked for this category (focused or must-have) but nothing fits
            // the budget/cap — rather than an empty tier ("Najbolji izbor" with no items, which looks broken),
            // offer the cheapest real option. The budget status then honestly shows it's above the stated cap.
            if (product == null && (focused || input.mustHaveCategories().contains(category))) {
                product = cheapestInCategory(category, input, picked, Double.MAX_VALUE, false);
                if (product == null && focused) {
                    product = cheapestInCategory(category, input, picked, Double.MAX_VALUE, true);
                }
            }
            if (product == null) continue;

            picked.add(product.getId());
            currentRetailers.add(product.getRetailer());
            currentColors.addAll(splitCsv(product.getColorTags()));
            total += product.getPrice().doubleValue() * qty;
            items.add(createPlanItem(product, input, mode, ""));
        }

        String label = switch (mode) {
            case "stretch" -> "Kompletniji prostor";
            case "value" -> "Uravnoteženo";
            default -> "Samo osnovno";
        };
        String name = switch (mode) {
            case "stretch" -> "Ljepša verzija";
            case "value" -> "Najbolji izbor";
            default -> "Najjeftinije";
        };

        List<PlanItemDto> repairedItems = repairBudget(input, items, mode);

        Set<String> retailersInPlan = repairedItems.stream()
                .map(item -> item.product().retailer())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        double repairedTotal = sumPrice(repairedItems);
        return calculatePlan(mode, name, label, describePlan(mode, input, repairedTotal, retailersInPlan), input, orderItemsForShopping(input, repairedItems));
    }

    private Product pickBest(String category, PlannerInputDto input, double remainingBudget, String mode, Set<String> picked, Set<String> currentRetailers,
                            Set<String> currentColors, double perItemTarget, boolean anyRoom) {
        List<String> allowedRetailers = selectedRetailers(input);
        boolean coreCategory = isCoreCategory(input.roomType(), category);
        double realisticLimit = coreCategory && !mode.equals("budget")
                ? Math.max(remainingBudget, input.budget() * 0.28)
                : remainingBudget;

        return marketCatalog(input).stream()
                .filter(product -> categoryMatchesSlot(product, category, input))
                // Sprint 10.156: in a FOCUSED plan every category is an item the user explicitly named, so a
                // cross-room ask ("a bed AND a sofa") must not drop the sofa just because the plan's inferred room
                // is the bedroom — match any room for those. Whole-room plans keep the strict room filter.
                .filter(product -> anyRoom || matchesRoom(product, input.roomType()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> !picked.contains(product.getId()))
                .filter(product -> allowedRetailers.contains(product.getRetailer()))
                .filter(product -> product.getPrice().doubleValue() <= realisticLimit || mode.equals("stretch"))
                .max(Comparator.comparingDouble(product -> scoreProduct(product, input, mode, currentRetailers, currentColors, perItemTarget)))
                .orElse(null);
    }

    private double scoreProduct(Product product, PlannerInputDto input, String mode, Set<String> currentRetailers, double perItemTarget) {
        return scoreProduct(product, input, mode, currentRetailers, Set.of(), perItemTarget);
    }

    private double scoreProduct(Product product, PlannerInputDto input, String mode, Set<String> currentRetailers, Set<String> currentColors, double perItemTarget) {
        double styleScore = styleMatches(product, input.style()) ? 38 : 12;
        double roomScore = matchesRoom(product, input.roomType()) ? 36 : 0;
        double ratingScore = product.getRating() * 5;
        double stockScore = product.isInStock() ? 10 : -80;
        double discountScore = product.getOriginalPrice() != null ? 8 : 0;
        double price = product.getPrice().doubleValue();

        double priceBias;
        if (UTILITY_ROOMS.contains(input.roomType())) {
            // Sprint 10.179: utility rooms are FUNCTIONAL in every tier — always lean cheap, never spend up (no
            // designer lamp or €2000 bookcase in a garage), so they don't blow the budget and a real laundry basket
            // wins its slot. Overrides the target + the stretch pricey-wins bias below for these rooms only.
            priceBias = Math.max(0, 20 - price / 55);
        } else if (perItemTarget > 0) {
            // Sprint 10.118/10.119: spend-up. Reward using the per-item budget share (peaks at the target),
            // with a gentle penalty above it so a full room stays complete. This replaces the
            // cheapest-wins bias so the value tier (and a focused/quality request) picks a nicer tier
            // within budget instead of flooring to the cheapest.
            // Sprint 10.137: peak raised 26 -> 34 so the per-item budget target competes with styleScore (38) and
            // reliably pulls the pick up to the mid tier instead of a style-matching cheap SKU winning every time
            // (the cause of the DE/ES/IT under-fill). Gentle over-penalty keeps a full room complete; the hard
            // budget cap in repairBudget is the real ceiling, so a strong pull-to-target is safe.
            double ratio = price / perItemTarget;
            priceBias = 34.0 * Math.min(1.0, ratio);
            if (ratio > 1.0) priceBias -= 15.0 * Math.min(1.0, ratio - 1.0);
        } else if (input.optimizationGoal().equals("lowest-price") || mode.equals("budget")) {
            priceBias = Math.max(0, 34 - price / 18);
        } else if (mode.equals("value")) {
            priceBias = Math.max(0, 20 - price / 55);
        } else {
            priceBias = price / 48;
        }

        double leastStoresBonus = prefersFewStores(input)
                ? (currentRetailers.contains(product.getRetailer()) || currentRetailers.isEmpty() ? 42 : -32)
                : 0;
        double stylePriorityBonus = input.optimizationGoal().equals("style-match") && styleMatches(product, input.style()) ? 20 : 0;
        double singleStoreBonus = input.retailerMode().equals("single") ? 14 : 0;
        double coreBonus = isCoreCategory(input.roomType(), product.getCategory()) ? 12 : 0;
        double preferredRetailerBonus = input.preferredRetailers() != null && input.preferredRetailers().contains(product.getRetailer()) ? 30 : 0;
        double requestedBonus = input.mustHaveCategories() != null && input.mustHaveCategories().contains(product.getCategory()) ? 18 : 0;
        double storeCapBonus = storeCapBonus(input, product, currentRetailers);
        double dataQualityBonus = dataQualityBonus(product);
        double preferenceBonus = colorMaterialBonus(product, input);
        double coherenceBonus = colorCoherenceBonus(product, input, currentColors);
        // Sprint 10.179: soft, capped room-size fit nudge (0 for the default/medium room, so a no-op there).
        double fitBonus = roomFitBonus(product, input);
        // Sprint 10.179: laundry-room-only nudge so a real laundry basket wins the storage slot (0 elsewhere).
        double laundryBonus = laundryFitBonus(product, input);

        return styleScore + roomScore + ratingScore + stockScore + discountScore + priceBias
                + leastStoresBonus + stylePriorityBonus + singleStoreBonus + coreBonus
                + preferredRetailerBonus + requestedBonus + storeCapBonus + dataQualityBonus
                + preferenceBonus + coherenceBonus + fitBonus + laundryBonus;
    }

    // Sprint 10.178 (B): colours that read as a clean "neutral base" (bathroom fixtures anchor to these).
    private static final Set<String> NEUTRAL_COLORS = Set.of("white", "grey", "beige", "natural");
    private static final Set<String> FIXTURE_CATEGORIES = Set.of("toilet", "washbasin", "bath-shower");

    // Sprint 10.178 (B): when the user did NOT state a colour, gently self-coordinate the plan — reward a candidate
    // whose colour overlaps the palette already picked (currentColors), and anchor bathroom FIXTURES to a neutral
    // base so the first fixture (which seeds the palette) defaults to white/neutral instead of a random loud colour
    // (the "black toilet + white washbasin" bug). Capped well below styleScore(38)/roomScore(36) so it only breaks
    // ties — never overrides style, room or budget. An explicit colour keeps full control (colorMaterialBonus drives
    // it; this returns 0 then).
    private double colorCoherenceBonus(Product product, PlannerInputDto input, Set<String> currentColors) {
        if (input.colorPreferences() != null && !input.colorPreferences().isEmpty()) return 0;
        Set<String> productColors = new LinkedHashSet<>(splitCsv(product.getColorTags()));
        if (productColors.isEmpty()) return 0;
        double coherence = currentColors != null && currentColors.stream().anyMatch(productColors::contains) ? 12 : 0;
        double neutralAnchor = FIXTURE_CATEGORIES.contains(product.getCategory())
                && productColors.stream().anyMatch(NEUTRAL_COLORS::contains) ? 8 : 0;
        return coherence + neutralAnchor;
    }

    // ── Sprint 10.179: size (m²) → piece-fit signal ───────────────────────────────────────────────────────
    // A soft "does this piece fit the room's size" nudge. We have NO structured dimensions — they live only in
    // product NAMES ("KIVIK 3-seat", "MALM 160x200", "ÄNGSJÖN 80x48x63") — so this reads a footprint band from the
    // name and, for an explicitly small/large room, gently biases the pick toward a fitting piece. It is capped
    // well below styleScore(38)/roomScore(36) → it only breaks ties, never overrides style, room or budget. A
    // MEDIUM/default room (size 20) or a piece with no size signal contributes 0, so existing plans are unchanged.

    // Only these big pieces get a size band — a rug/lamp/decor's footprint doesn't meaningfully change with room size.
    private static final Set<String> FOOTPRINT_CATEGORIES = Set.of(
            "sofa", "bed", "wardrobe", "dining-table", "dresser", "tv-unit", "desk", "storage");

    // A dimension GROUP like "160x200" or "80x48x63": the WIDTH is the first number (IKEA names are W×L or W×D×H).
    private static final Pattern DIMENSION_GROUP = Pattern.compile("(\\d{2,3})\\s*[x×]\\s*\\d{2,3}(?:\\s*[x×]\\s*\\d{2,3})?");
    // A standalone "200 cm". Requiring the 'cm' unit (or the 'x' join above) keeps bare model numbers / years from
    // being mis-read as a size (the "no collisions" guard).
    private static final Pattern DIMENSION_SINGLE = Pattern.compile("(\\d{2,3})\\s*cm\\b");
    // Numeric seat counts across the 15 markets: 2-seat / 2-seater / 2-Sitzer / 2 posti / 2 plazas / 2-sits /
    // 2-personers / 2 places / 2-sjed. The unit word is required so "2 drawers" is not read as 2 seats.
    private static final Pattern SEAT_COUNT = Pattern.compile(
            "(\\d)\\s*[- ]?\\s*(?:seater|seat|sitzer|posti|plazas|sits|personers?|places?|plätze|sjed)");
    // Corner / sectional keywords (multilingual) → always a large piece regardless of a stated seat count.
    private static final Pattern SECTIONAL = Pattern.compile(
            "corner|sectional|kutn|sekcij|ecksofa|angolo|rinconera|u-form|l-form|l-shape|u-shape");

    enum RoomBand { SMALL, MEDIUM, LARGE }
    enum PieceFit { COMPACT, MID, LARGE, NEUTRAL }

    // SMALL ≤ 14 m², LARGE ≥ 26 m², else MEDIUM (neutral). An unset/garbage size (≤ 0) is treated as MEDIUM so it
    // never applies a bias (input.size() is normalized to a default of 20 before it reaches here anyway).
    static RoomBand roomBand(int sizeM2) {
        if (sizeM2 <= 0) return RoomBand.MEDIUM;
        if (sizeM2 <= 14) return RoomBand.SMALL;
        if (sizeM2 >= 26) return RoomBand.LARGE;
        return RoomBand.MEDIUM;
    }

    // The piece's footprint band, read from its NAME. Only footprint categories get a band; for a sofa without cm
    // dimensions we fall back to the seat count (1-2 = compact, 3 = normal, 4+/corner = large). No signal → NEUTRAL.
    static PieceFit pieceFit(Product product) {
        if (product == null || product.getCategory() == null) return PieceFit.NEUTRAL;
        String category = product.getCategory().toLowerCase(Locale.ROOT);
        if (!FOOTPRINT_CATEGORIES.contains(category)) return PieceFit.NEUTRAL;
        String name = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ROOT);

        OptionalInt width = footprintWidthCm(name);
        if (width.isPresent()) return bandForWidthCm(width.getAsInt());

        if (category.equals("sofa")) {
            if (SECTIONAL.matcher(name).find()) return PieceFit.LARGE;
            OptionalInt seats = seatCount(name);
            if (seats.isPresent()) return bandForSeats(seats.getAsInt());
        }
        return PieceFit.NEUTRAL;
    }

    private static OptionalInt footprintWidthCm(String name) {
        int width = -1;
        Matcher group = DIMENSION_GROUP.matcher(name);
        while (group.find()) width = Math.max(width, Integer.parseInt(group.group(1)));
        if (width < 0) {
            Matcher single = DIMENSION_SINGLE.matcher(name);
            while (single.find()) width = Math.max(width, Integer.parseInt(single.group(1)));
        }
        return width < 0 ? OptionalInt.empty() : OptionalInt.of(width);
    }

    private static OptionalInt seatCount(String name) {
        Matcher numeric = SEAT_COUNT.matcher(name);
        if (numeric.find()) return OptionalInt.of(Integer.parseInt(numeric.group(1)));
        // Croatian word-forms (dvo = 2, tro = 3, četvero/četvoro = 4) carry the seat count as a prefix, not a digit.
        if (name.contains("jednosjed") || name.contains("jednosed")) return OptionalInt.of(1);
        if (name.contains("dvosjed") || name.contains("dvosed")) return OptionalInt.of(2);
        if (name.contains("trosjed") || name.contains("trosed")) return OptionalInt.of(3);
        if (name.contains("četverosjed") || name.contains("cetverosjed")
                || name.contains("četvorosjed") || name.contains("cetvorosjed")) return OptionalInt.of(4);
        return OptionalInt.empty();
    }

    private static PieceFit bandForWidthCm(int widthCm) {
        if (widthCm < 130) return PieceFit.COMPACT;
        if (widthCm > 190) return PieceFit.LARGE;
        return PieceFit.MID;
    }

    private static PieceFit bandForSeats(int seats) {
        if (seats <= 2) return PieceFit.COMPACT;
        if (seats == 3) return PieceFit.MID;
        return PieceFit.LARGE;
    }

    // Maps the room band × the piece's footprint band to a small, capped score nudge (added in scoreProduct). A
    // SMALL room rewards a compact piece / penalizes an oversized one; a LARGE room rewards a larger piece / mildly
    // penalizes a tiny one. A MEDIUM/default room, a MID piece, or a piece with no size signal all contribute 0 —
    // so the default size (20 m²) and every piece we can't read leave the existing scoring untouched.
    private double roomFitBonus(Product product, PlannerInputDto input) {
        RoomBand room = roomBand(input.size());
        if (room == RoomBand.MEDIUM) return 0;
        PieceFit piece = pieceFit(product);
        if (piece == PieceFit.NEUTRAL || piece == PieceFit.MID) return 0;
        return switch (room) {
            case SMALL -> piece == PieceFit.COMPACT ? 10 : -10;   // compact fits a small room; a large piece crowds it
            case LARGE -> piece == PieceFit.LARGE ? 8 : -4;       // a large piece suits a big room; a tiny one looks lost
            default -> 0;
        };
    }

    // Sprint 10.179: in the laundry room ONLY, nudge a real laundry-tagged item (a basket) to win the storage slot
    // over a generic shelf. Capped and laundry-room-only (0 everywhere else — so bathroom keeps the same basket as
    // ordinary storage, no regression). Below styleScore(38)/roomScore(36) → it only breaks the storage tie.
    private double laundryFitBonus(Product product, PlannerInputDto input) {
        if (!"laundry".equals(input.roomType())) return 0;
        return splitCsv(product.getRoomTags()).contains("laundry") ? 10 : 0;
    }

    // Sprint 10.118: should this plan spend toward the budget on nicer pieces instead of flooring to the
    // cheapest? Yes when the user asked for a specific good item (focused) or signalled quality. The AI's
    // qualityPreference is unreliable for "dobar"/"najbolji" (it maps them to balanced), so focused is the
    // primary, reliable trigger; an explicit style-match/complete choice (form or AI premium) also counts.
    private boolean prefersQuality(PlannerInputDto input, boolean focused) {
        return focused
                || "style-match".equals(input.optimizationGoal())
                || "complete".equals(input.furnishingLevel());
    }

    // Target spend for ONE item when the plan should use the budget. A weighted, fair share of what's left
    // (core pieces get more than secondary ones), scaled by tier: value aims a little under the share,
    // stretch fills it. Catalog price ceilings keep small categories (rug/decor) cheap on their own.
    private double spendTarget(double remaining, double myWeight, double totalWeight, String mode, boolean preferQuality) {
        if (totalWeight <= 0 || remaining <= 0) return 0;
        // value: a plain (balanced) plan aims at ~0.6 of its weighted share so it clearly beats the
        // cheapest tier yet leaves headroom; a quality/focused request aims higher (0.85). stretch only
        // gets a target when quality/focused (else it keeps its own price bias for the balanced splurge).
        // Sprint 10.137: balanced value raised 0.6 -> 0.75 so the RECOMMENDED plan actually uses a generous budget
        // (DE/ES/IT full-rooms were landing at 32-52% fill, reaching for bottom SKUs). Safe now that repairBudget
        // hard-caps value at the stated budget (it down-tiers even core items if needed), so aiming higher can't
        // blow the budget. Reaching mid-tier also surfaces local retailers the cheapest-bias never picked.
        double factor = switch (mode) {
            // Sprint 10.155: stretch always aims ~5% over its weighted share (a "nicer, complete" splurge that
            // leans just over budget), whether or not quality was signalled — repairBudget's 12% stretch headroom
            // keeps the resulting room full instead of trimming it. (Was 0.9 for balanced + a separate price bias.)
            case "stretch" -> 1.05;
            case "value" -> preferQuality ? 0.85 : 0.75;
            default -> 0.6;
        };
        return Math.max(0, remaining * (myWeight / totalWeight) * factor);
    }

    private double categoryWeight(String roomType, String category) {
        return isCoreCategory(roomType, category) ? 2.5 : 1.0;
    }

    // Sprint 10.7: gently prefer products whose colour/material tags match what the user asked for
    // ("zelene zidove, drvo i crni detalji"). Deliberately capped well below styleScore (38) and
    // roomScore (36) so a colour/material match nudges ties but never overrides style, room or price.
    private double colorMaterialBonus(Product product, PlannerInputDto input) {
        double colorBonus = Math.min(16, overlapCount(product.getColorTags(), input.colorPreferences()) * 10);
        double materialBonus = Math.min(16, overlapCount(product.getMaterialTags(), input.materialPreferences()) * 10);
        return Math.min(24, colorBonus + materialBonus);
    }

    private int overlapCount(String productCsvTags, List<String> preferences) {
        if (preferences == null || preferences.isEmpty()) return 0;
        Set<String> productTags = new LinkedHashSet<>(splitCsv(productCsvTags));
        if (productTags.isEmpty()) return 0;
        return (int) preferences.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .filter(productTags::contains)
                .count();
    }

    // Sprint 9.0: gently prefer products with better data (real link, image, recent check,
    // complete quality). Small tie-breaker bonuses — never enough to override style/room/price,
    // and never shown to the user as a "score".
    private double dataQualityBonus(Product product) {
        double bonus = 0;
        if (hasContent(product.getProductUrl())) bonus += 6;
        if (hasContent(product.getImageUrl())) bonus += 4;
        if (!ProductTaxonomy.isStale(product.getLastCheckedAt())) bonus += 6;
        String quality = product.getDataQuality();
        if ("complete".equalsIgnoreCase(quality)) bonus += 6;
        else if ("needs-review".equalsIgnoreCase(quality)) bonus -= 6;
        return bonus;
    }

    private boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }

    // Store limit v1: when the user asked for at most N stores, reward staying inside the
    // stores already in the plan and strongly penalise opening a new one past the limit.
    // The penalty is soft: if a category only exists in a new store, that product can still
    // be the best available option and gets picked, which is the "allow another store if
    // really needed" behaviour.
    private double storeCapBonus(PlannerInputDto input, Product product, Set<String> currentRetailers) {
        int cap = input.maxStores();
        if (cap <= 0) return 0;
        boolean newStore = !currentRetailers.contains(product.getRetailer());
        if (!newStore) return 22;
        return currentRetailers.size() >= cap ? -160 : -10;
    }

    private Product pickReplacement(
            PlanItemDto itemToReplace,
            PlannerInputDto input,
            double remainingBudget,
            String mode,
            String changeType,
            Set<String> picked,
            Set<String> currentRetailers
    ) {
        ProductDto current = itemToReplace.product();
        Set<String> blockedIds = new LinkedHashSet<>(picked);
        blockedIds.add(current.id());
        List<String> allowedRetailers = selectedRetailers(input);
        double currentPrice = current.price().doubleValue();
        double safeLimit = Math.max(remainingBudget, currentPrice);
        double stretchLimit = Math.max(remainingBudget, Math.min(input.budget() * 0.45, currentPrice * 1.25));

        return marketCatalog(input).stream()
                // Sprint 10.181: a replacement for a bathtub stays a bathtub (and a shower a shower) — preserve the
                // fixture subtype unless a future changeType explicitly asks to switch it.
                .filter(product -> sameFixtureFamilyAndFacet(product, current.category(), current.name()))
                .filter(product -> matchesRoom(product, input.roomType()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> !blockedIds.contains(product.getId()))
                .filter(product -> allowedRetailers.contains(product.getRetailer()))
                .filter(product -> replacementFits(product, changeType, currentPrice, safeLimit, stretchLimit))
                .max(Comparator.comparingDouble(product -> scoreReplacement(product, current, input, mode, changeType, currentRetailers)))
                .orElse(null);
    }

    private boolean replacementFits(Product product, String changeType, double currentPrice, double safeLimit, double stretchLimit) {
        double price = product.getPrice().doubleValue();
        return switch (changeType) {
            case "cheaper" -> price < currentPrice && price <= safeLimit;
            case "nicer" -> price >= currentPrice * 0.92 && price <= stretchLimit;
            case "different" -> price <= Math.max(safeLimit, currentPrice * 1.12);
            default -> price <= safeLimit;
        };
    }

    private double scoreReplacement(Product product, ProductDto current, PlannerInputDto input, String mode, String changeType, Set<String> currentRetailers) {
        double base = scoreProduct(product, input, mode, currentRetailers, 0);
        double price = product.getPrice().doubleValue();
        double currentPrice = current.price().doubleValue();
        double retailerVariety = product.getRetailer().equals(current.retailer()) ? 0 : 8;
        double differentStyle = productStyleMatches(splitCsv(product.getStyleTags()), input.style()) ? 10 : 0;

        return switch (changeType) {
            case "cheaper" -> base + Math.max(0, currentPrice - price) / 8;
            case "nicer" -> base + product.getRating() * 4 + Math.max(0, price - currentPrice) / 35;
            case "different" -> base + retailerVariety + differentStyle;
            default -> base;
        };
    }

    private String replacementPrefix(String changeType, String oldName) {
        return switch (changeType) {
            case "cheaper" -> "Povoljnija opcija umjesto " + oldName + ": ";
            case "nicer" -> "Ljepša opcija umjesto " + oldName + ": ";
            case "different" -> "Druga opcija umjesto " + oldName + ": ";
            default -> "Zamjena za " + oldName + ": ";
        };
    }

    private FurnishingPlanDto recalculate(FurnishingPlanDto originalPlan, PlannerInputDto input, List<PlanItemDto> items) {
        return calculatePlan(
                originalPlan.id(),
                originalPlan.name(),
                originalPlan.label(),
                originalPlan.description(),
                input,
                items
        );
    }

    private FurnishingPlanDto calculatePlan(String id, String name, String label, String description, PlannerInputDto input, List<PlanItemDto> items) {
        List<PlanItemDto> cleanItems = cleanPlanItems(input, items);
        double total = cleanItems.stream().mapToDouble(item -> item.product().price().doubleValue() * Math.max(1, item.quantity())).sum();
        List<String> retailersUsed = cleanItems.stream()
                .map(item -> item.product().retailer())
                .distinct()
                .toList();
        long styleMatches = cleanItems.stream().filter(item -> productStyleMatches(item.product().styleTags(), input.style())).count();
        int styleConsistency = cleanItems.isEmpty() ? 0 : Math.min(99, (int) Math.round(((double) styleMatches / cleanItems.size()) * 100 + 8));
        String shoppingEffort = retailersUsed.size() <= 1 ? "Low" : retailersUsed.size() <= 3 ? "Medium" : "High";
        int budgetFit = total <= input.budget() ? 8 : -6;
        int fitScore = Math.min(98, Math.max(48, (int) Math.round(62 + cleanItems.size() * 4 + styleConsistency / 6.0 + budgetFit)));
        StoreTripDto storeTrip = buildStoreTrip(cleanItems);
        double overBudget = Math.max(0, total - input.budget());

        return new FurnishingPlanDto(
                id,
                name,
                label,
                description,
                buildSummary(id, input, cleanItems, total, retailersUsed),
                buildGoodFor(id, input),
                buildTradeoff(id, input, total, retailersUsed),
                buildBudgetStatus(input, total),
                buildAdvisorNote(id, input, cleanItems, total, retailersUsed),
                buildNextStep(input, cleanItems, total),
                buildSavingTips(input, cleanItems, total),
                buildUpgradeTips(input, cleanItems, total),
                cleanItems,
                money(total),
                money(Math.max(0, input.budget() - total)),
                fitScore,
                shoppingEffort,
                styleConsistency,
                retailersUsed,
                storeTrip,
                buildPurchaseSummary(input, cleanItems, total, storeTrip),
                buildBudgetRepairSuggestions(input, cleanItems, total),
                money(overBudget),
                buildStoreLimitNote(input, retailersUsed)
        );
    }

    private StoreTripDto buildStoreTrip(List<PlanItemDto> items) {
        Map<String, List<PlanItemDto>> grouped = items.stream()
                .collect(Collectors.groupingBy(item -> item.product().retailer(), LinkedHashMap::new, Collectors.toList()));

        List<StoreTotalDto> stores = grouped.entrySet().stream()
                .map(entry -> new StoreTotalDto(
                        entry.getKey(),
                        money(entry.getValue().stream().mapToDouble(item -> item.product().price().doubleValue() * Math.max(1, item.quantity())).sum()),
                        entry.getValue().stream().mapToInt(item -> Math.max(1, item.quantity())).sum()))
                .sorted(Comparator.comparing(StoreTotalDto::total).reversed())
                .toList();

        int storeCount = stores.size();
        String mainRetailer = stores.isEmpty() ? null : stores.get(0).retailer();
        BigDecimal mainRetailerTotal = stores.isEmpty() ? money(0) : stores.get(0).total();
        int checkInStoreCount = (int) items.stream().filter(this::needsStoreCheck).count();
        String recommendation = buildStoreTripRecommendation(storeCount, mainRetailer, checkInStoreCount);

        return new StoreTripDto(storeCount, mainRetailer, mainRetailerTotal, checkInStoreCount, recommendation, stores);
    }

    private boolean needsStoreCheck(PlanItemDto item) {
        String status = item.product().availabilityStatus();
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("limited") || normalized.equals("check-store");
    }

    private String buildStoreTripRecommendation(int storeCount, String mainRetailer, int checkInStoreCount) {
        String base;
        if (storeCount <= 0 || mainRetailer == null) {
            base = "Plan je još prazan — dodaj prvi komad da krenemo.";
        } else if (storeCount == 1) {
            base = "Sve na jednom mjestu, u " + mainRetailer + " — jedan odlazak i gotovo.";
        } else {
            base = "Najviše je u " + mainRetailer + ", a ostatak pokupiš u još " + (storeCount - 1) + " " + storesWordLocative(storeCount - 1) + ".";
        }
        if (checkInStoreCount > 0) {
            base += checkInStoreCount == 1
                    ? " Jednu stvar prvo provjeri u trgovini."
                    : " Neke stvari prvo provjeri u trgovini.";
        }
        return base;
    }

    private String storesWord(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return "trgovine";
        return "trgovina";
    }

    // Locative case for "u još N ___" (e.g. "u još 1 trgovini", "u još 2 trgovine", "u još 5 trgovina").
    private String storesWordLocative(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod10 == 1 && mod100 != 11) return "trgovini";
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return "trgovine";
        return "trgovina";
    }

    // Accusative case for "koristi N ___" (e.g. "koristi 1 trgovinu", "koristi 2 trgovine", "koristi 5 trgovina").
    private String storesWordAccusative(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod10 == 1 && mod100 != 11) return "trgovinu";
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return "trgovine";
        return "trgovina";
    }

    // Budget repair v1. Only runs while building a fresh plan (not on manual replace).
    // Order: keep the most important pieces, make optional pieces cheaper, then move the
    // least important optional pieces out of the main buy. Core and explicitly requested
    // categories are never dropped.
    private List<PlanItemDto> repairBudget(PlannerInputDto input, List<PlanItemDto> items, String mode) {
        List<PlanItemDto> working = new ArrayList<>(items);
        // Sprint 10.155: the stretch ("splurge") tier may sit modestly over the stated budget for a NICER, COMPLETE
        // room, so it tolerates up to 12% over before trimming optionals — otherwise it gets stripped to a few
        // expensive core pieces. value/budget keep the hard budget ceiling. (overBudgetAmount, shown to the user, is
        // still measured against the real input.budget() in calculatePlan — this only relaxes the internal trim.)
        double budget = input.budget() * ("stretch".equals(mode) ? 1.12 : 1.0);
        if (sumPrice(working) <= budget) return working;

        Set<String> protectedCats = new LinkedHashSet<>(CORE_CATEGORIES_BY_ROOM.getOrDefault(input.roomType(), Set.of()));
        protectedCats.addAll(input.mustHaveCategories());

        List<String> optionalIds = working.stream()
                .filter(item -> !protectedCats.contains(item.product().category()))
                .sorted(Comparator.comparing((PlanItemDto item) -> item.product().price()).reversed())
                .map(item -> item.product().id())
                .toList();
        for (String id : optionalIds) {
            if (sumPrice(working) <= budget) break;
            int pos = indexOfId(working, id);
            if (pos < 0) continue;
            PlanItemDto item = working.get(pos);
            Set<String> usedIds = working.stream().map(other -> other.product().id()).collect(Collectors.toCollection(LinkedHashSet::new));
            usedIds.remove(id);
            Product cheaper = cheapestInCategory(item.product().category(), input, usedIds, item.product().price().doubleValue(), false);
            if (cheaper != null) {
                working.set(pos, createPlanItem(cheaper, input, mode, "Povoljnija opcija da plan ostane bliže budžetu: "));
            }
        }

        // Sprint 10.156: at a budget so low the PROTECTED core already exceeds the (stretch) ceiling, dropping
        // optionals can't bring the plan under it — it only leaves the STRETCH tier sparse AND still over budget
        // (worse than value/budget; the sweep flagged this on ~400 EUR living rooms). So shed optionals only when
        // that can actually achieve a fit; otherwise keep the fuller, nicer room and let it read as honestly over.
        double protectedSubtotal = working.stream()
                .filter(item -> protectedCats.contains(item.product().category()))
                .mapToDouble(item -> item.product().price().doubleValue() * Math.max(1, item.quantity()))
                .sum();
        if (!"stretch".equals(mode) || protectedSubtotal <= budget) {
            for (String priority : List.of("later", "add-comfort")) {
                while (sumPrice(working) > budget) {
                    PlanItemDto target = working.stream()
                            .filter(item -> !protectedCats.contains(item.product().category()))
                            .filter(item -> priority.equals(priorityForCategory(input.roomType(), item.product().category())))
                            .max(Comparator.comparing(item -> item.product().price()))
                            .orElse(null);
                    if (target == null) break;
                    working.removeIf(item -> item.product().id().equals(target.product().id()));
                }
            }
        }

        // Sprint 10.137: the VALUE (headline) plan must not exceed the stated budget. If trimming optionals wasn't
        // enough, the overage is in PROTECTED core/must-have items — a count set ("6 chairs + table") or a bed+
        // mattress on a tiny budget. Down-tier those to their cheapest in-category option too (keep them PRESENT so
        // the room stays complete; never drop a core piece). Stretch is the deliberate "spend a bit more" tier, so
        // it's left free to exceed. If even the cheapest essentials still overrun, the plan stays honestly over
        // (the UI flags it) — that floor is physically unavoidable.
        if (!"stretch".equals(mode)) {
            List<String> protectedByPrice = working.stream()
                    .filter(item -> protectedCats.contains(item.product().category()))
                    .sorted(Comparator.comparing((PlanItemDto item) -> item.product().price()).reversed())
                    .map(item -> item.product().id())
                    .toList();
            for (String id : protectedByPrice) {
                if (sumPrice(working) <= budget) break;
                int pos = indexOfId(working, id);
                if (pos < 0) continue;
                PlanItemDto item = working.get(pos);
                Set<String> usedIds = working.stream().map(other -> other.product().id()).collect(Collectors.toCollection(LinkedHashSet::new));
                usedIds.remove(id);
                Product cheaper = cheapestInCategory(item.product().category(), input, usedIds, item.product().price().doubleValue(), false);
                if (cheaper != null) {
                    working.set(pos, createPlanItem(cheaper, input, mode, "Povoljnija opcija da plan stane u budžet: "));
                }
            }
        }

        return working;
    }

    private double sumPrice(List<PlanItemDto> items) {
        return items.stream().mapToDouble(item -> item.product().price().doubleValue() * Math.max(1, item.quantity())).sum();
    }

    private int indexOfId(List<PlanItemDto> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product().id().equals(id)) return i;
        }
        return -1;
    }

    private Product cheapestInCategory(String category, PlannerInputDto input, Set<String> excludeIds, double maxPriceExclusive, boolean anyRoom) {
        List<String> allowed = selectedRetailers(input);
        return marketCatalog(input).stream()
                .filter(product -> categoryMatchesSlot(product, category, input))
                .filter(product -> anyRoom || matchesRoom(product, input.roomType()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> allowed.contains(product.getRetailer()))
                .filter(product -> !excludeIds.contains(product.getId()))
                .filter(product -> product.getPrice().doubleValue() < maxPriceExclusive)
                .min(Comparator.comparing(Product::getPrice))
                .orElse(null);
    }

    private List<String> buildPurchaseSummary(PlannerInputDto input, List<PlanItemDto> items, double total, StoreTripDto storeTrip) {
        List<String> summary = new ArrayList<>();
        int storeCount = storeTrip == null ? 0 : storeTrip.storeCount();
        if (total <= input.budget()) {
            summary.add("Sve staje u budžet, a kupuješ u " + storeCount + " " + storesWordAccusative(storeCount) + ".");
        } else {
            summary.add("Plan je " + money(total - input.budget()) + " iznad budžeta — pogledaj kako ga spustiti.");
        }
        if (!input.alreadyHaveCategories().isEmpty()) {
            String labels = input.alreadyHaveCategories().stream()
                    .map(this::categoryLabel)
                    .distinct()
                    .limit(3)
                    .collect(Collectors.joining(", "));
            summary.add(capitalize(labels) + " ne dodajem jer si rekao da već imaš.");
        }
        if (!input.mustHaveCategories().isEmpty()) {
            String first = categoryLabel(input.mustHaveCategories().get(0));
            summary.add(capitalize(first) + " je ostao prioritet jer si ga posebno tražio.");
        }
        if (storeCount > 1 && storeTrip.mainRetailer() != null) {
            summary.add("Većinu kupuješ u " + storeTrip.mainRetailer() + ".");
        }
        return summary.stream().limit(4).toList();
    }

    private List<String> buildBudgetRepairSuggestions(PlannerInputDto input, List<PlanItemDto> items, double total) {
        double budget = input.budget();
        boolean tight = total >= budget * 0.92;
        if (!tight) return List.of();

        Set<String> protectedCats = new LinkedHashSet<>(CORE_CATEGORIES_BY_ROOM.getOrDefault(input.roomType(), Set.of()));
        protectedCats.addAll(input.mustHaveCategories());

        List<String> tips = new ArrayList<>();
        items.stream()
                .filter(item -> "later".equals(priorityForCategory(input.roomType(), item.product().category())))
                .filter(item -> !protectedCats.contains(item.product().category()))
                .max(Comparator.comparing(item -> item.product().price()))
                .ifPresent(item -> tips.add("Preskoči " + categoryLabel(item.product().category()).toLowerCase(Locale.ROOT) + " i štedi " + money(item.product().price().doubleValue()) + "."));

        items.stream()
                .filter(item -> !protectedCats.contains(item.product().category()))
                .sorted(Comparator.comparing((PlanItemDto item) -> item.product().price()).reversed())
                .map(item -> swapSuggestion(input, item))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(tips::add);

        tips.add(total > budget
                ? "Kreni s najvažnijim stvarima, a ostalo dodaj kad ostane budžeta."
                : "Možeš sve uzeti odmah, ali kreni od najvažnijih stvari.");
        return tips.stream().distinct().limit(3).toList();
    }

    private String swapSuggestion(PlannerInputDto input, PlanItemDto item) {
        Product cheaper = cheapestInCategory(item.product().category(), input, Set.of(item.product().id()), item.product().price().doubleValue(), false);
        if (cheaper == null) return null;
        double diff = item.product().price().doubleValue() - cheaper.getPrice().doubleValue();
        if (diff < 1) return null;
        return "Povoljnija " + categoryLabel(item.product().category()).toLowerCase(Locale.ROOT) + " spušta plan za " + money(diff) + ".";
    }

    private String buildStoreLimitNote(PlannerInputDto input, List<String> retailersUsed) {
        int cap = input.maxStores();
        if (cap <= 0) return null;
        int used = retailersUsed.size();
        if (used <= cap) return null;
        return "Za bolju cijenu plan koristi " + used + " " + storesWordAccusative(used) + ". Ako želiš manje obilazaka, preskoči stvari iz „Može kasnije”.";
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private PlanItemDto createPlanItem(Product product, PlannerInputDto input, String mode, String prefix) {
        String category = product.getCategory();
        return new PlanItemDto(
                ProductDto.from(product),
                prefix + buildReason(product, input, mode),
                priorityForCategory(input.roomType(), category),
                roleForCategory(input.roomType(), category),
                stepForCategory(input.roomType(), category),
                quantityFor(input, category)
        );
    }

    // Sprint 10.120: how many of this category the user asked for (e.g. 6 dining chairs). Defaults to 1;
    // clamped to a sane furniture maximum so a typo ("60 chairs") can't blow up a plan.
    private int quantityFor(PlannerInputDto input, String category) {
        java.util.Map<String, Integer> q = input.quantities();
        Integer n = q == null ? null : q.get(category);
        if (n != null && n >= 1) return Math.min(n, 12);
        // Sprint 10.157: a dining set means SEATS — default dining chairs to a 4-person set when the user gave no
        // count (a "table + 1 chair" plan looked broken to shoppers in the sweep). Everything else is a single unit.
        return "dining-chair".equals(category) ? 4 : 1;
    }

    private PlanItemDto enrichExistingItem(PlanItemDto item, PlannerInputDto input) {
        if (item.shoppingPriority() != null && item.shoppingRole() != null && item.stepTitle() != null) return item;
        String category = item.product().category();
        return new PlanItemDto(
                item.product(),
                item.reason(),
                item.shoppingPriority() == null ? priorityForCategory(input.roomType(), category) : item.shoppingPriority(),
                item.shoppingRole() == null ? roleForCategory(input.roomType(), category) : item.shoppingRole(),
                item.stepTitle() == null ? stepForCategory(input.roomType(), category) : item.stepTitle(),
                Math.max(1, item.quantity())
        );
    }

    private List<PlanItemDto> cleanPlanItems(PlannerInputDto input, List<PlanItemDto> items) {
        Set<String> lockedIds = new LinkedHashSet<>(input.lockedProductIds());
        Set<String> alreadyHave = new LinkedHashSet<>(input.alreadyHaveCategories());
        LinkedHashMap<String, PlanItemDto> byCategory = new LinkedHashMap<>();

        for (PlanItemDto rawItem : orderItemsForShopping(input, items)) {
            PlanItemDto item = enrichExistingItem(rawItem, input);
            String category = item.product().category();
            boolean locked = lockedIds.contains(item.product().id());

            if (alreadyHave.contains(category) && !locked) {
                continue;
            }

            PlanItemDto existing = byCategory.get(category);
            if (existing == null) {
                byCategory.put(category, item);
                continue;
            }

            boolean existingLocked = lockedIds.contains(existing.product().id());
            if (!existingLocked && locked) {
                byCategory.put(category, item);
            }
        }

        return orderItemsForShopping(input, new ArrayList<>(byCategory.values()));
    }

    private List<PlanItemDto> orderItemsForShopping(PlannerInputDto input, List<PlanItemDto> items) {
        return items.stream()
                .map(item -> enrichExistingItem(item, input))
                .sorted(Comparator
                        .comparingInt((PlanItemDto item) -> categoryOrder(input.roomType(), item.product().category()))
                        .thenComparing(item -> item.product().price(), Comparator.reverseOrder()))
                .toList();
    }

    private String buildReason(Product product, PlannerInputDto input, String mode) {
        // Speak like a designer: one or two concrete reasons, no algorithm talk.
        String lead = switch (priorityForCategory(input.roomType(), product.getCategory())) {
            case "buy-first" -> "Temelj prostora — oko njega slažeš ostalo, zato ide prvi.";
            case "add-comfort" -> "Daje toplinu i čini da soba djeluje dovršeno, a ne prazno.";
            default -> "Završni detalj koji uskače kad ostane budžeta.";
        };
        String fit = styleMatches(product, input.style())
                ? " Po izgledu pogađa ono što si htio."
                : " Miran, neutralan komad koji pristaje uz gotovo sve.";
        String value = mode.equals("budget") || input.optimizationGoal().equals("lowest-price")
                ? " Uz to drži cijenu razumnom."
                : "";
        String note = product.getNote() == null || product.getNote().isBlank() ? "" : " " + product.getNote().trim();
        return lead + fit + value + note;
    }

    private String buildSummary(String mode, PlannerInputDto input, List<PlanItemDto> items, double total, List<String> retailersUsed) {
        String categories = items.stream()
                .map(item -> categoryLabel(item.product().category()))
                .distinct()
                .limit(6)
                .collect(Collectors.joining(", "));
        String budgetText = total <= input.budget()
                ? "ostaje unutar budžeta"
                : "prelazi budžet za " + money(total - input.budget());
        String stores = retailersUsed.size() <= 1 ? "iz jedne trgovine" : "iz " + retailersUsed.size() + " " + storesWord(retailersUsed.size());
        return switch (mode) {
            case "stretch" -> "Za dovršen dojam odmah, bez kompromisa na glavnim komadima. Pokriva " + categories + " — " + stores + ". " + capitalize(budgetText) + ".";
            case "value" -> "Novac ide tamo gdje se najviše osjeti. Pokriva " + categories + " — " + stores + ". " + capitalize(budgetText) + ".";
            default -> "Sigurna baza koja drži budžet. Pokriva " + categories + ". " + capitalize(budgetText) + ", a sitnice ostavlja za kasnije.";
        };
    }

    private String buildGoodFor(String mode, PlannerInputDto input) {
        return switch (mode) {
            case "stretch" -> "Kad želiš da prostor odmah izgleda gotovo i spreman si uložiti malo više.";
            case "value" -> "Za većinu ljudi — prvo glavni komadi, udobnost dolazi kad budžet dopusti.";
            default -> "Za useljenje ili tanji budžet: prvo osnovno, nadogradnja kasnije.";
        };
    }

    private String buildTradeoff(String mode, PlannerInputDto input, double total, List<String> retailersUsed) {
        String storeWarning = retailersUsed.size() > 2 ? " Ima više trgovina, pa će kupnja tražiti malo više organizacije." : "";
        String levelWarning = "complete".equals(input.furnishingLevel()) && total <= input.budget()
                ? " Ako želiš baš kompletan izgled, možda će još trebati sitnice koje trenutno nisu u ponudi."
                : "";
        return switch (mode) {
            case "stretch" -> "Može prijeći budžet jer daje prednost boljem izgledu i potpunijem prostoru." + storeWarning + levelWarning;
            case "value" -> "Neki proizvodi neće biti najjeftiniji, ali su odabrani jer bolje nose cijeli prostor." + storeWarning + levelWarning;
            default -> "Može izgledati jednostavnije jer izbacuje skuplje detalje i dekoracije da bi ostao povoljan." + storeWarning;
        };
    }

    private String buildBudgetStatus(PlannerInputDto input, double total) {
        double difference = total - input.budget();
        if (difference <= -input.budget() * 0.12) {
            return "Dobro stane u budžet — ostaje dovoljno mjesta za dostavu, sitnice ili malu nadogradnju.";
        }
        if (difference <= 0) {
            return "Stane u budžet — plan je siguran, ali nema puno prostora za veće promjene.";
        }
        if (difference <= input.budget() * 0.08) {
            return "Malo prelazi budžet za " + money(difference) + " — blizu je, ali jedna stvar može pričekati.";
        }
        return "Prelazi budžet za " + money(difference) + " — prvo bih maknuo detalje ili potražio povoljniju opciju za skuplji komad.";
    }

    private String buildAdvisorNote(String mode, PlannerInputDto input, List<PlanItemDto> items, double total, List<String> retailersUsed) {
        String firstItems = items.stream()
                .filter(item -> "buy-first".equals(priorityForCategory(input.roomType(), item.product().category())))
                .map(item -> categoryLabel(item.product().category()).toLowerCase(Locale.ROOT))
                .distinct()
                .limit(3)
                .collect(Collectors.joining(", "));
        String storeText = storeAdvice(input, items, retailersUsed);
        String budgetText = total <= input.budget()
                ? "Ostavio bih malo novca sa strane za dostavu i sitnice."
                : "Da čuvamo novac, jedan komad iz zadnjeg koraka može ostati za kasnije.";
        String alreadyHaveText = input.alreadyHaveCategories().isEmpty()
                ? ""
                : " Ono što već imaš nisam ponovno ubacio, pa budžet ide na ono što stvarno fali.";
        if (firstItems.isBlank()) firstItems = "glavni komadi";
        return "U ovom prostoru najviše smisla imaju " + firstItems + ". " + budgetText + " " + storeText + alreadyHaveText;
    }

    private String buildNextStep(PlannerInputDto input, List<PlanItemDto> items, double total) {
        Optional<PlanItemDto> first = items.stream()
                .filter(item -> "buy-first".equals(priorityForCategory(input.roomType(), item.product().category())))
                .findFirst();
        if (total > input.budget()) {
            return "Najlakše je probati povoljniju opciju na skupljim stvarima ili ostaviti detalje za kasnije.";
        }
        return first
                .map(item -> "Za početak provjeri dimenzije i dostupnost za: " + item.product().name() + ".")
                .orElse("Prije sitnica vrijedi provjeriti dimenzije prostora.");
    }

    private List<String> buildSavingTips(PlannerInputDto input, List<PlanItemDto> items, double total) {
        List<String> tips = new ArrayList<>();
        Optional<PlanItemDto> laterItem = items.stream()
                .filter(item -> "later".equals(priorityForCategory(input.roomType(), item.product().category())))
                .max(Comparator.comparing(item -> item.product().price()));
        Optional<PlanItemDto> comfortItem = items.stream()
                .filter(item -> "add-comfort".equals(priorityForCategory(input.roomType(), item.product().category())))
                .max(Comparator.comparing(item -> item.product().price()));
        Optional<PlanItemDto> expensiveItem = items.stream()
                .max(Comparator.comparing(item -> item.product().price()));

        laterItem.ifPresent(item -> tips.add("Najlakše je odgoditi " + categoryLabel(item.product().category()).toLowerCase(Locale.ROOT) + " i odmah uštedjeti oko " + money(item.product().price().doubleValue()) + "."));
        comfortItem.ifPresent(item -> tips.add("Ako želiš sigurnije ostati u budžetu, " + categoryLabel(item.product().category()).toLowerCase(Locale.ROOT) + " može pričekati nakon glavnih komada."));
        expensiveItem.ifPresent(item -> tips.add("Najveća ušteda je na komadu “" + item.product().name() + "” — za njega prvo probaj ‘Nađi jeftinije’."));

        if (tips.isEmpty()) tips.add("Plan je već sveden na osnovne stvari, pa bih prvo tražio jeftiniju varijantu glavnog komada.");
        if (total <= input.budget()) tips.add("Ne moraš potrošiti cijeli budžet: ostatak čuvaj za dostavu, montažu ili sitnice koje vidiš tek nakon kupnje.");
        return tips.stream().limit(3).toList();
    }

    private List<String> buildUpgradeTips(PlannerInputDto input, List<PlanItemDto> items, double total) {
        List<String> tips = new ArrayList<>();
        Set<String> pickedCategories = items.stream()
                .map(item -> item.product().category())
                .collect(Collectors.toSet());
        List<String> desired = desiredCategories(input);
        Set<String> pickedIds = items.stream().map(item -> item.product().id()).collect(Collectors.toSet());
        Set<String> retailers = items.stream().map(item -> item.product().retailer()).collect(Collectors.toCollection(LinkedHashSet::new));

        for (String category : desired) {
            if (pickedCategories.contains(category) || input.alreadyHaveCategories().contains(category)) continue;
            Product option = pickBest(category, input, Math.max(input.budget() * 0.35, 280), "value", pickedIds, retailers, Set.of(), 0, false);
            if (option != null) {
                tips.add("Za oko " + money(option.getPrice().doubleValue()) + " možeš dodati " + categoryLabel(category).toLowerCase(Locale.ROOT) + " i prostor će izgledati potpunije.");
            }
            if (tips.size() >= 2) break;
        }

        Optional<PlanItemDto> later = items.stream()
                .filter(item -> "later".equals(priorityForCategory(input.roomType(), item.product().category())))
                .findFirst();
        if (later.isPresent()) {
            tips.add("Ako želiš ljepši dojam, detalje dodaj tek nakon što su veliki komadi provjereni i naručeni.");
        } else {
            Optional<PlanItemDto> comfort = items.stream()
                    .filter(item -> "add-comfort".equals(priorityForCategory(input.roomType(), item.product().category())))
                    .findFirst();
            comfort.ifPresent(item -> tips.add("Najbolja nadogradnja je obično " + categoryLabel(item.product().category()).toLowerCase(Locale.ROOT) + " jer brzo mijenja osjećaj prostora."));
        }

        if (tips.isEmpty()) tips.add("Za bolji dojam prvo bih nadogradio rasvjetu ili tepih, jer se najviše vide u svakodnevnom korištenju.");
        return tips.stream().limit(3).toList();
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "sofa" -> "kauč";
            case "chair" -> "stolice";
            case "table" -> "stolić";
            case "tv-unit" -> "TV komoda";
            case "storage" -> "spremanje";
            case "rug" -> "tepih";
            case "lighting" -> "rasvjeta";
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
            default -> category;
        };
    }

    private String describePlan(String mode, PlannerInputDto input, double total, Set<String> retailersUsed) {
        String storeText = retailersUsed.size() == 1
                ? "Sve je iz " + retailersUsed.iterator().next() + "."
                : "Kupuješ iz više trgovina (" + String.join(", ", retailersUsed) + ").";
        String level = furnishingLevelText(input.furnishingLevel());

        return switch (mode) {
            case "stretch" -> "Ljepši, kompletniji komadi za razinu " + level + ". " + storeText;
            case "value" -> "Prvo glavni komadi, pa udobnost, a detalji ako preostane budžeta. " + storeText;
            default -> "Prvo čuva budžet, pa slaže kupnju po važnosti. " + storeText;
        };
    }

    // Sprint 10.114: the room each furniture category primarily belongs to — so a focused request for e.g. a
    // mattress or wardrobe plans in the bedroom (and isn't filtered out as a non-living-room category).
    // Room-agnostic categories (rug/lighting/storage/decor/gym-equipment) are intentionally omitted.
    private static final Map<String, String> CATEGORY_HOME_ROOM = Map.ofEntries(
            Map.entry("sofa", "living-room"), Map.entry("tv-unit", "living-room"),
            Map.entry("bed", "bedroom"), Map.entry("mattress", "bedroom"), Map.entry("nightstand", "bedroom"),
            Map.entry("wardrobe", "bedroom"), Map.entry("dresser", "bedroom"),
            Map.entry("desk", "home-office"),
            // 'chair' and 'table' are intentionally omitted — ambiguous (armchair vs office chair; coffee vs
            // dining table) — so a focused request for them stays in the room the user/context implies.
            Map.entry("dining-table", "dining-room"), Map.entry("dining-chair", "dining-room"),
            Map.entry("kitchen-cart", "kitchen"), Map.entry("kitchen-storage", "kitchen")
    );

    // Switch the room to the one the first requested item belongs to (mattress/wardrobe → bedroom, etc.) so the
    // item is valid for the room and the catalog is scoped correctly.
    private PlannerInputDto withInferredRoom(PlannerInputDto input) {
        for (String category : input.mustHaveCategories()) {
            String home = CATEGORY_HOME_ROOM.get(category == null ? "" : category.toLowerCase(Locale.ROOT));
            if (home != null && !home.equals(input.roomType())) {
                return input.withRoomType(home);
            }
        }
        return input;
    }

    // In focused mode the plan contains ONLY what the user asked for (minus anything they already have).
    // Sprint 10.181 — bath-shower subtype (fixture facet). bath-shower / bathtub / shower are ONE fixture family
    // (a bathroom's wet fixture); the plan keeps a single "bath-shower" slot, and an explicit shower/bathtub
    // request (or exclusion) filters that slot by the product's derived facet. This keeps the taxonomy backwards
    // compatible: stored products, saved plans and API consumers all keep using "bath-shower".
    private enum Facet { BATHTUB, SHOWER }

    private Facet requestedFixtureFacet(PlannerInputDto input) {
        boolean shower = input.mustHaveCategories().contains("shower");
        boolean bathtub = input.mustHaveCategories().contains("bathtub");
        if (shower && !bathtub) return Facet.SHOWER;
        if (bathtub && !shower) return Facet.BATHTUB;
        return null; // none, or both -> a generic fixture slot (any bathtub or shower is fine)
    }

    private Facet excludedFixtureFacet(PlannerInputDto input) {
        boolean noBathtub = input.alreadyHaveCategories().contains("bathtub");
        boolean noShower = input.alreadyHaveCategories().contains("shower");
        if (noBathtub && !noShower) return Facet.BATHTUB;
        if (noShower && !noBathtub) return Facet.SHOWER;
        return null;
    }

    private boolean productHasFacet(Product product, Facet facet) {
        return facet == Facet.BATHTUB
                ? ProductTaxonomy.isBathtubFixture(product.getCategory(), product.getName())
                : ProductTaxonomy.isShowerFixture(product.getCategory(), product.getName());
    }

    /** Does this product fill the given category SLOT for this request? Fixture slots match the whole fixture
     *  family and honor an explicit shower/bathtub request + exclusion; everything else is an exact category match. */
    private boolean categoryMatchesSlot(Product product, String slotCategory, PlannerInputDto input) {
        String pc = product.getCategory() == null ? "" : product.getCategory();
        if (ProductTaxonomy.isFixtureCategory(slotCategory)) {
            if (!ProductTaxonomy.isFixtureCategory(pc)) return false;
            Facet requested = requestedFixtureFacet(input);
            if (requested != null && !productHasFacet(product, requested)) return false;
            Facet excluded = excludedFixtureFacet(input);
            if (excluded != null && productHasFacet(product, excluded)) return false;
            return true;
        }
        return pc.equalsIgnoreCase(slotCategory);
    }

    /** The facet to preserve for Similar Items / Replace: a bathtub anchor stays a bathtub, a shower stays a shower;
     *  a genuine combined shower-bath (ambiguous) is not over-constrained. */
    private Facet anchorFacet(String category, String name) {
        if (!ProductTaxonomy.isFixtureCategory(category)) return null;
        boolean bath = ProductTaxonomy.isBathtubFixture(category, name);
        boolean shower = ProductTaxonomy.isShowerFixture(category, name);
        if (bath && !shower) return Facet.BATHTUB;
        if (shower && !bath) return Facet.SHOWER;
        return null;
    }

    /** Same category as the anchor, but for a fixture anchor it stays within the same fixture family AND facet
     *  (so Similar Items / Replace for a bathtub never return a shower). */
    private boolean sameFixtureFamilyAndFacet(Product product, String anchorCategory, String anchorName) {
        if (!ProductTaxonomy.isFixtureCategory(anchorCategory)) {
            return product.getCategory() != null && product.getCategory().equalsIgnoreCase(anchorCategory);
        }
        if (!ProductTaxonomy.isFixtureCategory(product.getCategory())) return false;
        Facet facet = anchorFacet(anchorCategory, anchorName);
        return facet == null || productHasFacet(product, facet);
    }

    /** Collapse an explicit shower/bathtub category into the single "bath-shower" slot (the shower/bathtub
     *  distinction is applied as a facet filter, not as a separate slot). Preserves order + de-dupes. */
    private List<String> collapseFixtureSlots(List<String> categories, PlannerInputDto input) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        boolean excludesWholeFixture = input.alreadyHaveCategories().contains("bath-shower")
                || (input.alreadyHaveCategories().contains("bathtub") && input.alreadyHaveCategories().contains("shower"));
        for (String c : categories) {
            String slot = ProductTaxonomy.isFixtureCategory(c) ? "bath-shower" : c;
            if ("bath-shower".equals(slot) && excludesWholeFixture) continue; // user already has both -> no fixture slot
            out.add(slot);
        }
        return new ArrayList<>(out);
    }

    private List<String> focusedCategories(PlannerInputDto input) {
        LinkedHashSet<String> categories = new LinkedHashSet<>(input.mustHaveCategories());
        input.alreadyHaveCategories().forEach(categories::remove);
        List<String> collapsed = collapseFixtureSlots(new ArrayList<>(categories), input);
        return collapsed.stream()
                .sorted(Comparator.comparingInt(category -> categoryOrder(input.roomType(), category)))
                .toList();
    }

    private List<String> desiredCategories(PlannerInputDto input) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        List<String> roomFlow = CATEGORY_FLOW_BY_ROOM.getOrDefault(input.roomType(), CATEGORY_FLOW_BY_ROOM.get("living-room"));
        Set<String> core = CORE_CATEGORIES_BY_ROOM.getOrDefault(input.roomType(), CORE_CATEGORIES_BY_ROOM.get("living-room"));
        String level = input.furnishingLevel() == null ? "comfort" : input.furnishingLevel();

        Set<String> comfort = COMFORT_CATEGORIES_BY_ROOM.getOrDefault(input.roomType(), Set.of());
        for (String category : roomFlow) {
            boolean isCore = core.contains(category);
            boolean isComfort = comfort.contains(category);
            boolean isLater = !isCore && !isComfort;
            boolean include = isCore
                    || (isComfort && (level.equals("comfort") || level.equals("complete")))
                    || (isLater && level.equals("complete"));
            if (include) categories.add(category);
        }

        categories.addAll(input.mustHaveCategories());
        categories.removeAll(input.alreadyHaveCategories());
        List<String> collapsed = collapseFixtureSlots(new ArrayList<>(categories), input);
        return collapsed.stream()
                .sorted(Comparator.comparingInt(category -> categoryOrder(input.roomType(), category)))
                .toList();
    }

    private int categoryOrder(String roomType, String category) {
        List<String> roomFlow = CATEGORY_FLOW_BY_ROOM.getOrDefault(roomType, CATEGORY_FLOW_BY_ROOM.get("living-room"));
        int index = roomFlow.indexOf(category);
        return index >= 0 ? index : 99;
    }

    private boolean isCoreCategory(String roomType, String category) {
        return CORE_CATEGORIES_BY_ROOM.getOrDefault(roomType, Set.of()).contains(category);
    }

    private boolean isComfortCategory(String roomType, String category) {
        return COMFORT_CATEGORIES_BY_ROOM.getOrDefault(roomType, Set.of()).contains(category);
    }

    private String priorityForCategory(String roomType, String category) {
        if (isCoreCategory(roomType, category)) return "buy-first";
        if (isComfortCategory(roomType, category)) return "add-comfort";
        return "later";
    }

    private String roleForCategory(String roomType, String category) {
        if (isCoreCategory(roomType, category)) return "Temelj prostora";
        if (isComfortCategory(roomType, category)) return "Za toplinu i udobnost";
        return "Završni detalj";
    }

    private String stepForCategory(String roomType, String category) {
        return switch (priorityForCategory(roomType, category)) {
            case "buy-first" -> "Kreni odavde";
            case "add-comfort" -> "Za udobnost";
            default -> "Može kasnije";
        };
    }

    private String furnishingLevelText(String level) {
        return switch (level == null ? "comfort" : level) {
            case "basic" -> "osnovno";
            case "complete" -> "kompletno";
            default -> "udobnije";
        };
    }

    private List<String> selectedRetailers(PlannerInputDto input) {
        List<String> base = input.selectedRetailers() == null || input.selectedRetailers().isEmpty()
                ? RETAILERS
                : input.selectedRetailers();
        if ("single".equals(input.retailerMode()) && !base.isEmpty()) {
            base = List.of(base.get(0));
        } else {
            // Sprint 10.137: market-aware default. The retailer chips default to a generic cross-market (HR-centric)
            // list; intersected with a non-HR market this often left only IKEA, so each market's LOCAL stores (ES
            // Kenay/Banak/Merkamueble, IT Conforama, FR Camif, the JYSK depth in FR/IT/PT, ...) never surfaced and
            // plans under-filled + looked single-retailer. If the requested set names a store that doesn't even sell
            // in this market, it's the generic default — not a deliberate in-market narrowing — so shop ALL of this
            // market's stores. A deliberate in-market pick (every requested store sells here) is honored as-is.
            Set<String> marketRetailers = marketCatalog(input).stream()
                    .map(Product::getRetailer)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            boolean genericDefault = base.stream().anyMatch(retailer -> !marketRetailers.contains(retailer));
            if (genericDefault && !marketRetailers.isEmpty()) {
                base = new ArrayList<>(marketRetailers);
            }
        }

        Set<String> excluded = input.excludedRetailers() == null
                ? Set.of()
                : input.excludedRetailers().stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());

        if (excluded.isEmpty()) return base;
        return base.stream()
                .filter(Objects::nonNull)
                .filter(retailer -> !excluded.contains(retailer.trim().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private boolean prefersFewStores(PlannerInputDto input) {
        return "single".equals(input.retailerMode()) || "least-stores".equals(input.optimizationGoal()) || input.maxStores() > 0;
    }

    private Optional<String> preferredRetailerForFewStores(PlannerInputDto input, List<String> categories, String mode) {
        List<String> allowedRetailers = selectedRetailers(input);
        return allowedRetailers.stream()
                .max(Comparator.comparingDouble(retailer -> retailerCoverageScore(retailer, categories, input, mode)));
    }

    private double retailerCoverageScore(String retailer, List<String> categories, PlannerInputDto input, String mode) {
        double total = 0;
        int covered = 0;
        int styleHits = 0;

        for (String category : categories) {
            Optional<Product> cheapest = marketCatalog(input).stream()
                    .filter(product -> product.getRetailer().equals(retailer))
                    .filter(product -> product.getCategory().equalsIgnoreCase(category))
                    .filter(product -> matchesRoom(product, input.roomType()))
                    .filter(ProductTaxonomy::canEnterPlanner)
                    .min(Comparator.comparing(Product::getPrice));
            if (cheapest.isPresent()) {
                Product product = cheapest.get();
                covered++;
                total += product.getPrice().doubleValue();
                if (styleMatches(product, input.style())) styleHits++;
            }
        }

        double budgetFit = total <= input.budget() * ("stretch".equals(mode) ? 1.12 : 1.0) ? 80 : -80;
        double preferredBonus = input.preferredRetailers() != null && input.preferredRetailers().contains(retailer) ? 600 : 0;
        return covered * 1000 + styleHits * 50 + budgetFit + preferredBonus - total / 10;
    }

    private String storeAdvice(PlannerInputDto input, List<PlanItemDto> items, List<String> retailersUsed) {
        if (retailersUsed.size() <= 1) {
            String retailer = retailersUsed.isEmpty() ? "jedne trgovine" : retailersUsed.get(0);
            return "Kupnja je jednostavna jer je sve iz " + retailer + ".";
        }

        if (prefersFewStores(input)) {
            String mainRetailer = items.stream()
                    .collect(Collectors.groupingBy(item -> item.product().retailer(), LinkedHashMap::new, Collectors.counting()))
                    .entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(retailersUsed.get(0));
            return "Najviše stvari su iz " + mainRetailer + ", a ostatak je dodan samo gdje bolje čuva budžet.";
        }

        return "Plan koristi " + retailersUsed.size() + " " + storesWordAccusative(retailersUsed.size()) + " da ne moraš tražiti svaku stvar posebno.";
    }

    private boolean styleMatches(Product product, String requestedStyle) {
        return productStyleMatches(splitCsv(product.getStyleTags()), requestedStyle);
    }

    private boolean productStyleMatches(List<String> productTags, String requestedStyle) {
        if (productTags == null || productTags.isEmpty()) return false;
        if ("surprise".equalsIgnoreCase(requestedStyle)) return true;
        Set<String> aliases = styleAliases(requestedStyle);
        return productTags.stream().anyMatch(tag -> aliases.contains(tag.toLowerCase(Locale.ROOT).trim()));
    }

    private Set<String> styleAliases(String requestedStyle) {
        return switch (requestedStyle == null ? "" : requestedStyle.toLowerCase(Locale.ROOT)) {
            case "bright", "scandinavian" -> Set.of("scandinavian", "minimal", "modern");
            case "warm", "cozy" -> Set.of("cozy", "scandinavian");
            case "modern" -> Set.of("modern", "minimal");
            case "minimal" -> Set.of("minimal", "scandinavian", "modern");
            case "classic" -> Set.of("classic", "cozy", "modern", "scandinavian");
            case "industrial" -> Set.of("industrial", "modern");
            case "boho" -> Set.of("boho", "cozy", "scandinavian");
            case "surprise" -> Set.of("scandinavian", "modern", "minimal", "cozy", "industrial", "classic", "boho");
            default -> Set.of(requestedStyle == null ? "" : requestedStyle.toLowerCase(Locale.ROOT));
        };
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
    }

    // Sprint 10.13 (#3): the catalog scoped to the request's market. A product with no market is
    // treated as global (matches any market) so HR + global products keep working.
    // Sprint 10.21+ (verified-only gate): the planner builds plans ONLY from production-eligible products
    // (CatalogSourcePolicy.isPlannerEligible) — never legacy data.sql sample rows, needs-review rows, or a
    // blocked retailer that wasn't fed. Stale rows still pass (they enter with a "check in store" note, so
    // an aging catalog never silently empties). A room with no eligible products yields an empty/partial
    // plan (honest) rather than placeholder data.
    // Sprint 10.39 (perf): the products table is effectively immutable after the startup seed — the only
    // runtime writer is the admin import, which is disabled in prod. marketCatalog() is called many times
    // per plan request (once per category/retailer pass), so caching findAll() for a short window collapses
    // those ~N full-table loads into a single query — the cost stops growing with the catalog (now 1200+
    // rows). The small TTL means a dev admin-import is still reflected within a couple of seconds.
    private static final long CATALOG_SNAPSHOT_TTL_MS = 2_000;
    // Sprint 10.167 (perf): cache the planner-eligible catalog BUCKETED BY MARKET, rebuilt at most once per TTL.
    // marketCatalog() is called ~10x per plan request and used to re-stream + re-filter the WHOLE products table
    // each time — an O(catalog) pass whose cost doubled when the catalog grew past 6000 rows and capped hot-path
    // throughput (a plan request only ever needs its own market's ~few-hundred rows). Bucketing makes each call an
    // O(1) map lookup, so per-request cost stops scaling with total catalog size. Eligibility + second-hand
    // filtering is applied ONCE at build time (was the single chokepoint in the old stream). Products with no
    // market are "global" (eligible in every market) and appended at lookup — matching the old equalsIgnoreCase
    // semantics. The benign rebuild race (two threads may both refresh) is harmless: same result, last wins.
    private volatile Map<String, List<Product>> catalogByMarket;
    private volatile List<Product> globalCatalog = List.of();
    private volatile long catalogSnapshotAt;

    private void refreshCatalogIfStale() {
        Map<String, List<Product>> current = catalogByMarket;
        if (current != null && System.currentTimeMillis() - catalogSnapshotAt < CATALOG_SNAPSHOT_TTL_MS) {
            return;
        }
        Map<String, List<Product>> buckets = new HashMap<>();
        List<Product> global = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            // Sprint 10.51: a second-hand marketplace listing is never a plan pick (different trust/freshness model);
            // it is surfaced only in the separate "Rabljeno" block and must never enter a plan or the budget total.
            if (!CatalogSourcePolicy.isPlannerEligible(product) || product.isSecondHand()) {
                continue;
            }
            String market = product.getMarket();
            if (market == null || market.isBlank()) {
                global.add(product);
            } else {
                buckets.computeIfAbsent(market.toUpperCase(Locale.ROOT), key -> new ArrayList<>()).add(product);
            }
        }
        this.catalogByMarket = buckets;
        this.globalCatalog = List.copyOf(global);
        this.catalogSnapshotAt = System.currentTimeMillis();
    }

    private List<Product> marketCatalog(PlannerInputDto input) {
        refreshCatalogIfStale();
        String market = Markets.normalize(input == null ? null : input.market());
        List<Product> forMarket = catalogByMarket.getOrDefault(market, List.of());
        List<Product> global = globalCatalog;
        if (global.isEmpty()) {
            return forMarket;
        }
        List<Product> combined = new ArrayList<>(forMarket.size() + global.size());
        combined.addAll(forMarket);
        combined.addAll(global);
        return combined;
    }

    private boolean hasTag(String csv, String tag) {
        if (csv == null || tag == null) return false;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(tag.trim()));
    }

    // Sprint 10.121: does this product belong to the requested room? For a studio that means EITHER the
    // living-room or bedroom catalog pool; every other room maps to itself.
    private boolean matchesRoom(Product product, String roomType) {
        for (String tag : ROOM_CATALOG_TAGS.getOrDefault(roomType, List.of(roomType))) {
            if (hasTag(product.getRoomTags(), tag)) return true;
        }
        return false;
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }

    private String normalizeChangeType(String changeType) {
        if (changeType == null || changeType.isBlank()) return "similar";
        String normalized = changeType.trim().toLowerCase(Locale.ROOT);
        if (Set.of("cheaper", "nicer", "different", "remove", "similar").contains(normalized)) return normalized;
        return "similar";
    }

    private String normalizeMode(String mode) {
        if ("value".equals(mode) || "budget".equals(mode) || "stretch".equals(mode)) return mode;
        if ("Best value plan".equalsIgnoreCase(mode)) return "value";
        if ("Stretch plan".equalsIgnoreCase(mode)) return "stretch";
        return "budget";
    }
}
