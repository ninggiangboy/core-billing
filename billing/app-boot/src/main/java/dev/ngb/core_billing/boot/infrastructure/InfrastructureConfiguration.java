package dev.ngb.core_billing.boot.infrastructure;

import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventInbox;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Wires the technical ports every context shares; it is the only configuration that sees the transaction manager. */
@Configuration(proxyBeanMethods = false)
class InfrastructureConfiguration {

    @Bean
    TenantContext tenantContext() {
        return new ScopedTenantContext();
    }

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager, JdbcTemplate jdbc, TenantContext tenantContext) {
        return new SpringUnitOfWork(transactionManager, jdbc, tenantContext);
    }

    @Bean
    Clock clock() {
        return new SystemClock();
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }

    @Bean
    EventPublisher eventPublisher(JdbcClient jdbc, JsonMapper json) {
        return new JdbcOutboxAppender(jdbc, json);
    }

    @Bean
    EventInbox eventInbox(JdbcClient jdbc) {
        return new JdbcEventInbox(jdbc);
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcClient jdbc) {
        return new JdbcIdempotencyStore(jdbc);
    }

    @Bean
    AuditLog auditLog(JdbcClient jdbc, IdGenerator ids) {
        return new JdbcAuditLog(jdbc, ids);
    }
}
