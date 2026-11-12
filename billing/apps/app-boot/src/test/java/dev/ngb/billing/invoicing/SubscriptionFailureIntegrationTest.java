package dev.ngb.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.billing.Application;
import dev.ngb.billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.billing.invoicing.application.subscription.command.RenewSubscriptionUseCase;
import dev.ngb.billing.invoicing.domain.subscription.Subscription;
import dev.ngb.billing.money.MoneyTestWorld;
import dev.ngb.billing.money.MoneyTestWorld.World;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.pricing.PricingTestWorld;
import dev.ngb.billing.pricing.api.plan.BillingCycle;
import dev.ngb.billing.pricing.application.plan.command.ManagePlanUseCase;
import dev.ngb.billing.support.OwnerDatabase;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A period that cannot be paid: the subscription is past due, the Tenant is told on every failed attempt, retries
 * are bounded, and a later success returns it to active with the whole story in its history.
 */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class SubscriptionFailureIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");
    private static final long PRICE = 2_900;
    private static final int MAX_ATTEMPTS = 5;

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired ManagePlanUseCase planAdmin;
    @Autowired CreateSubscriptionUseCase create;
    @Autowired RenewSubscriptionUseCase renew;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};

    private final JsonMapper json = JsonMapper.builder().build();

    private Subscription subscribe(World world) {
        UUID plan = pricing.plan(world.tenant().id(), "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().value();
        return scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(world.wallet().id(), plan,
                "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
    }

    private RenewSubscriptionUseCase.Report runDue(World world) {
        return scopes.inTenant(world.tenant().id(), renew::runDue);
    }

    private void due(Subscription subscription) {
        owner.jdbc().update("update subscriptions set next_renewal_at = now() - interval '1 second' where id = ? and next_renewal_at is not null", subscription.id().value());
    }

    private Map<String, Object> row(Subscription subscription) {
        return owner.jdbc().queryForMap("select status, periods_charged, renewal_attempts, next_renewal_at from subscriptions where id = ?", subscription.id().value());
    }

    private List<Map<String, Object>> notices(World world, String type) {
        return owner.jdbc().queryForList("select payload::text as payload from outbox where tenant_id = ? and type = ? order by created_at, id", world.tenant().id().value(), type)
                .stream().map(r -> json.readValue((String) r.get("payload"), PAYLOAD)).toList();
    }

    @Test
    void aRefusedPeriodMakesTheSubscriptionPastDueAndTellsTheTenant() {
        World world = worlds.prepaid(0);

        Subscription subscription = subscribe(world);

        assertThat(row(subscription)).containsEntry("status", "PAST_DUE").containsEntry("renewal_attempts", 1).containsEntry("periods_charged", 0);
        assertThat(row(subscription).get("next_renewal_at")).isNotNull();
        assertThat(notices(world, "subscription.payment_failed")).singleElement().satisfies(notice -> {
            assertThat(notice).containsEntry("subscriptionId", subscription.id().toString()).containsEntry("periodNumber", 0)
                    .containsEntry("reason", "insufficient_funds").containsEntry("attempt", 1).containsEntry("willRetry", true);
            assertThat(notice).containsKey("nextAttemptAt");
        });
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'PAYMENT_FAILED'", subscription.id().value())).isEqualTo(1);
        assertThat(notices(world, "subscription.renewed")).isEmpty();
    }

    @Test
    void retriesAreBoundedAndTheLastNoticeSaysBillingStoppedTrying() {
        World world = worlds.prepaid(0);
        Subscription subscription = subscribe(world);

        for (int attempt = 2; attempt <= MAX_ATTEMPTS; attempt++) {
            due(subscription);
            assertThat(runDue(world).failed()).isEqualTo(1);
        }

        assertThat(row(subscription)).containsEntry("status", "PAST_DUE").containsEntry("renewal_attempts", MAX_ATTEMPTS);
        assertThat(row(subscription).get("next_renewal_at")).as("no automatic attempt is left").isNull();
        List<Map<String, Object>> failures = notices(world, "subscription.payment_failed");
        assertThat(failures).hasSize(MAX_ATTEMPTS);
        assertThat(failures.stream().map(n -> n.get("attempt"))).containsExactly(1, 2, 3, 4, 5);
        assertThat(failures.get(MAX_ATTEMPTS - 1)).containsEntry("willRetry", false).doesNotContainKey("nextAttemptAt");

        due(subscription);
        assertThat(runDue(world).failed()).as("the job does not keep trying").isZero();
        assertThat(notices(world, "subscription.payment_failed")).hasSize(MAX_ATTEMPTS);
    }

    @Test
    void eachRetryWaitsLongerThanTheLast() {
        World world = worlds.prepaid(0);
        Subscription subscription = subscribe(world);
        Instant first = ((java.sql.Timestamp) row(subscription).get("next_renewal_at")).toInstant();
        Duration firstWait = Duration.between(Instant.now(), first);

        due(subscription);
        runDue(world);
        Instant second = ((java.sql.Timestamp) row(subscription).get("next_renewal_at")).toInstant();
        Duration secondWait = Duration.between(Instant.now(), second);

        assertThat(firstWait).isBetween(Duration.ofMinutes(45), Duration.ofMinutes(75));
        assertThat(secondWait).isGreaterThan(firstWait.plusMinutes(30));
    }

    @Test
    void aFailureThenASuccessEndsActiveWithBothRecordedAndBothAnnounced() {
        World world = worlds.prepaid(0);
        Subscription subscription = subscribe(world);

        worlds.credit(world.tenant(), world.wallet(), 10_000);
        due(subscription);
        assertThat(runDue(world).renewed()).isEqualTo(1);

        assertThat(row(subscription)).containsEntry("status", "ACTIVE").containsEntry("periods_charged", 1).containsEntry("renewal_attempts", 0);
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'PAYMENT_FAILED'", subscription.id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'RENEWED'", subscription.id().value())).isEqualTo(1);
        assertThat(notices(world, "subscription.payment_failed")).hasSize(1);
        assertThat(notices(world, "subscription.renewed")).singleElement().satisfies(n -> assertThat(n).containsEntry("periodNumber", 0));
        assertThat(owner.jdbc().queryForObject("select balance from wallets where id = ?", Long.class, world.wallet().id().value())).isEqualTo(10_000 - PRICE);
    }

    @Test
    void aPostpaidWalletAtItsCreditLimitFailsTheSameWay() {
        World world = worlds.postpaid(1_000);

        Subscription subscription = subscribe(world);

        assertThat(row(subscription)).containsEntry("status", "PAST_DUE");
        assertThat(notices(world, "subscription.payment_failed")).singleElement().satisfies(n -> assertThat(n).containsEntry("reason", "insufficient_funds"));
    }

    @Test
    void aPeriodWithNoPriceInForceFailsAndSaysSo() {
        World world = worlds.prepaid(100_000);
        Instant until = Instant.now().plus(Duration.ofDays(10));
        UUID plan = scopes.inTenant(world.tenant().id(), () -> planAdmin.create(PricingTestWorld.AUDIT, "ends-" + UUID.randomUUID().toString().substring(0, 8), "Ends soon",
                PricingTestWorld.USD, new ManagePlanUseCase.Terms(pricing.pro(), BillingCycle.MONTHLY, null, null, null, PricingTestWorld.VALID_FROM, until))).id().value();
        Subscription subscription = scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(world.wallet().id(), plan,
                "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
        assertThat(row(subscription)).containsEntry("status", "ACTIVE").containsEntry("periods_charged", 1);

        owner.jdbc().update("update subscriptions set anchor = anchor + interval '2 months', next_renewal_at = now() - interval '1 second' where id = ?", subscription.id().value());
        runDue(world);

        assertThat(row(subscription)).containsEntry("status", "PAST_DUE").containsEntry("periods_charged", 1);
        assertThat(notices(world, "subscription.payment_failed")).singleElement().satisfies(n -> {
            assertThat(n).containsEntry("reason", "price_unavailable").containsEntry("periodNumber", 1);
            assertThat(n).doesNotContainKey("amount");
        });
    }
}
