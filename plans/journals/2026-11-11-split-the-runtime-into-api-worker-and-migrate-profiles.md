---
title: "Split the runtime into api, worker and migrate profiles"
date: 2026-11-11
summary: "One jar with three profiles, a job lease table, a shared Postgres rate limiter, and zero-deadlock stress proof."
---

# Split the runtime into api, worker and migrate profiles

## What happened

- `V16__runtime_coordination.sql` adds `job_leases`, `job_runs` and `rate_limit_buckets`, plus the
  `try_acquire_rate_limit` function. The jOOQ schema was regenerated and committed.
- `JobLeases`, `JobRuns` and `PostgresRateLimiter` live in `boot.infrastructure.coordination`. Each
  statement is a one-row `REQUIRES_NEW` transaction; no row is held while work runs.
- `api`, `worker` and `migrate` profiles now shape one jar. Jobs and `@EnableScheduling` are gated to
  `!api & !migrate`; business controllers to `!worker & !migrate`; bootstrap skips `migrate`. The
  default profile keeps the all-in-one dev/test behaviour.
- Ledger verification and Stripe reconciliation take a lease. Verification also claims one
  `job_runs` row per tenant per UTC day, so a second run cannot double count
  `billing.ledger.mismatch`. Platform bootstrap runs under the same lease mechanism.
- `ApiExceptionHandler` maps `PessimisticLockingFailureException` (40P01/55P03) to 503 with
  `Retry-After`.
- `docs/runtime-profiles.md` and `docs/concurrency-and-locking.md` record the profiles, budgets and
  lock order; a test rejects blocking advisory locks.

## Decision

A lease table wins over an advisory lock: ownership is a deadline, so a slow job never holds a lock
and no hold-and-wait edge exists. The rate limiter moved from an in-memory bucket to one shared
Postgres function call because two api instances must spend from the same bucket.

## Verification

- `./gradlew build` green, including ArchUnit, the jOOQ drift test and the new profile tests.
- `DeadlockStressIntegrationTest`: charge, renewal, reservation expiry, cycle close and ten Stripe
  settlements on one wallet; `pg_stat_database.deadlocks = 0`.
- `MultiInstanceRuntimeIntegrationTest`: two api + two worker contexts on one Testcontainers
  Postgres, one worker stopped mid-webhook; outbox, webhook and renewal finish with one charge and
  one invoice.

## Next steps

- Measure the starting lock budgets under real API load; the current 30s api / 5s worker / 3s
  migrate values are documented starting points.
- Dockerfile and platform manifests remain out of scope.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
