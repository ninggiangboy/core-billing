package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.api.reservation.ReservationStatus;
import io.corebilling.money.application.reservation.port.ReservationRepository;
import io.corebilling.money.domain.reservation.Reservation;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcReservationRepository implements ReservationRepository {

    private static final String COLUMNS = "id, tenant_id, wallet_id, currency, amount, captured_amount, status, expires_at, created_at, "
            + "closed_at, reference, idempotency_key, request_hash";

    private static final RowMapper<Reservation> MAPPER = (rs, n) -> {
        Currency currency = Currency.of(rs.getString("currency"));
        return Reservation.restore(
                new ReservationId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                new WalletId(Rows.uuid(rs, "wallet_id")),
                Money.of(rs.getLong("amount"), currency),
                Money.of(rs.getLong("captured_amount"), currency),
                ReservationStatus.valueOf(rs.getString("status")),
                Rows.instant(rs, "expires_at"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "closed_at"),
                rs.getString("reference"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"));
    };

    private final JdbcClient jdbc;

    public JdbcReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Reservation r) {
        try {
            jdbc.sql("insert into reservations (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(r.id().value(), r.tenantId().value(), r.walletId().value(), r.amount().currency().code(), r.amount().minorUnits(),
                            r.captured().minorUnits(), r.status().name(), Timestamp.from(r.expiresAt()), Timestamp.from(r.createdAt()),
                            r.closedAt() == null ? null : Timestamp.from(r.closedAt()), r.reference(), r.idempotencyKey(), r.requestHash())
                    .update();
        } catch (RuntimeException e) {
            if (Rows.duplicate(e)) {
                throw new ConflictException("this idempotency key was already used for a reservation");
            }
            throw e;
        }
    }

    @Override
    public void update(Reservation r) {
        int updated = jdbc.sql("update reservations set status = ?, captured_amount = ?, closed_at = ? where id = ?")
                .params(r.status().name(), r.captured().minorUnits(), r.closedAt() == null ? null : Timestamp.from(r.closedAt()), r.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("reservation " + r.id() + " was not updated");
        }
    }

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return jdbc.sql("select " + COLUMNS + " from reservations where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from reservations where idempotency_key = ?").param(idempotencyKey).query(MAPPER).optional();
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
