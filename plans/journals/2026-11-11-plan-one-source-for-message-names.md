---
title: Plan one source for message names
date: 2026-11-11
summary: Planned moving consumed event type names to the producer api and gathering inbox consumer and system actor names per context.
---

# Plan one source for message names

## What happened

Several use cases declared their own `CONSUMER`, `EVENT_TYPE` and `ACTOR` strings. When I went through them, only the event types turned out to be copies. `CollectChargeUseCase.EVENT_TYPE` repeats `MoneyEvents.ChargeSucceeded.TYPE`, and `ApplyPaymentUseCase.SUCCEEDED`/`FAILED` repeat `PaymentEvents`. The copies exist because the `no-cross-context-import` rule stops invoicing from reading `money.domain` or `payment.domain`. Each consumer and actor name is unique and used in only one place.

Plan: `plans/261111-1421-shared-message-constants/`. It has two phases, each one commit.

## Decision

- Event type names that another context consumes are published from the producer's `api` package: `money.api.event.MoneyEventTypes` and `payment.api.payment.PaymentEventTypes`. The domain `TYPE` fields point at these constants. I chose this over a catalog in shared-kernel so the kernel does not need to know each context's events.
- The user chose one `<Ctx>Consumers` and one `<Ctx>Actors` class per context. They go under `<ctx>.application.messaging.service`, because the `role-package-not-layer-root` and `application-classes-in-a-role-package` ArchUnit rules forbid classes at the root of the application layer.
- Every string value stays byte-identical. Consumer names key `event_inbox`, and actor names are stored in the ledger and the audit log.

## Next steps

Run `/ak-cook plans/261111-1421-shared-message-constants/plan.md`.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
