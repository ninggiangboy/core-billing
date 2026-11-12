package dev.ngb.sampletenant.resource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.billing.api.client.BillingPricing;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.billing.api.model.Amount;
import dev.ngb.sampletenant.billing.api.model.Estimate;
import dev.ngb.sampletenant.billing.api.model.PricingPlan;
import dev.ngb.sampletenant.billing.api.model.Reservation;
import dev.ngb.sampletenant.billing.api.model.ReserveResult;
import dev.ngb.sampletenant.billing.api.model.Subscription;
import dev.ngb.sampletenant.billing.api.model.SubscriptionDetail;
import dev.ngb.sampletenant.billing.api.model.UsageInput;
import dev.ngb.sampletenant.billing.api.model.Wallet;
import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.resource.application.port.OrderSubmission;
import dev.ngb.sampletenant.resource.domain.Order;
import dev.ngb.sampletenant.resource.domain.OrderStatus;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import dev.ngb.sampletenant.shared.error.PaymentRequiredException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();

    private final FakeCatalog catalog = new FakeCatalog();
    private final FakeCustomers customers = new FakeCustomers();
    private final FakePricing pricing = new FakePricing();
    private final FakeSubscriptions subscriptions = new FakeSubscriptions();
    private final FakeWallets wallets = new FakeWallets();
    private final CapturingSubmission submission = new CapturingSubmission();
    private final CreateOrderUseCase create = new CreateOrderUseCase(
            catalog, customers, pricing, subscriptions, wallets, submission,
            () -> NOW, () -> UUID.randomUUID(), Duration.ofMinutes(15));

    @Test
    void estimatesReservesAndOnlyThenQueuesTheOrder() {
        Order order = create.execute(new CreateOrderUseCase.Command(USER, PLAN, "120", null));

        assertThat(order.status()).isEqualTo(OrderStatus.QUEUED);
        assertThat(order.reservationId()).isEqualTo("res_1");
        assertThat(order.estimatedMinorUnits()).isEqualTo(1000);
        assertThat(order.metric()).isEqualTo("compute.vcpu_minute");
        assertThat(pricing.estimated).isTrue();
        assertThat(pricing.reserved).isTrue();
        assertThat(submission.orders).containsExactly(order);
    }

    @Test
    void aRefusedReservationLeavesNoOrder() {
        pricing.refuse = true;

        assertThatThrownBy(() -> create.execute(new CreateOrderUseCase.Command(USER, PLAN, "120", null)))
                .isInstanceOf(PaymentRequiredException.class);
        assertThat(submission.orders).isEmpty();
    }

    @Test
    void refusesAnUnmappedCustomerBeforeEstimating() {
        customers.mapped = false;

        assertThatThrownBy(() -> create.execute(new CreateOrderUseCase.Command(USER, PLAN, "120", null)))
                .isInstanceOf(BadRequestException.class);
        assertThat(pricing.estimated).isFalse();
        assertThat(submission.orders).isEmpty();
    }

    @Test
    void refusesAPlanWithoutABillingBinding() {
        catalog.billable = false;

        assertThatThrownBy(() -> create.execute(new CreateOrderUseCase.Command(USER, PLAN, "120", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesWhenNoWalletHoldsThePlanCurrency() {
        wallets.currency = "EUR";

        assertThatThrownBy(() -> create.execute(new CreateOrderUseCase.Command(USER, PLAN, "120", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void refusesARetiredBillingPlan() {
        pricing.status = "RETIRED";

        assertThatThrownBy(() -> create.execute(new CreateOrderUseCase.Command(USER, PLAN, "120", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anEventBasedOrderCarriesWholeEventsToTheEstimate() {
        catalog.model = "EVENT_BASED";

        create.execute(new CreateOrderUseCase.Command(USER, PLAN, "25", null));

        assertThat(pricing.lastUsage).singleElement().satisfies(usage -> {
            assertThat(usage.metric()).isEqualTo("compute.vcpu_minute");
            assertThat(usage.events()).isEqualTo(25L);
        });
    }

    @Test
    void aSubscriptionPlanStartsASubscriptionAndSkipsTheReservation() {
        catalog.model = "SUBSCRIPTION";

        Order order = create.execute(new CreateOrderUseCase.Command(USER, PLAN, "1", null));

        assertThat(order.subscriptionId()).isEqualTo("sub_1");
        assertThat(order.reservationId()).isNull();
        assertThat(order.estimatedMinorUnits()).isZero();
        assertThat(subscriptions.subscribed).isTrue();
        assertThat(pricing.estimated).isFalse();
        assertThat(pricing.reserved).isFalse();
    }

    @Test
    void aHybridPlanAlsoStartsASubscription() {
        catalog.model = "HYBRID";

        Order order = create.execute(new CreateOrderUseCase.Command(USER, PLAN, "3", null));

        assertThat(order.subscriptionId()).isEqualTo("sub_1");
        assertThat(subscriptions.subscribed).isTrue();
    }

    private static final class FakeCatalog implements CatalogLookup {
        private boolean billable = true;
        private String model = "TIME_BASED";

        @Override
        public PlanSnapshot get(UUID id) {
            return new PlanSnapshot(
                    id, "compute-standard", "Compute", "bplan_1", "COMPUTE", model, "compute.vcpu_minute", "vCPU-minute", billable);
        }
    }

    private static final class FakeCustomers implements CustomerDirectory {
        private boolean mapped = true;

        @Override
        public CustomerSnapshot byUserId(UUID userId) {
            return new CustomerSnapshot(UUID.randomUUID(), userId, mapped ? "cus_1" : null, mapped);
        }
    }

    private static final class FakePricing implements BillingPricing {
        private boolean estimated;
        private boolean reserved;
        private boolean refuse;
        private String status = "ACTIVE";
        private List<UsageInput> lastUsage = List.of();

        @Override
        public List<PricingPlan> plans(String status) {
            return List.of();
        }

        @Override
        public PricingPlan plan(String id) {
            return new PricingPlan(id, "compute-standard", "Compute", "TIME_BASED", "USD", status, NOW, null, List.of());
        }

        @Override
        public Estimate estimate(EstimateRequest request) {
            estimated = true;
            lastUsage = request.usage();
            return new Estimate("bplan_1", "compute-standard", 1, new Amount(1000, "USD"), "10.00", "0", List.of());
        }

        @Override
        public ReserveResult reserve(String idempotencyKey, ReserveRequest request) {
            if (refuse) {
                throw new PaymentRequiredException("The wallet cannot cover this amount.");
            }
            reserved = true;
            return new ReserveResult(
                    new Estimate("bplan_1", "compute-standard", 1, new Amount(request.amount(), request.currency()), "10.00", "0", List.of()),
                    new Reservation(
                            "res_1", request.walletId(), "HELD", new Amount(request.amount(), request.currency()), null,
                            NOW.plusSeconds(900), request.reference(), NOW, null, false));
        }
    }

    private static final class FakeSubscriptions implements BillingSubscriptions {
        private boolean subscribed;

        @Override
        public SubscriptionDetail subscribe(String idempotencyKey, String walletId, String planId) {
            subscribed = true;
            return detail();
        }

        @Override
        public SubscriptionDetail subscription(String id) {
            return detail();
        }

        @Override
        public SubscriptionDetail cancel(String id) {
            return detail();
        }

        @Override
        public List<Subscription> subscriptions(String customerId, String walletId, String status, int limit) {
            return List.of(detail().subscription());
        }

        private static SubscriptionDetail detail() {
            return new SubscriptionDetail(
                    new Subscription("sub_1", "cus_1", "wal_1", "bplan_1", "managed-service-standard", "MONTHLY",
                            "ACTIVE", NOW, 1, NOW, NOW.plusSeconds(2_592_000), false, null, NOW.plusSeconds(2_592_000), 0, NOW),
                    List.of());
        }
    }

    private static final class FakeWallets implements BillingWallets {
        private String currency = "USD";

        @Override
        public List<Wallet> wallets(String customerId) {
            return List.of(new Wallet(
                    "wal_1", customerId, currency, "PREPAID", new Amount(5000, currency), new Amount(0, currency),
                    new Amount(5000, currency), new Amount(0, currency), new Amount(0, currency), 0L, NOW));
        }

        @Override
        public Wallet wallet(String id) {
            return wallets("cus_1").get(0);
        }

        @Override
        public Wallet open(String idempotencyKey, OpenWallet request) {
            return wallets(request.customerId()).get(0);
        }
    }

    private static final class CapturingSubmission implements OrderSubmission {
        private final List<Order> orders = new ArrayList<>();

        @Override
        public void submit(Order order) {
            orders.add(order);
        }
    }
}
