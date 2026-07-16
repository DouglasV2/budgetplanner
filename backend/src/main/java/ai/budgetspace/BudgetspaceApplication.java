package ai.budgetspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives the background crons (retention cleanup, catalog freshness re-check, catalog audit,
// billing reconciliation). Each is off-by-default or dormant unless configured, so nothing runs by surprise.
@SpringBootApplication
@EnableScheduling
public class BudgetspaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BudgetspaceApplication.class, args);
    }
}
