package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.ChargeId;
import io.corebilling.money.api.EntryType;
import io.corebilling.money.api.LedgerEntryId;
import io.corebilling.money.api.ReservationId;
import io.corebilling.money.application.LedgerRepository;
import io.corebilling.money.domain.LedgerEntry;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Insert and read only; the database also refuses anything else. */
public class JdbcLedgerRepository implements LedgerRepository {

    private static final String COLUMNS = "id, tenant_id, wallet_id, currency, seq, type, amount, reference, reservation_id, charge_id, "
            + "reversal_of, actor, reason, idempotency_key, created_at";

    private static final RowMapper<LedgerEntry> MAPPER = (rs, n) -> {
        Currency currency = Currency.of(rs.getString("currency"));
        UUID reservation = Rows.uuid(rs, "reservation_id");
        UUID charge = Rows.uuid(rs, "charge_id");
        UUID reversalOf = Rows.uuid(rs, "reversal_of");
        return LedgerEntry.restore(
                new LedgerEntryId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                new WalletId(Rows.uuid(rs, "wallet_id")),
                rs.getLong("seq"),
                EntryType.valueOf(rs.getString("type")),
                Money.of(rs.getLong("amount"), currency),
                new LedgerEntry.Source(rs.getString("reference"), reservation == null ? null : new ReservationId(reservation),
                        charge == null ? null : new ChargeId(charge)),
                reversalOf == null ? null : new LedgerEntryId(reversalOf),
                new LedgerEntry.Origin(rs.getString("actor"), rs.getString("reason"), rs.getString("idempotency_key")),
                Rows.instant(rs, "created_at"));
    };

    private final JdbcClient jdbc;

    public JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(LedgerEntry entry) {
        try {
            jdbc.sql("insert into ledger_entries (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(entry.id().value(), entry.tenantId().value(), entry.walletId().value(), entry.amount().currency().code(),
                            entry.sequence(), entry.type().name(), entry.amount().minorUnits(), entry.source().reference(),
                            entry.source().reservationId() == null ? null : entry.source().reservationId().value(),
                            entry.source().chargeId() == null ? null : entry.source().chargeId().value(),
                            entry.reversalOf() == null ? null : entry.reversalOf().value(),
                            entry.origin().actor(), entry.origin().reason(), entry.origin().idempotencyKey(),
                            Timestamp.from(entry.createdAt()))
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
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where idempotency_key = ?").param(idempotencyKey).query(MAPPER).optional();
    }

    @Override
    public Optional<LedgerEntry> findByCharge(ChargeId chargeId) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where charge_id = ?").param(chargeId.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<LedgerEntry> findReversalOf(LedgerEntryId original) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where reversal_of = ?").param(original.value()).query(MAPPER).optional();
    }

    @Override
    public List<LedgerEntry> page(WalletId walletId, long afterSequence, int limit) {
        return jdbc.sql("select " + COLUMNS + " from ledger_entries where wallet_id = ? and seq > ? order by seq limit ?")
                .params(walletId.value(), afterSequence, limit).query(MAPPER).list();
    }

    @Override
    public Money balanceThrough(WalletId walletId, long sequence) {
        return jdbc.sql("select coalesce(sum(amount), 0) as total, max(currency) as currency from ledger_entries where wallet_id = ? and seq <= ?")
                .params(walletId.value(), sequence)
                .query((rs, n) -> Money.of(rs.getLong("total"), Currency.of(rs.getString("currency"))))
                .single();
    }
}
