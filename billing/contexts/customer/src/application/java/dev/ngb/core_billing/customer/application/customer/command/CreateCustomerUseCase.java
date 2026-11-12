package dev.ngb.core_billing.customer.application.customer.command;

import dev.ngb.core.event.EventPublisher;
import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.customer.application.customer.port.CustomerRepository;
import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.CustomerEvents.CustomerCreated;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Instant;

/**
 * Creates a Customer, optionally mapped to a Tenant System user, once per idempotency key. The key and the
 * request hash live on the row, so an insert that finds the key already taken replays the original customer.
 * The customer, its mapping, the audit entry and the {@code customer.created} event commit together or not at all.
 */
public class CreateCustomerUseCase {

    /** {@code tenantSystemId} and {@code externalUserId} are both present (a mapped customer) or both absent. */
    public record Command(
            TenantSystemId tenantSystemId, String externalUserId, String displayName, String email, String idempotencyKey, Audit audit) {}

    /** {@code replayed} is true when the key had already been used and the original customer is returned. */
    public record Result(Customer customer, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final CustomerRepository customers;
    private final EventPublisher events;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateCustomerUseCase(
            UnitOfWork unitOfWork,
            CustomerRepository customers,
            EventPublisher events,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.customers = customers;
        this.events = events;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        Fingerprint.requireKey(command.idempotencyKey());
        if ((command.tenantSystemId() == null) != (command.externalUserId() == null)) {
            throw new IllegalArgumentException("a tenant system and an external user id must be given together");
        }
        String hash = fingerprint(command);
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            Instant now = clock.now();
            Customer customer = Customer.create(
                    new CustomerId(ids.newId()), tenantId, command.displayName(), command.email(), command.idempotencyKey(), hash, now);
            if (command.tenantSystemId() != null) {
                customer.mapTo(new ExternalIdentity(command.tenantSystemId(), command.externalUserId()));
            }
            if (!customers.addIfAbsent(customer)) {
                Customer original = customers.findByIdempotencyKey(command.idempotencyKey())
                        .orElseThrow(() -> new IllegalStateException("an idempotent customer vanished after a key conflict"));
                Fingerprint.requireSameRequest("customer.create", command.idempotencyKey(), original.requestHash(), hash);
                return new Result(original, true);
            }
            events.publish(new CustomerCreated(ids.newId(), customer, now));
            auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "customer.created", "customer/" + customer.id(), command.audit().requestId(), now));
            return new Result(customer, false);
        });
    }

    /** A digest of the request, so reusing a key for a different request is detected instead of replayed. */
    private static String fingerprint(Command c) {
        return Fingerprint.sha256(String.join("\u0000",
                String.valueOf(c.tenantSystemId()), String.valueOf(c.externalUserId()), c.displayName() == null ? "" : c.displayName().strip(),
                c.email() == null ? "" : c.email().strip()));
    }
}
