package io.corebilling.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.pricing.domain.BillingCycle;
import io.corebilling.pricing.domain.EventBased;
import io.corebilling.pricing.domain.PlanId;
import io.corebilling.pricing.domain.PlanStatus;
import io.corebilling.pricing.domain.PlanVersion;
import io.corebilling.pricing.domain.PricingModel;
import io.corebilling.pricing.domain.PricingPlan;
import io.corebilling.pricing.domain.RoundingPolicy;
import io.corebilling.pricing.domain.Subscription;
import io.corebilling.pricing.domain.TimeBased;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanVersionsTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private static PricingPlan calls() {
        return PricingPlan.create(new PlanId(UUID.randomUUID()), TENANT, "api-calls", "API calls", PricingModel.EVENT_BASED, Currency.of("USD"), NOW);
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
        PricingPlan plan = PricingPlan.create(new PlanId(UUID.randomUUID()), TENANT, "pro", "Pro", PricingModel.SUBSCRIPTION, Currency.of("USD"), NOW);
        assertThatThrownBy(() -> plan.publish(new Subscription(BigDecimal.TEN), null, null, null, null, null, null, "u", NOW)).hasMessageContaining("billing cycle");
        assertThat(plan.publish(new Subscription(BigDecimal.TEN), BillingCycle.MONTHLY, null, null, null, null, null, "u", NOW).billingCycle())
                .isEqualTo(BillingCycle.MONTHLY);

        PricingPlan other = calls();
        assertThatThrownBy(() -> other.publish(price("1"), null, 10L, 5L, null, null, null, "u", NOW)).hasMessageContaining("maximum charge");
        assertThatThrownBy(() -> other.publish(price("1"), null, -1L, null, null, null, null, "u", NOW)).hasMessageContaining("negative");
        assertThatThrownBy(() -> other.publish(price("1"), null, null, null, null, NOW, NOW, "u", NOW)).hasMessageContaining("validUntil");
    }

    @Test
    void planIdentity() {
        assertThatThrownBy(() -> PricingPlan.create(new PlanId(UUID.randomUUID()), TENANT, "Bad Code", "x", PricingModel.EVENT_BASED, Currency.of("USD"), NOW))
                .hasMessageContaining("plan code");
        assertThatThrownBy(() -> PricingPlan.create(new PlanId(UUID.randomUUID()), TENANT, "ok", " ", PricingModel.EVENT_BASED, Currency.of("USD"), NOW))
                .hasMessageContaining("name");
    }
}
