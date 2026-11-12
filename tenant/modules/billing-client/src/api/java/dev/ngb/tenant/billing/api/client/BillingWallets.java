package dev.ngb.tenant.billing.api.client;

import dev.ngb.tenant.billing.api.model.Wallet;
import java.util.List;

/** Wallet reads and the one write the tenant needs: opening a wallet in a chosen mode. */
public interface BillingWallets {

    record OpenWallet(String customerId, String currency, String mode, Long creditLimit) {}

    List<Wallet> wallets(String customerId);

    Wallet wallet(String id);

    Wallet open(String idempotencyKey, OpenWallet request);
}
