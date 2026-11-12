package dev.ngb.tenant.customer.adapter.customer.web;

import dev.ngb.tenant.customer.application.customer.command.RegisterCustomerUseCase;
import dev.ngb.tenant.customer.application.customer.query.CustomerQueries;
import dev.ngb.tenant.customer.application.customer.query.CustomerView;
import dev.ngb.tenant.customer.domain.customer.Customer;
import dev.ngb.tenant.identity.api.security.AuthenticatedUser;
import dev.ngb.tenant.identity.api.security.Permission;
import dev.ngb.tenant.identity.api.security.RequiresPermission;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The local view of the Billing Customer mapping. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    public record CustomerResponse(
            UUID id,
            UUID userId,
            String billingCustomerId,
            String displayName,
            String email,
            String status,
            Instant createdAt) {
        static CustomerResponse of(CustomerView customer) {
            return new CustomerResponse(
                    customer.id(),
                    customer.userId(),
                    customer.billingCustomerId(),
                    customer.displayName(),
                    customer.email(),
                    customer.status().name(),
                    customer.createdAt());
        }

        static CustomerResponse of(Customer customer) {
            return new CustomerResponse(
                    customer.id(),
                    customer.userId(),
                    customer.billingCustomerId(),
                    customer.displayName(),
                    customer.email(),
                    customer.status().name(),
                    customer.createdAt());
        }
    }

    private final CustomerQueries queries;
    private final RegisterCustomerUseCase register;

    public CustomerController(CustomerQueries queries, RegisterCustomerUseCase register) {
        this.queries = queries;
        this.register = register;
    }

    @GetMapping("/me")
    public CustomerResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return CustomerResponse.of(queries.byUser(principal.id()));
    }

    @GetMapping
    @RequiresPermission(Permission.USER_MANAGE)
    public List<CustomerResponse> list() {
        return queries.list().stream().map(CustomerResponse::of).toList();
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.CUSTOMER_MANAGE)
    public CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.of(queries.byId(id));
    }

    @PostMapping("/{id}/mapping")
    @RequiresPermission(Permission.CUSTOMER_MANAGE)
    public CustomerResponse retryMapping(@PathVariable UUID id) {
        return CustomerResponse.of(register.retry(id));
    }
}
