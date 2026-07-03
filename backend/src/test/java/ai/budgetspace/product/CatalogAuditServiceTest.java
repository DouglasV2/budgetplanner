package ai.budgetspace.product;

import ai.budgetspace.dto.CatalogHealthDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogAuditServiceTest {

    /** Minimal health DTO with just the fields the audit reads; others are filler. */
    private CatalogHealthDto health(int total, int stale, int unavailable, int needsReview) {
        return new CatalogHealthDto(
                total, Math.max(0, total - stale), 0, unavailable, needsReview, stale, 0, 0,
                Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }

    @Test
    void alertsWhenStaleShareCrossesThreshold() {
        CatalogHealthService healthService = mock(CatalogHealthService.class);
        when(healthService.compute()).thenReturn(health(100, 30, 4, 1));
        CatalogAuditService audit = new CatalogAuditService(healthService, true, 0.2);

        CatalogAuditService.AuditReport report = audit.runAudit();

        assertEquals(100, report.totalProducts());
        assertEquals(30, report.staleProducts());
        assertEquals(4, report.unavailableProducts());
        assertTrue(report.staleFraction() >= 0.2);
        assertTrue(report.alert(), "30% stale should trip the 20% threshold");
    }

    @Test
    void doesNotAlertBelowThreshold() {
        CatalogHealthService healthService = mock(CatalogHealthService.class);
        when(healthService.compute()).thenReturn(health(100, 5, 0, 0));
        CatalogAuditService audit = new CatalogAuditService(healthService, true, 0.2);

        assertFalse(audit.runAudit().alert(), "5% stale is under the 20% threshold");
    }

    @Test
    void emptyCatalogNeitherAlertsNorDividesByZero() {
        CatalogHealthService healthService = mock(CatalogHealthService.class);
        when(healthService.compute()).thenReturn(health(0, 0, 0, 0));
        CatalogAuditService audit = new CatalogAuditService(healthService, true, 0.2);

        CatalogAuditService.AuditReport report = audit.runAudit();

        assertEquals(0.0, report.staleFraction());
        assertFalse(report.alert());
    }

    @Test
    void thresholdIsClampedIntoRange() {
        CatalogHealthService healthService = mock(CatalogHealthService.class);
        when(healthService.compute()).thenReturn(health(100, 100, 0, 0));
        // A mis-set threshold above 1.0 is clamped to 1.0 — an all-stale catalog (fraction 1.0) still alerts.
        CatalogAuditService audit = new CatalogAuditService(healthService, true, 5.0);

        assertTrue(audit.runAudit().alert());
    }
}
