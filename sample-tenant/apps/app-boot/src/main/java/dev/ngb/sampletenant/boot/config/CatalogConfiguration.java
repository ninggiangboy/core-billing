package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.catalog.adapter.plan.persistence.CatalogPlanJdbcRepository;
import dev.ngb.sampletenant.catalog.adapter.plan.persistence.JdbcCatalogPlanRepository;
import dev.ngb.sampletenant.catalog.application.plan.command.AttachBillingPlanUseCase;
import dev.ngb.sampletenant.catalog.application.plan.query.CatalogQueries;
import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the offer catalog and the bootstrap's plan binding. */
@Configuration(proxyBeanMethods = false)
class CatalogConfiguration {

    @Bean
    CatalogPlanRepository catalogPlanRepository(CatalogPlanJdbcRepository rows, JdbcTemplate jdbc) {
        return new JdbcCatalogPlanRepository(rows, jdbc);
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
