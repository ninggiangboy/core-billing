package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.billing.api.client.BillingUsage;
import dev.ngb.sampletenant.usage.adapter.usage.persistence.JdbcUsageRepository;
import dev.ngb.sampletenant.usage.adapter.usage.persistence.UsageJdbcRepository;
import dev.ngb.sampletenant.usage.application.usage.command.RecordUsageUseCase;
import dev.ngb.sampletenant.usage.application.usage.query.UsageQueries;
import dev.ngb.sampletenant.usage.application.usage.port.UsageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires local usage records and their forwarding to Billing. */
@Configuration(proxyBeanMethods = false)
class UsageConfiguration {

    @Bean
    UsageRepository usageRepository(UsageJdbcRepository rows, JdbcTemplate jdbc) {
        return new JdbcUsageRepository(rows, jdbc);
    }

    @Bean
    RecordUsageUseCase recordUsageUseCase(
            UsageRepository usage, BillingUsage billing, Clock clock, IdGenerator ids) {
        return new RecordUsageUseCase(usage, billing, clock, ids);
    }

    @Bean
    UsageQueries usageQueries(UsageRepository usage) {
        return new UsageQueries(usage);
    }
}
