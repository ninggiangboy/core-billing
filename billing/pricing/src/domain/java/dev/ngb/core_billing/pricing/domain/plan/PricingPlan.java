package dev.ngb.core_billing.pricing.domain.plan;

import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import dev.ngb.core_billing.pricing.domain.model.PriceRule;
import dev.ngb.core_billing.pricing.domain.model.PricingModel;

/**
 * A named price list for one pricing model and one currency. Its terms live in immutable {@link PlanVersion}s;
 * publishing changes the plan by adding the next version, never by editing one. A retired plan takes no new
 * versions and is no longer offered, but its versions stay readable because charges refer to them.
 */
public final class PricingPlan {

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private final PlanId id;
    private final TenantId tenantId;
    private final String code;
    private final String name;
    private final PricingModel model;
    private final Currency currency;
    private final Instant createdAt;
    private PlanStatus status;
    private Instant retiredAt;
    private final List<PlanVersion> versions;

    private PricingPlan(
            PlanId id, TenantId tenantId, String code, String name, PricingModel model, Currency currency, PlanStatus status, Instant createdAt,
            Instant retiredAt, List<PlanVersion> versions) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.model = Objects.requireNonNull(model, "model");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.retiredAt = retiredAt;
        this.versions = new ArrayList<>(versions);
    }

    public static PricingPlan create(PlanId id, TenantId tenantId, String code, String name, PricingModel model, Currency currency, Instant now) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("a plan code is 1-64 characters: a lowercase letter or digit, then lowercase letters, digits, '.', '_' or '-'");
        }
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("a plan name is 1-200 characters");
        }
        return new PricingPlan(id, tenantId, code, name.strip(), model, currency, PlanStatus.ACTIVE, now, null, List.of());
    }

    public static PricingPlan restore(
            PlanId id, TenantId tenantId, String code, String name, PricingModel model, Currency currency, PlanStatus status, Instant createdAt,
            Instant retiredAt, List<PlanVersion> versions) {
        return new PricingPlan(id, tenantId, code, name, model, currency, status, createdAt, retiredAt, versions);
    }

    /**
     * Adds the next version. It starts at {@code validFrom} (now when null) and may not start before the previous
     * version did, so the timeline of versions only moves forward.
     */
    public PlanVersion publish(
            PriceRule rule, BillingCycle cycle, Long minCharge, Long maxCharge, RoundingPolicy rounding, Instant validFrom, Instant validUntil,
            String actor, Instant now) {
        if (status == PlanStatus.RETIRED) {
            throw new IllegalStateException("the plan is retired and takes no new versions");
        }
        if (rule.model() != model) {
            throw new IllegalArgumentException("this plan is " + model + "; a " + rule.model() + " rule does not fit");
        }
        Instant start = validFrom == null ? now : validFrom;
        if (!versions.isEmpty() && start.isBefore(latest().validFrom())) {
            throw new IllegalArgumentException("a new version cannot start before the previous one (" + latest().validFrom() + ")");
        }
        PlanVersion version = new PlanVersion(id, tenantId, versions.size() + 1, rule, cycle, minCharge, maxCharge,
                rounding == null ? RoundingPolicy.HALF_UP : rounding, start, validUntil, now, actor);
        versions.add(version);
        return version;
    }

    public void retire(Instant now) {
        if (status == PlanStatus.RETIRED) {
            throw new IllegalStateException("the plan is already retired");
        }
        this.status = PlanStatus.RETIRED;
        this.retiredAt = now;
    }

    /** The version that applies at {@code at}: the newest one that has started and has not ended. */
    public Optional<PlanVersion> effectiveAt(Instant at) {
        PlanVersion found = null;
        for (PlanVersion version : versions) {
            if (version.isValidAt(at) && (found == null || version.number() > found.number())) {
                found = version;
            }
        }
        return Optional.ofNullable(found);
    }

    public Optional<PlanVersion> version(int number) {
        return versions.stream().filter(v -> v.number() == number).findFirst();
    }

    public PlanVersion latest() {
        return versions.get(versions.size() - 1);
    }

    public PlanId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public PricingModel model() {
        return model;
    }

    public Currency currency() {
        return currency;
    }

    public PlanStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant retiredAt() {
        return retiredAt;
    }

    public List<PlanVersion> versions() {
        return List.copyOf(versions);
    }
}
