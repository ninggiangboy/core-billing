package dev.ngb.core_billing.invoicing.application.subscription.command;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionHistoryEntry;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.pricing.api.plan.PlanPricing;
import dev.ngb.core_billing.pricing.api.plan.SubscriptionTerms;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscribes a customer's wallet to a plan. The subscription is anchored now and its first period is due at once;
 * it is charged right after, through the same logic as every later renewal ({@link RenewSubscriptionUseCase}). The cycle and the currency come from the plan
 * and stay fixed for the life of the subscription, so the periods never drift if the plan changes later.
 */
public class CreateSubscriptionUseCase {

    public record Command(WalletId walletId, UUID planId, String idempotencyKey, Audit audit, UUID caller) {}

    /** {@code replayed} is true when the idempotency key had been used and the existing subscription is returned. */
    public record Result(Subscription subscription, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final SubscriptionRepository subscriptions;
    private final WalletLookup wallets;
    private final CustomerLookup customers;
    private final PlanPricing plans;
    private final RenewSubscriptionUseCase renewal;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateSubscriptionUseCase(
            UnitOfWork unitOfWork, SubscriptionRepository subscriptions, WalletLookup wallets, CustomerLookup customers, PlanPricing plans,
            RenewSubscriptionUseCase renewal, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.subscriptions = subscriptions;
        this.wallets = wallets;
        this.customers = customers;
        this.plans = plans;
        this.renewal = renewal;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        String key = Fingerprint.requireKey(command.idempotencyKey());
        String hash = Fingerprint.of(command.walletId().toString(), command.planId().toString());
        Result created = unitOfWork.transactional(() -> {
            Optional<Subscription> existing = subscriptions.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                Fingerprint.requireSameRequest("subscription", key, existing.get().requestHash(), hash);
                return new Result(existing.get(), true);
            }
            WalletSummary wallet = wallets.find(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            customers.findFor(wallet.customerId(), command.caller()).orElseThrow(() -> new NotFoundException("wallet not found"));
            Instant now = clock.now();
            SubscriptionTerms terms = plans.subscriptionTerms(command.planId(), now)
                    .orElseThrow(() -> new ConflictException("the plan does not exist, is retired, or has no recurring price in force now"));
            if (!terms.currency().equals(wallet.currency())) {
                throw new IllegalArgumentException("the wallet is in %s but the plan is priced in %s".formatted(wallet.currency(), terms.currency()));
            }
            Subscription subscription = Subscription.create(new SubscriptionId(ids.newId()), tenantContext.requireTenant(), wallet.customerId(), wallet.id(),
                    wallet.currency(), terms.planId(), terms.planCode(), terms.cycle(), key, hash, now);
            if (!subscriptions.addIfAbsent(subscription)) {
                Subscription winner = subscriptions.findByIdempotencyKey(key)
                        .orElseThrow(() -> new IllegalStateException("a subscription vanished after a key conflict"));
                Fingerprint.requireSameRequest("subscription", key, winner.requestHash(), hash);
                return new Result(winner, true);
            }
            subscriptions.appendEvent(SubscriptionHistoryEntry.created(ids.newId(), subscription, now));
            auditLog.record(AuditEntry.tenant(subscription.tenantId(), command.audit().actor(), "subscription.created", "subscription/" + subscription.id(),
                    command.audit().requestId(), now));
            return new Result(subscription, false);
        });
        if (created.replayed()) {
            return created;
        }
        // The first period is due at once: charge it now, in its own transaction, so a refusal by the wallet leaves the subscription recorded.
        renewal.renewIfDue(created.subscription().id());
        return new Result(unitOfWork.transactional(() -> subscriptions.findById(created.subscription().id()).orElseThrow()), false);
    }
}
