package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.invoicing.application.subscription.command.CancelSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.application.plan.command.ManagePlanUseCase;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.UncommittedRace;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Creating and cancelling subscriptions: the plan, the wallet and the idempotency key decide what is allowed. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class SubscriptionIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired ManagePlanUseCase planAdmin;
    @Autowired CreateSubscriptionUseCase create;
    @Autowired CancelSubscriptionUseCase cancel;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired UnitOfWork unitOfWork;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    private UUID proPlan(World world) {
        return pricing.plan(world.tenant().id(), "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().value();
    }

    private CreateSubscriptionUseCase.Result subscribe(World world, UUID plan, String key) {
        return scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(world.wallet().id(), plan, key, AUDIT, null)));
    }

    @Test
    void aSubscriptionIsCreatedActiveAndItsFirstPeriodIsChargedAtOnce() {
        World world = worlds.prepaid(100_000);

        Subscription subscription = subscribe(world, proPlan(world), "sub-1").subscription();

        Subscription reloaded = scopes.inTenant(world.tenant().id(), () -> unitOfWork.transactional(() -> subscriptions.findById(subscription.id()))).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(subscription);
        assertThat(reloaded.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reloaded.cycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(reloaded.periodsCharged()).isEqualTo(1);
        assertThat(reloaded.nextRenewalAt()).isEqualTo(reloaded.periodEnd(0));
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'CREATED'", subscription.id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'RENEWED'", subscription.id().value())).isEqualTo(1);
    }

    @Test
    void theSameKeyReturnsTheSameSubscription() {
        World world = worlds.prepaid(100_000);
        UUID plan = proPlan(world);

        var first = subscribe(world, plan, "sub-1");
        var again = subscribe(world, plan, "sub-1");

        assertThat(again.replayed()).isTrue();
        assertThat(again.subscription().id()).isEqualTo(first.subscription().id());
        assertThat(owner.count("select count(*) from subscriptions where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
        assertThatThrownBy(() -> subscribe(world, proPlan(world), "sub-1")).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aCreateArrivingWhileTheFirstIsUncommittedWaitsAndReplaysIt() throws Exception {
        World world = worlds.prepaid(100_000);
        UUID plan = proPlan(world);

        var race = UncommittedRace.run(unitOfWork, scopes, owner, world.tenant().id(), "insert into %\"subscriptions\"%",
                () -> subscribe(world, plan, "held-sub"), () -> subscribe(world, plan, "held-sub"));

        assertThat(race.first().replayed()).isFalse();
        assertThat(race.second().replayed()).as("the second insert found the key taken and replayed").isTrue();
        UUID id = race.first().subscription().id().value();
        assertThat(race.second().subscription().id().value()).isEqualTo(id);
        assertThat(owner.count("select count(*) from subscriptions where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from charges where reference = ?", "subscription:" + id + ":0")).as("one first-period charge").isEqualTo(1);
    }

    @Test
    void concurrentCreatesWithOneKeyGiveOneSubscriptionAndOneFirstPeriodCharge() throws Exception {
        World world = worlds.prepaid(100_000);
        UUID plan = proPlan(world);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> subscribe(world, plan, "parallel-sub"), pool))
                    .toList();
            Set<UUID> ids = new HashSet<>();
            long firsts = 0;
            long replays = 0;
            for (var future : futures) {
                var result = future.get(30, TimeUnit.SECONDS);
                ids.add(result.subscription().id().value());
                if (result.replayed()) {
                    replays++;
                } else {
                    firsts++;
                }
            }
            assertThat(ids).hasSize(1);
            assertThat(firsts).isEqualTo(1);
            assertThat(replays).isEqualTo(7);
            UUID id = ids.iterator().next();
            assertThat(owner.count("select count(*) from subscriptions where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
            assertThat(owner.count("select count(*) from charges where reference = ?", "subscription:" + id + ":0")).isEqualTo(1);
            assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'CREATED'", id)).isEqualTo(1);
            assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'RENEWED'", id)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aPlanThatCannotBeSubscribedToIsRefused() {
        World world = worlds.prepaid(100_000);
        UUID usageOnly = pricing.plan(world.tenant().id(), "calls", pricing.calls(pricing.metric())).id().value();
        assertThatThrownBy(() -> subscribe(world, usageOnly, "sub-1")).isInstanceOf(ConflictException.class);

        var pro = pricing.plan(world.tenant().id(), "pro", pricing.pro(), BillingCycle.MONTHLY, null, null);
        scopes.inTenant(world.tenant().id(), () -> planAdmin.retire(PricingTestWorld.AUDIT, pro.id()));
        assertThatThrownBy(() -> subscribe(world, pro.id().value(), "sub-2")).isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> subscribe(world, UUID.randomUUID(), "sub-3")).isInstanceOf(ConflictException.class);
    }

    @Test
    void aWalletOfAnotherTenantOrAnUnknownWalletIsNotFound() {
        World world = worlds.prepaid(100_000);
        World stranger = worlds.prepaid(100_000);
        UUID plan = proPlan(world);

        assertThatThrownBy(() -> scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(stranger.wallet().id(), plan,
                "sub-1", AUDIT, null)))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(new WalletId(UUID.randomUUID()),
                plan, "sub-2", AUDIT, null)))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancellingBeforeAnyPeriodIsPaidEndsItAtOnceAndIsRepeatable() {
        // An empty prepaid wallet cannot pay the first period, so nothing paid is running.
        World world = worlds.prepaid(0);
        Subscription subscription = subscribe(world, proPlan(world), "sub-1").subscription();

        Subscription canceled = scopes.inTenant(world.tenant().id(), () -> cancel.execute(new CancelSubscriptionUseCase.Command(subscription.id(), AUDIT, null)));
        Subscription again = scopes.inTenant(world.tenant().id(), () -> cancel.execute(new CancelSubscriptionUseCase.Command(subscription.id(), AUDIT, null)));

        assertThat(canceled.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(again.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'CANCELED'", subscription.id().value())).isEqualTo(1);
        assertThat(owner.jdbc().queryForObject("select next_renewal_at from subscriptions where id = ?", java.sql.Timestamp.class, subscription.id().value())).isNull();
    }
}
