package dev.ngb.core_billing.money.adapter.persistence;

import static dev.ngb.core_billing_jooq.Tables.LEDGER_ENTRIES;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing_jooq.tables.records.LedgerEntriesRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;

/** Insert and read only; the database also refuses anything else. */
public class JooqLedgerRepository implements LedgerRepository {

    private final DSLContext dsl;

    public JooqLedgerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void append(LedgerEntry entry) {
        try {
            dsl.insertInto(LEDGER_ENTRIES)
                    .set(LEDGER_ENTRIES.ID, entry.id().value())
                    .set(LEDGER_ENTRIES.TENANT_ID, entry.tenantId().value())
                    .set(LEDGER_ENTRIES.WALLET_ID, entry.walletId().value())
                    .set(LEDGER_ENTRIES.CURRENCY, entry.amount().currency().code())
                    .set(LEDGER_ENTRIES.SEQ, entry.sequence())
                    .set(LEDGER_ENTRIES.TYPE, entry.type().name())
                    .set(LEDGER_ENTRIES.AMOUNT, entry.amount().minorUnits())
                    .set(LEDGER_ENTRIES.REFERENCE, entry.source().reference())
                    .set(LEDGER_ENTRIES.RESERVATION_ID,
                            entry.source().reservationId() == null ? null : entry.source().reservationId().value())
                    .set(LEDGER_ENTRIES.CHARGE_ID, entry.source().chargeId() == null ? null : entry.source().chargeId().value())
                    .set(LEDGER_ENTRIES.REVERSAL_OF, entry.reversalOf() == null ? null : entry.reversalOf().value())
                    .set(LEDGER_ENTRIES.ACTOR, entry.origin().actor())
                    .set(LEDGER_ENTRIES.REASON, entry.origin().reason())
                    .set(LEDGER_ENTRIES.IDEMPOTENCY_KEY, entry.origin().idempotencyKey())
                    .set(LEDGER_ENTRIES.CREATED_AT, entry.createdAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("this idempotency key or entry was already posted");
            }
            throw e;
        }
    }

    @Override
    public Optional<LedgerEntry> findById(LedgerEntryId id) {
        return dsl.selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.ID.eq(id.value()))
                .fetchOptional(JooqLedgerRepository::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
        return dsl.selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(JooqLedgerRepository::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findByCharge(ChargeId chargeId) {
        return dsl.selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.CHARGE_ID.eq(chargeId.value()))
                .fetchOptional(JooqLedgerRepository::toDomain);
    }

    @Override
    public Optional<LedgerEntry> findReversalOf(LedgerEntryId original) {
        return dsl.selectFrom(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.REVERSAL_OF.eq(original.value()))
                .fetchOptional(JooqLedgerRepository::toDomain);
    }

    @Override
    public Money balanceThrough(WalletId walletId, long sequence) {
        var row = dsl.select(DSL.coalesce(DSL.sum(LEDGER_ENTRIES.AMOUNT).cast(Long.class), DSL.val(0L)), DSL.max(LEDGER_ENTRIES.CURRENCY))
                .from(LEDGER_ENTRIES)
                .where(LEDGER_ENTRIES.WALLET_ID.eq(walletId.value()))
                .and(LEDGER_ENTRIES.SEQ.le(sequence))
                .fetchOne();
        return Money.of(row.value1(), Currency.of(row.value2().strip()));
    }

    private static LedgerEntry toDomain(LedgerEntriesRecord row) {
        return LedgerEntry.restore(
                new LedgerEntryId(row.getId()),
                new TenantId(row.getTenantId()),
                new WalletId(row.getWalletId()),
                row.getSeq(),
                EntryType.valueOf(row.getType()),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                new LedgerEntry.Source(
                        row.getReference(),
                        row.getReservationId() == null ? null : new ReservationId(row.getReservationId()),
                        row.getChargeId() == null ? null : new ChargeId(row.getChargeId())),
                row.getReversalOf() == null ? null : new LedgerEntryId(row.getReversalOf()),
                new LedgerEntry.Origin(row.getActor(), row.getReason(), row.getIdempotencyKey()),
                row.getCreatedAt());
    }
}
