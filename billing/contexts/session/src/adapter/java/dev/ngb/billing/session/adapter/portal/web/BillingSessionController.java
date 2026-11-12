package dev.ngb.billing.session.adapter.portal.web;

import dev.ngb.billing.platform.api.actor.Actor;
import dev.ngb.billing.platform.api.actor.CurrentActor;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.platform.api.error.AccessDeniedException;
import dev.ngb.billing.session.api.portal.PortalPermission;
import dev.ngb.billing.session.application.portal.command.OpenBillingSessionUseCase;
import dev.ngb.billing.session.application.portal.command.RevokeBillingSessionUseCase;
import dev.ngb.billing.session.domain.portal.BillingSession;
import dev.ngb.billing.session.domain.portal.BillingSessionId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Tenant System side of a Billing Session: create one for a mapped customer, and revoke a session it issued.
 * The exchange token is returned once, inside the portal URL.
 */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/api/v1/billing-sessions")
public class BillingSessionController {

    public record CreateRequest(String customerId, String returnUrl, List<String> permissions, Long expiresInSeconds) {}

    public record CreateResponse(String id, String customerId, String portalUrl, List<String> permissions, Instant expiresAt) {}

    private final OpenBillingSessionUseCase open;
    private final RevokeBillingSessionUseCase revoke;
    private final CurrentActor currentActor;

    public BillingSessionController(OpenBillingSessionUseCase open, RevokeBillingSessionUseCase revoke, CurrentActor currentActor) {
        this.open = open;
        this.revoke = revoke;
        this.currentActor = currentActor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.BILLING_SESSION_CREATE)
    public CreateResponse create(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CreateRequest request,
            HttpServletRequest http) {
        if (request.customerId() == null || request.returnUrl() == null) {
            throw new IllegalArgumentException("customerId and returnUrl are required");
        }
        Actor actor = currentActor.get();
        String tenantSystemId = actor.tenantSystem().map(Object::toString)
                .orElseThrow(() -> new AccessDeniedException("only a tenant system can create a billing session"));
        Set<PortalPermission> permissions = request.permissions() == null
                ? null
                : PortalPermission.fromCodes(Set.copyOf(request.permissions()));
        Duration ttl = request.expiresInSeconds() == null ? null : Duration.ofSeconds(request.expiresInSeconds());
        OpenBillingSessionUseCase.Result result = open.execute(new OpenBillingSessionUseCase.Command(
                CustomerId.of(request.customerId()), request.returnUrl(), permissions, ttl, java.util.UUID.fromString(tenantSystemId),
                Audit.of(actor, requestId)));
        BillingSession session = result.session();
        return new CreateResponse(
                session.id().toString(),
                session.customerId().toString(),
                portalUrl(http, result.exchangeToken()),
                session.permissions().stream().map(PortalPermission::code).sorted().toList(),
                session.expiresAt());
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission(Permission.BILLING_SESSION_CREATE)
    public ResponseEntity<Void> revoke(
            @PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Actor actor = currentActor.get();
        java.util.UUID tenantSystemId = actor.tenantSystemId() == null ? null : actor.tenantSystemId().value();
        revoke.execute(BillingSessionId.of(id), tenantSystemId, Audit.of(actor, requestId));
        return ResponseEntity.noContent().build();
    }

    /** The portal URL lives on the hostname the request arrived on, which is the tenant's own hostname. */
    private static String portalUrl(HttpServletRequest request, String token) {
        StringBuilder origin = new StringBuilder(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort) {
            origin.append(':').append(port);
        }
        return origin.append("/portal/session#token=").append(token).toString();
    }
}
