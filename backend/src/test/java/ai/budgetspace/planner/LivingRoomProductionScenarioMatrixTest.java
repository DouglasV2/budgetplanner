package ai.budgetspace.planner;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LivingRoomProductionScenarioMatrixTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SOURCE_REFERENCE = "ikea-jysk-hr-living-room-production-pilot-10-3";
    private static final Set<String> REAL_RETAILER_HOSTS = Set.of("www.ikea.com", "ikea.com", "jysk.hr", "www.jysk.hr");

    @Test
    void productionSnapshotHasEnoughRealLivingRoomCoverage() throws Exception {
        List<Product> products = loadSnapshotProducts();
        List<Product> usable = products.stream()
                .filter(ProductTaxonomy::canEnterPlanner)
                .toList();

        assertThat(products).hasSizeGreaterThanOrEqualTo(70);
        assertThat(usable).hasSizeGreaterThanOrEqualTo(60);
        assertThat(usable).allSatisfy(product -> {
            assertThat(product.getRetailer()).isIn("IKEA", "JYSK");
            assertThat(ProductTaxonomy.normalizeCategory(product.getCategory())).contains(product.getCategory());
            assertThat(product.getRoomTags()).contains("living-room");
            assertThat(product.getStyleTags()).isNotBlank();
            assertThat(product.getSourceReference()).isEqualTo(SOURCE_REFERENCE);
            assertThat(product.getPrice()).isNotNull();
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(product.getProductUrl()).isNotBlank();
            assertThat(REAL_RETAILER_HOSTS).contains(URI.create(product.getProductUrl()).getHost());
        });

        Map<String, Long> categoryCounts = usable.stream()
                .collect(Collectors.groupingBy(Product::getCategory, LinkedHashMap::new, Collectors.counting()));
        assertThat(categoryCounts.getOrDefault("sofa", 0L)).isGreaterThanOrEqualTo(8);
        assertThat(categoryCounts.getOrDefault("tv-unit", 0L)).isGreaterThanOrEqualTo(8);
        assertThat(categoryCounts.getOrDefault("table", 0L)).isGreaterThanOrEqualTo(8);
        assertThat(categoryCounts.getOrDefault("rug", 0L)).isGreaterThanOrEqualTo(8);
        assertThat(categoryCounts.getOrDefault("lighting", 0L)).isGreaterThanOrEqualTo(8);
        assertThat(categoryCounts.getOrDefault("storage", 0L)).isGreaterThanOrEqualTo(6);
        assertThat(categoryCounts.getOrDefault("decor", 0L)).isGreaterThanOrEqualTo(4);
        assertThat(categoryCounts.getOrDefault("chair", 0L)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void realLivingRoomScenariosGenerateSafePlans() throws Exception {
        List<Product> products = new ArrayList<>(loadSnapshotProducts());
        products.addAll(blockedGuardrailProducts());

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(products);
        PlannerService service = new PlannerService(repository);

        JsonNode matrix = MAPPER.readTree(matrixPath().toFile());
        JsonNode scenarios = matrix.path("scenarios");
        assertThat(scenarios).hasSizeBetween(25, 40);

        boolean sawIkeaOnlyScenario = false;
        boolean sawJyskOnlyScenario = false;
        boolean sawMixedRetailerPlan = false;

        for (JsonNode scenario : scenarios) {
            PlannerInputDto input = inputFromScenario(scenario);
            PlanGenerationResponse response = service.generate(input);

            assertThat(response.plans())
                    .as(scenario.path("id").asText())
                    .hasSize(3);
            if (!scenario.path("expectPartial").asBoolean(false)) {
                assertThat(response.partialPlan()).as(scenario.path("id").asText()).isFalse();
                assertThat(response.missingImportantCategories()).as(scenario.path("id").asText()).isEmpty();
            }

            Set<String> expectedRetailers = stringSet(scenario.path("expectedRetailersSubset"));
            Set<String> forbiddenCategories = stringSet(scenario.path("forbidCategories"));
            Set<String> mustHaveCategories = stringSet(scenario.path("mustHaveCategories"));
            Set<String> lockedIds = stringSet(scenario.path("lockedProductIds"));

            for (FurnishingPlanDto plan : response.plans()) {
                assertThat(plan.items()).as(scenario.path("id").asText()).isNotEmpty();
                assertThat(plan.total()).as(scenario.path("id").asText()).isNotNull();
                assertThat(plan.total().signum()).as(scenario.path("id").asText()).isPositive();

                Set<String> retailersUsed = new LinkedHashSet<>(plan.retailersUsed());
                assertThat(expectedRetailers).as(scenario.path("id").asText()).containsAll(retailersUsed);
                if (expectedRetailers.size() == 1 || "single".equalsIgnoreCase(input.retailerMode())) {
                    assertThat(retailersUsed).as(scenario.path("id").asText()).hasSizeLessThanOrEqualTo(1);
                }
                if (retailersUsed.equals(Set.of("IKEA"))) sawIkeaOnlyScenario = true;
                if (retailersUsed.equals(Set.of("JYSK"))) sawJyskOnlyScenario = true;
                if (retailersUsed.contains("IKEA") && retailersUsed.contains("JYSK")) sawMixedRetailerPlan = true;

                Set<String> categories = plan.items().stream()
                        .map(item -> item.product().category())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                assertThat(categories).as(scenario.path("id").asText()).doesNotContainAnyElementsOf(forbiddenCategories);
                assertThat(categories).as(scenario.path("id").asText()).containsAll(mustHaveCategories);
                assertThat(plan.items().stream().map(item -> item.product().id()).toList())
                        .as(scenario.path("id").asText())
                        .containsAll(lockedIds);

                for (PlanItemDto item : plan.items()) {
                    assertSafePlanItem(scenario.path("id").asText(), item.product());
                }
            }
        }

        assertThat(sawIkeaOnlyScenario).isTrue();
        assertThat(sawJyskOnlyScenario).isTrue();
        assertThat(sawMixedRetailerPlan).isTrue();
    }

    @Test
    void guardrailProductsCannotEnterPlanner() {
        assertThat(blockedGuardrailProducts()).allSatisfy(product -> assertThat(ProductTaxonomy.canEnterPlanner(product)).isFalse());
    }

    private void assertSafePlanItem(String scenarioId, ProductDto product) {
        assertThat(product.id()).as(scenarioId).doesNotStartWith("guard-");
        assertThat(product.price()).as(scenarioId).isNotNull();
        assertThat(product.price().signum()).as(scenarioId).isPositive();
        assertThat(product.inStock()).as(scenarioId).isTrue();
        assertThat(product.availabilityStatus()).as(scenarioId).isNotEqualTo("unavailable");
        assertThat(product.productUrl()).as(scenarioId).isNotBlank();
        assertThat(REAL_RETAILER_HOSTS).as(scenarioId).contains(URI.create(product.productUrl()).getHost());
    }

    private PlannerInputDto inputFromScenario(JsonNode node) {
        return new PlannerInputDto(
                text(node, "prompt"),
                node.path("budget").asInt(),
                text(node, "roomType"),
                text(node, "style"),
                text(node, "location"),
                node.path("size").asInt(),
                text(node, "retailerMode"),
                stringList(node.path("selectedRetailers")),
                text(node, "optimizationGoal"),
                text(node, "furnishingLevel"),
                stringList(node.path("mustHaveCategories")),
                stringList(node.path("alreadyHaveCategories")),
                stringList(node.path("lockedProductIds")),
                stringList(node.path("preferredRetailers")),
                stringList(node.path("excludedRetailers")),
                node.path("maxStores").asInt()
        ).normalized();
    }

    private List<Product> loadSnapshotProducts() throws Exception {
        JsonNode root = MAPPER.readTree(snapshotPath().toFile());
        assertThat(root.isArray()).isTrue();
        List<Product> products = new ArrayList<>();
        for (JsonNode node : root) {
            products.add(productFromJson(node));
        }
        return products;
    }

    private Product productFromJson(JsonNode node) {
        Product product = new Product();
        String externalId = text(node, "externalId");
        String retailer = ProductTaxonomy.normalizeRetailer(text(node, "retailer")).orElse(text(node, "retailer"));
        String category = ProductTaxonomy.normalizeCategory(text(node, "category")).orElse(text(node, "category"));
        String availability = ProductTaxonomy.normalizeAvailability(text(node, "availabilityStatus"));

        product.setId(externalId);
        product.setExternalId(externalId);
        product.setName(text(node, "name"));
        product.setRetailer(retailer);
        product.setCategory(category);
        product.setPrice(node.path("price").isMissingNode() || node.path("price").isNull()
                ? null
                : BigDecimal.valueOf(node.path("price").asDouble()));
        product.setOriginalPrice(node.path("originalPrice").isMissingNode() || node.path("originalPrice").isNull()
                ? null
                : BigDecimal.valueOf(node.path("originalPrice").asDouble()));
        product.setProductUrl(text(node, "productUrl"));
        product.setUrl(text(node, "productUrl"));
        product.setImageUrl(text(node, "imageUrl"));
        product.setImage(text(node, "imageUrl"));
        product.setAvailabilityStatus(availability);
        product.setDeliveryNote(text(node, "deliveryNote"));
        product.setLastCheckedAt(text(node, "lastCheckedAt"));
        product.setRoomTags(normalizedTags(node.path("roomTags"), "room"));
        product.setStyleTags(normalizedTags(node.path("styleTags"), "style"));
        product.setPriceTier(text(node, "priceTier"));
        product.setSourceType(text(node, "sourceType"));
        product.setSourceName(text(node, "sourceName"));
        product.setSourceReference(text(node, "sourceReference"));
        product.setDataQuality(text(node, "dataQuality"));
        product.setDataQualityNotes(text(node, "dataQualityNotes"));
        product.setRating(4.4);
        product.setInStock(!"unavailable".equals(availability));
        product.setNote("Production pilot fixture from real retailer URL snapshot.");
        return product;
    }

    private List<Product> blockedGuardrailProducts() {
        Product noPrice = guardrailProduct("guard-no-price", "IKEA", "tv-unit", null, "in-stock", "partial", true);
        Product zeroPrice = guardrailProduct("guard-zero-price", "IKEA", "table", BigDecimal.ZERO, "in-stock", "partial", true);
        Product unavailable = guardrailProduct("guard-unavailable", "JYSK", "sofa", BigDecimal.ONE, "unavailable", "partial", false);
        Product needsReview = guardrailProduct("guard-needs-review", "JYSK", "rug", BigDecimal.ONE, "in-stock", "needs-review", true);
        return List.of(noPrice, zeroPrice, unavailable, needsReview);
    }

    private Product guardrailProduct(String id, String retailer, String category, BigDecimal price, String availability, String quality, boolean inStock) {
        Product product = new Product();
        product.setId(id);
        product.setExternalId(id);
        product.setName(id);
        product.setRetailer(retailer);
        product.setCategory(category);
        product.setPrice(price);
        product.setProductUrl(retailer.equals("IKEA")
                ? "https://www.ikea.com/hr/hr/p/lack-tv-klupa-bijela-00450088/"
                : "https://jysk.hr/dnevni-boravak/kauci/trosjed-andrup-ovalni-bez-tkanina");
        product.setUrl(product.getProductUrl());
        product.setImageUrl("");
        product.setImage("");
        product.setAvailabilityStatus(availability);
        product.setRoomTags("living-room");
        product.setStyleTags("modern");
        product.setSourceReference(SOURCE_REFERENCE);
        product.setDataQuality(quality);
        product.setRating(5.0);
        product.setInStock(inStock);
        product.setNote("Guardrail product that must not enter the planner.");
        return product;
    }

    private Path snapshotPath() {
        return existingPath(
                "docs/catalog-snapshots/real-ikea-jysk-hr-living-room-production-snapshot.json",
                "../docs/catalog-snapshots/real-ikea-jysk-hr-living-room-production-snapshot.json"
        );
    }

    private Path matrixPath() {
        return existingPath(
                "docs/production-pilot/living-room-scenario-matrix.json",
                "../docs/production-pilot/living-room-scenario-matrix.json"
        );
    }

    private Path existingPath(String rootRelative, String backendRelative) {
        Path path = Path.of(rootRelative);
        if (!Files.exists(path)) path = Path.of(backendRelative);
        assumeTrue(Files.exists(path), () -> "Missing fixture: " + rootRelative);
        return path;
    }

    private String normalizedTags(JsonNode array, String kind) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            String value = item.asText();
            if ("room".equals(kind)) {
                ProductTaxonomy.normalizeRoom(value).ifPresent(values::add);
            } else {
                ProductTaxonomy.normalizeStyle(value).ifPresent(values::add);
            }
        }
        return values.stream().distinct().collect(Collectors.joining(","));
    }

    private List<String> stringList(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.asText().isBlank()) values.add(item.asText());
        }
        return values;
    }

    private Set<String> stringSet(JsonNode array) {
        return new LinkedHashSet<>(stringList(array));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }
}
