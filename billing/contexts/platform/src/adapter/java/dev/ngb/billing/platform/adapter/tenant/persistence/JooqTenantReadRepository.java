package dev.ngb.billing.platform.adapter.tenant.persistence;

import static dev.ngb.billing_jooq.Tables.TENANTS;
import static dev.ngb.billing_jooq.Tables.TENANT_DOMAINS;

import dev.ngb.billing.platform.application.tenant.query.TenantDomainView;
import dev.ngb.billing.platform.application.tenant.query.TenantReadRepository;
import dev.ngb.billing.platform.application.tenant.query.TenantView;
import dev.ngb.billing.platform.domain.tenant.DomainKind;
import dev.ngb.billing.platform.domain.tenant.TenantStatus;
import dev.ngb.billing_jooq.tables.records.TenantDomainsRecord;
import dev.ngb.billing_jooq.tables.records.TenantsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects tenants and domains straight into views. */
public class JooqTenantReadRepository implements TenantReadRepository {

    private final DSLContext dsl;

    public JooqTenantReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<TenantView> list() {
        return dsl.selectFrom(TENANTS)
                .where(TENANTS.CODE.ne("platform"))
                .orderBy(TENANTS.CREATED_AT, TENANTS.ID)
                .fetch(JooqTenantReadRepository::toView);
    }

    @Override
    public Optional<TenantView> findById(UUID id) {
        return dsl.selectFrom(TENANTS)
                .where(TENANTS.ID.eq(id))
                .fetchOptional(JooqTenantReadRepository::toView);
    }

    @Override
    public List<TenantDomainView> domains(UUID tenantId) {
        return dsl.selectFrom(TENANT_DOMAINS)
                .where(TENANT_DOMAINS.TENANT_ID.eq(tenantId))
                .orderBy(TENANT_DOMAINS.HOSTNAME)
                .fetch(JooqTenantReadRepository::toView);
    }

    private static TenantView toView(TenantsRecord row) {
        return new TenantView(row.getId(), row.getCode(), row.getName(), TenantStatus.valueOf(row.getStatus()), row.getCreatedAt());
    }

    private static TenantDomainView toView(TenantDomainsRecord row) {
        return new TenantDomainView(
                row.getId(), row.getTenantId(), row.getHostname(), DomainKind.valueOf(row.getKind()), row.getCreatedAt());
    }
}
