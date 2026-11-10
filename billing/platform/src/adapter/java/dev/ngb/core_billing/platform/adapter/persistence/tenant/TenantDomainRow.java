package dev.ngb.core_billing.platform.adapter.persistence.tenant;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("tenant_domains")
record TenantDomainRow(@Id UUID id, UUID tenantId, String hostname, String kind, Instant createdAt) {}
