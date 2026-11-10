package dev.ngb.core_billing.payment.application.payment.command;

import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
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
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import dev.ngb.core_billing.payment.application.payment.port.PaymentProvider;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.application.shared.command.Audit;
import dev.ngb.core_billing.payment.application.stripe.command.ProviderRejectedException;
import dev.ngb.core_billing.payment.application.stripe.port.ProviderAccess;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.payment.application.shared.command.Fingerprint;

/**
 * Starts a wallet top-up through the Tenant's Stripe configuration. The payment is stored first, in
 * REQUIRES_ACTION, so a Stripe event can never arrive for a payment Billing does not know; then Stripe's hosted
 * Checkout is created outside any transaction; then the Stripe ids are attached. Nothing is credited here: the
 * wallet changes only when a verified Stripe event says the money arrived.
 */
public class CreateTopUpUseCase {

    public record Command(WalletId walletId, Money amount, String idempotencyKey, Audit audit, UUID caller) {
        public Command(WalletId walletId, Money amount, String idempotencyKey, Audit audit) {
            this(walletId, amount, idempotencyKey, audit, null);
        }
    }

    /** {@code replayed} is true when the idempotency key had been used and the existing payment is returned. */
    public record Result(Payment payment, boolean replayed) {}

    private record Prepared(Payment payment, StripeConfiguration configuration, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final StripeConfigurationRepository configurations;
    private final PaymentRepository payments;
    private final WalletLookup wallets;
    private final CustomerLookup customers;
    private final PaymentProvider provider;
    private final ProviderAccess access;
    private final PaymentSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateTopUpUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, WalletLookup wallets,
            CustomerLookup customers, PaymentProvider provider, ProviderAccess access, PaymentSettings settings, AuditLog auditLog,
            TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.payments = payments;
        this.wallets = wallets;
        this.customers = customers;
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
        customers.findFor(wallet.customerId(), command.caller()).orElseThrow(() -> new NotFoundException("wallet not found"));
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
