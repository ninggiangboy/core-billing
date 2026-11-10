package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.pricing.adapter.persistence.JdbcMetricRegistry;
import dev.ngb.core_billing.pricing.adapter.persistence.JdbcPlanRepository;
import dev.ngb.core_billing.pricing.adapter.persistence.JdbcRatingRepository;
import dev.ngb.core_billing.pricing.adapter.persistence.metric.JdbcMetricReadRepository;
import dev.ngb.core_billing.pricing.adapter.persistence.plan.JdbcPlanReadRepository;
import dev.ngb.core_billing.pricing.adapter.persistence.rating.JdbcRatingReadRepository;
import dev.ngb.core_billing.pricing.api.metric.MetricCatalog;
import dev.ngb.core_billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.core_billing.pricing.application.rating.command.EstimateCostUseCase;
import dev.ngb.core_billing.pricing.application.metric.query.MetricCatalogService;
import dev.ngb.core_billing.pricing.application.metric.query.MetricReadRepository;
import dev.ngb.core_billing.pricing.application.metric.port.MetricRegistry;
import dev.ngb.core_billing.pricing.application.plan.command.PlanAdministration;
import dev.ngb.core_billing.pricing.application.plan.port.PlanRepository;
import dev.ngb.core_billing.pricing.application.plan.query.PlanQueries;
import dev.ngb.core_billing.pricing.application.plan.query.PlanReadRepository;
import dev.ngb.core_billing.pricing.application.rating.query.RatingQueries;
import dev.ngb.core_billing.pricing.application.rating.query.RatingReadRepository;
import dev.ngb.core_billing.pricing.application.rating.port.RatingRepository;
import dev.ngb.core_billing.pricing.application.rating.command.ReserveFromEstimateUseCase;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.usage.api.usage.UsageLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** Wires the pricing module. Holds no business rules. Pricing reaches money and usage only through their api interfaces. */
@Configuration(proxyBeanMethods = false)
class PricingConfiguration {

    @Bean
    PlanRepository planRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc, JsonMapper json) {
        return new JdbcPlanRepository(aggregates, jdbc, json);
    }

    @Bean
    MetricRegistry metricRegistry(JdbcClient jdbc) {
        return new JdbcMetricRegistry(jdbc);
    }

    @Bean
    RatingRepository ratingRepository(JdbcClient jdbc, JsonMapper json) {
        return new JdbcRatingRepository(jdbc, json);
    }

    @Bean
    PlanReadRepository planReadRepository(JdbcClient jdbc) {
        return new JdbcPlanReadRepository(jdbc);
    }

    @Bean
    RatingReadRepository ratingReadRepository(JdbcClient jdbc, JsonMapper json) {
        return new JdbcRatingReadRepository(jdbc, json);
    }

    @Bean
    MetricReadRepository metricReadRepository(JdbcClient jdbc) {
        return new JdbcMetricReadRepository(jdbc);
    }

    @Bean
    MetricCatalog metricCatalog(UnitOfWork unitOfWork, MetricReadRepository metrics) {
        return new MetricCatalogService(unitOfWork, metrics);
    }

    @Bean
    PlanAdministration planAdministration(
            UnitOfWork unitOfWork, PlanRepository plans, MetricRegistry metrics, EventPublisher events, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        return new PlanAdministration(unitOfWork, plans, metrics, events, auditLog, tenantContext, ids, clock);
    }

    @Bean
    EstimateCostUseCase estimateCostUseCase(UnitOfWork unitOfWork, PlanRepository plans, Clock clock) {
        return new EstimateCostUseCase(unitOfWork, plans, clock);
    }

    @Bean
    ReserveFromEstimateUseCase reserveFromEstimateUseCase(
            UnitOfWork unitOfWork, EstimateCostUseCase estimates, ReserveBudget reserve, WalletLookup wallets, CustomerLookup customers) {
        return new ReserveFromEstimateUseCase(unitOfWork, estimates, reserve, wallets, customers);
    }

    @Bean
    ChargeFromUsageUseCase chargeFromUsageUseCase(
            UnitOfWork unitOfWork, PlanRepository plans, RatingRepository ratings, UsageLookup usage, ChargeWallet money, WalletLookup wallets,
            CustomerLookup customers, EventPublisher events, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new ChargeFromUsageUseCase(unitOfWork, plans, ratings, usage, money, wallets, customers, events, tenantContext, ids, clock);
    }

    @Bean
    PlanQueries planQueries(UnitOfWork unitOfWork, PlanReadRepository plans) {
        return new PlanQueries(unitOfWork, plans);
    }

    @Bean
    RatingQueries ratingQueries(UnitOfWork unitOfWork, RatingReadRepository ratings, CustomerLookup customers) {
        return new RatingQueries(unitOfWork, ratings, customers);
    }
}
