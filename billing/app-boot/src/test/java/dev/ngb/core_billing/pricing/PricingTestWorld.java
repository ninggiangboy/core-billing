package dev.ngb.core_billing.pricing;

import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.application.plan.command.PlanAdministration;
import dev.ngb.core_billing.pricing.domain.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.model.EventBased;
import dev.ngb.core_billing.pricing.domain.model.Hybrid;
import dev.ngb.core_billing.pricing.domain.model.PriceRule;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.pricing.domain.model.Subscription;
import dev.ngb.core_billing.pricing.domain.model.Tier;
import dev.ngb.core_billing.pricing.domain.model.TierMode;
import dev.ngb.core_billing.pricing.domain.model.TimeBased;
import dev.ngb.core_billing.pricing.domain.model.UsageBased;
import dev.ngb.core_billing.pricing.domain.model.WorkloadBased;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.usage.application.command.RecordUsageUseCase;
import dev.ngb.core_billing.usage.domain.usage.UsageTrace;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds tenants with a funded wallet, pricing plans and usage for the pricing tests. */
@Component
public class PricingTestWorld {

    public static final Audit AUDIT = new Audit("user/test", "req-1");
    public static final Currency USD = Currency.of("USD");
    /** Plans start well before the usage the tests record, so the first version is in force for it. */
    public static final Instant VALID_FROM = Instant.parse("2026-09-01T00:00:00Z");

    private final MoneyTestWorld money;
    private final ScopeTestSupport scopes;
    private final PlanAdministration plans;
    private final RecordUsageUseCase recordUsage;

    PricingTestWorld(MoneyTestWorld money, ScopeTestSupport scopes, PlanAdministration plans, RecordUsageUseCase recordUsage) {
        this.money = money;
        this.scopes = scopes;
        this.plans = plans;
        this.recordUsage = recordUsage;
    }

    public static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    public World prepaid(long balance) {
        return money.prepaid(balance);
    }

    public World postpaid(long limit) {
        return money.postpaid(limit);
    }

    public PricingPlan plan(TenantId tenant, String codePrefix, PriceRule rule) {
        return plan(tenant, codePrefix, rule, null, null, null);
    }

    public PricingPlan plan(TenantId tenant, String codePrefix, PriceRule rule, BillingCycle cycle, Long min, Long max) {
        return scopes.inTenant(tenant, () -> plans.create(AUDIT, codePrefix + "-" + UUID.randomUUID().toString().substring(0, 8), "Plan " + codePrefix, USD,
                new PlanAdministration.Terms(rule, cycle, min, max, null, VALID_FROM, null)));
    }

    /** Storage in GB: 100 free, then 2 per GB up to 1000, then 1.5 per GB. Each test gets its own metric. */
    public UsageBased storage(String metric) {
        return new UsageBased(metric, "gb", TierMode.GRADUATED, List.of(new Tier(d("100"), d("0")), new Tier(d("1000"), d("2")), new Tier(null, d("1.5"))));
    }

    public TimeBased vm(String metric) {
        return new TimeBased(metric, "hour", d("3.5"), d("0"));
    }

    public EventBased calls(String metric) {
        return new EventBased(metric, "call", d("0.05"));
    }

    public WorkloadBased jobs(String metric) {
        return new WorkloadBased(metric, "cpu-second", d("5"), d("0.1"));
    }

    public Subscription pro() {
        return new Subscription(d("2900"));
    }

    public Hybrid cloudPackage(String storageMetric, String callMetric) {
        return new Hybrid(d("2900"), List.of(storage(storageMetric), calls(callMetric)));
    }

    public String metric() {
        return "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public RecordUsageUseCase.Recorded record(TenantId tenant, CustomerId customer, String metric, String unit, String quantity, Instant at, String key) {
        return scopes.inTenant(tenant, () -> recordUsage.record(
                new RecordUsageUseCase.Item(customer, metric, d(quantity), unit, at, key, new UsageTrace(null, "res-1", "src-" + key))));
    }

    public RecordUsageUseCase.Recorded record(TenantId tenant, CustomerId customer, String metric, String unit, String quantity, Instant at) {
        return record(tenant, customer, metric, unit, quantity, at, "u-" + UUID.randomUUID());
    }
}
