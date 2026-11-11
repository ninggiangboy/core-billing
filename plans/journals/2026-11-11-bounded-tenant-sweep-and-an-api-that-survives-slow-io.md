---
title: Bounded tenant sweep and an api that survives slow IO
date: 2026-11-11
summary: "Bounded virtual-thread tenant sweep, webhook pool budget, api virtual threads with a 503 on pool exhaustion, a lag timer, and a 500-tenant load test outside the build."
---

# Bounded tenant sweep and an api that survives slow IO

## What happened

Implemented plans/261111-1501-bounded-tenant-sweep-and-api-virtual-threads as six phases.

- `TenantSweep` now runs tenants on virtual threads behind one semaphore shared by every job
  (`billing.jobs.tenant-parallelism`, default 1). The tenant scope is bound inside each task; the serial
  path keeps the old loop and its per-tenant error handling. Interruption cancels the round and restores the flag.
- Webhook outbound HTTP limits are configuration (`max-connections`, `max-connections-per-route`,
  `connection-request-timeout`); the sender maps a pool wait to `timeout`, and `poll-delay` is PT1S.
- The `api` profile enables virtual threads with a 3s Hikari connection timeout (Hikari wants milliseconds:
  `3000`, not `3s`) and maps `CannotGetJdbcConnectionException` to 503 with `Retry-After: 1`. The worker stays
  on platform threads so its four-thread scheduler survives.
- `billing.webhook.lag` records one sample per successful delivery from the delivery row's creation to the
  completed attempt, through a `DeliveryLagRecorder` port so the application module stays Micrometer-free.
- `WebhookLagLoadTest` (tag `loadtest`, excluded from `test`) seeds 500 tenants, 497 healthy and 3 sleeping 15s,
  and drives the real jobs. `ops/loadtest/run.sh` runs it.

## Evidence

- `./gradlew build`: 453 app-boot tests, no failures; load test not in the build graph.
- Load test at parallelism 8: 497/497 healthy, p50 853ms, p95 972ms, p99 1001ms, max 1015ms,
  `hikaricp.connections.pending` max 0, elapsed 11.1s.
- Same test at parallelism 1: p95 32676ms -> fails the 5s assertion, proving the head-of-line fix.
- Deadlock stress test stays at zero deadlocks as part of the build.
- Docs updated: concurrency-and-locking (rule 10), runtime-profiles (threading + settings), webhooks-and-events
  (latency budget, pool limits, metric).

## Next steps

Commit the change and let the plan ship with its code. Nothing migrated; no schema change.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
