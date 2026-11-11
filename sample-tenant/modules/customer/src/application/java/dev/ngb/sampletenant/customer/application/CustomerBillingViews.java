package dev.ngb.sampletenant.customer.application;

import dev.ngb.sampletenant.billing.api.BillingInvoices;
import dev.ngb.sampletenant.billing.api.BillingPayments;
import dev.ngb.sampletenant.billing.api.BillingSessions;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.BillingSession;
import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.customer.domain.Customer;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/**
 * Scenarios 8, 9 and 10: a mapped customer opens a Billing Session for top-up and reads invoices
 * and payments. The call is identical for a Direct and a Connect tenant because Billing owns the
 * Stripe configuration; the tenant never branches on the Stripe account mode.
 */
public class CustomerBillingViews {

    private final CustomerQueries customers;
    private final BillingSessions sessions;
    private final BillingInvoices invoices;
    private final BillingPayments payments;
    private final BillingWallets wallets;
    private final String defaultReturnUrl;

    public CustomerBillingViews(
            CustomerQueries customers,
            BillingSessions sessions,
            BillingInvoices invoices,
            BillingPayments payments,
            BillingWallets wallets,
            String defaultReturnUrl) {
        this.customers = customers;
        this.sessions = sessions;
        this.invoices = invoices;
        this.payments = payments;
        this.wallets = wallets;
        this.defaultReturnUrl = defaultReturnUrl;
    }

    public BillingSession openSession(UUID userId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
        Customer customer = mapped(userId);
        String target = returnUrl == null || returnUrl.isBlank() ? defaultReturnUrl : returnUrl;
        if (target == null || target.isBlank()) {
            throw new BadRequestException("a return URL is required");
        }
        return sessions.create(customer.billingCustomerId(), target, permissions, expiresInSeconds);
    }

    public List<Wallet> wallets(UUID userId) {
        return wallets.wallets(mapped(userId).billingCustomerId());
    }

    public List<Invoice> invoices(UUID userId) {
        return invoices.invoices(mapped(userId).billingCustomerId(), null, null, 50);
    }

    public List<Payment> payments(UUID userId) {
        Customer customer = mapped(userId);
        return wallets.wallets(customer.billingCustomerId()).stream()
                .flatMap(wallet -> payments.payments(wallet.id(), null, 50).stream())
                .toList();
    }

    public Payment topUp(UUID userId, String walletId, long amount, String currency, String idempotencyKey) {
        Customer customer = mapped(userId);
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

    private Customer mapped(UUID userId) {
        Customer customer = customers.byUser(userId);
        if (!customer.mapped()) {
            throw new BadRequestException("this account has no Billing customer mapping yet");
        }
        return customer;
    }
}
