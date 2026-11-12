package dev.ngb.billing.payment.adapter.incident.web;

import dev.ngb.billing.payment.application.incident.query.IncidentQueries;
import dev.ngb.billing.payment.application.incident.query.IncidentView;
import dev.ngb.billing.payment.application.stripe.command.ManageStripeEventUseCase;
import dev.ngb.billing.platform.api.actor.CurrentActor;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.platform.api.audit.Audit;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Stripe incidents: problems that need a person to look, and resolving them. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class IncidentController {

    public record IncidentResponse(
            String id, String kind, String subject, String detail, int occurrences, Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {
        static IncidentResponse ofView(IncidentView i) {
            return new IncidentResponse(i.id().toString(), i.kind().name(), i.subject(), i.detail(), i.occurrences(), i.createdAt(), i.lastSeenAt(),
                    i.resolvedAt());
        }
    }

    private final IncidentQueries incidentQueries;
    private final ManageStripeEventUseCase eventAdmin;
    private final CurrentActor currentActor;

    public IncidentController(IncidentQueries incidentQueries, ManageStripeEventUseCase eventAdmin, CurrentActor currentActor) {
        this.incidentQueries = incidentQueries;
        this.eventAdmin = eventAdmin;
        this.currentActor = currentActor;
    }

    @GetMapping("/stripe/incidents")
    @RequiresPermission(Permission.STRIPE_READ)
    public List<IncidentResponse> incidents(@RequestParam(defaultValue = "true") boolean open, @RequestParam(defaultValue = "50") int limit) {
        return incidentQueries.list(open, limit).stream().map(IncidentResponse::ofView).toList();
    }

    @PostMapping("/stripe/incidents/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.STRIPE_MANAGE)
    public void resolveIncident(@PathVariable String id, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        eventAdmin.resolveIncident(Audit.of(currentActor.get(), requestId), UUID.fromString(id));
    }
}
