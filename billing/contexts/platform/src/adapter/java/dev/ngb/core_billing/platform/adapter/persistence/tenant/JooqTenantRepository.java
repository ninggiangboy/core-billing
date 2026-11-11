package dev.ngb.core_billing.platform.adapter.persistence.tenant;

import static dev.ngb.core_billing_jooq.Tables.TENANTS;

import dev.ngb.core_billing.platform.application.tenant.exception.TenantCodeAlreadyUsedException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantCode;
import dev.ngb.core_billing.platform.domain.tenant.TenantStatus;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing_jooq.tables.records.TenantsRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqTenantRepository implements TenantRepository {

    private final DSLContext dsl;

    public JooqTenantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Tenant tenant) {
        try {
            dsl.insertInto(TENANTS)
                    .set(TENANTS.ID, tenant.id().value())
                    .set(TENANTS.CODE, tenant.code().value())
                    .set(TENANTS.NAME, tenant.name())
                    .set(TENANTS.STATUS, tenant.status().name())
                    .set(TENANTS.CREATED_AT, tenant.createdAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new TenantCodeAlreadyUsedException(tenant.code().value());
            }
            throw e;
        }
    }

    @Override
    public void update(Tenant tenant) {
        dsl.update(TENANTS)
                .set(TENANTS.NAME, tenant.name())
                .set(TENANTS.STATUS, tenant.status().name())
                .where(TENANTS.ID.eq(tenant.id().value()))
                .execute();
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return dsl.selectFrom(TENANTS)
                .where(TENANTS.ID.eq(id.value()))
                .fetchOptional(JooqTenantRepository::toDomain);
    }

    @Override
    public Optional<Tenant> findByCode(TenantCode code) {
        return dsl.selectFrom(TENANTS)
                .where(TENANTS.CODE.eq(code.value()))
                .fetchOptional(JooqTenantRepository::toDomain);
    }

    private static Tenant toDomain(TenantsRecord row) {
        return Tenant.restore(
                new TenantId(row.getId()),
                new TenantCode(row.getCode()),
                row.getName(),
                TenantStatus.valueOf(row.getStatus()),
                row.getCreatedAt());
    }
}
