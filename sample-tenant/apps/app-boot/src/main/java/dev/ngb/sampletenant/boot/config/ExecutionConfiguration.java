package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.billing.api.client.BillingCharges;
import dev.ngb.sampletenant.execution.application.queue.command.ExecuteOrdersUseCase;
import dev.ngb.sampletenant.resource.api.execution.ExecutionOrders;
import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue;
import dev.ngb.sampletenant.usage.api.recording.UsageRecorder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the queue worker and its bound on retries. */
@Configuration(proxyBeanMethods = false)
class ExecutionConfiguration {

    @Bean
    ExecuteOrdersUseCase executeOrdersUseCase(
            ExecutionQueue queue,
            ExecutionOrders orders,
            UsageRecorder usage,
            @Qualifier("billingCharges") BillingCharges charges,
            Clock clock,
            @Value("${sampletenant.execution.max-attempts:3}") int maxAttempts) {
        return new ExecuteOrdersUseCase(queue, orders, usage, charges, clock, maxAttempts);
    }
}
