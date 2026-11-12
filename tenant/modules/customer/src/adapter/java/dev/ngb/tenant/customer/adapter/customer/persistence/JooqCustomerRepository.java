package dev.ngb.tenant.customer.adapter.customer.persistence;

import static dev.ngb.tenant_jooq.Tables.CUSTOMERS;

import dev.ngb.tenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.tenant.customer.domain.customer.Customer;
import dev.ngb.tenant.customer.domain.customer.CustomerStatus;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The write side: upsert and the lookups registration and the listener decide with. */
public class JooqCustomerRepository implements CustomerRepository {

    private final DSLContext dsl;

    public JooqCustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(Customer customer) {
        dsl.insertInto(CUSTOMERS)
                .columns(
                        CUSTOMERS.ID,
                        CUSTOMERS.USER_ID,
                        CUSTOMERS.BILLING_CUSTOMER_ID,
                        CUSTOMERS.DISPLAY_NAME,
                        CUSTOMERS.EMAIL,
                        CUSTOMERS.STATUS,
                        CUSTOMERS.CREATED_AT)
                .values(
                        customer.id(),
                        customer.userId(),
                        customer.billingCustomerId(),
                        customer.displayName(),
                        customer.email(),
                        customer.status().name(),
                        customer.createdAt())
                .onConflict(CUSTOMERS.ID)
                .doUpdate()
                .set(CUSTOMERS.BILLING_CUSTOMER_ID, DSL.excluded(CUSTOMERS.BILLING_CUSTOMER_ID))
                .set(CUSTOMERS.DISPLAY_NAME, DSL.excluded(CUSTOMERS.DISPLAY_NAME))
                .set(CUSTOMERS.EMAIL, DSL.excluded(CUSTOMERS.EMAIL))
                .set(CUSTOMERS.STATUS, DSL.excluded(CUSTOMERS.STATUS))
                .execute();
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return dsl.selectFrom(CUSTOMERS).where(CUSTOMERS.ID.eq(id)).fetchOptional().map(JooqCustomerRepository::toDomain);
    }

    @Override
    public Optional<Customer> findByUserId(UUID userId) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.USER_ID.eq(userId))
                .fetchOptional()
                .map(JooqCustomerRepository::toDomain);
    }

    private static Customer toDomain(dev.ngb.tenant_jooq.tables.records.CustomersRecord row) {
        return new Customer(
                row.getId(),
                row.getUserId(),
                row.getBillingCustomerId(),
                row.getDisplayName(),
                row.getEmail(),
                CustomerStatus.valueOf(row.getStatus()),
                row.getCreatedAt());
    }
}
