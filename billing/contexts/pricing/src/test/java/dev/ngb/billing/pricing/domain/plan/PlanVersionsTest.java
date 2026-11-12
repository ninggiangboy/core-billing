package dev.ngb.billing.pricing.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.pricing.api.plan.BillingCycle;
import dev.ngb.billing.pricing.domain.rule.EventBased;
import dev.ngb.billing.pricing.domain.rule.FixedPrice;
import dev.ngb.billing.pricing.domain.rule.Hybrid;
import dev.ngb.billing.pricing.domain.rule.PricingModel;
import dev.ngb.billing.pricing.domain.rule.TimeBased;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanVersionsTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private static PricingPlan calls() {
        return PricingPlan.create(new PricingPlanId(UUID.randomUUID()), TENANT, "api-calls", "API calls", PricingModel.EVENT_BASED, Currency.of("USD"), NOW);
    }

    private static EventBased price(String unitPrice) {
        return new EventBased("api-calls", "call", new BigDecimal(unitPrice));
    }

    @Test
    void versionsAreNumberedInOrderAndNeverChange() {
        PricingPlan plan = calls();
        PlanVersion v1 = plan.publish(price("1"), null, null, null, null, null, null, "user/a", NOW);
        PlanVersion v2 = plan.publish(price("2"), null, null, null, RoundingPolicy.UP, NOW.plusSeconds(60), null, "user/a", NOW.plusSeconds(10));

        assertThat(v1.number()).isEqualTo(1);
        assertThat(v2.number()).isEqualTo(2);
        assertThat(plan.versions()).containsExactly(v1, v2);
        assertThat(v1.rounding()).as("the default").isEqualTo(RoundingPolicy.HALF_UP);
        assertThat(((EventBased) v1.rule()).unitPrice()).as("publishing v2 left v1 as it was").isEqualByComparingTo("1");
    }

    @Test
    void theVersionInForceDependsOnTheMoment() {
        PricingPlan plan = calls();
        plan.publish(price("1"), null, null, null, null, NOW, null, "u", NOW);
        plan.publish(price("2"), null, null, null, null, NOW.plusSeconds(3600), null, "u", NOW);

        assertThat(plan.effectiveAt(NOW.minusSeconds(1))).isEmpty();
        assertThat(plan.effectiveAt(NOW).orElseThrow().number()).isEqualTo(1);
        assertThat(plan.effectiveAt(NOW.plusSeconds(3599)).orElseThrow().number()).isEqualTo(1);
        assertThat(plan.effectiveAt(NOW.plusSeconds(3600)).orElseThrow().number()).isEqualTo(2);
    }

    @Test
    void aVersionCanBeGivenAnEndDate() {
        PricingPlan plan = calls();
        plan.publish(price("1"), null, null, null, null, NOW, NOW.plusSeconds(100), "u", NOW);
        assertThat(plan.effectiveAt(NOW.plusSeconds(99))).isPresent();
        assertThat(plan.effectiveAt(NOW.plusSeconds(100))).as("the end is excluded").isEmpty();
    }

    @Test
    void theTimelineOnlyMovesForward() {
        PricingPlan plan = calls();
        plan.publish(price("1"), null, null, null, null, NOW.plusSeconds(500), null, "u", NOW);
        assertThatThrownBy(() -> plan.publish(price("2"), null, null, null, null, NOW.plusSeconds(100), null, "u", NOW)).hasMessageContaining("cannot start before");
    }

    @Test
    void aVersionMustFitThePlansModel() {
        PricingPlan plan = calls();
        assertThatThrownBy(() -> plan.publish(new TimeBased("t", "hour", BigDecimal.ONE, BigDecimal.ZERO), null, null, null, null, null, null, "u", NOW))
                .hasMessageContaining("EVENT_BASED");
    }

    @Test
    void aRetiredPlanTakesNoNewVersionsButKeepsItsOld() {
        PricingPlan plan = calls();
        plan.publish(price("1"), null, null, null, null, null, null, "u", NOW);
        plan.retire(NOW.plusSeconds(1));

        assertThat(plan.status()).isEqualTo(PlanStatus.RETIRED);
        assertThat(plan.retiredAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(plan.version(1)).isPresent();
        assertThatThrownBy(() -> plan.publish(price("2"), null, null, null, null, null, null, "u", NOW)).hasMessageContaining("retired");
        assertThatThrownBy(() -> plan.retire(NOW)).hasMessageContaining("already retired");
    }

    @Test
    void versionRules() {
        PricingPlan plan = PricingPlan.create(new PricingPlanId(UUID.randomUUID()), TENANT, "pro", "Pro", PricingModel.SUBSCRIPTION, Currency.of("USD"), NOW);
        assertThatThrownBy(() -> plan.publish(new FixedPrice(BigDecimal.TEN), null, null, null, null, null, null, "u", NOW)).hasMessageContaining("billing cycle");
        assertThat(plan.publish(new FixedPrice(BigDecimal.TEN), BillingCycle.MONTHLY, null, null, null, null, null, "u", NOW).billingCycle())
                .isEqualTo(BillingCycle.MONTHLY);

        PricingPlan other = calls();
        assertThatThrownBy(() -> other.publish(price("1"), null, 10L, 5L, null, null, null, "u", NOW)).hasMessageContaining("maximum charge");
        assertThatThrownBy(() -> other.publish(price("1"), null, -1L, null, null, null, null, "u", NOW)).hasMessageContaining("negative");
        assertThatThrownBy(() -> other.publish(price("1"), null, null, null, null, NOW, NOW, "u", NOW)).hasMessageContaining("validUntil");
    }

    @Test
    void planIdentity() {
        assertThatThrownBy(() -> PricingPlan.create(new PricingPlanId(UUID.randomUUID()), TENANT, "Bad Code", "x", PricingModel.EVENT_BASED, Currency.of("USD"), NOW))
                .hasMessageContaining("plan code");
        assertThatThrownBy(() -> PricingPlan.create(new PricingPlanId(UUID.randomUUID()), TENANT, "ok", " ", PricingModel.EVENT_BASED, Currency.of("USD"), NOW))
                .hasMessageContaining("name");
    }

    @Test
    void aSubscriptionVersionHasItsFixedPriceAsTheRecurringAmountWithTheLimitsApplied() {
        PricingPlan plan = PricingPlan.create(new PricingPlanId(UUID.randomUUID()), TENANT, "pro", "Pro", PricingModel.SUBSCRIPTION, Currency.of("USD"), NOW);
        PlanVersion plain = plan.publish(new FixedPrice(new BigDecimal("1999.6")), BillingCycle.MONTHLY, null, null, null, null, null, "user/a", NOW);
        PlanVersion capped = plan.publish(new FixedPrice(new BigDecimal("1999.6")), BillingCycle.MONTHLY, null, 1_500L, null, NOW.plusSeconds(60), null, "user/a",
                NOW.plusSeconds(1));

        assertThat(plain.recurringAmount(Currency.of("USD"))).contains(Money.of(2_000, Currency.of("USD")));
        assertThat(capped.recurringAmount(Currency.of("USD"))).contains(Money.of(1_500, Currency.of("USD")));
    }

    @Test
    void aHybridVersionRecurringAmountIsItsBaseOnlyAndPureUsagePlansHaveNone() {
        PricingPlan hybrid = PricingPlan.create(new PricingPlanId(UUID.randomUUID()), TENANT, "cloud", "Cloud", PricingModel.HYBRID, Currency.of("USD"), NOW);
        PlanVersion version = hybrid.publish(new Hybrid(new BigDecimal("500"), List.of(price("1"))), BillingCycle.MONTHLY, null, 100L, null, null, null, "user/a", NOW);
        assertThat(version.recurringAmount(Currency.of("USD"))).as("the usage limits do not cut the base").contains(Money.of(500, Currency.of("USD")));

        PlanVersion usageOnly = calls().publish(price("1"), null, null, null, null, null, null, "user/a", NOW);
        assertThat(usageOnly.recurringAmount(Currency.of("USD"))).isEmpty();
    }
}
