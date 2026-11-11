package dev.ngb.core_billing.boot.infrastructure;

import dev.ngb.core_billing.boot.infrastructure.coordination.JobLeases;
import dev.ngb.core_billing.boot.infrastructure.coordination.JobRuns;
import dev.ngb.core_billing.boot.infrastructure.coordination.PostgresRateLimiter;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.auth.port.RateLimiter;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventInbox;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
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
    EventPublisher eventPublisher(DSLContext dsl, JsonMapper json) {
        return new JooqOutboxAppender(dsl, json);
    }

    @Bean
    EventInbox eventInbox(DSLContext dsl) {
        return new JooqEventInbox(dsl);
    }

    @Bean
    IdempotencyStore idempotencyStore(DSLContext dsl) {
        return new JooqIdempotencyStore(dsl);
    }

    @Bean
    AuditLog auditLog(DSLContext dsl, IdGenerator ids) {
        return new JooqAuditLog(dsl, ids);
    }

    @Bean
    JobLeases jobLeases(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new JobLeases(transactionManager, jdbc);
    }

    @Bean
    JobRuns jobRuns(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new JobRuns(transactionManager, jdbc);
    }

    @Bean
    RateLimiter apiKeyRateLimiter(
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbc,
            @Value("${billing.security.api-key-rate-limit.burst:100}") int burst,
            @Value("${billing.security.api-key-rate-limit.per-second:20}") double perSecond) {
        return new PostgresRateLimiter(transactionManager, jdbc, burst, perSecond);
    }
}
