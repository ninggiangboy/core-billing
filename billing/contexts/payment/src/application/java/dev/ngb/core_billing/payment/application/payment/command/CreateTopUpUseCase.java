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
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.payment.application.stripe.port.StripeConfigurationRepository;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.payment.application.payment.service.CheckoutOpener;

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
    private final CheckoutOpener checkouts;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateTopUpUseCase(
            UnitOfWork unitOfWork, StripeConfigurationRepository configurations, PaymentRepository payments, WalletLookup wallets,
            CustomerLookup customers, CheckoutOpener checkouts, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.configurations = configurations;
        this.payments = payments;
        this.wallets = wallets;
        this.customers = customers;
        this.checkouts = checkouts;
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
        return new Result(checkouts.open(payment, prepared.configuration(), "Wallet top-up", "topup-" + payment.id()), false);
    }

    private Prepared prepare(Command command, String key, String hash) {
        StripeConfiguration configuration = configurations.findLive().orElseThrow(() -> new ConflictException("Stripe is not configured"));
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Fingerprint.requireSameRequest("payment", key, existing.get().requestHash(), hash);
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
            throw new IllegalArgumentException("the top-up is in %s but the wallet is in %s".formatted(command.amount().currency(), wallet.currency()));
        }
        Instant now = clock.now();
        Payment payment = Payment.start(new PaymentId(ids.newId()), configuration, wallet.id(), wallet.customerId(), command.amount(), key, hash, now);
        payments.add(payment);
        TenantId tenantId = tenantContext.requireTenant();
        auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "payment.top-up-started", "payment/" + payment.id(),
                command.audit().requestId(), now));
        return new Prepared(payment, configuration, false);
    }
}
