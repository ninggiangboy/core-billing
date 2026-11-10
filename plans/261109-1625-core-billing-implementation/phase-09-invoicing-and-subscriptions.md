---
phase: 9
title: "Invoicing and subscriptions"
status: pending
priority: P2
effort: "6d"
dependencies: [7, 8]
---

# Phase 9: Invoicing and subscriptions

## Goal

Generate Invoices from Charges and Subscriptions, support Postpaid aggregation cycles and debt payment, and run recurring Subscriptions with renewal events.

## Context

Source: [invoicing-and-subscriptions.md](../../docs/invoicing-and-subscriptions.md), [wallet-ledger-and-charging.md](../../docs/wallet-ledger-and-charging.md). Billing never provisions or stops Resources; it only reports `subscription.renewed` and `subscription.payment_failed`. The `invoicing` module consumes money events asynchronously; the renewal charge is on the money path and may call `money.api` and `pricing.api` synchronously.

## Files to Create / Modify

- Create: `billing/invoicing/` (`Invoice`, `InvoiceItem`, `Subscription`, `SubscriptionEvent`, cycle and renewal jobs, REST)
- Create: Flyway `V9__invoicing.sql`; event schemas `invoice.created`, `invoice.paid`, `subscription.renewed`, `subscription.payment_failed`

## Tasks & Steps

- [ ] 1. `Invoice` aggregate: draft, open, paid, void, uncollectible; items with source reference (charge or subscription period); totals in the wallet currency; per-tenant invoice number from a locked counter row; payment status and history.
- [ ] 2. Postpaid aggregation: consume `charge.succeeded` for Postpaid wallets into unbilled lines (inbox-deduplicated); a cycle job closes the period and creates the Invoice, then publishes `invoice.created`.
- [ ] 3. Invoice payment: create a payment for an Invoice through `payment.api`; on the verified `payment.succeeded` event, mark the Invoice paid, credit the debt payoff through `money.api` for Postpaid, publish `invoice.paid`.
- [ ] 4. Invoices from standalone Charges when the Tenant asks for one, using the same aggregate.
- [ ] 5. `Subscription` aggregate: plan reference, billing cycle anchor, status (active, past_due, canceled), renewal schedule, cancellation at period end.
- [ ] 6. Renewal job (`for update skip locked`): compute the price from `pricing.api`, charge the Prepaid wallet through `money.api` or add a Postpaid invoice line, extend the period, record a `SubscriptionEvent`, publish `subscription.renewed`.
- [ ] 7. Failure path: insufficient funds or payment failure moves the subscription to past_due, publishes `subscription.payment_failed`, retries on a bounded schedule, and never stops any Resource.
- [ ] 8. Combined models: a Hybrid plan produces a subscription base charge plus usage lines on the same Invoice.
- [ ] 9. REST for Tenant Systems (create subscription, cancel, list invoices) and administrators (invoices, subscriptions, void with reason).
- [ ] 10. Idempotency on renewal (key from subscription and period) so a re-run of the job cannot charge a period twice.

## Verification

- Postpaid cycle test: several charges in a period produce one Invoice with correct totals; running the cycle job twice produces one Invoice.
- Renewal job run twice for one period charges once; failure then success sequence ends in `active` with both events recorded.
- Invoice paid only after a verified payment event (no path marks paid from a client request).
- Hybrid plan test produces the expected combined Invoice.

## Risks and rollback

- Clock and timezone edge cases at period boundaries: store periods in UTC, test month ends and leap days.
- Voiding an invoice after payment is out of scope; refunds go through the Refund path of phase 8.
