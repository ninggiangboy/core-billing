package dev.ngb.tenant.webhook.adapter.event.persistence;

import static dev.ngb.tenant_jooq.Tables.CUSTOMER_PERIODS;

import dev.ngb.tenant.webhook.application.event.port.CustomerPeriods;
import java.time.Instant;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The tenant's own service period, extended by a paid subscription renewal. */
public class JooqCustomerPeriods implements CustomerPeriods {

    private final DSLContext dsl;

    public JooqCustomerPeriods(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void extend(String billingCustomerId, Instant periodEnd, Instant now) {
        dsl.insertInto(CUSTOMER_PERIODS)
                .columns(CUSTOMER_PERIODS.BILLING_CUSTOMER_ID, CUSTOMER_PERIODS.PERIOD_END, CUSTOMER_PERIODS.UPDATED_AT)
                .values(billingCustomerId, periodEnd, now)
                .onConflict(CUSTOMER_PERIODS.BILLING_CUSTOMER_ID)
                .doUpdate()
                .set(
                        CUSTOMER_PERIODS.PERIOD_END,
                        DSL.greatest(CUSTOMER_PERIODS.PERIOD_END, DSL.excluded(CUSTOMER_PERIODS.PERIOD_END)))
                .set(CUSTOMER_PERIODS.UPDATED_AT, DSL.excluded(CUSTOMER_PERIODS.UPDATED_AT))
                .execute();
    }
}
