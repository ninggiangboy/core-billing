package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.wallet.WalletMode;
import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
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
