package dev.ngb.sampletenant.customer.application.billing.query;

import dev.ngb.sampletenant.billing.api.client.BillingInvoices;
import dev.ngb.sampletenant.billing.api.client.BillingPayments;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import java.util.List;
import java.util.UUID;

/** Scenarios 8 and 9: the billing views the tenant renders for a mapped customer. */
public class CustomerBillingQueries {

    private final CustomerDirectory customers;
    private final BillingInvoices invoices;
    private final BillingPayments payments;
    private final BillingWallets wallets;

    public CustomerBillingQueries(
            CustomerDirectory customers, BillingInvoices invoices, BillingPayments payments, BillingWallets wallets) {
        this.customers = customers;
        this.invoices = invoices;
        this.payments = payments;
        this.wallets = wallets;
    }

    public List<Wallet> wallets(UUID userId) {
        return wallets.wallets(mapped(userId).billingCustomerId());
    }

    public List<Invoice> invoices(UUID userId) {
        return invoices.invoices(mapped(userId).billingCustomerId(), null, null, 50);
    }

    public List<Payment> payments(UUID userId) {
        CustomerDirectory.CustomerSnapshot customer = mapped(userId);
        return wallets.wallets(customer.billingCustomerId()).stream()
                .flatMap(wallet -> payments.payments(wallet.id(), null, 50).stream())
                .toList();
    }

    private CustomerDirectory.CustomerSnapshot mapped(UUID userId) {
        CustomerDirectory.CustomerSnapshot customer = customers.byUserId(userId);
        if (!customer.mapped()) {
            throw new BadRequestException("this account has no Billing customer mapping yet");
        }
        return customer;
    }
}
