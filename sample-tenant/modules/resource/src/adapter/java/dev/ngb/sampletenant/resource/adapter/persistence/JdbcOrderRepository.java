package dev.ngb.sampletenant.resource.adapter.persistence;

import dev.ngb.sampletenant.resource.application.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.Order;
import dev.ngb.sampletenant.resource.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcOrderRepository implements OrderRepository {

    private final OrderJdbcRepository rows;

    public JdbcOrderRepository(OrderJdbcRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(Order order) {
        rows.save(new OrderRow(
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
                order.status().name(),
                order.reference(),
                order.failureReason(),
                order.createdAt(),
                order.updatedAt()));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return rows.findById(id).map(JdbcOrderRepository::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(UUID customerId) {
        return rows.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(JdbcOrderRepository::toDomain)
                .toList();
    }

    @Override
    public List<Order> findByBillingCustomerId(String billingCustomerId) {
        return rows.findByBillingCustomerIdOrderByCreatedAtDesc(billingCustomerId).stream()
                .map(JdbcOrderRepository::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return rows.findAllByOrderByCreatedAtDesc().stream().map(JdbcOrderRepository::toDomain).toList();
    }

    private static Order toDomain(OrderRow row) {
        return new Order(
                row.id(),
                row.customerId(),
                row.billingCustomerId(),
                row.walletId(),
                row.catalogPlanId(),
                row.billingPlanId(),
                row.pricingModel(),
                row.metric(),
                row.usageQuantity(),
                row.unit(),
                row.eventCount(),
                row.estimatedMinorUnits(),
                row.currency(),
                row.reservationId(),
                OrderStatus.valueOf(row.status()),
                row.reference(),
                row.failureReason(),
                row.createdAt(),
                row.updatedAt());
    }
}
