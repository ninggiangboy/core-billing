package dev.ngb.core_billing.customer.adapter.persistence;

import dev.ngb.core_billing.customer.application.port.CustomerRepository;
import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.CustomerStatus;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
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
