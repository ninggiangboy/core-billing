package dev.ngb.core_billing.platform.adapter.persistence.tenantsystem;
import dev.ngb.core_billing.sharedkernel.Causes;


import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystemStatus;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

public class JdbcTenantSystemRepository implements TenantSystemRepository {

    private final JdbcAggregateOperations jdbc;

    public JdbcTenantSystemRepository(JdbcAggregateOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(TenantSystem system) {
        try {
            jdbc.insert(toRow(system));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("a tenant system with this name already exists");
            }
            throw e;
        }
    }

    @Override
    public void update(TenantSystem system) {
        jdbc.update(toRow(system));
    }

    @Override
    public Optional<TenantSystem> findById(TenantSystemId id) {
        return Optional.ofNullable(jdbc.findById(id.value(), TenantSystemRow.class)).map(JdbcTenantSystemRepository::toDomain);
    }

    private static TenantSystemRow toRow(TenantSystem system) {
        return new TenantSystemRow(
                system.id().value(), system.tenantId().value(), system.name(), system.status().name(), system.createdAt());
    }

    private static TenantSystem toDomain(TenantSystemRow row) {
        return TenantSystem.restore(
                new TenantSystemId(row.id()),
                new TenantId(row.tenantId()),
                row.name(),
                TenantSystemStatus.valueOf(row.status()),
                row.createdAt());
    }
}
