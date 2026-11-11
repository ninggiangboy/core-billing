package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.pricing.api.metric.MetricCatalog;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.usage.adapter.usage.persistence.JooqUsageEventRepository;
import dev.ngb.core_billing.usage.adapter.usage.persistence.JooqUsageEventReadRepository;
import dev.ngb.core_billing.usage.application.usage.port.MetricUnits;
import dev.ngb.core_billing.usage.application.usage.command.RecordUsageUseCase;
import dev.ngb.core_billing.usage.application.usage.port.UsageEventRepository;
import dev.ngb.core_billing.usage.application.usage.query.UsageEventReadRepository;
import dev.ngb.core_billing.usage.application.usage.query.UsageQueries;
import dev.ngb.core_billing.usage.application.usage.service.UsageLookupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jooq.DSLContext;

/**
 * Wires the usage module. Holds no business rules. The one connection between usage and pricing lives here:
 * usage asks "which unit does this metric have?" through its own port, and the answer comes from the pricing
 * catalog, so neither module depends on the other's internals.
 */
@Configuration(proxyBeanMethods = false)
class UsageConfiguration {

    @Bean
    UsageEventRepository usageEventRepository(DSLContext dsl) {
        return new JooqUsageEventRepository(dsl);
    }

    @Bean
    UsageEventReadRepository usageEventReadRepository(DSLContext dsl) {
        return new JooqUsageEventReadRepository(dsl);
    }

    @Bean
    MetricUnits metricUnits(MetricCatalog catalog) {
        return catalog::unitOf;
    }

    @Bean
    RecordUsageUseCase recordUsageUseCase(
            UnitOfWork unitOfWork, UsageEventRepository events, CustomerLookup customers, MetricUnits metricUnits, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        return new RecordUsageUseCase(unitOfWork, events, customers, metricUnits, tenantContext, ids, clock);
    }

    @Bean
    UsageQueries usageQueries(UnitOfWork unitOfWork, UsageEventReadRepository events, CustomerLookup customers) {
        return new UsageQueries(unitOfWork, events, customers);
    }

    /** The {@code UsageLookup} other contexts read usage through. */
    @Bean
    UsageLookupService usageLookup(UsageQueries queries) {
        return new UsageLookupService(queries);
    }
}
