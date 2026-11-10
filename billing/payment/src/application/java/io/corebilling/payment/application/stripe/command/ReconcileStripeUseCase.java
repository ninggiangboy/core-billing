package io.corebilling.payment.application.stripe.command;

import io.corebilling.payment.domain.incident.IncidentKind;
import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundStatus;
import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.corebilling.payment.application.incident.port.IncidentRepository;
import io.corebilling.payment.application.payment.command.PaymentSettings;
import io.corebilling.payment.application.shared.command.PaymentSettlement;
import io.corebilling.payment.application.payment.port.PaymentProvider;
import io.corebilling.payment.application.payment.port.PaymentRepository;
import io.corebilling.payment.application.refund.port.RefundRepository;
import io.corebilling.payment.application.stripe.port.ProviderAccess;
import io.corebilling.payment.application.stripe.port.StripeConfigurationRepository;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.refund.RefundId;

/**
 * Compares the current tenant's recent payments and refunds with what Stripe holds. A disagreement is recorded
 * as an incident, and where Stripe's side is authoritative and ahead (a payment that was paid while the webhook
 * was lost) the correction goes through {@link PaymentSettlement}, the path a verified event takes. Where Billing
 * is ahead of Stripe, nothing is changed and a person is told.
 */
public class ReconcileStripeUseCase {

    public record Report(int checked, int corrected, int mismatches) {}

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final IncidentRepository incidents;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final PaymentSettlement settlement;
    private final PaymentSettings settings;
    private final IdGenerator ids;
    private final Clock clock;

    public ReconcileStripeUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, RefundRepository refunds,
            IncidentRepository incidents, PaymentProvider provider, ProviderAccess access, PaymentSettlement settlement, PaymentSettings settings,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.payments = payments;
        this.refunds = refunds;
        this.incidents = incidents;
        this.provider = provider;
        this.access = access;
        this.settlement = settlement;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
    }

    public Report runOnce() {
        Optional<StripeConfiguration> found = unitOfWork.transactional(configurations::findLive);
        if (found.isEmpty()) {
            return new Report(0, 0, 0);
        }
        StripeConfiguration configuration = found.get();
        PaymentProvider.Access credentials = access.of(configuration);
        Instant since = clock.now().minus(settings.reconcileLookback());
        // The provider is read first, outside any transaction; the comparison and corrections follow in short ones.
        List<PaymentProvider.Checkout> checkouts = provider.recentCheckouts(credentials, since);
        List<PaymentProvider.RefundObject> refundObjects = provider.recentRefunds(credentials, since);

        int checked = 0;
        int corrected = 0;
        int mismatches = 0;
        for (PaymentProvider.Checkout checkout : checkouts) {
            if (checkout.paymentRef() == null) {
                continue;
            }
            checked++;
            try {
                // A completed session that is not yet paid needs its intent; it is read here, before any transaction.
                PaymentProvider.Intent intent = checkout.status() == PaymentProvider.CheckoutStatus.COMPLETE
                        && checkout.payment() == PaymentProvider.CheckoutPayment.UNPAID && checkout.paymentIntentId() != null
                        ? provider.retrievePayment(credentials, checkout.paymentIntentId())
                        : null;
                int[] outcome = unitOfWork.transactional(() -> reconcileCheckout(configuration, checkout, intent));
                corrected += outcome[0];
                mismatches += outcome[1];
            } catch (RuntimeException failure) {
                // One bad item must not hide the rest; it is flagged for a person and the next run looks at it again.
                mismatches += failed(configuration, "checkout/" + checkout.sessionId(), failure);
            }
        }
        for (PaymentProvider.RefundObject refund : refundObjects) {
            checked++;
            try {
                int[] outcome = unitOfWork.transactional(() -> reconcileRefund(configuration, refund));
                corrected += outcome[0];
                mismatches += outcome[1];
            } catch (RuntimeException failure) {
                mismatches += failed(configuration, "refund/" + refund.refundId(), failure);
            }
        }
        return new Report(checked, corrected, mismatches);
    }

    /** @return {corrected, mismatches} */
    private int[] reconcileCheckout(StripeConfiguration configuration, PaymentProvider.Checkout checkout, PaymentProvider.Intent intent) {
        Optional<Payment> local = parse(checkout.paymentRef()).flatMap(id -> payments.findById(new io.corebilling.payment.domain.payment.PaymentId(id)));
        if (local.isEmpty()) {
            return new int[] {0, 0};
        }
        Payment payment = local.get();
        boolean stripePaid = checkout.status() == PaymentProvider.CheckoutStatus.COMPLETE && checkout.payment() == PaymentProvider.CheckoutPayment.PAID;
        if (payment.status() == PaymentStatus.SUCCEEDED && !stripePaid) {
            flag(configuration, "payment/" + payment.id(), "Billing has the payment as succeeded but Stripe's session is "
                    + checkout.status() + "/" + checkout.payment());
            return new int[] {0, 1};
        }
        if (payment.status().isFinal()) {
            return new int[] {0, 0};
        }
        PaymentSettlement.Result result = settlement.checkout(configuration, checkout, "reconciliation:" + checkout.sessionId(), false, intent);
        if (result.kind() == PaymentSettlement.Kind.APPLIED) {
            flag(configuration, "payment/" + payment.id(), "Corrected from Stripe: " + result.detail());
            return new int[] {1, 1};
        }
        if (result.kind() == PaymentSettlement.Kind.REJECTED) {
            flag(configuration, "payment/" + payment.id(), "Could not correct from Stripe: " + result.detail());
            return new int[] {0, 1};
        }
        return new int[] {0, 0};
    }

    private int[] reconcileRefund(StripeConfiguration configuration, PaymentProvider.RefundObject observed) {
        Optional<Refund> local = observed.refundRef() == null
                ? refunds.findByStripeRefundId(observed.refundId())
                : parse(observed.refundRef()).flatMap(id -> refunds.findById(new io.corebilling.payment.domain.refund.RefundId(id)));
        if (local.isEmpty()) {
            // Not ours to account for unless it refunds one of our payments; the settlement flags that case.
            if (observed.intentId() == null || payments.lockByPaymentIntent(observed.intentId()).isEmpty()) {
                return new int[] {0, 0};
            }
            settlement.refund(configuration, observed, "reconciliation:" + observed.refundId());
            return new int[] {0, 1};
        }
        Refund refund = local.get();
        if (refund.status() == RefundStatus.SUCCEEDED && observed.state() != PaymentProvider.RefundState.SUCCEEDED) {
            flag(configuration, "refund/" + refund.id(), "Billing has the refund as succeeded but Stripe reports " + observed.state());
            return new int[] {0, 1};
        }
        if (refund.status().isFinal()) {
            return new int[] {0, 0};
        }
        PaymentSettlement.Result result = settlement.refund(configuration, observed, "reconciliation:" + observed.refundId());
        if (result.kind() == PaymentSettlement.Kind.APPLIED) {
            flag(configuration, "refund/" + refund.id(), "Corrected from Stripe: " + result.detail());
            return new int[] {1, 1};
        }
        if (result.kind() == PaymentSettlement.Kind.REJECTED) {
            flag(configuration, "refund/" + refund.id(), "Could not correct from Stripe: " + result.detail());
            return new int[] {0, 1};
        }
        return new int[] {0, 0};
    }

    private int failed(StripeConfiguration configuration, String subject, RuntimeException failure) {
        unitOfWork.transactional(() -> flag(configuration, subject, "Reconciliation could not examine this: " + failure.getClass().getSimpleName() + ": "
                + failure.getMessage()));
        return 1;
    }

    private void flag(StripeConfiguration configuration, String subject, String detail) {
        incidents.record(ids.newId(), configuration.tenantId(), configuration.id(), IncidentKind.RECONCILIATION_MISMATCH, subject, detail, clock.now());
    }

    private static Optional<UUID> parse(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
