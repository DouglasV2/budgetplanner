package ai.budgetspace.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sprint 10.10 — in-memory AI usage tracking + cost/rate guardrails (monetization groundwork, no DB
 * yet). Before any real AI call the planner asks {@link #canUseAi(String)}; when a monthly-budget,
 * per-day or per-session limit is hit, the app uses the rule-based fallback instead of failing.
 *
 * <p>Counters are derived from recorded events, so they reset naturally each day/month. Only real AI
 * calls ({@code fallbackUsed=false}) count toward the limits.</p>
 */
@Service
public class AiUsageTracker {
    private static final Logger log = LoggerFactory.getLogger(AiUsageTracker.class);
    private static final int MAX_RETAINED_EVENTS = 5000;

    private final Deque<AiUsageEvent> events = new ArrayDeque<>();
    private final double monthlyBudgetUsd;
    private final int maxRequestsPerDay;
    private final int maxRequestsPerSession;
    private final double inputCostPer1k;
    private final double outputCostPer1k;

    public AiUsageTracker(
            @Value("${budgetspace.ai.monthly-budget-usd:20}") double monthlyBudgetUsd,
            @Value("${budgetspace.ai.max-requests-per-day:100}") int maxRequestsPerDay,
            @Value("${budgetspace.ai.max-requests-per-session:10}") int maxRequestsPerSession,
            @Value("${budgetspace.ai.input-cost-per-1k-usd:0.0002}") double inputCostPer1k,
            @Value("${budgetspace.ai.output-cost-per-1k-usd:0.0008}") double outputCostPer1k) {
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.maxRequestsPerDay = maxRequestsPerDay;
        this.maxRequestsPerSession = maxRequestsPerSession;
        this.inputCostPer1k = inputCostPer1k;
        this.outputCostPer1k = outputCostPer1k;
    }

    /** True if a real AI call is allowed for this session under all guardrails. */
    public synchronized boolean canUseAi(String sessionId) {
        if (monthlySpendUsd() >= monthlyBudgetUsd) {
            log.warn("AI monthly budget reached ({} USD); using rule-based fallback.", monthlyBudgetUsd);
            return false;
        }
        if (countToday() >= maxRequestsPerDay) {
            log.warn("AI daily request limit reached ({}); using rule-based fallback.", maxRequestsPerDay);
            return false;
        }
        if (countTodayForSession(sessionId) >= maxRequestsPerSession) {
            return false;
        }
        return true;
    }

    public double estimateCostUsd(Integer inputTokens, Integer outputTokens) {
        double in = inputTokens == null ? 0 : inputTokens / 1000.0 * inputCostPer1k;
        double out = outputTokens == null ? 0 : outputTokens / 1000.0 * outputCostPer1k;
        return Math.round((in + out) * 1_000_000.0) / 1_000_000.0;
    }

    public synchronized void record(AiUsageEvent event) {
        events.addLast(event);
        while (events.size() > MAX_RETAINED_EVENTS) {
            events.removeFirst();
        }
    }

    public synchronized double monthlySpendUsd() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        return events.stream()
                .filter(e -> !e.fallbackUsed())
                .filter(e -> YearMonth.from(e.createdAt().atZone(ZoneOffset.UTC)).equals(now))
                .mapToDouble(AiUsageEvent::estimatedCostUsd)
                .sum();
    }

    private long countToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return events.stream()
                .filter(e -> !e.fallbackUsed())
                .filter(e -> e.createdAt().atZone(ZoneOffset.UTC).toLocalDate().equals(today))
                .count();
    }

    private long countTodayForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return 0;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return events.stream()
                .filter(e -> !e.fallbackUsed())
                .filter(e -> sessionId.equals(e.sessionId()))
                .filter(e -> e.createdAt().atZone(ZoneOffset.UTC).toLocalDate().equals(today))
                .count();
    }

    /** Snapshot for an operator/debug view. */
    public synchronized UsageSnapshot snapshot() {
        return new UsageSnapshot(events.size(), monthlySpendUsd(), countToday(), monthlyBudgetUsd,
                maxRequestsPerDay, maxRequestsPerSession);
    }

    public record UsageSnapshot(int retainedEvents, double monthlySpendUsd, long requestsToday,
                                double monthlyBudgetUsd, int maxRequestsPerDay, int maxRequestsPerSession) {
    }

    static AiUsageEvent now(String provider, String model, String useCase, String sessionId,
                            Integer inputTokens, Integer outputTokens, double estimatedCostUsd,
                            boolean success, boolean fallbackUsed) {
        return new AiUsageEvent(provider, model, useCase, sessionId, inputTokens, outputTokens,
                estimatedCostUsd, success, fallbackUsed, Instant.now());
    }
}
