---
phase: 6
title: "Docs and full verification"
status: pending
priority: P2
effort: "1h"
dependencies: [1, 2, 3, 4, 5]
---

# Phase 6: Docs and full verification

## Goal

Write the new rules where the next reader will look for them, then prove on a clean tree that the build, the
concurrency guarantees and the load test all hold.

## Context

Three documents own facts this plan changes or relies on:

- `docs/concurrency-and-locking.md` owns lock order and the timeout budget per profile. Bounded parallelism adds a rule
  about how much work a worker may run at once and why tenant scope must be bound inside a task.
- `docs/runtime-profiles.md` owns what each Spring profile enables. Virtual threads and the short connection timeout
  are profile facts.
- `docs/webhooks-and-events.md` owns the delivery contract. The poll budgets, the outbound pool limits and the new
  metric belong next to the retry and at-least-once description.

No schema change is involved, so the jOOQ drift test and the committed generated schema need no regeneration.

## Files to Create / Modify

- Modify: `docs/concurrency-and-locking.md`
- Modify: `docs/runtime-profiles.md`
- Modify: `docs/webhooks-and-events.md`

## Tasks & Steps

1. In `docs/concurrency-and-locking.md`, add a rule to the numbered "Rules" list: background jobs walk tenants on
   virtual threads under one semaphore shared by every job, whose permits are `billing.jobs.tenant-parallelism` and
   must stay below the worker pool size; the tenant scope is bound inside each task because `ScopedValue` does not
   cross threads; nothing inside a single tenant's money flow runs in parallel; a permit is held for the length of the
   task, including an outbound HTTP call.
2. In `docs/runtime-profiles.md`, add to the `api` profile section: virtual threads, the 3s Hikari connection timeout,
   and the 503 with `Retry-After` that a connection-pool timeout produces. Add to the `worker` section that it
   deliberately stays on platform threads, because enabling virtual threads there would replace the four-thread
   scheduler and drop `spring.task.scheduling.pool.size`.
3. In `docs/webhooks-and-events.md`, document the latency budget from `plan.md` as the delivery expectation, the three
   outbound HTTP limits and that their sum must stay inside `billing.webhooks.deadline`, and `billing.webhook.lag`:
   a timer per successful delivery, measured from the delivery row's creation to the completed attempt, excluding the
   outbox poll delay.
4. Run `./gradlew build` from `billing/` and confirm it passes without running the load test. ArchUnit, the jOOQ drift
   test and the profile tests are all part of it.
5. Run `./gradlew :apps:app-boot:test --tests '*DeadlockStressIntegrationTest'` and confirm it asserts
   `pg_stat_database.deadlocks` at zero.
6. Run `ops/loadtest/run.sh` once and paste its report block into the phase notes.
7. Re-read `docs/concurrency-and-locking.md` against the phases: no flow added here changes a lock order, and the sweep
   holds no lock across a task.

## Todo

- [x] Add the bounded-parallelism rule to `docs/concurrency-and-locking.md`
- [x] Document the api and worker threading model in `docs/runtime-profiles.md`
- [x] Document the budget, pool limits and metric in `docs/webhooks-and-events.md`
- [x] `./gradlew build` passes
- [x] Deadlock stress test passes with zero deadlocks
- [x] Load test report recorded

## Success Criteria

- The three documents describe what the code does, with the property names and numbers that exist in
  `application-api.yml`, `application-worker.yml` and `application.yml`.
- `./gradlew build` is green and does not include the load test.
- The deadlock stress test is green and `pg_stat_database.deadlocks` is zero.
- The load test reports a healthy p95 below 5s at 500 tenants.

## Risk Assessment

- **Docs drifting from YAML.** Every number quoted in the docs must be taken from the YAML in this session; if a phase
  ends up with a different value, change the doc, not the YAML.
- **A green build that did not run the new tests.** Confirm from the test report that `TenantSweepTest`,
  `ApiExceptionHandlerTest` and the new `HttpSenderTest` case actually executed.
