package io.corebilling.payment.application;

import io.corebilling.money.api.WalletLookup;
import io.corebilling.money.api.WalletSummary;
import io.corebilling.payment.domain.Payment;
import io.corebilling.payment.domain.PaymentId;
import io.corebilling.payment.domain.PaymentStatus;
import io.corebilling.payment.domain.StripeConfiguration;
import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.IdempotencyConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Optional;

/**
 * Starts a wallet top-up through the Tenant's Stripe configuration. The payment is stored first, in
 * REQUIRES_ACTION, so a Stripe event can never arrive for a payment Billing does not know; then Stripe's hosted
 * Checkout is created outside any transaction; then the Stripe ids are attached. Nothing is credited here: the
 * wallet changes only when a verified Stripe event says the money arrived.
 */
public class CreateTopUpUseCase {

    public record Command(WalletId walletId, Money amount, String idempotencyKey, Audit audit) {}

    /** {@code replayed} is true when the idempotency key had been used and the existing payment is returned. */
    public record Result(Payment payment, boolean replayed) {}

    private record Prepared(Payment payment, StripeConfiguration configuration, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final PaymentRepository payments;
    private final WalletLookup wallets;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final PaymentSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateTopUpUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, WalletLookup wallets,
            PaymentProvider provider, ProviderAccess access, PaymentSettings settings, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.payments = payments;
        this.wallets = wallets;
        this.provider = provider;
        this.access = access;
        this.settings = settings;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        String key = Fingerprint.requireKey(command.idempotencyKey());
        if (!command.amount().isPositive()) {
            throw new IllegalArgumentException("a top-up must be positive");
        }
        String hash = Fingerprint.of(command.walletId().toString(), String.valueOf(command.amount().minorUnits()), command.amount().currency().code());
        Prepared prepared = unitOfWork.transactional(() -> prepare(command, key, hash));
        Payment payment = prepared.payment();
        if (payment.checkoutSessionId() != null || payment.status() != PaymentStatus.REQUIRES_ACTION) {
            return new Result(payment, prepared.replayed());
        }
        PaymentProvider.CreatedCheckout checkout;
        try {
            checkout = provider.createCheckout(access.of(prepared.configuration()), new PaymentProvider.CheckoutRequest(payment.id(),
                    payment.tenantId(), payment.amount(), "Wallet top-up", urlFor(settings.checkoutSuccessUrl(), payment.id()),
                    urlFor(settings.checkoutCancelUrl(), payment.id())), "topup-" + payment.id());
        } catch (ProviderRejectedException rejected) {
            // Stripe said no for good: the payment never reached a customer, so close it and tell the caller.
            unitOfWork.transactional(() -> {
                Payment locked = payments.lockById(payment.id()).orElseThrow();
                locked.fail(rejected.getMessage(), clock.now());
                payments.update(locked);
            });
            throw rejected;
        }
        Payment attached = unitOfWork.transactional(() -> {
            Payment locked = payments.lockById(payment.id()).orElseThrow();
            locked.attachCheckout(checkout.sessionId(), checkout.url(), clock.now());
            payments.update(locked);
            return locked;
        });
        return new Result(attached, false);
    }

    private Prepared prepare(Command command, String key, String hash) {
        StripeConfiguration configuration = configurations.findLive().orElseThrow(() -> new ConflictException("Stripe is not configured"));
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(hash)) {
                throw new IdempotencyConflictException("payment", key);
            }
            if (existing.get().checkoutSessionId() == null && !existing.get().configurationId().equals(configuration.id())) {
                // The payment was stored for a configuration that has since been replaced; Stripe would report it to the old one.
                throw new ConflictException("the Stripe configuration changed since this top-up was started; start it again with a new Idempotency-Key");
            }
            return new Prepared(existing.get(), configuration, true);
        }
        if (!configuration.canTakePayments()) {
            throw new ConflictException("Stripe is " + configuration.status().name().toLowerCase(java.util.Locale.ROOT) + " and cannot take payments yet");
        }
        WalletSummary wallet = wallets.find(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
        if (!wallet.currency().equals(command.amount().currency())) {
            throw new IllegalArgumentException("the top-up is in " + command.amount().currency() + " but the wallet is in " + wallet.currency());
        }
        Instant now = clock.now();
        Payment payment = Payment.start(new PaymentId(ids.newId()), configuration, wallet.id(), wallet.customerId(), command.amount(), key, hash, now);
        payments.add(payment);
        TenantId tenantId = tenantContext.requireTenant();
        auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "payment.top-up-started", "payment/" + payment.id(),
                command.audit().requestId(), now));
        return new Prepared(payment, configuration, false);
    }

    private static String urlFor(String template, PaymentId id) {
        return template.replace("{PAYMENT_ID}", id.toString());
    }
}
