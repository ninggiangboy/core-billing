package dev.ngb.sampletenant.webhook.adapter.event.persistence;

import static dev.ngb.sampletenant_jooq.Tables.CUSTOMER_DEBTS;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.webhook.application.event.port.DebtBook;
import java.time.Instant;
import org.jooq.DSLContext;

/** The tenant's own debt book, fed by Billing's payment failures. */
public class JooqDebtBook implements DebtBook {

    private final DSLContext dsl;
    private final IdGenerator ids;

    public JooqDebtBook(DSLContext dsl, IdGenerator ids) {
        this.dsl = dsl;
        this.ids = ids;
    }

    @Override
    public void recordDebt(String billingCustomerId, long minorUnits, String currency, String reason, Instant occurredAt) {
        dsl.insertInto(CUSTOMER_DEBTS)
                .columns(
                        CUSTOMER_DEBTS.ID,
                        CUSTOMER_DEBTS.BILLING_CUSTOMER_ID,
                        CUSTOMER_DEBTS.MINOR_UNITS,
                        CUSTOMER_DEBTS.CURRENCY,
                        CUSTOMER_DEBTS.REASON,
                        CUSTOMER_DEBTS.OCCURRED_AT)
                .values(ids.newId(), billingCustomerId, minorUnits, currency, reason, occurredAt)
                .execute();
    }

    @Override
    public void clearOpenDebts(String billingCustomerId, Instant clearedAt) {
        dsl.update(CUSTOMER_DEBTS)
                .set(CUSTOMER_DEBTS.CLEARED_AT, clearedAt)
                .where(CUSTOMER_DEBTS.BILLING_CUSTOMER_ID.eq(billingCustomerId)
                        .and(CUSTOMER_DEBTS.CLEARED_AT.isNull()))
                .execute();
    }
}
