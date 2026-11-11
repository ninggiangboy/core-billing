---
phase: 2
title: "Webhook time budget and connection pool"
status: pending
priority: P2
effort: "1.5h"
dependencies: [1]
---

# Phase 2: Webhook time budget and connection pool

## Goal

Spend at most 1s waiting for the webhook tick, and make the outbound HTTP pool limits configurable so that a sweep
running several tenants at once does not queue behind hard-coded limits.

## Context

A healthy receiver's lag is mostly waiting, not sending: the outbox relay sees the event within
`billing.outbox.poll-delay` (now `PT1S`) and the sender picks the delivery up within `billing.webhooks.poll-delay`
(now `PT2S`). Two polls can eat 3 of the 5s target before a byte leaves the process.

`HttpComponentsWebhookSender` hard-codes `setMaxConnTotal(100)` and `setMaxConnPerRoute(10)`. With phase 1 running 8
tenants at once, each with `batch-size` 25, up to 200 sends can be in flight, and 25 of them may target the same
receiver: 15 of those wait for a connection with no bound of their own. The 15s deadline currently only limits reading
the response excerpt, so a send can sit in the pool for an unbounded time on top of it.

## Files to Create / Modify

- Modify: `billing/apps/app-boot/src/main/resources/application.yml`
- Modify: `billing/contexts/webhook/src/adapter/java/dev/ngb/core_billing/webhook/adapter/delivery/http/HttpComponentsWebhookSender.java`
- Modify: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/config/WebhookConfiguration.java`
- Modify: `billing/contexts/webhook/src/test/java/dev/ngb/core_billing/webhook/adapter/delivery/http/HttpSenderTest.java`

## Tasks & Steps

1. In `application.yml`, change `billing.webhooks.poll-delay` from `PT2S` to `PT1S` and add, next to the existing
   timeouts:
   ```yaml
   # Outbound HTTP pool. The limits are sized for the tenants a worker sends for at once, not for one tenant.
   max-connections: ${BILLING_WEBHOOKS_MAX_CONNECTIONS:100}
   max-connections-per-route: ${BILLING_WEBHOOKS_MAX_CONNECTIONS_PER_ROUTE:25}
   # Worst case is this wait plus connect-timeout plus read-timeout, which must stay inside deadline: 2s + 3s + 10s.
   connection-request-timeout: PT2S
   ```
2. Add three constructor parameters to `HttpComponentsWebhookSender`: `int maxConnTotal`, `int maxConnPerRoute`,
   `Duration connectionRequestTimeout`. Use them for `setMaxConnTotal`, `setMaxConnPerRoute` and
   `RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofMilliseconds(...))`. Keep the existing
   `setResponseTimeout(readTimeout)` and the deadline check on the excerpt loop unchanged.
3. In `WebhookConfiguration.webhookSender`, bind the new values with `@Value` defaults matching the YAML and pass them
   through. Keep the bean `destroyMethod = "close"`.
4. Update `HttpSenderTest`'s `sender(...)` helper for the new signature and add one test: a sender with
   `maxConnPerRoute = 1` and a 200ms connection-request timeout, against a receiver that holds the first request open
   for 3s. The second send must come back quickly with error `timeout` instead of waiting for the read timeout; assert
   the elapsed time is far below the read timeout.
5. Do not change `billing.webhooks.batch-size` or the deadline in this phase. If the load test in phase 5 shows queueing
   inside the pool, the numbers to change are `max-connections-per-route` and `deadline`, in that order.

## Todo

- [x] Set `billing.webhooks.poll-delay` to `PT1S`
- [x] Add the three `billing.webhooks` pool properties
- [x] Thread them through `HttpComponentsWebhookSender` and `WebhookConfiguration`
- [x] Update `HttpSenderTest` and add the connection-request timeout test
- [x] Run `./gradlew :contexts:webhook:test --tests '*HttpSenderTest'`

## Success Criteria

- No outbound HTTP limit is hard-coded in the adapter; all three come from configuration.
- Connection request, connect and read timeouts add up to the 15s deadline, and the comment in `application.yml` says
  so next to the numbers.
- A saturated per-route pool fails fast with `timeout` rather than blocking a worker thread for the read timeout.
- `./gradlew :contexts:webhook:test` and `./gradlew :apps:app-boot:test --tests '*Webhook*'` are green.

## Risk Assessment

- **Too low a per-route limit turns healthy sends into `timeout`.** 25 is the batch size, so one tenant's whole round
  can be in flight to one receiver. That is what the load test measures; it is a starting number, not a claim.
- **Connection-request timeout counted outside the deadline.** The deadline check only bounds reading the excerpt, so
  the three timeouts are the only guard. The sum must stay inside `deadline`, and the YAML comment records the sum.
- **Phase 1 changes the concurrency this phase is sized for.** If phase 1 lands with a different default, revisit
  `max-connections-per-route` before running the load test.
