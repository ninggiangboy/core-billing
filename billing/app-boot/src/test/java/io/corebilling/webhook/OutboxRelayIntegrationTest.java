package io.corebilling.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.corebilling.Application;
import io.corebilling.boot.infrastructure.outbox.OutboxRelay;
import io.corebilling.sharedkernel.EventEnvelope;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.support.OwnerDatabase;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.ScopeTestSupport;
import io.corebilling.support.TestEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The relay's own guarantees, independent of any consumer: claim, retry with backoff, dead-letter, rollback of a failed consumer. */
@SpringBootTest(classes = Application.class)
@Import({PostgresContainerConfig.class, OutboxRelayIntegrationTest.Consumers.class})
class OutboxRelayIntegrationTest {

    /** Writes a probe row first, then fails for {@code test.poison} while {@code failing} is set. */
    static final class Probe {
        final AtomicBoolean failing = new AtomicBoolean(true);
        private final JdbcClient jdbc;

        Probe(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE - 1)
        void record(EventEnvelope event) {
            jdbc.sql("insert into event_inbox (tenant_id, consumer, event_id) values (?, 'probe', ?) on conflict do nothing")
                    .params(event.tenantId().value(), event.eventId()).update();
        }

        @EventListener
        void failOnPoison(EventEnvelope event) {
            if (event.type().equals("test.poison") && failing.get()) {
                throw new IllegalStateException("consumer exploded");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Consumers {
        @Bean
        Probe probe(JdbcClient jdbc) {
            return new Probe(jdbc);
        }
    }

    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired UnitOfWork unitOfWork;
    @Autowired EventPublisher events;
    @Autowired OutboxRelay relay;
    @Autowired Probe probe;

    private UUID publish(TenantId tenant, String type) {
        UUID id = UUID.randomUUID();
        scopes.runInTenant(tenant, () -> unitOfWork.transactional(() ->
                events.publish(new TestEvent(id, tenant, type, "test/" + id, Instant.now(), Map.of("n", 1)))));
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return owner.jdbc().queryForMap("select status, attempts, last_error, next_attempt_at > now() as in_future from outbox where id = ?", id);
    }

    private boolean probed(UUID id) {
        return owner.count("select count(*) from event_inbox where consumer = 'probe' and event_id = ?", id) == 1;
    }

    @Test
    void aDeliveredEventIsMarkedAndNotRelayedAgain() {
        TenantId tenant = scopes.newTenant().id();
        UUID id = publish(tenant, "test.fine");

        assertThat(scopes.inTenant(tenant, relay::relayDue)).isEqualTo(new OutboxRelay.Result(1, 0, 0));
        assertThat(row(id)).containsEntry("status", "DELIVERED");
        assertThat(probed(id)).isTrue();
        assertThat(scopes.inTenant(tenant, relay::relayDue)).isEqualTo(new OutboxRelay.Result(0, 0, 0));
    }

    @Test
    void aFailingConsumerRollsBackItsWorkAndTheEventIsRetriedWithBackoffThenDeadLettered() {
        TenantId tenant = scopes.newTenant().id();
        UUID poison = publish(tenant, "test.poison");
        UUID fine = publish(tenant, "test.fine");

        OutboxRelay.Result first = scopes.inTenant(tenant, relay::relayDue);
        assertThat(first).as("one failed, the event behind it still goes through").isEqualTo(new OutboxRelay.Result(1, 1, 0));
        assertThat(row(poison)).containsEntry("status", "PENDING").containsEntry("attempts", 1).containsEntry("in_future", true);
        assertThat((String) row(poison).get("last_error")).contains("consumer exploded");
        assertThat(probed(poison)).as("the consumer's partial work was rolled back").isFalse();
        assertThat(probed(fine)).isTrue();
        assertThat(scopes.inTenant(tenant, relay::relayDue)).as("not due until the backoff passes").isEqualTo(new OutboxRelay.Result(0, 0, 0));

        owner.jdbc().update("update outbox set next_attempt_at = now() - interval '1 second' where id = ?", poison);
        assertThat(scopes.inTenant(tenant, relay::relayDue).failed()).isEqualTo(1);
        owner.jdbc().update("update outbox set next_attempt_at = now() - interval '1 second' where id = ?", poison);
        assertThat(scopes.inTenant(tenant, relay::relayDue)).as("the third failure is the last").isEqualTo(new OutboxRelay.Result(0, 0, 1));

        assertThat(row(poison)).containsEntry("status", "DEAD").containsEntry("attempts", 3);
        assertThat(owner.count("select count(*) from outbox where id = ?", poison)).as("a dead row is kept").isEqualTo(1);
        owner.jdbc().update("update outbox set next_attempt_at = now() - interval '1 second' where id = ?", poison);
        assertThat(scopes.inTenant(tenant, relay::relayDue)).as("and never picked up again").isEqualTo(new OutboxRelay.Result(0, 0, 0));
    }

    @Test
    void aTransientFailureSucceedsOnTheNextAttempt() {
        TenantId tenant = scopes.newTenant().id();
        UUID id = publish(tenant, "test.poison");
        probe.failing.set(true);
        assertThat(scopes.inTenant(tenant, relay::relayDue).failed()).isEqualTo(1);

        probe.failing.set(false);
        try {
            owner.jdbc().update("update outbox set next_attempt_at = now() - interval '1 second' where id = ?", id);
            assertThat(scopes.inTenant(tenant, relay::relayDue).delivered()).isEqualTo(1);
            assertThat(row(id)).containsEntry("status", "DELIVERED");
            assertThat(probed(id)).isTrue();
        } finally {
            probe.failing.set(true);
        }
    }

    @Test
    void aRelayOnlyEverSeesItsOwnTenantsRows() {
        TenantId acme = scopes.newTenant().id();
        TenantId globex = scopes.newTenant().id();
        UUID acmeEvent = publish(acme, "test.fine");
        UUID globexEvent = publish(globex, "test.fine");

        assertThat(scopes.inTenant(acme, relay::relayDue).delivered()).isEqualTo(1);
        assertThat(row(acmeEvent)).containsEntry("status", "DELIVERED");
        assertThat(row(globexEvent)).containsEntry("status", "PENDING");
    }
}
