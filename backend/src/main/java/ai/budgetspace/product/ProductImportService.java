package ai.budgetspace.product;

import ai.budgetspace.dto.ImportErrorDto;
import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.ProductDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductImportService {
    private static final List<String> REQUIRED_HEADERS = List.of(
            "externalId",
            "name",
            "retailer",
            "category",
            "price",
            "styleTags",
            "roomTags"
    );

    private final ProductRepository productRepository;

    public ProductImportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ImportSummaryDto importProducts(List<ImportProductDto> imports) {
        if (imports == null || imports.isEmpty()) {
            return summary(0, 0, 0, 0, List.of(), List.of(error(0, null, "Nema proizvoda za import.")));
        }

        List<ImportCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < imports.size(); index++) {
            candidates.add(new ImportCandidate(index + 1, imports.get(index)));
        }
        return importCandidates(candidates, List.of(), imports.size());
    }

    public ImportSummaryDto importCsv(String csv) {
        if (!hasText(csv)) {
            return summary(0, 0, 0, 0, List.of(), List.of(error(0, null, "CSV je prazan.")));
        }

        String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        Optional<Integer> headerLine = firstNonBlankLine(lines);
        if (headerLine.isEmpty()) {
            return summary(0, 0, 0, 0, List.of(), List.of(error(0, null, "CSV mora imati header i barem jedan proizvod.")));
        }

        List<String> headers = parseCsvLine(lines[headerLine.get()]).stream()
                .map(String::trim)
                .map(this::normalizeHeader)
                .toList();

        List<String> missingHeaders = missingRequiredHeaders(headers);
        if (!missingHeaders.isEmpty()) {
            String message = "CSV header nedostaje: " + String.join(", ", missingHeaders) + ".";
            return summary(0, 0, 0, 0, List.of(), List.of(error(headerLine.get() + 1, null, message)));
        }

        List<ImportCandidate> candidates = new ArrayList<>();
        List<ImportErrorDto> parseErrors = new ArrayList<>();
        int receivedRows = 0;

        for (int i = headerLine.get() + 1; i < lines.length; i++) {
            if (!hasText(lines[i])) continue;
            receivedRows++;
            List<String> values = parseCsvLine(lines[i]);
            Map<String, String> row = toCsvRow(headers, values);
            try {
                candidates.add(new ImportCandidate(i + 1, fromCsvRow(row)));
            } catch (IllegalArgumentException exception) {
                parseErrors.add(error(i + 1, value(row, "externalid", "external_id", "external"), exception.getMessage()));
            }
        }

        if (receivedRows == 0) {
            return summary(0, 0, 0, 0, List.of(), List.of(error(headerLine.get() + 1, null, "CSV nema nijedan proizvod nakon headera.")));
        }

        return importCandidates(candidates, parseErrors, receivedRows);
    }

    public ImportSummaryDto importIkeaStarterCatalog() {
        return importProducts(List.of(
                product("ikea-starter-sofa-light-textile", "IKEA", "Dvosjed svijetli tekstil", "sofa", "249.00", "modern,minimal", "living-room", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=900&q=80", "Dobar osnovni kauč za manji dnevni boravak."),
                product("ikea-starter-coffee-table-oak", "IKEA", "Stolić hrast efekt", "stolić", "39.99", "minimal,modern", "living-room", "budget", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1499933374294-4584851497cc?auto=format&fit=crop&w=900&q=80", "Povoljan stolić koji ne pojede budžet."),
                product("ikea-starter-tv-unit-white", "IKEA", "TV komoda bijela", "tv komoda", "129.00", "modern,minimal", "living-room", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1617103996702-96ff29b1c467?auto=format&fit=crop&w=900&q=80", "Jednostavna TV komoda za uredan prostor."),
                product("ikea-starter-floor-lamp-black", "IKEA", "Podna lampa crna", "rasvjeta", "69.99", "industrial,modern", "living-room,bedroom,home-office", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80", "Rasvjeta koja brzo mijenja dojam prostora."),
                product("ikea-starter-shelf-white", "IKEA", "Otvorena polica bijela", "polica", "59.99", "minimal,modern,classic", "living-room,home-office,bedroom", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=900&q=80", "Praktična pohrana za stvari koje ne želiš gledati po sobi."),
                product("ikea-starter-bed-frame-oak", "IKEA", "Okvir kreveta hrast efekt", "krevet", "199.00", "minimal,modern,classic", "bedroom", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80", "Siguran osnovni komad za spavaću sobu."),
                product("ikea-starter-lounge-chair", "IKEA", "Fotelja mekani tekstil", "stolica", "89.99", "classic,cozy", "living-room,home-office,bedroom", "standard", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=900&q=80", "Udoban dodatak ako ostane prostora i budžeta."),
                product("ikea-starter-desk-white", "IKEA", "Radni stol bijeli", "stol", "49.99", "minimal,modern", "home-office", "budget", "https://www.ikea.com/hr/hr/", "https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80", "Povoljan stol za osnovni radni kutak.")
        ));
    }

    private ImportSummaryDto importCandidates(List<ImportCandidate> candidates, List<ImportErrorDto> initialErrors, int totalReceived) {
        List<ImportErrorDto> errors = new ArrayList<>(initialErrors);
        List<ProductDto> importedProducts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = initialErrors.size();

        for (ImportCandidate candidate : candidates) {
            ImportProductDto dto = candidate.dto();
            List<ImportErrorDto> validationErrors = validate(dto, candidate.row());
            if (!validationErrors.isEmpty()) {
                errors.addAll(validationErrors);
                skipped++;
                continue;
            }

            String externalId = dto.externalId().trim();
            Product entity = productRepository.findByExternalId(externalId).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new Product();
                entity.setId(hasText(dto.id()) ? dto.id().trim() : UUID.randomUUID().toString());
                entity.setRating(0);
                entity.setInStock(true);
            }

            apply(dto, entity);
            applyDefaults(entity);
            productRepository.save(entity);
            importedProducts.add(ProductDto.from(entity));

            if (isNew) created++; else updated++;
        }

        return summary(created, updated, skipped, totalReceived, importedProducts, errors);
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
                value(row, "note", "napomena"),
                value(row, "sourcetype", "source_type", "izvor_tip"),
                value(row, "sourcename", "source_name", "izvor"),
                value(row, "sourcereference", "source_reference", "izvor_ref"),
                value(row, "dataquality", "data_quality", "kvaliteta"),
                value(row, "dataqualitynotes", "data_quality_notes", "kvaliteta_napomena"),
                splitTags(value(row, "colortags", "color_tags", "boje")),
                splitTags(value(row, "materialtags", "material_tags", "materijali"))
        );
    }

    private void apply(ImportProductDto dto, Product entity) {
        entity.setExternalId(dto.externalId().trim());
        entity.setName(dto.name().trim());
        entity.setRetailer(ProductTaxonomy.normalizeRetailer(dto.retailer()).orElse(dto.retailer().trim()));
        entity.setCategory(ProductTaxonomy.normalizeCategory(dto.category()).orElse(dto.category().trim()));
        entity.setPrice(dto.price());
        if (dto.originalPrice() != null) entity.setOriginalPrice(dto.originalPrice());
        entity.setStyleTags(joinCanonicalStyles(dto.styleTags()));
        entity.setRoomTags(joinCanonicalRooms(dto.roomTags()));
        if (hasText(dto.imageUrl())) {
            entity.setImageUrl(dto.imageUrl().trim());
            entity.setImage(dto.imageUrl().trim());
        }
        if (hasText(dto.productUrl())) {
            entity.setProductUrl(dto.productUrl().trim());
            entity.setUrl(dto.productUrl().trim());
        }
        if (hasText(dto.availabilityStatus())) {
            String availability = ProductTaxonomy.normalizeAvailability(dto.availabilityStatus());
            entity.setAvailabilityStatus(availability);
            entity.setInStock(!"unavailable".equals(availability));
        } else if (!hasText(entity.getAvailabilityStatus())) {
            entity.setAvailabilityStatus("in-stock");
            entity.setInStock(true);
        } else {
            entity.setInStock(!"unavailable".equals(ProductTaxonomy.normalizeAvailability(entity.getAvailabilityStatus())));
        }
        if (hasText(dto.deliveryNote())) entity.setDeliveryNote(dto.deliveryNote().trim());
        if (hasText(dto.lastCheckedAt())) {
            entity.setLastCheckedAt(dto.lastCheckedAt().trim());
        } else if (!hasText(entity.getLastCheckedAt())) {
            entity.setLastCheckedAt(LocalDate.now().toString());
        }
        if (hasText(dto.priceTier())) entity.setPriceTier(dto.priceTier().trim());
        if (hasText(dto.note())) entity.setNote(dto.note().trim());
        if (hasText(dto.sourceType())) {
            entity.setSourceType(ProductTaxonomy.normalizeSourceType(dto.sourceType()).orElse(dto.sourceType().trim().toLowerCase(Locale.ROOT)));
        }
        if (hasText(dto.sourceName())) entity.setSourceName(dto.sourceName().trim());
        if (hasText(dto.sourceReference())) entity.setSourceReference(dto.sourceReference().trim());
        if (hasText(dto.dataQuality())) {
            entity.setDataQuality(ProductTaxonomy.normalizeDataQuality(dto.dataQuality()).orElse(dto.dataQuality().trim().toLowerCase(Locale.ROOT)));
        }
        if (hasText(dto.dataQualityNotes())) entity.setDataQualityNotes(dto.dataQualityNotes().trim());
        applyColorAndMaterialTags(dto, entity);
        // Sprint 10.13: reviews (#2) + market (#3). Market defaults to HR (the launch market).
        if (dto.reviewCount() != null) entity.setReviewCount(dto.reviewCount());
        if (dto.reviewRating() != null) entity.setReviewRating(dto.reviewRating());
        if (hasText(dto.reviewsUrl())) entity.setReviewsUrl(dto.reviewsUrl().trim());
        entity.setMarket(Markets.normalize(dto.market()));
        entity.setImportedAt(Instant.now().toString());
    }

    // Sprint 10.7: store colour/material tags so the planner can prefer products that match the
    // user's colour/material preferences. If the import did not declare them, derive a best-effort
    // set from the product name (e.g. "Kauč sivi" -> grey, "Stol hrast" -> wood). Only overwrites
    // when we actually have a value, so re-imports never wipe previously-set tags.
    private void applyColorAndMaterialTags(ImportProductDto dto, Product entity) {
        String color = joinSimpleTags(dto.colorTags());
        if (!hasText(color)) color = String.join(",", ProductTaxonomy.deriveColorTags(dto.name()));
        if (hasText(color)) entity.setColorTags(color);

        String material = joinSimpleTags(dto.materialTags());
        if (!hasText(material)) material = String.join(",", ProductTaxonomy.deriveMaterialTags(dto.name()));
        if (hasText(material)) entity.setMaterialTags(material);
    }

    private String joinSimpleTags(List<String> tags) {
        if (tags == null) return null;
        return tags.stream()
                .filter(this::hasText)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
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
        if (entity.getSourceType() == null) entity.setSourceType("manual");
        if (entity.getSourceName() == null) entity.setSourceName(entity.getRetailer());
        if (entity.getSourceReference() == null) entity.setSourceReference("");
        if (entity.getImportedAt() == null) entity.setImportedAt(Instant.now().toString());
        if (entity.getDataQuality() == null) entity.setDataQuality(inferDataQuality(entity));
        if (entity.getDataQualityNotes() == null) entity.setDataQualityNotes("");
    }

    // When the source did not declare a quality, infer it: a product with a real link, image
    // and a recent check is "complete"; otherwise "partial".
    private String inferDataQuality(Product entity) {
        boolean hasUrl = hasText(entity.getProductUrl());
        boolean hasImage = hasText(entity.getImageUrl());
        boolean fresh = !ProductTaxonomy.isStale(entity.getLastCheckedAt());
        return hasUrl && hasImage && fresh ? "complete" : "partial";
    }

    private List<ImportErrorDto> validate(ImportProductDto dto, int rowNumber) {
        List<ImportErrorDto> errors = new ArrayList<>();
        String externalId = dto == null ? null : emptyToNull(dto.externalId());
        if (dto == null) {
            errors.add(error(rowNumber, null, "Prazan zapis."));
            return errors;
        }

        if (!hasText(dto.externalId())) errors.add(error(rowNumber, null, "Nedostaje externalId."));
        if (!hasText(dto.name())) errors.add(error(rowNumber, externalId, "Nedostaje naziv proizvoda."));
        if (!hasText(dto.retailer())) {
            errors.add(error(rowNumber, externalId, "Nedostaje trgovina."));
        } else if (ProductTaxonomy.normalizeRetailer(dto.retailer()).isEmpty()) {
            errors.add(error(rowNumber, externalId, "Trgovina nije podržana: " + dto.retailer().trim() + "."));
        }

        if (!hasText(dto.category())) {
            errors.add(error(rowNumber, externalId, "Nedostaje kategorija."));
        } else if (ProductTaxonomy.normalizeCategory(dto.category()).isEmpty()) {
            errors.add(error(rowNumber, externalId, "Nepoznata kategorija: " + dto.category().trim() + "."));
        }

        if (dto.price() == null || dto.price().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(error(rowNumber, externalId, "Cijena mora biti veća od 0."));
        }

        if (dto.styleTags() == null || dto.styleTags().stream().noneMatch(this::hasText)) {
            errors.add(error(rowNumber, externalId, "styleTags ne smije biti prazan."));
        } else {
            dto.styleTags().stream()
                    .filter(this::hasText)
                    .filter(style -> ProductTaxonomy.normalizeStyle(style).isEmpty())
                    .forEach(style -> errors.add(error(rowNumber, externalId, "Nepoznat stil: " + style.trim() + ".")));
        }

        if (dto.roomTags() == null || dto.roomTags().stream().noneMatch(this::hasText)) {
            errors.add(error(rowNumber, externalId, "roomTags ne smije biti prazan."));
        } else {
            dto.roomTags().stream()
                    .filter(this::hasText)
                    .filter(room -> ProductTaxonomy.normalizeRoom(room).isEmpty())
                    .forEach(room -> errors.add(error(rowNumber, externalId, "Nepoznata prostorija: " + room.trim() + ".")));
        }

        if (hasText(dto.productUrl()) && !looksLikeUrl(dto.productUrl())) {
            errors.add(error(rowNumber, externalId, "productUrl mora izgledati kao URL."));
        }
        if (hasText(dto.imageUrl()) && !looksLikeUrl(dto.imageUrl())) {
            errors.add(error(rowNumber, externalId, "imageUrl mora izgledati kao URL."));
        }
        if (hasText(dto.availabilityStatus()) && !ProductTaxonomy.isSupportedAvailability(dto.availabilityStatus())) {
            errors.add(error(rowNumber, externalId, "availabilityStatus mora biti jedan od: in-stock, limited, unavailable, check-store."));
        }
        if (hasText(dto.lastCheckedAt()) && !ProductTaxonomy.isParseableDate(dto.lastCheckedAt())) {
            errors.add(error(rowNumber, externalId, "lastCheckedAt mora biti datum, npr. 2026-06-01 ili ISO vrijeme."));
        }
        if (hasText(dto.sourceType()) && ProductTaxonomy.normalizeSourceType(dto.sourceType()).isEmpty()) {
            errors.add(error(rowNumber, externalId, "sourceType mora biti jedan od: manual, retailer-snapshot, future-scraper."));
        }
        if (hasText(dto.dataQuality()) && ProductTaxonomy.normalizeDataQuality(dto.dataQuality()).isEmpty()) {
            errors.add(error(rowNumber, externalId, "dataQuality mora biti jedan od: complete, partial, needs-review."));
        }
        if (hasText(dto.sourceType()) && !hasText(dto.sourceName())) {
            errors.add(error(rowNumber, externalId, "sourceName je obavezan kad je sourceType postavljen."));
        }
        // Sprint 9.2: a collected (future-scraper) product must carry a real link, otherwise the
        // user could not open or verify it. Manual / retailer-snapshot products may omit it.
        if ("future-scraper".equalsIgnoreCase(trimmed(dto.sourceType())) && !hasText(dto.productUrl())) {
            errors.add(error(rowNumber, externalId, "productUrl je obavezan za prikupljene proizvode."));
        }

        return errors;
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, String> toCsvRow(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        int valueIndex = 0;

        for (int headerIndex = 0; headerIndex < headers.size(); headerIndex++) {
            String header = headers.get(headerIndex);
            int remainingHeaders = headers.size() - headerIndex - 1;
            List<String> parts = new ArrayList<>();
            if (valueIndex < values.size()) {
                parts.add(values.get(valueIndex).trim());
                valueIndex++;
            }

            if (isTagHeader(header)) {
                while (valueIndex < values.size() && values.size() - valueIndex > remainingHeaders) {
                    String next = values.get(valueIndex).trim();
                    boolean consume = isStyleHeader(header)
                            ? ProductTaxonomy.isKnownStyle(next)
                            : ProductTaxonomy.isKnownRoom(next);
                    if (!consume) break;
                    parts.add(next);
                    valueIndex++;
                }
            }

            row.put(header, String.join(",", parts).trim());
        }
        return row;
    }

    private List<String> missingRequiredHeaders(List<String> headers) {
        return REQUIRED_HEADERS.stream()
                .filter(required -> !headersContain(headers, required))
                .toList();
    }

    private boolean headersContain(List<String> headers, String expected) {
        String normalizedExpected = normalizeHeader(expected);
        if (headers.contains(normalizedExpected)) return true;
        return switch (normalizedExpected) {
            case "externalid" -> headers.contains("external");
            case "name" -> headers.contains("naziv");
            case "retailer" -> headers.contains("trgovina");
            case "category" -> headers.contains("kategorija");
            case "price" -> headers.contains("cijena");
            case "styletags" -> headers.contains("stilovi");
            case "roomtags" -> headers.contains("prostorije");
            default -> false;
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
                note,
                "manual",
                retailer,
                null,
                null,
                null
        );
    }

    private BigDecimal parsePrice(String value) {
        if (!hasText(value)) return null;
        try {
            return new BigDecimal(value.replace("€", "").replace(',', '.').trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Cijena nije ispravna: " + value + ".");
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
        if (value == null) return "";
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.replace(" ", "").replace("-", "").replace("_", "");
    }

    private Optional<Integer> firstNonBlankLine(String[] lines) {
        for (int index = 0; index < lines.length; index++) {
            if (hasText(lines[index])) return Optional.of(index);
        }
        return Optional.empty();
    }

    private List<String> splitTags(String value) {
        if (!hasText(value)) return List.of();
        return Arrays.stream(value.split("[,;|]"))
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }

    private String joinCanonicalStyles(List<String> tags) {
        if (tags == null) return "";
        return tags.stream()
                .filter(this::hasText)
                .map(ProductTaxonomy::normalizeStyle)
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String joinCanonicalRooms(List<String> tags) {
        if (tags == null) return "";
        return tags.stream()
                .filter(this::hasText)
                .map(ProductTaxonomy::normalizeRoom)
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.joining(","));
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

    private boolean looksLikeUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && hasText(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isTagHeader(String header) {
        return isStyleHeader(header) || "roomtags".equals(header);
    }

    private boolean isStyleHeader(String header) {
        return "styletags".equals(header);
    }

    private ImportErrorDto error(int row, String externalId, String message) {
        return new ImportErrorDto(row, emptyToNull(externalId), message);
    }

    private ImportSummaryDto summary(int created, int updated, int skipped, int totalReceived, List<ProductDto> products, List<ImportErrorDto> errors) {
        return new ImportSummaryDto(created, updated, skipped, totalReceived, products, errors);
    }

    private String emptyToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ImportCandidate(int row, ImportProductDto dto) {
    }
}
