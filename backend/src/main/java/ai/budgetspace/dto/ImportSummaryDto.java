package ai.budgetspace.dto;

import java.util.List;

public record ImportSummaryDto(
        int created,
        int updated,
        int skipped,
        List<String> errors,
        List<ProductDto> products
) {
}
