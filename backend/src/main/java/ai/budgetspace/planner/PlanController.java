package ai.budgetspace.planner;

import ai.budgetspace.dto.DesignAssistantResponse;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.auth.AuthService;
import ai.budgetspace.config.ClientIp;
import ai.budgetspace.dto.MoveInRequestDto;
import ai.budgetspace.dto.MoveInResponse;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.PlannerIntentAnalysisDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import ai.budgetspace.dto.SimilarItemsRequest;
import ai.budgetspace.dto.SimilarItemsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanController {
    private static final String AUTH_COOKIE = "bs_auth";
    private static final String SESSION_HEADER = "X-BudgetSpace-Session";

    private final PlannerService plannerService;
    private final DesignAssistantService designAssistantService;
    private final PromptIntelligenceService promptIntelligenceService;
    private final AuthService authService;
    // Sprint 10.168: trusted reverse-proxy hop count for resolving the real client IP (Railway/most PaaS = 1).
    private final int trustedProxyCount;

    public PlanController(PlannerService plannerService, DesignAssistantService designAssistantService,
                         PromptIntelligenceService promptIntelligenceService, AuthService authService,
                         @Value("${budgetspace.trusted-proxy-count:1}") int trustedProxyCount) {
        this.plannerService = plannerService;
        this.designAssistantService = designAssistantService;
        this.promptIntelligenceService = promptIntelligenceService;
        this.authService = authService;
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    @PostMapping("/api/plans/generate")
    public PlanGenerationResponse generate(@RequestBody PlannerInputDto input,
                                           @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
                                           @CookieValue(name = AUTH_COOKIE, required = false) String authToken,
                                           HttpServletRequest request) {
        // Understand the prompt first (AI when enabled + within the tier's allowance, else rule-based), then plan.
        Caller caller = resolveCaller(authToken, sessionId, request);
        PlannerIntentAnalysisDto analysis = promptIntelligenceService.analyze(input, caller.countingKey(), caller.tier());
        PlanGenerationResponse response = analysis.aiUsed()
                ? plannerService.generateResolved(promptIntelligenceService.toPlannerInput(analysis, input), analysis.specificItemsOnly())
                : plannerService.generate(input);
        return response.withAnalysis(analysis);
    }

    // Sprint 10.78: an INSTANT deterministic plan (rule-based parse, no AI/LLM call, no auth needed) so the
    // frontend can paint a draft in ~50ms while /api/plans/generate refines it with AI in the background — the
    // user no longer stares at a ~2s spinner. No tier gating here because no AI call happens.
    @PostMapping("/api/plans/generate-fast")
    public PlanGenerationResponse generateFast(@RequestBody PlannerInputDto input) {
        return plannerService.generate(input);
    }

    // Sprint 10.109 (Move-In / "Cijeli stan"): whole-apartment plan — split one total budget across the chosen
    // rooms (catalog-floor-aware) and return a plan per room + a grand total. Rule-based, no AI/auth (like
    // generate-fast); the per-room gating happens inside the planner.
    @PostMapping("/api/plans/generate-move-in")
    public MoveInResponse generateMoveIn(@RequestBody MoveInRequestDto request) {
        return plannerService.generateMoveIn(request);
    }

    @PostMapping("/api/plans/replace")
    public FurnishingPlanDto replaceProduct(@RequestBody ReplaceProductRequest request) {
        return plannerService.replaceProduct(request);
    }

    // Sprint 10.173 (P0): "find similar items under my budget" — given the anchor product the user is looking at
    // and a budget cap, return up to three verified in-catalog alternatives (budget pick / best value / nicer).
    // Rule-based, no AI/auth (like generate-fast/replace); browse-only, never mutates a plan.
    @PostMapping("/api/plans/similar")
    public SimilarItemsResponse similarItems(@RequestBody SimilarItemsRequest request) {
        return plannerService.findSimilar(request);
    }

    // Sprint 10.8: the frontend calls this after /api/plans/generate, passing the generation result
    // back in, to get a short design-assistant description of the plan.
    // Sprint 10.71: resolve the caller so the (Anthropic) design-summary AI shares the same per-tier caps.
    @PostMapping("/api/plans/design")
    public DesignAssistantResponse design(@RequestBody PlanGenerationResponse plan,
                                          @RequestHeader(name = SESSION_HEADER, required = false) String sessionId,
                                          @CookieValue(name = AUTH_COOKIE, required = false) String authToken,
                                          HttpServletRequest request) {
        Caller caller = resolveCaller(authToken, sessionId, request);
        return designAssistantService.describe(plan, caller.countingKey(), caller.tier());
    }

    // Sprint 10.70/10.71: who is asking, for AI usage gating. The tier (FREE/PLUS/PRO, else GUEST) sets the
    // per-day cap. Sprint 10.168: a GUEST's allowance is counted PER CLIENT IP, not per browser session — so
    // clearing localStorage / a fresh incognito window (a new session id) can't reset the daily cap. Signed-in
    // callers stay keyed by their account. Tradeoff: guests behind one NAT/office IP share the guest/day
    // allowance (they can sign in for a per-account one; tune budgetspace.ai.daily-per-user.guest). The IP is
    // resolved spoof-resistantly (ClientIp) so a rotated X-Forwarded-For can't mint fresh guest buckets.
    private Caller resolveCaller(String authToken, String sessionId, HttpServletRequest request) {
        String ownerKey = authService.resolveOwnerKey(authToken, sessionId);
        String tier = authService.aiTierFor(ownerKey);
        String countingKey = (ownerKey != null && !"GUEST".equalsIgnoreCase(tier))
                ? ownerKey
                : "guest-ip:" + ClientIp.resolve(request, trustedProxyCount);
        return new Caller(countingKey, tier);
    }

    private record Caller(String countingKey, String tier) {
    }
}
