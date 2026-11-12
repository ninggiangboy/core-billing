package dev.ngb.billing.pricing;

import static dev.ngb.billing.pricing.PricingTestWorld.AUDIT;
import static dev.ngb.billing.pricing.PricingTestWorld.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.Application;
import dev.ngb.billing.money.MoneyTestWorld.World;
import dev.ngb.billing.money.api.charge.ChargeCommand;
import dev.ngb.billing.money.api.charge.ChargeStatus;
import dev.ngb.billing.money.api.charge.ChargeWallet;
import dev.ngb.billing.money.api.wallet.CreditCommand;
import dev.ngb.billing.money.api.wallet.CreditWallet;
import dev.ngb.billing.money.api.wallet.InsufficientFundsException;
import dev.ngb.billing.money.application.wallet.query.WalletQueries;
import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.billing.platform.api.error.NotFoundException;
import dev.ngb.billing.pricing.api.plan.BillingCycle;
import dev.ngb.billing.pricing.application.plan.command.ManagePlanUseCase;
import dev.ngb.billing.pricing.application.plan.query.PlanQueries;
import dev.ngb.billing.pricing.application.plan.query.PricingPlanView;
import dev.ngb.billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.billing.pricing.application.rating.command.EstimateCostUseCase;
import dev.ngb.billing.pricing.application.rating.command.ReserveFromEstimateUseCase;
import dev.ngb.billing.pricing.application.rating.query.PriceLineView;
import dev.ngb.billing.pricing.application.rating.query.RatingQueries;
import dev.ngb.billing.pricing.application.rating.query.UsageRatingView;
import dev.ngb.billing.pricing.domain.metric.Measure;
import dev.ngb.billing.pricing.domain.plan.PlanStatus;
import dev.ngb.billing.pricing.domain.plan.PlanVersion;
import dev.ngb.billing.pricing.domain.plan.PricingPlan;
import dev.ngb.billing.pricing.domain.plan.PricingPlanId;
import dev.ngb.billing.pricing.domain.rating.UsageRating;
import dev.ngb.billing.pricing.domain.rule.EventBased;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.support.OwnerDatabase;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import dev.ngb.billing.usage.api.lookup.UsageLookup;
import dev.ngb.billing.usage.api.lookup.UsageTotal;
import dev.ngb.billing.usage.application.usage.command.RecordUsageUseCase;
import dev.ngb.billing.usage.domain.usage.UsageTrace;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Plans, usage, estimation and charging against real PostgreSQL: idempotency, versions, atomicity and isolation. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class PricingUsageIntegrationTest {

    private static final Instant DAY = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired PricingTestWorld worlds;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired JdbcTemplate appJdbc;
    @Autowired ManagePlanUseCase planAdmin;
    @Autowired PlanQueries planQueries;
    @Autowired EstimateCostUseCase estimate;
    @Autowired ReserveFromEstimateUseCase reserve;
    @Autowired ChargeFromUsageUseCase chargeUsage;
    @Autowired RatingQueries ratings;
    @Autowired RecordUsageUseCase recordUsage;
    @Autowired UsageLookup usageLookup;
    @Autowired WalletQueries wallets;
    @Autowired CreditWallet credit;
    @Autowired dev.ngb.billing.money.api.charge.ChargeWallet chargeWallet;

    private ChargeFromUsageUseCase.Command window(World w, PricingPlan plan, Integer version) {
        return new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), plan.id(), version, DAY, DAY.plus(1, ChronoUnit.DAYS));
    }

    private ChargeFromUsageUseCase.Result charge(World w, ChargeFromUsageUseCase.Command command) {
        return scopes.inTenant(w.tenant().id(), () -> chargeUsage.execute(command));
    }

    private long balance(World w) {
        return scopes.inTenant(w.tenant().id(), () -> wallets.wallet(w.wallet().id())).balance().minorUnits();
    }

    private long count(String sql, Object... args) {
        return owner.count(sql, args);
    }

    // ---- usage recording

    @Test
    void postingTheSameUsageEventTwiceStoresOneRow() {
        World w = worlds.prepaid(0);
        String metric = worlds.metric();
        worlds.plan(w.tenant().id(), "calls", worlds.calls(metric));
        RecordUsageUseCase.Item item = new RecordUsageUseCase.Item(w.customerId(), metric, d("5"), "call", DAY, "key-1", new UsageTrace(null, "vm-1", "evt-1"));

        var first = scopes.inTenant(w.tenant().id(), () -> recordUsage.record(item));
        var second = scopes.inTenant(w.tenant().id(), () -> recordUsage.record(item));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.event().id()).isEqualTo(first.event().id());
        assertThat(count("select count(*) from usage_events where tenant_id = ?", w.tenant().id().value())).isEqualTo(1);
        assertThat(first.event().trace().resourceRef()).as("the source trace is kept").isEqualTo("vm-1");
    }

    @Test
    void theSameKeyWithDifferentContentIsAConflict() {
        World w = worlds.prepaid(0);
        String metric = worlds.metric();
        worlds.plan(w.tenant().id(), "calls", worlds.calls(metric));
        worlds.record(w.tenant().id(), w.customerId(), metric, "call", "5", DAY, "key-x");

        assertThatThrownBy(() -> worlds.record(w.tenant().id(), w.customerId(), metric, "call", "6", DAY, "key-x")).isInstanceOf(IdempotencyConflictException.class);
        assertThatThrownBy(() -> worlds.record(w.tenant().id(), w.customerId(), metric, "call", "5", DAY.plusSeconds(1), "key-x")).isInstanceOf(IdempotencyConflictException.class);
        assertThat(worlds.record(w.tenant().id(), w.customerId(), metric, "call", "5.000", DAY, "key-x").replayed()).as("5 and 5.000 are the same quantity").isTrue();
    }

    @Test
    void concurrentDuplicatesOfOneEventStoreExactlyOne() throws Exception {
        World w = worlds.prepaid(0);
        String metric = worlds.metric();
        worlds.plan(w.tenant().id(), "calls", worlds.calls(metric));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<RecordUsageUseCase.Recorded>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> worlds.record(w.tenant().id(), w.customerId(), metric, "call", "1", DAY, "same-key"), pool));
            }
            Set<UUID> ids = new HashSet<>();
            long fresh = 0;
            for (var f : futures) {
                var recorded = f.get(30, TimeUnit.SECONDS);
                ids.add(recorded.event().id().value());
                fresh += recorded.replayed() ? 0 : 1;
            }
            assertThat(ids).hasSize(1);
            assertThat(fresh).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from usage_events where tenant_id = ?", w.tenant().id().value())).isEqualTo(1);
    }

    @Test
    void aBatchIsAllOrNothingAndReportsWhichEventsWereRepeats() {
        World w = worlds.prepaid(0);
        String metric = worlds.metric();
        worlds.plan(w.tenant().id(), "calls", worlds.calls(metric));
        TenantId tenant = w.tenant().id();
        RecordUsageUseCase.Item a = new RecordUsageUseCase.Item(w.customerId(), metric, d("1"), "call", DAY, "a", new UsageTrace(null, null, null));
        RecordUsageUseCase.Item b = new RecordUsageUseCase.Item(w.customerId(), metric, d("2"), "call", DAY, "b", new UsageTrace(null, null, null));
        RecordUsageUseCase.Item bad = new RecordUsageUseCase.Item(w.customerId(), metric, d("-1"), "call", DAY, "c", new UsageTrace(null, null, null));

        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> recordUsage.record(List.of(a, bad)))).hasMessageContaining("usage event 1");
        assertThat(count("select count(*) from usage_events where tenant_id = ?", tenant.value())).as("the valid one was not kept either").isZero();

        scopes.inTenant(tenant, () -> recordUsage.record(List.of(a)));
        var results = scopes.inTenant(tenant, () -> recordUsage.record(List.of(a, b)));
        assertThat(results).extracting(RecordUsageUseCase.Recorded::replayed).containsExactly(true, false);

        RecordUsageUseCase.Item conflicting = new RecordUsageUseCase.Item(w.customerId(), metric, d("9"), "call", DAY, "b", new UsageTrace(null, null, null));
        RecordUsageUseCase.Item fresh = new RecordUsageUseCase.Item(w.customerId(), metric, d("3"), "call", DAY, "d", new UsageTrace(null, null, null));
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> recordUsage.record(List.of(fresh, conflicting)))).isInstanceOf(IdempotencyConflictException.class);
        assertThat(count("select count(*) from usage_events where tenant_id = ?", tenant.value())).as("a conflict rolls the whole batch back").isEqualTo(2);

        List<RecordUsageUseCase.Item> tooMany = java.util.Collections.nCopies(RecordUsageUseCase.MAX_BATCH + 1, a);
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> recordUsage.record(tooMany))).hasMessageContaining("at most");
    }

    @Test
    void usageIsValidatedAgainstThePlansMetricAndUnit() {
        World w = worlds.prepaid(0);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        worlds.plan(tenant, "calls", worlds.calls(metric));

        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), "no-such-metric", "call", "1", DAY)).hasMessageContaining("unknown metric");
        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), metric, "gb", "1", DAY)).hasMessageContaining("measured in 'call'");
        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), metric, "call", "0", DAY)).hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), metric, "call", "-3", DAY)).hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), metric, "call", "0.1234567", DAY)).hasMessageContaining("decimal places");
        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), metric, "call", "1", Instant.now().plus(1, ChronoUnit.HOURS))).hasMessageContaining("future");
        assertThatThrownBy(() -> worlds.record(tenant, w.customerId(), metric, "call", "1", DAY, " ")).hasMessageContaining("Idempotency-Key must be 1-255");
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> recordUsage.record(new RecordUsageUseCase.Item(
                new dev.ngb.billing.sharedkernel.id.CustomerId(UUID.randomUUID()), metric, d("1"), "call", DAY, "k", new UsageTrace(null, null, null)))))
                .isInstanceOf(NotFoundException.class);
        assertThat(worlds.record(tenant, w.customerId(), metric, "call", "0.000001", DAY).replayed()).as("six decimals are fine").isFalse();
    }

    @Test
    void anotherTenantsCustomerAndPlansAreInvisible() {
        World acme = worlds.prepaid(0);
        World globex = worlds.prepaid(0);
        String metric = worlds.metric();
        PricingPlan acmePlan = worlds.plan(acme.tenant().id(), "calls", worlds.calls(metric));
        assertThatThrownBy(() -> worlds.record(globex.tenant().id(), acme.customerId(), metric, "call", "1", DAY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown metric");
        assertThatThrownBy(() -> scopes.inTenant(globex.tenant().id(), () -> planAdmin.get(acmePlan.id()))).isInstanceOf(NotFoundException.class);
        assertThat(scopes.inTenant(globex.tenant().id(), () -> planQueries.list(null))).isEmpty();
        worlds.plan(globex.tenant().id(), "calls", worlds.calls(metric));
        assertThatThrownBy(() -> worlds.record(globex.tenant().id(), acme.customerId(), metric, "call", "1", DAY)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void usageCannotBeChangedOrDeletedByAnyone() {
        World w = worlds.prepaid(0);
        String metric = worlds.metric();
        worlds.plan(w.tenant().id(), "calls", worlds.calls(metric));
        var recorded = worlds.record(w.tenant().id(), w.customerId(), metric, "call", "1", DAY);
        UUID id = recorded.event().id().value();

        assertThatThrownBy(() -> appJdbc.update("update usage_events set quantity = 99 where id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> appJdbc.update("delete from usage_events where id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.jdbc().update("update usage_events set quantity = 99 where id = ?", id)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.jdbc().update("delete from usage_events where id = ?", id)).hasMessageContaining("append-only");
        assertThat(count("select count(*) from usage_events where id = ? and quantity = 1", id)).isEqualTo(1);
    }

    @Test
    void aggregationSumsQuantityAndEventsPerMetricInAHalfOpenPeriod() {
        World w = worlds.prepaid(0);
        TenantId tenant = w.tenant().id();
        String storage = worlds.metric();
        String calls = worlds.metric();
        worlds.plan(tenant, "pkg", worlds.cloudPackage(storage, calls), dev.ngb.billing.pricing.api.plan.BillingCycle.MONTHLY, null, null);
        worlds.record(tenant, w.customerId(), storage, "gb", "10.5", DAY);
        worlds.record(tenant, w.customerId(), storage, "gb", "4.5", DAY.plusSeconds(60));
        worlds.record(tenant, w.customerId(), calls, "call", "100", DAY.plusSeconds(120));
        worlds.record(tenant, w.customerId(), storage, "gb", "1000", DAY.plus(1, ChronoUnit.DAYS)); // exactly at the end: excluded

        List<UsageTotal> totals = scopes.inTenant(tenant, () -> usageLookup.totals(w.customerId(), Set.of(), DAY, DAY.plus(1, ChronoUnit.DAYS)));
        assertThat(totals).extracting(UsageTotal::metric).containsExactlyInAnyOrder(storage, calls);
        UsageTotal storageTotal = totals.stream().filter(t -> t.metric().equals(storage)).findFirst().orElseThrow();
        assertThat(storageTotal.quantity()).isEqualByComparingTo("15");
        assertThat(storageTotal.events()).isEqualTo(2);
        assertThat(storageTotal.unit()).isEqualTo("gb");
        assertThat(scopes.inTenant(tenant, () -> usageLookup.totals(w.customerId(), Set.of(calls), DAY, DAY.plus(1, ChronoUnit.DAYS)))).hasSize(1);
    }

    // ---- plans

    @Test
    void plansAreVersionedRetiredAndAnnounced() {
        World w = worlds.prepaid(0);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "calls", worlds.calls(metric));

        PlanVersion v2 = scopes.inTenant(tenant, () -> planAdmin.publishVersion(AUDIT, plan.id(), new ManagePlanUseCase.Terms(
                new EventBased(metric, "call", d("0.10")), null, null, null, null, null, null)));
        assertThat(v2.number()).isEqualTo(2);
        PricingPlan loaded = scopes.inTenant(tenant, () -> planAdmin.get(plan.id()));
        assertThat(loaded.versions()).hasSize(2);
        assertThat(((EventBased) loaded.version(1).orElseThrow().rule()).unitPrice()).isEqualByComparingTo("0.05");

        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> planAdmin.create(AUDIT, plan.code(), "dup", PricingTestWorld.USD,
                new ManagePlanUseCase.Terms(worlds.calls(worlds.metric()), null, null, null, null, null, null)))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> planAdmin.create(AUDIT, "other-" + UUID.randomUUID().toString().substring(0, 6), "other",
                PricingTestWorld.USD, new ManagePlanUseCase.Terms(new EventBased(metric, "request", d("1")), null, null, null, null, null, null))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already measured in 'call'");

        scopes.inTenant(tenant, () -> planAdmin.retire(AUDIT, plan.id()));
        assertThat(scopes.inTenant(tenant, () -> planQueries.list(dev.ngb.billing.pricing.domain.plan.PlanStatus.ACTIVE))).extracting(PricingPlanView::id).doesNotContain(plan.id().value());
        assertThat(scopes.inTenant(tenant, () -> planQueries.list(dev.ngb.billing.pricing.domain.plan.PlanStatus.RETIRED))).extracting(PricingPlanView::id).contains(plan.id().value());
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> planAdmin.publishVersion(AUDIT, plan.id(), new ManagePlanUseCase.Terms(
                worlds.calls(metric), null, null, null, null, null, null)))).isInstanceOf(ConflictException.class).hasMessageContaining("retired");
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> planAdmin.retire(AUDIT, plan.id()))).isInstanceOf(ConflictException.class);

        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'pricing-plan.version-published'", tenant.value())).isEqualTo(2);
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'pricing-plan.retired'", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where tenant_id = ? and action like 'pricing-plan.%'", tenant.value())).isEqualTo(3);
    }

    @Test
    void aPlanVersionCannotBeChangedOrDeleted() {
        World w = worlds.prepaid(0);
        PricingPlan plan = worlds.plan(w.tenant().id(), "calls", worlds.calls(worlds.metric()));
        assertThatThrownBy(() -> appJdbc.update("update pricing_plan_versions set rounding = 'DOWN' where plan_id = ?", plan.id().value())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.jdbc().update("update pricing_plan_versions set rounding = 'DOWN' where plan_id = ?", plan.id().value())).hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.jdbc().update("delete from pricing_plan_versions where plan_id = ?", plan.id().value())).hasMessageContaining("append-only");
    }

    // ---- estimation

    @Test
    void estimatingWritesNothing() {
        World w = worlds.prepaid(10_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "storage", worlds.storage(metric));
        worlds.record(tenant, w.customerId(), metric, "gb", "500", DAY);
        String[] tables = {"outbox", "audit_log", "usage_events", "usage_charges", "usage_ratings", "charges", "ledger_entries", "reservations", "wallets",
            "pricing_plans", "pricing_plan_versions", "pricing_metrics", "webhook_deliveries"};
        Map<String, Long> before = new java.util.TreeMap<>();
        for (String table : tables) {
            before.put(table, count("select count(*) from " + table + " where tenant_id = ?", tenant.value()));
        }
        long balanceBefore = balance(w);

        var result = scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), null, null, Map.of(metric, new Measure(d("1100"), 1)))));

        assertThat(result.quote().amount().minorUnits()).isEqualTo(1950);
        Map<String, Long> after = new java.util.TreeMap<>();
        for (String table : tables) {
            after.put(table, count("select count(*) from " + table + " where tenant_id = ?", tenant.value()));
        }
        assertThat(after).isEqualTo(before);
        assertThat(balance(w)).isEqualTo(balanceBefore);
    }

    @Test
    void anEstimateIsRefusedForAMetricThePlanDoesNotPrice() {
        World w = worlds.prepaid(0);
        PricingPlan plan = worlds.plan(w.tenant().id(), "calls", worlds.calls(worlds.metric()));
        assertThatThrownBy(() -> scopes.inTenant(w.tenant().id(), () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), null, null,
                Map.of("other", new Measure(d("1"), 1)))))).hasMessageContaining("not priced");
    }

    @Test
    void anEstimateUsesTheVersionInForceAtTheGivenMoment() {
        World w = worlds.prepaid(0);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "calls", new EventBased(metric, "call", d("1")));
        Instant later = Instant.now().plus(10, ChronoUnit.DAYS);
        scopes.inTenant(tenant, () -> planAdmin.publishVersion(AUDIT, plan.id(), new ManagePlanUseCase.Terms(
                new EventBased(metric, "call", d("2")), null, null, null, null, later, null)));

        Map<String, Measure> usage = Map.of(metric, new Measure(d("10"), 10));
        assertThat(scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), null, null, usage))).quote().amount().minorUnits()).isEqualTo(10);
        assertThat(scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), null, later.plusSeconds(1), usage))).quote().amount().minorUnits()).isEqualTo(20);
        assertThat(scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), 2, null, usage))).version().number()).isEqualTo(2);
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), 3, null, usage)))).isInstanceOf(NotFoundException.class);
    }

    // ---- charging usage

    @Test
    void chargingUsageEqualsTheEstimateAndPostsOneLedgerEntry() {
        World w = worlds.prepaid(100_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "storage", worlds.storage(metric));
        worlds.record(tenant, w.customerId(), metric, "gb", "600", DAY.plusSeconds(10));
        worlds.record(tenant, w.customerId(), metric, "gb", "500", DAY.plusSeconds(20));

        var quoted = scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), null, null, Map.of(metric, new Measure(d("1100"), 2)))));
        var result = charge(w, window(w, plan, null));

        assertThat(result.outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.CHARGED);
        assertThat(result.replayed()).isFalse();
        UsageRating rating = result.rating();
        assertThat(rating.amount()).as("the estimate and the charge agree").isEqualTo(quoted.quote().amount());
        assertThat(rating.amount().minorUnits()).isEqualTo(1950);
        assertThat(rating.planId()).isEqualTo(plan.id());
        assertThat(rating.planVersion()).isEqualTo(1);
        assertThat(rating.usageCount()).isEqualTo(2);
        assertThat(rating.breakdown()).hasSize(3);

        assertThat(balance(w)).isEqualTo(100_000 - 1950);
        assertThat(count("select count(*) from ledger_entries where tenant_id = ? and type = 'DEBIT' and charge_id = ?", tenant.value(), rating.chargeId())).isEqualTo(1);
        assertThat(count("select count(*) from charges where tenant_id = ? and id = ? and status = 'SUCCEEDED' and source = 'USAGE'", tenant.value(), rating.chargeId())).isEqualTo(1);
        assertThat(owner.jdbc().queryForObject("select reference from charges where id = ?", String.class, rating.chargeId())).isEqualTo("plan:" + plan.code() + "@v1");
        assertThat(count("select count(*) from usage_charges where tenant_id = ? and rating_id = ?", tenant.value(), rating.id().value())).isEqualTo(2);
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'usage.rated'", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'charge.succeeded'", tenant.value())).isEqualTo(1);
    }

    @Test
    void chargingTheSameUsageTwiceProducesOneLedgerEntry() {
        World w = worlds.prepaid(100_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "storage", worlds.storage(metric));
        worlds.record(tenant, w.customerId(), metric, "gb", "600", DAY.plusSeconds(10));

        var first = charge(w, window(w, plan, null));
        var again = charge(w, window(w, plan, null));
        var wider = charge(w, new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), plan.id(), null, DAY.minus(5, ChronoUnit.DAYS), DAY.plus(5, ChronoUnit.DAYS)));

        assertThat(again.replayed()).isTrue();
        assertThat(wider.replayed()).as("a wider window finds the same usage already rated").isTrue();
        assertThat(again.rating().id()).isEqualTo(first.rating().id());
        assertThat(wider.rating().id()).isEqualTo(first.rating().id());
        assertThat(count("select count(*) from ledger_entries where tenant_id = ? and type = 'DEBIT'", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from charges where tenant_id = ?", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from usage_ratings where tenant_id = ?", tenant.value())).isEqualTo(1);
        assertThat(balance(w)).as("600 gb: 100 free, 500 at 2").isEqualTo(100_000 - 1000);
    }

    @Test
    void lateUsageInAnAlreadyRatedWindowIsRatedOnItsOwn() {
        World w = worlds.prepaid(100_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "calls", new EventBased(metric, "call", d("1")));
        worlds.record(tenant, w.customerId(), metric, "call", "10", DAY.plusSeconds(10));
        var first = charge(w, window(w, plan, null));
        worlds.record(tenant, w.customerId(), metric, "call", "5", DAY.plusSeconds(20));
        var second = charge(w, window(w, plan, null));

        assertThat(first.rating().amount().minorUnits()).isEqualTo(10);
        assertThat(second.replayed()).isFalse();
        assertThat(second.rating().id()).isNotEqualTo(first.rating().id());
        assertThat(second.rating().amount().minorUnits()).as("only the new event").isEqualTo(5);
        assertThat(second.rating().usageCount()).isEqualTo(1);
        assertThat(balance(w)).isEqualTo(100_000 - 15);
        assertThat(count("select count(*) from usage_charges where tenant_id = ?", tenant.value())).isEqualTo(2);
    }

    @Test
    void aNewPlanVersionNeverChangesAChargeAlreadyMade() {
        World w = worlds.prepaid(100_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "calls", new EventBased(metric, "call", d("1")));
        worlds.record(tenant, w.customerId(), metric, "call", "10", DAY.plusSeconds(10));
        var first = charge(w, window(w, plan, null));

        scopes.inTenant(tenant, () -> planAdmin.publishVersion(AUDIT, plan.id(), new ManagePlanUseCase.Terms(
                new EventBased(metric, "call", d("100")), null, null, null, null, DAY.plusSeconds(30), null)));
        var replay = charge(w, window(w, plan, null));
        UsageRatingView stored = scopes.inTenant(tenant, () -> ratings.get(first.rating().id().value()));

        assertThat(replay.rating().planVersion()).isEqualTo(1);
        assertThat(replay.rating().amount().minorUnits()).isEqualTo(10);
        assertThat(stored.planVersion()).isEqualTo(1);
        assertThat(stored.breakdown().stream().map(PriceLineView::amount).toList())
                .isEqualTo(first.rating().breakdown().stream().map(dev.ngb.billing.pricing.domain.rule.PriceLine::amount).toList());
        assertThat(balance(w)).isEqualTo(100_000 - 10);

        worlds.record(tenant, w.customerId(), metric, "call", "1", DAY.plusSeconds(50));
        var next = charge(w, window(w, plan, null));
        assertThat(next.rating().planVersion()).as("new usage is rated under the version in force").isEqualTo(2);
        assertThat(next.rating().amount().minorUnits()).isEqualTo(100);
    }

    @Test
    void aChargeThatTheWalletCannotPayIsRecordedAndRetriesAfterATopUp() {
        World w = worlds.prepaid(100);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "storage", worlds.storage(metric));
        worlds.record(tenant, w.customerId(), metric, "gb", "1100", DAY.plusSeconds(10));

        var failed = charge(w, window(w, plan, null));
        assertThat(failed.outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.FAILED);
        assertThat(failed.rating().failureReason()).isEqualTo("insufficient_funds");
        assertThat(balance(w)).isEqualTo(100);
        assertThat(count("select count(*) from usage_charges where tenant_id = ?", w.tenant().id().value())).as("the usage stays free to retry").isZero();
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'charge.failed'", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'usage.rated'", tenant.value())).isZero();
        assertThat(charge(w, window(w, plan, null)).rating().id()).as("repeating while still broke records another failed attempt").isNotEqualTo(failed.rating().id());

        scopes.inTenant(tenant, () -> credit.credit(new CreditCommand(w.wallet().id(), Money.of(5000, w.wallet().currency()), "topup:1", null, "topup-" + UUID.randomUUID(), "system/test")));
        var paid = charge(w, window(w, plan, null));
        assertThat(paid.outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.CHARGED);
        assertThat(paid.rating().attempt()).isEqualTo(3);
        assertThat(balance(w)).isEqualTo(100 + 5000 - 1950);
        var replay = charge(w, window(w, plan, null));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.rating().id()).isEqualTo(paid.rating().id());
        assertThat(count("select count(*) from ledger_entries where tenant_id = ? and type = 'DEBIT'", tenant.value())).isEqualTo(1);
    }

    @Test
    void aPostpaidWalletIsChargedIntoDebtWithinItsLimit() {
        World w = worlds.postpaid(10_000);
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(w.tenant().id(), "storage", worlds.storage(metric));
        worlds.record(w.tenant().id(), w.customerId(), metric, "gb", "1100", DAY.plusSeconds(10));
        assertThat(charge(w, window(w, plan, null)).outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.CHARGED);
        assertThat(balance(w)).isEqualTo(-1950);
    }

    @Test
    void usageThatRoundsToNothingIsAccountedForWithoutACharge() {
        World w = worlds.prepaid(1000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "calls", new EventBased(metric, "call", d("0.01")));
        worlds.record(tenant, w.customerId(), metric, "call", "20", DAY.plusSeconds(10)); // 0.2 -> 0

        var result = charge(w, window(w, plan, null));
        assertThat(result.outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.NO_CHARGE);
        assertThat(result.rating().chargeId()).isNull();
        assertThat(count("select count(*) from charges where tenant_id = ?", tenant.value())).isZero();
        assertThat(count("select count(*) from usage_charges where tenant_id = ?", tenant.value())).isEqualTo(1);
        assertThat(charge(w, window(w, plan, null)).replayed()).isTrue();
        assertThat(balance(w)).isEqualTo(1000);
    }

    @Test
    void noUsageInThePeriodChargesNothing() {
        World w = worlds.prepaid(1000);
        PricingPlan plan = worlds.plan(w.tenant().id(), "calls", worlds.calls(worlds.metric()));
        var result = charge(w, window(w, plan, null));
        assertThat(result.outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.NO_USAGE);
        assertThat(result.rating()).isNull();
        assertThat(count("select count(*) from usage_ratings where tenant_id = ?", w.tenant().id().value())).isZero();
    }

    @Test
    void aChargeIsRefusedForTheWrongWalletCurrencyOrPlan() {
        World w = worlds.prepaid(1000);
        World other = worlds.prepaid(1000);
        TenantId tenant = w.tenant().id();
        PricingPlan plan = worlds.plan(tenant, "calls", worlds.calls(worlds.metric()));
        assertThatThrownBy(() -> charge(w, new ChargeFromUsageUseCase.Command(other.customerId(), w.wallet().id(), plan.id(), null, DAY, DAY.plusSeconds(60))))
                .hasMessageContaining("another customer");
        assertThatThrownBy(() -> charge(w, new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), new PricingPlanId(UUID.randomUUID()), null, DAY, DAY.plusSeconds(60))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> charge(w, new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), plan.id(), null, DAY.plusSeconds(60), DAY)))
                .hasMessageContaining("before");
        assertThatThrownBy(() -> charge(w, new ChargeFromUsageUseCase.Command(w.customerId(), w.wallet().id(), plan.id(), 7, DAY, DAY.plusSeconds(60))))
                .isInstanceOf(NotFoundException.class);
        PricingPlan subscription = worlds.plan(tenant, "pro", worlds.pro(), dev.ngb.billing.pricing.api.plan.BillingCycle.MONTHLY, null, null);
        assertThatThrownBy(() -> charge(w, window(w, subscription, null))).hasMessageContaining("no metered usage");
        assertThatThrownBy(() -> charge(other, new ChargeFromUsageUseCase.Command(other.customerId(), other.wallet().id(), plan.id(), null, DAY, DAY.plusSeconds(60))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void concurrentRequestsToChargeTheSameUsageChargeItOnce() throws Exception {
        World w = worlds.prepaid(100_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "storage", worlds.storage(metric));
        for (int i = 0; i < 5; i++) {
            worlds.record(tenant, w.customerId(), metric, "gb", "200", DAY.plusSeconds(10 + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<CompletableFuture<ChargeFromUsageUseCase.Result>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> charge(w, window(w, plan, null)), pool));
            }
            Set<UUID> ratingIds = new HashSet<>();
            for (var f : futures) {
                ratingIds.add(f.get(60, TimeUnit.SECONDS).rating().id().value());
            }
            assertThat(ratingIds).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from ledger_entries where tenant_id = ? and type = 'DEBIT'", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from usage_ratings where tenant_id = ?", tenant.value())).isEqualTo(1);
        assertThat(count("select count(*) from usage_charges where tenant_id = ?", tenant.value())).isEqualTo(5);
        assertThat(balance(w)).as("1000 gb: the first 100 free, 900 at 2").isEqualTo(100_000 - 1800);
    }

    @Test
    void aFailureAfterTheMoneyMovedRollsBackTheChargeTheRatingAndTheEvents() {
        World w = worlds.prepaid(100_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "calls", new EventBased(metric, "call", d("1")));
        worlds.record(tenant, w.customerId(), metric, "call", "10", DAY.plusSeconds(10));
        String trigger = "test_fail_link_" + UUID.randomUUID().toString().replace("-", "");
        owner.jdbc().execute("create or replace function test_fail_link() returns trigger language plpgsql as $$ begin raise exception 'injected failure'; end $$");
        owner.jdbc().execute("create trigger " + trigger + " before insert on usage_charges for each row when (new.tenant_id = '" + tenant + "') execute function test_fail_link()");
        try {
            assertThatThrownBy(() -> charge(w, window(w, plan, null))).hasMessageContaining("injected failure");
        } finally {
            owner.jdbc().execute("drop trigger " + trigger + " on usage_charges");
        }

        assertThat(balance(w)).as("the wallet was not touched").isEqualTo(100_000);
        assertThat(count("select count(*) from charges where tenant_id = ?", tenant.value())).isZero();
        assertThat(count("select count(*) from ledger_entries where tenant_id = ? and type = 'DEBIT'", tenant.value())).isZero();
        assertThat(count("select count(*) from usage_ratings where tenant_id = ?", tenant.value())).isZero();
        assertThat(count("select count(*) from outbox where tenant_id = ? and type in ('charge.succeeded', 'usage.rated')", tenant.value())).isZero();

        assertThat(charge(w, window(w, plan, null)).outcome()).as("and the same request then succeeds, once").isEqualTo(ChargeFromUsageUseCase.Outcome.CHARGED);
        assertThat(balance(w)).isEqualTo(100_000 - 10);
    }

    // ---- reserving an estimate

    @Test
    void reservingAnEstimateHoldsExactlyTheEstimatedAmountAndIsIdempotent() {
        World w = worlds.prepaid(10_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "vm", worlds.vm(metric));
        String key = "reserve-" + UUID.randomUUID();
        ReserveFromEstimateUseCase.Command command = new ReserveFromEstimateUseCase.Command(
                w.wallet().id(), plan.id(), null, null, Map.of(metric, new Measure(d("4"), 1)), Duration.ofMinutes(10), "vm-1", key);

        var first = scopes.inTenant(tenant, () -> reserve.reserve(command));
        var again = scopes.inTenant(tenant, () -> reserve.reserve(command));

        assertThat(first.estimate().quote().amount().minorUnits()).isEqualTo(14);
        assertThat(first.reservation().amount()).isEqualTo(first.estimate().quote().amount());
        assertThat(again.reservation().replayed()).isTrue();
        assertThat(again.reservation().id()).isEqualTo(first.reservation().id());
        assertThat(scopes.inTenant(tenant, () -> wallets.wallet(w.wallet().id())).held().minorUnits()).isEqualTo(14);
        assertThat(count("select count(*) from reservations where tenant_id = ?", tenant.value())).isEqualTo(1);
    }

    @Test
    void reservingMoreThanTheWalletHoldsIsRefusedAndAZeroEstimateHasNothingToHold() {
        World w = worlds.prepaid(10);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "vm", worlds.vm(metric));
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> reserve.reserve(new ReserveFromEstimateUseCase.Command(
                w.wallet().id(), plan.id(), null, null, Map.of(metric, new Measure(d("100"), 1)), Duration.ofMinutes(10), null, "k1"))))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> scopes.inTenant(tenant, () -> reserve.reserve(new ReserveFromEstimateUseCase.Command(
                w.wallet().id(), plan.id(), null, null, Map.of(), Duration.ofMinutes(10), null, "k2")))).hasMessageContaining("zero");
        assertThat(count("select count(*) from reservations where tenant_id = ?", tenant.value())).isZero();
    }

    @Test
    void capturingAReservedEstimateChargesTheActualCostAndReleasesTheRest() {
        World w = worlds.prepaid(10_000);
        TenantId tenant = w.tenant().id();
        String metric = worlds.metric();
        PricingPlan plan = worlds.plan(tenant, "vm", worlds.vm(metric));
        var held = scopes.inTenant(tenant, () -> reserve.reserve(new ReserveFromEstimateUseCase.Command(
                w.wallet().id(), plan.id(), null, null, Map.of(metric, new Measure(d("10"), 1)), Duration.ofMinutes(10), "vm-2", "cap-1")));
        assertThat(held.reservation().amount().minorUnits()).isEqualTo(35);

        // The resource actually ran for 4 hours: price that, and capture it against the reservation.
        var actual = scopes.inTenant(tenant, () -> estimate.estimate(new EstimateCostUseCase.Request(plan.id(), null, null, Map.of(metric, new Measure(d("4"), 1)))));
        var captured = scopes.inTenant(tenant, () -> chargeWallet.charge(new dev.ngb.billing.money.api.charge.ChargeCommand(
                null, held.reservation().id(), actual.quote().amount(), null, "vm-2", "capture-" + UUID.randomUUID())));

        assertThat(captured.status()).isEqualTo(ChargeStatus.SUCCEEDED);
        assertThat(balance(w)).isEqualTo(10_000 - 14);
        assertThat(scopes.inTenant(tenant, () -> wallets.wallet(w.wallet().id())).held().minorUnits()).as("the unused remainder was released").isZero();
    }
}
