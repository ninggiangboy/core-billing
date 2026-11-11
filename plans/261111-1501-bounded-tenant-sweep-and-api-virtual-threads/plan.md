---
title: "Bounded tenant sweep and an api that survives slow IO"
description: "Run every background job over tenants on bounded virtual threads, let the api profile use virtual threads behind a short connection timeout, add a webhook lag metric, and prove event-to-webhook p95 under 5s at 500 tenants with a load test that stays out of the build."
status: completed
priority: P1
effort: 9h
branch: dev
tags: [backend, performance, reliability, database]
created: 2026-11-11
---

# Bounded tenant sweep and an api that survives slow IO

## Outcome, constraints, non-goals, acceptance

- **Outcome.** With 500 active tenants, a slow webhook receiver no longer delays the webhooks of every tenant behind
  it, and the api keeps serving while Stripe or Keycloak is slow, answering a fast 503 instead of queueing requests
  behind a connection pool. One configurable semaphore bounds how many tenants any background job touches at once, so
  the extra concurrency can never outrun the worker's database pool. A `billing.webhook.lag` timer makes the
  event-to-webhook delay visible in production, and a load test outside the build reports the p95 at 500 tenants.
- **Constraints.**
  - Keep one transaction per outbox row, the `skip locked` claims, tenant RLS scope and the documented lock order in
    `docs/concurrency-and-locking.md`. No change to any money lock order.
  - Tenant scope is bound inside every task, because `ScopedTenantContext` holds it in a `ScopedValue`, which does not
    cross a thread boundary.
  - Parallelism is bounded by one semaphore shared by every job, with permits at most the worker pool size minus two.
  - Virtual threads on the `api` profile only, and never without a short Hikari `connection-timeout`.
  - No new Gradle module, broker, cache or schema change. No preview JDK features.
- **Non-goals.** WebFlux or R2DBC, Kafka or any broker, parallelism inside a single tenant's money flows,
  cross-tenant "due work" queries that bypass RLS, a rewrite of the scheduler, tenant sharding.
- **Acceptance.**
  - A test shows one slow tenant does not delay the others and that concurrency never exceeds the permits.
  - The `worker` profile runs background work with `billing.jobs.tenant-parallelism` permits, and the multi-instance
    test pins it to its own pool size.
  - `billing.webhooks` exposes pool and connection-request limits; the worst case of connection request, connect and
    read fits inside the 15s delivery deadline.
  - `application-api.yml` enables virtual threads and a 3s Hikari connection timeout, and a connection-pool timeout
    becomes a 503 with `Retry-After`.
  - `billing.webhook.lag` is a timer with a sample per successful delivery.
  - `ops/loadtest/run.sh` seeds 500 tenants with 3 receivers that sleep 15s and reports p50/p95/p99 lag, failing when
    the healthy p95 is 5s or more, while `hikaricp.connections.pending` stays at zero.
  - `./gradlew build` passes and does not run the load test; the deadlock stress test still leaves
    `pg_stat_database.deadlocks` at zero.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Bounded tenant sweep](./phase-01-bounded-tenant-sweep.md) | Pending |
| 2 | [Webhook time budget and connection pool](./phase-02-webhook-time-budget.md) | Pending |
| 3 | [Virtual threads on the api profile](./phase-03-api-virtual-threads.md) | Pending |
| 4 | [Webhook lag metric](./phase-04-webhook-lag-metric.md) | Pending |
| 5 | [Load test outside the build](./phase-05-load-test.md) | Pending |
| 6 | [Docs and full verification](./phase-06-docs-and-verification.md) | Pending |

Each phase: run the narrowest test first, then `./gradlew :apps:app-boot:test`, and commit. Phases 2, 3 and 4 are
independent of each other; phase 5 needs phase 1 to show the improvement, and phase 6 needs all of them.

## Where the numbers come from

The 5s target is a conversation budget, not a measurement:

| Step | Budget |
|---|---|
| Event committed to the outbox, until the relay tick sees it | at most `billing.outbox.poll-delay`, 1s |
| Consumers record the webhook deliveries | one transaction per outbox row |
| Until the webhook tick picks the delivery up | at most `billing.webhooks.poll-delay`, 1s |
| HTTP send | connection request 2s, connect 3s, read 10s, inside a 15s deadline |

Only the load test in phase 5 turns this into evidence.

## Dependencies

- Assumes the runtime split in [261111-1620-api-worker-runtime-split](../261111-1620-api-worker-runtime-split/plan.md)
  is in place: `api`, `worker` and `migrate` profiles, no jobs in `api`, four scheduler threads in `worker`.
- `docs/concurrency-and-locking.md` is the authority for lock order and retryable SQLSTATEs; this plan adds rules, it
  does not change existing ones.

<!-- slug: bounded-tenant-sweep-and-api-virtual-threads -->
