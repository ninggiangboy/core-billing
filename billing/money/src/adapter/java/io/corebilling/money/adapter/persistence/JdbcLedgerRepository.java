package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.ledger.LedgerEntryId;
import io.corebilling.money.application.ledger.port.LedgerRepository;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.platform.api.error.ConflictException;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Insert and read only; the database also refuses anything else. */
public class JdbcLedgerRepository implements LedgerRepository {

    private static final String COLUMNS = "id, tenant_id, wallet_id, currency, seq, type, amount, reference, reservation_id, charge_id, "
            + "reversal_of, actor, reason, idempotency_key, created_at";

    private record BalanceRow(long total, String currency) {}

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcLedgerRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void append(LedgerEntry entry) {
        LedgerEntryRow row = LedgerEntryRow.from(entry);
        try {
            jdbc.sql("insert into ledger_entries (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(row.id(), row.tenantId(), row.walletId(), row.currency(), row.seq(), row.type(), row.amount(), row.reference(),
                            row.reservationId(), row.chargeId(), row.reversalOf(), row.actor(), row.reason(), row.idempotencyKey(),
                            Timestamp.from(row.createdAt()))
                    .update();
        } catch (RuntimeException e) {
            if (Rows.duplicate(e)) {
                throw new ConflictException("this idempotency key or entry was already posted");
            }
            throw e;
        }
    }

    @Override
    public Optional<LedgerEntry> findById(LedgerEntryId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), LedgerEntryRow.class)).map(LedgerEntryRow::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where idempotency_key = ?").param(idempotencyKey).query(LedgerEntryRow.class)
                .optional()
                .map(LedgerEntryRow::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findByCharge(ChargeId chargeId) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where charge_id = ?").param(chargeId.value()).query(LedgerEntryRow.class)
                .optional()
                .map(LedgerEntryRow::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findReversalOf(LedgerEntryId original) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where reversal_of = ?").param(original.value()).query(LedgerEntryRow.class)
                .optional()
                .map(LedgerEntryRow::toDomain);
    }

    @Override
    public Money balanceThrough(WalletId walletId, long sequence) {
        BalanceRow row = jdbc.sql("select coalesce(sum(amount), 0) as total, max(currency) as currency from ledger_entries where wallet_id = ? and seq <= ?")
                .params(walletId.value(), sequence)
                .query(BalanceRow.class)
                .single();
        return Money.of(row.total(), Currency.of(row.currency().strip()));
    }
}
