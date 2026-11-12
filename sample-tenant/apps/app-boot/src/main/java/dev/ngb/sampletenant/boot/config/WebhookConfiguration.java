package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.resource.api.execution.ExecutionOrders;
import dev.ngb.sampletenant.webhook.adapter.event.persistence.JooqCustomerPeriods;
import dev.ngb.sampletenant.webhook.adapter.event.persistence.JooqDebtBook;
import dev.ngb.sampletenant.webhook.adapter.event.persistence.JooqWebhookRepository;
import dev.ngb.sampletenant.webhook.application.event.command.ReceiveWebhookUseCase;
import dev.ngb.sampletenant.webhook.application.event.port.CustomerPeriods;
import dev.ngb.sampletenant.webhook.application.event.port.DebtBook;
import dev.ngb.sampletenant.webhook.application.event.port.WebhookRepository;
import dev.ngb.sampletenant.webhook.application.event.service.WebhookPolicy;
import java.time.Duration;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires inbound delivery verification and the tenant's policy for each event. */
@Configuration(proxyBeanMethods = false)
class WebhookConfiguration {

    @Bean
    WebhookRepository webhookRepository(DSLContext dsl) {
        return new JooqWebhookRepository(dsl);
    }

    @Bean
    DebtBook debtBook(DSLContext dsl, IdGenerator ids) {
        return new JooqDebtBook(dsl, ids);
    }

    @Bean
    CustomerPeriods customerPeriods(DSLContext dsl) {
        return new JooqCustomerPeriods(dsl);
    }

    @Bean
    WebhookPolicy webhookPolicy(DebtBook debts, CustomerPeriods periods, ExecutionOrders orders, Clock clock) {
        return new WebhookPolicy(debts, periods, orders, clock);
    }

    @Bean
    ReceiveWebhookUseCase receiveWebhookUseCase(
            WebhookRepository events,
            WebhookPolicy policy,
            Clock clock,
            IdGenerator ids,
            @Value("${sampletenant.webhooks.signing-secret:}") String signingSecret,
            @Value("${sampletenant.webhooks.tolerance:PT5M}") Duration tolerance) {
        return new ReceiveWebhookUseCase(events, policy, clock, ids, signingSecret, tolerance);
    }
}
