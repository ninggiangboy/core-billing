package io.corebilling.money.adapter.persistence.charge;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.charge.ChargeStatus;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.application.charge.query.ChargeReadRepository;
import io.corebilling.money.application.charge.query.ChargeView;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects a charge straight into the view; the aggregate is never built. */
public class JdbcChargeReadRepository implements ChargeReadRepository {

    private static final String COLUMNS = "id, currency, amount, status, failure_reason, reservation_id, usage_ref, reference";

    private record Row(
            UUID id,
            String currency,
            long amount,
            String status,
            String failureReason,
            UUID reservationId,
            String usageRef,
            String reference) {

        ChargeView toView() {
            return new ChargeView(
                    new ChargeId(id),
                    ChargeStatus.valueOf(status),
                    failureReason,
                    Money.of(amount, Currency.of(currency.strip())),
                    reservationId == null ? null : new ReservationId(reservationId),
                    usageRef,
                    reference);
        }
    }

    private final JdbcClient jdbc;

    public JdbcChargeReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChargeView> findById(ChargeId id) {
        return jdbc.sql("select " + COLUMNS + " from charges where id = ?").param(id.value()).query(Row.class).optional().map(Row::toView);
    }
}
