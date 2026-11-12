package dev.ngb.core_billing.platform.adapter.tenantsystem.persistence;

import static dev.ngb.core_billing_jooq.Tables.TENANT_SYSTEMS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Causes;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystemStatus;
import dev.ngb.core_billing_jooq.tables.records.TenantSystemsRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqTenantSystemRepository implements TenantSystemRepository {

    private final DSLContext dsl;

    public JooqTenantSystemRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(TenantSystem system) {
        try {
            dsl.insertInto(TENANT_SYSTEMS)
                    .set(TENANT_SYSTEMS.ID, system.id().value())
                    .set(TENANT_SYSTEMS.TENANT_ID, system.tenantId().value())
                    .set(TENANT_SYSTEMS.NAME, system.name())
                    .set(TENANT_SYSTEMS.STATUS, system.status().name())
                    .set(TENANT_SYSTEMS.CREATED_AT, system.createdAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("a tenant system with this name already exists");
            }
            throw e;
        }
    }

    @Override
    public void update(TenantSystem system) {
        dsl.update(TENANT_SYSTEMS)
                .set(TENANT_SYSTEMS.NAME, system.name())
                .set(TENANT_SYSTEMS.STATUS, system.status().name())
                .where(TENANT_SYSTEMS.ID.eq(system.id().value()))
                .execute();
    }

    @Override
    public Optional<TenantSystem> findById(TenantSystemId id) {
        return dsl.selectFrom(TENANT_SYSTEMS)
                .where(TENANT_SYSTEMS.ID.eq(id.value()))
                .fetchOptional(JooqTenantSystemRepository::toDomain);
    }

    private static TenantSystem toDomain(TenantSystemsRecord row) {
        return TenantSystem.restore(
                new TenantSystemId(row.getId()),
                new TenantId(row.getTenantId()),
                row.getName(),
                TenantSystemStatus.valueOf(row.getStatus()),
                row.getCreatedAt());
    }
}
