package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.billing.api.BillingPricing;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.catalog.api.CatalogLookup;
import dev.ngb.sampletenant.customer.api.CustomerDirectory;
import dev.ngb.sampletenant.resource.adapter.persistence.JdbcOrderRepository;
import dev.ngb.sampletenant.resource.adapter.persistence.JdbcOrderSubmission;
import dev.ngb.sampletenant.resource.adapter.persistence.OrderJdbcRepository;
import dev.ngb.sampletenant.resource.application.CreateOrderUseCase;
import dev.ngb.sampletenant.resource.application.OrderQueries;
import dev.ngb.sampletenant.resource.application.port.OrderRepository;
import dev.ngb.sampletenant.resource.application.port.OrderSubmission;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the order flow and the queue row it writes with the order. */
@Configuration(proxyBeanMethods = false)
class ResourceConfiguration {

    @Bean
    OrderRepository orderRepository(OrderJdbcRepository rows) {
        return new JdbcOrderRepository(rows);
    }

    @Bean
    OrderSubmission orderSubmission(JdbcOrderRepository rows, JdbcTemplate jdbc) {
        return new JdbcOrderSubmission(rows, jdbc);
    }

    @Bean
    CreateOrderUseCase createOrderUseCase(
            CatalogLookup catalog,
            CustomerDirectory customers,
            BillingPricing pricing,
            BillingWallets wallets,
            OrderSubmission submission,
            Clock clock,
            @Value("${sampletenant.resource.reservation-ttl:PT15M}") Duration reservationTtl) {
        return new CreateOrderUseCase(catalog, customers, pricing, wallets, submission, clock, reservationTtl);
    }

    @Bean
    OrderQueries orderQueries(OrderRepository orders, CustomerDirectory customers) {
        return new OrderQueries(orders, customers);
    }
}
