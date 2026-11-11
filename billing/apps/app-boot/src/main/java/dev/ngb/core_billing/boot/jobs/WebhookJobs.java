package dev.ngb.core_billing.boot.jobs;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.webhook.application.delivery.command.DeliverWebhooksUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The webhook sender. Switching it off with {@code billing.webhooks.sender-enabled=false} is the rollback:
 * events keep accumulating as queued deliveries and go out when it is switched back on.
 */
@Profile("worker")
@Component
@ConditionalOnProperty(name = {"billing.jobs.enabled", "billing.webhooks.sender-enabled"}, havingValue = "true", matchIfMissing = true)
public class WebhookJobs {

    private final TenantSweep sweep;
    private final DeliverWebhooksUseCase deliver;
    private final MeterRegistry meters;

    WebhookJobs(TenantSweep sweep, DeliverWebhooksUseCase deliver, MeterRegistry meters) {
        this.sweep = sweep;
        this.deliver = deliver;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${billing.webhooks.poll-delay:PT2S}")
    public void deliver() {
        sweep.forEachActiveTenant(tenant -> {
            DeliverWebhooksUseCase.Round round = deliver.runOnce();
            if (round.succeeded() > 0) {
                meters.counter("billing.webhook.delivery", "outcome", "succeeded").increment(round.succeeded());
            }
            if (round.retrying() > 0) {
                meters.counter("billing.webhook.delivery", "outcome", "retrying").increment(round.retrying());
            }
            if (round.dead() > 0) {
                meters.counter("billing.webhook.delivery", "outcome", "dead").increment(round.dead());
            }
        });
    }
}
