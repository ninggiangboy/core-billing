package dev.ngb.core_billing.boot.jobs;

import dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Relays outbox events to the in-process consumers for every active tenant. */
@Component
@ConditionalOnProperty(name = "billing.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxJobs {

    private final TenantSweep sweep;
    private final OutboxRelay relay;

    OutboxJobs(TenantSweep sweep, OutboxRelay relay) {
        this.sweep = sweep;
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${billing.outbox.poll-delay:PT1S}")
    public void relay() {
        sweep.forEachActiveTenant(tenant -> relay.relayDue());
    }
}
