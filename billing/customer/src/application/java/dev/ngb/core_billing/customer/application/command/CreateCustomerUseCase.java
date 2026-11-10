package dev.ngb.core_billing.customer.application.command;

import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.CustomerCreated;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.idempotency.StoredResponse;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import dev.ngb.core_billing.customer.application.port.CustomerRepository;

/**
 * Creates a Customer, optionally mapped to a Tenant System user, once per idempotency key. The key, the
 * customer, its mapping, the audit entry and the {@code customer.created} event commit together or not at all.
 */
public class CreateCustomerUseCase {

    private static final String SCOPE = "customer.create";

    /** {@code tenantSystemId} and {@code externalUserId} are both present (a mapped customer) or both absent. */
    public record Command(
            TenantSystemId tenantSystemId, String externalUserId, String displayName, String email, String idempotencyKey, AuditContext audit) {}

    /** {@code replayed} is true when the key had already been used and the original customer is returned. */
    public record Result(Customer customer, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final CustomerRepository customers;
    private final IdempotencyStore idempotency;
    private final EventPublisher events;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateCustomerUseCase(
            UnitOfWork unitOfWork,
            CustomerRepository customers,
            IdempotencyStore idempotency,
            EventPublisher events,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.customers = customers;
        this.idempotency = idempotency;
        this.events = events;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        requireKey(command.idempotencyKey());
        if ((command.tenantSystemId() == null) != (command.externalUserId() == null)) {
            throw new IllegalArgumentException("a tenant system and an external user id must be given together");
        }
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            var stored = idempotency.begin(tenantId, SCOPE, command.idempotencyKey(), fingerprint(command));
            if (stored.isPresent()) {
                Customer original = customers.findById(CustomerId.of(stored.get().body()))
                        .orElseThrow(() -> new IllegalStateException("an idempotent customer no longer exists"));
                return new Result(original, true);
            }
            Instant now = clock.now();
            Customer customer = Customer.create(new CustomerId(ids.newId()), tenantId, command.displayName(), command.email(), now);
            if (command.tenantSystemId() != null) {
                customer.mapTo(new ExternalIdentity(command.tenantSystemId(), command.externalUserId()));
            }
            customers.add(customer);
            events.publish(new CustomerCreated(ids.newId(), customer, now));
            auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "customer.created", "customer/" + customer.id(), command.audit().requestId(), now));
            idempotency.complete(tenantId, SCOPE, command.idempotencyKey(), new StoredResponse(201, customer.id().toString()));
            return new Result(customer, false);
        });
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255 || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-255 printable characters");
        }
    }

    /** A digest of the request, so reusing a key for a different request is detected instead of replayed. */
    private static String fingerprint(Command c) {
        String canonical = String.join("\u0000",
                String.valueOf(c.tenantSystemId()), String.valueOf(c.externalUserId()), c.displayName() == null ? "" : c.displayName().strip(),
                c.email() == null ? "" : c.email().strip());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
