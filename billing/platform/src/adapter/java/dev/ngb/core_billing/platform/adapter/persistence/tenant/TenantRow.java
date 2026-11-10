package dev.ngb.core_billing.platform.adapter.persistence.tenant;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("tenants")
record TenantRow(@Id UUID id, String code, String name, String status, Instant createdAt) {}
