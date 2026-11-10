package io.corebilling.boot.config;

import io.corebilling.money.api.ChargeWallet;
import io.corebilling.money.api.ReserveBudget;
import io.corebilling.money.api.WalletLookup;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.pricing.adapter.persistence.JdbcMetricRegistry;
import io.corebilling.pricing.adapter.persistence.JdbcPlanRepository;
import io.corebilling.pricing.adapter.persistence.JdbcRatingRepository;
import io.corebilling.pricing.api.MetricCatalog;
import io.corebilling.pricing.application.ChargeFromUsageUseCase;
import io.corebilling.pricing.application.EstimateCostUseCase;
import io.corebilling.pricing.application.MetricCatalogService;
import io.corebilling.pricing.application.MetricRegistry;
import io.corebilling.pricing.application.PlanAdministration;
import io.corebilling.pricing.application.PlanRepository;
import io.corebilling.pricing.application.RatingQueries;
import io.corebilling.pricing.application.RatingRepository;
import io.corebilling.pricing.application.ReserveFromEstimateUseCase;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.usage.api.UsageLookup;
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
    MetricCatalog metricCatalog(UnitOfWork unitOfWork, MetricRegistry metrics) {
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
    RatingQueries ratingQueries(UnitOfWork unitOfWork, RatingRepository ratings) {
        return new RatingQueries(unitOfWork, ratings);
    }
}
