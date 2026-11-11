package dev.ngb.core_billing.payment.application.refund.command;

import dev.ngb.core_billing.money.api.wallet.InsufficientFundsException;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.payment.domain.refund.Refund;
import dev.ngb.core_billing.payment.domain.refund.RefundId;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfiguration;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Optional;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.payment.application.payment.service.PaymentSettings;
import dev.ngb.core_billing.payment.application.payment.service.PaymentSettlement;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.application.refund.port.RefundRepository;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.payment.application.stripe.exception.ProviderRejectedException;
import dev.ngb.core_billing.payment.application.stripe.service.ProviderAccess;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;

/**
 * Sends money back to the customer's card, in full or in part. Three rules protect the books:
 * refunds never add up to more than was captured; the unspent balance is held before Stripe is asked, so a
 * refund cannot drive a Prepaid wallet negative; and the ledger changes only when Stripe reports the refund
 * succeeded (see {@link PaymentSettlement}), never because the request was accepted.
 */
public class RequestRefundUseCase {

    /** Well inside the 24 hours Stripe remembers an idempotency key for. */
    private static final java.time.Duration RETRY_WINDOW = java.time.Duration.ofHours(12);

    public record Command(PaymentId paymentId, Money amount, String reason, String idempotencyKey, Audit audit) {}

    public record Result(Refund refund, boolean replayed) {}

    private record Prepared(Refund refund, Payment payment, StripeConfiguration configuration, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final StripeConfigurationRepository configurations;
    private final ReserveBudget reserve;
    private final PaymentSettlement settlement;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final PaymentSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public RequestRefundUseCase(
            UnitOfWork unitOfWork, PaymentRepository payments, RefundRepository refunds, StripeConfigurationRepository configurations,
            ReserveBudget reserve, PaymentSettlement settlement, PaymentProvider provider, ProviderAccess access, PaymentSettings settings,
            AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.payments = payments;
        this.refunds = refunds;
        this.configurations = configurations;
        this.reserve = reserve;
        this.settlement = settlement;
        this.provider = provider;
        this.access = access;
        this.settings = settings;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    /** @param command {@code amount} null means everything still refundable */
    public Result execute(Command command) {
        String key = Fingerprint.requireKey(command.idempotencyKey());
        if (command.amount() != null && !command.amount().isPositive()) {
            throw new IllegalArgumentException("a refund must be positive");
        }
        String hash = Fingerprint.of(command.paymentId().toString(), command.amount() == null ? "all" : command.amount().toString(),
                String.valueOf(command.reason()));
        Prepared prepared = unitOfWork.transactional(() -> prepare(command, key, hash));
        Refund refund = prepared.refund();
        if (refund.stripeRefundId() != null || refund.status().isFinal()) {
            return new Result(refund, prepared.replayed());
        }
        if (prepared.replayed() && refund.createdAt().isBefore(clock.now().minus(RETRY_WINDOW))) {
            // Stripe forgets an idempotency key after a day, so asking again could refund twice. The reconciliation
            // job finds the refund by its reference and settles it; until then there is nothing safe to repeat.
            throw new ConflictException("the outcome of this refund is unknown and will be settled by reconciliation; it cannot be repeated now");
        }
        PaymentProvider.RefundObject created;
        try {
            created = provider.createRefund(access.of(prepared.configuration()), new PaymentProvider.RefundRequest(refund.id(),
                    prepared.payment().id(), prepared.payment().paymentIntentId(), refund.amount(), refund.reason()), "refund-" + refund.id());
        } catch (ProviderRejectedException rejected) {
            unitOfWork.transactional(() -> {
                Refund locked = refunds.lockById(refund.id()).orElseThrow();
                settlement.releaseHold(locked);
                locked.fail(rejected.getMessage(), clock.now());
                refunds.update(locked);
            });
            throw rejected;
        }
        // The outcome of an unreachable Stripe is unknown: the refund stays PENDING and the same key retries it safely.
        Refund attached = unitOfWork.transactional(() -> {
            Refund locked = refunds.lockById(refund.id()).orElseThrow();
            locked.attachStripeRefund(created.refundId(), clock.now());
            refunds.update(locked);
            return locked;
        });
        return new Result(attached, false);
    }

    /**
     * The configuration a payment can be refunded through: its own while connected, or the current one when the
     * Tenant has since linked the same Stripe account again (a rotated key). A payment of an account that is no
     * longer connected has to be refunded in the Stripe Dashboard.
     */
    private StripeConfiguration usable(Payment payment) {
        StripeConfiguration own = configurations.findById(payment.configurationId()).orElseThrow();
        if (own.status().isLive()) {
            return own;
        }
        return configurations.findLive().filter(live -> live.accountId().equals(own.accountId()))
                .orElseThrow(() -> new ConflictException("the payment's Stripe account is no longer connected, so it cannot be refunded here"));
    }

    private Prepared prepare(Command command, String key, String hash) {
        Optional<Refund> existing = refunds.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(hash)) {
                throw new IdempotencyConflictException("refund", key);
            }
            Payment payment = payments.findById(existing.get().paymentId()).orElseThrow();
            return new Prepared(existing.get(), payment, usable(payment), true);
        }
        // Lock order is payment, then refund, then wallet, everywhere.
        Payment payment = payments.lockById(command.paymentId()).orElseThrow(() -> new NotFoundException("payment not found"));
        if (payment.status() != PaymentStatus.SUCCEEDED) {
            throw new ConflictException("only a succeeded payment can be refunded; this one is " + payment.status());
        }
        if (payment.isForInvoice()) {
            // Refunding it would bring the paid-off debt back while the invoice stays paid.
            throw new ConflictException("a payment of an invoice cannot be refunded through Billing");
        }
        if (payment.paymentIntentId() == null) {
            throw new ConflictException("the payment has no Stripe payment to refund");
        }
        StripeConfiguration configuration = usable(payment);
        Money inFlight = Money.of(refunds.liveAmount(payment.id()), payment.amount().currency());
        Money remaining = payment.amount().minus(inFlight);
        Money amount = command.amount() == null ? remaining : command.amount();
        if (!amount.currency().equals(payment.amount().currency())) {
            throw new IllegalArgumentException("the refund must be in " + payment.amount().currency());
        }
        if (!amount.isPositive() || amount.isGreaterThan(remaining)) {
            throw new ConflictException("at most " + remaining + " of this payment can still be refunded");
        }
        Instant now = clock.now();
        RefundId id = new RefundId(ids.newId());
        ReservationResult hold;
        try {
            hold = reserve.reserve(new ReserveBudgetCommand(payment.walletId(), amount, settings.refundHold(), "refund/" + id, "refund-hold:" + id, null));
        } catch (InsufficientFundsException unspent) {
            throw new ConflictException("only the unspent balance of a top-up can be refunded; the wallet cannot cover " + amount);
        }
        Refund refund = Refund.request(id, payment, amount, command.reason(), hold.id().value(), command.audit().actor(), key, hash, now);
        refunds.add(refund);
        auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), command.audit().actor(), "refund.requested", "refund/" + id,
                command.audit().requestId(), now));
        return new Prepared(refund, payment, configuration, false);
    }
}
