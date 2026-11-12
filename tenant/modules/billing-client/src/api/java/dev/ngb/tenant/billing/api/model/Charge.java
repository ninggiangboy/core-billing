package dev.ngb.tenant.billing.api.model;

/** A recorded charge. A wallet that cannot pay it returns status FAILED with a reason. */
public record Charge(
        String id,
        String status,
        String failureReason,
        Amount amount,
        String ledgerEntryId,
        String reservationId,
        String usageRef,
        String reference) {}
