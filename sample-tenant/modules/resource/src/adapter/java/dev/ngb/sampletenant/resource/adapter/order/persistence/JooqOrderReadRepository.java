package dev.ngb.sampletenant.resource.adapter.order.persistence;

import static dev.ngb.sampletenant_jooq.Tables.ORDERS;

import dev.ngb.sampletenant.resource.application.order.query.OrderReadRepository;
import dev.ngb.sampletenant.resource.application.order.query.OrderView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;

/** The read side: selects straight into the view the order screens render. */
public class JooqOrderReadRepository implements OrderReadRepository {

    private final DSLContext dsl;

    public JooqOrderReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<OrderView> forCustomer(UUID customerId) {
        return dsl.select(
                        ORDERS.ID,
                        ORDERS.CUSTOMER_ID,
                        ORDERS.REFERENCE,
                        ORDERS.CATALOG_PLAN_ID,
                        ORDERS.BILLING_PLAN_ID,
                        ORDERS.STATUS,
                        ORDERS.WALLET_ID,
                        ORDERS.RESERVATION_ID,
                        ORDERS.ESTIMATED_MINOR_UNITS,
                        ORDERS.CURRENCY,
                        ORDERS.FAILURE_REASON,
                        ORDERS.CREATED_AT,
                        ORDERS.UPDATED_AT)
                .from(ORDERS)
                .where(ORDERS.CUSTOMER_ID.eq(customerId))
                .orderBy(ORDERS.CREATED_AT.desc())
                .fetch(this::view);
    }

    @Override
    public List<OrderView> forBillingCustomer(String billingCustomerId) {
        return dsl.select(
                        ORDERS.ID,
                        ORDERS.CUSTOMER_ID,
                        ORDERS.REFERENCE,
                        ORDERS.CATALOG_PLAN_ID,
                        ORDERS.BILLING_PLAN_ID,
                        ORDERS.STATUS,
                        ORDERS.WALLET_ID,
                        ORDERS.RESERVATION_ID,
                        ORDERS.ESTIMATED_MINOR_UNITS,
                        ORDERS.CURRENCY,
                        ORDERS.FAILURE_REASON,
                        ORDERS.CREATED_AT,
                        ORDERS.UPDATED_AT)
                .from(ORDERS)
                .where(ORDERS.BILLING_CUSTOMER_ID.eq(billingCustomerId))
                .orderBy(ORDERS.CREATED_AT.desc())
                .fetch(this::view);
    }

    @Override
    public List<OrderView> all() {
        return dsl.select(
                        ORDERS.ID,
                        ORDERS.CUSTOMER_ID,
                        ORDERS.REFERENCE,
                        ORDERS.CATALOG_PLAN_ID,
                        ORDERS.BILLING_PLAN_ID,
                        ORDERS.STATUS,
                        ORDERS.WALLET_ID,
                        ORDERS.RESERVATION_ID,
                        ORDERS.ESTIMATED_MINOR_UNITS,
                        ORDERS.CURRENCY,
                        ORDERS.FAILURE_REASON,
                        ORDERS.CREATED_AT,
                        ORDERS.UPDATED_AT)
                .from(ORDERS)
                .orderBy(ORDERS.CREATED_AT.desc())
                .fetch(this::view);
    }

    @Override
    public Optional<OrderView> byId(UUID id) {
        return dsl.select(
                        ORDERS.ID,
                        ORDERS.CUSTOMER_ID,
                        ORDERS.REFERENCE,
                        ORDERS.CATALOG_PLAN_ID,
                        ORDERS.BILLING_PLAN_ID,
                        ORDERS.STATUS,
                        ORDERS.WALLET_ID,
                        ORDERS.RESERVATION_ID,
                        ORDERS.ESTIMATED_MINOR_UNITS,
                        ORDERS.CURRENCY,
                        ORDERS.FAILURE_REASON,
                        ORDERS.CREATED_AT,
                        ORDERS.UPDATED_AT)
                .from(ORDERS)
                .where(ORDERS.ID.eq(id))
                .fetchOptional(this::view);
    }

    private OrderView view(Record row) {
        return new OrderView(
                row.get(ORDERS.ID),
                row.get(ORDERS.CUSTOMER_ID),
                row.get(ORDERS.REFERENCE),
                row.get(ORDERS.CATALOG_PLAN_ID),
                row.get(ORDERS.BILLING_PLAN_ID),
                row.get(ORDERS.STATUS),
                row.get(ORDERS.WALLET_ID),
                row.get(ORDERS.RESERVATION_ID),
                row.get(ORDERS.ESTIMATED_MINOR_UNITS),
                row.get(ORDERS.CURRENCY),
                row.get(ORDERS.FAILURE_REASON),
                row.get(ORDERS.CREATED_AT),
                row.get(ORDERS.UPDATED_AT));
    }
}
