package io.corebilling.payment.adapter.persistence.stripe;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("stripe_event_attempts")
record EventAttemptRow(
        @Id UUID id,
        UUID tenantId,
        UUID eventRowId,
        int attemptNo,
        String outcome,
        String detail,
        Instant attemptedAt) {}
