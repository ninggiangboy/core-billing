package dev.ngb.sampletenant.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.ngb.sampletenant.customer.domain.Customer;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerBillingViewsTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    private final Fakes.Customers customers = new Fakes.Customers();
    private final FakeSessions sessions = new FakeSessions();
    private final FakeInvoices invoices = new FakeInvoices();
    private final FakePayments payments = new FakePayments();
    private final FakeWallets wallets = new FakeWallets();
    private final CustomerBillingViews views = new CustomerBillingViews(
            new CustomerQueries(customers), sessions, invoices, payments, wallets, "http://localhost:5174/portal/return");

    @Test
    void opensASessionForTheMappedCustomerWithTheDefaultReturnUrl() {
        mapped();

        BillingSession session = views.openSession(USER, null, List.of("wallet:view"), 3600L);

        assertThat(session.portalUrl()).isEqualTo("https://tenant-a.example/portal#token");
        assertThat(sessions.customerId).isEqualTo("cus_1");
        assertThat(sessions.returnUrl).isEqualTo("http://localhost:5174/portal/return");
    }

    @Test
    void invoicesAreScopedToTheMappedCustomer() {
        mapped();

        List<Invoice> result = views.invoices(USER);

        assertThat(result).hasSize(1);
        assertThat(invoices.customerId).isEqualTo("cus_1");
    }

    @Test
    void paymentsAggregateTheWalletHistory() {
        mapped();

        List<Payment> result = views.payments(USER);

        assertThat(result).hasSize(2);
    }

    @Test
    void refusesAnUnmappedCustomer() {
        customers.save(Customer.pending(() -> UUID.randomUUID(), USER, "Ada", "ada@example.com", NOW));

        assertThatThrownBy(() -> views.openSession(USER, null, List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesATopUpForAnotherCustomersWallet() {
        mapped();

        assertThatThrownBy(() -> views.topUp(USER, "wal_other", 500, "USD", "key"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void startsATopUpForAnOwnedWallet() {
        mapped();

        Payment payment = views.topUp(USER, "wal_1", 500, "USD", "key-1");

        assertThat(payment.id()).isEqualTo("pay_1");
        assertThat(payments.topUpKey).isEqualTo("key-1");
    }

    private void mapped() {
        customers.save(Customer.pending(() -> UUID.randomUUID(), USER, "Ada", "ada@example.com", NOW).mapped("cus_1"));
    }

    private static final class FakeSessions implements BillingSessions {
        private String customerId;
        private String returnUrl;

        @Override
        public BillingSession create(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {
            this.customerId = customerId;
            this.returnUrl = returnUrl;
            return new BillingSession("ses_1", customerId, "https://tenant-a.example/portal#token", permissions, NOW.plusSeconds(3600));
        }

        @Override
        public void revoke(String id) {}
    }

    private static final class FakeInvoices implements BillingInvoices {
        private String customerId;

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

    private static final class FakePayments implements BillingPayments {
        private String topUpKey;

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

    private static final class FakeWallets implements BillingWallets {
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
