package dev.ngb.tenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.resource.api.execution.ExecutionOrders;
import dev.ngb.tenant.webhook.adapter.event.persistence.JooqCustomerPeriods;
import dev.ngb.tenant.webhook.adapter.event.persistence.JooqDebtBook;
import dev.ngb.tenant.webhook.adapter.event.persistence.JooqWebhookRepository;
import dev.ngb.tenant.webhook.application.event.command.ReceiveWebhookUseCase;
import dev.ngb.tenant.webhook.application.event.port.CustomerPeriods;
import dev.ngb.tenant.webhook.application.event.port.DebtBook;
import dev.ngb.tenant.webhook.application.event.port.WebhookRepository;
import dev.ngb.tenant.webhook.application.event.service.WebhookPolicy;
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
            @Value("${tenant.webhooks.signing-secret:}") String signingSecret,
            @Value("${tenant.webhooks.tolerance:PT5M}") Duration tolerance) {
        return new ReceiveWebhookUseCase(events, policy, clock, ids, signingSecret, tolerance);
    }
}
