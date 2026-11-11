package dev.ngb.core_billing.payment.application.payment.command;

import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.payment.api.invoice.InvoicePaymentStarted;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePayment;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePaymentCommand;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.Locale;
import java.util.Optional;

/**
 * Starts the payment of an invoice: the payment is stored in REQUIRES_ACTION first, then Stripe's hosted Checkout
 * is created outside any transaction, then its ids are attached. Nothing is credited here, and success is only ever
 * recorded from a verified Stripe result.
 */
public class CreateInvoicePaymentUseCase implements StartInvoicePayment {

    private record Prepared(Payment payment, StripeConfiguration configuration, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final PaymentRepository payments;
    private final WalletLookup wallets;
    private final CheckoutOpener checkouts;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateInvoicePaymentUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, WalletLookup wallets, CheckoutOpener checkouts,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.payments = payments;
        this.wallets = wallets;
        this.checkouts = checkouts;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public InvoicePaymentStarted start(StartInvoicePaymentCommand command) {
        String key = Fingerprint.requireKey(command.idempotencyKey());
        if (!command.amount().isPositive()) {
            throw new IllegalArgumentException("an invoice payment must be positive");
        }
        String hash = Fingerprint.of("invoice", command.invoiceId().toString(), String.valueOf(command.amount().minorUnits()),
                command.amount().currency().code());
        Prepared prepared = unitOfWork.transactional(() -> prepare(command, key, hash));
        Payment payment = prepared.payment();
        if (payment.checkoutSessionId() != null || payment.status() != PaymentStatus.REQUIRES_ACTION) {
            return started(payment, prepared.replayed());
        }
        return started(checkouts.open(payment, prepared.configuration(), command.description(), "invoice-payment-" + payment.id()), false);
    }

    private Prepared prepare(StartInvoicePaymentCommand command, String key, String hash) {
        StripeConfiguration configuration = configurations.findLive().orElseThrow(() -> new ConflictException("Stripe is not configured"));
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(hash) || !existing.get().isForInvoice()) {
                throw new IdempotencyConflictException("payment", key);
            }
            if (existing.get().checkoutSessionId() == null && !existing.get().configurationId().equals(configuration.id())) {
                throw new ConflictException("the Stripe configuration changed since this payment was started; start it again with a new Idempotency-Key");
            }
            return new Prepared(existing.get(), configuration, true);
        }
        if (!configuration.canTakePayments()) {
            throw new ConflictException("Stripe is " + configuration.status().name().toLowerCase(Locale.ROOT) + " and cannot take payments yet");
        }
        WalletSummary wallet = wallets.find(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
        if (!wallet.customerId().equals(command.customerId())) {
            throw new IllegalArgumentException("the wallet belongs to another customer");
        }
        if (!wallet.currency().equals(command.amount().currency())) {
            throw new IllegalArgumentException("the payment is in " + command.amount().currency() + " but the wallet is in " + wallet.currency());
        }
        Payment payment = Payment.startForInvoice(new PaymentId(ids.newId()), configuration, command.invoiceId(), wallet.id(), wallet.customerId(),
                command.amount(), key, hash, clock.now());
        payments.add(payment);
        return new Prepared(payment, configuration, false);
    }

    private static InvoicePaymentStarted started(Payment payment, boolean replayed) {
        // The hosted page is only useful while the customer can still pay.
        String url = payment.status() == PaymentStatus.REQUIRES_ACTION ? payment.checkoutUrl() : null;
        return new InvoicePaymentStarted(payment.id().value(), payment.status().name(), url, replayed);
    }
}
