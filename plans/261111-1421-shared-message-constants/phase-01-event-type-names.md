---
phase: 1
title: "Event type names in the producer api"
status: completed
priority: P2
effort: "45m"
dependencies: []
---

# Event type names in the producer api

## Goal

The event types invoicing reacts to are defined once, in the `api` package of the context that
announces them, and both the producer's domain event and the consumer's use case use that constant.

## Context

- `money.domain.event.MoneyEvents.ChargeSucceeded.TYPE = "charge.succeeded"`
  (`billing/contexts/money/src/domain/java/dev/ngb/core_billing/money/domain/event/MoneyEvents.java:140`).
  `CollectChargeUseCase` copies it as `EVENT_TYPE` because invoicing may not import `money.domain`.
- `payment.domain.payment.PaymentEvents.PaymentSucceeded.TYPE = "payment.succeeded"` (line 35) and
  `PaymentFailed.TYPE = "payment.failed"` (line 63). `ApplyPaymentUseCase` copies them as
  `SUCCEEDED` and `FAILED`.
- A domain may import its own context's `api`: `LedgerEntry`, `Charge` and `Reservation` already do.
- Invoicing already depends on `payment.api` (`PayInvoiceUseCase`) and on `money.api`; neither
  producer depends on invoicing, so Modulith sees no new cycle.

## Files to Create / Modify

- Create: `billing/contexts/money/src/api/java/dev/ngb/core_billing/money/api/event/MoneyEventTypes.java`
- Create: `billing/contexts/payment/src/api/java/dev/ngb/core_billing/payment/api/payment/PaymentEventTypes.java`
- Modify: `billing/contexts/money/src/domain/java/dev/ngb/core_billing/money/domain/event/MoneyEvents.java`
- Modify: `billing/contexts/payment/src/domain/java/dev/ngb/core_billing/payment/domain/payment/PaymentEvents.java`
- Modify: `billing/contexts/invoicing/src/application/java/dev/ngb/core_billing/invoicing/application/cycle/command/CollectChargeUseCase.java`
- Modify: `billing/contexts/invoicing/src/application/java/dev/ngb/core_billing/invoicing/application/invoice/command/ApplyPaymentUseCase.java`

## Tasks & Steps

1. Create `MoneyEventTypes`: a `final` class with a private constructor and
   `public static final String CHARGE_SUCCEEDED = "charge.succeeded";`. The Javadoc says these are
   the published names of money events that other contexts react to, and that the schemas in
   `contracts/events` define the payloads.
2. Set `MoneyEvents.ChargeSucceeded.TYPE = MoneyEventTypes.CHARGE_SUCCEEDED`. Keep the nested
   `TYPE` field so existing callers inside money stay unchanged.
3. Create `PaymentEventTypes` the same way with `PAYMENT_SUCCEEDED = "payment.succeeded"` and
   `PAYMENT_FAILED = "payment.failed"`.
4. Set `PaymentEvents.PaymentSucceeded.TYPE` and `PaymentEvents.PaymentFailed.TYPE` to those
   constants.
5. In `CollectChargeUseCase`, delete `EVENT_TYPE` and compare with
   `MoneyEventTypes.CHARGE_SUCCEEDED`. Leave `CONSUMER` for phase 2.
6. In `ApplyPaymentUseCase`, delete `SUCCEEDED` and `FAILED` and compare with
   `PaymentEventTypes.PAYMENT_SUCCEEDED` and `PaymentEventTypes.PAYMENT_FAILED`. Leave `CONSUMER`
   and `ACTOR` for phase 2.
7. Leave every other `TYPE` constant in the domain: no other context consumes it.

## Verification

- `grep -rnE '"(charge\.succeeded|payment\.(succeeded|failed))"' billing/contexts/*/src/{domain,application,api}`
  matches only `MoneyEventTypes` and `PaymentEventTypes`.
- `cd billing && ./gradlew :apps:app-boot:test --tests '*ArchitectureTest' --tests '*ModulithVerificationTest' --tests '*PostpaidCycleIntegrationTest' --tests '*EventContractIntegrationTest'`
- `git diff` shows no changed string literal, only moved ones.
