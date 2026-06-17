package ai.budgetspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives the Sprint 10.34 price-watch re-check (PriceWatchRecheckService), which is a
// no-op unless budgetspace.price-watch.recheck-enabled=true, so nothing fetches by surprise in dev/prod.
@SpringBootApplication
@EnableScheduling
public class BudgetspaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BudgetspaceApplication.class, args);
    }
}
