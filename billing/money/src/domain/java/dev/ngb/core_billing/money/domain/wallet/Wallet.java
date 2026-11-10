package dev.ngb.core_billing.money.domain.wallet;

import dev.ngb.core_billing.money.api.wallet.InsufficientFundsException;
import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Objects;

/**
 * A customer's balance in one currency. The balance is a projection of the ledger, kept in step by
 * {@link #post}: every change to it corresponds to exactly one ledger entry whose sequence number this
 * wallet hands out. The wallet's currency never changes. Callers hold the wallet's row lock while they
 * change it, so sequence numbers are assigned in a single, gap-free order.
 *
 * <p>Money rules: a Prepaid wallet can spend only what is available (balance minus holds); a Postpaid
 * wallet can additionally spend up to its credit limit, and its debt is whatever the balance is below zero.
 */
public final class Wallet {

    private final WalletId id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final Currency currency;
    private final WalletMode mode;
    private final Money creditLimit;
    private final Instant createdAt;
    private Money balance;
    private Money held;
    private long lastSequence;

    private Wallet(
            WalletId id,
            TenantId tenantId,
            CustomerId customerId,
            Currency currency,
            WalletMode mode,
            Money creditLimit,
            Money balance,
            Money held,
            long lastSequence,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.creditLimit = requireCurrency(creditLimit);
        this.balance = requireCurrency(balance);
        this.held = requireCurrency(held);
        this.lastSequence = lastSequence;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (creditLimit.isNegative() || held.isNegative()) {
            throw new IllegalArgumentException("credit limit and held amount must not be negative");
        }
        if (mode == WalletMode.PREPAID && !creditLimit.isZero()) {
            throw new IllegalArgumentException("a prepaid wallet has no credit limit");
        }
    }

    public static Wallet open(
            WalletId id, TenantId tenantId, CustomerId customerId, Currency currency, WalletMode mode, Money creditLimit, Instant now) {
        Money zero = Money.zero(currency);
        return new Wallet(id, tenantId, customerId, currency, mode, creditLimit == null ? zero : creditLimit, zero, zero, 0, now);
    }

    public static Wallet restore(
            WalletId id,
            TenantId tenantId,
            CustomerId customerId,
            Currency currency,
            WalletMode mode,
            Money creditLimit,
            Money balance,
            Money held,
            long lastSequence,
            Instant createdAt) {
        return new Wallet(id, tenantId, customerId, currency, mode, creditLimit, balance, held, lastSequence, createdAt);
    }

    /** Balance minus holds. */
    public Money available() {
        return balance.minus(held);
    }

    /** What can still be spent: the available balance plus the credit limit. */
    public Money spendable() {
        return available().plus(creditLimit);
    }

    /** The debt of a Postpaid wallet: how far the balance is below zero. Always zero for Prepaid. */
    public Money outstanding() {
        return mode == WalletMode.POSTPAID && balance.isNegative() ? balance.negate() : Money.zero(currency);
    }

    public boolean canSpend(Money amount) {
        requireCurrency(amount);
        return !spendable().isLessThan(amount);
    }

    /**
     * Applies one ledger entry's amount to the balance and returns the sequence number the entry must carry.
     * A negative amount needs spendable funds; a positive one is always accepted.
     *
     * @throws InsufficientFundsException when a debit exceeds what can be spent
     */
    public long post(Money signedAmount) {
        requireCurrency(signedAmount);
        if (signedAmount.isZero()) {
            throw new IllegalArgumentException("an entry must move money");
        }
        if (signedAmount.isNegative() && !canSpend(signedAmount.negate())) {
            throw new InsufficientFundsException("insufficient funds");
        }
        balance = balance.plus(signedAmount);
        return ++lastSequence;
    }

    /** Reserves budget without touching the ledger. */
    public void hold(Money amount) {
        requireCurrency(amount);
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a hold must be positive");
        }
        if (!canSpend(amount)) {
            throw new InsufficientFundsException("insufficient funds");
        }
        held = held.plus(amount);
    }

    public void unhold(Money amount) {
        requireCurrency(amount);
        if (!amount.isPositive() || held.isLessThan(amount)) {
            throw new IllegalStateException("cannot release more than is held");
        }
        held = held.minus(amount);
    }

    public WalletId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public Currency currency() {
        return currency;
    }

    public WalletMode mode() {
        return mode;
    }

    public Money creditLimit() {
        return creditLimit;
    }

    public Money balance() {
        return balance;
    }

    public Money held() {
        return held;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private Money requireCurrency(Money money) {
        Objects.requireNonNull(money, "money");
        if (!money.currency().equals(currency)) {
            throw new IllegalArgumentException("currency mismatch: wallet is " + currency + ", amount is " + money.currency());
        }
        return money;
    }
}
