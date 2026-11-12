package dev.ngb.tenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.tenant.billing.api.client.BillingPricing;
import dev.ngb.tenant.billing.api.client.BillingSubscriptions;
import dev.ngb.tenant.billing.api.client.BillingWallets;
import dev.ngb.tenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.tenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.tenant.resource.adapter.order.persistence.JooqOrderQueueRepository;
import dev.ngb.tenant.resource.adapter.order.persistence.JooqOrderReadRepository;
import dev.ngb.tenant.resource.adapter.order.persistence.JooqOrderRepository;
import dev.ngb.tenant.resource.api.execution.ExecutionOrders;
import dev.ngb.tenant.resource.api.execution.ExecutionQueue;
import dev.ngb.tenant.resource.application.order.command.CreateOrderUseCase;
import dev.ngb.tenant.resource.application.order.command.ManageOrderUseCase;
import dev.ngb.tenant.resource.application.order.port.OrderQueueRepository;
import dev.ngb.tenant.resource.application.order.port.OrderRepository;
import dev.ngb.tenant.resource.application.order.query.OrderQueries;
import dev.ngb.tenant.resource.application.order.query.OrderReadRepository;
import dev.ngb.tenant.resource.application.order.service.ExecutionOrdersService;
import dev.ngb.tenant.resource.application.order.service.ExecutionQueueService;
import java.time.Duration;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the order flow and the queue row it writes with the order. */
@Configuration(proxyBeanMethods = false)
class ResourceConfiguration {

    @Bean
    OrderRepository orderRepository(DSLContext dsl) {
        return new JooqOrderRepository(dsl);
    }

    @Bean
    OrderReadRepository orderReadRepository(DSLContext dsl) {
        return new JooqOrderReadRepository(dsl);
    }

    @Bean
    OrderQueueRepository orderQueueRepository(DSLContext dsl, Clock clock, IdGenerator ids) {
        return new JooqOrderQueueRepository(dsl, clock, ids);
    }

    @Bean
    ExecutionQueue executionQueue(OrderQueueRepository queue) {
        return new ExecutionQueueService(queue);
    }

    @Bean
    CreateOrderUseCase createOrderUseCase(
            CatalogLookup catalog,
            CustomerDirectory customers,
            @Qualifier("billingPricing") BillingPricing pricing,
            @Qualifier("billingSubscriptions") BillingSubscriptions subscriptions,
            @Qualifier("billingWallets") BillingWallets wallets,
            OrderRepository orders,
            OrderQueueRepository queue,
            UnitOfWork unitOfWork,
            Clock clock,
            IdGenerator ids,
            @Value("${tenant.resource.reservation-ttl:PT15M}") Duration reservationTtl) {
        return new CreateOrderUseCase(
                catalog, customers, pricing, subscriptions, wallets, orders, queue, unitOfWork, clock, ids, reservationTtl);
    }

    @Bean
    ManageOrderUseCase manageOrderUseCase(
            OrderRepository orders,
            CustomerDirectory customers,
            @Qualifier("billingSubscriptions") BillingSubscriptions subscriptions,
            Clock clock) {
        return new ManageOrderUseCase(orders, customers, subscriptions, clock);
    }

    @Bean
    OrderQueries orderQueries(OrderReadRepository orders, CustomerDirectory customers) {
        return new OrderQueries(orders, customers);
    }

    @Bean
    ExecutionOrders executionOrders(OrderRepository orders, Clock clock) {
        return new ExecutionOrdersService(orders, clock);
    }
}
