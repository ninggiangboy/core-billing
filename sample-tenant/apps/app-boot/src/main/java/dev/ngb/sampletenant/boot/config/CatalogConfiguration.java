package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.catalog.adapter.plan.persistence.JooqCatalogPlanReadRepository;
import dev.ngb.sampletenant.catalog.adapter.plan.persistence.JooqCatalogPlanRepository;
import dev.ngb.sampletenant.catalog.application.plan.command.AttachBillingPlanUseCase;
import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.application.plan.query.CatalogPlanReadRepository;
import dev.ngb.sampletenant.catalog.application.plan.query.CatalogQueries;
import dev.ngb.sampletenant.catalog.application.plan.service.CatalogLookupService;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the offer catalog and the bootstrap's plan binding. */
@Configuration(proxyBeanMethods = false)
class CatalogConfiguration {

    @Bean
    CatalogPlanRepository catalogPlanRepository(DSLContext dsl) {
        return new JooqCatalogPlanRepository(dsl);
    }

    @Bean
    CatalogPlanReadRepository catalogPlanReadRepository(DSLContext dsl) {
        return new JooqCatalogPlanReadRepository(dsl);
    }

    @Bean
    CatalogQueries catalogQueries(CatalogPlanReadRepository plans) {
        return new CatalogQueries(plans);
    }

    @Bean
    CatalogLookupService catalogLookupService(CatalogPlanReadRepository plans) {
        return new CatalogLookupService(plans);
    }

    @Bean
    AttachBillingPlanUseCase attachBillingPlanUseCase(CatalogPlanRepository plans) {
        return new AttachBillingPlanUseCase(plans);
    }
}
