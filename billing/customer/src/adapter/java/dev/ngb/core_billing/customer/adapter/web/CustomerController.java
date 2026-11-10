package dev.ngb.core_billing.customer.adapter.web;

import dev.ngb.core_billing.customer.application.command.AuditContext;
import dev.ngb.core_billing.customer.application.command.CreateCustomerUseCase;
import dev.ngb.core_billing.customer.application.command.CustomerAdministration;
import dev.ngb.core_billing.customer.application.query.CustomerQueries;
import dev.ngb.core_billing.customer.application.query.CustomerView;
import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.platform.api.error.AccessDeniedException;
import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customers of the current tenant, for both kinds of caller: a Tenant System (API key) maps its own users, a
 * Tenant User (OIDC) administers customers and names the tenant system explicitly.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    public record CreateCustomerRequest(String tenantSystemId, String externalUserId, String displayName, String email) {}

    public record UpdateCustomerRequest(String displayName, String email) {}

    public record IdentityResponse(String tenantSystemId, String externalUserId) {}

    public record CustomerResponse(String id, String displayName, String email, String status, Instant createdAt, List<IdentityResponse> identities) {
        static CustomerResponse of(Customer customer) {
            return new CustomerResponse(
                    customer.id().toString(), customer.displayName(), customer.email(), customer.status().name(), customer.createdAt(),
                    customer.identities().stream()
                            .map(i -> new IdentityResponse(i.tenantSystemId().toString(), i.externalUserId()))
                            .sorted(java.util.Comparator.comparing(IdentityResponse::externalUserId))
                            .toList());
        }

        static CustomerResponse ofView(CustomerView customer) {
            return new CustomerResponse(
                    customer.id().toString(), customer.displayName(), customer.email(), customer.status().name(), customer.createdAt(),
                    customer.identities().stream()
                            .map(i -> new IdentityResponse(i.tenantSystemId().toString(), i.externalUserId()))
                            .sorted(java.util.Comparator.comparing(IdentityResponse::externalUserId))
                            .toList());
        }
    }

    private final CreateCustomerUseCase create;
    private final CustomerAdministration administration;
    private final CustomerQueries queries;
    private final CurrentActor currentActor;

    public CustomerController(CreateCustomerUseCase create, CustomerAdministration administration, CustomerQueries queries, CurrentActor currentActor) {
        this.create = create;
        this.administration = administration;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    /** Safe to retry: the same Idempotency-Key returns the same customer; the same key with a different body is a 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CUSTOMER_WRITE)
    public CustomerResponse create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CreateCustomerRequest request) {
        Actor actor = currentActor.get();
        TenantSystemId system = tenantSystemFor(actor, request.tenantSystemId());
        return CustomerResponse.of(create.execute(new CreateCustomerUseCase.Command(
                system, request.externalUserId(), request.displayName(), request.email(), idempotencyKey, audit(actor, requestId))).customer());
    }

    @GetMapping
    @RequiresPermission(Permission.CUSTOMER_READ)
    public List<CustomerResponse> list() {
        return queries.list().stream().map(CustomerResponse::ofView).toList();
    }

    @GetMapping("/by-external-user/{externalUserId}")
    @RequiresPermission(Permission.CUSTOMER_READ)
    public CustomerResponse byExternalUser(@PathVariable String externalUserId, @RequestParam(required = false) String tenantSystemId) {
        TenantSystemId system = tenantSystemFor(currentActor.get(), tenantSystemId);
        if (system == null) {
            throw new IllegalArgumentException("tenantSystemId is required");
        }
        return CustomerResponse.ofView(queries.getByExternalUser(system, externalUserId));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.CUSTOMER_READ)
    public CustomerResponse get(@PathVariable String id) {
        return CustomerResponse.ofView(queries.get(CustomerId.of(id)));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.CUSTOMER_WRITE)
    public CustomerResponse update(
            @PathVariable String id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.of(administration.update(
                audit(currentActor.get(), requestId), CustomerId.of(id), request.displayName(), request.email()));
    }

    @PostMapping("/{id}/deactivate")
    @RequiresPermission(Permission.CUSTOMER_WRITE)
    public CustomerResponse deactivate(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return CustomerResponse.of(administration.deactivate(audit(currentActor.get(), requestId), CustomerId.of(id)));
    }

    /**
     * A Tenant System always acts for itself and may not name another one; a Tenant User names the system (or
     * none, to create an unmapped customer).
     */
    private static TenantSystemId tenantSystemFor(Actor actor, String requested) {
        if (actor.tenantSystemId() != null) {
            if (requested != null && !TenantSystemId.of(requested).equals(actor.tenantSystemId())) {
                throw new AccessDeniedException("a tenant system can only act for itself");
            }
            return actor.tenantSystemId();
        }
        return requested == null ? null : TenantSystemId.of(requested);
    }

    private static AuditContext audit(Actor actor, String requestId) {
        boolean usable = requestId != null && !requestId.isBlank() && requestId.length() <= 64 && requestId.chars().allMatch(c -> c > 32 && c < 127);
        return new AuditContext(actor.auditName(), usable ? requestId : UUID.randomUUID().toString());
    }
}
