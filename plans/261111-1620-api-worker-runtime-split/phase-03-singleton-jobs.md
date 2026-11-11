---
phase: 3
title: "Singleton heavy jobs and bootstrap"
status: done
---

# Singleton heavy jobs and bootstrap

## Goal

Verify ledger and Stripe reconcile run on one worker at a time; a second verify run is a no-op; the
platform bootstrap runs once even when two instances start together. No lease holds a database lock
while work runs.

## Work

- `MoneyJobs.verifyLedgers`: acquire `verify-ledger` lease, claim the per-day run record per tenant
  before the work, renew between tenants, release in `finally`.
- `PaymentJobs.reconcileWithStripe`: acquire `stripe-reconcile` lease, renew between tenants,
  release in `finally`.
- `PlatformBootstrap`: run under a `platform-bootstrap` lease; skip when another instance owns it;
  release in `finally`; never hold a transaction across the Keycloak call.
- A worker-only housekeeping job removes expired rate-limit buckets in bounded `skip locked` batches.

## Verification

- Two concurrent verify runs produce one mismatch count and one run record per tenant and day.
- Two concurrent bootstrap runs both complete; exactly one performs the seed work.
- Reconcile skips while the lease is held by another owner.
