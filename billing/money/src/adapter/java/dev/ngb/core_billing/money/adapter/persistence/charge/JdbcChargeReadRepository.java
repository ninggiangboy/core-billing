package dev.ngb.core_billing.money.adapter.persistence.charge;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeSource;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.charge.query.ChargeReadRepository;
import dev.ngb.core_billing.money.application.charge.query.ChargeView;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects a charge straight into the view; the aggregate is never built. */
public class JdbcChargeReadRepository implements ChargeReadRepository {

    private static final String COLUMNS = "id, wallet_id, currency, amount, status, failure_reason, reservation_id, usage_ref, reference, source, created_at";

    private record Row(
            UUID id,
            UUID walletId,
            String currency,
            long amount,
            String status,
            String failureReason,
            UUID reservationId,
            String usageRef,
            String reference,
            String source,
            Instant createdAt) {

        ChargeView toView() {
            return new ChargeView(
                    new ChargeId(id),
                    new WalletId(walletId),
                    ChargeStatus.valueOf(status),
                    failureReason,
                    Money.of(amount, Currency.of(currency.strip())),
                    reservationId == null ? null : new ReservationId(reservationId),
                    usageRef,
                    reference,
                    ChargeSource.valueOf(source),
                    createdAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcChargeReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChargeView> findById(ChargeId id) {
        return jdbc.sql("""
                        select %s
                        from charges
                        where id = ?
                        """.formatted(COLUMNS)).param(id.value()).query(Row.class).optional().map(Row::toView);
    }
}
