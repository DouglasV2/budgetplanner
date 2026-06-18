package ai.budgetspace.planner;

import ai.budgetspace.dto.*;
import ai.budgetspace.product.CatalogSourcePolicy;
import ai.budgetspace.product.Markets;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private static final List<String> RETAILERS = List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina",
            // Sprint 10.16: additional retailers that have verified products (HR/SI/DE).
            "Harvey Norman", "Namjestaj.hr", "Otto", "Segmüller", "Poco",
            // Sprint 10.36: France — Camif (verified products).
            "Camif");

    private static final Map<String, List<String>> CATEGORY_FLOW_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", List.of("sofa", "tv-unit", "table", "rug", "lighting", "storage", "decor")),
            Map.entry("home-office", List.of("desk", "chair", "lighting", "storage", "decor", "rug")),
            Map.entry("bedroom", List.of("bed", "mattress", "nightstand", "wardrobe", "dresser", "storage", "lighting", "rug", "decor")),
            Map.entry("home-gym", List.of("gym-equipment", "storage", "lighting", "decor", "rug")),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", List.of("kitchen-cart", "kitchen-storage", "lighting", "storage", "decor")),
            Map.entry("dining-room", List.of("dining-table", "dining-chair", "lighting", "rug", "storage", "decor")),
            Map.entry("hallway", List.of("storage", "lighting", "rug", "decor")),
            Map.entry("bathroom", List.of("storage", "lighting", "decor"))
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
            Map.entry("bathroom", Set.of("storage"))
    );

    // Za ugodniji prostor (add-comfort) po prostoru. Sve ostalo u flowu je "može kasnije".
    private static final Map<String, Set<String>> COMFORT_CATEGORIES_BY_ROOM = Map.ofEntries(
            Map.entry("living-room", Set.of("table", "rug", "lighting", "storage")),
            Map.entry("home-office", Set.of("lighting", "storage")),
            Map.entry("bedroom", Set.of("nightstand", "wardrobe", "dresser", "storage", "lighting", "rug")),
            Map.entry("home-gym", Set.of("storage", "lighting")),
            // Sprint 10.7: new rooms.
            Map.entry("kitchen", Set.of("kitchen-storage", "lighting", "storage")),
            Map.entry("dining-room", Set.of("lighting", "rug", "storage")),
            Map.entry("hallway", Set.of("lighting", "rug")),
            Map.entry("bathroom", Set.of("lighting"))
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
            Map.entry("bathroom", "kupaonica")
    );

    private final ProductRepository productRepository;
    private final PlannerIntentExtractor intentExtractor = new PlannerIntentExtractor();

    public PlannerService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public PlanGenerationResponse generate(PlannerInputDto rawInput) {
        // Rule-based path: parse the prompt with the deterministic extractor, then plan.
        return buildResponse(intentExtractor.enrich(rawInput == null ? null : rawInput.normalized()));
    }

    /**
     * Sprint 10.10: the input has already been resolved by the AI prompt-intelligence layer, so the
     * rule-based extractor is NOT re-run here — the resolved fields are authoritative. The planner
     * still picks only real products from the catalog.
     */
    public PlanGenerationResponse generateResolved(PlannerInputDto resolvedInput) {
        return buildResponse(resolvedInput == null ? intentExtractor.enrich(null) : resolvedInput.normalized());
    }

    private PlanGenerationResponse buildResponse(PlannerInputDto input) {
        List<FurnishingPlanDto> plans = List.of(
                buildPlan(input, "value"),
                buildPlan(input, "budget"),
                buildPlan(input, "stretch")
        );
        List<String> missingImportant = missingImportantCategories(input);
        boolean partial = !missingImportant.isEmpty();
        String catalogWarning = partial ? buildCatalogWarning(missingImportant) : null;
        logPlanSummary(input, plans, missingImportant);
        return new PlanGenerationResponse(input, plans, partial, missingImportant, catalogWarning);
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
                .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
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

    private FurnishingPlanDto buildPlan(PlannerInputDto input, String mode) {
        double multiplier = switch (mode) {
            case "stretch" -> 1.12;
            case "value" -> 0.98;
            default -> 0.82;
        };
        double planBudget = input.budget() * multiplier;
        List<String> categories = new ArrayList<>(desiredCategories(input));
        Set<String> picked = new LinkedHashSet<>();
        Set<String> currentRetailers = new LinkedHashSet<>();
        List<PlanItemDto> items = new ArrayList<>();
        double total = 0;

        Set<String> lockedIds = new LinkedHashSet<>(input.lockedProductIds());
        if (!lockedIds.isEmpty()) {
            List<String> lockedOrder = new ArrayList<>(lockedIds);
            List<Product> lockedProducts = marketCatalog(input).stream()
                    .filter(product -> lockedIds.contains(product.getId()))
                    .filter(ProductTaxonomy::canEnterPlanner)
                    .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
                    .sorted(Comparator.comparingInt(product -> lockedOrder.indexOf(product.getId())))
                    .toList();

            for (Product lockedProduct : lockedProducts) {
                if (picked.contains(lockedProduct.getId())) continue;
                picked.add(lockedProduct.getId());
                currentRetailers.add(lockedProduct.getRetailer());
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

        for (String category : categories) {
            double remaining = planBudget - total;
            Product product = pickBest(category, input, remaining, mode, picked, currentRetailers);
            if (product == null) continue;

            picked.add(product.getId());
            currentRetailers.add(product.getRetailer());
            total += product.getPrice().doubleValue();
            items.add(createPlanItem(product, input, mode, ""));
        }

        String label = switch (mode) {
            case "stretch" -> "Ljepša verzija s jačim dojmom";
            case "value" -> "Najbolji omjer cijene i izgleda";
            default -> "Najpovoljnija razumna verzija";
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

    private Product pickBest(String category, PlannerInputDto input, double remainingBudget, String mode, Set<String> picked, Set<String> currentRetailers) {
        List<String> allowedRetailers = selectedRetailers(input);
        boolean coreCategory = isCoreCategory(input.roomType(), category);
        double realisticLimit = coreCategory && !mode.equals("budget")
                ? Math.max(remainingBudget, input.budget() * 0.28)
                : remainingBudget;

        return marketCatalog(input).stream()
                .filter(product -> product.getCategory().equalsIgnoreCase(category))
                .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .filter(product -> !picked.contains(product.getId()))
                .filter(product -> allowedRetailers.contains(product.getRetailer()))
                .filter(product -> product.getPrice().doubleValue() <= realisticLimit || mode.equals("stretch"))
                .max(Comparator.comparingDouble(product -> scoreProduct(product, input, mode, currentRetailers)))
                .orElse(null);
    }

    private double scoreProduct(Product product, PlannerInputDto input, String mode, Set<String> currentRetailers) {
        double styleScore = styleMatches(product, input.style()) ? 38 : 12;
        double roomScore = hasTag(product.getRoomTags(), input.roomType()) ? 36 : 0;
        double ratingScore = product.getRating() * 5;
        double stockScore = product.isInStock() ? 10 : -80;
        double discountScore = product.getOriginalPrice() != null ? 8 : 0;
        double price = product.getPrice().doubleValue();

        double priceBias;
        if (input.optimizationGoal().equals("lowest-price") || mode.equals("budget")) {
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

        return styleScore + roomScore + ratingScore + stockScore + discountScore + priceBias
                + leastStoresBonus + stylePriorityBonus + singleStoreBonus + coreBonus
                + preferredRetailerBonus + requestedBonus + storeCapBonus + dataQualityBonus
                + preferenceBonus;
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
                .filter(product -> product.getCategory().equalsIgnoreCase(current.category()))
                .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
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
        double base = scoreProduct(product, input, mode, currentRetailers);
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
        double total = cleanItems.stream().mapToDouble(item -> item.product().price().doubleValue()).sum();
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
                        money(entry.getValue().stream().mapToDouble(item -> item.product().price().doubleValue()).sum()),
                        entry.getValue().size()))
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
            base = "Plan je još prazan, dodaj barem jedan glavni komad.";
        } else if (storeCount == 1) {
            base = "Sve kupuješ u " + mainRetailer + ", pa je manje obilazaka.";
        } else {
            base = "Većinu kupuješ u " + mainRetailer + ", a plan koristi " + storeCount + " " + storesWord(storeCount) + ".";
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

    // Budget repair v1. Only runs while building a fresh plan (not on manual replace).
    // Order: keep the most important pieces, make optional pieces cheaper, then move the
    // least important optional pieces out of the main buy. Core and explicitly requested
    // categories are never dropped.
    private List<PlanItemDto> repairBudget(PlannerInputDto input, List<PlanItemDto> items, String mode) {
        List<PlanItemDto> working = new ArrayList<>(items);
        double budget = input.budget();
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
            Product cheaper = cheapestInCategory(item.product().category(), input, usedIds, item.product().price().doubleValue());
            if (cheaper != null) {
                working.set(pos, createPlanItem(cheaper, input, mode, "Povoljnija opcija da plan ostane bliže budžetu: "));
            }
        }

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

        return working;
    }

    private double sumPrice(List<PlanItemDto> items) {
        return items.stream().mapToDouble(item -> item.product().price().doubleValue()).sum();
    }

    private int indexOfId(List<PlanItemDto> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product().id().equals(id)) return i;
        }
        return -1;
    }

    private Product cheapestInCategory(String category, PlannerInputDto input, Set<String> excludeIds, double maxPriceExclusive) {
        List<String> allowed = selectedRetailers(input);
        return marketCatalog(input).stream()
                .filter(product -> product.getCategory().equalsIgnoreCase(category))
                .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
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
            summary.add("Ova kombinacija drži budžet i koristi " + storeCount + " " + storesWord(storeCount) + ".");
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
        Product cheaper = cheapestInCategory(item.product().category(), input, Set.of(item.product().id()), item.product().price().doubleValue());
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
        return "Za bolju cijenu plan koristi " + used + " " + storesWord(used) + ". Ako želiš manje obilazaka, preskoči stvari iz „Može kasnije”.";
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
                stepForCategory(input.roomType(), category)
        );
    }

    private PlanItemDto enrichExistingItem(PlanItemDto item, PlannerInputDto input) {
        if (item.shoppingPriority() != null && item.shoppingRole() != null && item.stepTitle() != null) return item;
        String category = item.product().category();
        return new PlanItemDto(
                item.product(),
                item.reason(),
                item.shoppingPriority() == null ? priorityForCategory(input.roomType(), category) : item.shoppingPriority(),
                item.shoppingRole() == null ? roleForCategory(input.roomType(), category) : item.shoppingRole(),
                item.stepTitle() == null ? stepForCategory(input.roomType(), category) : item.stepTitle()
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
        String styleMatch = styleMatches(product, input.style())
                ? "izgledom ide uz ono što si tražio"
                : "dovoljno je neutralan da se lako uklopi";
        String priceNote = input.optimizationGoal().equals("lowest-price") || mode.equals("budget")
                ? "ne jede previše budžeta"
                : mode.equals("value")
                ? "daje dobar omjer cijene, izgleda i korisnosti"
                : "podiže finalni dojam prostora";
        String categoryNote = roleForCategory(input.roomType(), product.getCategory()) + ".";
        String stepNote = switch (priorityForCategory(input.roomType(), product.getCategory())) {
            case "buy-first" -> "Zato ima smisla biti u prvom fokusu, prije sitnica.";
            case "add-comfort" -> "Dodaje udobnost i pomaže da prostor ne izgleda prazno.";
            default -> "Može i kasnije ako želiš prvo čuvati budžet.";
        };
        return categoryNote + " " + stepNote + " " + styleMatch + " i " + priceNote + ". " + product.getNote();
    }

    private String buildSummary(String mode, PlannerInputDto input, List<PlanItemDto> items, double total, List<String> retailersUsed) {
        String room = ROOM_LABELS.getOrDefault(input.roomType(), input.roomType());
        String categories = items.stream()
                .map(item -> categoryLabel(item.product().category()))
                .distinct()
                .limit(6)
                .collect(Collectors.joining(", "));
        String budgetText = total <= input.budget()
                ? "ostaje unutar budžeta"
                : "prelazi budžet za " + money(total - input.budget());
        String stores = retailersUsed.size() <= 1 ? "iz jedne trgovine" : "iz " + retailersUsed.size() + " trgovine";
        return switch (mode) {
            case "stretch" -> "Ljepša verzija ima smisla ako želiš dovršeniji dojam odmah. Uključuje " + categories + ", " + budgetText + " i koristi " + stores + ".";
            case "value" -> "Najisplativije je prvo riješiti " + categories + ". Plan " + budgetText + ", a novac ide na stvari koje najviše mijenjaju " + room + ".";
            default -> "Najpovoljnija razumna baza pokriva " + categories + ". Plan " + budgetText + " i namjerno preskače detalje koji mogu čekati.";
        };
    }

    private String buildGoodFor(String mode, PlannerInputDto input) {
        return switch (mode) {
            case "stretch" -> "Dobro ako želiš da prostor odmah izgleda dovršenije i spreman si dodati malo novca za bolji dojam.";
            case "value" -> "Dobro za većinu ljudi: prvo pokriva glavne komade, zatim dodaje udobnost tek kad budžet to dopušta.";
            default -> "Dobro ako se useljavaš, imaš ograničen budžet ili želiš prvo riješiti osnovne stvari pa kasnije nadograditi.";
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
        String room = ROOM_LABELS.getOrDefault(input.roomType(), input.roomType());
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
        if (firstItems.isBlank()) firstItems = "glavne komade";
        return "Za " + room + " najviše smisla imaju " + firstItems + ". " + budgetText + " " + storeText + alreadyHaveText;
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
            Product option = pickBest(category, input, Math.max(input.budget() * 0.35, 280), "value", pickedIds, retailers);
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
                ? "sve iz " + retailersUsed.iterator().next()
                : "kombinacija: " + String.join(", ", retailersUsed);
        String room = ROOM_LABELS.getOrDefault(input.roomType(), input.roomType());
        String level = furnishingLevelText(input.furnishingLevel());

        return switch (mode) {
            case "stretch" -> "Ova verzija bira malo ljepše i kompletnije komade za razinu: " + level + ". " + storeText + ".";
            case "value" -> "Ovo je najuravnoteženija verzija za " + input.size() + " m²: prvo glavni komadi, zatim udobnost, pa detalji ako stanu. " + storeText + ".";
            default -> "Ova verzija prvo čuva budžet za " + room + " u " + input.location() + ", pa kupnju slaže redom važnosti. " + storeText + ".";
        };
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
        return categories.stream()
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
        if (isCoreCategory(roomType, category)) return "Ovo je glavni komad";
        if (isComfortCategory(roomType, category)) return "Ovo zaokružuje prostor";
        return "Ovo je detalj za kasnije";
    }

    private String stepForCategory(String roomType, String category) {
        return switch (priorityForCategory(roomType, category)) {
            case "buy-first" -> "1. Najvažnije za početak";
            case "add-comfort" -> "2. Za ugodniji prostor";
            default -> "3. Može kasnije";
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
                    .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
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

        return "Plan koristi " + retailersUsed.size() + " trgovine da ne moraš tražiti svaku stvar posebno.";
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
    private List<Product> marketCatalog(PlannerInputDto input) {
        String market = Markets.normalize(input == null ? null : input.market());
        List<Product> all = productRepository.findAll();
        return all.stream()
                .filter(CatalogSourcePolicy::isPlannerEligible)
                .filter(product -> product.getMarket() == null || product.getMarket().isBlank()
                        || product.getMarket().equalsIgnoreCase(market))
                .toList();
    }

    private boolean hasTag(String csv, String tag) {
        if (csv == null || tag == null) return false;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(tag.trim()));
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
