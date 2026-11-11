---
title: "Core Billing phase 9: invoicing and subscriptions"
date: 2026-11-11
summary: "Invoices from Postpaid cycles and on request, invoice payment from verified events, recurring subscriptions with bounded retries; one full-build failure showed consumers must not block each other."
---

# Core Billing phase 9: invoicing and subscriptions

## What happened

Phase 9 is built, verified (full `./gradlew build` green) and committed task by task. It adds the `invoicing` module: invoices with gap-free numbers, Postpaid aggregation by calendar period, invoices on request for a Charge, payment of an invoice through the existing Stripe path, subscriptions that renew once per period with a bounded retry schedule, and the REST API for all of it. Four events have schemas: `invoice.created`, `invoice.paid`, `subscription.renewed`, `subscription.payment_failed`.

## Problems worth remembering

- The model that makes everything else simple is that an invoice asks for payment of debt a Charge already recorded. Adding a line that was not a Charge (the plan's Postpaid renewal wording) would have let an invoice be paid without anything having been owed, so renewals are Charges and reach the invoice through collection.
- A Charge that failed for lack of funds is remembered by its idempotency key, so a renewal retry with the same key would replay the failure forever. The key now names the attempt as well as the period.
- Charging usage under a Hybrid plan priced the base amount again on every rating (phase 7). It was invisible until a subscription started billing the base; the Hybrid test would have double-billed.
- The full build, not the focused runs, caught the worst defect: all consumers of an outbox event share one transaction, so my charge consumer throwing on an unreadable payload blocked the webhook delivery of that event. Consumers now ignore what they cannot read.
- A new HTTP test reused a Stripe key whose last characters the fake provider turns into an account id, which collided with another test's account across tenants. Test keys need a random part.

## Decisions

Recorded in the phase file; to be written back into `docs/architecture.md` in phase 14.

- Periods are charged in advance, counted from an anchor so month ends never drift; creating a subscription charges period 0.
- Paying an invoice credits the wallet from the verified `payment.succeeded` event, not inside the payment context; a payment of an invoice is not refundable.
- Retries are capped and bounded; after the last one the subscription stays past due for the Tenant System to decide.
- Voiding an invoice never touches the wallet; a paid invoice cannot be voided.

## Next steps

- Decide how a subscription whose attempts ran out recovers: a manual "retry now", or cancel and subscribe again (today only the second works).
- Decide whether invoices need a due date, payment terms or a rendered document; the specification defines none.
- Add retention for `invoice_payments`, `subscription_events` and the outbox.
- Phase 10 can reuse the invoice payment entry point for the Customer Portal; phase 11 should consume `subscription.renewed` and `subscription.payment_failed` without Billing ever stopping a Resource.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
