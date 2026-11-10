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
    private static final String COLUMNS_C = "c.id, c.display_name, c.email, c.status, c.created_at";

    private record Row(UUID id, String displayName, String email, String status, Instant createdAt) {}

    private record MappingRow(UUID customerId, UUID tenantSystemId, String externalUserId) {}

    private final JdbcClient jdbc;

    public JdbcCustomerReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CustomerView> list() {
        List<Row> rows = jdbc.sql("select " + COLUMNS + " from customers order by created_at, id").query(Row.class).list();
        Map<UUID, List<CustomerIdentityView>> identities = mappings(rows.stream().map(Row::id).toArray(UUID[]::new), null);
        return rows.stream().map(row -> toView(row, identities.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public List<CustomerView> listMappedTo(UUID tenantSystemId) {
        List<Row> rows = jdbc.sql("select " + COLUMNS_C + " from customers c join customer_mappings m "
                        + "on m.customer_id = c.id and m.tenant_system_id = ? order by c.created_at, c.id")
                .param(tenantSystemId)
                .query(Row.class)
                .list();
        Map<UUID, List<CustomerIdentityView>> identities = mappings(rows.stream().map(Row::id).toArray(UUID[]::new), tenantSystemId);
        return rows.stream().map(row -> toView(row, identities.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public Optional<CustomerView> findById(CustomerId id) {
        return jdbc.sql("select " + COLUMNS + " from customers where id = ?").param(id.value()).query(Row.class).optional()
                .map(row -> toView(row, mappings(new UUID[] {row.id()}, null).getOrDefault(row.id(), List.of())));
    }

    @Override
    public Optional<CustomerView> findByIdMappedTo(CustomerId id, UUID tenantSystemId) {
        return jdbc.sql("select " + COLUMNS_C + " from customers c where c.id = ? and exists "
                        + "(select 1 from customer_mappings m where m.customer_id = c.id and m.tenant_system_id = ?)")
                .params(id.value(), tenantSystemId)
                .query(Row.class)
                .optional()
                .map(row -> toView(row, mappings(new UUID[] {row.id()}, tenantSystemId).getOrDefault(row.id(), List.of())));
    }

    @Override
    public Optional<CustomerView> findByExternalIdentity(UUID tenantSystemId, String externalUserId) {
        return jdbc.sql("select customer_id from customer_mappings where tenant_system_id = ? and external_user_id = ?")
                .params(tenantSystemId, externalUserId)
                .query(UUID.class)
                .optional()
                .flatMap(id -> findByIdMappedTo(new CustomerId(id), tenantSystemId));
    }

    @Override
    public List<UUID> mappedCustomerIds(UUID tenantSystemId) {
        return jdbc.sql("select distinct customer_id from customer_mappings where tenant_system_id = ?")
                .param(tenantSystemId)
                .query(UUID.class)
                .list();
    }

    private Map<UUID, List<CustomerIdentityView>> mappings(UUID[] customerIds, UUID tenantSystemId) {
        if (customerIds.length == 0) {
            return Map.of();
        }
        Map<UUID, List<CustomerIdentityView>> byCustomer = new HashMap<>();
        String scope = tenantSystemId == null ? "" : " and tenant_system_id = ?";
        var statement = jdbc.sql("select customer_id, tenant_system_id, external_user_id from customer_mappings "
                + "where customer_id = any(?)" + scope + " order by external_user_id");
        if (tenantSystemId == null) {
            statement = statement.param(customerIds);
        } else {
            statement = statement.params(customerIds, tenantSystemId);
        }
        statement.query(MappingRow.class)
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
