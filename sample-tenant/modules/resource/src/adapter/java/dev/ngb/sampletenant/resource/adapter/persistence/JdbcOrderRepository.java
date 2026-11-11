package dev.ngb.sampletenant.resource.adapter.persistence;

import dev.ngb.sampletenant.resource.application.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.Order;
import dev.ngb.sampletenant.resource.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcOrderRepository implements OrderRepository {

    private final OrderJdbcRepository rows;
    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(OrderJdbcRepository rows, JdbcTemplate jdbc) {
        this.rows = rows;
        this.jdbc = jdbc;
    }

    @Override
    public void save(Order order) {
        jdbc.update(
                """
                insert into orders
                    (id, customer_id, billing_customer_id, wallet_id, catalog_plan_id, billing_plan_id,
                     pricing_model, metric, usage_quantity, unit, event_count, estimated_minor_units,
                     currency, reservation_id, subscription_id, status, reference, failure_reason,
                     created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update
                    set status = excluded.status,
                        failure_reason = excluded.failure_reason,
                        updated_at = excluded.updated_at
                """,
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
                java.sql.Timestamp.from(order.createdAt()),
                java.sql.Timestamp.from(order.updatedAt()));
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
                row.subscriptionId(),
                OrderStatus.valueOf(row.status()),
                row.reference(),
                row.failureReason(),
                row.createdAt(),
                row.updatedAt());
    }
}
