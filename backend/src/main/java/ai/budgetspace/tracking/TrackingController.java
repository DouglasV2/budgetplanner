package ai.budgetspace.tracking;

import ai.budgetspace.dto.PlanFeedbackRequest;
import ai.budgetspace.dto.ProductClickRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class TrackingController {
    private final ProductClickRepository productClickRepository;
    private final PlanFeedbackRepository planFeedbackRepository;

    public TrackingController(ProductClickRepository productClickRepository, PlanFeedbackRepository planFeedbackRepository) {
        this.productClickRepository = productClickRepository;
        this.planFeedbackRepository = planFeedbackRepository;
    }

    @PostMapping("/api/events/product-click")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> trackProductClick(@RequestBody ProductClickRequest request) {
        if (request != null && request.productId() != null && request.retailer() != null) {
            productClickRepository.save(new ProductClick(
                    emptyToNull(request.planId()),
                    request.productId(),
                    request.retailer(),
                    request.source() == null || request.source().isBlank() ? "plan" : request.source(),
                    Instant.now()
            ));
        }
        return Map.of("status", "ok");
    }

    @PostMapping("/api/events/plan-feedback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> trackPlanFeedback(@RequestBody PlanFeedbackRequest request) {
        if (request != null && request.planId() != null && request.feedback() != null) {
            planFeedbackRepository.save(new PlanFeedback(
                    request.planId(),
                    request.feedback(),
                    request.note(),
                    Instant.now()
            ));
        }
        return Map.of("status", "ok");
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
