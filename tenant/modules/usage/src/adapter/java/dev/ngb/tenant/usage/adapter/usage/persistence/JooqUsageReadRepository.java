package dev.ngb.tenant.usage.adapter.usage.persistence;

import static dev.ngb.tenant_jooq.Tables.USAGE_RECORDS;

import dev.ngb.tenant.usage.application.usage.query.UsageReadRepository;
import dev.ngb.tenant.usage.application.usage.query.UsageRecordView;
import java.util.List;
import org.jooq.DSLContext;

/** The read side: selects straight into the view the controller renders. */
public class JooqUsageReadRepository implements UsageReadRepository {

    private final DSLContext dsl;

    public JooqUsageReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<UsageRecordView> forBillingCustomer(String billingCustomerId) {
        return dsl.select(
                        USAGE_RECORDS.ID,
                        USAGE_RECORDS.ORDER_ID,
                        USAGE_RECORDS.METRIC,
                        USAGE_RECORDS.QUANTITY,
                        USAGE_RECORDS.UNIT,
                        USAGE_RECORDS.BILLING_EVENT_ID,
                        USAGE_RECORDS.OCCURRED_AT,
                        USAGE_RECORDS.RECORDED_AT)
                .from(USAGE_RECORDS)
                .where(USAGE_RECORDS.BILLING_CUSTOMER_ID.eq(billingCustomerId))
                .orderBy(USAGE_RECORDS.OCCURRED_AT.desc())
                .fetch(row -> new UsageRecordView(
                        row.get(USAGE_RECORDS.ID),
                        row.get(USAGE_RECORDS.ORDER_ID),
                        row.get(USAGE_RECORDS.METRIC),
                        row.get(USAGE_RECORDS.QUANTITY),
                        row.get(USAGE_RECORDS.UNIT),
                        row.get(USAGE_RECORDS.BILLING_EVENT_ID),
                        row.get(USAGE_RECORDS.OCCURRED_AT),
                        row.get(USAGE_RECORDS.RECORDED_AT)));
    }
}
