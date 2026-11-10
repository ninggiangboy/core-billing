package dev.ngb.core_billing.customer.adapter.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("customers")
record CustomerRow(
        @Id UUID id,
        UUID tenantId,
        String displayName,
        String email,
        String status,
        Instant createdAt,
        @MappedCollection(idColumn = "customer_id") Set<MappingRow> mappings) {

    @Table("customer_mappings")
    record MappingRow(UUID tenantId, UUID tenantSystemId, String externalUserId) {}
}
