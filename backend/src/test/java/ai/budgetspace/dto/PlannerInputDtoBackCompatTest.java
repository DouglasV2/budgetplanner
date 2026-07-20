package ai.budgetspace.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.190: adding {@code secondaryStyles} must not break the two legacy constructor signatures that the
 * frontend, tests and scenario fixtures still use, and must not require a migration for previously saved plans.
 */
class PlannerInputDtoBackCompatTest {

    @Test
    void legacyConstructorsStillCompileAndDefaultSecondaryStyles() {
        // 16-arg (pre-10.7) signature
        PlannerInputDto legacy16 = new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        assertThat(legacy16.secondaryStyles()).isEmpty();

        // 19-arg (pre-10.120) signature
        PlannerInputDto legacy19 = new PlannerInputDto("", 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), "HR");
        assertThat(legacy19.secondaryStyles()).isEmpty();
    }

    @Test
    void aNullSecondaryStyleListIsNeverHandedOn() {
        PlannerInputDto direct = new PlannerInputDto("", 1500, "living-room", "modern", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), "HR", java.util.Map.of(), false, null);
        assertThat(direct.secondaryStyles()).isNotNull().isEmpty();
    }

    @Test
    void withSecondaryStylesPreservesEverythingElse() {
        PlannerInputDto base = new PlannerInputDto("kuhinja", 2000, "kitchen", "modern", "Zagreb", 20, "multi",
                List.of("IKEA"), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);

        PlannerInputDto blended = base.withSecondaryStyles(List.of("classic"));

        assertThat(blended.secondaryStyles()).containsExactly("classic");
        assertThat(blended.style()).isEqualTo("modern");
        assertThat(blended.budget()).isEqualTo(2000);
        assertThat(blended.roomType()).isEqualTo("kitchen");
    }
}
