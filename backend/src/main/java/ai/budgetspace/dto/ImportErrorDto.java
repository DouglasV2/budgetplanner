package ai.budgetspace.dto;

public record ImportErrorDto(
        int row,
        String externalId,
        String message
) {
}
