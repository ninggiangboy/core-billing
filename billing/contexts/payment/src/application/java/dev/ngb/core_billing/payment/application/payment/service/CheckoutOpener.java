package dev.ngb.core_billing.payment.application.payment.service;

import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.application.stripe.exception.ProviderRejectedException;
import dev.ngb.core_billing.payment.application.stripe.service.ProviderAccess;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;

/**
 * Opens Stripe's hosted Checkout for a payment that is already stored, outside any transaction, and attaches the
 * Stripe ids afterwards. Storing first means a Stripe event can never arrive for a payment Billing does not know.
 * A top-up and an invoice payment take exactly this path.
 */
public class CheckoutOpener {

    private final UnitOfWork unitOfWork;
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final PaymentSettings settings;
    private final Clock clock;

    public CheckoutOpener(
            UnitOfWork unitOfWork, PaymentRepository payments, PaymentProvider provider, ProviderAccess access, PaymentSettings settings, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.payments = payments;
        this.provider = provider;
        this.access = access;
        this.settings = settings;
        this.clock = clock;
    }

    /** Returns the payment as it stands: already open or settled payments are returned unchanged, so a replay never creates a second session. */
    public Payment open(Payment payment, StripeConfiguration configuration, String title, String stripeIdempotencyKey) {
        if (payment.checkoutSessionId() != null || payment.status() != PaymentStatus.REQUIRES_ACTION) {
            return payment;
        }
        PaymentProvider.CreatedCheckout checkout;
        try {
            checkout = provider.createCheckout(access.of(configuration), new PaymentProvider.CheckoutRequest(payment.id(), payment.tenantId(),
                    payment.amount(), title, urlFor(settings.checkoutSuccessUrl(), payment.id()), urlFor(settings.checkoutCancelUrl(), payment.id())),
                    stripeIdempotencyKey);
        } catch (ProviderRejectedException rejected) {
            // Stripe said no for good: the payment never reached a customer, so close it and tell the caller.
            unitOfWork.transactional(() -> {
                Payment locked = payments.lockById(payment.id()).orElseThrow();
                locked.fail(rejected.getMessage(), clock.now());
                payments.update(locked);
            });
            throw rejected;
        }
        return unitOfWork.transactional(() -> {
            Payment locked = payments.lockById(payment.id()).orElseThrow();
            locked.attachCheckout(checkout.sessionId(), checkout.url(), clock.now());
            payments.update(locked);
            return locked;
        });
    }

    private static String urlFor(String template, PaymentId id) {
        return template.replace("{PAYMENT_ID}", id.toString());
    }
}
