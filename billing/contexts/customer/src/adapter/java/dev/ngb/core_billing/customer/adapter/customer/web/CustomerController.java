package dev.ngb.core_billing.customer.adapter.customer.web;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.customer.application.customer.command.CreateCustomerUseCase;
import dev.ngb.core_billing.customer.application.customer.command.ManageCustomerUseCase;
import dev.ngb.core_billing.customer.application.customer.query.CustomerQueries;
import dev.ngb.core_billing.customer.application.customer.query.CustomerView;
import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.CurrentActor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.error.AccessDeniedException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.api.idempotency.RepeatableCreate;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customers of the current tenant, for both kinds of caller: a Tenant System (API key) maps its own users, a
 * Tenant User (OIDC) administers customers and names the tenant system explicitly.
 */
@Profile("api")
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
    private final ManageCustomerUseCase manage;
    private final CustomerQueries queries;
    private final CurrentActor currentActor;

    public CustomerController(CreateCustomerUseCase create, ManageCustomerUseCase manage, CustomerQueries queries, CurrentActor currentActor) {
        this.create = create;
        this.manage = manage;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    /** Safe to retry: the same Idempotency-Key returns the same customer (201 for new, 200 for replay); the same key with a different body is a 409. */
    @PostMapping
    @RepeatableCreate
    @RequiresPermission(Permission.CUSTOMER_WRITE)
    public ResponseEntity<CustomerResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CreateCustomerRequest request) {
        Actor actor = currentActor.get();
        TenantSystemId system = tenantSystemFor(actor, request.tenantSystemId());
        CreateCustomerUseCase.Result result = create.execute(new CreateCustomerUseCase.Command(
                system, request.externalUserId(), request.displayName(), request.email(), idempotencyKey, Audit.of(actor, requestId)));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(CustomerResponse.of(result.customer()));
    }

    @GetMapping
    @RequiresPermission(Permission.CUSTOMER_READ)
    public List<CustomerResponse> list() {
        return queries.list(currentActor.get().tenantSystemId()).stream().map(CustomerResponse::ofView).toList();
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
        return CustomerResponse.ofView(queries.get(CustomerId.of(id), currentActor.get().tenantSystemId()));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.CUSTOMER_WRITE)
    public CustomerResponse update(
            @PathVariable String id,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody UpdateCustomerRequest request) {
        Actor actor = currentActor.get();
        return CustomerResponse.of(manage.update(
                Audit.of(actor, requestId), CustomerId.of(id), request.displayName(), request.email(), actor.tenantSystemId()));
    }

    @PostMapping("/{id}/deactivate")
    @RequiresPermission(Permission.CUSTOMER_WRITE)
    public CustomerResponse deactivate(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Actor actor = currentActor.get();
        return CustomerResponse.of(manage.deactivate(Audit.of(actor, requestId), CustomerId.of(id), actor.tenantSystemId()));
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
}
