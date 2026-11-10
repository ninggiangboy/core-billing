package io.corebilling.money.adapter.persistence.ledger;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.ledger.EntryType;
import io.corebilling.money.api.ledger.LedgerEntryId;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.application.ledger.query.LedgerEntryView;
import io.corebilling.money.application.ledger.query.LedgerReadRepository;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
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
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where wallet_id = ? and seq > ? order by seq limit ?")
                .params(walletId.value(), afterSequence, limit)
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }
}
