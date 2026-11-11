package dev.ngb.core_billing.pricing.application.plan.command;

import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.pricing.domain.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.model.PriceRule;
import dev.ngb.core_billing.pricing.domain.plan.PricingEvents;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Map;
import dev.ngb.core_billing.pricing.application.metric.port.MetricRegistry;
import dev.ngb.core_billing.pricing.application.plan.port.PlanRepository;
import dev.ngb.core_billing.platform.api.audit.Audit;

/**
 * Creates, versions, retires and reads plans. A plan and its versions commit together with the event that
 * announces them. Creating a plan fixes the unit of each metric it prices; a later plan that measures the same
 * metric in another unit is refused, because usage is validated against that one unit.
 */
public class PlanAdministration {

    /** The terms of one version, as a caller states them. */
    public record Terms(PriceRule rule, BillingCycle billingCycle, Long minCharge, Long maxCharge, RoundingPolicy rounding, Instant validFrom, Instant validUntil) {}

    private final UnitOfWork unitOfWork;
    private final PlanRepository plans;
    private final MetricRegistry metrics;
    private final EventPublisher events;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public PlanAdministration(
            UnitOfWork unitOfWork, PlanRepository plans, MetricRegistry metrics, EventPublisher events, AuditLog auditLog, TenantContext tenantContext,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.plans = plans;
        this.metrics = metrics;
        this.events = events;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public PricingPlan create(Audit audit, String code, String name, Currency currency, Terms firstVersion) {
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            Instant now = clock.now();
            if (plans.findByCode(code).isPresent()) {
                throw new ConflictException("a plan with code '" + code + "' already exists");
            }
            PricingPlan plan = PricingPlan.create(new PlanId(ids.newId()), tenantId, code, name, firstVersion.rule().model(), currency, now);
            PlanVersion version = publish(plan, firstVersion, audit, now);
            plans.add(plan);
            announce(plan, version, audit, now, "pricing-plan.created");
            return plan;
        });
    }

    public PlanVersion publishVersion(Audit audit, PlanId id, Terms terms) {
        return unitOfWork.transactional(() -> {
            Instant now = clock.now();
            PricingPlan plan = plans.lockById(id).orElseThrow(() -> new NotFoundException("pricing plan not found"));
            PlanVersion version;
            try {
                version = publish(plan, terms, audit, now);
            } catch (IllegalStateException retired) {
                throw new ConflictException(retired.getMessage());
            }
            plans.addVersion(version);
            announce(plan, version, audit, now, "pricing-plan.version-published");
            return version;
        });
    }

    public PricingPlan retire(Audit audit, PlanId id) {
        return unitOfWork.transactional(() -> {
            Instant now = clock.now();
            PricingPlan plan = plans.lockById(id).orElseThrow(() -> new NotFoundException("pricing plan not found"));
            try {
                plan.retire(now);
            } catch (IllegalStateException already) {
                throw new ConflictException(already.getMessage());
            }
            plans.update(plan);
            events.publish(new PricingEvents.PlanRetired(ids.newId(), plan, now));
            auditLog.record(AuditEntry.tenant(plan.tenantId(), audit.actor(), "pricing-plan.retired", "pricing-plan/" + plan.id(), audit.requestId(), now));
            return plan;
        });
    }

    public PricingPlan get(PlanId id) {
        return unitOfWork.transactional(() -> plans.findById(id).orElseThrow(() -> new NotFoundException("pricing plan not found")));
    }

    private PlanVersion publish(PricingPlan plan, Terms terms, Audit audit, Instant now) {
        for (Map.Entry<String, String> metric : terms.rule().metrics().entrySet()) {
            metrics.register(metric.getKey(), metric.getValue());
        }
        return plan.publish(terms.rule(), terms.billingCycle(), terms.minCharge(), terms.maxCharge(), terms.rounding(), terms.validFrom(), terms.validUntil(),
                audit.actor(), now);
    }

    private void announce(PricingPlan plan, PlanVersion version, Audit audit, Instant now, String action) {
        events.publish(new PricingEvents.PlanVersionPublished(ids.newId(), plan, version, now));
        auditLog.record(AuditEntry.tenant(plan.tenantId(), audit.actor(), action, "pricing-plan/" + plan.id() + "@" + version.number(), audit.requestId(), now));
    }
}
