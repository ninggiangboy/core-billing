package io.corebilling.platform.application;

import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.platform.domain.ApiCredential;
import io.corebilling.platform.domain.ApiKey;
import io.corebilling.platform.domain.CredentialId;
import io.corebilling.platform.domain.TenantSystem;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Issues, rotates and revokes API credentials. The secret exists in plaintext only in the value returned
 * by {@link #create} and {@link #rotate}; storage keeps a digest and nothing here logs it.
 */
public class CredentialAdministration {

    public static final Duration MIN_OVERLAP = Duration.ofMinutes(1);
    public static final Duration MAX_OVERLAP = Duration.ofDays(7);

    /** The stored credential plus the one-time plaintext key. */
    public record Issued(ApiCredential credential, String plaintextKey) {
        @Override
        public String toString() {
            return "Issued[credential=" + credential + "]";
        }
    }

    private final UnitOfWork unitOfWork;
    private final CredentialRepository credentials;
    private final TenantSystemRepository systems;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;
    private final SecureRandom random;

    public CredentialAdministration(
            UnitOfWork unitOfWork,
            CredentialRepository credentials,
            TenantSystemRepository systems,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock,
            SecureRandom random) {
        this.unitOfWork = unitOfWork;
        this.credentials = credentials;
        this.systems = systems;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
        this.random = random;
    }

    public Issued create(Caller caller, TenantSystemId systemId, String label, Set<Permission> scopes) {
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            requireActiveSystem(systemId);
            Issued issued = issue(tenantId, systemId, label, scopes);
            audit(caller, tenantId, "credential.created", issued.credential());
            return issued;
        });
    }

    /**
     * Issues a replacement and lets the current credential live on for {@code overlap}, so the Tenant
     * System can switch without downtime. At most two credentials may be live at once.
     */
    public Issued rotate(Caller caller, TenantSystemId systemId, Duration overlap) {
        if (overlap.compareTo(MIN_OVERLAP) < 0 || overlap.compareTo(MAX_OVERLAP) > 0) {
            throw new IllegalArgumentException("overlap must be between 1 minute and 7 days");
        }
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            requireActiveSystem(systemId);
            Instant now = clock.now();
            List<ApiCredential> live = credentials.findByTenantSystem(systemId).stream()
                    .filter(credential -> credential.isUsable(now))
                    .toList();
            if (live.isEmpty()) {
                throw new ConflictException("there is no active credential to rotate");
            }
            if (live.size() > 1) {
                throw new ConflictException("a rotation is already in its overlap window");
            }
            ApiCredential current = live.getFirst();
            Issued issued = issue(tenantId, systemId, current.label(), current.scopes());
            current.expireAt(now.plus(overlap));
            credentials.update(current);
            audit(caller, tenantId, "credential.rotated", issued.credential());
            return issued;
        });
    }

    public ApiCredential revoke(Caller caller, CredentialId id) {
        return unitOfWork.transactional(() -> {
            ApiCredential credential = credentials.findById(id).orElseThrow(() -> new NotFoundException("credential not found"));
            credential.revoke(clock.now());
            credentials.update(credential);
            audit(caller, credential.tenantId(), "credential.revoked", credential);
            return credential;
        });
    }

    public List<ApiCredential> list(TenantSystemId systemId) {
        return unitOfWork.transactional(() -> {
            requireActiveSystemOrDisabled(systemId);
            return credentials.findByTenantSystem(systemId);
        });
    }

    private Issued issue(TenantId tenantId, TenantSystemId systemId, String label, Set<Permission> scopes) {
        ApiKey.Generated key = ApiKey.generate(random);
        ApiCredential credential = ApiCredential.issue(
                new CredentialId(ids.newId()), tenantId, systemId, key, scopes, label == null ? "" : label, clock.now());
        credentials.add(credential);
        return new Issued(credential, key.plaintext());
    }

    private TenantSystem requireActiveSystem(TenantSystemId systemId) {
        TenantSystem system = systems.findById(systemId).orElseThrow(() -> new NotFoundException("tenant system not found"));
        if (!system.isActive()) {
            throw new ConflictException("the tenant system is disabled");
        }
        return system;
    }

    private void requireActiveSystemOrDisabled(TenantSystemId systemId) {
        systems.findById(systemId).orElseThrow(() -> new NotFoundException("tenant system not found"));
    }

    private void audit(Caller caller, TenantId tenantId, String action, ApiCredential credential) {
        auditLog.record(AuditEntry.tenant(
                tenantId,
                caller.actor(),
                action,
                "tenant-system/" + credential.tenantSystemId() + "/credential/" + credential.id(),
                caller.requestId(),
                clock.now()));
    }
}
