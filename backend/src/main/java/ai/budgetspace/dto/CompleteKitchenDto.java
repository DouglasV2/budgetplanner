package ai.budgetspace.dto;

import java.util.List;

/**
 * Sprint 10.175 (kitchen Increment 1): the "complete kitchen" result section. {@code sets} are real modular
 * kitchen sets (each a {@link ProductDto}, category {@code kitchen-set}); {@code shape}/{@code includeAppliances}
 * are what we understood from the prompt (display only, never hard filters). {@code showModularNote} drives the
 * honest "modular, not fitted" note. An empty {@code sets} list is an honest "no set fits your budget" state —
 * never a fabricated result.
 */
public record CompleteKitchenDto(List<ProductDto> sets, String shape, boolean includeAppliances, boolean showModularNote) {
    public CompleteKitchenDto {
        sets = sets == null ? List.of() : sets;
    }

    public static CompleteKitchenDto none() {
        return new CompleteKitchenDto(List.of(), "unknown", false, false);
    }
}
