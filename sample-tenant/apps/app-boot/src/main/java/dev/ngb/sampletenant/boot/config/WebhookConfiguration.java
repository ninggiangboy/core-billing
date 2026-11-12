package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.resource.api.execution.ExecutionOrders;
import dev.ngb.sampletenant.webhook.adapter.event.persistence.JdbcCustomerPeriods;
import dev.ngb.sampletenant.webhook.adapter.event.persistence.JdbcDebtBook;
import dev.ngb.sampletenant.webhook.adapter.event.persistence.JdbcWebhookRepository;
import dev.ngb.sampletenant.webhook.application.event.command.ReceiveWebhookUseCase;
import dev.ngb.sampletenant.webhook.application.event.service.WebhookPolicy;
import dev.ngb.sampletenant.webhook.application.event.port.CustomerPeriods;
import dev.ngb.sampletenant.webhook.application.event.port.DebtBook;
import dev.ngb.sampletenant.webhook.application.event.port.WebhookRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires inbound delivery verification and the tenant's policy for each event. */
@Configuration(proxyBeanMethods = false)
class WebhookConfiguration {

    @Bean
    WebhookRepository webhookRepository(JdbcTemplate jdbc) {
        return new JdbcWebhookRepository(jdbc);
    }

    @Bean
    DebtBook debtBook(JdbcTemplate jdbc, IdGenerator ids) {
        return new JdbcDebtBook(jdbc, ids);
    }

    @Bean
    CustomerPeriods customerPeriods(JdbcTemplate jdbc) {
        return new JdbcCustomerPeriods(jdbc);
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
