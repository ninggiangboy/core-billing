package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.catalog.adapter.persistence.CatalogPlanJdbcRepository;
import dev.ngb.sampletenant.catalog.adapter.persistence.JdbcCatalogPlanRepository;
import dev.ngb.sampletenant.catalog.application.AttachBillingPlanUseCase;
import dev.ngb.sampletenant.catalog.application.CatalogQueries;
import dev.ngb.sampletenant.catalog.application.port.CatalogPlanRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the offer catalog and the bootstrap's plan binding. */
@Configuration(proxyBeanMethods = false)
class CatalogConfiguration {

    @Bean
    CatalogPlanRepository catalogPlanRepository(CatalogPlanJdbcRepository rows) {
        return new JdbcCatalogPlanRepository(rows);
    }

    @Bean
    CatalogQueries catalogQueries(CatalogPlanRepository plans) {
        return new CatalogQueries(plans);
    }

    @Bean
    AttachBillingPlanUseCase attachBillingPlanUseCase(CatalogPlanRepository plans) {
        return new AttachBillingPlanUseCase(plans);
    }
}
