package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.sampletenant.billing.api.client.BillingPricing;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.resource.adapter.order.persistence.JdbcOrderRepository;
import dev.ngb.sampletenant.resource.adapter.order.persistence.JdbcOrderSubmission;
import dev.ngb.sampletenant.resource.adapter.order.persistence.OrderJdbcRepository;
import dev.ngb.sampletenant.resource.api.execution.ExecutionOrders;
import dev.ngb.sampletenant.resource.application.order.command.CreateOrderUseCase;
import dev.ngb.sampletenant.resource.application.order.command.ManageOrderUseCase;
import dev.ngb.sampletenant.resource.application.order.service.ExecutionOrdersService;
import dev.ngb.sampletenant.resource.application.order.query.OrderQueries;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.application.order.port.OrderSubmission;
import java.time.Duration;
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
            BillingPricing pricing,
            BillingSubscriptions subscriptions,
            BillingWallets wallets,
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
            BillingSubscriptions subscriptions,
            Clock clock) {
        return new ManageOrderUseCase(queries, orders, subscriptions, clock);
    }

    @Bean
    OrderQueries orderQueries(OrderRepository orders, CustomerDirectory customers) {
        return new OrderQueries(orders, customers);
    }

    @Bean
    ExecutionOrders executionOrders(OrderRepository orders, Clock clock) {
        return new ExecutionOrdersService(orders, clock);
    }
}
