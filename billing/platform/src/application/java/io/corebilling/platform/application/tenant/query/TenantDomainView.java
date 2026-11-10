package io.corebilling.platform.application.tenant.query;

import io.corebilling.platform.domain.tenant.DomainKind;
import java.time.Instant;
import java.util.UUID;

/** One hostname mapped to a tenant. */
public record TenantDomainView(UUID id, UUID tenantId, String hostname, DomainKind kind, Instant createdAt) {}
