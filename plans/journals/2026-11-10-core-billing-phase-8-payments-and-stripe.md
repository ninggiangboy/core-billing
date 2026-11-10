---
title: "Core Billing phase 8: payments and Stripe"
date: 2026-11-10
summary: "Per-tenant Stripe (Direct and Connect), top-ups, refunds and verified-webhook settlement; an independent review found and we fixed five defects."
---

# Core Billing phase 8: payments and Stripe

## What happened

Phase 8 is built and verified (full `./gradlew build` green) but not committed. It adds the `payment` module: Stripe configuration in Direct and Connect modes, Checkout-based top-ups, full and partial refunds, signature-verified webhooks stored once and processed with bounded retry and history, an hourly and on-demand reconciliation, and the `payment.succeeded`, `payment.failed` and `refund.succeeded` events with schemas.

## Problems worth remembering

- A paid Checkout never recorded its payment intent id, so refunds had nothing to refund. The integration tests caught it; unit tests on the domain could not have.
- Test Stripe ids were reused across tenants and tripped the global unique index (real ids are unique); in the full run, extra Spring contexts also exhausted Postgres connections until the test pools were made small.
- An independent review found five real defects after everything was green: swallowing a `ConflictException` from a nested unit of work marks the shared transaction rollback-only, so a refund could never settle after its hold expired; disconnecting or rotating a key orphaned payments in flight; Stripe calls ran inside transactions holding row locks; events left in `RECEIVED` were never retried; Stripe activity Billing did not start was retried eight times with an incident each. All fixed with tests.

## Decisions

Recorded in the phase file; to be written back into `docs/architecture.md` in phase 14.

- Official Stripe SDK, pinned API version, types confined to one adapter package by an architecture rule; events read from raw JSON.
- One credit per payment (`topup:{paymentId}` key). Refunds hold the unspent balance first and debit only on Stripe's confirmation.
- A payment belongs to a Stripe account, not a configuration row, so rotation and disconnect do not orphan it.
- `stripe_routes` is the one new platform-owned lookup table.

## Next steps

- Decide whether a refund Stripe has confirmed may overdraw a Prepaid wallet after the seven-day hold expired. Today it never does: the event is flagged for a person.
- Add retention for `stripe_events` payloads and the outbox, and rate limiting for the open webhook endpoints.
- Run the Stripe test-mode suite once sandbox keys exist.
- Phase 9 should reuse `payment` through the money api only; phase 10 needs a top-up entry point for the portal.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
