package dev.ngb.core_billing.platform.adapter.tenantsystem.persistence;

import static dev.ngb.core_billing_jooq.Tables.TENANT_SYSTEMS;

import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemReadRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemView;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystemStatus;
import dev.ngb.core_billing_jooq.tables.records.TenantSystemsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects tenant systems straight into views. */
public class JooqTenantSystemReadRepository implements TenantSystemReadRepository {

    private final DSLContext dsl;

    public JooqTenantSystemReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<TenantSystemView> list() {
        return dsl.selectFrom(TENANT_SYSTEMS)
                .orderBy(TENANT_SYSTEMS.CREATED_AT, TENANT_SYSTEMS.ID)
                .fetch(JooqTenantSystemReadRepository::toView);
    }

    @Override
    public Optional<TenantSystemView> findById(UUID id) {
        return dsl.selectFrom(TENANT_SYSTEMS)
                .where(TENANT_SYSTEMS.ID.eq(id))
                .fetchOptional(JooqTenantSystemReadRepository::toView);
    }

    private static TenantSystemView toView(TenantSystemsRecord row) {
        return new TenantSystemView(row.getId(), row.getName(), TenantSystemStatus.valueOf(row.getStatus()), row.getCreatedAt());
    }
}
