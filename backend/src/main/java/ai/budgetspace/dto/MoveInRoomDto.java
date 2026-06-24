package ai.budgetspace.dto;

import java.util.List;

/**
 * Sprint 10.109: one room of a Move-In result — its allocated budget plus the normal 3 plan tiers
 * (the frontend defaults to the "best value" tier). {@code partial} mirrors the single-room partial flag.
 */
public record MoveInRoomDto(String roomType, int allocatedBudget, List<FurnishingPlanDto> plans, boolean partial) {
}
