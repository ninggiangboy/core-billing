package dev.ngb.sampletenant.resource.adapter.order.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
record OrderRow(
        @Id UUID id,
        UUID customerId,
        String billingCustomerId,
        String walletId,
        UUID catalogPlanId,
        String billingPlanId,
        String pricingModel,
        String metric,
        String usageQuantity,
        String unit,
        Long eventCount,
        long estimatedMinorUnits,
        String currency,
        String reservationId,
        String subscriptionId,
        String status,
        String reference,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {}
