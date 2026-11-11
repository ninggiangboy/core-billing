package dev.ngb.core_billing.money.adapter.persistence.ledger;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.core_billing.money.application.ledger.query.LedgerReadRepository;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects ledger entries straight into the view; the aggregate is never built. */
public class JdbcLedgerReadRepository implements LedgerReadRepository {

    private static final String COLUMNS = "id, seq, type, amount, currency, reference, reservation_id, charge_id, reversal_of, actor, reason, created_at";

    private record Row(
            UUID id,
            long seq,
            String type,
            long amount,
            String currency,
            String reference,
            UUID reservationId,
            UUID chargeId,
            UUID reversalOf,
            String actor,
            String reason,
            Instant createdAt) {

        LedgerEntryView toView() {
            return new LedgerEntryView(
                    new LedgerEntryId(id),
                    seq,
                    EntryType.valueOf(type),
                    Money.of(amount, Currency.of(currency.strip())),
                    reference,
                    reservationId == null ? null : new ReservationId(reservationId),
                    chargeId == null ? null : new ChargeId(chargeId),
                    reversalOf == null ? null : new LedgerEntryId(reversalOf),
                    actor,
                    reason,
                    createdAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcLedgerReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LedgerEntryView> page(WalletId walletId, long afterSequence, int limit) {
        return jdbc.sql("""
                        select %s
                        from ledger_entries
                        where wallet_id = ? and seq > ?
                        order by seq
                        limit ?
                        """.formatted(COLUMNS))
                .params(walletId.value(), afterSequence, limit)
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }
}
