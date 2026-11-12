package dev.ngb.tenant.usage.domain.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsageRecordTest {

    private static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final Instant OCCURRED = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant RECORDED = Instant.parse("2026-11-11T00:00:05Z");
    private static final IdGenerator IDS = () -> ID;

    @Test
    void recordingAssignsAnIdAndKeepsWhatBillingReturned() {
        UsageRecord record = UsageRecord.recorded(
                IDS, ORDER_ID, "cus_1", "compute_hours", "10", "hour", "evt_1", "usage:key", OCCURRED, RECORDED);

        assertThat(record.id()).isEqualTo(ID);
        assertThat(record.orderId()).isEqualTo(ORDER_ID);
        assertThat(record.billingCustomerId()).isEqualTo("cus_1");
        assertThat(record.metric()).isEqualTo("compute_hours");
        assertThat(record.quantity()).isEqualTo("10");
        assertThat(record.unit()).isEqualTo("hour");
        assertThat(record.billingEventId()).isEqualTo("evt_1");
        assertThat(record.idempotencyKey()).isEqualTo("usage:key");
        assertThat(record.occurredAt()).isEqualTo(OCCURRED);
        assertThat(record.recordedAt()).isEqualTo(RECORDED);
    }
}
