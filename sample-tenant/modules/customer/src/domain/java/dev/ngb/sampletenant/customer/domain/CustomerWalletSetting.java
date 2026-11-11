package dev.ngb.sampletenant.customer.domain;

import java.time.Instant;
import java.util.UUID;

/** The customer's chosen Billing wallet mode and the wallet it opened. */
public record CustomerWalletSetting(
        UUID customerId,
        WalletMode walletMode,
        String currency,
        Long creditLimit,
        String walletId,
        Instant updatedAt) {

    public static CustomerWalletSetting defaults(UUID customerId) {
        return new CustomerWalletSetting(customerId, WalletMode.PREPAID, "USD", null, null, null);
    }
}
