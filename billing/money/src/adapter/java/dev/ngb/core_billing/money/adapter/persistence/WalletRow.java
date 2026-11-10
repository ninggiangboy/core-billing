package dev.ngb.core_billing.money.adapter.persistence;

import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("wallets")
record WalletRow(
        @Id UUID id,
        UUID tenantId,
        UUID customerId,
        String currency,
        String mode,
        long creditLimit,
        long balance,
        long held,
        long lastSequence,
        Instant createdAt) {

    static WalletRow from(Wallet wallet) {
        return new WalletRow(
                wallet.id().value(),
                wallet.tenantId().value(),
                wallet.customerId().value(),
                wallet.currency().code(),
                wallet.mode().name(),
                wallet.creditLimit().minorUnits(),
                wallet.balance().minorUnits(),
                wallet.held().minorUnits(),
                wallet.lastSequence(),
                wallet.createdAt());
    }

    Wallet toDomain() {
        Currency money = Currency.of(currency.strip());
        return Wallet.restore(
                new WalletId(id),
                new TenantId(tenantId),
                new CustomerId(customerId),
                money,
                WalletMode.valueOf(mode),
                Money.of(creditLimit, money),
                Money.of(balance, money),
                Money.of(held, money),
                lastSequence,
                createdAt);
    }
}
