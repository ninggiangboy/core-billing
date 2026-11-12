package dev.ngb.core_billing.invoicing.domain.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    static final Currency USD = Currency.of("USD");
    static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    static InvoiceItem item(long amount) {
        return new InvoiceItem(UUID.randomUUID(), ItemSource.CHARGE, UUID.randomUUID(), null, null, null, "usage", Money.of(amount, USD), NOW);
    }

    static Invoice draft(long... amounts) {
        List<InvoiceItem> items = java.util.Arrays.stream(amounts).mapToObj(InvoiceTest::item).toList();
        return Invoice.draft(new InvoiceId(UUID.randomUUID()), new TenantId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()),
                new WalletId(UUID.randomUUID()), USD, InvoiceOrigin.POSTPAID_CYCLE, items, Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"), NOW);
    }

    static Invoice open(long... amounts) {
        Invoice invoice = draft(amounts);
        invoice.issue("INV-000001", NOW);
        return invoice;
    }

    @Test
    void totalIsTheSumOfItsLinesInTheInvoiceCurrency() {
        assertThat(draft(1_250, 300, 50).total()).isEqualTo(Money.of(1_600, USD));
    }

    @Test
    void aLineInAnotherCurrencyIsRejected() {
        InvoiceItem euro = new InvoiceItem(UUID.randomUUID(), ItemSource.CHARGE, UUID.randomUUID(), null, null, null, "x", Money.of(5, Currency.of("EUR")), NOW);
        assertThatThrownBy(() -> Invoice.draft(new InvoiceId(UUID.randomUUID()), new TenantId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()),
                new WalletId(UUID.randomUUID()), USD, InvoiceOrigin.CHARGE, List.of(euro), null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvoiceNeedsALineAndEachLineMustBePositive() {
        assertThatThrownBy(() -> Invoice.draft(new InvoiceId(UUID.randomUUID()), new TenantId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()),
                new WalletId(UUID.randomUUID()), USD, InvoiceOrigin.CHARGE, List.of(), null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSubscriptionLineMustNameItsSubscription() {
        assertThatThrownBy(() -> new InvoiceItem(UUID.randomUUID(), ItemSource.SUBSCRIPTION_PERIOD, UUID.randomUUID(), null, null, null, "x", Money.of(5, USD), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDraftHasNoNumberAndIsNotPayable() {
        Invoice invoice = draft(100);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.number()).isNull();
        assertThatThrownBy(() -> invoice.startPayment(UUID.randomUUID(), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuingNumbersAndOpensTheInvoiceOnce() {
        Invoice invoice = draft(100);
        invoice.issue("INV-000007", NOW);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.number()).isEqualTo("INV-000007");
        assertThatThrownBy(() -> invoice.issue("INV-000008", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void numbersAreFormattedWithPaddingAndStartAtOne() {
        assertThat(InvoiceNumber.format(42)).isEqualTo("INV-000042");
        assertThatThrownBy(() -> InvoiceNumber.format(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aVerifiedPaymentPaysAnOpenInvoiceAndKeepsTheHistory() {
        Invoice invoice = open(1_000);
        UUID payment = UUID.randomUUID();
        invoice.startPayment(payment, NOW);
        UUID entry = UUID.randomUUID();

        assertThat(invoice.recordPaymentSucceeded(payment, entry, NOW.plusSeconds(5))).isEqualTo(Invoice.Change.PAID);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.settlement()).isEqualTo(Settlement.PAYMENT);
        assertThat(invoice.paidAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(invoice.payments()).singleElement().satisfies(p -> {
            assertThat(p.status()).isEqualTo(InvoicePayment.Status.SUCCEEDED);
            assertThat(p.ledgerEntryId()).isEqualTo(entry);
            assertThat(p.amount()).isEqualTo(invoice.total());
        });
    }

    @Test
    void theSameSuccessTwiceChangesNothing() {
        Invoice invoice = open(1_000);
        UUID payment = UUID.randomUUID();
        invoice.recordPaymentSucceeded(payment, UUID.randomUUID(), NOW);
        assertThat(invoice.recordPaymentSucceeded(payment, UUID.randomUUID(), NOW.plusSeconds(1))).isEqualTo(Invoice.Change.UNCHANGED);
        assertThat(invoice.paidAt()).isEqualTo(NOW);
    }

    @Test
    void aSecondPaymentForAPaidInvoiceIsRecordedButDoesNotMoveIt() {
        Invoice invoice = open(1_000);
        invoice.recordPaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), NOW);
        assertThat(invoice.recordPaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(1))).isEqualTo(Invoice.Change.RECORDED_ONLY);
        assertThat(invoice.payments()).hasSize(2);
        assertThat(invoice.paidAt()).isEqualTo(NOW);
    }

    @Test
    void aFailedPaymentLeavesTheInvoiceOpenAndPayableAgain() {
        Invoice invoice = open(1_000);
        UUID first = UUID.randomUUID();
        invoice.startPayment(first, NOW);
        assertThat(invoice.recordPaymentFailed(first, "card declined", NOW.plusSeconds(1))).isEqualTo(Invoice.Change.RECORDED_ONLY);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.OPEN);
        invoice.startPayment(UUID.randomUUID(), NOW.plusSeconds(2));
        assertThat(invoice.payments()).extracting(InvoicePayment::status).containsExactly(InvoicePayment.Status.FAILED, InvoicePayment.Status.STARTED);
    }

    @Test
    void aSucceededPaymentCannotBeFailedByALateEvent() {
        Invoice invoice = open(1_000);
        UUID payment = UUID.randomUUID();
        invoice.recordPaymentSucceeded(payment, UUID.randomUUID(), NOW);
        assertThat(invoice.recordPaymentFailed(payment, "late", NOW.plusSeconds(1))).isEqualTo(Invoice.Change.UNCHANGED);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void aWalletThatAlreadyPaidSettlesTheInvoiceWithoutAPayment() {
        Invoice invoice = open(500);
        invoice.settleFromWallet(NOW);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.settlement()).isEqualTo(Settlement.WALLET);
        assertThat(invoice.payments()).isEmpty();
    }

    @Test
    void voidingNeedsAReasonAndIsRefusedOncePaid() {
        Invoice invoice = open(500);
        assertThatThrownBy(() -> invoice.voidInvoice(" ", NOW)).isInstanceOf(IllegalArgumentException.class);
        invoice.voidInvoice("issued in error", NOW);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOID);
        assertThat(invoice.voidReason()).isEqualTo("issued in error");
        assertThat(invoice.voidedAt()).isEqualTo(NOW);

        Invoice paid = open(500);
        paid.recordPaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> paid.voidInvoice("too late", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aVoidInvoiceCannotBePaidButAnUncollectibleOneStillCanBe() {
        Invoice voided = open(500);
        voided.voidInvoice("mistake", NOW);
        assertThatThrownBy(() -> voided.startPayment(UUID.randomUUID(), NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(voided.recordPaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), NOW)).isEqualTo(Invoice.Change.RECORDED_ONLY);
        assertThat(voided.status()).isEqualTo(InvoiceStatus.VOID);

        Invoice written = open(500);
        written.markUncollectible();
        assertThat(written.recordPaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(), NOW)).isEqualTo(Invoice.Change.PAID);
        assertThat(written.status()).isEqualTo(InvoiceStatus.PAID);
    }
}
