package dev.ngb.core_billing.money.adapter.persistence.ledger;

import static dev.ngb.core_billing_jooq.Tables.LEDGER_ENTRIES;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.core_billing.money.application.ledger.query.LedgerReadRepository;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing_jooq.tables.records.LedgerEntriesRecord;
import java.util.List;
import org.jooq.DSLContext;

/** Selects ledger entries straight into the view; the aggregate is never built. */
public class JooqLedgerReadRepository implements LedgerReadRepository {

    private final DSLContext dsl;

    public JooqLedgerReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<LedgerEntryView> page(WalletId walletId, long afterSequence, int limit) {
        return dsl.selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.WALLET_ID.eq(walletId.value()))
                .and(LEDGER_ENTRIES.SEQ.gt(afterSequence))
                .orderBy(LEDGER_ENTRIES.SEQ)
                .limit(limit)
                .fetch(JooqLedgerReadRepository::toView);
    }

    private static LedgerEntryView toView(LedgerEntriesRecord row) {
        return new LedgerEntryView(
                new LedgerEntryId(row.getId()),
                row.getSeq(),
                EntryType.valueOf(row.getType()),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                row.getReference(),
                row.getReservationId() == null ? null : new ReservationId(row.getReservationId()),
                row.getChargeId() == null ? null : new ChargeId(row.getChargeId()),
                row.getReversalOf() == null ? null : new LedgerEntryId(row.getReversalOf()),
                row.getActor(),
                row.getReason(),
                row.getCreatedAt());
    }
}
