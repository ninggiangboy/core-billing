package dev.ngb.tenant.customer.adapter.customer.persistence;

import static dev.ngb.tenant_jooq.Tables.CUSTOMERS;

import dev.ngb.tenant.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.tenant.customer.application.customer.query.CustomerView;
import dev.ngb.tenant.customer.domain.customer.CustomerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** The read side: selects straight into the view the customer screens render. */
public class JooqCustomerReadRepository implements CustomerReadRepository {

    private final DSLContext dsl;

    public JooqCustomerReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<CustomerView> list() {
        return dsl.select(
                        CUSTOMERS.ID,
                        CUSTOMERS.USER_ID,
                        CUSTOMERS.BILLING_CUSTOMER_ID,
                        CUSTOMERS.DISPLAY_NAME,
                        CUSTOMERS.EMAIL,
                        CUSTOMERS.STATUS,
                        CUSTOMERS.CREATED_AT)
                .from(CUSTOMERS)
                .orderBy(CUSTOMERS.CREATED_AT.asc())
                .fetch(row -> view(row.get(CUSTOMERS.ID), row));
    }

    @Override
    public Optional<CustomerView> byId(UUID id) {
        return dsl.select(
                        CUSTOMERS.ID,
                        CUSTOMERS.USER_ID,
                        CUSTOMERS.BILLING_CUSTOMER_ID,
                        CUSTOMERS.DISPLAY_NAME,
                        CUSTOMERS.EMAIL,
                        CUSTOMERS.STATUS,
                        CUSTOMERS.CREATED_AT)
                .from(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id))
                .fetchOptional(row -> view(row.get(CUSTOMERS.ID), row));
    }

    @Override
    public Optional<CustomerView> byUserId(UUID userId) {
        return dsl.select(
                        CUSTOMERS.ID,
                        CUSTOMERS.USER_ID,
                        CUSTOMERS.BILLING_CUSTOMER_ID,
                        CUSTOMERS.DISPLAY_NAME,
                        CUSTOMERS.EMAIL,
                        CUSTOMERS.STATUS,
                        CUSTOMERS.CREATED_AT)
                .from(CUSTOMERS)
                .where(CUSTOMERS.USER_ID.eq(userId))
                .fetchOptional(row -> view(row.get(CUSTOMERS.ID), row));
    }

    private static CustomerView view(UUID id, org.jooq.Record row) {
        return new CustomerView(
                id,
                row.get(CUSTOMERS.USER_ID),
                row.get(CUSTOMERS.BILLING_CUSTOMER_ID),
                row.get(CUSTOMERS.DISPLAY_NAME),
                row.get(CUSTOMERS.EMAIL),
                CustomerStatus.valueOf(row.get(CUSTOMERS.STATUS)),
                row.get(CUSTOMERS.CREATED_AT));
    }
}
