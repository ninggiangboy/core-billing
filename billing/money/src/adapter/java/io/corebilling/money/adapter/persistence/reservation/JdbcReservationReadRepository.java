package io.corebilling.money.adapter.persistence.reservation;

import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.api.reservation.ReservationStatus;
import io.corebilling.money.application.reservation.query.ReservationReadRepository;
import io.corebilling.money.application.reservation.query.ReservationView;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects a reservation straight into the view; the aggregate is never built. */
public class JdbcReservationReadRepository implements ReservationReadRepository {

    private static final String COLUMNS = "id, wallet_id, currency, amount, captured_amount, status, expires_at, reference, created_at, closed_at";

    private record Row(
            UUID id,
            UUID walletId,
            String currency,
            long amount,
            long capturedAmount,
            String status,
            Instant expiresAt,
            String reference,
            Instant createdAt,
            Instant closedAt) {

        ReservationView toView() {
            Currency money = Currency.of(currency.strip());
            return new ReservationView(
                    new ReservationId(id),
                    new WalletId(walletId),
                    ReservationStatus.valueOf(status),
                    Money.of(amount, money),
                    Money.of(capturedAmount, money),
                    expiresAt,
                    reference,
                    createdAt,
                    closedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcReservationReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReservationView> findById(ReservationId id) {
        return jdbc.sql("select " + COLUMNS + " from reservations where id = ?").param(id.value()).query(Row.class).optional().map(Row::toView);
    }
}
