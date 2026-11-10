package dev.ngb.core_billing.customer.adapter.persistence.customer;

import dev.ngb.core_billing.customer.application.query.CustomerIdentityView;
import dev.ngb.core_billing.customer.application.query.CustomerReadRepository;
import dev.ngb.core_billing.customer.application.query.CustomerView;
import dev.ngb.core_billing.customer.domain.customer.CustomerStatus;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects customers and their mappings straight into views; the aggregate is never built. */
public class JdbcCustomerReadRepository implements CustomerReadRepository {

    private static final String COLUMNS = "id, display_name, email, status, created_at";

    private record Row(UUID id, String displayName, String email, String status, Instant createdAt) {}

    private record MappingRow(UUID customerId, UUID tenantSystemId, String externalUserId) {}

    private final JdbcClient jdbc;

    public JdbcCustomerReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CustomerView> list() {
        List<Row> rows = jdbc.sql("select " + COLUMNS + " from customers order by created_at, id").query(Row.class).list();
        Map<UUID, List<CustomerIdentityView>> identities = mappings(rows.stream().map(Row::id).toArray(UUID[]::new));
        return rows.stream().map(row -> toView(row, identities.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public Optional<CustomerView> findById(CustomerId id) {
        return jdbc.sql("select " + COLUMNS + " from customers where id = ?").param(id.value()).query(Row.class).optional()
                .map(row -> toView(row, mappings(new UUID[] {row.id()}).getOrDefault(row.id(), List.of())));
    }

    @Override
    public Optional<CustomerView> findByExternalIdentity(UUID tenantSystemId, String externalUserId) {
        return jdbc.sql("select customer_id from customer_mappings where tenant_system_id = ? and external_user_id = ?")
                .params(tenantSystemId, externalUserId)
                .query(UUID.class)
                .optional()
                .flatMap(id -> findById(new CustomerId(id)));
    }

    private Map<UUID, List<CustomerIdentityView>> mappings(UUID[] customerIds) {
        if (customerIds.length == 0) {
            return Map.of();
        }
        Map<UUID, List<CustomerIdentityView>> byCustomer = new HashMap<>();
        jdbc.sql("select customer_id, tenant_system_id, external_user_id from customer_mappings where customer_id = any(?) order by external_user_id")
                .param(customerIds)
                .query(MappingRow.class)
                .list()
                .forEach(row -> byCustomer.computeIfAbsent(row.customerId(), key -> new ArrayList<>())
                        .add(new CustomerIdentityView(row.tenantSystemId(), row.externalUserId())));
        return byCustomer;
    }

    private static CustomerView toView(Row row, List<CustomerIdentityView> identities) {
        return new CustomerView(
                new CustomerId(row.id()),
                row.displayName(),
                row.email(),
                CustomerStatus.valueOf(row.status()),
                row.createdAt(),
                List.copyOf(identities));
    }
}
