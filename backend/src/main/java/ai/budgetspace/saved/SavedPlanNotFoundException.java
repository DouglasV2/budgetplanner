package ai.budgetspace.saved;

import java.util.NoSuchElementException;

/**
 * Sprint 10.65 — a saved plan was requested by an id that doesn't exist (e.g. a shared /plan/&lt;id&gt; link whose
 * plan is gone), or by a non-owner. Mapped to a clean 404 by the global handler — not a 500 with a stack trace,
 * since a missing/stale plan is an expected client situation, not a server fault.
 *
 * <p>Extends {@link NoSuchElementException} so existing call sites and tests that treat "not found" as that type
 * keep working; the global handler matches this more specific type first and answers 404.</p>
 */
public class SavedPlanNotFoundException extends NoSuchElementException {
    public SavedPlanNotFoundException(String message) {
        super(message);
    }
}
