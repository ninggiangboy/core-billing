package dev.ngb.billing.boot.portal;

import dev.ngb.billing.customer.application.customer.query.CustomerView;
import dev.ngb.billing.session.api.portal.PortalPermission;
import dev.ngb.billing.session.api.portal.RequiresPortalPermission;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer the session was issued for, and nothing else. */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/portal/api/customer")
public class PortalCustomerController {

    public record CustomerResponse(String id, String displayName, String email, String status, Instant createdAt) {
        static CustomerResponse of(CustomerView customer) {
            return new CustomerResponse(customer.id().toString(), customer.displayName(), customer.email(), customer.status().name(), customer.createdAt());
        }
    }

    private final PortalReadModel portal;

    public PortalCustomerController(PortalReadModel portal) {
        this.portal = portal;
    }

    @GetMapping
    @RequiresPortalPermission(PortalPermission.CUSTOMER_VIEW)
    public CustomerResponse customer() {
        return CustomerResponse.of(portal.customer());
    }
}
