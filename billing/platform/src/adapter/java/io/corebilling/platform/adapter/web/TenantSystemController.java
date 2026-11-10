package io.corebilling.platform.adapter.web;

import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.api.actor.RequiresPermission;
import io.corebilling.platform.api.tenant.TenantSystemId;
import io.corebilling.platform.application.credential.command.CredentialAdministration;
import io.corebilling.platform.application.tenantsystem.command.TenantSystemAdministration;
import io.corebilling.platform.domain.credential.ApiCredential;
import io.corebilling.platform.domain.credential.CredentialId;
import io.corebilling.platform.domain.tenantsystem.TenantSystem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant Systems of the current Tenant and the API credentials they authenticate with. */
@RestController
@RequestMapping("/api/v1")
public class TenantSystemController {

    public record CreateSystemRequest(String name) {}

    public record CreateCredentialRequest(String label, List<String> scopes) {}

    public record RotateRequest(Long overlapMinutes) {}

    public record SystemResponse(String id, String name, String status, Instant createdAt) {
        static SystemResponse of(TenantSystem system) {
            return new SystemResponse(system.id().toString(), system.name(), system.status().name(), system.createdAt());
        }
    }

    /** Credential metadata. It never contains the secret. */
    public record CredentialResponse(
            String id, String keyId, String label, Set<String> scopes, String status, Instant createdAt, Instant expiresAt, Instant revokedAt) {
        static CredentialResponse of(ApiCredential credential) {
            return new CredentialResponse(
                    credential.id().toString(), credential.keyId(), credential.label(),
                    credential.scopes().stream().map(Permission::code).collect(Collectors.toSet()),
                    credential.status().name(), credential.createdAt(), credential.expiresAt(), credential.revokedAt());
        }
    }

    /** Returned once, when a credential is created or rotated: the only place the key appears. */
    public record IssuedCredentialResponse(CredentialResponse credential, String key) {
        @Override
        public String toString() {
            return "IssuedCredentialResponse[credential=" + credential + "]";
        }
    }

    private static final long DEFAULT_OVERLAP_MINUTES = 24 * 60;

    private final TenantSystemAdministration systems;
    private final CredentialAdministration credentials;
    private final CallerResolver callers;

    public TenantSystemController(TenantSystemAdministration systems, CredentialAdministration credentials, CallerResolver callers) {
        this.systems = systems;
        this.credentials = credentials;
        this.callers = callers;
    }

    @PostMapping("/tenant-systems")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.TENANT_SYSTEM_MANAGE)
    public SystemResponse create(@RequestBody CreateSystemRequest request) {
        return SystemResponse.of(systems.create(callers.current(), request.name()));
    }

    @GetMapping("/tenant-systems")
    @RequiresPermission(Permission.TENANT_SYSTEM_READ)
    public List<SystemResponse> list() {
        return systems.list().stream().map(SystemResponse::of).toList();
    }

    @GetMapping("/tenant-systems/{id}")
    @RequiresPermission(Permission.TENANT_SYSTEM_READ)
    public SystemResponse get(@PathVariable String id) {
        return SystemResponse.of(systems.get(TenantSystemId.of(id)));
    }

    @PostMapping("/tenant-systems/{id}/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CREDENTIAL_MANAGE)
    public IssuedCredentialResponse createCredential(@PathVariable String id, @RequestBody CreateCredentialRequest request) {
        Set<Permission> scopes = request.scopes() == null
                ? Set.of()
                : request.scopes().stream()
                        .map(code -> Permission.fromCode(code).orElseThrow(() -> new IllegalArgumentException("unknown scope: " + code)))
                        .collect(Collectors.toSet());
        return issued(credentials.create(callers.current(), TenantSystemId.of(id), request.label(), scopes));
    }

    @GetMapping("/tenant-systems/{id}/credentials")
    @RequiresPermission(Permission.CREDENTIAL_MANAGE)
    public List<CredentialResponse> listCredentials(@PathVariable String id) {
        return credentials.list(TenantSystemId.of(id)).stream().map(CredentialResponse::of).toList();
    }

    @PostMapping("/tenant-systems/{id}/credentials/rotate")
    @RequiresPermission(Permission.CREDENTIAL_MANAGE)
    public IssuedCredentialResponse rotate(@PathVariable String id, @RequestBody(required = false) RotateRequest request) {
        long minutes = request == null || request.overlapMinutes() == null ? DEFAULT_OVERLAP_MINUTES : request.overlapMinutes();
        return issued(credentials.rotate(callers.current(), TenantSystemId.of(id), Duration.ofMinutes(minutes)));
    }

    @DeleteMapping("/tenant-systems/{systemId}/credentials/{credentialId}")
    @RequiresPermission(Permission.CREDENTIAL_MANAGE)
    public CredentialResponse revoke(@PathVariable String systemId, @PathVariable String credentialId) {
        TenantSystemId expected = TenantSystemId.of(systemId);
        boolean belongs = credentials.list(expected).stream().anyMatch(c -> c.id().equals(CredentialId.of(credentialId)));
        if (!belongs) {
            throw new NotFoundException("credential not found");
        }
        return CredentialResponse.of(credentials.revoke(callers.current(), CredentialId.of(credentialId)));
    }

    private static IssuedCredentialResponse issued(CredentialAdministration.Issued issued) {
        return new IssuedCredentialResponse(CredentialResponse.of(issued.credential()), issued.plaintextKey());
    }
}
