package dev.ngb.core_billing.money.domain.ledger;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.Objects;

/**
 * One immutable line of a wallet's ledger. Amounts are signed: credits are positive, debits negative, and a
 * reversal carries the negation of the entry it corrects, so a wallet's balance is the sum of its entries.
 * There are no setters and no update path; the only way to correct an entry is {@link #reversalOf}.
 */
public final class LedgerEntry {

    /** What an entry refers to: a free-form reference plus the reservation and charge behind it, when any. */
    public record Source(String reference, ReservationId reservationId, ChargeId chargeId) {
        public Source {
            Objects.requireNonNull(reference, "reference");
        }

        public static Source of(String reference) {
            return new Source(reference, null, null);
        }
    }

    /** Who posted the entry, why, and the key that makes posting it twice impossible. */
    public record Origin(String actor, String reason, String idempotencyKey) {
        public Origin {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
    }

    private final LedgerEntryId id;
    private final TenantId tenantId;
    private final WalletId walletId;
    private final long sequence;
    private final EntryType type;
    private final Money amount;
    private final Source source;
    private final LedgerEntryId reversalOf;
    private final Origin origin;
    private final Instant createdAt;

    private LedgerEntry(
            LedgerEntryId id,
            TenantId tenantId,
            WalletId walletId,
            long sequence,
            EntryType type,
            Money amount,
            Source source,
            LedgerEntryId reversalOf,
            Origin origin,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.walletId = Objects.requireNonNull(walletId, "walletId");
        this.sequence = sequence;
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.source = Objects.requireNonNull(source, "source");
        this.reversalOf = reversalOf;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence starts at 1");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("an entry must move money");
        }
        boolean consistent = switch (type) {
            case CREDIT -> amount.isPositive() && reversalOf == null;
            case DEBIT -> amount.isNegative() && reversalOf == null;
            case REVERSAL -> reversalOf != null;
        };
        if (!consistent) {
            throw new IllegalArgumentException("the amount's sign or the reversal link does not match the entry type");
        }
    }

    /** A positive amount that adds money. */
    public static LedgerEntry credit(
            LedgerEntryId id, TenantId tenantId, WalletId walletId, long sequence, Money amount, Source source, Origin origin, Instant now) {
        return new LedgerEntry(id, tenantId, walletId, sequence, EntryType.CREDIT, amount, source, null, origin, now);
    }

    /** {@code amount} is positive; the entry stores it negated. */
    public static LedgerEntry debit(
            LedgerEntryId id, TenantId tenantId, WalletId walletId, long sequence, Money amount, Source source, Origin origin, Instant now) {
        return new LedgerEntry(id, tenantId, walletId, sequence, EntryType.DEBIT, amount.negate(), source, null, origin, now);
    }

    /** Negates {@code original} on the same wallet and currency. The reason is mandatory. */
    public static LedgerEntry reversalOf(LedgerEntry original, LedgerEntryId id, long sequence, Origin origin, Instant now) {
        if (original.type == EntryType.REVERSAL) {
            throw new IllegalStateException("a reversal cannot itself be reversed; post a new entry instead");
        }
        if (origin.reason() == null || origin.reason().isBlank()) {
            throw new IllegalArgumentException("a reversal needs a reason");
        }
        return new LedgerEntry(
                id, original.tenantId, original.walletId, sequence, EntryType.REVERSAL, original.amount.negate(),
                Source.of("reversal:" + original.id), original.id, origin, now);
    }

    public static LedgerEntry restore(
            LedgerEntryId id,
            TenantId tenantId,
            WalletId walletId,
            long sequence,
            EntryType type,
            Money amount,
            Source source,
            LedgerEntryId reversalOf,
            Origin origin,
            Instant createdAt) {
        return new LedgerEntry(id, tenantId, walletId, sequence, type, amount, source, reversalOf, origin, createdAt);
    }

    public LedgerEntryId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public WalletId walletId() {
        return walletId;
    }

    public long sequence() {
        return sequence;
    }

    public EntryType type() {
        return type;
    }

    /** Signed: positive adds money to the wallet, negative takes it away. */
    public Money amount() {
        return amount;
    }

    public Source source() {
        return source;
    }

    /** The entry this one corrects, or null. */
    public LedgerEntryId reversalOf() {
        return reversalOf;
    }

    public Origin origin() {
        return origin;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
