---
title: "Split the runtime into api and worker profiles"
description: "One jar, three Spring profiles: HTTP api, background worker and Flyway migrate. Singleton heavy jobs through time-bounded leases, a shared Postgres rate limiter, and no new deadlock risk."
status: in-progress
priority: P1
branch: dev
tags: [backend, deployment, database, reliability]
created: 2026-11-11
---

# Split the runtime into api and worker profiles

## Outcome, constraints, non-goals, acceptance

- **Outcome.** Two or more `api` instances and two or more `worker` instances run on one PostgreSQL
  with no job downtime, singleton heavy jobs, no duplicate work and no new deadlock risk. A separate
  `migrate` run owns Flyway. `dev` and `test` keep today's all-in-one behaviour when no profile is set.
- **Constraints.**
  - One jar and Spring profiles only. No separate Gradle modules, Dockerfile or platform manifests.
  - No change to HTTP or event contracts. Schema changes only through Flyway.
  - Coordination state (leases, run records, rate-limit counters) lives in short, single-row
    transactions outside every business transaction. No blocking advisory lock.
  - Consistent lock order; `40P01` and `55P03` are retryable; `lock_timeout` and
    `idle_in_transaction_session_timeout` are set per profile.
  - No plan or phase identifiers in code, test names or commits.
- **Non-goals.** Message broker, Redis, separate Gradle modules, tenant sharding, splitting usage
  ingestion, making the five skip-locked jobs singleton, Dockerfile and platform manifests.
- **Acceptance.**
  - The `api` profile has no job beans; the `worker` profile has no business controllers; `migrate`
    migrates and exits.
  - Verify ledger and Stripe reconcile run on one worker at a time through a lease; a per-day run
    record makes a second verify run a no-op, so `billing.ledger.mismatch` is not double counted.
  - Platform bootstrap runs once under a lease; two simultaneous starts never crash either.
  - API key rate limiting uses a shared Postgres store and is correct across two api instances.
  - The lock hierarchy is in `docs/concurrency-and-locking.md`; a test fails if a blocking advisory
    lock appears.
  - `./gradlew test`, ArchUnit and the jOOQ drift test are green; `contracts/` is unchanged.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | Coordination schema and stores: job leases, run records, shared Postgres rate limiter | done |
| 2 | Runtime profiles: api, worker, migrate, probes, pools, shutdown, per-profile timeouts | done |
| 3 | Singleton heavy jobs and platform bootstrap under a lease | done |
| 4 | Lock safety net: retryable SQLSTATEs, 503, lock hierarchy documentation | done |
| 5 | Concurrency and multi-instance tests: profile separation, lease contention, failover, deadlock stress | pending |

Each phase: regenerate jOOQ when the schema changes, run the narrowest tests then
`./gradlew :app-boot:test`, and commit.
