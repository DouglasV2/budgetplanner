package ai.budgetspace.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 10.10 / 10.70 — in-memory AI usage tracking + cost/rate guardrails (monetization groundwork,
 * no DB yet). Before any real AI call the caller does {@link #tryAcquire(String, String)} and, when it
 * returns true, MUST {@link #complete(String, AiUsageEvent)} exactly once (in a finally). When a
 * guardrail is hit the app uses the rule-based fallback instead of failing.
 *
 * <p>Three layers, widest to narrowest:</p>
 * <ol>
 *   <li><strong>monthly USD budget</strong> — the wallet's hard stop across ALL users; once reached no
 *       real AI runs until next month (the deterministic core still works).</li>
 *   <li><strong>global daily request cap</strong> — a coarse safety backstop across all users.</li>
 *   <li><strong>per-user, per-tier daily cap</strong> — the product control: a guest gets a small taste,
 *       Free a generous taste, Plus a near-unlimited allowance, Pro the most. Counted per owner key.</li>
 * </ol>
 *
 * <p>Admission is atomic with accounting: {@code tryAcquire} reserves an in-flight slot under the lock
 * BEFORE the slow LLM round-trip, and the per-owner/global counts include in-flight reservations — so
 * concurrent requests from one owner can't all pass a stale count and overshoot the cap (closes the
 * check-then-act window across the network call). {@code complete} releases the reservation and records
 * the real event (fallback events are not retained — they count toward nothing and would otherwise let a
 * flapping provider evict today's real events from the bounded log).</p>
 *
 * <p>Counters are derived from recorded events, so they reset naturally each day/month. Sprint 10.86: the
 * in-memory log stays the live, atomic source (fast, lock-guarded), but every real event is written through to a
 * durable table ({@link AiUsageRecord}) and the counters are rehydrated from it on startup — so the monthly-USD
 * wallet and per-user daily caps survive restarts/redeploys instead of resetting to zero. The write is
 * best-effort (a DB hiccup never fails the user's request). Atomicity is per instance; for multi-instance, pair
 * with a shared store.</p>
 */
@Service
public class AiUsageTracker {
    private static final Logger log = LoggerFactory.getLogger(AiUsageTracker.class);
    private static final int MAX_RETAINED_EVENTS = 5000;
    // We only ever count the current month (wallet) + today (daily caps), so older rows are dead weight.
    private static final int RETENTION_DAYS = 45;

    private final Deque<AiUsageEvent> events = new ArrayDeque<>();
    // Per-owner count of admitted-but-not-yet-completed AI calls. Counts toward the caps so a burst of
    // concurrent requests from one owner can't overshoot during the (unlocked) LLM round-trip.
    private final Map<String, Integer> inFlight = new HashMap<>();
    private final double monthlyBudgetUsd;
    private final int maxRequestsPerDay;
    private final int guestDailyLimit;
    private final int freeDailyLimit;
    private final int plusDailyLimit;
    private final int proDailyLimit;
    private final double inputCostPer1k;
    private final double outputCostPer1k;
    // Durable ledger (Sprint 10.86). Null in the pure in-memory test constructor; set by Spring in production.
    private final AiUsageRecordRepository repository;

    // Test/convenience constructor: pure in-memory, no persistence. Production uses the @Autowired one below.
    public AiUsageTracker(double monthlyBudgetUsd, int maxRequestsPerDay, int guestDailyLimit, int freeDailyLimit,
                          int plusDailyLimit, int proDailyLimit, double inputCostPer1k, double outputCostPer1k) {
        this(monthlyBudgetUsd, maxRequestsPerDay, guestDailyLimit, freeDailyLimit, plusDailyLimit, proDailyLimit,
                inputCostPer1k, outputCostPer1k, null);
    }

    @Autowired
    public AiUsageTracker(
            @Value("${budgetspace.ai.monthly-budget-usd:20}") double monthlyBudgetUsd,
            @Value("${budgetspace.ai.max-requests-per-day:2000}") int maxRequestsPerDay,
            @Value("${budgetspace.ai.daily-per-user.guest:3}") int guestDailyLimit,
            @Value("${budgetspace.ai.daily-per-user.free:10}") int freeDailyLimit,
            @Value("${budgetspace.ai.daily-per-user.plus:100}") int plusDailyLimit,
            @Value("${budgetspace.ai.daily-per-user.pro:500}") int proDailyLimit,
            @Value("${budgetspace.ai.input-cost-per-1k-usd:0.0002}") double inputCostPer1k,
            @Value("${budgetspace.ai.output-cost-per-1k-usd:0.0008}") double outputCostPer1k,
            AiUsageRecordRepository repository) {
        this.monthlyBudgetUsd = monthlyBudgetUsd;
        this.maxRequestsPerDay = maxRequestsPerDay;
        this.guestDailyLimit = guestDailyLimit;
        this.freeDailyLimit = freeDailyLimit;
        this.plusDailyLimit = plusDailyLimit;
        this.proDailyLimit = proDailyLimit;
        this.inputCostPer1k = inputCostPer1k;
        this.outputCostPer1k = outputCostPer1k;
        this.repository = repository;
    }

    /**
     * Atomically checks all guardrails AND reserves a slot for this owner when allowed. Pair every
     * {@code true} return with exactly one {@link #complete} (in a finally) to release the reservation.
     * Fails closed for a missing owner key.
     */
    public synchronized boolean tryAcquire(String ownerKey, String tier) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return false; // a guardrail with no identity denies rather than admits
        }
        if (monthlySpendUsd() >= monthlyBudgetUsd) {
            log.warn("AI monthly budget reached ({} USD); using rule-based fallback.", monthlyBudgetUsd);
            return false;
        }
        if (countToday() + totalInFlight() >= maxRequestsPerDay) {
            log.warn("AI global daily request limit reached ({}); using rule-based fallback.", maxRequestsPerDay);
            return false;
        }
        int tierLimit = dailyLimitForTier(tier);
        if (usageForOwner(ownerKey) >= tierLimit) {
            log.debug("AI daily cap reached for tier {} ({}/day); using rule-based fallback.", tier, tierLimit);
            return false;
        }
        inFlight.merge(ownerKey, 1, Integer::sum);
        return true;
    }

    /** Releases the reservation taken by {@link #tryAcquire} and records the (real or fallback) event. */
    public synchronized void complete(String ownerKey, AiUsageEvent event) {
        releaseInFlight(ownerKey);
        record(event);
        persist(event);
    }

    // --- durability: write-through + rehydrate-on-boot + retention prune (Sprint 10.86) ---

    /**
     * Restore the in-memory counters from the durable ledger on startup, so the monthly-USD wallet and per-user
     * daily caps don't reset to zero on every restart/redeploy. Loads this month's most-recent real events
     * (bounded like the in-memory log). Best-effort: a DB hiccup just starts with empty counters.
     */
    @PostConstruct
    synchronized void rehydrate() {
        if (repository == null) {
            return;
        }
        try {
            Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            List<AiUsageRecord> recent = repository.findTop5000ByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(monthStart);
            // Loaded newest-first; add oldest-first so the bounded log evicts the oldest if it ever overflows.
            for (int i = recent.size() - 1; i >= 0; i--) {
                events.addLast(recent.get(i).toEvent());
            }
            while (events.size() > MAX_RETAINED_EVENTS) {
                events.removeFirst();
            }
            log.info("AI usage ledger rehydrated: {} event(s) from this month.", events.size());
        } catch (RuntimeException exception) {
            log.warn("AI usage ledger rehydrate failed — starting with empty in-memory counters.", exception);
        }
    }

    // Write-through to the durable ledger (real events only). Best-effort: the in-memory counters already updated,
    // so a DB error must not fail the user's request — just log it. Skipped in the pure in-memory test mode.
    private void persist(AiUsageEvent event) {
        if (repository == null || event == null || event.fallbackUsed()) {
            return;
        }
        try {
            repository.save(AiUsageRecord.from(event));
        } catch (RuntimeException exception) {
            log.warn("AI usage ledger persist failed — counters stay in memory for this instance.", exception);
        }
    }

    /** Drop ledger rows past the retention window (only the current month + today are ever counted). */
    @Scheduled(cron = "${budgetspace.ai.ledger-cleanup-cron:0 15 3 * * *}")
    void pruneOldLedgerRows() {
        if (repository == null) {
            return;
        }
        try {
            int deleted = repository.deleteByCreatedAtBefore(Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS));
            if (deleted > 0) {
                log.info("AI usage ledger: pruned {} row(s) older than {} days.", deleted, RETENTION_DAYS);
            }
        } catch (RuntimeException exception) {
            log.warn("AI usage ledger prune failed.", exception);
        }
    }

    /**
     * GDPR Art. 17 (best-effort): drop this owner's in-memory usage events + any live reservation, so no per-owner
     * usage metadata for a deleted account lingers in this instance's memory. Holds the same monitor as every other
     * mutation, so it's safe against concurrent tryAcquire/complete. The durable ledger is erased separately (that
     * is the authoritative deletion); this just keeps the transient counters consistent until the next restart.
     */
    public synchronized void forgetOwner(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return;
        }
        events.removeIf(e -> ownerKey.equals(e.ownerKey()));
        inFlight.remove(ownerKey);
    }

    /** The per-user daily AI allowance for a subscription tier. Unknown/guest tiers get the smallest. */
    int dailyLimitForTier(String tier) {
        if (tier == null) return guestDailyLimit;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "PRO" -> proDailyLimit;
            case "PLUS" -> plusDailyLimit;
            case "FREE" -> freeDailyLimit;
            case "GUEST" -> guestDailyLimit;
            default -> guestDailyLimit;
        };
    }

    public double estimateCostUsd(Integer inputTokens, Integer outputTokens) {
        double in = inputTokens == null ? 0 : inputTokens / 1000.0 * inputCostPer1k;
        double out = outputTokens == null ? 0 : outputTokens / 1000.0 * outputCostPer1k;
        return Math.round((in + out) * 1_000_000.0) / 1_000_000.0;
    }

    public synchronized double monthlySpendUsd() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        return events.stream()
                .filter(e -> !e.fallbackUsed())
                .filter(e -> YearMonth.from(e.createdAt().atZone(ZoneOffset.UTC)).equals(now))
                .mapToDouble(AiUsageEvent::estimatedCostUsd)
                .sum();
    }

    // --- counting (caller holds the lock) ---

    // Total that counts against the per-owner cap = today's recorded real calls + current reservations.
    private long usageForOwner(String ownerKey) {
        return countTodayForOwner(ownerKey) + inFlight.getOrDefault(ownerKey, 0);
    }

    private int totalInFlight() {
        return inFlight.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void releaseInFlight(String ownerKey) {
        if (ownerKey == null) return;
        inFlight.computeIfPresent(ownerKey, (k, v) -> v <= 1 ? null : v - 1);
    }

    // Only real (counted) events are retained — fallback events count toward nothing, and keeping them
    // would let a flapping provider's unbounded fallbacks evict today's real events from the bounded log.
    private void record(AiUsageEvent event) {
        if (event == null || event.fallbackUsed()) {
            return;
        }
        events.addLast(event);
        while (events.size() > MAX_RETAINED_EVENTS) {
            events.removeFirst();
        }
    }

    private long countToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return events.stream()
                .filter(e -> !e.fallbackUsed())
                .filter(e -> e.createdAt().atZone(ZoneOffset.UTC).toLocalDate().equals(today))
                .count();
    }

    private long countTodayForOwner(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) return 0;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return events.stream()
                .filter(e -> !e.fallbackUsed())
                .filter(e -> ownerKey.equals(e.ownerKey()))
                .filter(e -> e.createdAt().atZone(ZoneOffset.UTC).toLocalDate().equals(today))
                .count();
    }

    /** Snapshot for an operator/debug view. */
    public synchronized UsageSnapshot snapshot() {
        return new UsageSnapshot(events.size(), monthlySpendUsd(), countToday(), monthlyBudgetUsd, maxRequestsPerDay);
    }

    public record UsageSnapshot(int retainedEvents, double monthlySpendUsd, long requestsToday,
                                double monthlyBudgetUsd, int maxRequestsPerDay) {
    }
}
