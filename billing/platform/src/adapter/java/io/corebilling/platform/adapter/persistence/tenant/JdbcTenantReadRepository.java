package io.corebilling.platform.adapter.persistence.tenant;

import io.corebilling.platform.application.tenant.query.TenantDomainView;
import io.corebilling.platform.application.tenant.query.TenantReadRepository;
import io.corebilling.platform.application.tenant.query.TenantView;
import io.corebilling.platform.domain.tenant.DomainKind;
import io.corebilling.platform.domain.tenant.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects tenants and domains straight into views. */
public class JdbcTenantReadRepository implements TenantReadRepository {

    private record Row(UUID id, String code, String name, String status, Instant createdAt) {
        TenantView toView() {
            return new TenantView(id, code, name, TenantStatus.valueOf(status), createdAt);
        }
    }

    private record DomainRow(UUID id, UUID tenantId, String hostname, String kind, Instant createdAt) {
        TenantDomainView toView() {
            return new TenantDomainView(id, tenantId, hostname, DomainKind.valueOf(kind), createdAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcTenantReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TenantView> list() {
        return jdbc.sql("select id, code, name, status, created_at from tenants where code <> 'platform' order by created_at, id")
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }

    @Override
    public Optional<TenantView> findById(UUID id) {
        return jdbc.sql("select id, code, name, status, created_at from tenants where id = ?").param(id).query(Row.class).optional().map(Row::toView);
    }

    @Override
    public List<TenantDomainView> domains(UUID tenantId) {
        return jdbc.sql("select id, tenant_id, hostname, kind, created_at from tenant_domains where tenant_id = ? order by hostname")
                .param(tenantId)
                .query(DomainRow.class)
                .list()
                .stream()
                .map(DomainRow::toView)
                .toList();
    }
}
