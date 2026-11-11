---
phase: 4
title: "Webhook lag metric"
status: pending
priority: P2
effort: "1h"
dependencies: [1]
---

# Phase 4: Webhook lag metric

## Goal

Publish `billing.webhook.lag` as a timer with one sample per successful delivery, so the delay that the 5s target is
about can be watched in production, not only in a test run.

## Context

Today the sender counts outcomes (`billing.webhook.delivery` with `outcome=succeeded|retrying|dead`) and the attempt
table stores `latency_ms`, which is the HTTP call only. Nothing measures how long a delivery waited between being
recorded and being sent, which is exactly what phase 1 changes.

`DeliverWebhooksUseCase` already has the two timestamps it needs: `WebhookDelivery.createdAt()` on the claimed row and
`DeliveryAttempt.attemptedAt()` on the outcome. The record transaction is where both are in hand.

The application layer has no micrometer dependency (the module descriptors in
`billing/contexts/webhook/build.gradle.kts` do not include it), so the timer is recorded through a port and the
Micrometer implementation stays in `app-boot`, where `MeterRegistry` is already used by the jobs.

## Files to Create / Modify

- Create: `billing/contexts/webhook/src/application/java/dev/ngb/core_billing/webhook/application/delivery/port/DeliveryLagRecorder.java`
- Modify: `billing/contexts/webhook/src/application/java/dev/ngb/core_billing/webhook/application/delivery/command/DeliverWebhooksUseCase.java`
- Modify: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/config/WebhookConfiguration.java`
- Modify: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/webhook/WebhookDeliveryIntegrationTest.java`

## Tasks & Steps

1. Create a functional port in the `port` role package:
   ```java
   /** Where a successful delivery's wait is reported. */
   @FunctionalInterface
   public interface DeliveryLagRecorder {
       void record(Duration lag);
       DeliveryLagRecorder NONE = lag -> {};
   }
   ```
   `NONE` keeps unit tests that build the use case by hand readable.
2. Add a `DeliveryLagRecorder lags` constructor parameter to `DeliverWebhooksUseCase` and keep the fields final.
3. In `runOnce`, collect `Duration.between(delivery.createdAt(), attempt.attemptedAt())` for every successful attempt
   while the record transaction runs, then call `lags.record(...)` for each sample **after** the transaction returns.
   A metrics call must not run inside the transaction that writes the attempts, and it must never decide the outcome.
4. In `WebhookConfiguration`, add
   ```java
   @Bean
   DeliveryLagRecorder deliveryLagRecorder(MeterRegistry meters) {
       return meters.timer("billing.webhook.lag")::record;
   }
   ```
   and pass it to `deliverWebhooksUseCase`. Do not create a second registry or a static timer: the actuator registry is
   the one already scraped.
5. Extend `WebhookDeliveryIntegrationTest` with one test: publish an event, relay it, send it to a healthy receiver, and
   assert `meterRegistry.timer("billing.webhook.lag").count()` grew by one and its total time is greater than zero.
   Autowire `MeterRegistry` in the test; do not assert a ceiling on a shared registry that other tests have written to.
6. Write the metric's definition into the class comment: measured from the delivery row's creation to the completed
   attempt, so it excludes the outbox poll delay (at most `billing.outbox.poll-delay`) and the consumer transaction.
   The load test in phase 5 measures the whole event-to-receipt path instead.

## Todo

- [x] Add the `DeliveryLagRecorder` port
- [x] Record one sample per successful delivery, outside the transaction
- [x] Wire the Micrometer timer bean in `WebhookConfiguration`
- [x] Add the integration assertion
- [x] Run `./gradlew :apps:app-boot:test --tests '*WebhookDeliveryIntegrationTest'`

## Success Criteria

- Every successful delivery produces exactly one `billing.webhook.lag` sample; failures and retries produce none.
- The application module still does not depend on Micrometer.
- The metric's definition, including what it leaves out, is in the code and in `docs/webhooks-and-events.md` (phase 6).

## Risk Assessment

- **The metric under-reports the true lag.** It starts at the delivery row, not at the event, so up to 1s of outbox
  poll delay is missing. Naming it `billing.webhook.lag` and documenting the boundary is deliberate; the load test is
  the end-to-end number. If production needs the full path, the follow-up is to store the event's `occurredAt` on the
  delivery row, which is a schema change and out of scope here.
- **A high-cardinality tag would drain the registry.** Record no tags; per-tenant lag is not worth the cardinality.
