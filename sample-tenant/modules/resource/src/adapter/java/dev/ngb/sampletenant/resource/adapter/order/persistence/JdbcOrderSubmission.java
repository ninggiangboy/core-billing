package dev.ngb.sampletenant.resource.adapter.order.persistence;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.sampletenant.resource.application.order.port.OrderSubmission;
import dev.ngb.sampletenant.resource.domain.order.Order;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;

/** The order and its queue row are inserted together or not at all. */
public class JdbcOrderSubmission implements OrderSubmission {

    private final JdbcOrderRepository orders;
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;

    public JdbcOrderSubmission(JdbcOrderRepository orders, JdbcTemplate jdbc, IdGenerator ids, UnitOfWork unitOfWork) {
        this.orders = orders;
        this.jdbc = jdbc;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public void submit(Order order) {
        unitOfWork.transactional(() -> {
            orders.save(order);
            jdbc.update(
                    "insert into execution_queue (id, order_id, status, enqueued_at, attempts) values (?, ?, ?, ?, 0)",
                    ids.newId(),
                    order.id(),
                    "PENDING",
                    Timestamp.from(order.createdAt()));
        });
    }
}
