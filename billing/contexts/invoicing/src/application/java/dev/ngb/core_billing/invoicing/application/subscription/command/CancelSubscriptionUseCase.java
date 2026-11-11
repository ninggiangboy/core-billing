package dev.ngb.core_billing.invoicing.application.subscription.command;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionHistoryEntry;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.time.Instant;
import java.util.UUID;

/**
 * Cancels a subscription at the end of the period that is already paid, so the customer keeps what was paid for. When
 * no paid period is running (nothing charged yet, or the last period could not be paid) it ends at once. Cancelling
 * again changes nothing. Billing does not stop any Resource: the Tenant System decides what the end means for it.
 */
public class CancelSubscriptionUseCase {

    public record Command(SubscriptionId subscriptionId, Audit audit, UUID caller) {}

    private final UnitOfWork unitOfWork;
    private final SubscriptionRepository subscriptions;
    private final CustomerLookup customers;
    private final AuditLog auditLog;
    private final IdGenerator ids;
    private final Clock clock;

    public CancelSubscriptionUseCase(
            UnitOfWork unitOfWork, SubscriptionRepository subscriptions, CustomerLookup customers, AuditLog auditLog, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.auditLog = auditLog;
        this.ids = ids;
        this.clock = clock;
    }

    public Subscription execute(Command command) {
        return unitOfWork.transactional(() -> {
            Subscription subscription = subscriptions.lockById(command.subscriptionId()).orElseThrow(() -> new NotFoundException("subscription not found"));
            customers.findFor(subscription.customerId(), command.caller()).orElseThrow(() -> new NotFoundException("subscription not found"));
            Instant now = clock.now();
            Subscription.Cancellation outcome = subscription.requestCancel(now);
            switch (outcome) {
                case CANCELED_NOW -> subscriptions.appendEvent(SubscriptionHistoryEntry.canceled(ids.newId(), subscription, "canceled before a paid period was running", now));
                case SCHEDULED -> subscriptions.appendEvent(SubscriptionHistoryEntry.cancelRequested(ids.newId(), subscription, now));
                case UNCHANGED -> {
                    return subscription;
                }
                default -> throw new IllegalStateException("unexpected " + outcome);
            }
            subscriptions.update(subscription);
            auditLog.record(AuditEntry.tenant(subscription.tenantId(), command.audit().actor(), "subscription.cancel-requested",
                    "subscription/" + subscription.id(), command.audit().requestId(), now));
            return subscription;
        });
    }
}
