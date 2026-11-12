package dev.ngb.core_billing.pricing.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.metric.Measure;
import dev.ngb.core_billing.pricing.domain.metric.UsageSummary;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlanId;
import dev.ngb.core_billing.pricing.domain.plan.Quote;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every pricing model, the tier boundaries, the charge limits and the rounding policies, with no framework and no database. */
class PriceRulesTest {

    private static final Currency USD = Currency.of("USD");
    private static final Instant T0 = Instant.parse("2026-11-10T00:00:00Z");
    private static final PricingPlanId PLAN = new PricingPlanId(UUID.randomUUID());
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    private static UsageSummary usage(String metric, String quantity, long events) {
        return new UsageSummary(Map.of(metric, new Measure(d(quantity), events)));
    }

    private static PlanVersion version(PriceRule rule, BillingCycle cycle, Long min, Long max, RoundingPolicy rounding) {
        return new PlanVersion(PLAN, TENANT, 1, rule, cycle, min, max, rounding, T0, null, T0, "test");
    }

    private static Quote quote(PriceRule rule, UsageSummary usage) {
        return version(rule, rule.model() == PricingModel.SUBSCRIPTION || rule.model() == PricingModel.HYBRID ? BillingCycle.MONTHLY : null, null, null,
                RoundingPolicy.HALF_UP).quote(USD, usage);
    }

    private static long cents(PriceRule rule, UsageSummary usage) {
        return quote(rule, usage).amount().minorUnits();
    }

    // ---- time-based

    @ParameterizedTest(name = "{0} hours at 3.5 = {1}")
    @CsvSource({"0, 0", "1, 4", "2, 7", "2.5, 9", "0.1, 0", "10, 35", "24.0001, 84"})
    void timeBasedMultipliesTheDurationByTheRate(String hours, long expected) {
        assertThat(cents(new TimeBased("vm-hours", "hour", d("3.5"), d("0")), usage("vm-hours", hours, 1))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} minutes with a 1-minute increment at 2 = {1}")
    @CsvSource({"0.2, 2", "1, 2", "1.0001, 4", "90.2, 182", "60, 120"})
    void timeBasedBillsEveryStartedIncrement(String minutes, long expected) {
        assertThat(cents(new TimeBased("vm-minutes", "minute", d("2"), d("1")), usage("vm-minutes", minutes, 1))).isEqualTo(expected);
    }

    @Test
    void anIncrementCanBeFractional() {
        assertThat(cents(new TimeBased("vm-hours", "hour", d("60"), d("0.25")), usage("vm-hours", "1.1", 1))).as("1.25 hours x 60").isEqualTo(75);
    }

    // ---- usage-based

    private static final UsageBased GRADUATED = new UsageBased("storage-gb", "gb", TierMode.GRADUATED,
            List.of(new Tier(d("100"), d("0")), new Tier(d("1000"), d("2")), new Tier(null, d("1.5"))));
    private static final UsageBased VOLUME = new UsageBased("storage-gb", "gb", TierMode.VOLUME,
            List.of(new Tier(d("100"), d("3")), new Tier(d("1000"), d("2")), new Tier(null, d("1"))));

    @ParameterizedTest(name = "graduated {0} gb = {1}")
    @CsvSource({"0, 0", "50, 0", "100, 0", "100.5, 1", "101, 2", "500, 800", "1000, 1800", "1000.5, 1801", "1001, 1802", "1100, 1950", "10000, 15300"})
    void graduatedTiersPriceEachBandAtItsOwnRate(String gb, long expected) {
        assertThat(cents(GRADUATED, usage("storage-gb", gb, 1))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "volume {0} gb = {1}")
    @CsvSource({"0, 0", "1, 3", "100, 300", "100.5, 201", "101, 202", "1000, 2000", "1000.5, 1001", "1001, 1001", "5000, 5000"})
    void volumeTiersPriceTheWholeQuantityAtTheTierItFallsIn(String gb, long expected) {
        assertThat(cents(VOLUME, usage("storage-gb", gb, 1))).isEqualTo(expected);
    }

    @Test
    void aSingleTierIsAFlatRate() {
        assertThat(cents(new UsageBased("egress-gb", "gb", TierMode.GRADUATED, List.of(new Tier(null, d("9")))), usage("egress-gb", "12.5", 3)))
                .isEqualTo(113); // 112.5 rounds half up
    }

    @Test
    void theBreakdownExplainsEachBand() {
        Quote quote = quote(GRADUATED, usage("storage-gb", "1100", 1));
        assertThat(quote.lines()).extracting(PriceLine::amount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("0"), d("1800"), d("150"));
        assertThat(quote.lines().get(1).description()).contains("900 gb x 2").contains("tier 2");
    }

    @Test
    void tierValidation() {
        assertThatThrownBy(() -> new UsageBased("m", "u", TierMode.GRADUATED, List.of())).hasMessageContaining("at least one tier");
        assertThatThrownBy(() -> new UsageBased("m", "u", TierMode.GRADUATED, List.of(new Tier(d("10"), d("1"))))).hasMessageContaining("unbounded");
        assertThatThrownBy(() -> new UsageBased("m", "u", TierMode.GRADUATED, List.of(new Tier(null, d("1")), new Tier(null, d("2"))))).hasMessageContaining("only the last");
        assertThatThrownBy(() -> new UsageBased("m", "u", TierMode.GRADUATED, List.of(new Tier(d("10"), d("1")), new Tier(d("10"), d("2")), new Tier(null, d("3")))))
                .hasMessageContaining("increase");
        assertThatThrownBy(() -> new Tier(d("0"), d("1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tier(null, d("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- workload-based

    @Test
    void workloadChargesAStartFeePerExecutionPlusTheWorkDone() {
        WorkloadBased rule = new WorkloadBased("job-cpu-seconds", "cpu-second", d("5"), d("0.1"));
        Quote quote = quote(rule, usage("job-cpu-seconds", "120.5", 3));
        assertThat(quote.exactAmount()).isEqualByComparingTo("27.05"); // 3 x 5 + 120.5 x 0.1
        assertThat(quote.amount().minorUnits()).isEqualTo(27);
        assertThat(cents(rule, usage("job-cpu-seconds", "10", 1))).as("a short job still pays to start").isEqualTo(6);
        assertThat(cents(rule, UsageSummary.none())).isZero();
    }

    // ---- event-based

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 0", "1000, 50", "1000000, 50000", "30, 2"})
    void eventBasedMultipliesTheCountByTheRate(String count, long expected) {
        assertThat(cents(new EventBased("api-calls", "call", d("0.05")), usage("api-calls", count, 1))).isEqualTo(expected);
    }

    @Test
    void eventBasedNeedsWholeEvents() {
        assertThatThrownBy(() -> cents(new EventBased("api-calls", "call", d("1")), usage("api-calls", "2.5", 1))).hasMessageContaining("whole number");
        assertThat(cents(new EventBased("api-calls", "call", d("1")), usage("api-calls", "2.000", 1))).isEqualTo(2);
    }

    // ---- subscription and hybrid

    @Test
    void aSubscriptionIsAFixedAmountWhateverIsUsed() {
        assertThat(cents(new FixedPrice(d("2900")), UsageSummary.none())).isEqualTo(2900);
        assertThat(cents(new FixedPrice(d("2900")), usage("anything", "999", 9))).isEqualTo(2900);
    }

    @Test
    void aHybridAddsTheBaseToEveryComponent() {
        Hybrid package1 = new Hybrid(d("2900"), List.of(GRADUATED, new EventBased("api-calls", "call", d("0.05"))));
        UsageSummary usage = new UsageSummary(Map.of("storage-gb", new Measure(d("1100"), 4), "api-calls", new Measure(d("1000"), 1000)));
        Quote quote = quote(package1, usage);
        assertThat(quote.exactAmount()).isEqualByComparingTo("4900"); // 2900 + 1950 + 50
        assertThat(quote.lines()).hasSize(1 + 3 + 1);
        assertThat(package1.metrics()).containsOnlyKeys("storage-gb", "api-calls");
        assertThat(cents(package1, UsageSummary.none())).as("the base alone").isEqualTo(2900);
    }

    @Test
    void hybridValidation() {
        assertThatThrownBy(() -> new Hybrid(d("1"), List.of())).hasMessageContaining("at least one");
        assertThatThrownBy(() -> new Hybrid(d("1"), List.of(new FixedPrice(d("1"))))).hasMessageContaining("component");
        assertThatThrownBy(() -> new Hybrid(d("1"), List.of(new EventBased("calls", "call", d("1")), new EventBased("calls", "call", d("2")))))
                .hasMessageContaining("more than one");
    }

    // ---- limits and rounding

    @Test
    void theMinimumAndMaximumChargeReplaceTheCalculatedAmount() {
        TimeBased rule = new TimeBased("vm-hours", "hour", d("100"), d("0"));
        PlanVersion limited = version(rule, null, 500L, 1000L, RoundingPolicy.HALF_UP);

        Quote low = limited.quote(USD, usage("vm-hours", "1", 1));
        assertThat(low.amount().minorUnits()).isEqualTo(500);
        assertThat(low.adjustment()).isEqualTo(Quote.Adjustment.MINIMUM_CHARGE);
        assertThat(low.exactAmount()).as("the calculation is still visible").isEqualByComparingTo("100");

        Quote high = limited.quote(USD, usage("vm-hours", "50", 1));
        assertThat(high.amount().minorUnits()).isEqualTo(1000);
        assertThat(high.adjustment()).isEqualTo(Quote.Adjustment.MAXIMUM_CHARGE);

        Quote inside = limited.quote(USD, usage("vm-hours", "7", 1));
        assertThat(inside.amount().minorUnits()).isEqualTo(700);
        assertThat(inside.adjustment()).isEqualTo(Quote.Adjustment.NONE);
        assertThat(limited.quote(USD, UsageSummary.none()).amount().minorUnits()).as("a minimum applies even to no usage").isEqualTo(500);
    }

    @ParameterizedTest(name = "{0} rounds {1} to {2}")
    @CsvSource({
        "HALF_UP, 10.4, 10", "HALF_UP, 10.5, 11", "HALF_UP, 10.6, 11", "HALF_UP, 11.5, 12",
        "HALF_EVEN, 10.4, 10", "HALF_EVEN, 10.5, 10", "HALF_EVEN, 10.6, 11", "HALF_EVEN, 11.5, 12",
        "UP, 10, 10", "UP, 10.0001, 11", "UP, 10.9, 11",
        "DOWN, 10.9999, 10", "DOWN, 10, 10", "DOWN, 10.5, 10"})
    void everyRoundingPolicyRoundsTheTotalOnce(RoundingPolicy policy, String exact, long expected) {
        PlanVersion priced = version(new TimeBased("t", "hour", d(exact), d("0")), null, null, null, policy);
        assertThat(priced.quote(USD, usage("t", "1", 1)).amount().minorUnits()).isEqualTo(expected);
    }

    @Test
    void roundingHappensOnceOnTheTotalNotOnEachLine() {
        // Two lines of 0.4 each: rounding each would give 0 + 0; the total 0.8 rounds to 1.
        Hybrid hybrid = new Hybrid(d("0.4"), List.of(new EventBased("calls", "call", d("0.4"))));
        assertThat(version(hybrid, BillingCycle.MONTHLY, null, null, RoundingPolicy.HALF_UP).quote(USD, usage("calls", "1", 1)).amount().minorUnits()).isEqualTo(1);
    }

    @Test
    void anAmountThatDoesNotFitInALongIsRefused() {
        TimeBased huge = new TimeBased("t", "hour", d("1000000000000000"), d("0"));
        assertThatThrownBy(() -> cents(huge, usage("t", "999999999999999999", 1))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void fractionsOfAMinorUnitSurviveIntermediateMath() {
        EventBased rule = new EventBased("calls", "call", d("0.0000004"));
        assertThat(quote(rule, usage("calls", "1000000", 1)).exactAmount()).isEqualByComparingTo("0.4");
        assertThat(cents(rule, usage("calls", "3000000", 1))).isEqualTo(1);
    }

    // ---- a strategy added without touching anything else

    /** Not one of the shipped rules: the first N units are free, the rest cost 1 each. */
    private record FirstUnitsFree(String metric, int freeUnits) implements PriceRule {
        @Override
        public PricingModel model() {
            return PricingModel.USAGE_BASED;
        }

        @Override
        public Map<String, String> metrics() {
            return Map.of(metric, "unit");
        }

        @Override
        public List<PriceLine> price(UsageSummary usage) {
            BigDecimal paid = usage.of(metric).quantity().subtract(BigDecimal.valueOf(freeUnits)).max(BigDecimal.ZERO);
            return List.of(new PriceLine(paid + " units after " + freeUnits + " free", metric, paid, paid));
        }
    }

    @Test
    void aNewStrategyPricesThroughTheUnchangedQuotePipeline() {
        PlanVersion v = version(new FirstUnitsFree("seats", 5), null, 100L, null, RoundingPolicy.UP);
        assertThat(v.quote(USD, usage("seats", "3", 1)).amount().minorUnits()).as("minimum charge still applies").isEqualTo(100);
        assertThat(v.quote(USD, usage("seats", "205", 1)).amount().minorUnits()).isEqualTo(200);
    }

    // ---- inputs

    @Test
    void ruleInputsAreValidated() {
        assertThatThrownBy(() -> new TimeBased("Bad Metric", "hour", d("1"), d("0"))).hasMessageContaining("metric");
        assertThatThrownBy(() -> new TimeBased("m", "9units", d("1"), d("0"))).hasMessageContaining("unit");
        assertThatThrownBy(() -> new TimeBased("m", "hour", d("-1"), d("0"))).hasMessageContaining("negative");
        assertThatThrownBy(() -> new TimeBased("m", "hour", d("0.00000000001"), d("0"))).hasMessageContaining("decimal places");
        assertThatThrownBy(() -> new TimeBased("m", "hour", d("1000000000000001"), d("0"))).hasMessageContaining("too large");
        assertThatThrownBy(() -> new EventBased("m", "call", null)).hasMessageContaining("required");
    }

    @Test
    void aHybridChargedForUsageIsPricedWithoutItsBaseBecauseTheSubscriptionBillsTheBase() {
        PlanVersion v = version(new Hybrid(d("2900"), List.of(new EventBased("api-calls", "call", d("0.05")))), BillingCycle.MONTHLY, null, null, RoundingPolicy.HALF_UP);
        UsageSummary calls = usage("api-calls", "1000", 1);

        assertThat(v.quote(USD, calls).amount().minorUnits()).as("an estimate of a whole cycle includes the base").isEqualTo(2_950);
        Quote used = v.quoteUsage(USD, calls);
        assertThat(used.amount().minorUnits()).isEqualTo(50);
        assertThat(used.lines()).extracting(PriceLine::description).noneMatch(d -> d.contains("base"));
        assertThat(v.quoteUsage(USD, UsageSummary.none()).amount().minorUnits()).isZero();
    }

    @Test
    void everyOtherModelPricesUsageExactlyAsAQuote() {
        PlanVersion v = version(new EventBased("api-calls", "call", d("0.05")), null, null, null, RoundingPolicy.HALF_UP);
        UsageSummary calls = usage("api-calls", "1000", 1);
        assertThat(v.quoteUsage(USD, calls).amount()).isEqualTo(v.quote(USD, calls).amount());
    }
}
