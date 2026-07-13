package ai.budgetspace.dto;

import java.util.List;

/**
 * Sprint 10.109: one room of a Move-In result — its allocated budget plus the normal 3 plan tiers
 * (the frontend defaults to the "best value" tier). {@code partial} mirrors the single-room partial flag.
 *
 * <p>Sprint 10.183 (Move-In QoL — apartment status): three OPTIONAL honesty buckets, surfaced so the
 * whole-apartment overview can group what is still missing without any fabrication:
 * <ul>
 *   <li>{@code missingEssential} — room-required categories the market can't supply ("Treba za useljenje").</li>
 *   <li>{@code niceToHave} — desired-but-optional categories absent yet available in-market ("Dobro je dodati").</li>
 *   <li>{@code unavailableInMarket} — categories the user explicitly asked for that this market lacks
 *       ("Nije pronađeno za tvoje tržište").</li>
 * </ul>
 * All three are back-compatible: an old client that omits them (or sends null) reads them as empty.
 */
public record MoveInRoomDto(String roomType, int allocatedBudget, List<FurnishingPlanDto> plans, boolean partial,
                            List<String> missingEssential, List<String> niceToHave,
                            List<String> unavailableInMarket) {

    public MoveInRoomDto {
        plans = plans == null ? List.of() : plans;
        missingEssential = missingEssential == null ? List.of() : missingEssential;
        niceToHave = niceToHave == null ? List.of() : niceToHave;
        unavailableInMarket = unavailableInMarket == null ? List.of() : unavailableInMarket;
    }

    /** Legacy 4-arg shape (pre-10.183): no honesty buckets. */
    public MoveInRoomDto(String roomType, int allocatedBudget, List<FurnishingPlanDto> plans, boolean partial) {
        this(roomType, allocatedBudget, plans, partial, List.of(), List.of(), List.of());
    }
}
