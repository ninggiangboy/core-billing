package dev.ngb.core_billing.money.adapter.persistence;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import org.springframework.dao.DuplicateKeyException;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
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
            if (Causes.contains(e, DuplicateKeyException.class)) {
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
