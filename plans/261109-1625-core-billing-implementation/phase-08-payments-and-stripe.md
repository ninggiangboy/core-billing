---
phase: 8
title: "Payments and Stripe"
status: pending
priority: P1
effort: "7d"
dependencies: [5, 6]
---

# Phase 8: Payments and Stripe

## Goal

Run per-Tenant Stripe configuration (Direct and Connect), top-ups, payments and refunds with Direct charges, and apply results to the Ledger only from verified Stripe events.

## Context

Source: [payments-and-stripe.md](../../docs/payments-and-stripe.md), [architecture.md](../../docs/architecture.md) (Direct charges decision). Load the Stripe best-practices guidance at execution time to choose between Checkout Sessions and PaymentIntents, and the current Connect account model; do not rely on memory of Stripe API shapes.

## Files to Create / Modify

- Create: `billing/payment/` (`StripeConfiguration`, `Payment`, `Refund`, `PaymentProvider` port, Stripe adapter, webhook inbox, reconciliation job, REST)
- Create: Flyway `V8__payments.sql` (stripe_configurations, payments, refunds, stripe_events, payment_attempts)
- Create: secret-cipher adapter for the payment module in `app-boot` (reusing the AES-GCM implementation)
- Create: tests with a fake `PaymentProvider`, signature fixtures and an optional Stripe test-mode suite

## Tasks & Steps

- [ ] 1. `StripeConfiguration`: mode (Direct or Connect), account id, secret key and webhook secret (encrypted, write-only), publishable key, environment, connection status, connected-at, last-verified-at. A partial unique index allows one active configuration per tenant. Read APIs never return secrets.
- [ ] 2. Direct mode: validate the supplied key by a harmless Stripe call, record the account id, expose a per-configuration webhook URL whose secret is that configuration's webhook secret.
- [ ] 3. Connect mode: Platform-level Connect settings (platform keys and Connect webhook secret) live in platform configuration; a Tenant starts the account-linking flow, Billing stores the connected account id, tracks connection status and capabilities, and handles the return and refresh paths.
- [ ] 4. `PaymentProvider` port (create payment/session, retrieve, refund, verify account) with a Stripe adapter that sends the `Stripe-Account` header for connected accounts (Direct charges), pins the API version, and passes idempotency keys on every write.
- [ ] 5. Top-up: create a payment through the Tenant's configuration (hosted Checkout or Payment Element, per the guidance), persist `Payment` in `requires_action`; nothing is credited yet.
- [ ] 6. Stripe webhook endpoints (Direct per configuration, Connect at platform level): verify the signature, identify tenant and account, insert into `stripe_events` with `unique (stripe_account, event_id)` (duplicate means skip), then process through the payment state machine.
- [ ] 7. Payment state machine: `requires_action → processing → succeeded | failed | canceled`, monotonic (terminal states never regress), late and out-of-order events handled, ambiguous cases resolved by re-fetching the object from Stripe. On `succeeded` call `money.api` credit with an idempotency key derived from the Stripe event and payment, in one transaction with the payment status change and outbox events.
- [ ] 8. Refund: full and partial, cumulative refunds never exceed the captured amount, top-up refunds limited to the unspent balance (assumption 6), Stripe refund created with an idempotency key, Ledger updated by `money.api` only after `refund.succeeded` is verified.
- [ ] 9. Reconciliation job: for each active configuration, list recent Stripe payments and refunds, compare with local state, record mismatches, and apply corrections through the same verified path rather than a side door.
- [ ] 10. Retry and failed-event handling for webhook processing: processing history per Stripe event, bounded retry, visible failures in the admin API.
- [ ] 11. Events through the outbox: `payment.succeeded`, `payment.failed`, `refund.succeeded` with schemas in `contracts/events/`.
- [ ] 12. Extension check: run the payment application tests against a fake in-memory `PaymentProvider` to show the application layer depends only on the port.
- [ ] 13. Never store card data; return only Stripe-hosted URLs and client secrets needed by the browser.

## Verification

- Signature tests: valid, wrong secret, stale timestamp and tampered body; each handled correctly for Direct and Connect.
- Replaying the same Stripe event twice credits the wallet once; delivering `payment_intent.succeeded` before the local payment record exists, and out-of-order failure-after-success, leave a correct final state.
- Browser redirect to the success URL changes no balance (test hits the return URL and asserts no Ledger entry).
- Refund over the captured amount or over the unspent balance is rejected; a partial then full refund sequence stays within limits.
- Secrets never appear in API responses, logs or exception messages in tests.
- Optional Stripe test-mode suite (skipped without keys) creates a payment on a test account and confirms the full path.

## Risks and rollback

- Stripe API drift: pin the API version and keep Stripe types inside the adapter.
- A bad webhook secret would drop legitimate events: failed verification is recorded and surfaced rather than swallowed, and reconciliation catches gaps.
- Rollback is disabling top-up creation per tenant by configuration; ledger data already written is never rewritten.
