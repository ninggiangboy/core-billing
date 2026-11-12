package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.sampletenant.billing.api.BillingPricing;
import dev.ngb.sampletenant.billing.api.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.customer.api.CustomerDirectory;
import dev.ngb.sampletenant.resource.adapter.persistence.JdbcOrderRepository;
import dev.ngb.sampletenant.resource.adapter.persistence.JdbcOrderSubmission;
import dev.ngb.sampletenant.resource.adapter.persistence.OrderJdbcRepository;
import dev.ngb.sampletenant.resource.api.ExecutionOrders;
import dev.ngb.sampletenant.resource.application.CreateOrderUseCase;
import dev.ngb.sampletenant.resource.application.ManageOrderUseCase;
import dev.ngb.sampletenant.resource.application.OrderExecutionService;
import dev.ngb.sampletenant.resource.application.OrderQueries;
import dev.ngb.sampletenant.resource.application.port.OrderRepository;
import dev.ngb.sampletenant.resource.application.port.OrderSubmission;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the order flow and the queue row it writes with the order. */
@Configuration(proxyBeanMethods = false)
class ResourceConfiguration {

    @Bean
    OrderRepository orderRepository(OrderJdbcRepository rows, JdbcTemplate jdbc) {
        return new JdbcOrderRepository(rows, jdbc);
    }

    @Bean
    OrderSubmission orderSubmission(JdbcOrderRepository rows, JdbcTemplate jdbc, IdGenerator ids, UnitOfWork unitOfWork) {
        return new JdbcOrderSubmission(rows, jdbc, ids, unitOfWork);
    }

    @Bean
    CreateOrderUseCase createOrderUseCase(
            CatalogLookup catalog,
            CustomerDirectory customers,
            @Qualifier("billingPricing") BillingPricing pricing,
            @Qualifier("billingSubscriptions") BillingSubscriptions subscriptions,
            @Qualifier("billingWallets") BillingWallets wallets,
            OrderSubmission submission,
            Clock clock,
            IdGenerator ids,
            @Value("${sampletenant.resource.reservation-ttl:PT15M}") Duration reservationTtl) {
        return new CreateOrderUseCase(
                catalog, customers, pricing, subscriptions, wallets, submission, clock, ids, reservationTtl);
    }

    @Bean
    ManageOrderUseCase manageOrderUseCase(
            OrderQueries queries,
            OrderRepository orders,
            @Qualifier("billingSubscriptions") BillingSubscriptions subscriptions,
            Clock clock) {
        return new ManageOrderUseCase(queries, orders, subscriptions, clock);
    }

    @Bean
    OrderQueries orderQueries(OrderRepository orders, CustomerDirectory customers) {
        return new OrderQueries(orders, customers);
    }

    @Bean
    ExecutionOrders executionOrders(OrderRepository orders, Clock clock) {
        return new OrderExecutionService(orders, clock);
    }
}
