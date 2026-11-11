package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.resource.api.ExecutionOrders;
import dev.ngb.sampletenant.webhook.adapter.persistence.JdbcCustomerPeriods;
import dev.ngb.sampletenant.webhook.adapter.persistence.JdbcDebtBook;
import dev.ngb.sampletenant.webhook.adapter.persistence.JdbcWebhookRepository;
import dev.ngb.sampletenant.webhook.application.ReceiveWebhookUseCase;
import dev.ngb.sampletenant.webhook.application.WebhookPolicy;
import dev.ngb.sampletenant.webhook.application.port.CustomerPeriods;
import dev.ngb.sampletenant.webhook.application.port.DebtBook;
import dev.ngb.sampletenant.webhook.application.port.WebhookRepository;
import java.time.Clock;
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
    DebtBook debtBook(JdbcTemplate jdbc) {
        return new JdbcDebtBook(jdbc);
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
            @Value("${sampletenant.webhooks.signing-secret:}") String signingSecret,
            @Value("${sampletenant.webhooks.tolerance:PT5M}") Duration tolerance) {
        return new ReceiveWebhookUseCase(events, policy, clock, signingSecret, tolerance);
    }
}
