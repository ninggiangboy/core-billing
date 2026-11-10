package io.corebilling.platform.adapter.persistence.tenant;
import io.corebilling.platform.adapter.persistence.Causes;


import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import io.corebilling.platform.application.tenant.command.HostnameAlreadyMappedException;
import io.corebilling.platform.application.tenant.port.TenantDomainRepository;
import io.corebilling.platform.domain.tenant.DomainKind;
import io.corebilling.platform.domain.tenant.Hostname;
import io.corebilling.platform.domain.tenant.TenantDomain;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

public class JdbcTenantDomainRepository implements TenantDomainRepository {

    private final JdbcAggregateOperations jdbc;

    public JdbcTenantDomainRepository(JdbcAggregateOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(TenantDomain domain) {
        try {
            jdbc.insert(toRow(domain));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new HostnameAlreadyMappedException(domain.hostname().value());
            }
            throw e;
        }
    }

    @Override
    public Optional<TenantDomain> findByHostname(Hostname hostname) {
        return jdbc.findOne(query(where("hostname").is(hostname.value())), TenantDomainRow.class)
                .map(JdbcTenantDomainRepository::toDomain);
    }

    @Override
    public List<TenantDomain> findByTenant(TenantId tenantId) {
        return jdbc.findAll(query(where("tenantId").is(tenantId.value())), TenantDomainRow.class).stream()
                .map(JdbcTenantDomainRepository::toDomain)
                .toList();
    }

    private static TenantDomainRow toRow(TenantDomain domain) {
        return new TenantDomainRow(
                domain.id(),
                domain.tenantId().map(TenantId::value).orElse(null),
                domain.hostname().value(),
                domain.kind().name(),
                domain.createdAt());
    }

    private static TenantDomain toDomain(TenantDomainRow row) {
        return TenantDomain.restore(
                row.id(),
                row.tenantId() == null ? null : new TenantId(row.tenantId()),
                new Hostname(row.hostname()),
                DomainKind.valueOf(row.kind()),
                row.createdAt());
    }
}
