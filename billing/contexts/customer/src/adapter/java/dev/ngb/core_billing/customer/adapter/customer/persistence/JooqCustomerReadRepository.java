package dev.ngb.core_billing.customer.adapter.customer.persistence;

import static dev.ngb.core_billing_jooq.Tables.CUSTOMERS;
import static dev.ngb.core_billing_jooq.Tables.CUSTOMER_MAPPINGS;
import static org.jooq.impl.DSL.any;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.selectOne;

import dev.ngb.core_billing.customer.application.customer.query.CustomerIdentityView;
import dev.ngb.core_billing.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.core_billing.customer.application.customer.query.CustomerView;
import dev.ngb.core_billing.customer.domain.customer.CustomerStatus;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing_jooq.tables.records.CustomersRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record3;

/** Selects customers and their mappings straight into views; the aggregate is never built. */
public class JooqCustomerReadRepository implements CustomerReadRepository {

    private final DSLContext dsl;

    public JooqCustomerReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<CustomerView> list() {
        List<CustomersRecord> rows = dsl.selectFrom(CUSTOMERS).orderBy(CUSTOMERS.CREATED_AT, CUSTOMERS.ID).fetch();
        return withIdentities(rows, null);
    }

    @Override
    public List<CustomerView> listMappedTo(UUID tenantSystemId) {
        List<CustomersRecord> rows = dsl.select(CUSTOMERS.fields())
                .from(CUSTOMERS)
                .join(CUSTOMER_MAPPINGS)
                .on(CUSTOMER_MAPPINGS.CUSTOMER_ID.eq(CUSTOMERS.ID).and(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(tenantSystemId)))
                .orderBy(CUSTOMERS.CREATED_AT, CUSTOMERS.ID)
                .fetchInto(CUSTOMERS);
        return withIdentities(rows, tenantSystemId);
    }

    @Override
    public Optional<CustomerView> findById(CustomerId id) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id.value()))
                .fetchOptional()
                .map(row -> withIdentities(List.of(row), null).getFirst());
    }

    @Override
    public Optional<CustomerView> findByIdMappedTo(CustomerId id, UUID tenantSystemId) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id.value()))
                .and(exists(selectOne()
                        .from(CUSTOMER_MAPPINGS)
                        .where(CUSTOMER_MAPPINGS.CUSTOMER_ID.eq(CUSTOMERS.ID))
                        .and(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(tenantSystemId))))
                .fetchOptional()
                .map(row -> withIdentities(List.of(row), tenantSystemId).getFirst());
    }

    @Override
    public Optional<CustomerView> findByExternalIdentity(UUID tenantSystemId, String externalUserId) {
        return dsl.select(CUSTOMER_MAPPINGS.CUSTOMER_ID)
                .from(CUSTOMER_MAPPINGS)
                .where(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .and(CUSTOMER_MAPPINGS.EXTERNAL_USER_ID.eq(externalUserId))
                .fetchOptional(CUSTOMER_MAPPINGS.CUSTOMER_ID)
                .flatMap(id -> findByIdMappedTo(new CustomerId(id), tenantSystemId));
    }

    @Override
    public List<UUID> mappedCustomerIds(UUID tenantSystemId) {
        return dsl.selectDistinct(CUSTOMER_MAPPINGS.CUSTOMER_ID)
                .from(CUSTOMER_MAPPINGS)
                .where(CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .fetch(CUSTOMER_MAPPINGS.CUSTOMER_ID);
    }

    private List<CustomerView> withIdentities(List<CustomersRecord> rows, UUID tenantSystemId) {
        Map<UUID, List<CustomerIdentityView>> identities = mappings(rows.stream().map(CustomersRecord::getId).toArray(UUID[]::new), tenantSystemId);
        return rows.stream().map(row -> toView(row, identities.getOrDefault(row.getId(), List.of()))).toList();
    }

    private Map<UUID, List<CustomerIdentityView>> mappings(UUID[] customerIds, UUID tenantSystemId) {
        if (customerIds.length == 0) {
            return Map.of();
        }
        Condition scope = tenantSystemId == null ? noCondition() : CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID.eq(tenantSystemId);
        Map<UUID, List<CustomerIdentityView>> byCustomer = new HashMap<>();
        for (Record3<UUID, UUID, String> row : dsl.select(CUSTOMER_MAPPINGS.CUSTOMER_ID, CUSTOMER_MAPPINGS.TENANT_SYSTEM_ID, CUSTOMER_MAPPINGS.EXTERNAL_USER_ID)
                .from(CUSTOMER_MAPPINGS)
                .where(CUSTOMER_MAPPINGS.CUSTOMER_ID.eq(any(customerIds)))
                .and(scope)
                .orderBy(CUSTOMER_MAPPINGS.EXTERNAL_USER_ID)
                .fetch()) {
            byCustomer.computeIfAbsent(row.value1(), key -> new ArrayList<>()).add(new CustomerIdentityView(row.value2(), row.value3()));
        }
        return byCustomer;
    }

    private static CustomerView toView(CustomersRecord row, List<CustomerIdentityView> identities) {
        return new CustomerView(
                new CustomerId(row.getId()),
                row.getDisplayName(),
                row.getEmail(),
                CustomerStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                List.copyOf(identities));
    }
}
