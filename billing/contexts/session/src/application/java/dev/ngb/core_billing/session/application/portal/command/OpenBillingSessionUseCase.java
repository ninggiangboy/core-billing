package dev.ngb.core_billing.session.application.portal.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.session.api.portal.PortalPermission;
import dev.ngb.core_billing.session.application.portal.port.BillingSessionRepository;
import dev.ngb.core_billing.session.application.portal.port.ReturnUrlRepository;
import dev.ngb.core_billing.session.application.portal.service.ReturnUrlPolicy;
import dev.ngb.core_billing.session.application.portal.service.SessionSettings;
import dev.ngb.core_billing.session.application.portal.service.SessionTokens;
import dev.ngb.core_billing.session.domain.portal.BillingSession;
import dev.ngb.core_billing.session.domain.portal.BillingSessionId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Issues a Billing Session for one Customer to the Tenant System that asked. The exchange token is returned
 * once, in the portal URL; only its digest is stored.
 */
public class OpenBillingSessionUseCase {

    public record Command(
            CustomerId customerId, String returnUrl, Set<PortalPermission> permissions, Duration ttl, UUID tenantSystemId, Audit audit) {}

    public record Result(BillingSession session, String exchangeToken) {}

    private final UnitOfWork unitOfWork;
    private final BillingSessionRepository sessions;
    private final ReturnUrlRepository returnUrls;
    private final CustomerLookup customers;
    private final SessionTokens tokens;
    private final SessionSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public OpenBillingSessionUseCase(
            UnitOfWork unitOfWork, BillingSessionRepository sessions, ReturnUrlRepository returnUrls, CustomerLookup customers,
            SessionTokens tokens, SessionSettings settings, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.sessions = sessions;
        this.returnUrls = returnUrls;
        this.customers = customers;
        this.tokens = tokens;
        this.settings = settings;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command.tenantSystemId(), "tenantSystemId");
        Duration ttl = command.ttl() == null ? settings.defaultTtl() : command.ttl();
        if (ttl.compareTo(settings.minTtl()) < 0 || ttl.compareTo(settings.maxTtl()) > 0) {
            throw new IllegalArgumentException("the session lifetime must be between " + settings.minTtl() + " and " + settings.maxTtl());
        }
        Set<PortalPermission> requested = command.permissions() == null || command.permissions().isEmpty()
                ? PortalPermission.defaultReads()
                : Set.copyOf(command.permissions());
        java.util.EnumSet<PortalPermission> permissions = java.util.EnumSet.noneOf(PortalPermission.class);
        permissions.addAll(requested);
        // A session may always read its own metadata, whatever else it was narrowed to.
        permissions.add(PortalPermission.SESSION_VIEW);
        return unitOfWork.transactional(() -> {
            ReturnUrlPolicy.requireAllowed(command.returnUrl(), returnUrls.allowed());
            customers.findFor(command.customerId(), command.tenantSystemId())
                    .orElseThrow(() -> new NotFoundException("customer not found"));
            String exchangeToken = tokens.newToken();
            Instant now = clock.now();
            BillingSession session = BillingSession.create(
                    new BillingSessionId(ids.newId()),
                    tenantContext.requireTenant(),
                    command.customerId(),
                    new TenantSystemId(command.tenantSystemId()),
                    permissions,
                    command.returnUrl(),
                    tokens.hash(exchangeToken),
                    now,
                    now.plus(ttl));
            sessions.add(session);
            auditLog.record(AuditEntry.tenant(session.tenantId(), command.audit().actor(), "session.created",
                    "billing-session/" + session.id(), command.audit().requestId(), now));
            return new Result(session, exchangeToken);
        });
    }
}
