package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.application.reservation.port.ReservationRepository;
import io.corebilling.money.domain.reservation.Reservation;
import io.corebilling.platform.api.error.ConflictException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcReservationRepository implements ReservationRepository {

    private static final String COLUMNS = "id, tenant_id, wallet_id, currency, amount, captured_amount, status, expires_at, created_at, "
            + "closed_at, reference, idempotency_key, request_hash";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcReservationRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Reservation r) {
        try {
            aggregates.insert(ReservationRow.from(r));
        } catch (RuntimeException e) {
            if (Rows.duplicate(e)) {
                throw new ConflictException("this idempotency key was already used for a reservation");
            }
            throw e;
        }
    }

    @Override
    public void update(Reservation r) {
        aggregates.update(ReservationRow.from(r));
    }

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), ReservationRow.class)).map(ReservationRow::toDomain);
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from reservations where idempotency_key = ?").param(idempotencyKey).query(ReservationRow.class)
                .optional()
                .map(ReservationRow::toDomain);
    }

    @Override
    public List<ReservationId> findExpired(Instant now, int limit) {
        return jdbc.sql("select id from reservations where status = 'ACTIVE' and expires_at <= ? order by expires_at limit ?")
                .params(Timestamp.from(now), limit)
                .query(UUID.class)
                .list()
                .stream()
                .map(ReservationId::new)
                .toList();
    }
}
