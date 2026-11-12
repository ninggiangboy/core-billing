package dev.ngb.core_billing.money.domain.reservation;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationStatus;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * A budget held on a wallet before a resource runs. A hold is not a ledger posting: the amount lives here and
 * on the wallet's held total, and the ledger is written only when the reservation is captured. Every way a
 * reservation ends (capture, release, expiry) returns the whole hold to the wallet in the same step.
 */
public final class Reservation {

    private final ReservationId id;
    private final TenantId tenantId;
    private final WalletId walletId;
    private final Money amount;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final String reference;
    private final String idempotencyKey;
    private final String requestHash;
    private ReservationStatus status;
    private Money captured;
    private Instant closedAt;

    private Reservation(
            ReservationId id,
            TenantId tenantId,
            WalletId walletId,
            Money amount,
            Money captured,
            ReservationStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant closedAt,
            String reference,
            String idempotencyKey,
            String requestHash) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.walletId = Objects.requireNonNull(walletId, "walletId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.captured = Objects.requireNonNull(captured, "captured");
        this.status = Objects.requireNonNull(status, "status");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.closedAt = closedAt;
        this.reference = reference;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
    }

    /** Holds {@code amount} on {@code wallet}, which must be able to spend it. */
    public static Reservation hold(
            ReservationId id, Wallet wallet, Money amount, Instant expiresAt, String reference, String idempotencyKey, String requestHash, Instant now) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("a reservation must expire in the future");
        }
        wallet.hold(amount);
        return new Reservation(
                id, wallet.tenantId(), wallet.id(), amount, Money.zero(amount.currency()), ReservationStatus.ACTIVE, expiresAt, now, null,
                reference, idempotencyKey, requestHash);
    }

    public static Reservation restore(
            ReservationId id,
            TenantId tenantId,
            WalletId walletId,
            Money amount,
            Money captured,
            ReservationStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant closedAt,
            String reference,
            String idempotencyKey,
            String requestHash) {
        return new Reservation(id, tenantId, walletId, amount, captured, status, expiresAt, createdAt, closedAt, reference, idempotencyKey, requestHash);
    }

    /**
     * Settles the reservation for the actual cost and returns the unused remainder to the wallet. The caller
     * posts the ledger debit for {@code charged} afterwards.
     */
    public void capture(Wallet wallet, Money charged, Instant now) {
        requireActive();
        if (isExpired(now)) {
            throw new IllegalStateException("the reservation has expired");
        }
        if (!charged.isPositive() || charged.isGreaterThan(amount)) {
            throw new IllegalArgumentException("the charge must be positive and no more than the reserved amount");
        }
        wallet.unhold(amount);
        captured = charged;
        close(ReservationStatus.CAPTURED, now);
    }

    public void release(Wallet wallet, Instant now) {
        requireActive();
        wallet.unhold(amount);
        close(ReservationStatus.RELEASED, now);
    }

    /** Releases a hold whose time has run out. */
    public void expire(Wallet wallet, Instant now) {
        requireActive();
        if (!isExpired(now)) {
            throw new IllegalStateException("the reservation has not expired yet");
        }
        wallet.unhold(amount);
        close(ReservationStatus.EXPIRED, now);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    private void requireActive() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("the reservation is already " + status.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private void close(ReservationStatus newStatus, Instant now) {
        status = newStatus;
        closedAt = now;
    }

    public ReservationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public WalletId walletId() {
        return walletId;
    }

    public Money amount() {
        return amount;
    }

    public Money captured() {
        return captured;
    }

    public ReservationStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public String reference() {
        return reference;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }
}
