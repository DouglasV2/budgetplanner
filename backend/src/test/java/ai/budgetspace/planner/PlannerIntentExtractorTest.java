package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerIntentExtractorTest {
    private final PlannerIntentExtractor extractor = new PlannerIntentExtractor();

    @Test
    void budgetMentionNeverMarksAProductAsAlreadyOwned() {
        PlannerInputDto parsed = parse("Imam 1500 € za dnevni boravak, treba mi kauč.");

        assertThat(parsed.budget()).isEqualTo(1500);
        assertThat(parsed.alreadyHaveCategories()).isEmpty();
        assertThat(parsed.mustHaveCategories()).contains("sofa");
    }

    @Test
    void alreadyHaveRemovesCategoriesWhileRequestKeepsTheRest() {
        PlannerInputDto parsed = parse("Dnevni boravak, već imam TV i tepih, treba mi kauč i TV komoda.");

        assertThat(parsed.alreadyHaveCategories()).contains("rug");
        assertThat(parsed.alreadyHaveCategories()).doesNotContain("sofa", "tv-unit");
        assertThat(parsed.mustHaveCategories()).contains("sofa", "tv-unit");
    }

    @Test
    void withoutRugExcludesRug() {
        PlannerInputDto parsed = parse("Dnevni boravak do 900 €, bez tepiha.");

        assertThat(parsed.alreadyHaveCategories()).contains("rug");
        assertThat(parsed.budget()).isEqualTo(900);
    }

    @Test
    void chairIsNotMappedAsTableAndStolIsNotChair() {
        assertThat(parse("Radni kutak, treba mi stolica.").mustHaveCategories())
                .contains("chair")
                .doesNotContain("table");
        assertThat(parse("Radni kutak, treba mi stol.").mustHaveCategories())
                .doesNotContain("chair");
    }

    @Test
    void recognisesStoreLimit() {
        assertThat(parse("Dnevni boravak, ne želim više od dvije trgovine.").maxStores()).isEqualTo(2);
        assertThat(parse("Dnevni boravak, jedna trgovina ako može.").maxStores()).isEqualTo(1);
        assertThat(parse("Dnevni boravak, ne želim puno trgovina.").maxStores()).isEqualTo(2);
    }

    @Test
    void recognisesPreferredAndExcludedRetailers() {
        PlannerInputDto preferred = parse("Radni kutak do 600 €, bez Lesnine, najviše IKEA.");

        assertThat(preferred.preferredRetailers()).contains("IKEA");
        assertThat(preferred.excludedRetailers()).contains("Lesnina");
        assertThat(preferred.selectedRetailers()).doesNotContain("Lesnina");
        assertThat(preferred.roomType()).isEqualTo("home-office");
        assertThat(preferred.budget()).isEqualTo(600);
    }

    @Test
    void parsesBudgetVariantsAndRoom() {
        assertThat(parse("Spavaća soba do 1200 €, najvažniji su krevet i madrac.").budget()).isEqualTo(1200);
        assertThat(parse("Spavaća soba do 1200 €.").roomType()).isEqualTo("bedroom");
        assertThat(parse("Kućna teretana, ne preko 600.").budget()).isEqualTo(600);
        assertThat(parse("Dnevni boravak, budget 800.").budget()).isEqualTo(800);
    }

    @Test
    void parsesEuropeanThousandsSeparatorBudget() {
        // Sprint 10.91: "1.500 €" / "1 500 €" must read as 1500, not the "500" after the separator.
        assertThat(parse("Dnevni boravak do 1.500 €, treba mi kauč.").budget()).isEqualTo(1500);
        assertThat(parse("Imam 1 500 € za spavaću sobu.").budget()).isEqualTo(1500);
        assertThat(parse("Kuhinja, budžet 2.000.").budget()).isEqualTo(2000);
        // Plain (no separator) still parses unchanged.
        assertThat(parse("Dnevni boravak do 1500 €.").budget()).isEqualTo(1500);
    }

    @Test
    void basicLevelIsRecognised() {
        assertThat(parse("Spavaća soba do 500 €, samo osnovno.").furnishingLevel()).isEqualTo("basic");
    }

    @Test
    void gymPromptNoLongerMapsToHomeGym() {
        // Sprint 10.79: home-gym is de-scoped (no verified gym products), so a gym prompt must NOT yield it.
        assertThat(parse("Kućna teretana do 500 €, samo osnovno.").roomType()).isNotEqualTo("home-gym");
    }

    @Test
    void parsesColorAndMaterialPreferences() {
        PlannerInputDto parsed = parse("Dnevni boravak, želim zelene zidove, drvo i crne detalje.");

        assertThat(parsed.colorPreferences()).contains("green", "black");
        assertThat(parsed.materialPreferences()).contains("wood");
    }

    @Test
    void colorPreferencesAreEmptyWhenNoneMentioned() {
        PlannerInputDto parsed = parse("Dnevni boravak, treba mi kauč.");

        assertThat(parsed.colorPreferences()).isEmpty();
        assertThat(parsed.materialPreferences()).isEmpty();
    }

    @Test
    void recognisesNewRooms() {
        assertThat(parse("Trebam urediti kuhinju do 2000 €.").roomType()).isEqualTo("kitchen");
        assertThat(parse("Blagovaonica, treba mi stol i stolice.").roomType()).isEqualTo("dining-room");
        assertThat(parse("Mali hodnik, treba mi ormar.").roomType()).isEqualTo("hallway");
        assertThat(parse("Kupaonica, treba mi polica.").roomType()).isEqualTo("bathroom");
    }

    @Test
    void parsesDiningTableCategory() {
        assertThat(parse("Blagovaonica, treba mi blagovaonski stol.").mustHaveCategories())
                .contains("dining-table");
    }

    @Test
    void parsesKitchenAppliancesAsMustHaveAndInfersKitchen() {
        var parsed = parse("Imam 800 €, trebam pećnicu i frižider za kuhinju.");
        assertThat(parsed.mustHaveCategories()).contains("oven", "fridge");
        assertThat(parsed.roomType()).isEqualTo("kitchen");

        // An appliance alone implies the kitchen even without the word "kuhinja".
        var dishwasher = parse("Trebam perilicu posuđa do 400 €.");
        assertThat(dishwasher.mustHaveCategories()).contains("dishwasher");
        assertThat(dishwasher.roomType()).isEqualTo("kitchen");

        // "mikrovalna pećnica" is a microwave, not a full oven.
        var micro = parse("Trebam mikrovalnu pećnicu.");
        assertThat(micro.mustHaveCategories()).contains("microwave").doesNotContain("oven");
    }

    @Test
    void fallbackClassifiesRoomsInOtherMarketLanguages() {
        // Sprint 10.135: when the LLM is down/throttled/capped, the rule-based fallback must still get the room
        // right in every market's language. Before this, any non-HR/EN prompt collapsed to living-room (no bed).
        assertThat(parse("Schlafzimmer komplett einrichten, Budget 2000 €.").roomType()).isEqualTo("bedroom"); // DE
        assertThat(parse("Voglio arredare la camera da letto.").roomType()).isEqualTo("bedroom"); // IT
        assertThat(parse("Aménager ma chambre.").roomType()).isEqualTo("bedroom"); // FR
        assertThat(parse("Amueblar el salón por completo.").roomType()).isEqualTo("living-room"); // ES
        assertThat(parse("Wohnzimmer einrichten.").roomType()).isEqualTo("living-room"); // DE
        assertThat(parse("Indret stuen.").roomType()).isEqualTo("living-room"); // DK definite form
        assertThat(parse("Sisustan keittiön.").roomType()).isEqualTo("kitchen"); // FI
        assertThat(parse("Amueblar el comedor.").roomType()).isEqualTo("dining-room"); // ES
    }

    @Test
    void fallbackDetectsStudioAcrossLanguages() {
        // Sprint 10.135: studio is the combined-room container (must include a bed). The fallback now detects it
        // in every market, not just HR/EN — otherwise a throttled studio prompt shipped a bedless living room.
        assertThat(parse("Opremam garsonijeru, sve u jednom.").roomType()).isEqualTo("studio"); // HR
        assertThat(parse("Furnish my studio apartment.").roomType()).isEqualTo("studio"); // EN
        assertThat(parse("Arredare un monolocale.").roomType()).isEqualTo("studio"); // IT
        assertThat(parse("Einzimmerwohnung einrichten.").roomType()).isEqualTo("studio"); // DE
        assertThat(parse("Sisustan yksiön.").roomType()).isEqualTo("studio"); // FI
    }

    @Test
    void fallbackReadsNonEuroAndMultilingualBudgets() {
        // Sprint 10.135: kr / £ / non-HR verbs, not just "… €" (amounts stay within the default EUR ceiling 9000).
        assertThat(parse("Indret stuen, omkring 9000 kr.").budget()).isEqualTo(9000); // DK kr
        assertThat(parse("Furnish the living room, around 1800.").budget()).isEqualTo(1800); // EN verb
        assertThat(parse("Wohnzimmer, circa 2000 €.").budget()).isEqualTo(2000); // DE verb
        assertThat(parse("Living room for £1800.").budget()).isEqualTo(1800); // GBP symbol
    }

    @Test
    void absurdlyLargeGroupedBudgetDoesNotThrowAndIsIgnored() {
        // A pathological grouped number must not overflow Integer.parseInt and 500 the (unauthenticated)
        // rule-based endpoint. It is rejected as a budget, so the request keeps its default 1500 (not a crash).
        assertThat(parse("Dnevni boravak do 111.111.111.111 €, treba mi kauč.").budget()).isEqualTo(1500);
        assertThat(parse("Kuhinja, budget 999.999.999.999.").budget()).isEqualTo(1500);
        // A normal grouped budget still parses correctly (regression guard for the sane path).
        assertThat(parse("Dnevni boravak do 2.500 €.").budget()).isEqualTo(2500);
    }

    @Test
    void englishWordBadIsNotMisreadAsBathroom() {
        // German "Bad" = bathroom, but the bare token used to also match the English adjective "bad".
        assertThat(parse("My old sofa is really bad, help me furnish the living room.").roomType())
                .isEqualTo("living-room");
        // German bathroom intent still resolves in the rule-based fallback.
        assertThat(parse("Ich möchte mein Bad einrichten.").roomType()).isEqualTo("bathroom"); // determiner
        assertThat(parse("Bad renovieren, Budget 1500 €.").roomType()).isEqualTo("bathroom"); // verb
    }

    @Test
    void explicitNonDefaultRoomSelectionIsNotOverriddenByThePrompt() {
        // Sprint 10.169 regression: the pre-filled example prompt is a living-room, so a user who explicitly picks
        // Bathroom (or any non-living-room) in the UI but leaves the example text must still get THAT room — the
        // prompt-inferred room only applies while the user is on the living-room default.
        assertThat(extractor.enrich(baseWithRoom(
                "Prazan mi je dnevni boravak, treba mi kauč, TV komoda i tepih.", "bathroom")).roomType())
                .isEqualTo("bathroom");
        assertThat(extractor.enrich(baseWithRoom("Dnevni boravak, treba mi kauč.", "bedroom")).roomType())
                .isEqualTo("bedroom");
        // On the living-room default the prompt may still switch the room (the natural-language flow is preserved).
        assertThat(extractor.enrich(baseWithRoom("Zapravo spavaća soba, treba mi krevet.", "living-room")).roomType())
                .isEqualTo("bedroom");
    }

    @Test
    void aRoomMerelyInferredFromAPreviousPromptIsOverriddenByANewPrompt() {
        // Bug (2026-07-10): after generating a Bathroom plan the frontend writes the INFERRED room back into the
        // form input, so the next request arrives with roomType="bathroom". A NEW prompt naming a different room
        // (roomInferred=true) must re-derive it — the user typed "Kupaonica…" then "Spavaća soba…" and got the
        // bathroom list again. An EXPLICIT pick (roomInferred=false) is still honored (Sprint 10.169 preserved).
        assertThat(extractor.enrich(baseWithRoom("Spavaća soba za 1500 eura.", "bathroom").withRoomInferred(true)).roomType())
                .isEqualTo("bedroom");
        assertThat(extractor.enrich(baseWithRoom("Spavaća soba za 1500 eura.", "bathroom").withRoomInferred(false)).roomType())
                .isEqualTo("bathroom");
        // A new prompt that names NO room keeps the inferred room (there is nothing to re-derive to).
        assertThat(extractor.enrich(baseWithRoom("Napravi jeftiniju verziju.", "bathroom").withRoomInferred(true)).roomType())
                .isEqualTo("bathroom");
    }

    private PlannerInputDto parse(String prompt) {
        return extractor.enrich(base(prompt));
    }

    private PlannerInputDto base(String prompt) {
        return new PlannerInputDto(
                prompt, 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0
        );
    }

    private PlannerInputDto baseWithRoom(String prompt, String roomType) {
        return new PlannerInputDto(
                prompt, 1500, roomType, "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0
        );
    }
}
