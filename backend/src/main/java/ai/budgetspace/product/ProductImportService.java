package ai.budgetspace.product;

import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.ProductDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductImportService {
    private final ProductRepository productRepository;

    public ProductImportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ImportSummaryDto importProducts(List<ImportProductDto> imports) {
        List<String> errors = new ArrayList<>();
        List<ProductDto> importedProducts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        if (imports == null || imports.isEmpty()) {
            return new ImportSummaryDto(0, 0, 0, List.of("Nema proizvoda za import."), List.of());
        }

        for (int index = 0; index < imports.size(); index++) {
            ImportProductDto dto = imports.get(index);
            List<String> validationErrors = validate(dto, index + 1);
            if (!validationErrors.isEmpty()) {
                errors.addAll(validationErrors);
                skipped++;
                continue;
            }

            Product entity = productRepository.findByExternalId(dto.externalId()).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new Product();
                entity.setId(hasText(dto.id()) ? dto.id().trim() : UUID.randomUUID().toString());
                entity.setExternalId(dto.externalId().trim());
                entity.setRating(0);
                entity.setInStock(true);
            }

            apply(dto, entity);
            applyDefaults(entity);
            productRepository.save(entity);
            importedProducts.add(ProductDto.from(entity));

            if (isNew) created++; else updated++;
        }

        return new ImportSummaryDto(created, updated, skipped, errors, importedProducts);
    }

    public ImportSummaryDto importCsv(String csv) {
        if (!hasText(csv)) {
            return new ImportSummaryDto(0, 0, 0, List.of("CSV je prazan."), List.of());
        }

        String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length < 2) {
            return new ImportSummaryDto(0, 0, 0, List.of("CSV mora imati header i barem jedan proizvod."), List.of());
        }

        List<String> headers = parseCsvLine(lines[0]).stream().map(this::normalizeHeader).toList();
        List<ImportProductDto> products = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            if (!hasText(lines[i])) continue;
            List<String> values = parseCsvLine(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < values.size() ? values.get(j).trim() : "");
            }
            try {
                products.add(fromCsvRow(row));
            } catch (IllegalArgumentException exception) {
                errors.add("Red " + (i + 1) + ": " + exception.getMessage());
            }
        }

        ImportSummaryDto summary = importProducts(products);
        List<String> allErrors = new ArrayList<>(errors);
        allErrors.addAll(summary.errors());
        return new ImportSummaryDto(
                summary.created(),
                summary.updated(),
                summary.skipped() + errors.size(),
                allErrors,
                summary.products()
        );
    }

    public ImportSummaryDto importIkeaStarterCatalog() {
        return importProducts(List.of(
                product("ikea-klippan-2-seat-sofa", "IKEA", "KLIPPAN dvosjed", "sofa", "249.00", "modern,minimal,classic", "living-room", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=900&q=80", "Dobar osnovni kauč za manji dnevni boravak."),
                product("ikea-lack-coffee-table", "IKEA", "LACK stolić", "table", "29.99", "minimal,modern", "living-room", "budget", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1499933374294-4584851497cc?auto=format&fit=crop&w=900&q=80", "Povoljan stolić koji ne pojede budžet."),
                product("ikea-besta-tv-bench", "IKEA", "BESTÅ TV klupa", "tv-unit", "129.00", "modern,minimal", "living-room", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1617103996702-96ff29b1c467?auto=format&fit=crop&w=900&q=80", "Jednostavna TV komoda za uredan prostor."),
                product("ikea-hektar-floor-lamp", "IKEA", "HEKTAR podna lampa", "lighting", "69.99", "industrial,modern", "living-room,bedroom,home-office", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80", "Rasvjeta koja brzo mijenja dojam prostora."),
                product("ikea-kallax-shelf", "IKEA", "KALLAX regal", "storage", "59.99", "minimal,modern,classic", "living-room,home-office,bedroom", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80", "Praktična pohrana za stvari koje ne želiš gledati po sobi."),
                product("ikea-malm-bed-frame", "IKEA", "MALM okvir kreveta", "bed", "199.00", "minimal,modern,classic", "bedroom", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80", "Siguran osnovni komad za spavaću sobu."),
                product("ikea-poang-chair", "IKEA", "POÄNG fotelja", "chair", "89.99", "classic,cozy", "living-room,home-office,bedroom", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=900&q=80", "Udoban dodatak ako ostane prostora i budžeta."),
                product("ikea-linnmon-desk", "IKEA", "LINNMON radni stol", "desk", "49.99", "minimal,modern", "home-office", "budget", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80", "Povoljan stol za osnovni radni kutak.")
        ));
    }

    private ImportProductDto fromCsvRow(Map<String, String> row) {
        BigDecimal price = parsePrice(value(row, "price", "cijena"));
        BigDecimal originalPrice = parseOptionalPrice(value(row, "originalprice", "original_price", "stara cijena"));
        return new ImportProductDto(
                value(row, "id"),
                value(row, "externalid", "external_id", "external"),
                value(row, "name", "naziv"),
                value(row, "retailer", "trgovina"),
                value(row, "category", "kategorija"),
                price,
                originalPrice,
                splitTags(value(row, "styletags", "style_tags", "stilovi")),
                splitTags(value(row, "roomtags", "room_tags", "prostorije")),
                value(row, "imageurl", "image_url", "slika"),
                value(row, "producturl", "product_url", "link"),
                value(row, "availabilitystatus", "availability_status", "dostupnost"),
                value(row, "deliverynote", "delivery_note", "dostava"),
                value(row, "lastcheckedat", "last_checked_at", "provjereno"),
                value(row, "pricetier", "price_tier", "razina"),
                value(row, "note", "napomena")
        );
    }

    private void apply(ImportProductDto dto, Product entity) {
        entity.setExternalId(dto.externalId().trim());
        entity.setName(dto.name().trim());
        entity.setRetailer(dto.retailer().trim());
        entity.setCategory(mapCategory(dto.category()));
        entity.setPrice(dto.price());
        if (dto.originalPrice() != null) entity.setOriginalPrice(dto.originalPrice());
        if (dto.styleTags() != null) entity.setStyleTags(joinTags(dto.styleTags()));
        if (dto.roomTags() != null) entity.setRoomTags(joinTags(dto.roomTags()));
        if (hasText(dto.imageUrl())) {
            entity.setImageUrl(dto.imageUrl().trim());
            entity.setImage(dto.imageUrl().trim());
        }
        if (hasText(dto.productUrl())) {
            entity.setProductUrl(dto.productUrl().trim());
            entity.setUrl(dto.productUrl().trim());
        }
        if (hasText(dto.availabilityStatus())) {
            entity.setAvailabilityStatus(dto.availabilityStatus().trim());
            entity.setInStock(!"unavailable".equalsIgnoreCase(dto.availabilityStatus().trim()));
        }
        if (hasText(dto.deliveryNote())) entity.setDeliveryNote(dto.deliveryNote().trim());
        if (hasText(dto.lastCheckedAt())) {
            entity.setLastCheckedAt(dto.lastCheckedAt().trim());
        } else if (!hasText(entity.getLastCheckedAt())) {
            entity.setLastCheckedAt(LocalDate.now().toString());
        }
        if (hasText(dto.priceTier())) entity.setPriceTier(dto.priceTier().trim());
        if (hasText(dto.note())) entity.setNote(dto.note().trim());
    }

    private void applyDefaults(Product entity) {
        if (entity.getStyleTags() == null) entity.setStyleTags("");
        if (entity.getRoomTags() == null) entity.setRoomTags("");
        if (entity.getAvailabilityStatus() == null) entity.setAvailabilityStatus(entity.isInStock() ? "in-stock" : "unavailable");
        if (entity.getDeliveryNote() == null) entity.setDeliveryNote("Provjeri dostavu ili preuzimanje prije kupnje.");
        if (entity.getLastCheckedAt() == null) entity.setLastCheckedAt(LocalDate.now().toString());
        if (entity.getExternalId() == null) entity.setExternalId(entity.getId());
        if (entity.getPriceTier() == null) entity.setPriceTier(inferPriceTier(entity.getPrice()));
        if (entity.getImageUrl() == null) entity.setImageUrl("");
        if (entity.getProductUrl() == null) entity.setProductUrl("");
        if (entity.getImage() == null) entity.setImage(entity.getImageUrl());
        if (entity.getUrl() == null) entity.setUrl(entity.getProductUrl());
        if (entity.getNote() == null) entity.setNote("");
    }

    private List<String> validate(ImportProductDto dto, int rowNumber) {
        List<String> errors = new ArrayList<>();
        String prefix = "Proizvod " + rowNumber + ": ";
        if (dto == null) {
            errors.add(prefix + "prazan zapis.");
            return errors;
        }
        if (!hasText(dto.externalId())) errors.add(prefix + "nedostaje externalId.");
        if (!hasText(dto.name())) errors.add(prefix + "nedostaje naziv.");
        if (!hasText(dto.retailer())) errors.add(prefix + "nedostaje trgovina.");
        if (!hasText(dto.category())) errors.add(prefix + "nedostaje kategorija.");
        if (dto.price() == null || dto.price().compareTo(BigDecimal.ZERO) <= 0) errors.add(prefix + "cijena mora biti veća od 0.");
        return errors;
    }

    private String mapCategory(String category) {
        if (category == null) return null;
        String normalised = normalize(category);
        return switch (normalised) {
            case "kauc", "kauč", "sofa", "couch" -> "sofa";
            case "tv stand", "tv-stand", "tv unit", "tv-unit", "komoda", "tv komoda" -> "tv-unit";
            case "stolic", "stolic za kavu", "stolić", "table", "coffee table" -> "table";
            case "tepih", "rug", "carpet" -> "rug";
            case "rasvjeta", "lighting", "lamp", "lampa" -> "lighting";
            case "dekor", "dekoracije", "decor", "decoration" -> "decor";
            case "pohrana", "storage", "orman", "ormar", "ladice", "regal" -> "storage";
            case "krevet", "bed" -> "bed";
            case "madrac", "mattress" -> "mattress";
            case "stolica", "chair" -> "chair";
            case "radni stol", "pisaci stol", "pisaći stol", "desk" -> "desk";
            case "gym equipment", "gym-equipment", "oprema", "sprava" -> "gym-equipment";
            default -> normalised;
        };
    }

    private ImportProductDto product(String externalId, String retailer, String name, String category, String price, String styles, String rooms, String tier, String url, String image, String note) {
        return new ImportProductDto(
                null,
                externalId,
                name,
                retailer,
                category,
                new BigDecimal(price),
                null,
                splitTags(styles),
                splitTags(rooms),
                image,
                url,
                "in-stock",
                "Provjeri dostavu ili preuzimanje prije kupnje.",
                LocalDate.now().toString(),
                tier,
                note
        );
    }

    private BigDecimal parsePrice(String value) {
        if (!hasText(value)) return null;
        try {
            return new BigDecimal(value.replace("€", "").replace(",", ".").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("cijena nije ispravna: " + value);
        }
    }

    private BigDecimal parseOptionalPrice(String value) {
        return hasText(value) ? parsePrice(value) : null;
    }

    private String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return normalize(value).replace(" ", "").replace("-", "").replace("_", "");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private List<String> splitTags(String value) {
        if (!hasText(value)) return List.of();
        return Arrays.stream(value.split("[,;|]"))
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }

    private String joinTags(List<String> tags) {
        if (tags == null) return "";
        return tags.stream().filter(this::hasText).map(String::trim).distinct().collect(java.util.stream.Collectors.joining(","));
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String inferPriceTier(BigDecimal price) {
        if (price == null) return "standard";
        if (price.compareTo(BigDecimal.valueOf(120)) <= 0) return "budget";
        if (price.compareTo(BigDecimal.valueOf(450)) <= 0) return "standard";
        return "premium";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
