package dev.ngb.core_billing.platform.adapter.persistence.tenant;
import dev.ngb.core_billing.platform.adapter.persistence.Causes;


import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import dev.ngb.core_billing.platform.application.tenant.command.TenantCodeAlreadyUsedException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantCode;
import dev.ngb.core_billing.platform.domain.tenant.TenantStatus;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

public class JdbcTenantRepository implements TenantRepository {

    private final JdbcAggregateOperations jdbc;

    public JdbcTenantRepository(JdbcAggregateOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Tenant tenant) {
        try {
            jdbc.insert(toRow(tenant));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new TenantCodeAlreadyUsedException(tenant.code().value());
            }
            throw e;
        }
    }

    @Override
    public void update(Tenant tenant) {
        jdbc.update(toRow(tenant));
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return Optional.ofNullable(jdbc.findById(id.value(), TenantRow.class)).map(JdbcTenantRepository::toDomain);
    }

    @Override
    public Optional<Tenant> findByCode(TenantCode code) {
        return jdbc.findOne(query(where("code").is(code.value())), TenantRow.class)
                .map(JdbcTenantRepository::toDomain);
    }

    private static TenantRow toRow(Tenant tenant) {
        return new TenantRow(
                tenant.id().value(),
                tenant.code().value(),
                tenant.name(),
                tenant.status().name(),
                tenant.createdAt());
    }

    private static Tenant toDomain(TenantRow row) {
        return Tenant.restore(
                new TenantId(row.id()),
                new TenantCode(row.code()),
                row.name(),
                TenantStatus.valueOf(row.status()),
                row.createdAt());
    }
}
