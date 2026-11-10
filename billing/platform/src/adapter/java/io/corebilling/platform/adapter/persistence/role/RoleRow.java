package io.corebilling.platform.adapter.persistence.role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("roles")
record RoleRow(
        @Id UUID id,
        UUID tenantId,
        String scope,
        String name,
        @Column("is_system") boolean system,
        Instant createdAt,
        @MappedCollection(idColumn = "role_id") Set<RolePermissionRow> permissions) {

    @Table("role_permissions")
    record RolePermissionRow(UUID tenantId, String permissionCode) {}
}
