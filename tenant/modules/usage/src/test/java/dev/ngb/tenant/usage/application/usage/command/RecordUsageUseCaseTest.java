package dev.ngb.tenant.usage.application.usage.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.billing.api.client.BillingUsage;
import dev.ngb.tenant.billing.api.model.RecordedUsage;
import dev.ngb.tenant.billing.api.model.UsageEvent;
import dev.ngb.tenant.billing.api.model.UsageEventRequest;
import dev.ngb.tenant.usage.api.recording.UsageRecorder;
import dev.ngb.tenant.usage.application.usage.port.UsageRepository;
import dev.ngb.tenant.usage.domain.usage.UsageRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordUsageUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    private final InMemoryUsage usage = new InMemoryUsage();
    private final FakeBillingUsage billing = new FakeBillingUsage();
    private final RecordUsageUseCase record =
            new RecordUsageUseCase(usage, billing, () -> NOW, () -> UUID.randomUUID());

    @Test
    void recordsTheEventInBillingWithADeterministicKey() {
        UUID orderId = UUID.randomUUID();

        UsageRecorder.Recorded recorded = record.record(command(orderId));

        assertThat(recorded.billingEventId()).isEqualTo("use_1");
        assertThat(billing.requests).singleElement().satisfies(request -> {
            assertThat(request.idempotencyKey()).isEqualTo("usage:" + orderId + ":compute.vcpu_minute");
            assertThat(request.resourceRef()).isEqualTo("ORD-1");
        });
        assertThat(usage.rows).hasSize(1);
    }

    @Test
    void aRepeatReturnsTheStoredEventWithoutCallingBillingAgain() {
        UUID orderId = UUID.randomUUID();

        UsageRecorder.Recorded first = record.record(command(orderId));
        UsageRecorder.Recorded second = record.record(command(orderId));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(billing.requests).hasSize(1);
        assertThat(usage.rows).hasSize(1);
    }

    private static UsageRecorder.UsageCommand command(UUID orderId) {
        return new UsageRecorder.UsageCommand(
                orderId, "cus_1", "compute.vcpu_minute", "120", "vCPU-minute", "ORD-1", NOW);
    }

    private static final class FakeBillingUsage implements BillingUsage {
        private final List<UsageEventRequest> requests = new ArrayList<>();

        @Override
        public RecordedUsage record(UsageEventRequest request) {
            requests.add(request);
            return new RecordedUsage(new UsageEvent(
                    "use_" + requests.size(), request.customerId(), request.metric(), request.quantity(), request.unit(),
                    request.occurredAt(), NOW, request.idempotencyKey(), "sys_1", request.resourceRef(), request.sourceEventId()), false);
        }

        @Override
        public List<UsageEvent> history(String customerId, String metric, Instant from, Instant to, int limit) {
            return List.of();
        }
    }

    private static final class InMemoryUsage implements UsageRepository {
        private final Map<UUID, UsageRecord> rows = new LinkedHashMap<>();

        @Override
        public void save(UsageRecord record) {
            rows.put(record.id(), record);
        }

        @Override
        public Optional<UsageRecord> findByOrderIdAndMetric(UUID orderId, String metric) {
            return rows.values().stream()
                    .filter(row -> row.orderId().equals(orderId) && row.metric().equals(metric))
                    .findFirst();
        }
    }
}
