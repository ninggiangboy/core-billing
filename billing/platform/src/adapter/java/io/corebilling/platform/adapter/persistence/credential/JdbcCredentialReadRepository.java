package io.corebilling.platform.adapter.persistence.credential;

import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.application.credential.query.CredentialReadRepository;
import io.corebilling.platform.application.credential.query.CredentialView;
import io.corebilling.platform.domain.credential.CredentialStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/** Selects credential metadata straight into views; the secret hash is never selected. */
public class JdbcCredentialReadRepository implements CredentialReadRepository {

    private record Row(
            UUID id,
            UUID tenantSystemId,
            String keyId,
            String scopesCsv,
            String label,
            String status,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt) {

        CredentialView toView() {
            Set<Permission> scopes = scopesCsv == null || scopesCsv.isEmpty()
                    ? Set.of()
                    : Arrays.stream(scopesCsv.split(",")).map(code -> Permission.fromCode(code).orElseThrow()).collect(Collectors.toSet());
            return new CredentialView(
                    id, tenantSystemId, keyId, label, scopes, CredentialStatus.valueOf(status), createdAt, expiresAt, revokedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcCredentialReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CredentialView> findByTenantSystem(UUID tenantSystemId) {
        return jdbc.sql("select id, tenant_system_id, key_id, array_to_string(scopes, ',') as scopes_csv, label, status, created_at, expires_at, revoked_at "
                        + "from api_credentials where tenant_system_id = ? order by created_at, id")
                .param(tenantSystemId)
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }
}
