package io.corebilling.platform.adapter.persistence.tenantsystem;

import io.corebilling.platform.application.tenantsystem.query.TenantSystemReadRepository;
import io.corebilling.platform.application.tenantsystem.query.TenantSystemView;
import io.corebilling.platform.domain.tenantsystem.TenantSystemStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects tenant systems straight into views. */
public class JdbcTenantSystemReadRepository implements TenantSystemReadRepository {

    private record Row(UUID id, String name, String status, Instant createdAt) {
        TenantSystemView toView() {
            return new TenantSystemView(id, name, TenantSystemStatus.valueOf(status), createdAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcTenantSystemReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TenantSystemView> list() {
        return jdbc.sql("select id, name, status, created_at from tenant_systems order by created_at, id")
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }

    @Override
    public Optional<TenantSystemView> findById(UUID id) {
        return jdbc.sql("select id, name, status, created_at from tenant_systems where id = ?").param(id).query(Row.class).optional().map(Row::toView);
    }
}
