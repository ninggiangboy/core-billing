package io.corebilling.platform.adapter.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("billing_users")
record UserRow(
        @Id UUID id,
        UUID tenantId,
        String scope,
        String email,
        String displayName,
        String keycloakSubject,
        String status,
        Instant createdAt,
        @MappedCollection(idColumn = "user_id") Set<RoleAssignmentRow> roleAssignments,
        @MappedCollection(idColumn = "user_id") Set<UserPermissionRow> permissions) {

    @Table("role_assignments")
    record RoleAssignmentRow(UUID tenantId, UUID roleId) {}

    @Table("user_permissions")
    record UserPermissionRow(UUID tenantId, String permissionCode) {}
}
