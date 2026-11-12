package dev.ngb.tenant.resource.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.shared.error.BadRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-11T01:00:00Z");
    private static final IdGenerator IDS = () -> ORDER_ID;

    private static Order queued() {
        return Order.queued(
                IDS,
                UUID.fromString("00000000-0000-7000-8000-0000000000c1"),
                "cus_1",
                "wal_1",
                UUID.fromString("00000000-0000-7000-8000-0000000000a1"),
                "bplan_1",
                "TIME_BASED",
                "compute_hours",
                "10",
                "hour",
                null,
                500L,
                "USD",
                "res_1",
                null,
                CREATED);
    }

    @Test
    void aNewOrderIsQueuedWithAReferenceDerivedFromItsId() {
        Order order = queued();

        assertThat(order.id()).isEqualTo(ORDER_ID);
        assertThat(order.reference()).isEqualTo("ORD-" + ORDER_ID);
        assertThat(order.status()).isEqualTo(OrderStatus.QUEUED);
        assertThat(order.failureReason()).isNull();
        assertThat(order.createdAt()).isEqualTo(CREATED);
        assertThat(order.updatedAt()).isEqualTo(CREATED);
        assertThat(order.terminal()).isFalse();
    }

    @Test
    void aTransitionKeepsEveryOtherFieldAndMovesOnlyTheUpdateTime() {
        Order order = queued();

        Order running = order.running(LATER);

        assertThat(running.status()).isEqualTo(OrderStatus.RUNNING);
        assertThat(running.updatedAt()).isEqualTo(LATER);
        assertThat(running).usingRecursiveComparison()
                .ignoringFields("status", "updatedAt")
                .isEqualTo(order);
    }

    @Test
    void onlyFailureRecordsAReason() {
        Order failed = queued().failed("wallet empty", LATER);
        Order suspended = queued().running(LATER).suspended(LATER);

        assertThat(failed.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("wallet empty");
        assertThat(suspended.status()).isEqualTo(OrderStatus.SUSPENDED);
        assertThat(suspended.failureReason()).isNull();
    }

    @Test
    void aSuspendedOrderCanBeResumedToTheQueue() {
        Order resumed = queued().running(LATER).suspended(LATER).resumed(LATER);

        assertThat(resumed.status()).isEqualTo(OrderStatus.QUEUED);
        assertThat(resumed.terminal()).isFalse();
    }

    @Test
    void completedFailedAndCanceledAreTerminal() {
        assertThat(queued().completed(LATER).terminal()).isTrue();
        assertThat(queued().failed("x", LATER).terminal()).isTrue();
        assertThat(queued().canceled(LATER).terminal()).isTrue();
        assertThat(queued().suspended(LATER).terminal()).isFalse();
    }

    @Test
    void aTerminalOrderRejectsEveryFurtherTransition() {
        Order completed = queued().completed(LATER);

        assertThatThrownBy(() -> completed.running(LATER)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> completed.failed("late", LATER)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> completed.suspended(LATER)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> completed.resumed(LATER)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> completed.canceled(LATER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(ORDER_ID.toString())
                .hasMessageContaining("COMPLETED");
        assertThatThrownBy(() -> queued().canceled(LATER).completed(LATER)).isInstanceOf(BadRequestException.class);
    }
}
