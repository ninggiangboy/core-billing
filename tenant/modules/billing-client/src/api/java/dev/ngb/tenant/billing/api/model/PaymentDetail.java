package dev.ngb.tenant.billing.api.model;

import java.util.List;

/** A payment with its refunds and failed attempts. */
public record PaymentDetail(Payment payment, List<Refund> refunds, List<Attempt> failedAttempts) {}
