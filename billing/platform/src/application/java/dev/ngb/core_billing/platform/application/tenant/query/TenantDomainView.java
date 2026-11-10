package dev.ngb.core_billing.platform.application.tenant.query;

import dev.ngb.core_billing.platform.domain.tenant.DomainKind;
import java.time.Instant;
import java.util.UUID;

/** One hostname mapped to a tenant. */
public record TenantDomainView(UUID id, UUID tenantId, String hostname, DomainKind kind, Instant createdAt) {}
