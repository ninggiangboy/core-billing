package dev.ngb.sampletenant.customer.application.billing.command;

import dev.ngb.sampletenant.billing.api.BillingPayments;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.customer.api.CustomerDirectory;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.UUID;

/** Scenario 10: a mapped customer tops up a wallet it owns. */
public class TopUpUseCase {

    private final CustomerDirectory customers;
    private final BillingWallets wallets;
    private final BillingPayments payments;

    public TopUpUseCase(CustomerDirectory customers, BillingWallets wallets, BillingPayments payments) {
        this.customers = customers;
        this.wallets = wallets;
        this.payments = payments;
    }

    public Payment execute(UUID userId, String walletId, long amount, String currency, String idempotencyKey) {
        CustomerDirectory.CustomerSnapshot customer = mapped(userId);
        boolean owned = wallets.wallets(customer.billingCustomerId()).stream()
                .anyMatch(wallet -> wallet.id().equals(walletId));
        if (!owned) {
            throw new NotFoundException("wallet not found");
        }
        if (amount <= 0) {
            throw new BadRequestException("a top-up amount must be positive");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? "top-up:" + customer.billingCustomerId() + ":" + UUID.randomUUID()
                : idempotencyKey;
        return payments.createTopUp(key, new BillingPayments.TopUpRequest(walletId, amount, currency));
    }

    private CustomerDirectory.CustomerSnapshot mapped(UUID userId) {
        CustomerDirectory.CustomerSnapshot customer = customers.byUserId(userId);
        if (!customer.mapped()) {
            throw new BadRequestException("this account has no Billing customer mapping yet");
        }
        return customer;
    }
}
