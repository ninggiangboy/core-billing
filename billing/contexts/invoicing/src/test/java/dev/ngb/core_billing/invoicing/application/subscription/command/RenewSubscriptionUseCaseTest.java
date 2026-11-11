package dev.ngb.core_billing.invoicing.application.subscription.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.invoicing.application.subscription.service.RenewalSettings;
import dev.ngb.core_billing.invoicing.application.subscription.port.SubscriptionRepository;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.api.plan.PeriodPrice;
import dev.ngb.core_billing.pricing.api.plan.PlanPricing;
import dev.ngb.core_billing.pricing.api.plan.SubscriptionTerms;
import dev.ngb.core_billing.sharedkernel.Backoff;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The renewal logic against in-memory ports only: it depends on what the application layer declares and on nothing else. */
class RenewSubscriptionUseCaseTest {

    private static final Currency USD = Currency.of("USD");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final Map<SubscriptionId, Subscription> store = new LinkedHashMap<>();
    private final List<SubscriptionEvent> history = new ArrayList<>();
    private final List<DomainEvent> published = new ArrayList<>();
    private final List<ChargeCommand> charged = new ArrayList<>();
    private final UUID brokenPlan = UUID.randomUUID();

    private final SubscriptionRepository repository = new SubscriptionRepository() {
        @Override
        public void add(Subscription subscription) {
            store.put(subscription.id(), subscription);
        }

        @Override
        public void update(Subscription subscription) {
            store.put(subscription.id(), subscription);
        }

        @Override
        public Optional<Subscription> findById(SubscriptionId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Subscription> lockById(SubscriptionId id) {
            return findById(id);
        }

        @Override
        public Optional<Subscription> lockNextDue(Instant now, Set<UUID> excluding) {
            return store.values().stream()
                    .filter(s -> s.dueAt(now))
                    .filter(s -> !excluding.contains(s.id().value()))
                    .min(Comparator.comparing(Subscription::nextRenewalAt));
        }

        @Override
        public Optional<Subscription> findByIdempotencyKey(String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void appendEvent(SubscriptionEvent event) {
            history.add(event);
        }
    };

    private final PlanPricing pricing = new PlanPricing() {
        @Override
        public Optional<SubscriptionTerms> subscriptionTerms(UUID planId, Instant at) {
            return Optional.empty();
        }

        @Override
        public Optional<PeriodPrice> periodPrice(UUID planId, Instant at) {
            if (planId.equals(brokenPlan)) {
                throw new IllegalStateException("the pricing store is broken for this plan");
            }
            return Optional.of(new PeriodPrice(planId, "pro", 1, BillingCycle.MONTHLY, Money.of(2_900, USD)));
        }
    };

    private final UnitOfWork passThrough = new UnitOfWork() {
        @Override
        public <T> T transactional(Supplier<T> work) {
            return work.get();
        }
    };

    private boolean walletContended;
    private UUID contendedWallet;

    private final ChargeWallet charging = command -> {
        if (walletContended || (contendedWallet != null && contendedWallet.equals(command.walletId().value()))) {
            throw new IllegalStateException("the wallet lock timed out",
                    new SQLException("canceling statement due to lock timeout", "55P03"));
        }
        charged.add(command);
        return new ChargeResult(new ChargeId(UUID.randomUUID()), ChargeStatus.SUCCEEDED, null, command.amount(), new LedgerEntryId(UUID.randomUUID()), false);
    };

    private final RenewSubscriptionUseCase useCase = new RenewSubscriptionUseCase(passThrough, repository, pricing, charging, published::add,
            new RenewalSettings(10, 3, new Backoff(Duration.ofMinutes(1), Duration.ofHours(1), 0.0)), () -> 0.5, UUID::randomUUID, () -> NOW);

    private Subscription due(UUID plan, Instant dueAt) {
        Subscription s = Subscription.create(new SubscriptionId(UUID.randomUUID()), TENANT, new CustomerId(UUID.randomUUID()), new WalletId(UUID.randomUUID()), USD, plan,
                "pro", BillingCycle.MONTHLY, "key-" + UUID.randomUUID(), "hash", dueAt);
        store.put(s.id(), s);
        return s;
    }

    @Test
    void aSubscriptionThatThrowsIsSetAsideSoTheOnesBehindItAreStillRenewed() {
        Subscription broken = due(brokenPlan, NOW.minusSeconds(60));
        Subscription healthy = due(UUID.randomUUID(), NOW.minusSeconds(30));

        RenewSubscriptionUseCase.Report report = useCase.runDue();

        assertThat(report.renewed()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(healthy.periodsCharged()).isEqualTo(1);
        assertThat(healthy.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(broken.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(broken.renewalAttempts()).isEqualTo(1);
        assertThat(broken.nextRenewalAt()).isAfter(NOW);
        assertThat(published).extracting(DomainEvent::type).containsExactlyInAnyOrder("subscription.payment_failed", "subscription.renewed");
        assertThat(published.stream().filter(e -> e.type().equals("subscription.payment_failed")).findFirst().orElseThrow().payload()).containsEntry("reason", "renewal_error");
    }

    @Test
    void aLockWaitThatTimesOutIsNotAFailedAttempt() {
        Subscription s = due(UUID.randomUUID(), NOW.minusSeconds(60));
        walletContended = true;

        RenewSubscriptionUseCase.Report contended = useCase.runDue();

        assertThat(contended).as("nothing was charged and no attempt was taken").isEqualTo(new RenewSubscriptionUseCase.Report(0, 0, 0));
        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.renewalAttempts()).isZero();
        assertThat(s.nextRenewalAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(history).isEmpty();
        assertThat(published).isEmpty();

        walletContended = false;
        assertThat(useCase.runDue().renewed()).as("the next tick finds the period still due").isEqualTo(1);
        assertThat(s.periodsCharged()).isEqualTo(1);
        assertThat(s.renewalAttempts()).isZero();
    }

    @Test
    void aContendedSubscriptionDoesNotStarveTheOnesBehindIt() {
        Subscription contended = due(UUID.randomUUID(), NOW.minusSeconds(60));
        Subscription healthy = due(UUID.randomUUID(), NOW.minusSeconds(30));
        contendedWallet = contended.walletId().value();

        RenewSubscriptionUseCase.Report report = useCase.runDue();

        assertThat(report).isEqualTo(new RenewSubscriptionUseCase.Report(1, 0, 0));
        assertThat(healthy.periodsCharged()).as("the subscription behind the busy wallet is still renewed").isEqualTo(1);
        assertThat(contended.periodsCharged()).isZero();
        assertThat(contended.renewalAttempts()).as("the busy wallet costs no attempt").isZero();
    }

    @Test
    void theChargeKeyNamesTheSubscriptionThePeriodAndTheAttempt() {
        Subscription s = due(UUID.randomUUID(), NOW.minusSeconds(60));

        useCase.runDue();

        assertThat(charged).singleElement().satisfies(command -> {
            assertThat(command.idempotencyKey()).isEqualTo("renewal:" + s.id() + ":0:1");
            assertThat(command.reference()).isEqualTo("subscription:" + s.id() + ":0");
        });
        assertThat(useCase.runDue().renewed()).as("not due again until the period ends").isZero();
        assertThat(charged).hasSize(1);
    }
}
