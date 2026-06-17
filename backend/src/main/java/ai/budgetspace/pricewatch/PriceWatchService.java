package ai.budgetspace.pricewatch;

import ai.budgetspace.dto.CreatePriceWatchRequest;
import ai.budgetspace.dto.PriceWatchDto;
import ai.budgetspace.product.Markets;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sprint 10.34 — creates and cancels opt-in price-drop watches. Honest + GDPR-minimal: a watch needs an
 * explicit consent flag, a valid email and a real (link-bearing) catalog product; we store only what an
 * alert needs and issue a one-click unsubscribe token. Nothing is sent here — delivery happens later in
 * the re-check job through the {@code PriceWatchNotifier} seam.
 */
@Service
public class PriceWatchService {
    private static final Logger log = LoggerFactory.getLogger(PriceWatchService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int DEFAULT_THRESHOLD = 5; // owner decision: ignore sub-5% noise
    private static final int MIN_THRESHOLD = 1;
    private static final int MAX_THRESHOLD = 90;

    private final PriceWatchRepository repository;
    private final ProductRepository productRepository;

    public PriceWatchService(PriceWatchRepository repository, ProductRepository productRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
    }

    public PriceWatchDto create(CreatePriceWatchRequest request, String sessionId) {
        if (request == null) throw new IllegalArgumentException("Prazan zahtjev.");
        if (!request.consent()) {
            throw new IllegalArgumentException("Za praćenje cijene potreban je izričit pristanak na primanje e-maila.");
        }
        String email = request.email() == null ? "" : request.email().trim();
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Unesi ispravnu e-mail adresu.");
        }
        String externalId = request.externalId() == null ? "" : request.externalId().trim();
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("Nedostaje proizvod za praćenje.");
        }

        Product product = productRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Proizvod nije pronađen."));
        String productUrl = firstNonBlank(product.getProductUrl(), product.getUrl());
        if (productUrl == null || !productUrl.startsWith("http")) {
            throw new IllegalArgumentException("Proizvod nema poveznicu pa mu cijenu ne možemo pratiti.");
        }
        if (product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Proizvod nema cijenu pa ga ne možemo pratiti.");
        }

        // Idempotent: one active watch per email+product. Re-submitting is a no-op that returns the watch.
        Optional<PriceWatch> existing = repository.findByEmailIgnoreCaseAndExternalIdAndActiveTrue(email, externalId);
        if (existing.isPresent()) {
            return PriceWatchDto.of(existing.get(), true);
        }

        String now = Instant.now().toString();
        PriceWatch watch = new PriceWatch();
        watch.setId(UUID.randomUUID().toString());
        watch.setExternalId(externalId);
        watch.setMarket(Markets.normalize(firstNonBlank(request.market(), product.getMarket())));
        watch.setRetailer(product.getRetailer());
        watch.setProductName(product.getName());
        watch.setProductUrl(productUrl);
        watch.setEmail(email);
        watch.setSessionId(emptyToNull(sessionId));
        watch.setBaselinePrice(product.getPrice());
        watch.setThresholdPercent(clampThreshold(request.thresholdPercent()));
        watch.setActive(true);
        watch.setUnsubscribeToken(UUID.randomUUID().toString().replace("-", ""));
        watch.setConsentAt(now);
        watch.setCreatedAt(now);
        repository.save(watch);

        log.info("Price watch created: product={} market={} threshold={}% baseline={} session={}",
                externalId, watch.getMarket(), watch.getThresholdPercent(), watch.getBaselinePrice(), watch.getSessionId());
        return PriceWatchDto.of(watch, false);
    }

    public Map<String, String> unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Nedostaje token za odjavu.");
        }
        Optional<PriceWatch> watch = repository.findByUnsubscribeToken(token.trim());
        if (watch.isPresent() && watch.get().isActive()) {
            watch.get().setActive(false);
            repository.save(watch.get());
            log.info("Price watch unsubscribed: product={}", watch.get().getExternalId());
        }
        // Always report success so the token cannot be probed for validity.
        return Map.of("status", "unsubscribed");
    }

    private int clampThreshold(Integer requested) {
        if (requested == null) return DEFAULT_THRESHOLD;
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, requested));
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b == null || b.isBlank() ? null : b.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
