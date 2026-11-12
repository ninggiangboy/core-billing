package dev.ngb.core_billing.boot.config;

import dev.ngb.core.event.EventPublisher;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.pricing.adapter.metric.persistence.JooqMetricReadRepository;
import dev.ngb.core_billing.pricing.adapter.metric.persistence.JooqMetricRegistry;
import dev.ngb.core_billing.pricing.adapter.plan.persistence.JooqPlanReadRepository;
import dev.ngb.core_billing.pricing.adapter.plan.persistence.JooqPlanRepository;
import dev.ngb.core_billing.pricing.adapter.rating.persistence.JooqRatingReadRepository;
import dev.ngb.core_billing.pricing.adapter.rating.persistence.JooqRatingRepository;
import dev.ngb.core_billing.pricing.api.metric.MetricCatalog;
import dev.ngb.core_billing.pricing.api.plan.PlanPricing;
import dev.ngb.core_billing.pricing.application.metric.port.MetricRegistry;
import dev.ngb.core_billing.pricing.application.metric.query.MetricReadRepository;
import dev.ngb.core_billing.pricing.application.metric.service.MetricCatalogService;
import dev.ngb.core_billing.pricing.application.plan.command.ManagePlanUseCase;
import dev.ngb.core_billing.pricing.application.plan.port.PlanRepository;
import dev.ngb.core_billing.pricing.application.plan.query.PlanQueries;
import dev.ngb.core_billing.pricing.application.plan.query.PlanReadRepository;
import dev.ngb.core_billing.pricing.application.plan.service.PlanPricingService;
import dev.ngb.core_billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.core_billing.pricing.application.rating.command.EstimateCostUseCase;
import dev.ngb.core_billing.pricing.application.rating.command.ReserveFromEstimateUseCase;
import dev.ngb.core_billing.pricing.application.rating.port.RatingRepository;
import dev.ngb.core_billing.pricing.application.rating.query.RatingQueries;
import dev.ngb.core_billing.pricing.application.rating.query.RatingReadRepository;
import dev.ngb.core_billing.usage.api.lookup.UsageLookup;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Wires the pricing module. Holds no business rules. Pricing reaches money and usage only through their api interfaces. */
@Configuration(proxyBeanMethods = false)
class PricingConfiguration {

    @Bean
    PlanRepository planRepository(DSLContext dsl, JsonMapper json) {
        return new JooqPlanRepository(dsl, json);
    }

    @Bean
    MetricRegistry metricRegistry(DSLContext dsl) {
        return new JooqMetricRegistry(dsl);
    }

    @Bean
    RatingRepository ratingRepository(DSLContext dsl, JsonMapper json) {
        return new JooqRatingRepository(dsl, json);
    }

    @Bean
    PlanReadRepository planReadRepository(DSLContext dsl) {
        return new JooqPlanReadRepository(dsl);
    }

    @Bean
    RatingReadRepository ratingReadRepository(DSLContext dsl, JsonMapper json) {
        return new JooqRatingReadRepository(dsl, json);
    }

    @Bean
    MetricReadRepository metricReadRepository(DSLContext dsl) {
        return new JooqMetricReadRepository(dsl);
    }

    @Bean
    MetricCatalog metricCatalog(UnitOfWork unitOfWork, MetricReadRepository metrics) {
        return new MetricCatalogService(unitOfWork, metrics);
    }

    @Bean
    ManagePlanUseCase managePlan(
            UnitOfWork unitOfWork, PlanRepository plans, MetricRegistry metrics, EventPublisher events, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        return new ManagePlanUseCase(unitOfWork, plans, metrics, events, auditLog, tenantContext, ids, clock);
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

    @Bean
    PlanPricing planPricing(UnitOfWork unitOfWork, PlanRepository plans) {
        return new PlanPricingService(unitOfWork, plans);
    }
}
