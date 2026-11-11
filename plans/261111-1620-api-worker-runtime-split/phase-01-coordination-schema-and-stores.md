---
phase: 1
title: "Coordination schema and stores"
status: done
---

# Coordination schema and stores

## Goal

Add the only shared mutable state the runtime needs outside a business transaction: a job lease
table, a per-key run record table, and a Postgres token-bucket rate limiter. Every statement is
short and touches one row.

## Work

- Migration `V16__runtime_coordination.sql`:
  - `job_leases (name primary key, owner, acquired_at, expires_at)`.
  - `job_runs (job, run_key, tenant_id references tenants, started_at, primary key (job, run_key, tenant_id))`.
  - `rate_limit_buckets (bucket_key primary key, tokens, updated_at, expires_at)` plus an expiry index.
  - `try_acquire_rate_limit(key, burst, refill_per_second, ttl_seconds) returns boolean`, a single
    function call that locks one bucket row and decides atomically.
- `boot.infrastructure.coordination.JobLeases`: acquire (insert-or-take-expired), renew, release;
  each a one-statement `REQUIRES_NEW` transaction.
- `boot.infrastructure.coordination.JobRuns`: `claim(job, runKey, tenantId)` with
  `on conflict do nothing`.
- `boot.infrastructure.coordination.PostgresRateLimiter`: implements `RateLimiter` through the
  function, owns removal of expired buckets in bounded `skip locked` batches.
- Wire the rate limiter into `Authentication`; remove the in-memory implementation from the bean graph.
- Regenerate `jooq-schema` from the migration and add the new tables to the ArchUnit table owner map.

## Verification

- Unit: lease acquired once; a second owner is refused; expiry lets it take over; release frees it.
- Unit: run record claims once per key.
- Integration: two limiter instances share the burst; refill restores capacity; expiry cleanup leaves
  live buckets.
- `./gradlew :app-boot:test --tests '*JooqSchemaTest'` passes after regeneration.
