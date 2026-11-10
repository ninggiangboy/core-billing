package dev.ngb.core_billing.platform.adapter.persistence.tenantsystem;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("tenant_systems")
record TenantSystemRow(@Id UUID id, UUID tenantId, String name, String status, Instant createdAt) {}
