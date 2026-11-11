package dev.ngb.sampletenant.resource.adapter.persistence;

import dev.ngb.sampletenant.resource.application.port.OrderSubmission;
import dev.ngb.sampletenant.resource.domain.Order;
import dev.ngb.sampletenant.shared.id.Ids;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** The order and its queue row are inserted together or not at all. */
public class JdbcOrderSubmission implements OrderSubmission {

    private final JdbcOrderRepository orders;
    private final JdbcTemplate jdbc;

    public JdbcOrderSubmission(JdbcOrderRepository orders, JdbcTemplate jdbc) {
        this.orders = orders;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void submit(Order order) {
        orders.save(order);
        jdbc.update(
                "insert into execution_queue (id, order_id, status, enqueued_at, attempts) values (?, ?, ?, ?, 0)",
                Ids.newId(),
                order.id(),
                "PENDING",
                Timestamp.from(order.createdAt()));
    }
}
