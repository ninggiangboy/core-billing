package io.corebilling.platform.adapter.persistence;

import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.platform.application.TenantSystemRepository;
import io.corebilling.platform.domain.TenantSystem;
import io.corebilling.platform.domain.TenantSystemStatus;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Query;

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

    @Override
    public List<TenantSystem> list() {
        return jdbc.findAll(Query.empty().sort(Sort.by("createdAt")), TenantSystemRow.class).stream()
                .map(JdbcTenantSystemRepository::toDomain)
                .toList();
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
