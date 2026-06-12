package ai.budgetspace.dto;

import java.util.List;

public record ImportSummaryDto(
        int created,
        int updated,
        int skipped,
        int totalReceived,
        List<ProductDto> products,
        List<ImportErrorDto> errors
) {
}
