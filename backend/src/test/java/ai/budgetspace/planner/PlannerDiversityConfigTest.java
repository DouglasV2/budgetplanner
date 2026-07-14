package ai.budgetspace.planner;

import ai.budgetspace.feed.EbayBrowseFeed;
import ai.budgetspace.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Sprint 10.186 — closes the test/prod gap for the retailer-diversity flag. The diversity tie-break is field-injected
 * ({@code @Value("${budgetspace.planner.retailer-diversity:true}")}), which keeps the ~30 {@code new PlannerService(repo)}
 * UNIT tests on the legacy path (a Java boolean field defaults to false when not Spring-wired). That is convenient but
 * means those tests do NOT exercise the code that actually runs in production. This test boots a real Spring context,
 * lets Spring instantiate the {@code PlannerService} bean through its {@code @Autowired} constructor, and asserts the
 * property binding: diversity is ON by default in the application context, and a config override can turn it off.
 */
class PlannerDiversityConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ProductRepository.class, () -> mock(ProductRepository.class))
            .withBean(EbayBrowseFeed.class, () -> mock(EbayBrowseFeed.class))
            .withBean(PlannerService.class);

    @Test
    void retailerDiversityDefaultsOnWhenSpringWiresThePlanner() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PlannerService.class);
            assertThat(diversityFlagOf(context.getBean(PlannerService.class)))
                    .as("retailer-diversity @Value must resolve to TRUE by default in a real Spring context — i.e. the "
                            + "production planner uses the new selection, not the legacy path the unit tests run on")
                    .isTrue();
        });
    }

    @Test
    void retailerDiversityCanBeDisabledByConfig() {
        contextRunner.withPropertyValues("budgetspace.planner.retailer-diversity=false").run(context ->
                assertThat(diversityFlagOf(context.getBean(PlannerService.class))).isFalse());
    }

    private static boolean diversityFlagOf(PlannerService planner) throws Exception {
        Field field = PlannerService.class.getDeclaredField("retailerDiversity");
        field.setAccessible(true);
        return field.getBoolean(planner);
    }
}
