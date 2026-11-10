package io.corebilling.pricing;

import io.corebilling.money.MoneyTestWorld;
import io.corebilling.money.MoneyTestWorld.World;
import io.corebilling.pricing.application.Audit;
import io.corebilling.pricing.application.PlanAdministration;
import io.corebilling.pricing.domain.BillingCycle;
import io.corebilling.pricing.domain.EventBased;
import io.corebilling.pricing.domain.Hybrid;
import io.corebilling.pricing.domain.PriceRule;
import io.corebilling.pricing.domain.PricingPlan;
import io.corebilling.pricing.domain.Subscription;
import io.corebilling.pricing.domain.Tier;
import io.corebilling.pricing.domain.TierMode;
import io.corebilling.pricing.domain.TimeBased;
import io.corebilling.pricing.domain.UsageBased;
import io.corebilling.pricing.domain.WorkloadBased;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.support.ScopeTestSupport;
import io.corebilling.usage.application.command.RecordUsageUseCase;
import io.corebilling.usage.domain.usage.UsageTrace;
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
