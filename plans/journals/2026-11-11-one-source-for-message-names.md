---
title: One source for message names
date: 2026-11-11
summary: "Consumed event types are published from the producer api, and each context lists its inbox consumers and system actors in one class"
---

# One source for message names

## What changed

- `money.api.event.MoneyEventTypes` publishes `charge.succeeded`; `MoneyEvents.ChargeSucceeded.TYPE` points at it.
- `payment.api.payment.PaymentEventTypes` publishes `payment.succeeded` and `payment.failed`; both `PaymentEvents` `TYPE`
  fields point at them.
- `CollectChargeUseCase` and `ApplyPaymentUseCase` compare against those constants. Their `EVENT_TYPE`, `SUCCEEDED` and
  `FAILED` copies are gone.
- `InvoicingConsumers`, `InvoicingActors`, `MoneyActors`, `WebhookConsumers` and `PlatformActors` live under
  `<ctx>.application.messaging.service` and are the only declarations of the consumer and actor names:
  `invoicing.cycle`, `invoicing.payment`, `system/invoice-payment`, `system/charge`, `system/ledger-verify`, `webhook`
  and `system/bootstrap`.
- Every string value is byte-identical; only the declarations moved.

## Verification

- `./gradlew test` is green, including `ArchitectureTest` and `ModulithVerificationTest`.
- No use case declares `CONSUMER`, `EVENT_TYPE` or `ACTOR` any more. The only survivor of the acceptance grep is
  `WebhookHeaders.EVENT_TYPE`, an HTTP header name (`Billing-Event-Type`), not a message name.
- `contracts/` is untouched.
- `SubscriptionFailureIntegrationTest.eachRetryWaitsLongerThanTheLast` failed once on timing jitter and passes in
  isolation; it compares backoff durations measured with two `Instant.now()` reads and is unrelated to this change.
