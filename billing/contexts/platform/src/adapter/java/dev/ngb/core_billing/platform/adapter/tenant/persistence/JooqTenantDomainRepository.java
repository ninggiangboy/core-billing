package dev.ngb.core_billing.platform.adapter.tenant.persistence;

import static dev.ngb.core_billing_jooq.Tables.TENANT_DOMAINS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Causes;
import dev.ngb.core_billing.platform.application.tenant.exception.HostnameAlreadyMappedException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.core_billing.platform.domain.tenant.DomainKind;
import dev.ngb.core_billing.platform.domain.tenant.Hostname;
import dev.ngb.core_billing.platform.domain.tenant.TenantDomain;
import dev.ngb.core_billing_jooq.tables.records.TenantDomainsRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqTenantDomainRepository implements TenantDomainRepository {

    private final DSLContext dsl;

    public JooqTenantDomainRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(TenantDomain domain) {
        try {
            dsl.insertInto(TENANT_DOMAINS)
                    .set(TENANT_DOMAINS.ID, domain.id())
                    .set(TENANT_DOMAINS.TENANT_ID, domain.tenantId().map(TenantId::value).orElse(null))
                    .set(TENANT_DOMAINS.HOSTNAME, domain.hostname().value())
                    .set(TENANT_DOMAINS.KIND, domain.kind().name())
                    .set(TENANT_DOMAINS.CREATED_AT, domain.createdAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new HostnameAlreadyMappedException(domain.hostname().value());
            }
            throw e;
        }
    }

    @Override
    public Optional<TenantDomain> findByHostname(Hostname hostname) {
        return dsl.selectFrom(TENANT_DOMAINS)
                .where(TENANT_DOMAINS.HOSTNAME.eq(hostname.value()))
                .fetchOptional(JooqTenantDomainRepository::toDomain);
    }

    @Override
    public List<TenantDomain> findByTenant(TenantId tenantId) {
        return dsl.selectFrom(TENANT_DOMAINS)
                .where(TENANT_DOMAINS.TENANT_ID.eq(tenantId.value()))
                .fetch(JooqTenantDomainRepository::toDomain);
    }

    private static TenantDomain toDomain(TenantDomainsRecord row) {
        return TenantDomain.restore(
                row.getId(),
                row.getTenantId() == null ? null : new TenantId(row.getTenantId()),
                new Hostname(row.getHostname()),
                DomainKind.valueOf(row.getKind()),
                row.getCreatedAt());
    }
}
