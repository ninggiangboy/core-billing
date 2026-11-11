---
phase: 1
title: "Bounded tenant sweep"
status: pending
priority: P1
effort: "2h"
dependencies: []
---

# Phase 1: Bounded tenant sweep

## Goal

Every background job walks the tenants on virtual threads, at most a fixed number at a time counted across all jobs,
so one tenant whose receiver is stuck cannot delay the tenants behind it.

## Context

`TenantSweep.forEachActiveTenant` currently runs its consumer in a plain `for` loop on the scheduler thread. Seven
jobs share it: the outbox relay, the webhook sender, reservation expiry, ledger verification, cycle close, renewal
and Stripe event retry. A webhook round for one tenant can take up to the 15s delivery deadline, and a cycle close or
a renewal can wait on a wallet lock, so with 500 tenants the last tenant in the list waits for the sum of the tenants
before it. That is the head-of-line blocking this phase removes.

Two facts shape the change:

- `ScopedTenantContext` binds the tenant in a `ScopedValue`, which is per-thread. The existing code already calls
  `tenantContext.runAs(...)` inside the consumer, so the binding must stay inside the task, never before submitting it.
- The worker pool is 10 connections (`billing.datasource.hikari.maximum-pool-size` in `application-worker.yml`) and
  the scheduler has 4 threads. Four jobs each running N tenants in parallel would want 4×N connections. One semaphore
  on the singleton `TenantSweep` bean is shared by every job, so the whole worker never exceeds its permits.

## Files to Create / Modify

- Modify: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/jobs/TenantSweep.java`
- Modify: `billing/apps/app-boot/src/main/resources/application-worker.yml`
- Modify: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/runtime/MultiInstanceRuntimeIntegrationTest.java`
- Create: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/boot/jobs/TenantSweepTest.java`

## Tasks & Steps

1. Read `TenantSweep`, `MoneyJobs`, `OutboxJobs`, `WebhookJobs`, `PaymentJobs`, `InvoicingJobs` and confirm that every
   collaborator they call from inside the sweep is stateless. `DeliverWebhooksUseCase` already creates its own
   `newVirtualThreadPerTaskExecutor()` per round, so it is safe to run from several tenants at once. If any
   collaborator turns out to hold mutable state, stop and report it instead of adding synchronisation.
2. Add a constructor parameter `@Value("${billing.jobs.tenant-parallelism:1}") int parallelism` and a field
   `Semaphore permits = new Semaphore(Math.max(1, parallelism))`.
3. Keep the current serial loop when `parallelism <= 1`. This is the default, so `dev`, the existing integration tests
   and the multi-instance test keep today's behaviour and no test starts racing.
4. For `parallelism > 1`: list the active tenants on the calling thread in the platform scope as today, then submit one
   task per tenant to a `try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())` and wait with
   `executor.invokeAll(...)`.
5. Inside each task: `permits.acquire()`, then `tenantContext.runAs(RequestScope.tenant(tenantId), () -> work.accept(tenantId))`
   in a `try`, `LOG.error("background job failed for tenant {}", tenant.code(), failure)` on `RuntimeException` exactly
   as today, and `permits.release()` in a `finally`. The tenant code stays in the message; do not add MDC, because the
   application has no logback pattern that would print it.
6. Handle interruption: wrap `invokeAll` so an `InterruptedException` cancels the remaining tasks, restores the
   interrupt flag and returns, letting the worker shut down within its 30s grace period. Do not swallow it into a job
   failure.
7. Add to `application-worker.yml`:
   ```yaml
   billing:
     jobs:
       # At most this many tenants run at once across all jobs; keep it below the pool size so a job never starves
       # the others of connections. 8 = maximum-pool-size 10 minus 2.
       tenant-parallelism: ${BILLING_WORKER_TENANT_PARALLELISM:8}
   ```
8. Add `--billing.jobs.tenant-parallelism=2` to the argument list in `MultiInstanceRuntimeIntegrationTest.start`,
   because that test runs two workers with `maximum-pool-size=2`; with the worker default of 8 they would queue on
   their own pool.
9. Write `TenantSweepTest` as a plain unit test: a real `ScopedTenantContext`, a `TenantQueries` built over a stub
   `TenantReadRepository` that returns N active tenants, and a work consumer that asserts
   `tenantContext.requireTenant()` equals the tenant it was handed. Cover:
   - 40 tenants with `parallelism = 4`: every tenant runs exactly once and the observed maximum concurrency is 4.
   - One tenant's work throws: the failure is logged and the other 39 still run.
   - A tenant whose work blocks on a latch does not stop the work of a later tenant from starting.
   - `parallelism = 1`: all work runs on the calling thread.

## Todo

- [x] Audit the seven jobs' collaborators for mutable shared state
- [x] Add the semaphore and the virtual-thread executor to `TenantSweep`
- [x] Keep the serial path for `parallelism <= 1`
- [x] Add `billing.jobs.tenant-parallelism` to `application-worker.yml`
- [x] Pin the multi-instance test to 2 permits
- [x] Write `TenantSweepTest`
- [x] Run `./gradlew :apps:app-boot:test --tests '*TenantSweepTest' --tests '*MultiInstanceRuntimeIntegrationTest'`

## Success Criteria

- A slow tenant's consumer no longer blocks the consumers of the tenants after it, and the number of tenants running
  concurrently never exceeds the permits.
- Tenant scope is always bound inside the task; a test proves `requireTenant()` answers the right tenant from a
  task thread.
- The default remains serial, so no existing test changes behaviour except where it sets the property.
- `./gradlew :apps:app-boot:test` is green.

## Risk Assessment

- **A permit held across a lease or a long HTTP call.** The webhook sender holds a permit for the whole round,
  including its 15s deadline, so a few stuck receivers occupy permits. That is intended: it bounds connections. If the
  load test shows it starves healthy tenants, lower `billing.webhooks.deadline` rather than raise the permits.
- **Connection starvation.** Permits above the pool size turn into Hikari waiting. The worker default is pool minus
  two; changing the pool means changing the property in the same commit.
- **Log interleaving.** Logs from different tenants will interleave. Every job message already carries the tenant, and
  the plan deliberately does not add MDC because nothing would print it.
- **Rule 1 of `docs/concurrency-and-locking.md`.** A lease must never be held while the job runs. `MoneyJobs` acquires
  the ledger lease before the sweep and renews it per tenant; parallel tenants each renew, so the lease must be renewed
  with the same short single-row statement per tenant (it is). Do not move lease renewal outside the task.
