package io.corebilling.customer.adapter.persistence;

import io.corebilling.customer.application.port.CustomerRepository;
import io.corebilling.customer.domain.customer.Customer;
import io.corebilling.customer.domain.customer.CustomerStatus;
import io.corebilling.customer.domain.customer.ExternalIdentity;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Query;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcCustomerRepository implements CustomerRepository {

    private final JdbcAggregateOperations jdbc;
    private final JdbcClient sql;

    public JdbcCustomerRepository(JdbcAggregateOperations jdbc, JdbcClient sql) {
        this.jdbc = jdbc;
        this.sql = sql;
    }

    @Override
    public void add(Customer customer) {
        try {
            jdbc.insert(toRow(customer));
        } catch (RuntimeException e) {
            if (contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this external user is already mapped to a customer");
            }
            throw e;
        }
    }

    @Override
    public void update(Customer customer) {
        jdbc.update(toRow(customer));
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(jdbc.findById(id.value(), CustomerRow.class)).map(JdbcCustomerRepository::toDomain);
    }

    @Override
    public Optional<Customer> findByExternalIdentity(ExternalIdentity identity) {
        return sql.sql("select customer_id from customer_mappings where tenant_system_id = ? and external_user_id = ?")
                .params(identity.tenantSystemId().value(), identity.externalUserId())
                .query(UUID.class)
                .optional()
                .flatMap(id -> findById(new CustomerId(id)));
    }

    @Override
    public List<Customer> list() {
        return jdbc.findAll(Query.empty().sort(Sort.by("createdAt")), CustomerRow.class).stream()
                .map(JdbcCustomerRepository::toDomain)
                .toList();
    }

    private static CustomerRow toRow(Customer customer) {
        return new CustomerRow(
                customer.id().value(),
                customer.tenantId().value(),
                customer.displayName(),
                customer.email(),
                customer.status().name(),
                customer.createdAt(),
                customer.identities().stream()
                        .map(i -> new CustomerRow.MappingRow(customer.tenantId().value(), i.tenantSystemId().value(), i.externalUserId()))
                        .collect(Collectors.toSet()));
    }

    private static Customer toDomain(CustomerRow row) {
        return Customer.restore(
                new CustomerId(row.id()),
                new TenantId(row.tenantId()),
                row.displayName(),
                row.email(),
                CustomerStatus.valueOf(row.status()),
                row.createdAt(),
                row.mappings().stream()
                        .map(m -> new ExternalIdentity(new TenantSystemId(m.tenantSystemId()), m.externalUserId()))
                        .collect(Collectors.toSet()));
    }

    private static boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
