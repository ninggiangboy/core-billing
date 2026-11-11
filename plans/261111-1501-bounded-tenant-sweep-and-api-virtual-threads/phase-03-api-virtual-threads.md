---
phase: 3
title: "Virtual threads on the api profile"
status: pending
priority: P1
effort: "1.5h"
dependencies: []
---

# Phase 3: Virtual threads on the api profile

## Goal

While Stripe or Keycloak is slow, the api keeps serving other requests on virtual threads, and a request that cannot
get a database connection is refused with a 503 and `Retry-After` instead of queueing for 30s.

## Context

A top-up holds a request thread while Stripe answers, and `billing.payments.stripe.read-timeout` is `PT30S`. Tomcat
serves requests on 200 platform threads by default, so 200 concurrent top-ups during a slow Stripe window occupy every
request thread; unrelated endpoints queue behind them. Virtual threads make that wait cheap.

Virtual threads alone only move the queue: the Hikari pool still has 20 connections in the `api` profile and its
default `connection-timeout` is 30s, so the failure changes from "no threads" to "thousands of requests waiting 30s for
a connection". The short connection timeout, and the 503 that turns it into, are what make the overload visible and
fast.

`jobs` are unaffected: the `worker` profile must not enable virtual threads. When Spring Boot enables them it replaces
the scheduler with `SimpleAsyncTaskScheduler` and stops honouring `spring.task.scheduling.pool.size`, which would remove
the four-thread bound the worker relies on.

## Files to Create / Modify

- Modify: `billing/apps/app-boot/src/main/resources/application-api.yml`
- Modify: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/web/ApiExceptionHandler.java`
- Modify: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/runtime/RuntimeConfigurationTest.java`
- Create: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/boot/web/ApiExceptionHandlerTest.java`

## Tasks & Steps

1. In `application-api.yml`, under `spring`, add:
   ```yaml
   threads:
     virtual:
       enabled: true
   ```
   and under `spring.datasource.hikari`, add `connection-timeout: 3s`. Extend the file's comment so it says why the
   two belong together: a wait for Stripe is cheap on a virtual thread, and a wait for a connection is short and ends
   in a 503 rather than a 30s queue.
2. Leave `application.yml`, `application-worker.yml` and `application-migrate.yml` unchanged. The default (all-in-one,
   used by `dev` and tests) stays on platform threads, which keeps the worker's scheduler bound intact.
3. In `ApiExceptionHandler` add one handler, next to the existing `PessimisticLockingFailureException` one:
   ```java
   @ExceptionHandler(CannotGetJdbcConnectionException.class)
   ResponseEntity<ProblemDetail> noConnection(CannotGetJdbcConnectionException e) { ... }
   ```
   It logs a warning without the pool's internals in the response, answers `503` with `Retry-After: 1` and a message
   telling the caller to repeat the request with the same `Idempotency-Key`, matching the lock-timeout handler.
   `CannotGetJdbcConnectionException` is what `DataSourceUtils` throws when Hikari's `getConnection()` times out, and
   every money operation already carries an idempotency key, so a repeat is safe.
4. Extend `RuntimeConfigurationTest`:
   - `application-api.yml` has `spring.threads.virtual.enabled = true` and
     `spring.datasource.hikari.connection-timeout = 3s`.
   - `application-worker.yml` does not set `spring.threads.virtual.enabled`, and still sets its scheduler pool size.
   - `application-api.yml` still sets its own pool size and lock budget (guards against a careless rewrite).
5. Write `ApiExceptionHandlerTest` in the same package, asserting the returned entity's status is 503, it carries
   `Retry-After: 1`, and the detail does not contain the Hikari message. Keep it a plain unit test; no Spring context is
   needed for a package-private handler.
6. Do not change Tomcat's thread settings, `server.tomcat.*`, or the security filter chain in this phase.

## Todo

- [x] Enable virtual threads and a 3s Hikari connection timeout in `application-api.yml`
- [x] Map connection-pool timeouts to 503 with `Retry-After` in `ApiExceptionHandler`
- [x] Extend `RuntimeConfigurationTest`
- [x] Add `ApiExceptionHandlerTest`
- [x] Run `./gradlew :apps:app-boot:test --tests '*RuntimeConfigurationTest' --tests '*ApiExceptionHandlerTest' --tests '*ApiProfileIntegrationTest'`

## Success Criteria

- The shipped `api` profile enables virtual threads and a 3s connection timeout together; the `worker` profile does
  neither.
- A connection-pool timeout answers 503 with `Retry-After` and leaks no pool details.
- `ApiProfileIntegrationTest` still starts the api profile with no job beans and still answers its probes.
- `./gradlew :apps:app-boot:test` is green.

## Risk Assessment

- **Pinning with `synchronized` or a thread-local pool.** Virtual threads pin the carrier thread when they block inside
  `synchronized`. The request path uses Spring, jOOQ and the JDBC driver; this is a known JDK limitation outside our
  code. Measure in phase 5 rather than rewriting call sites; revisit only if the load test shows carrier starvation.
- **Connection timeout too aggressive.** A 3s wait is short for a wallet lock that legitimately serialises two charges,
  but the lock is waited on inside the transaction, not while getting a connection. If the load test shows legitimate
  requests failing, raise the timeout rather than removing it.
- **A 503 the client does not retry.** The message names the idempotency key, as the lock-timeout handler already does;
  no client contract changes.
