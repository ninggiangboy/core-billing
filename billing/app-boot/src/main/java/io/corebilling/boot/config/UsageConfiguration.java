package io.corebilling.boot.config;

import io.corebilling.customer.api.CustomerLookup;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.pricing.api.MetricCatalog;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.usage.adapter.persistence.JdbcUsageEventRepository;
import io.corebilling.usage.application.MetricUnits;
import io.corebilling.usage.application.RecordUsageUseCase;
import io.corebilling.usage.application.UsageEventRepository;
import io.corebilling.usage.application.UsageQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Wires the usage module. Holds no business rules. The one connection between usage and pricing lives here:
 * usage asks "which unit does this metric have?" through its own port, and the answer comes from the pricing
 * catalog, so neither module depends on the other's internals.
 */
@Configuration(proxyBeanMethods = false)
class UsageConfiguration {

    @Bean
    UsageEventRepository usageEventRepository(JdbcClient jdbc) {
        return new JdbcUsageEventRepository(jdbc);
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

    /** Also the {@code UsageLookup} other contexts read usage through. */
    @Bean
    UsageQueries usageQueries(UnitOfWork unitOfWork, UsageEventRepository events, CustomerLookup customers) {
        return new UsageQueries(unitOfWork, events, customers);
    }
}
