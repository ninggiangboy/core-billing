package io.corebilling.boot.config;

import io.corebilling.money.api.charge.ChargeWallet;
import io.corebilling.money.api.reservation.ReserveBudget;
import io.corebilling.money.api.wallet.WalletLookup;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.pricing.adapter.persistence.JdbcMetricRegistry;
import io.corebilling.pricing.adapter.persistence.JdbcPlanRepository;
import io.corebilling.pricing.adapter.persistence.JdbcRatingRepository;
import io.corebilling.pricing.adapter.persistence.metric.JdbcMetricReadRepository;
import io.corebilling.pricing.adapter.persistence.plan.JdbcPlanReadRepository;
import io.corebilling.pricing.adapter.persistence.rating.JdbcRatingReadRepository;
import io.corebilling.pricing.api.metric.MetricCatalog;
import io.corebilling.pricing.application.rating.command.ChargeFromUsageUseCase;
import io.corebilling.pricing.application.rating.command.EstimateCostUseCase;
import io.corebilling.pricing.application.metric.query.MetricCatalogService;
import io.corebilling.pricing.application.metric.query.MetricReadRepository;
import io.corebilling.pricing.application.metric.port.MetricRegistry;
import io.corebilling.pricing.application.plan.command.PlanAdministration;
import io.corebilling.pricing.application.plan.port.PlanRepository;
import io.corebilling.pricing.application.plan.query.PlanQueries;
import io.corebilling.pricing.application.plan.query.PlanReadRepository;
import io.corebilling.pricing.application.rating.query.RatingQueries;
import io.corebilling.pricing.application.rating.query.RatingReadRepository;
import io.corebilling.pricing.application.rating.port.RatingRepository;
import io.corebilling.pricing.application.rating.command.ReserveFromEstimateUseCase;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.usage.api.usage.UsageLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** Wires the pricing module. Holds no business rules. Pricing reaches money and usage only through their api interfaces. */
@Configuration(proxyBeanMethods = false)
class PricingConfiguration {

    @Bean
    PlanRepository planRepository(JdbcClient jdbc, JsonMapper json) {
        return new JdbcPlanRepository(jdbc, json);
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
    ReserveFromEstimateUseCase reserveFromEstimateUseCase(UnitOfWork unitOfWork, EstimateCostUseCase estimates, ReserveBudget reserve, WalletLookup wallets) {
        return new ReserveFromEstimateUseCase(unitOfWork, estimates, reserve, wallets);
    }

    @Bean
    ChargeFromUsageUseCase chargeFromUsageUseCase(
            UnitOfWork unitOfWork, PlanRepository plans, RatingRepository ratings, UsageLookup usage, ChargeWallet money, WalletLookup wallets,
            EventPublisher events, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new ChargeFromUsageUseCase(unitOfWork, plans, ratings, usage, money, wallets, events, tenantContext, ids, clock);
    }

    @Bean
    PlanQueries planQueries(UnitOfWork unitOfWork, PlanReadRepository plans) {
        return new PlanQueries(unitOfWork, plans);
    }

    @Bean
    RatingQueries ratingQueries(UnitOfWork unitOfWork, RatingReadRepository ratings) {
        return new RatingQueries(unitOfWork, ratings);
    }
}
