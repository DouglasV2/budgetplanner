package ai.budgetspace.planner;

import ai.budgetspace.dto.*;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PlannerService {
    private static final List<String> RETAILERS = List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon");

    private static final Map<String, List<String>> CATEGORY_FLOW_BY_ROOM = Map.of(
            "living-room", List.of("sofa", "tv-unit", "table", "rug", "lighting", "storage", "decor"),
            "home-office", List.of("desk", "chair", "storage", "lighting", "decor"),
            "bedroom", List.of("bed", "mattress", "storage", "lighting", "rug", "decor"),
            "home-gym", List.of("gym-equipment", "storage", "lighting", "decor")
    );

    private static final Map<String, Set<String>> CORE_CATEGORIES_BY_ROOM = Map.of(
            "living-room", Set.of("sofa", "tv-unit", "table"),
            "home-office", Set.of("desk", "chair"),
            "bedroom", Set.of("bed", "mattress"),
            "home-gym", Set.of("gym-equipment")
    );

    private static final Set<String> COMFORT_CATEGORIES = Set.of("rug", "lighting", "storage");
    private static final Set<String> DETAIL_CATEGORIES = Set.of("decor");

    private static final Map<String, String> ROOM_LABELS = Map.of(
            "living-room", "dnevni boravak",
            "home-office", "radni kutak",
            "bedroom", "spavaća soba",
            "home-gym", "kućna teretana"
    );

    private final ProductRepository productRepository;

    public PlannerService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public PlanGenerationResponse generate(PlannerInputDto rawInput) {
        PlannerInputDto input = normalizePrompt(rawInput == null ? null : rawInput.normalized());
        List<FurnishingPlanDto> plans = List.of(
                buildPlan(input, "budget"),
                buildPlan(input, "value"),
                buildPlan(input, "stretch")
        );
        return new PlanGenerationResponse(input, plans);
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

        double currentTotalWithoutItem = plan.items().stream()
                .filter(item -> !item.product().id().equals(request.productId()))
                .mapToDouble(item -> item.product().price().doubleValue())
                .sum();
        double remainingBudget = Math.max(0, input.budget() - currentTotalWithoutItem);
        String mode = normalizeMode(plan.id());

        Product alternative = pickBest(
                itemToReplace.product().category(),
                input,
                remainingBudget,
                mode,
                usedIds,
                currentRetailers
        );

        if (alternative == null) return plan;

        List<PlanItemDto> nextItems = plan.items().stream()
                .map(item -> item.product().id().equals(request.productId())
                        ? createPlanItem(alternative, input, mode, "Zamjena za " + item.product().name() + ": ")
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
            List<Product> lockedProducts = productRepository.findAll().stream()
                    .filter(product -> lockedIds.contains(product.getId()))
                    .filter(Product::isInStock)
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

        return calculatePlan(mode, name, label, describePlan(mode, input, total, currentRetailers), input, orderItemsForShopping(input, items));
    }

    private Product pickBest(String category, PlannerInputDto input, double remainingBudget, String mode, Set<String> picked, Set<String> currentRetailers) {
        List<String> allowedRetailers = selectedRetailers(input);
        boolean coreCategory = isCoreCategory(input.roomType(), category);
        double realisticLimit = coreCategory && !mode.equals("budget")
                ? Math.max(remainingBudget, input.budget() * 0.28)
                : remainingBudget;

        return productRepository.findAll().stream()
                .filter(product -> product.getCategory().equalsIgnoreCase(category))
                .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
                .filter(Product::isInStock)
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

        double leastStoresBonus = input.optimizationGoal().equals("least-stores")
                ? (currentRetailers.contains(product.getRetailer()) || currentRetailers.isEmpty() ? 22 : -14)
                : 0;
        double stylePriorityBonus = input.optimizationGoal().equals("style-match") && styleMatches(product, input.style()) ? 20 : 0;
        double singleStoreBonus = input.retailerMode().equals("single") ? 8 : 0;
        double coreBonus = isCoreCategory(input.roomType(), product.getCategory()) ? 12 : 0;

        return styleScore + roomScore + ratingScore + stockScore + discountScore + priceBias + leastStoresBonus + stylePriorityBonus + singleStoreBonus + coreBonus;
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
        double total = items.stream().mapToDouble(item -> item.product().price().doubleValue()).sum();
        List<String> retailersUsed = items.stream()
                .map(item -> item.product().retailer())
                .distinct()
                .toList();
        long styleMatches = items.stream().filter(item -> productStyleMatches(item.product().styleTags(), input.style())).count();
        int styleConsistency = items.isEmpty() ? 0 : Math.min(99, (int) Math.round(((double) styleMatches / items.size()) * 100 + 8));
        String shoppingEffort = retailersUsed.size() <= 1 ? "Low" : retailersUsed.size() <= 3 ? "Medium" : "High";
        int budgetFit = total <= input.budget() ? 8 : -6;
        int fitScore = Math.min(98, Math.max(48, (int) Math.round(62 + items.size() * 4 + styleConsistency / 6.0 + budgetFit)));

        return new FurnishingPlanDto(
                id,
                name,
                label,
                description,
                buildSummary(id, input, items, total, retailersUsed),
                buildGoodFor(id, input),
                buildTradeoff(id, input, total, retailersUsed),
                items,
                money(total),
                money(Math.max(0, input.budget() - total)),
                fitScore,
                shoppingEffort,
                styleConsistency,
                retailersUsed
        );
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
            case "buy-first" -> "Zato ga je pametno riješiti prije sitnica.";
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
        String levelText = furnishingLevelText(input.furnishingLevel());
        return switch (mode) {
            case "stretch" -> "Za " + room + " smo složili ljepšu verziju " + stores + ": " + categories + ". Plan " + budgetText + " i pokušava dovesti prostor bliže razini: " + levelText + ".";
            case "value" -> "Za " + room + " smo složili uravnotežen plan " + stores + ": " + categories + ". Plan " + budgetText + " i novac prvo usmjerava na stvari koje nose prostor.";
            default -> "Za " + room + " smo složili najpovoljniju razumnu bazu " + stores + ": " + categories + ". Plan " + budgetText + " i namjerno preskače manje važne detalje.";
        };
    }

    private String buildGoodFor(String mode, PlannerInputDto input) {
        return switch (mode) {
            case "stretch" -> "Dobro ako želiš da prostor odmah izgleda dovršenije i spreman si dodati malo novca za bolji dojam.";
            case "value" -> "Dobro za većinu ljudi: prvo pokriva glavne komade, zatim dodaje udobnost tek kad budžet to dopušta.";
            default -> "Dobro ako se useljavaš, imaš ograničen budžet ili želiš prvo kupiti osnovne stvari pa kasnije nadograditi.";
        };
    }

    private String buildTradeoff(String mode, PlannerInputDto input, double total, List<String> retailersUsed) {
        String storeWarning = retailersUsed.size() > 2 ? " Ima više trgovina, pa će kupnja tražiti malo više organizacije." : "";
        String levelWarning = "complete".equals(input.furnishingLevel()) && total <= input.budget()
                ? " Ako želiš baš kompletan izgled, možda će još trebati sitnice koje mock katalog trenutno nema."
                : "";
        return switch (mode) {
            case "stretch" -> "Može prijeći budžet jer daje prednost boljem izgledu i potpunijem prostoru." + storeWarning + levelWarning;
            case "value" -> "Neki proizvodi neće biti najjeftiniji, ali su odabrani jer bolje nose cijeli prostor." + storeWarning + levelWarning;
            default -> "Može izgledati jednostavnije jer izbacuje skuplje detalje i dekoracije da bi ostao povoljan." + storeWarning;
        };
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

        for (String category : roomFlow) {
            boolean include = core.contains(category)
                    || (COMFORT_CATEGORIES.contains(category) && (level.equals("comfort") || level.equals("complete")))
                    || (DETAIL_CATEGORIES.contains(category) && level.equals("complete"));
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

    private String priorityForCategory(String roomType, String category) {
        if (isCoreCategory(roomType, category)) return "buy-first";
        if (COMFORT_CATEGORIES.contains(category)) return "add-comfort";
        return "later";
    }

    private String roleForCategory(String roomType, String category) {
        if (isCoreCategory(roomType, category)) return "Ovo je glavni komad";
        if (COMFORT_CATEGORIES.contains(category)) return "Ovo zaokružuje prostor";
        return "Ovo je detalj za kasnije";
    }

    private String stepForCategory(String roomType, String category) {
        return switch (priorityForCategory(roomType, category)) {
            case "buy-first" -> "1. Kupi osnovne komade";
            case "add-comfort" -> "2. Dodaj udobnost";
            default -> "3. Dodaj detalje ako ostane budžeta";
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
        if (input.selectedRetailers() == null || input.selectedRetailers().isEmpty()) return RETAILERS;
        if (input.retailerMode().equals("single")) return List.of(input.selectedRetailers().get(0));
        return input.selectedRetailers();
    }

    private PlannerInputDto normalizePrompt(PlannerInputDto rawInput) {
        PlannerInputDto input = rawInput == null ? new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi", List.of("IKEA", "JYSK", "Pevex"), "best-value", "comfort", List.of(), List.of(), List.of()) : rawInput.normalized();
        String text = normalize(input.prompt());
        if (text.isBlank()) return input;

        Optional<Integer> budget = firstNumber(text, Pattern.compile("(\\d{3,5})\\s*(€|eur|eura|euro)"));
        if (budget.isPresent()) input = input.withBudget(clamp(budget.get(), 500, 5000));

        Optional<Integer> size = firstNumber(text, Pattern.compile("(\\d{1,2})\\s*(m2|m²|kvadrata)"));
        if (size.isPresent()) input = input.withSize(clamp(size.get(), 8, 45));

        if (matches(text, "dnevni|boravak|living")) input = input.withRoomType("living-room");
        if (matches(text, "ured|office|radni|posao")) input = input.withRoomType("home-office");
        if (matches(text, "spava|bedroom|krevet")) input = input.withRoomType("bedroom");
        if (matches(text, "teretan|gym|trening|fitness")) input = input.withRoomType("home-gym");

        if (matches(text, "ne znam|svejedno|predlozi|predloži")) input = input.withStyle("surprise");
        if (matches(text, "svijetl|prozrac|prozrač|skandi|scandi|nordic|skandinav")) input = input.withStyle("bright");
        if (matches(text, "toplo|ugodno|mekano|domac|domać|cozy")) input = input.withStyle("warm");
        if (matches(text, "modern|uredno")) input = input.withStyle("modern");
        if (matches(text, "minimal|jednostavn|cisto|čisto")) input = input.withStyle("minimal");
        if (matches(text, "classic|klasic|klasič")) input = input.withStyle("classic");
        if (matches(text, "industrial|industrij|tamno|crno|metal")) input = input.withStyle("industrial");
        if (matches(text, "boho|prirodn|biljk|ratan")) input = input.withStyle("boho");

        if (matches(text, "osnovno|samo osnov|minimalno oprem")) input = input.withFurnishingLevel("basic");
        if (matches(text, "udobnij|normalno|dovoljno komplet")) input = input.withFurnishingLevel("comfort");
        if (matches(text, "kompletno|sve oprem|dovrsen|dovršen|odmah gotovo")) input = input.withFurnishingLevel("complete");

        List<String> mentionedRetailers = RETAILERS.stream()
                .filter(retailer -> text.contains(normalize(retailer)))
                .toList();
        if (mentionedRetailers.size() == 1 && matches(text, "samo|iskljucivo|jedna trgovina|sve iz")) {
            input = input.withRetailers("single", mentionedRetailers);
        } else if (!mentionedRetailers.isEmpty()) {
            input = input.withRetailers("multi", mentionedRetailers);
        }

        if (matches(text, "najjeftin|sto jeftin|low cost|budget")) input = input.withOptimizationGoal("lowest-price");
        if (matches(text, "best value|omjer|balans|vrijednost")) input = input.withOptimizationGoal("best-value");
        if (matches(text, "jedna trgovina|manje trgovina|sto manje trgovina|jedan odlazak")) input = input.withOptimizationGoal("least-stores");
        if (matches(text, "najljep|estetsk|stil")) input = input.withOptimizationGoal("style-match");

        Map<String, Pattern> categoryKeywords = Map.ofEntries(
                Map.entry("sofa", Pattern.compile("kauc|kauč|sofa|trosjed|garnitura")),
                Map.entry("tv-unit", Pattern.compile("tv komod|tv element|komod")),
                Map.entry("table", Pattern.compile("stolic|stolić|klub stol|coffee table")),
                Map.entry("rug", Pattern.compile("tepih")),
                Map.entry("lighting", Pattern.compile("lampa|rasvjet|svjetl")),
                Map.entry("storage", Pattern.compile("polic|regal|ormar|storage")),
                Map.entry("decor", Pattern.compile("dekor|slika|jastuk|biljk")),
                Map.entry("desk", Pattern.compile("radni stol|desk")),
                Map.entry("chair", Pattern.compile("stolica|chair")),
                Map.entry("bed", Pattern.compile("krevet|bed")),
                Map.entry("mattress", Pattern.compile("madrac|mattress")),
                Map.entry("gym-equipment", Pattern.compile("bucic|bučic|bench|klupa|utezi|sprava"))
        );

        String existingSegment = segmentAfter(text, "imam|vec imam|već imam", 130);
        String extractedNeedSegment = segmentAfter(text, "trebam|fali|dodaj|zelim|želim", 170);
        String needSegment = extractedNeedSegment.isBlank() ? text : extractedNeedSegment;

        LinkedHashSet<String> alreadyHave = new LinkedHashSet<>(input.alreadyHaveCategories());
        LinkedHashSet<String> mustHave = new LinkedHashSet<>(input.mustHaveCategories());

        categoryKeywords.forEach((category, pattern) -> {
            if (pattern.matcher(existingSegment).find()) alreadyHave.add(category);
            if (pattern.matcher(needSegment).find()) mustHave.add(category);
        });
        mustHave.removeAll(alreadyHave);
        input = input.withCategories(new ArrayList<>(mustHave), new ArrayList<>(alreadyHave));

        return input;
    }

    private Optional<Integer> firstNumber(String text, Pattern pattern) {
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return Optional.empty();
    }

    private String segmentAfter(String text, String keywordRegex, int maxChars) {
        var matcher = Pattern.compile("(" + keywordRegex + ")(.{0," + maxChars + "})").matcher(text);
        return matcher.find() ? matcher.group(2) : "";
    }

    private boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized;
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
            case "classic" -> Set.of("cozy", "modern", "scandinavian");
            case "industrial" -> Set.of("industrial", "modern");
            case "boho" -> Set.of("cozy", "scandinavian");
            case "surprise" -> Set.of("scandinavian", "modern", "minimal", "cozy", "industrial");
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

    private boolean hasTag(String csv, String tag) {
        if (csv == null || tag == null) return false;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(tag.trim()));
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeMode(String mode) {
        if ("value".equals(mode) || "budget".equals(mode) || "stretch".equals(mode)) return mode;
        if ("Best value plan".equalsIgnoreCase(mode)) return "value";
        if ("Stretch plan".equalsIgnoreCase(mode)) return "stretch";
        return "budget";
    }
}
