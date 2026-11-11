package dev.ngb.core_billing.boot.portal;

import dev.ngb.core_billing.session.api.portal.PortalPermission;
import dev.ngb.core_billing.session.api.portal.RequiresPortalPermission;
import dev.ngb.core_billing.usage.api.lookup.UsageTotal;
import dev.ngb.core_billing.usage.application.usage.query.UsageEventView;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Usage reported for the session's customer: event history and per-metric totals. */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/portal/api/usage")
public class PortalUsageController {

    public record UsageEventResponse(String id, String metric, String quantity, String unit, Instant occurredAt, String resourceRef) {
        static UsageEventResponse of(UsageEventView event) {
            return new UsageEventResponse(event.id().toString(), event.metric(), event.quantity().toPlainString(), event.unit(), event.occurredAt(),
                    event.resourceRef());
        }
    }

    public record UsageTotalResponse(String metric, String unit, String quantity, long events) {
        static UsageTotalResponse of(UsageTotal total) {
            return new UsageTotalResponse(total.metric(), total.unit(), total.quantity().stripTrailingZeros().toPlainString(), total.events());
        }
    }

    private final PortalReadModel portal;

    public PortalUsageController(PortalReadModel portal) {
        this.portal = portal;
    }

    @GetMapping("/history")
    @RequiresPortalPermission(PortalPermission.USAGE_VIEW)
    public List<UsageEventResponse> history(
            @RequestParam(required = false) String metric, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return portal.usage(metric, from, to, before, limit).stream().map(UsageEventResponse::of).toList();
    }

    @GetMapping("/totals")
    @RequiresPortalPermission(PortalPermission.USAGE_VIEW)
    public List<UsageTotalResponse> totals(
            @RequestParam(required = false) String metric, @RequestParam Instant from, @RequestParam Instant to) {
        return portal.usageTotals(metric, from, to).stream().map(UsageTotalResponse::of).toList();
    }
}
