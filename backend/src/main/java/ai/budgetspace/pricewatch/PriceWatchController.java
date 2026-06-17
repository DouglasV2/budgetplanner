package ai.budgetspace.pricewatch;

import ai.budgetspace.dto.CreatePriceWatchRequest;
import ai.budgetspace.dto.PriceWatchDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sprint 10.34 — public opt-in price-watch endpoints. {@code POST /api/price-watch} creates a watch
 * (explicit consent required; see {@link PriceWatchService}); {@code GET /api/price-watch/unsubscribe}
 * is the one-click opt-out used by the link in every alert. Bad input surfaces as a 400 via the global
 * exception handler.
 */
@RestController
public class PriceWatchController {
    private final PriceWatchService service;

    public PriceWatchController(PriceWatchService service) {
        this.service = service;
    }

    @PostMapping("/api/price-watch")
    public PriceWatchDto create(
            @RequestBody CreatePriceWatchRequest request,
            @RequestHeader(value = "X-BudgetSpace-Session", required = false) String session) {
        return service.create(request, session);
    }

    @GetMapping("/api/price-watch/unsubscribe")
    public Map<String, String> unsubscribe(@RequestParam("token") String token) {
        return service.unsubscribe(token);
    }
}
