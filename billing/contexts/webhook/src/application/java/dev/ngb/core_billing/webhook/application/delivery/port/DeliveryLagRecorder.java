package dev.ngb.core_billing.webhook.application.delivery.port;

import java.time.Duration;

/** Where a successful delivery's wait is reported. */
@FunctionalInterface
public interface DeliveryLagRecorder {

    void record(Duration lag);

    DeliveryLagRecorder NONE = lag -> {};
}
