package dev.ngb.billing.customer.application.customer.query;

import java.util.UUID;

/** One Tenant System account mapped to a customer. */
public record CustomerIdentityView(UUID tenantSystemId, String externalUserId) {}
