package dev.ngb.core_billing.customer.domain.customer;

import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A Customer now exists. The payload is the integration contract; it carries no email or other personal data. */
public record CustomerCreated(UUID eventId, Customer customer, Instant occurredAt) implements DomainEvent {

    public static final String TYPE = "customer.created";

    @Override
    public TenantId tenantId() {
        return customer.tenantId();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregate() {
        return "customer/" + customer.id();
    }

    @Override
    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customer.id().toString());
        payload.put("displayName", customer.displayName());
        payload.put("identities", customer.identities().stream()
                .map(identity -> Map.<String, Object>of(
                        "tenantSystemId", identity.tenantSystemId().toString(), "externalUserId", identity.externalUserId()))
                .toList());
        return payload;
    }
}
