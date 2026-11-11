package dev.ngb.core_billing.platform.application.tenantsystem.query;

import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystemStatus;
import java.time.Instant;
import java.util.UUID;

/** A tenant system as the admin GET handlers show it. */
public record TenantSystemView(UUID id, String name, TenantSystemStatus status, Instant createdAt) {}
