package ai.budgetspace.tracking;

import ai.budgetspace.dto.PlanFeedbackRequest;
import ai.budgetspace.dto.PlusInterestRequest;
import ai.budgetspace.dto.ProductClickRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
public class TrackingController {
    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);
    private static final String SESSION_HEADER = "X-BudgetSpace-Session";

    private final ProductClickRepository productClickRepository;
    private final PlanFeedbackRepository planFeedbackRepository;
    private final PlusInterestRepository plusInterestRepository;

    public TrackingController(ProductClickRepository productClickRepository, PlanFeedbackRepository planFeedbackRepository,
                              PlusInterestRepository plusInterestRepository) {
        this.productClickRepository = productClickRepository;
        this.planFeedbackRepository = planFeedbackRepository;
        this.plusInterestRepository = plusInterestRepository;
    }

    // Sprint 10.68: early willingness-to-pay signal — a "I want Plus" tap, optionally with an email for the
    // launch list. No payment; a waitlist + interest counter that lets us gauge demand before building Stripe.
    @PostMapping("/api/events/plus-interest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> trackPlusInterest(@RequestBody(required = false) PlusInterestRequest request,
                                                 @RequestHeader(name = SESSION_HEADER, required = false) String sessionId) {
        // Truncate to the column widths so an oversized email/source can't overflow the insert into a 500.
        String email = request == null ? null : truncate(emptyToNull(request.email()), 320);
        String source = request == null ? null : truncate(emptyToNull(request.source()), 40);
        plusInterestRepository.save(new PlusInterest(
                UUID.randomUUID().toString().replace("-", ""),
                emptyToNull(sessionId), email, source, Instant.now()));
        // Never log the email value (PII) — only whether one was left.
        log.info("Plus interest captured (source={}, email={}).", source, email != null ? "provided" : "none");
        return Map.of("status", "ok");
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

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
