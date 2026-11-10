package io.corebilling.pricing.domain.plan;

import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.corebilling.pricing.domain.metric.UsageSummary;
import io.corebilling.pricing.domain.rating.Quote;

/**
 * One immutable version of a plan: the rule, the cycle, the charge limits, the rounding and the period it is valid.
 * A charge records the version it used, and a version is never edited, so what a customer was charged can always
 * be explained from the data that was in force at the time.
 */
public final class PlanVersion {

    private final PlanId planId;
    private final TenantId tenantId;
    private final int number;
    private final PriceRule rule;
    private final BillingCycle billingCycle;
    private final Long minCharge;
    private final Long maxCharge;
    private final RoundingPolicy rounding;
    private final Instant validFrom;
    private final Instant validUntil;
    private final Instant createdAt;
    private final String createdBy;

    public PlanVersion(
            PlanId planId, TenantId tenantId, int number, PriceRule rule, BillingCycle billingCycle, Long minCharge, Long maxCharge, RoundingPolicy rounding,
            Instant validFrom, Instant validUntil, Instant createdAt, String createdBy) {
        this.planId = Objects.requireNonNull(planId, "planId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.number = number;
        this.rule = Objects.requireNonNull(rule, "rule");
        this.billingCycle = billingCycle;
        this.minCharge = minCharge;
        this.maxCharge = maxCharge;
        this.rounding = Objects.requireNonNull(rounding, "rounding");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        this.validUntil = validUntil;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        if (number < 1) {
            throw new IllegalArgumentException("a version number starts at 1");
        }
        if (minCharge != null && minCharge < 0 || maxCharge != null && maxCharge < 0) {
            throw new IllegalArgumentException("minimum and maximum charge must not be negative");
        }
        if (minCharge != null && maxCharge != null && maxCharge < minCharge) {
            throw new IllegalArgumentException("the maximum charge must not be below the minimum charge");
        }
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        boolean fixedPart = rule.model() == PricingModel.SUBSCRIPTION || rule.model() == PricingModel.HYBRID;
        if (fixedPart && billingCycle == null) {
            throw new IllegalArgumentException("a " + rule.model() + " plan needs a billing cycle");
        }
    }

    /** Prices {@code usage} under this version in {@code currency}: exact lines, one rounding, then the limits. */
    public Quote quote(Currency currency, UsageSummary usage) {
        List<PriceLine> lines = new ArrayList<>(rule.price(usage));
        BigDecimal exact = BigDecimal.ZERO;
        for (PriceLine line : lines) {
            exact = exact.add(line.amount());
        }
        long amount = rounding.toMinorUnits(exact);
        Quote.Adjustment adjustment = Quote.Adjustment.NONE;
        if (minCharge != null && amount < minCharge) {
            amount = minCharge;
            adjustment = Quote.Adjustment.MINIMUM_CHARGE;
        }
        if (maxCharge != null && amount > maxCharge) {
            amount = maxCharge;
            adjustment = Quote.Adjustment.MAXIMUM_CHARGE;
        }
        return new Quote(Money.of(amount, currency), exact, lines, adjustment);
    }

    public boolean isValidAt(Instant at) {
        return !at.isBefore(validFrom) && (validUntil == null || at.isBefore(validUntil));
    }

    public PlanId planId() {
        return planId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public int number() {
        return number;
    }

    public PriceRule rule() {
        return rule;
    }

    public BillingCycle billingCycle() {
        return billingCycle;
    }

    public Long minCharge() {
        return minCharge;
    }

    public Long maxCharge() {
        return maxCharge;
    }

    public RoundingPolicy rounding() {
        return rounding;
    }

    public Instant validFrom() {
        return validFrom;
    }

    public Instant validUntil() {
        return validUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }
}
