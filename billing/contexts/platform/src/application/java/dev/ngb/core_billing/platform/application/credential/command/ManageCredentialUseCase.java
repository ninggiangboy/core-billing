package dev.ngb.core_billing.platform.application.credential.command;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.domain.credential.ApiCredential;
import dev.ngb.core_billing.platform.domain.credential.ApiKey;
import dev.ngb.core_billing.platform.domain.credential.ApiCredentialId;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;

/**
 * Issues, rotates and revokes API credentials. The secret exists in plaintext only in the value returned
 * by {@link #create} and {@link #rotate}; storage keeps a digest and nothing here logs it.
 */
public class ManageCredentialUseCase {

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

    public ManageCredentialUseCase(
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

    public Issued create(Audit caller, TenantSystemId systemId, String label, Set<Permission> scopes) {
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
    public Issued rotate(Audit caller, TenantSystemId systemId, Duration overlap) {
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

    public ApiCredential revoke(Audit caller, ApiCredentialId id) {
        return unitOfWork.transactional(() -> {
            ApiCredential credential = credentials.findById(id).orElseThrow(() -> new NotFoundException("credential not found"));
            credential.revoke(clock.now());
            credentials.update(credential);
            audit(caller, credential.tenantId(), "credential.revoked", credential);
            return credential;
        });
    }

    private Issued issue(TenantId tenantId, TenantSystemId systemId, String label, Set<Permission> scopes) {
        ApiKey.Generated key = ApiKey.generate(random);
        ApiCredential credential = ApiCredential.issue(
                new ApiCredentialId(ids.newId()), tenantId, systemId, key, scopes, label == null ? "" : label, clock.now());
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

    private void audit(Audit caller, TenantId tenantId, String action, ApiCredential credential) {
        auditLog.record(AuditEntry.tenant(
                tenantId,
                caller.actor(),
                action,
                "tenant-system/" + credential.tenantSystemId() + "/credential/" + credential.id(),
                caller.requestId(),
                clock.now()));
    }
}
