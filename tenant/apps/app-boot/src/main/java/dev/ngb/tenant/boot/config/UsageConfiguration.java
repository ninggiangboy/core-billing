package dev.ngb.tenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.billing.api.client.BillingUsage;
import dev.ngb.tenant.usage.adapter.usage.persistence.JooqUsageReadRepository;
import dev.ngb.tenant.usage.adapter.usage.persistence.JooqUsageRepository;
import dev.ngb.tenant.usage.application.usage.command.RecordUsageUseCase;
import dev.ngb.tenant.usage.application.usage.port.UsageRepository;
import dev.ngb.tenant.usage.application.usage.query.UsageQueries;
import dev.ngb.tenant.usage.application.usage.query.UsageReadRepository;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires local usage records and their forwarding to Billing. */
@Configuration(proxyBeanMethods = false)
class UsageConfiguration {

    @Bean
    UsageRepository usageRepository(DSLContext dsl) {
        return new JooqUsageRepository(dsl);
    }

    @Bean
    UsageReadRepository usageReadRepository(DSLContext dsl) {
        return new JooqUsageReadRepository(dsl);
    }

    @Bean
    RecordUsageUseCase recordUsageUseCase(
            UsageRepository usage, @Qualifier("billingUsage") BillingUsage billing, Clock clock, IdGenerator ids) {
        return new RecordUsageUseCase(usage, billing, clock, ids);
    }

    @Bean
    UsageQueries usageQueries(UsageReadRepository usage) {
        return new UsageQueries(usage);
    }
}
