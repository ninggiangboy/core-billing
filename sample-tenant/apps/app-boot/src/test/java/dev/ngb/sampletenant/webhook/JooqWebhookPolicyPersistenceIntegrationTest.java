package dev.ngb.sampletenant.webhook;

import static dev.ngb.sampletenant_jooq.Tables.CUSTOMER_DEBTS;
import static dev.ngb.sampletenant_jooq.Tables.CUSTOMER_PERIODS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.webhook.application.event.port.CustomerPeriods;
import dev.ngb.sampletenant.webhook.application.event.port.DebtBook;
import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqWebhookPolicyPersistenceIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    DebtBook debts;

    @Autowired
    CustomerPeriods periods;

    @Autowired
    DSLContext dsl;

    @Test
    void recordsAndClearsOpenDebts() {
        String customerId = "cus_" + UUID.randomUUID();
        debts.recordDebt(customerId, 500, "USD", "declined", NOW);

        assertThat(dsl.fetchCount(CUSTOMER_DEBTS, CUSTOMER_DEBTS.BILLING_CUSTOMER_ID.eq(customerId))).isEqualTo(1);
        assertThat(dsl.fetchOne(CUSTOMER_DEBTS, CUSTOMER_DEBTS.BILLING_CUSTOMER_ID.eq(customerId))
                        .get(CUSTOMER_DEBTS.CLEARED_AT))
                .isNull();

        debts.clearOpenDebts(customerId, NOW.plusSeconds(60));

        assertThat(dsl.fetchOne(CUSTOMER_DEBTS, CUSTOMER_DEBTS.BILLING_CUSTOMER_ID.eq(customerId))
                        .get(CUSTOMER_DEBTS.CLEARED_AT))
                .isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void extendingAPeriodKeepsTheLaterEnd() {
        String customerId = "cus_" + UUID.randomUUID();
        Instant later = NOW.plusSeconds(86_400);
        periods.extend(customerId, later, NOW);
        periods.extend(customerId, NOW.plusSeconds(3_600), NOW.plusSeconds(1));

        assertThat(dsl.fetchOne(CUSTOMER_PERIODS, CUSTOMER_PERIODS.BILLING_CUSTOMER_ID.eq(customerId))
                        .get(CUSTOMER_PERIODS.PERIOD_END))
                .isEqualTo(later);
    }
}
