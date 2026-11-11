package dev.ngb.core_billing.customer.adapter.customer.persistence;

import static dev.ngb.core_billing_jooq.Tables.CUSTOMERS;
import static dev.ngb.core_billing_jooq.Tables.CUSTOMER_MAPPINGS;

import dev.ngb.core_billing.customer.application.customer.port.CustomerRepository;
import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.CustomerStatus;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.util.Causes;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqCustomerRepository implements CustomerRepository {

    private final DSLContext dsl;

    public JooqCustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean addIfAbsent(Customer customer) {
        try {
            int inserted = dsl.insertInto(CUSTOMERS)
                    .set(CUSTOMERS.ID, customer.id().value())
                    .set(CUSTOMERS.TENANT_ID, customer.tenantId().value())
                    .set(CUSTOMERS.DISPLAY_NAME, customer.displayName())
                    .set(CUSTOMERS.EMAIL, customer.email())
                    .set(CUSTOMERS.STATUS, customer.status().name())
                    .set(CUSTOMERS.IDEMPOTENCY_KEY, customer.idempotencyKey())
                    .set(CUSTOMERS.REQUEST_HASH, customer.requestHash())
                    .set(CUSTOMERS.CREATED_AT, customer.createdAt())
                    .onConflict(CUSTOMERS.TENANT_ID, CUSTOMERS.IDEMPOTENCY_KEY)
                    .doNothing()
                    .execute();
            if (inserted == 0) {
                return false;
            }
            insertMappings(customer);
            return true;
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this external user is already mapped to a customer");
            }
            throw e;
        }
    }

    @Override
    public void update(Customer customer) {
        dsl.update(CUSTOMERS)
                .set(CUSTOMERS.DISPLAY_NAME, customer.displayName())
                .set(CUSTOMERS.EMAIL, customer.email())
                .set(CUSTOMERS.STATUS, customer.status().name())
                .where(CUSTOMERS.ID.eq(customer.id().value()))
                .execute();
        dsl.deleteFrom(CUSTOMER_MAPPINGS).where(CUSTOMER_MAPPINGS.CUSTOMER_ID.eq(customer.id().value())).execute();
        insertMappings(customer);
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id.value()))
                .fetchOptional(row -> restore(row, identities(id)));
    }

    @Override
    public Optional<Customer> findByIdempotencyKey(String idempotencyKey) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(row -> restore(row, identities(new CustomerId(row.getId()))));
    }

    private static Customer restore(org.jooq.Record row, Set<ExternalIdentity> identities) {
        return Customer.restore(
                new CustomerId(row.get(CUSTOMERS.ID)),
                new TenantId(row.get(CUSTOMERS.TENANT_ID)),
                row.get(CUSTOMERS.DISPLAY_NAME),
                row.get(CUSTOMERS.EMAIL),
                CustomerStatus.valueOf(row.get(CUSTOMERS.STATUS)),
                row.get(CUSTOMERS.CREATED_AT),
                row.get(CUSTOMERS.IDEMPOTENCY_KEY),
                row.get(CUSTOMERS.REQUEST_HASH),
                identities);
    }

    @Override
    public Optional<Customer> findByExternalIdentity(ExternalIdentity identity) {
        return dsl.select(CUSTOMER_MAPPINGS.CUSTOMER_ID)
                .from(CUSTOMER_MAPPINGS)
                .where(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(identity.tenantSystemId().value()))
                .and(CUSTOMER_MAPPINGS.EXTERNAL_USER_ID.eq(identity.externalUserId()))
                .fetchOptional(CUSTOMER_MAPPINGS.CUSTOMER_ID)
                .flatMap(id -> findById(new CustomerId(id)));
    }

    @Override
    public boolean isMappedTo(CustomerId id, TenantSystemId tenantSystem) {
        return dsl.fetchExists(
                CUSTOMER_MAPPINGS,
                CUSTOMER_MAPPINGS.CUSTOMER_ID.eq(id.value()).and(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(tenantSystem.value())));
    }

    private Set<ExternalIdentity> identities(CustomerId id) {
        return dsl.select(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID, CUSTOMER_MAPPINGS.EXTERNAL_USER_ID)
                .from(CUSTOMER_MAPPINGS)
                .where(CUSTOMER_MAPPINGS.CUSTOMER_ID.eq(id.value()))
                .fetch(row -> new ExternalIdentity(new TenantSystemId(row.value1()), row.value2()))
                .stream()
                .collect(Collectors.toSet());
    }

    private void insertMappings(Customer customer) {
        if (customer.identities().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(
                CUSTOMER_MAPPINGS,
                CUSTOMER_MAPPINGS.TENANT_ID,
                CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID,
                CUSTOMER_MAPPINGS.EXTERNAL_USER_ID,
                CUSTOMER_MAPPINGS.CUSTOMER_ID);
        for (ExternalIdentity identity : customer.identities()) {
            insert = insert.values(
                    customer.tenantId().value(), identity.tenantSystemId().value(), identity.externalUserId(), customer.id().value());
        }
        insert.execute();
    }
}
