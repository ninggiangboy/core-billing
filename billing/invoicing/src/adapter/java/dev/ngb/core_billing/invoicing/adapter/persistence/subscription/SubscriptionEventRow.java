package dev.ngb.core_billing.invoicing.adapter.persistence.subscription;

import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("subscription_events")
record SubscriptionEventRow(
        @Id UUID id,
        UUID tenantId,
        UUID subscriptionId,
        String type,
        Integer periodNumber,
        Instant periodStart,
        Instant periodEnd,
        Long amount,
        UUID chargeId,
        String detail,
        Instant occurredAt) {

    static SubscriptionEventRow from(SubscriptionEvent e) {
        return new SubscriptionEventRow(e.id(), e.tenantId().value(), e.subscriptionId().value(), e.type().name(), e.periodNumber(), e.periodStart(), e.periodEnd(),
                e.amount() == null ? null : e.amount().minorUnits(), e.chargeId(), e.detail(), e.occurredAt());
    }
}
