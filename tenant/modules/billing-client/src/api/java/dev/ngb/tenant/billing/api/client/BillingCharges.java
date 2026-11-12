package dev.ngb.tenant.billing.api.client;

import dev.ngb.tenant.billing.api.model.Charge;
import dev.ngb.tenant.billing.api.model.ChargeUsageRequest;
import dev.ngb.tenant.billing.api.model.Rating;
import dev.ngb.tenant.billing.api.model.Reservation;

/** Charges from a reservation, standalone charges and charging a period's usage. */
public interface BillingCharges {

    record ChargeRequest(String walletId, long amount, String currency, String usageRef, String reference) {}

    record CaptureRequest(long amount, String currency, String reference) {}

    Charge charge(String idempotencyKey, ChargeRequest request);

    Charge capture(String idempotencyKey, String reservationId, CaptureRequest request);

    Reservation release(String reservationId);

    Rating chargeUsage(ChargeUsageRequest request);

    Rating rating(String id);
}
