package io.corebilling.platform.adapter.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("tenant_systems")
record TenantSystemRow(@Id UUID id, UUID tenantId, String name, String status, Instant createdAt) {}
