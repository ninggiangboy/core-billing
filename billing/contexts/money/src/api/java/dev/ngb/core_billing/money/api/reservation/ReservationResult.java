package dev.ngb.core_billing.money.api.reservation;

import dev.ngb.core_billing.sharedkernel.money.Money;
import java.time.Instant;

/** {@code replayed} is true when the idempotency key was already used and the original reservation is returned. */
public record ReservationResult(ReservationId id, ReservationStatus status, Money amount, Instant expiresAt, boolean replayed) {}
