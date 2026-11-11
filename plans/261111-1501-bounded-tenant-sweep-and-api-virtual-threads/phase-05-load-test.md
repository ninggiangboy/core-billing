---
phase: 5
title: "Load test outside the build"
status: pending
priority: P1
effort: "2h"
dependencies: [1]
---

# Phase 5: Load test outside the build

## Goal

A repeatable run that seeds 500 tenants, keeps 3 receivers stuck for 15s, and reports the event-to-receipt lag
distribution for the other 497, failing when their p95 reaches 5s or when `hikaricp.connections.pending` ever goes
above zero. It never runs as part of `./gradlew build`.

## Context

The 5s target cannot be checked by a normal integration test: the numbers only mean something with hundreds of
tenants, a few stuck receivers and a real database. The existing integration tests already create tenants, publish
through the outbox and drive the job beans directly (`WebhookDeliveryIntegrationTest`, `SingletonJobIntegrationTest`),
and `TestReceiver` is a working receiver on loopback.

The build must stay fast, so the load test is a JUnit test tagged `loadtest`, excluded from `test` and run by a
separate `loadTest` task that `build` does not depend on. The harness lives next to the other app tests because it
needs the real wiring (jobs, `TenantSweep`, repositories); `ops/loadtest/` holds the entry point only.

## Files to Create / Modify

- Create: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/loadtest/WebhookLagLoadTest.java`
- Modify: `billing/apps/app-boot/build.gradle.kts`
- Create: `ops/loadtest/run.sh`

## Tasks & Steps

1. In `billing/apps/app-boot/build.gradle.kts`:
   - In the existing `tasks.test` block add `useJUnitPlatform { excludeTags("loadtest") }`.
   - Register a `Test` task `loadTest` in the `verification` group with `testClassesDirs =
     sourceSets.test.get().output.classesDirs`, `classpath = sourceSets.test.get().runtimeClasspath`,
     `useJUnitPlatform { includeTags("loadtest") }` and `outputs.upToDateWhen { false }`. Do not make `build` depend on
     it.
2. Write `WebhookLagLoadTest` with `@Tag("loadtest")`, `@SpringBootTest(classes = Application.class)`,
   `@ActiveProfiles("worker")` and a test timeout of five minutes. Provide its own `@DynamicPropertySource` (do not
   import `PostgresContainerConfig`'s registrar, which pins the pool to 4): database URL, role and password from
   `PostgresContainerConfig.postgres()`, Flyway enabled as the owner, `billing.secrets.master-key` = the test key,
   `spring.datasource.hikari.maximum-pool-size=20`, `minimum-idle=5`,
   `billing.jobs.tenant-parallelism=8`, `billing.webhooks.allow-insecure-targets=true`,
   `billing.webhooks.max-attempts=3`, and every scheduling delay pushed out of the way so the test drives the ticks
   itself: `billing.outbox.poll-delay=PT1H`, `billing.webhooks.poll-delay=PT1H`,
   `billing.jobs.expire-reservations-delay=PT1H`, `billing.jobs.verify-ledger-initial-delay=PT1H`,
   `billing.invoicing.cycle-delay=PT1H`, `billing.invoicing.renewal-delay=PT1H`,
   `billing.rate-limit.cleanup-delay=PT1H`, `billing.payments.jobs-enabled=false`.
3. Seed:
   - 500 tenant rows in one batch on the owner connection
     (`insert into tenants (id, code, name, status, created_at) values (?, ?, ?, 'ACTIVE', now())`), the pattern
     `SingletonJobIntegrationTest` already uses.
   - One endpoint per tenant through `CreateEndpointUseCase` in that tenant's scope, with event types `List.of("*")`
     and an `Audit("loadtest", "loadtest")`. 497 point at a fast `TestReceiver`; 3 point at a slow receiver whose
     handler sleeps 15s before answering. Record the tenant ids of the slow three.
   - One `TestEvent.forTenant(tenant)` per tenant, published inside that tenant's transaction with `EventPublisher` and
     `UnitOfWork`, so the outbox rows are exactly what production writes.
4. Measure: before publishing, start a daemon thread that samples every 50ms
   `meterRegistry.find("hikaricp.connections.pending").gauges()` and keeps the maximum. Stop it when the measurement
   ends.
5. Drive the real jobs in a loop until every healthy delivery has arrived or 120s pass: call `OutboxJobs.relay()` once,
   then repeat `WebhookJobs.deliver()` with a 100ms pause. Both are the production beans with the production
   `TenantSweep`; a round therefore waits for the stuck tenants' sends to time out, which is expected and does not
   delay the healthy receipts.
6. Compute lag per delivered event as the receiver's receipt time minus the `occurredAt` in the received body (parse it
   with the Jackson `JsonMapper`), for the 497 healthy tenants only. Print one report block: tenants seeded, healthy
   deliveries received, p50/p95/p99/max in milliseconds, slow receivers still in flight at the end, maximum
   `hikaricp.connections.pending`, and total elapsed.
7. Assert: all 497 healthy deliveries arrived, p95 is below 5000ms, the maximum pending gauge is 0 or 1, and the three
   slow deliveries are either still in flight or recorded as failures. Do not assert anything about the slow tenants'
   latency; the point is that they did not delay the others.
8. Add `ops/loadtest/run.sh`:
   ```sh
   #!/usr/bin/env sh
   set -eu
   cd "$(dirname "$0")/../../billing"
   exec ./gradlew :apps:app-boot:loadTest
   ```
   Make it executable. To see the old behaviour for comparison, run with `-Dbilling.jobs.tenant-parallelism=1`
   passed through `ORG_GRADLE_PROJECT_` or by editing the property source; record whichever numbers you use in the
   phase notes, not in the repo.

## Todo

- [x] Exclude the `loadtest` tag from `test` and register the `loadTest` task
- [x] Write the seeded, measured load test
- [x] Add `ops/loadtest/run.sh`
- [x] Run the load test and record p50/p95/p99 and max pending
- [x] Confirm `./gradlew build` does not run it

## Success Criteria

- One command runs the load test, and its output states whether the healthy p95 met 5s.
- `./gradlew build` and `./gradlew test` never run it (verify from the Gradle task output, not by assumption).
- The test fails when `billing.jobs.tenant-parallelism` is 1, which is the proof that phase 1 is what fixes the
  head-of-line delay.

## Risk Assessment

- **Flaky on a busy laptop.** 500 tenants and 3 stuck receivers is a real load. Percentiles near the boundary can
  flip; the asserted threshold is the product target, so a failure is a finding, not a flaky test. Keep the run out of
  CI until it is stable enough to gate on.
- **Testcontainers startup dominates.** The suite already shares one container per JVM; the load test reuses
  `PostgresContainerConfig.postgres()` so it does not start a second one.
- **The scheduled ticks fire once at startup.** Every delay is set to `PT1H` for that reason; the single startup tick
  sees only the platform tenant and does no work.
