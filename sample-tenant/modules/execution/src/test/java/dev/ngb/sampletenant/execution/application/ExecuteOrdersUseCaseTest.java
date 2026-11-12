package dev.ngb.sampletenant.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.billing.api.BillingCharges;
import dev.ngb.sampletenant.billing.api.model.Amount;
import dev.ngb.sampletenant.billing.api.model.Charge;
import dev.ngb.sampletenant.billing.api.model.ChargeUsageRequest;
import dev.ngb.sampletenant.billing.api.model.Rating;
import dev.ngb.sampletenant.billing.api.model.Reservation;
import dev.ngb.sampletenant.execution.application.port.ExecutionQueue;
import dev.ngb.sampletenant.resource.api.ExecutionOrders;
import dev.ngb.sampletenant.shared.error.UpstreamUnavailableException;
import dev.ngb.sampletenant.usage.api.UsageRecorder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecuteOrdersUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final UUID ORDER_ID = UUID.randomUUID();

    private final FakeQueue queue = new FakeQueue();
    private final FakeOrders orders = new FakeOrders();
    private final FakeUsage usage = new FakeUsage();
    private final FakeCharges charges = new FakeCharges();
    private final ExecuteOrdersUseCase execute =
            new ExecuteOrdersUseCase(queue, orders, usage, charges, () -> NOW, 3);

    @Test
    void runsRecordsUsageCapturesReleasesAndCompletes() {
        ExecuteOrdersUseCase.Report report = execute.run(10);

        assertThat(report).isEqualTo(new ExecuteOrdersUseCase.Report(1, 1, 0, 0));
        assertThat(orders.status).isEqualTo("COMPLETED");
        assertThat(usage.commands).hasSize(1);
        assertThat(charges.captured).isTrue();
        assertThat(charges.released).isTrue();
        assertThat(queue.finished).isTrue();
    }

    @Test
    void aFailedChargeMarksTheOrderFailedAndReleasesTheHold() {
        charges.failCharge = true;

        ExecuteOrdersUseCase.Report report = execute.run(10);

        assertThat(report).isEqualTo(new ExecuteOrdersUseCase.Report(1, 0, 1, 0));
        assertThat(orders.status).isEqualTo("FAILED");
        assertThat(orders.failureReason).isEqualTo("insufficient_funds");
        assertThat(charges.released).isTrue();
        assertThat(queue.finished).isTrue();
    }

    @Test
    void aTransientFailureIsRequeuedWhileAttemptsRemain() {
        usage.fail = true;
        queue.claimed.add(new ExecutionQueue.Claimed(ORDER_ID, 1));

        ExecuteOrdersUseCase.Report report = execute.run(10);

        assertThat(report).isEqualTo(new ExecuteOrdersUseCase.Report(1, 0, 0, 1));
        assertThat(orders.status).isEqualTo("QUEUED");
        assertThat(queue.requeued).isTrue();
    }

    @Test
    void aTransientFailureAfterTheLastAttemptFailsTheOrder() {
        usage.fail = true;
        queue.claimed.add(new ExecutionQueue.Claimed(ORDER_ID, 3));

        ExecuteOrdersUseCase.Report report = execute.run(10);

        assertThat(report).isEqualTo(new ExecuteOrdersUseCase.Report(1, 0, 1, 0));
        assertThat(orders.status).isEqualTo("FAILED");
        assertThat(queue.finished).isTrue();
    }

    @Test
    void aSuspendedOrderIsFinishedWithoutCharging() {
        orders.status = "SUSPENDED";

        execute.run(10);

        assertThat(usage.commands).isEmpty();
        assertThat(charges.captured).isFalse();
        assertThat(queue.finished).isTrue();
    }

    @Test
    void aHybridOrderRatesItsUsageWithoutAReservation() {
        orders.pricingModel = "HYBRID";
        orders.estimated = 0;
        orders.reservationId = null;

        ExecuteOrdersUseCase.Report report = execute.run(10);

        assertThat(report.completed()).isEqualTo(1);
        assertThat(usage.commands).hasSize(1);
        assertThat(charges.rated).isTrue();
        assertThat(charges.captured).isFalse();
        assertThat(orders.status).isEqualTo("COMPLETED");
    }

    @Test
    void aSubscriptionOrderUsesTheSubscriptionChargeAndRecordsNoUsage() {
        orders.pricingModel = "SUBSCRIPTION";
        orders.estimated = 0;
        orders.reservationId = null;

        execute.run(10);

        assertThat(usage.commands).isEmpty();
        assertThat(charges.rated).isFalse();
        assertThat(charges.captured).isFalse();
        assertThat(orders.status).isEqualTo("COMPLETED");
    }

    private static final class FakeQueue implements ExecutionQueue {
        private final List<Claimed> claimed = new ArrayList<>();
        private boolean finished;
        private boolean requeued;

        @Override
        public List<Claimed> claim(int batchSize) {
            return claimed.isEmpty() ? List.of(new Claimed(ORDER_ID, 1)) : List.copyOf(claimed);
        }

        @Override
        public void finish(UUID orderId) {
            finished = true;
        }

        @Override
        public void requeue(UUID orderId) {
            requeued = true;
        }
    }

    private static final class FakeOrders implements ExecutionOrders {
        private String status = "QUEUED";
        private String pricingModel = "TIME_BASED";
        private long estimated = 1000;
        private String reservationId = "res_1";
        private String failureReason;

        @Override
        public Optional<ExecutionOrder> find(UUID orderId) {
            return Optional.of(new ExecutionOrder(
                    orderId, UUID.randomUUID(), "cus_1", "wal_1", "bplan_1", pricingModel, "compute.vcpu_minute",
                    "120", "vCPU-minute", null, estimated, "USD", reservationId, null, "ORD-1", status));
        }

        @Override
        public void markRunning(UUID orderId) {
            status = "RUNNING";
        }

        @Override
        public void markQueued(UUID orderId) {
            status = "QUEUED";
        }

        @Override
        public void markCompleted(UUID orderId) {
            status = "COMPLETED";
        }

        @Override
        public void markFailed(UUID orderId, String reason) {
            status = "FAILED";
            failureReason = reason;
        }

        @Override
        public int suspendByBillingCustomer(String billingCustomerId, Instant now) {
            return 0;
        }

        @Override
        public int resumeByBillingCustomer(String billingCustomerId, Instant now) {
            return 0;
        }
    }

    private static final class FakeUsage implements UsageRecorder {
        private final List<UsageCommand> commands = new ArrayList<>();
        private boolean fail;

        @Override
        public Recorded record(UsageCommand command) {
            if (fail) {
                throw new UpstreamUnavailableException("billing is down");
            }
            commands.add(command);
            return new Recorded(UUID.randomUUID(), "use_1");
        }
    }

    private static final class FakeCharges implements BillingCharges {
        private boolean failCharge;
        private boolean captured;
        private boolean released;
        private boolean rated;

        @Override
        public Charge charge(String idempotencyKey, ChargeRequest request) {
            captured = true;
            return result();
        }

        @Override
        public Charge capture(String idempotencyKey, String reservationId, CaptureRequest request) {
            captured = true;
            return result();
        }

        @Override
        public Reservation release(String reservationId) {
            released = true;
            return new Reservation(
                    reservationId, "wal_1", "RELEASED", new Amount(1000, "USD"), null, NOW, null, NOW, NOW, false);
        }

        @Override
        public Rating chargeUsage(ChargeUsageRequest request) {
            rated = true;
            return new Rating("CHARGED", "rat_1", "bplan_1", 1, new Amount(100, "USD"), "chg_2", null, 1,
                    NOW.minusSeconds(86_400), NOW, List.of(), false);
        }

        @Override
        public Rating rating(String id) {
            return chargeUsage(new ChargeUsageRequest("cus_1", "wal_1", "bplan_1", null, NOW, NOW));
        }

        private Charge result() {
            if (failCharge) {
                return new Charge("chg_1", "FAILED", "insufficient_funds", new Amount(1000, "USD"), null, "res_1", null, "ORD-1");
            }
            return new Charge("chg_1", "SUCCEEDED", null, new Amount(1000, "USD"), "led_1", "res_1", null, "ORD-1");
        }
    }
}
