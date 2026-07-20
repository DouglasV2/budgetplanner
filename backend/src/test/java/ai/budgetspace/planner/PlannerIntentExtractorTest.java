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
    void retailerIntentFromPromptHardExcludesWithMisspellingsAndDeclensions() {
        // Sprint 10.186: the AI path routes the raw prompt through this. "ne želim IKEA" must be a HARD exclude, and
        // a typo/declension ("IKEE", "iz JYSKa", "neću ništa iz…") must still be recognised as the store.
        assertThat(retailerIntent("Dnevni boravak 1500e, ne želim IKEA").excludedRetailers()).contains("IKEA");
        assertThat(retailerIntent("Living room 1500, no IKEA please").excludedRetailers()).contains("IKEA");
        assertThat(retailerIntent("dnevni boravak, bez IKEE").excludedRetailers()).contains("IKEA"); // typo/genitive
        assertThat(retailerIntent("Spavaća 1200e, neću ništa iz JYSKa").excludedRetailers()).contains("JYSK");
    }

    @Test
    void retailerIntentFromPromptOnlyOneStoreRestricts() {
        PlannerInputDto only = retailerIntent("spavaća soba 1500e, samo JYSK");
        assertThat(only.selectedRetailers()).containsExactly("JYSK");
        assertThat(only.retailerMode()).isEqualTo("single");
        // Sprint 10.186: "only"/"nur" restrict too — the app is multilingual (the live audit caught English leaking).
        assertThat(retailerIntent("bedroom 1500, only JYSK").selectedRetailers()).containsExactly("JYSK");
        assertThat(retailerIntent("Schlafzimmer 1500, nur JYSK").selectedRetailers()).containsExactly("JYSK");
    }

    private PlannerInputDto retailerIntent(String prompt) {
        return extractor.applyRetailerIntentFromPrompt(prompt, new PlannerInputDto(
                "", 1500, "living-room", "modern", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0));
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

    // Sprint 10.183: the everyday way to ask for a good-looking room — "da bude lijepo", "lijepa soba",
    // "neka bude ljepše", "dopadljivo" — must set the aesthetic goal so the planner spends up on nicer,
    // better-rated, style-coherent pieces. Previously ONLY "najljepše/estetski/što ljepše/ljepša verzija"
    // did, so the most natural phrasing silently fell back to the default best-value (cheapest-leaning) tier.
    @Test
    void plainNicePhrasingSignalsAestheticPriority() {
        assertThat(parse("Dnevni boravak 2000 €, da bude lijepo.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Uredi mi lijepu dnevnu sobu.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Dnevni boravak, neka bude ljepše.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Spavaća soba, želim nešto dopadljivo.").optimizationGoal()).isEqualTo("style-match");
        // Regression: a plain value/budget prompt stays the default best-value tier, not style-match.
        assertThat(parse("Dnevni boravak do 1500 €, treba mi kauč.").optimizationGoal()).isEqualTo("best-value");
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

    // Sprint 10.179: the utility rooms (garage / pantry / laundry / attic / basement). A garage prompt no longer
    // silently becomes a living-room plan. `terasa` stays unmapped (no outdoor stock) — it must NOT be mis-routed.
    @Test
    void recognisesUtilityRooms() {
        assertThat(parse("Opremi mi garažu do 1000 €.").roomType()).isEqualTo("garage");
        assertThat(parse("Trebam police za radionicu.").roomType()).isEqualTo("garage");
        assertThat(parse("Sredi mi ostavu, police i rasvjeta.").roomType()).isEqualTo("pantry");
        assertThat(parse("Špajz, trebam police.").roomType()).isEqualTo("pantry");
        assertThat(parse("Uredi praonicu, treba mi košara za rublje.").roomType()).isEqualTo("laundry");
        assertThat(parse("Veseraj do 500 €.").roomType()).isEqualTo("laundry");
        assertThat(parse("Tavan, samo police i svjetlo.").roomType()).isEqualTo("attic");
        assertThat(parse("Podrum, trebam regale.").roomType()).isEqualTo("basement");
        assertThat(parse("Opremi mi terasu do 1000 €.").roomType())
                .isNotIn("garage", "pantry", "laundry", "attic", "basement");
        // "dostava" (delivery) must not be mis-read as "ostava" (pantry).
        assertThat(parse("Dnevni boravak, važna mi je besplatna dostava.").roomType()).isEqualTo("living-room");
    }

    // --- Sprint 10.189 multilingual synonym audit (2026-07-18): every gap below is a real native word a user in a
    // live market types, that used to silently fall back to a wrong/default value. Each positive is paired, where the
    // audit found one, with a false-positive guard for the exact over-match its adversarial pass surfaced. ---

    // --- Sprint 10.190: negation. A preference the user explicitly ruled out must never be applied. Every
    // negated case is paired with its affirmative twin, so a suppression bug can't hide as a passing test. ---

    @Test
    void negatedPreferencesAreNotApplied() {
        assertThat(parse("Dnevni boravak, ne želim tamno.").style()).isNotEqualTo("industrial");
        assertThat(parse("Dnevni boravak, tamno i metalno.").style()).isEqualTo("industrial");

        assertThat(parse("Living room, not too basic please.").furnishingLevel()).isNotEqualTo("basic");
        assertThat(parse("Living room, just the basics.").furnishingLevel()).isEqualTo("basic");

        assertThat(parse("Dnevni boravak, bez crne boje.").colorPreferences()).doesNotContain("black");
        assertThat(parse("Dnevni boravak, crna boja.").colorPreferences()).contains("black");

        assertThat(parse("Uredi mi dnevni boravak, ne spavaću sobu.").roomType()).isEqualTo("living-room");
    }

    @Test
    void negatedCheapMeansQualityNotLowestPrice() {
        // The one deliberate inversion: "I don't want it cheap" is a quality signal, not a no-op.
        assertThat(parse("Dnevni boravak 2000 €, neću da bude jeftino.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Wohnzimmer 2000, keine billigen Möbel.").optimizationGoal()).isEqualTo("style-match");
        assertThat(parse("Living room 2000, nothing cheap.").optimizationGoal()).isEqualTo("style-match");
    }

    @Test
    void notTooModernBecomesAModernClassicBlend() {
        // "ne previše moderno" is a DEGREE phrase, not a plain negation — the user wants a softer modern, so the
        // style survives and picks up its complement instead of being dropped.
        PlannerInputDto soft = parse("Dnevni boravak 2000 €, ne previše moderno.");
        assertThat(soft.style()).isEqualTo("modern");
        assertThat(soft.secondaryStyles()).containsExactly("classic");

        assertThat(parse("Living room, not too modern.").secondaryStyles()).containsExactly("classic");     // EN
        assertThat(parse("Wohnzimmer, nicht zu modern.").secondaryStyles()).containsExactly("classic");     // DE
        assertThat(parse("Soggiorno, non troppo moderno.").secondaryStyles()).containsExactly("classic");   // IT
        assertThat(parse("Salón, no demasiado moderno.").secondaryStyles()).containsExactly("classic");     // ES
        assertThat(parse("Spavaća soba, ne previše minimalistički.").secondaryStyles()).containsExactly("warm");

        // A plain style request stays single...
        assertThat(parse("Dnevni boravak, moderno.").style()).isEqualTo("modern");
        assertThat(parse("Dnevni boravak, moderno.").secondaryStyles()).isEmpty();
        // ...and a softener aimed at something that is NOT a style leaves the blend alone.
        assertThat(parse("Living room, not too basic please.").secondaryStyles()).isEmpty();
    }

    @Test
    void cheaperIsNotTheSameAsCheapest() {
        // Comparative / plain → a lower price band, still quality-ranked.
        assertThat(parse("Dnevni boravak, može malo jeftinije.").optimizationGoal()).isEqualTo("lower-price");
        assertThat(parse("Living room, something cheaper.").optimizationGoal()).isEqualTo("lower-price");
        assertThat(parse("Wohnzimmer, günstiger bitte.").optimizationGoal()).isEqualTo("lower-price");
        assertThat(parse("Salón, algo barato.").optimizationGoal()).isEqualTo("lower-price");
        // Superlative → the floor.
        assertThat(parse("Dnevni boravak, što jeftinije.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Just get me the cheapest sofa.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("So günstig wie möglich einrichten.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Lo más barato posible.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Le moins cher possible.").optimizationGoal()).isEqualTo("lowest-price");
    }

    @Test
    void doubleNegationTurnsAnExcludeIntoAPreference() {
        // "not without IKEA" means WITH IKEA — it must not exclude the store.
        PlannerInputDto de = retailerIntent("Wohnzimmer 2000, nicht ohne IKEA.");
        assertThat(de.excludedRetailers()).doesNotContain("IKEA");
        assertThat(de.preferredRetailers()).contains("IKEA");

        PlannerInputDto hr = retailerIntent("Dnevni boravak, ne bez IKEA.");
        assertThat(hr.excludedRetailers()).doesNotContain("IKEA");
        assertThat(hr.preferredRetailers()).contains("IKEA");

        // Single negation still excludes.
        assertThat(retailerIntent("Wohnzimmer 2000, ohne IKEA.").excludedRetailers()).contains("IKEA");
    }

    @Test
    void affirmativePathIsUnchangedByTheNegationScope() {
        // Regression guard: the positive signals from 10.189 must all still fire.
        assertThat(parse("So günstig wie möglich einrichten.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Le moins cher possible.").optimizationGoal()).isEqualTo("lowest-price");
        assertThat(parse("Make the bedroom cosy.").style()).isEqualTo("warm");
        assertThat(parse("Dnevni boravak, ne želim više od dvije trgovine.").maxStores()).isEqualTo(2);
        assertThat(parse("Dnevni boravak do 900 €, bez tepiha.").alreadyHaveCategories()).contains("rug");
    }

    @Test
    void recognisesUtilityRoomsAcrossLanguages() {
        assertThat(parse("Quiero amueblar mi garaje, unas estanterías.").roomType()).isEqualTo("garage");    // ES
        assertThat(parse("Sisusta autotalli, hyllyt ja työpöytä.").roomType()).isEqualTo("garage");          // FI
        assertThat(parse("Innrede garasjen min, hyller.").roomType()).isEqualTo("garage");                    // NO
        assertThat(parse("Organizar la despensa con baldas.").roomType()).isEqualTo("pantry");                // ES
        assertThat(parse("Vorrei arredare la lavanderia, scaffali.").roomType()).isEqualTo("laundry");        // IT
        assertThat(parse("Inreda tvättstugan, hyllor.").roomType()).isEqualTo("laundry");                     // SE
        assertThat(parse("Amueblar el sótano, estanterías.").roomType()).isEqualTo("basement");               // ES
        assertThat(parse("Sisusta kellari, hyllyt.").roomType()).isEqualTo("basement");                        // FI
        assertThat(parse("Arredare la mansarda, scaffalature.").roomType()).isEqualTo("attic");               // IT
        assertThat(parse("Mobilar o corredor, um aparador.").roomType()).isEqualTo("hallway");                // PT
    }

    @Test
    void utilityRoomSynonymsDoNotOverTrigger() {
        // PT "escorredor de louça" (a dish rack) must NOT reclassify a kitchen prompt to hallway (\bcorredor\b is bounded).
        assertThat(parse("Cozinha: armários e um escorredor de louça.").roomType()).isEqualTo("kitchen");
        // IT ceiling lamp "lampada da soffitto" must NOT be read as attic ("soffitt[ae]" excludes the -o ceiling word).
        assertThat(parse("Arredare il salotto con una lampada da soffitto.").roomType()).isNotEqualTo("attic");
    }

    @Test
    void recognisesFurnitureCategoriesAcrossLanguages() {
        assertThat(parse("Ich brauche einen Stuhl und einen Teppich fürs Wohnzimmer.").mustHaveCategories())
                .contains("chair", "rug");                                                                     // DE
        assertThat(parse("Ich brauche einen Schreibtisch fürs Arbeitszimmer.").mustHaveCategories())
                .contains("desk");                                                                             // DE
        assertThat(parse("Necesito una silla y una alfombra para el salón.").mustHaveCategories())
                .contains("chair", "rug");                                                                     // ES
        assertThat(parse("Il me faut un canapé, une chaise et un tapis.").mustHaveCategories())
                .contains("sofa", "chair", "rug");                                                             // FR
        assertThat(parse("J'ai besoin d'une armoire pour la chambre.").mustHaveCategories())
                .contains("wardrobe");                                                                         // FR
        assertThat(parse("Mi serve un divano, una sedia e un tappeto.").mustHaveCategories())
                .contains("sofa", "chair", "rug");                                                             // IT
        assertThat(parse("Tarvitsen sohvan olohuoneeseen.").mustHaveCategories()).contains("sofa");            // FI
        assertThat(parse("I need a rug and a carpet for the living room.").mustHaveCategories()).contains("rug"); // EN
    }

    @Test
    void categorySynonymsDoNotOverTrigger() {
        // FI "sohvapöytä" is a COFFEE TABLE, not a sofa — the sohva(?!p) lookahead must keep it out of the sofa bucket.
        assertThat(parse("Tarvitsen sohvapöydän olohuoneeseen.").mustHaveCategories()).doesNotContain("sofa");
        // FR "armoire de toilette" is a bathroom cabinet, not a bedroom wardrobe.
        assertThat(parse("J'ai besoin d'une armoire de toilette pour la salle de bain.").mustHaveCategories())
                .doesNotContain("wardrobe");
    }

    @Test
    void recognisesLevelGoalAndStyleAcrossLanguages() {
        assertThat(parse("Die Wohnung komplett einrichten, Budget 3000.").furnishingLevel()).isEqualTo("complete"); // DE
        assertThat(parse("I want the flat fully furnished.").furnishingLevel()).isEqualTo("complete");              // EN
        assertThat(parse("Voglio un arredamento completo.").furnishingLevel()).isEqualTo("complete");              // IT
        assertThat(parse("Just the basics for now.").furnishingLevel()).isEqualTo("basic");                         // EN
        assertThat(parse("Das Wohnzimmer soll gemütlich sein.").style()).isEqualTo("warm");                        // DE
        assertThat(parse("Make the bedroom cosy.").style()).isEqualTo("warm");                                      // EN (GB)
        assertThat(parse("Klassischer Stil bitte.").style()).isEqualTo("classic");                                 // DE
        assertThat(parse("So günstig wie möglich einrichten.").optimizationGoal()).isEqualTo("lowest-price");      // DE
        assertThat(parse("Just get me the cheapest sofa.").optimizationGoal()).isEqualTo("lowest-price");          // EN
        assertThat(parse("Lo más barato posible.").optimizationGoal()).isEqualTo("lowest-price");                  // ES
        assertThat(parse("Le moins cher possible.").optimizationGoal()).isEqualTo("lowest-price");                 // FR
        assertThat(parse("Make the room beautiful, budget 2000.").optimizationGoal()).isEqualTo("style-match");    // EN
        assertThat(parse("Que quede bonito.").optimizationGoal()).isEqualTo("style-match");                        // ES
    }

    @Test
    void recognisesRetailerIntentAcrossLanguages() {
        assertThat(retailerIntent("Wohnzimmer 2000, ohne IKEA bitte.").excludedRetailers()).contains("IKEA");   // DE
        assertThat(retailerIntent("Sala de estar 1500, sem IKEA.").excludedRetailers()).contains("IKEA");        // PT
        assertThat(retailerIntent("Stue 8000 kr, ingen IKEA takk.").excludedRetailers()).contains("IKEA");       // NO
        assertThat(retailerIntent("Salón 1800, prefiero IKEA.").preferredRetailers()).contains("IKEA");          // ES
        assertThat(retailerIntent("Quarto 1200, prefiro IKEA.").preferredRetailers()).contains("IKEA");          // PT
        assertThat(retailerIntent("Für das Büro 900, ich bevorzuge JYSK.").preferredRetailers()).contains("JYSK"); // DE
        assertThat(retailerIntent("Woonkamer 1500, ik wil liever IKEA.").preferredRetailers()).contains("IKEA");  // NL
        assertThat(retailerIntent("Wohnzimmer 2500, maximal zwei Geschäfte.").maxStores()).isEqualTo(2);          // DE
        assertThat(retailerIntent("Soggiorno 2000, massimo due negozi.").maxStores()).isEqualTo(2);               // IT
        assertThat(retailerIntent("Dormitorio 1500, dos tiendas como máximo.").maxStores()).isEqualTo(2);         // ES
        assertThat(retailerIntent("Želim sve iz jednog dućana, blizu mi je.").maxStores()).isEqualTo(1);          // HR
        assertThat(retailerIntent("Vardagsrum 8000 kr, allt från en affär.").maxStores()).isEqualTo(1);          // SE
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
