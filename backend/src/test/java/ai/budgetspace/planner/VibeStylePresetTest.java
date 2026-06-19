package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.57 — locks the "Choose a vibe" contract. Each vibe's STYLE-PURE prompt (these strings mirror the
 * frontend i18n {@code form.vibe*Prompt} keys) must map, via the rule-based {@link PlannerIntentExtractor}, to
 * the style the vibe intends — so if {@code applyStyle} ever changes, the vibe picker fails loudly here instead
 * of silently picking the wrong look. (Also verified live against the running extractor for HR + EN.)
 */
class VibeStylePresetTest {

    private final PlannerIntentExtractor extractor = new PlannerIntentExtractor();

    @Test
    void croatianVibePromptsMapToTheIntendedStyle() {
        assertThat(styleFor("U skandinavskom stilu — svijetlo i prozračno.")).isEqualTo("bright");
        assertThat(styleFor("Japandi ugođaj — minimalistički i smireno.")).isEqualTo("minimal");
        assertThat(styleFor("Minimalistički — čisto i jednostavno.")).isEqualTo("minimal");
        assertThat(styleFor("Industrijski ugođaj — sirovo i karakterno.")).isEqualTo("industrial");
        assertThat(styleFor("Toplo i ugodno.")).isEqualTo("warm");
        assertThat(styleFor("Luksuzno i elegantno — klasičan ugođaj.")).isEqualTo("classic");
    }

    @Test
    void englishVibePromptsMapToTheIntendedStyle() {
        assertThat(styleFor("Scandinavian style — bright and airy.")).isEqualTo("bright");
        assertThat(styleFor("Japandi feel — minimalist and calm.")).isEqualTo("minimal");
        assertThat(styleFor("Minimalist — clean and simple.")).isEqualTo("minimal");
        assertThat(styleFor("Industrial look — raw and characterful.")).isEqualTo("industrial");
        assertThat(styleFor("Luxurious and elegant — a classic mood.")).isEqualTo("classic");
        // "Warm and inviting." carries no style keyword, so the extractor preserves the explicit vibe style
        // (applyVibe sets style='warm'); the vibe relies on this preservation, so assert it explicitly.
        assertThat(extractor.enrich(input("Warm and inviting.", "warm")).style()).isEqualTo("warm");
    }

    private String styleFor(String prompt) {
        return extractor.enrich(input(prompt, "surprise")).style();
    }

    private PlannerInputDto input(String prompt, String style) {
        return new PlannerInputDto(prompt, 1500, "living-room", style, "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }
}
