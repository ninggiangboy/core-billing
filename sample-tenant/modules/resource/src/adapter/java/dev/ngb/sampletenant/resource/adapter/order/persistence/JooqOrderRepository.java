package dev.ngb.sampletenant.resource.adapter.order.persistence;

import static dev.ngb.sampletenant_jooq.Tables.ORDERS;

import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.resource.domain.order.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The write side: the order is inserted once and later transitions update its status. */
public class JooqOrderRepository implements OrderRepository {

    private final DSLContext dsl;

    public JooqOrderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(Order order) {
        dsl.insertInto(ORDERS)
                .columns(
                        ORDERS.ID,
                        ORDERS.CUSTOMER_ID,
                        ORDERS.BILLING_CUSTOMER_ID,
                        ORDERS.WALLET_ID,
                        ORDERS.CATALOG_PLAN_ID,
                        ORDERS.BILLING_PLAN_ID,
                        ORDERS.PRICING_MODEL,
                        ORDERS.METRIC,
                        ORDERS.USAGE_QUANTITY,
                        ORDERS.UNIT,
                        ORDERS.EVENT_COUNT,
                        ORDERS.ESTIMATED_MINOR_UNITS,
                        ORDERS.CURRENCY,
                        ORDERS.RESERVATION_ID,
                        ORDERS.SUBSCRIPTION_ID,
                        ORDERS.STATUS,
                        ORDERS.REFERENCE,
                        ORDERS.FAILURE_REASON,
                        ORDERS.CREATED_AT,
                        ORDERS.UPDATED_AT)
                .values(
                        order.id(),
                        order.customerId(),
                        order.billingCustomerId(),
                        order.walletId(),
                        order.catalogPlanId(),
                        order.billingPlanId(),
                        order.pricingModel(),
                        order.metric(),
                        order.usageQuantity(),
                        order.unit(),
                        order.eventCount(),
                        order.estimatedMinorUnits(),
                        order.currency(),
                        order.reservationId(),
                        order.subscriptionId(),
                        order.status().name(),
                        order.reference(),
                        order.failureReason(),
                        order.createdAt(),
                        order.updatedAt())
                .onConflict(ORDERS.ID)
                .doUpdate()
                .set(ORDERS.STATUS, DSL.excluded(ORDERS.STATUS))
                .set(ORDERS.FAILURE_REASON, DSL.excluded(ORDERS.FAILURE_REASON))
                .set(ORDERS.UPDATED_AT, DSL.excluded(ORDERS.UPDATED_AT))
                .execute();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return dsl.selectFrom(ORDERS).where(ORDERS.ID.eq(id)).fetchOptional().map(JooqOrderRepository::toDomain);
    }

    @Override
    public List<Order> findByBillingCustomerId(String billingCustomerId) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.BILLING_CUSTOMER_ID.eq(billingCustomerId))
                .orderBy(ORDERS.CREATED_AT.desc())
                .fetch(JooqOrderRepository::toDomain);
    }

    private static Order toDomain(dev.ngb.sampletenant_jooq.tables.records.OrdersRecord row) {
        return new Order(
                row.getId(),
                row.getCustomerId(),
                row.getBillingCustomerId(),
                row.getWalletId(),
                row.getCatalogPlanId(),
                row.getBillingPlanId(),
                row.getPricingModel(),
                row.getMetric(),
                row.getUsageQuantity(),
                row.getUnit(),
                row.getEventCount(),
                row.getEstimatedMinorUnits(),
                row.getCurrency(),
                row.getReservationId(),
                row.getSubscriptionId(),
                OrderStatus.valueOf(row.getStatus()),
                row.getReference(),
                row.getFailureReason(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
