---
phase: 2
title: "Consumer and actor names per context"
status: completed
priority: P2
effort: "45m"
dependencies: [1]
---

# Consumer and actor names per context

## Goal

Each context lists its inbox consumer names in one `<Ctx>Consumers` class and its system actor
names in one `<Ctx>Actors` class, under `<ctx>.application.messaging.service`, and no use case
declares these names itself.

## Context

The names today, all used only inside their own use case:

| Context | Use case | Constant | Value | Stored in |
|---------|----------|----------|-------|-----------|
| invoicing | `CollectChargeUseCase` | `CONSUMER` | `invoicing.cycle` | `event_inbox.consumer` |
| invoicing | `ApplyPaymentUseCase` | `CONSUMER` | `invoicing.payment` | `event_inbox.consumer` |
| invoicing | `ApplyPaymentUseCase` | `ACTOR` | `system/invoice-payment` | `ledger_entries.actor` |
| money | `ChargeUseCase` | `ACTOR` | `system/charge` | the charge's ledger entry, through `ChargeService.Request` |
| money | `VerifyLedgerUseCase` | `ACTOR` | `system/ledger-verify` | `audit_log.actor` |
| webhook | `RecordDeliveriesUseCase` | `CONSUMER` | `webhook` | `event_inbox.consumer` |
| platform | `BootstrapPlatformAdminUseCase` | inside `BOOTSTRAP` | `system/bootstrap` | `audit_log.actor` |

No test or other class references these constants through the use case, so removing them breaks no
caller.

## Files to Create / Modify

- Create: `billing/contexts/invoicing/src/application/java/dev/ngb/core_billing/invoicing/application/messaging/service/InvoicingConsumers.java`
- Create: `billing/contexts/invoicing/src/application/java/dev/ngb/core_billing/invoicing/application/messaging/service/InvoicingActors.java`
- Create: `billing/contexts/money/src/application/java/dev/ngb/core_billing/money/application/messaging/service/MoneyActors.java`
- Create: `billing/contexts/webhook/src/application/java/dev/ngb/core_billing/webhook/application/messaging/service/WebhookConsumers.java`
- Create: `billing/contexts/platform/src/application/java/dev/ngb/core_billing/platform/application/messaging/service/PlatformActors.java`
- Modify: `.../invoicing/application/cycle/command/CollectChargeUseCase.java`
- Modify: `.../invoicing/application/invoice/command/ApplyPaymentUseCase.java`
- Modify: `.../money/application/charge/command/ChargeUseCase.java`
- Modify: `.../money/application/ledger/command/VerifyLedgerUseCase.java`
- Modify: `.../webhook/application/delivery/command/RecordDeliveriesUseCase.java`
- Modify: `.../platform/application/user/command/BootstrapPlatformAdminUseCase.java`

## Tasks & Steps

1. Create each class as a `final` class with a private constructor and `public static final String`
   fields:
   - `InvoicingConsumers`: `CYCLE = "invoicing.cycle"`, `PAYMENT = "invoicing.payment"`
   - `InvoicingActors`: `INVOICE_PAYMENT = "system/invoice-payment"`
   - `MoneyActors`: `CHARGE = "system/charge"`, `LEDGER_VERIFY = "system/ledger-verify"`
   - `WebhookConsumers`: `DELIVERY = "webhook"`
   - `PlatformActors`: `BOOTSTRAP = "system/bootstrap"`
2. Give each `*Consumers` class a Javadoc saying a name is part of the inbox key and must never
   change once released, and must not be shared by two consumers, because the second would skip
   every event the first has seen. Give each `*Actors` class a Javadoc saying the names are stored
   in the ledger and audit log, so they must not change.
3. Replace the uses and delete the old constants:
   - `CollectChargeUseCase`: `CONSUMER` becomes `InvoicingConsumers.CYCLE`.
   - `ApplyPaymentUseCase`: `CONSUMER` becomes `InvoicingConsumers.PAYMENT`, `ACTOR` becomes
     `InvoicingActors.INVOICE_PAYMENT`.
   - `ChargeUseCase`: `ACTOR` becomes `MoneyActors.CHARGE`.
   - `VerifyLedgerUseCase`: `ACTOR` becomes `MoneyActors.LEDGER_VERIFY`.
   - `RecordDeliveriesUseCase`: `CONSUMER` becomes `WebhookConsumers.DELIVERY`.
   - `BootstrapPlatformAdminUseCase`: keep the `BOOTSTRAP` field, written as
     `new Audit(PlatformActors.BOOTSTRAP, "bootstrap")`.
4. Copy each value from the line being deleted, not from this plan.

## Verification

- `grep -rnE 'static final String (CONSUMER|EVENT_TYPE|ACTOR)\b' billing/contexts` prints nothing.
- `grep -rnE '"(system/[a-z-]+|invoicing\.(cycle|payment)|webhook)"' billing/contexts/*/src/{domain,application}`
  matches only the five new classes.
- `cd billing && ./gradlew test` is green, including `ArchitectureTest` and `ModulithVerificationTest`.
- `git diff` shows no changed string literal, only moved ones.
