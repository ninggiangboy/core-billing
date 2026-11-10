---
phase: 8
title: "Payments and Stripe"
status: completed
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
- Create: Flyway `V9__payments.sql` (stripe_routes, stripe_configurations, payments, payment_attempts, refunds, stripe_events, stripe_event_attempts, stripe_incidents)
- Create: secret-cipher adapter for the payment module in `app-boot` (reusing the AES-GCM implementation)
- Create: tests with a fake `PaymentProvider`, signature fixtures and an optional Stripe test-mode suite

## Tasks & Steps

- [x] 1. `StripeConfiguration`: mode (Direct or Connect), account id, secret key and webhook secret (encrypted, write-only), publishable key, environment, connection status, connected-at, last-verified-at. A partial unique index allows one active configuration per tenant. Read APIs never return secrets.
- [x] 2. Direct mode: validate the supplied key by a harmless Stripe call, record the account id, expose a per-configuration webhook URL whose secret is that configuration's webhook secret.
- [x] 3. Connect mode: Platform-level Connect settings (platform keys and Connect webhook secret) live in platform configuration; a Tenant starts the account-linking flow, Billing stores the connected account id, tracks connection status and capabilities, and handles the return and refresh paths.
- [x] 4. `PaymentProvider` port (create payment/session, retrieve, refund, verify account) with a Stripe adapter that sends the `Stripe-Account` header for connected accounts (Direct charges), pins the API version, and passes idempotency keys on every write.
- [x] 5. Top-up: create a payment through the Tenant's configuration (hosted Checkout or Payment Element, per the guidance), persist `Payment` in `requires_action`; nothing is credited yet.
- [x] 6. Stripe webhook endpoints (Direct per configuration, Connect at platform level): verify the signature, identify tenant and account, insert into `stripe_events` with `unique (stripe_account, event_id)` (duplicate means skip), then process through the payment state machine.
- [x] 7. Payment state machine: `requires_action → processing → succeeded | failed | canceled`, monotonic (terminal states never regress), late and out-of-order events handled, ambiguous cases resolved by re-fetching the object from Stripe. On `succeeded` call `money.api` credit with an idempotency key derived from the Stripe event and payment, in one transaction with the payment status change and outbox events.
- [x] 8. Refund: full and partial, cumulative refunds never exceed the captured amount, top-up refunds limited to the unspent balance (assumption 6), Stripe refund created with an idempotency key, Ledger updated by `money.api` only after `refund.succeeded` is verified.
- [x] 9. Reconciliation job: for each active configuration, list recent Stripe payments and refunds, compare with local state, record mismatches, and apply corrections through the same verified path rather than a side door.
- [x] 10. Retry and failed-event handling for webhook processing: processing history per Stripe event, bounded retry, visible failures in the admin API.
- [x] 11. Events through the outbox: `payment.succeeded`, `payment.failed`, `refund.succeeded` with schemas in `contracts/events/`.
- [x] 12. Extension check: run the payment application tests against a fake in-memory `PaymentProvider` to show the application layer depends only on the port.
- [x] 13. Never store card data; return only Stripe-hosted URLs and client secrets needed by the browser.

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

## Deviations and results

- **Migration number.** `V9__payments.sql`; `V7` and `V8` were taken by webhooks and pricing. Tables beyond the plan: `stripe_routes` (the one platform-owned lookup, from Stripe account or configuration to tenant, needed because a webhook arrives before any tenant scope exists; it holds no secrets and is listed beside `tenant_domains` in the RLS coverage test), `stripe_event_attempts` (processing history) and `stripe_incidents` (signature failures, unknown accounts, rejected events, reconciliation mismatches, counted when repeated).
- **Stripe SDK, not hand-rolled HTTP.** `com.stripe:stripe-java` 34.0.0, pinned to API version `2026-09-30.endive`; a test fails when an SDK upgrade changes it. Stripe's types live only in `payment.adapter.stripe`, enforced by a new architecture rule with a violating fixture. Events are read from raw JSON, because an event is rendered in the webhook endpoint's own API version, not the SDK's. Payments are Checkout Sessions with no `payment_method_types`; Connect uses Accounts v2 (merchant configuration, Stripe-hosted onboarding link, capability read from `configuration.merchant.capabilities.card_payments`), Direct charges through the `Stripe-Account` header.
- **One credit per payment.** The money idempotency key is `topup:{paymentId}`, not derived from the event, so the several events that report one payment (session completed, intent succeeded, reconciliation) can never credit twice.
- **Refund hold.** To honour assumption 6 the unspent balance is reserved through `money.api` before Stripe is asked and released then debited, in one transaction, when Stripe reports success. That needed `money.api.ReleaseReservation`, whose release reports an already closed hold instead of failing (a failure inside the shared transaction would have rolled back the refund). The money context caps a hold at seven days; a refund settled later is debited only if the balance is still there, otherwise the event is flagged and the wallet is never overdrawn.
- **No Stripe call under a lock.** The settlement never calls Stripe; the processor and reconciliation read what they need (the intent of a delayed-method session, a connected account's state) before the transaction opens.
- **Events from outside Billing** (no Billing reference, unknown intent, a refund of a payment Billing never made) are ignored without retries or incidents, because the Tenant may use the account for other things. A refund made in the Dashboard against a Billing payment is flagged, not applied.
- **Disconnect and key rotation.** A payment belongs to a Stripe account: routes and webhook handling survive disconnecting, so in-flight payments and refunds settle; relinking the same account moves the route to the new configuration; another tenant cannot take an account while this one holds it. Payments of an account that is no longer connected cannot be refunded through Billing.
- **Connect events.** v1 `account.updated` is handled; v2 thin events are not consumed. Capability status is refreshed on demand, on the return path, and by reconciliation.
- **Open endpoints.** `@PublicEndpoint` marks the two webhook endpoints; the access-rule test lists them exactly, the body is read with a size limit, and the security config allows `/stripe/webhooks/**` only.
- **Verification.** 100+ new tests: domain state machine and signature checks (valid, wrong secret, stale, tampered, missing; Direct and Connect secrets) with no Spring context; the adapter against a local stand-in for Stripe (headers, idempotency keys, API version, Checkout shape, error translation without echoing Stripe text); payment flows against real PostgreSQL with an in-memory provider (replay, out-of-order, late, contradiction, delayed methods, declines and expiry, amount mismatch, cross-tenant references, concurrent events, refunds over captured and unspent amounts, expired holds, rotation, disconnect in flight, reconciliation); the webhook endpoints and the configuration, top-up and refund API over HTTP with real tokens (permissions, no secret in any response or log, redirect changes nothing); every new event validated against its schema. An independent review found five defects (hold expiry rolling back a refund, orphaned in-flight payments, Stripe calls under locks, events stuck in `RECEIVED`, foreign events retried); all are fixed and tested. An optional test-mode suite against a real Stripe sandbox was not written: no keys are available here.
- **Not done / later.** No retention job yet for `stripe_events` payloads (they can hold customer details Stripe returns) or the outbox; the webhook endpoints rely on infrastructure rate limiting; the retry job relies on the processor's row lock rather than `skip locked` across instances.
