package dev.ngb.billing.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.customer.api.lookup.CustomerLookup;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.session.adapter.portal.persistence.JooqBillingSessionRepository;
import dev.ngb.billing.session.adapter.portal.persistence.JooqReturnUrlRepository;
import dev.ngb.billing.session.api.portal.PortalSessions;
import dev.ngb.billing.session.application.portal.command.ExchangeBillingSessionUseCase;
import dev.ngb.billing.session.application.portal.command.ManageReturnUrlsUseCase;
import dev.ngb.billing.session.application.portal.command.OpenBillingSessionUseCase;
import dev.ngb.billing.session.application.portal.command.RevokeBillingSessionUseCase;
import dev.ngb.billing.session.application.portal.port.BillingSessionRepository;
import dev.ngb.billing.session.application.portal.port.ReturnUrlRepository;
import dev.ngb.billing.session.application.portal.service.PortalSessionsService;
import dev.ngb.billing.session.application.portal.service.SessionSettings;
import dev.ngb.billing.session.application.portal.service.SessionTokens;
import java.security.SecureRandom;
import java.time.Duration;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the session module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class SessionConfiguration {

    @Bean
    BillingSessionRepository billingSessionRepository(DSLContext dsl) {
        return new JooqBillingSessionRepository(dsl);
    }

    @Bean
    ReturnUrlRepository returnUrlRepository(DSLContext dsl) {
        return new JooqReturnUrlRepository(dsl);
    }

    @Bean
    SessionTokens sessionTokens() {
        return new SessionTokens(new SecureRandom());
    }

    @Bean
    SessionSettings sessionSettings(
            @Value("${billing.portal.session-default-ttl:PT1H}") Duration defaultTtl,
            @Value("${billing.portal.session-min-ttl:PT1M}") Duration minTtl,
            @Value("${billing.portal.session-max-ttl:P1D}") Duration maxTtl) {
        return new SessionSettings(defaultTtl, minTtl, maxTtl);
    }

    @Bean
    OpenBillingSessionUseCase openBillingSessionUseCase(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, ReturnUrlRepository returnUrls, CustomerLookup customers,
            SessionTokens tokens, SessionSettings settings, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new OpenBillingSessionUseCase(unitOfWork, sessions, returnUrls, customers, tokens, settings, auditLog, tenantContext, ids, clock);
    }

    @Bean
    ExchangeBillingSessionUseCase exchangeBillingSessionUseCase(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, SessionTokens tokens, AuditLog auditLog, Clock clock) {
        return new ExchangeBillingSessionUseCase(unitOfWork, sessions, tokens, auditLog, clock);
    }

    @Bean
    RevokeBillingSessionUseCase revokeBillingSessionUseCase(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, AuditLog auditLog, Clock clock) {
        return new RevokeBillingSessionUseCase(unitOfWork, sessions, auditLog, clock);
    }

    @Bean
    ManageReturnUrlsUseCase manageReturnUrlsUseCase(
            UnitOfWork unitOfWork, ReturnUrlRepository returnUrls, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        return new ManageReturnUrlsUseCase(unitOfWork, returnUrls, auditLog, tenantContext, clock);
    }

    @Bean
    PortalSessions portalSessions(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, ExchangeBillingSessionUseCase exchange, SessionTokens tokens, Clock clock) {
        return new PortalSessionsService(unitOfWork, sessions, exchange, tokens, clock);
    }
}
