package io.corebilling.money.domain;

import io.corebilling.money.api.ChargeId;
import io.corebilling.money.api.ChargeSource;
import io.corebilling.money.api.ChargeStatus;
import io.corebilling.money.api.ReservationId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Objects;

/** The actual cost incurred, and whether the wallet could pay it. A failed charge is kept as a record. */
public final class Charge {

    private final ChargeId id;
    private final TenantId tenantId;
    private final WalletId walletId;
    private final Money amount;
    private final ChargeSource source;
    private final ChargeStatus status;
    private final String failureReason;
    private final ReservationId reservationId;
    private final String usageRef;
    private final String reference;
    private final String idempotencyKey;
    private final String requestHash;
    private final Instant createdAt;

    private Charge(
            ChargeId id,
            TenantId tenantId,
            WalletId walletId,
            Money amount,
            ChargeSource source,
            ChargeStatus status,
            String failureReason,
            ReservationId reservationId,
            String usageRef,
            String reference,
            String idempotencyKey,
            String requestHash,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.walletId = Objects.requireNonNull(walletId, "walletId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.source = Objects.requireNonNull(source, "source");
        this.status = Objects.requireNonNull(status, "status");
        this.failureReason = failureReason;
        this.reservationId = reservationId;
        this.usageRef = usageRef;
        this.reference = reference;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a charge must be positive");
        }
        if ((status == ChargeStatus.FAILED) != (failureReason != null)) {
            throw new IllegalArgumentException("a failed charge has a reason and a successful one has none");
        }
    }

    public static Charge succeeded(
            ChargeId id, Wallet wallet, Money amount, ChargeSource source, ReservationId reservationId, String usageRef, String reference,
            String idempotencyKey, String requestHash, Instant now) {
        return new Charge(id, wallet.tenantId(), wallet.id(), amount, source, ChargeStatus.SUCCEEDED, null, reservationId, usageRef, reference,
                idempotencyKey, requestHash, now);
    }

    public static Charge failed(
            ChargeId id, Wallet wallet, Money amount, ChargeSource source, ReservationId reservationId, String usageRef, String reference,
            String idempotencyKey, String requestHash, String failureReason, Instant now) {
        return new Charge(id, wallet.tenantId(), wallet.id(), amount, source, ChargeStatus.FAILED, failureReason, reservationId, usageRef,
                reference, idempotencyKey, requestHash, now);
    }

    public static Charge restore(
            ChargeId id, TenantId tenantId, WalletId walletId, Money amount, ChargeSource source, ChargeStatus status, String failureReason,
            ReservationId reservationId, String usageRef, String reference, String idempotencyKey, String requestHash, Instant createdAt) {
        return new Charge(id, tenantId, walletId, amount, source, status, failureReason, reservationId, usageRef, reference, idempotencyKey,
                requestHash, createdAt);
    }

    public ChargeId id() {
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

    public ChargeSource source() {
        return source;
    }

    public ChargeStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public String usageRef() {
        return usageRef;
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

    public Instant createdAt() {
        return createdAt;
    }
}
