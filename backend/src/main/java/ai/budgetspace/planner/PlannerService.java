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
    private static final Map<String, List<String>> ESSENTIALS_BY_ROOM = Map.of(
            "living-room", List.of("sofa", "tv-unit", "table", "rug", "lighting"),
            "home-office", List.of("desk", "chair", "storage", "lighting"),
            "bedroom", List.of("bed", "mattress", "rug", "lighting", "storage"),
            "home-gym", List.of("gym-equipment", "storage", "lighting")
    );
    private static final Map<String, String> ROOM_LABELS = Map.of(
            "living-room", "dnevni boravak",
            "home-office", "home office",
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
                        ? new PlanItemDto(ProductDto.from(alternative), "Zamjena za " + item.product().name() + ": " + buildReason(alternative, input, mode))
                        : item)
                .toList();

        return recalculate(plan, input, nextItems);
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
            List<Product> lockedProducts = productRepository.findAll().stream()
                    .filter(product -> lockedIds.contains(product.getId()))
                    .filter(Product::isInStock)
                    .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
                    .sorted(Comparator.comparingInt(product -> new ArrayList<>(lockedIds).indexOf(product.getId())))
                    .toList();

            for (Product lockedProduct : lockedProducts) {
                if (picked.contains(lockedProduct.getId())) continue;
                picked.add(lockedProduct.getId());
                currentRetailers.add(lockedProduct.getRetailer());
                total += lockedProduct.getPrice().doubleValue();
                categories.removeIf(category -> category.equalsIgnoreCase(lockedProduct.getCategory()));
                items.add(new PlanItemDto(ProductDto.from(lockedProduct), "Zaključano iz prethodnog plana — zadržavam ovaj proizvod i slažem ostatak oko njega."));
            }
        }

        for (String category : categories) {
            double remaining = planBudget - total;
            Product product = pickBest(category, input, remaining, mode, picked, currentRetailers);
            if (product == null) continue;

            picked.add(product.getId());
            currentRetailers.add(product.getRetailer());
            total += product.getPrice().doubleValue();
            items.add(new PlanItemDto(ProductDto.from(product), buildReason(product, input, mode)));
        }

        String label = switch (mode) {
            case "stretch" -> "Malo iznad budžeta, ali najkompletnije";
            case "value" -> "Najbolji omjer izgleda i cijene";
            default -> "Najsigurnija opcija za manji trošak";
        };
        String name = switch (mode) {
            case "stretch" -> "Kompletniji plan";
            case "value" -> "Preporučeni plan";
            default -> "Štedljivi plan";
        };

        return calculatePlan(mode, name, label, describePlan(mode, input, total, currentRetailers), input, items);
    }

    private Product pickBest(String category, PlannerInputDto input, double remainingBudget, String mode, Set<String> picked, Set<String> currentRetailers) {
        List<String> allowedRetailers = selectedRetailers(input);
        return productRepository.findAll().stream()
                .filter(product -> product.getCategory().equalsIgnoreCase(category))
                .filter(product -> hasTag(product.getRoomTags(), input.roomType()))
                .filter(Product::isInStock)
                .filter(product -> !picked.contains(product.getId()))
                .filter(product -> allowedRetailers.contains(product.getRetailer()))
                .filter(product -> product.getPrice().doubleValue() <= remainingBudget || mode.equals("stretch"))
                .max(Comparator.comparingDouble(product -> scoreProduct(product, input, mode, currentRetailers)))
                .orElse(null);
    }

    private double scoreProduct(Product product, PlannerInputDto input, String mode, Set<String> currentRetailers) {
        double styleScore = hasTag(product.getStyleTags(), input.style()) ? 38 : 12;
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
        double stylePriorityBonus = input.optimizationGoal().equals("style-match") && hasTag(product.getStyleTags(), input.style()) ? 20 : 0;
        double singleStoreBonus = input.retailerMode().equals("single") ? 8 : 0;

        return styleScore + roomScore + ratingScore + stockScore + discountScore + priceBias + leastStoresBonus + stylePriorityBonus + singleStoreBonus;
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
        long styleMatches = items.stream().filter(item -> item.product().styleTags().contains(input.style())).count();
        int styleConsistency = items.isEmpty() ? 0 : Math.min(99, (int) Math.round(((double) styleMatches / items.size()) * 100 + 8));
        String shoppingEffort = retailersUsed.size() <= 1 ? "Low" : retailersUsed.size() <= 3 ? "Medium" : "High";
        int budgetFit = total <= input.budget() ? 8 : -6;
        int fitScore = Math.min(98, Math.max(48, (int) Math.round(62 + items.size() * 4 + styleConsistency / 6.0 + budgetFit)));

        return new FurnishingPlanDto(
                id,
                name,
                label,
                description,
                items,
                money(total),
                money(Math.max(0, input.budget() - total)),
                fitScore,
                shoppingEffort,
                styleConsistency,
                retailersUsed
        );
    }

    private String buildReason(Product product, PlannerInputDto input, String mode) {
        String styleMatch = hasTag(product.getStyleTags(), input.style()) ? "paše uz odabrani stil" : "neutralno se uklapa u prostor";
        String priceNote = input.optimizationGoal().equals("lowest-price") || mode.equals("budget")
                ? "čuva budžet"
                : mode.equals("value")
                ? "ima dobar omjer cijene i kvalitete"
                : "diže finalni dojam prostora";
        return styleMatch + ", " + priceNote + ". " + product.getNote();
    }

    private String describePlan(String mode, PlannerInputDto input, double total, Set<String> retailersUsed) {
        String storeText = retailersUsed.size() == 1
                ? "sve iz " + retailersUsed.iterator().next()
                : "kombinacija: " + String.join(", ", retailersUsed);
        String room = ROOM_LABELS.getOrDefault(input.roomType(), input.roomType());

        return switch (mode) {
            case "stretch" -> "Malo ambicioznija kombinacija od " + money(total) + ", koristan ako želiš bolji finalni izgled i manje naknadnih zamjena. " + storeText + ".";
            case "value" -> "Najuravnoteženija kombinacija za " + input.size() + " m²: dobra baza, usklađen stil i pametno iskorišten budžet. " + storeText + ".";
            default -> "Složen da ostane ispod budžeta od " + money(input.budget()) + ", za " + room + " u " + input.location() + ". " + storeText + ".";
        };
    }

    private List<String> desiredCategories(PlannerInputDto input) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.addAll(ESSENTIALS_BY_ROOM.getOrDefault(input.roomType(), ESSENTIALS_BY_ROOM.get("living-room")));
        categories.addAll(input.mustHaveCategories());
        categories.removeAll(input.alreadyHaveCategories());
        return new ArrayList<>(categories);
    }

    private List<String> selectedRetailers(PlannerInputDto input) {
        if (input.selectedRetailers() == null || input.selectedRetailers().isEmpty()) return RETAILERS;
        if (input.retailerMode().equals("single")) return List.of(input.selectedRetailers().get(0));
        return input.selectedRetailers();
    }

    private PlannerInputDto normalizePrompt(PlannerInputDto rawInput) {
        PlannerInputDto input = rawInput == null ? new PlannerInputDto("", 1500, "living-room", "scandinavian", "Zagreb", 20, "multi", List.of("IKEA", "JYSK", "Pevex"), "best-value", List.of(), List.of(), List.of()) : rawInput.normalized();
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

        if (matches(text, "skandi|scandi|nordic|skandinav")) input = input.withStyle("scandinavian");
        if (matches(text, "modern")) input = input.withStyle("modern");
        if (matches(text, "minimal")) input = input.withStyle("minimal");
        if (matches(text, "cozy|toplo|ugodno|mekano")) input = input.withStyle("cozy");
        if (matches(text, "industrial|industrij")) input = input.withStyle("industrial");

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
