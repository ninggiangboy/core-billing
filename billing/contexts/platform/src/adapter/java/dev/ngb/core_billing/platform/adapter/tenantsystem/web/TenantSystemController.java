package dev.ngb.core_billing.platform.adapter.tenantsystem.web;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.credential.command.ManageCredentialUseCase;
import dev.ngb.core_billing.platform.application.credential.query.CredentialQueries;
import dev.ngb.core_billing.platform.application.credential.query.CredentialView;
import dev.ngb.core_billing.platform.application.tenantsystem.command.CreateTenantSystemUseCase;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemQueries;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemView;
import dev.ngb.core_billing.platform.domain.credential.ApiCredential;
import dev.ngb.core_billing.platform.domain.credential.ApiCredentialId;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
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
import dev.ngb.core_billing.platform.adapter.auth.web.CallerResolver;

/** Tenant Systems of the current Tenant and the API credentials they authenticate with. */
@Profile("!worker & !migrate")
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

        static SystemResponse ofView(TenantSystemView system) {
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

        static CredentialResponse ofView(CredentialView credential) {
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

    private final CreateTenantSystemUseCase systems;
    private final TenantSystemQueries systemQueries;
    private final ManageCredentialUseCase credentials;
    private final CredentialQueries credentialQueries;
    private final CallerResolver callers;

    public TenantSystemController(CreateTenantSystemUseCase systems, TenantSystemQueries systemQueries, ManageCredentialUseCase credentials, CredentialQueries credentialQueries, CallerResolver callers) {
        this.systems = systems;
        this.systemQueries = systemQueries;
        this.credentials = credentials;
        this.credentialQueries = credentialQueries;
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
        return systemQueries.list().stream().map(SystemResponse::ofView).toList();
    }

    @GetMapping("/tenant-systems/{id}")
    @RequiresPermission(Permission.TENANT_SYSTEM_READ)
    public SystemResponse get(@PathVariable String id) {
        return SystemResponse.ofView(systemQueries.get(TenantSystemId.of(id).value()));
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
        return credentialQueries.list(TenantSystemId.of(id)).stream().map(CredentialResponse::ofView).toList();
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
        boolean belongs = credentialQueries.list(expected).stream().anyMatch(c -> c.id().equals(ApiCredentialId.of(credentialId).value()));
        if (!belongs) {
            throw new NotFoundException("credential not found");
        }
        return CredentialResponse.of(credentials.revoke(callers.current(), ApiCredentialId.of(credentialId)));
    }

    private static IssuedCredentialResponse issued(ManageCredentialUseCase.Issued issued) {
        return new IssuedCredentialResponse(CredentialResponse.of(issued.credential()), issued.plaintextKey());
    }
}
