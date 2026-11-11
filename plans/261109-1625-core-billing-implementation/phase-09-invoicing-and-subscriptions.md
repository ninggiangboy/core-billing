---
phase: 9
title: "Invoicing and subscriptions"
status: completed
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

- [x] 1. `Invoice` aggregate: draft, open, paid, void, uncollectible; items with source reference (charge or subscription period); totals in the wallet currency; per-tenant invoice number from a locked counter row; payment status and history.
- [x] 2. Postpaid aggregation: consume `charge.succeeded` for Postpaid wallets into unbilled lines (inbox-deduplicated); a cycle job closes the period and creates the Invoice, then publishes `invoice.created`.
- [x] 3. Invoice payment: create a payment for an Invoice through `payment.api`; on the verified `payment.succeeded` event, mark the Invoice paid, credit the debt payoff through `money.api` for Postpaid, publish `invoice.paid`.
- [x] 4. Invoices from standalone Charges when the Tenant asks for one, using the same aggregate.
- [x] 5. `Subscription` aggregate: plan reference, billing cycle anchor, status (active, past_due, canceled), renewal schedule, cancellation at period end.
- [x] 6. Renewal job (`for update skip locked`): compute the price from `pricing.api`, charge the Prepaid wallet through `money.api` or add a Postpaid invoice line, extend the period, record a `SubscriptionEvent`, publish `subscription.renewed`.
- [x] 7. Failure path: insufficient funds or payment failure moves the subscription to past_due, publishes `subscription.payment_failed`, retries on a bounded schedule, and never stops any Resource.
- [x] 8. Combined models: a Hybrid plan produces a subscription base charge plus usage lines on the same Invoice.
- [x] 9. REST for Tenant Systems (create subscription, cancel, list invoices) and administrators (invoices, subscriptions, void with reason).
- [x] 10. Idempotency on renewal (key from subscription and period) so a re-run of the job cannot charge a period twice.

## Verification

- Postpaid cycle test: several charges in a period produce one Invoice with correct totals; running the cycle job twice produces one Invoice.
- Renewal job run twice for one period charges once; failure then success sequence ends in `active` with both events recorded.
- Invoice paid only after a verified payment event (no path marks paid from a client request).
- Hybrid plan test produces the expected combined Invoice.

## Risks and rollback

- Clock and timezone edge cases at period boundaries: store periods in UTC, test month ends and leap days.
- Voiding an invoice after payment is out of scope; refunds go through the Refund path of phase 8.

## Deviations and results

- **Migrations.** `V11__invoices.sql`, `V12__invoice_payments.sql` (the `payments` table gains `invoice_id` and the `INVOICE` purpose), `V13__subscriptions.sql`, `V14__subscription_retry_bound.sql` and `V15__subscription_renewal_once.sql`; `V9` and `V10` were taken. Tables: `invoices`, `invoice_items` (a line with no invoice is an unbilled line), `invoice_payments`, `invoice_counters`, `subscriptions`, `subscription_events`.
- **An invoice asks for payment of existing debt; it never creates any.** Every line is backed by a succeeded Charge, a Charge is on one line at most (unique per tenant), and paying credits the wallet while voiding leaves it alone. This follows from the Postpaid model: the Charge is what records the debt, so an invoice line that was not a Charge could be paid without ever having been owed.
- **Postpaid renewal charges the wallet** instead of adding a line that is not a Charge (the plan's wording). The renewal Charge records the debt and reaches the cycle invoice through the normal charge collection as a `SUBSCRIPTION_PERIOD` line, so paying the invoice credits exactly the debt it covers. A Prepaid renewal is a Charge only; it gets no invoice.
- **Invoice on request for a Prepaid Charge** is created `PAID` with settlement `WALLET`: the wallet paid it when the Charge was made. This is the only way an invoice is paid without a payment event, and it follows from a ledger entry, not a client request. A Postpaid Charge opens an invoice, and a line already waiting for its cycle moves onto it.
- **Invoice payment reuses `payment`.** New `payment.api.invoice.StartInvoicePayment`; the payment context keeps its Checkout path (extracted as `CheckoutOpener`, shared with top-ups) and a payment of an invoice succeeds **without a ledger entry**: `PaymentSettlement` records it and announces `payment.succeeded` with the `invoiceId`, and invoicing credits the wallet (key `invoice-payoff:{paymentId}`) and marks the invoice paid from that event. Money that arrives for an invoice that is already paid or void is still credited. A payment of an invoice is **not refundable** through Billing (the debt would come back while the invoice stayed paid); the plan's risk note assumed the Refund path would apply.
- **Periods are charged in advance and counted from the anchor.** Creating a subscription charges period 0 at once (reported as `subscription.renewed` with `periodNumber` 0); month ends clamp and return (`BillingCycle.plus`), tested with month-end and leap-day anchors. `BillingCycle` moved to `pricing.api.plan` with the period arithmetic; the new read-only `PlanPricing` api answers the recurring price at a moment, so `invoicing` never reads plan internals.
- **Idempotency.** The Charge key is `renewal:{subscription}:{period}:{attempt}`: a Charge that failed for lack of funds is remembered by its key, so the next attempt needs a new one. With the locked subscription row and the schedule moving in the Charge's transaction, a re-run cannot charge a period twice; a unique index on the renewed record backs it at the database.
- **Failure path.** A refused or unpriced period makes the subscription `PAST_DUE`, is recorded and announced (`subscription.payment_failed`, with the attempt and the next attempt) and retried on a capped exponential backoff (`renewal-max-attempts` 5, base 1 hour, cap 1 day). After the last attempt Billing stops on its own (`willRetry: false`) and the subscription stays past due; a success at any attempt returns it to `ACTIVE`. A subscription that throws is set aside so the others still renew.
- **Hybrid plans.** Charging usage used to price the base amount again on every rating; `PlanVersion.quoteUsage` now prices only the usage components (an estimate of a whole cycle still includes the base). A Hybrid base is rounded but not cut by the plan's minimum and maximum charge, which bound the usage part. This changes phase 7 behaviour for Hybrid plans only.
- **Consumers must not block each other.** All consumers of an outbox event share one transaction, so the charge consumer ignores a payload it cannot read; an earlier version threw and blocked webhook delivery, which the full build caught.
- **REST and permissions.** Four new permissions: `invoice:read`, `invoice:create`, `subscription:read`, `subscription:manage` (administrators and Tenant Systems) and `invoice:manage` (administrators only, never a Tenant System). Paying an invoice needs `payment:create`. Both OpenAPI contracts are regenerated. Not in the plan but needed: an administrator can also write an invoice off as uncollectible (the status exists and the domain moves to it).
- **Events.** Schemas `invoice.created`, `invoice.paid`, `subscription.renewed`, `subscription.payment_failed`; `payment.succeeded` makes `ledgerEntryId` optional and both payment events gain an optional `invoiceId`. The contract test produces every one through the real code.
- **Verification.** New tests at three levels: domain and in-memory-port unit tests (invoice rules, subscription schedule and cancellation, month-end and leap-day periods, the renewal logic against fake ports including a throwing subscription), PostgreSQL integration tests (reload-and-compare, gap-free numbering and rollback, the cycle run twice and by two workers, late charges, invoice payment from verified events with replays, double payments and a failed payment, renewals once per period, bounded retries, failure then success, Postpaid lines on the invoice, a Hybrid plan's base and usage on one invoice, a failure after the Charge rolling it back) and HTTP tests with real tokens (permissions, mapped-customer visibility, no request marks an invoice paid). The full `./gradlew build` is green.
- **Not done / later.** There is no manual "retry now" for a subscription whose attempts ran out: the Tenant System cancels it or the customer subscribes again, which is an open product question. Invoices have no document rendering (PDF) and no due date or payment terms, because the specification defines neither. Retention of `invoice_payments`, `subscription_events` and the outbox is not handled. The write-back of the new synchronous edges (`invoicing` to `money`, `payment` and `pricing`, all through their `api` packages) into `docs/architecture.md` is left to phase 14, as for the earlier phases.
