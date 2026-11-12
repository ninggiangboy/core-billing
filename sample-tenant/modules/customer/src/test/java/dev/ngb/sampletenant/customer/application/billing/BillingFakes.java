package dev.ngb.sampletenant.customer.application.billing;

import dev.ngb.sampletenant.billing.api.BillingInvoices;
import dev.ngb.sampletenant.billing.api.BillingPayments;
import dev.ngb.sampletenant.billing.api.BillingSessions;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Amount;
import dev.ngb.sampletenant.billing.api.model.BillingSession;
import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.InvoiceDetail;
import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.billing.api.model.PaymentDetail;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Deterministic Billing and customer fakes shared by the billing use-case and query tests. */
public final class BillingFakes {

    public static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    public static final UUID USER = UUID.randomUUID();

    private BillingFakes() {}

    public static CustomerDirectory mappedCustomer() {
        return new Customers(true);
    }

    public static CustomerDirectory unmappedCustomer() {
        return new Customers(false);
    }

    private static final class Customers implements CustomerDirectory {
        private final boolean mapped;

        private Customers(boolean mapped) {
            this.mapped = mapped;
        }

        @Override
        public CustomerSnapshot byUserId(UUID userId) {
            return new CustomerSnapshot(UUID.randomUUID(), userId, mapped ? "cus_1" : null, mapped);
        }
    }

    public static final class Sessions implements BillingSessions {
        public String customerId;
        public String returnUrl;

        @Override
        public BillingSession create(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
            this.customerId = customerId;
            this.returnUrl = returnUrl;
            return new BillingSession("ses_1", customerId, "https://tenant-a.example/portal#token", permissions, NOW.plusSeconds(3600));
        }

        @Override
        public void revoke(String id) {}
    }

    public static final class Invoices implements BillingInvoices {
        public String customerId;

        @Override
        public List<Invoice> invoices(String customerId, String walletId, String status, int limit) {
            this.customerId = customerId;
            return List.of(new Invoice(
                    "inv_1", "INV-000001", customerId, "wal_1", "OPEN", "CHARGE", new Amount(1000, "USD"),
                    NOW, NOW, null, null, NOW, NOW, null, null));
        }

        @Override
        public InvoiceDetail invoice(String id) {
            return new InvoiceDetail(invoices("cus_1", null, null, 50).get(0), List.of(), List.of());
        }
    }

    public static final class Payments implements BillingPayments {
        public String topUpKey;

        @Override
        public List<Payment> payments(String walletId, String status, int limit) {
            return List.of(
                    new Payment("pay_" + walletId + "_1", walletId, "cus_1", "SUCCEEDED", new Amount(500, "USD"), null, null, null, "led_1", NOW, NOW, NOW),
                    new Payment("pay_" + walletId + "_2", walletId, "cus_1", "FAILED", new Amount(500, "USD"), null, null, "declined", null, NOW, NOW, null));
        }

        @Override
        public PaymentDetail payment(String id) {
            return new PaymentDetail(payments("wal_1", null, 50).get(0), List.of(), List.of());
        }

        @Override
        public Payment createTopUp(String idempotencyKey, TopUpRequest request) {
            topUpKey = idempotencyKey;
            return new Payment(
                    "pay_1", request.walletId(), "cus_1", "PENDING", new Amount(request.amount(), request.currency()),
                    null, "https://checkout.stripe.test/1", null, null, NOW, NOW, null);
        }
    }

    public static final class Wallets implements BillingWallets {
        @Override
        public List<Wallet> wallets(String customerId) {
            return List.of(new Wallet(
                    "wal_1", customerId, "USD", "PREPAID", new Amount(5000, "USD"), new Amount(0, "USD"),
                    new Amount(5000, "USD"), new Amount(0, "USD"), new Amount(0, "USD"), 0L, NOW));
        }

        @Override
        public Wallet wallet(String id) {
            return wallets("cus_1").get(0);
        }

        @Override
        public Wallet open(String idempotencyKey, OpenWallet request) {
            return wallets(request.customerId()).get(0);
        }
    }
}
