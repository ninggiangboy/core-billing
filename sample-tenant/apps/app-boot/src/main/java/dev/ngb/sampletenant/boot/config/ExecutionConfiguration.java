package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.billing.api.client.BillingCharges;
import dev.ngb.sampletenant.execution.adapter.queue.persistence.JdbcExecutionQueue;
import dev.ngb.sampletenant.execution.application.queue.command.ExecuteOrdersUseCase;
import dev.ngb.sampletenant.execution.application.queue.port.ExecutionQueue;
import dev.ngb.sampletenant.resource.api.execution.ExecutionOrders;
import dev.ngb.sampletenant.usage.api.recording.UsageRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the queue worker and its bound on retries. */
@Configuration(proxyBeanMethods = false)
class ExecutionConfiguration {

    @Bean
    ExecutionQueue executionQueue(JdbcTemplate jdbc, Clock clock) {
        return new JdbcExecutionQueue(jdbc, clock);
    }

    @Bean
    ExecuteOrdersUseCase executeOrdersUseCase(
            ExecutionQueue queue,
            ExecutionOrders orders,
            UsageRecorder usage,
            BillingCharges charges,
            Clock clock,
            @Value("${sampletenant.execution.max-attempts:3}") int maxAttempts) {
        return new ExecuteOrdersUseCase(queue, orders, usage, charges, clock, maxAttempts);
    }
}
