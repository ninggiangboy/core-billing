package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.messaging.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.command.PayInvoiceUseCase;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.payment.PaymentTestWorld;
import dev.ngb.core_billing.payment.PaymentTestWorld.PayWorld;
import dev.ngb.core_billing.payment.application.refund.command.RequestRefundUseCase;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePayment;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePaymentCommand;
import dev.ngb.core_billing.payment.application.stripe.command.ReceiveStripeWebhookUseCase;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.support.FakePaymentProvider;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.StripeEvents;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Paying an invoice: the invoice becomes paid, and the Postpaid debt is paid off, only from a verified Stripe
 * result that has gone through the payment context and the outbox. Stripe is the in-memory provider; signature
 * checking and event parsing are the real ones.
 */
@SpringBootTest(classes = Application.class)
@Import({PostgresContainerConfig.class, FakePaymentProviderConfig.class})
class InvoicePaymentIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");

    @Autowired MoneyTestWorld money;
    @Autowired PaymentTestWorld payments;
    @Autowired ChargeWallet charges;
    @Autowired OutboxRelay relay;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Autowired PayInvoiceUseCase payInvoice;
    @Autowired RequestRefundUseCase refund;
    @Autowired StartInvoicePayment invoicePayments;
    @Autowired ReceiveStripeWebhookUseCase webhooks;
    @Autowired FakePaymentProvider stripe;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    @BeforeEach
    void resetStripe() {
        stripe.reset();
    }

    /** A Postpaid wallet with 1,000 of debt invoiced for the previous month, and a working Stripe configuration. */
    private record Debt(PayWorld pay, InvoiceId invoice) {
        World money() {
            return pay.money();
        }
    }

    private Debt invoicedDebt() {
        World postpaid = money.postpaid(1_000_000);
        PayWorld pay = payments.directFor(postpaid, "acct_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        var tenant = postpaid.tenant().id();
        for (long amount : new long[] {300, 700}) {
            scopes.inTenant(tenant, () -> charges.charge(new ChargeCommand(postpaid.wallet().id(), null, Money.of(amount, MoneyTestWorld.USD), null, "work",
                    UUID.randomUUID().toString())));
        }
        scopes.runInTenant(tenant, relay::relayDue);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' "
                + "where tenant_id = ?", tenant.value());
        scopes.inTenant(tenant, closeCycle::runOnce);
        UUID invoiceId = owner.jdbc().queryForObject("select id from invoices where tenant_id = ?", UUID.class, tenant.value());
        assertThat(balance(postpaid)).isEqualTo(-1_000);
        return new Debt(pay, new InvoiceId(invoiceId));
    }

    private long balance(World world) {
        return owner.jdbc().queryForObject("select balance from wallets where id = ?", Long.class, world.wallet().id().value());
    }

    private String invoiceStatus(Debt debt) {
        return owner.jdbc().queryForObject("select status from invoices where id = ?", String.class, debt.invoice().value());
    }

    private PayInvoiceUseCase.Result pay(Debt debt, String key) {
        return scopes.inTenant(debt.money().tenant().id(), () -> payInvoice.execute(new PayInvoiceUseCase.Command(debt.invoice(), key, AUDIT, null)));
    }

    private void deliver(Debt debt, String body) {
        webhooks.receiveDirect(debt.pay().configuration().id(), body, StripeEvents.sign(debt.pay().webhookSecret(), body));
    }

    private void stripeSays(Debt debt, UUID paymentId, boolean paid) {
        String session = owner.jdbc().queryForObject("select checkout_session_id from payments where id = ?", String.class, paymentId);
        String body = paid
                ? StripeEvents.paidCheckout(StripeEvents.newEventId(), null, session, "pi_" + paymentId, paymentId.toString(), 1_000)
                : StripeEvents.checkout(StripeEvents.newEventId(), "checkout.session.async_payment_failed", null, session, "pi_" + paymentId,
                        paymentId.toString(), "complete", "unpaid", 1_000, "usd");
        deliver(debt, body);
    }

    private void relayEvents(Debt debt) {
        scopes.runInTenant(debt.money().tenant().id(), relay::relayDue);
    }

    @Test
    void anInvoiceIsPaidOnlyByAVerifiedPaymentAndTheDebtIsPaidOff() {
        Debt debt = invoicedDebt();

        PayInvoiceUseCase.Result started = pay(debt, "pay-1");

        assertThat(started.payment().status()).isEqualTo("REQUIRES_ACTION");
        assertThat(started.payment().checkoutUrl()).isNotBlank();
        assertThat(started.invoice().payments()).singleElement().extracting("status").hasToString("STARTED");
        assertThat(invoiceStatus(debt)).isEqualTo("OPEN");
        assertThat(balance(debt.money())).isEqualTo(-1_000);

        stripeSays(debt, started.payment().paymentId(), true);
        // Stripe's verified result settles the payment, but the invoice and the wallet wait for the relay.
        assertThat(owner.jdbc().queryForObject("select status from payments where id = ?", String.class, started.payment().paymentId())).isEqualTo("SUCCEEDED");
        assertThat(owner.jdbc().queryForObject("select ledger_entry_id from payments where id = ?", UUID.class, started.payment().paymentId())).isNull();
        assertThat(invoiceStatus(debt)).isEqualTo("OPEN");

        relayEvents(debt);

        assertThat(invoiceStatus(debt)).isEqualTo("PAID");
        assertThat(balance(debt.money())).isZero();
        Map<String, Object> row = owner.jdbc().queryForMap("select settlement, paid_at from invoices where id = ?", debt.invoice().value());
        assertThat(row).containsEntry("settlement", "PAYMENT");
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'invoice.paid'", debt.money().tenant().id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from invoice_payments where invoice_id = ? and status = 'SUCCEEDED' and ledger_entry_id is not null",
                debt.invoice().value())).isEqualTo(1);
    }

    @Test
    void redeliveringTheVerifiedResultCreditsTheWalletOnce() {
        Debt debt = invoicedDebt();
        PayInvoiceUseCase.Result started = pay(debt, "pay-1");
        stripeSays(debt, started.payment().paymentId(), true);
        relayEvents(debt);
        stripeSays(debt, started.payment().paymentId(), true);
        relayEvents(debt);

        assertThat(balance(debt.money())).isZero();
        assertThat(owner.count("select count(*) from ledger_entries where wallet_id = ? and idempotency_key = ?", debt.money().wallet().id().value(),
                "invoice-payoff:" + started.payment().paymentId())).isEqualTo(1);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'invoice.paid'", debt.money().tenant().id().value())).isEqualTo(1);
    }

    @Test
    void aFailedPaymentLeavesTheInvoiceOpenAndItCanBePaidAgain() {
        Debt debt = invoicedDebt();
        PayInvoiceUseCase.Result first = pay(debt, "pay-1");
        stripeSays(debt, first.payment().paymentId(), false);
        relayEvents(debt);

        assertThat(invoiceStatus(debt)).isEqualTo("OPEN");
        assertThat(owner.count("select count(*) from invoice_payments where invoice_id = ? and status = 'FAILED'", debt.invoice().value())).isEqualTo(1);
        assertThat(balance(debt.money())).isEqualTo(-1_000);

        PayInvoiceUseCase.Result second = pay(debt, "pay-2");
        stripeSays(debt, second.payment().paymentId(), true);
        relayEvents(debt);

        assertThat(invoiceStatus(debt)).isEqualTo("PAID");
        assertThat(balance(debt.money())).isZero();
    }

    @Test
    void theSameKeyReturnsTheSamePaymentAndStartsOnlyOne() {
        Debt debt = invoicedDebt();
        PayInvoiceUseCase.Result first = pay(debt, "pay-1");
        PayInvoiceUseCase.Result again = pay(debt, "pay-1");

        assertThat(again.payment().paymentId()).isEqualTo(first.payment().paymentId());
        assertThat(again.payment().replayed()).isTrue();
        assertThat(owner.count("select count(*) from payments where invoice_id = ?", debt.invoice().value())).isEqualTo(1);
        assertThat(stripe.checkouts).hasSize(1);
    }

    @Test
    void theSameKeyOnAnotherRequestForTheInvoiceIsAConflict() {
        Debt debt = invoicedDebt();
        pay(debt, "pay-1");

        assertThatThrownBy(() -> scopes.inTenant(debt.money().tenant().id(), () -> invoicePayments.start(new StartInvoicePaymentCommand(
                debt.invoice().value(), debt.money().wallet().id(), debt.money().customerId(), Money.of(1, MoneyTestWorld.USD), "Other amount", "pay-1"))))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(owner.count("select count(*) from payments where invoice_id = ?", debt.invoice().value())).isEqualTo(1);
    }

    @Test
    void concurrentPaymentsWithOneKeyStartOnePaymentAndOneCheckout() throws Exception {
        Debt debt = invoicedDebt();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> pay(debt, "parallel-pay"), pool))
                    .toList();
            Set<UUID> ids = new HashSet<>();
            long firsts = 0;
            long replays = 0;
            for (var future : futures) {
                PayInvoiceUseCase.Result result = future.get(30, TimeUnit.SECONDS);
                ids.add(result.payment().paymentId());
                if (result.payment().replayed()) {
                    replays++;
                } else {
                    firsts++;
                }
            }
            assertThat(ids).hasSize(1);
            assertThat(firsts).isEqualTo(1);
            assertThat(replays).isEqualTo(7);
            assertThat(owner.count("select count(*) from payments where invoice_id = ?", debt.invoice().value())).isEqualTo(1);
            assertThat(stripe.checkoutSessions.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoPaymentsForOneInvoiceBothCreditTheWalletButTheInvoiceIsPaidOnce() {
        Debt debt = invoicedDebt();
        PayInvoiceUseCase.Result first = pay(debt, "pay-1");
        PayInvoiceUseCase.Result second = pay(debt, "pay-2");
        stripeSays(debt, first.payment().paymentId(), true);
        stripeSays(debt, second.payment().paymentId(), true);
        relayEvents(debt);

        assertThat(invoiceStatus(debt)).isEqualTo("PAID");
        // The customer really paid twice: the second payment is credit on the wallet, never lost.
        assertThat(balance(debt.money())).isEqualTo(1_000);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'invoice.paid'", debt.money().tenant().id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from invoice_payments where invoice_id = ? and status = 'SUCCEEDED'", debt.invoice().value())).isEqualTo(2);
    }

    @Test
    void aPaidOrUnknownInvoiceCannotBePaidAgain() {
        Debt debt = invoicedDebt();
        PayInvoiceUseCase.Result started = pay(debt, "pay-1");
        stripeSays(debt, started.payment().paymentId(), true);
        relayEvents(debt);

        assertThatThrownBy(() -> pay(debt, "pay-3")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> scopes.inTenant(debt.money().tenant().id(), () -> payInvoice.execute(
                new PayInvoiceUseCase.Command(new InvoiceId(UUID.randomUUID()), "pay-4", AUDIT, null)))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aPaymentOfAnInvoiceCannotBeRefunded() {
        Debt debt = invoicedDebt();
        PayInvoiceUseCase.Result started = pay(debt, "pay-1");
        stripeSays(debt, started.payment().paymentId(), true);
        relayEvents(debt);

        assertThatThrownBy(() -> scopes.inTenant(debt.money().tenant().id(), () -> refund.execute(
                new RequestRefundUseCase.Command(new PaymentId(started.payment().paymentId()), null, "changed my mind", "refund-1", AUDIT))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("invoice");
    }

    @Test
    void aStripeRedirectAloneChangesNothing() {
        Debt debt = invoicedDebt();
        pay(debt, "pay-1");

        relayEvents(debt);

        assertThat(invoiceStatus(debt)).isEqualTo("OPEN");
        assertThat(balance(debt.money())).isEqualTo(-1_000);
    }
}
