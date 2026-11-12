package dev.ngb.tenant.usage.adapter.usage.web;

import dev.ngb.tenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.tenant.identity.api.security.AuthenticatedUser;
import dev.ngb.tenant.identity.api.security.Permission;
import dev.ngb.tenant.identity.api.security.RequiresPermission;
import dev.ngb.tenant.usage.application.usage.query.UsageQueries;
import dev.ngb.tenant.usage.application.usage.query.UsageRecordView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The usage this tenant reported for the current customer. */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    public record UsageResponse(
            UUID id,
            UUID orderId,
            String metric,
            String quantity,
            String unit,
            String billingEventId,
            Instant occurredAt,
            Instant recordedAt) {
        static UsageResponse of(UsageRecordView record) {
            return new UsageResponse(
                    record.id(),
                    record.orderId(),
                    record.metric(),
                    record.quantity(),
                    record.unit(),
                    record.billingEventId(),
                    record.occurredAt(),
                    record.recordedAt());
        }
    }

    private final UsageQueries queries;
    private final CustomerDirectory customers;

    public UsageController(UsageQueries queries, CustomerDirectory customers) {
        this.queries = queries;
        this.customers = customers;
    }

    @GetMapping
    @RequiresPermission(Permission.USAGE_READ)
    public List<UsageResponse> usage(@AuthenticationPrincipal AuthenticatedUser principal) {
        String billingCustomerId = customers.byUserId(principal.id()).billingCustomerId();
        if (billingCustomerId == null) {
            return List.of();
        }
        return queries.forBillingCustomer(billingCustomerId).stream().map(UsageResponse::of).toList();
    }
}
