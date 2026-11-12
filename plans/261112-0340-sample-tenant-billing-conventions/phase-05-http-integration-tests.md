---
phase: 5
title: "HTTP integration tests"
status: completed
priority: P1
effort: "1.5d"
dependencies: [4]
---

# Phase 5: HTTP integration tests

## Goal

Pin the observable HTTP behaviour of every controller on the **current** Spring Data JDBC persistence, before any repository is rewritten. These tests are the safety net for phases 6 to 8: a view that drops a field, or a jOOQ statement that behaves differently, fails here.

## Context

- Today only application-layer tests with in-memory fakes exist, plus `HttpBillingClientTest` and `ArchitectureTest`. The review traced a real bug (commit `e55958a`, inserts with application-assigned ids) to the absence of any test that runs SQL.
- Billing's pattern: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/support/{PostgresContainerConfig,HttpWorld}.java` and `*HttpIntegrationTest.java`.
- Controllers: `AuthController`, `UserController` (identity); `CustomerController`, `WalletSettingController`, `BillingViewController` (customer); `CatalogController`; `OrderController`; `UsageController`; `WebhookController`. Endpoints are listed in `sample-tenant/README.md` (API surface).
- Billing is reached through the `Billing*` interfaces of `billing-client`; the tests replace them with one in-memory `FakeBilling`, never call the network, and never depend on `billing/`.

## Files

- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/support/{HttpWorld,FakeBilling,FakeBillingConfig}.java`
- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/{identity,customer,catalog,resource,usage,webhook}/*HttpIntegrationTest.java`
- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/boot/ApplicationContextTest.java`
- Create, only if needed: `sample-tenant/apps/app-boot/src/test/resources/application-test.yml` (no test resources exist today) to disable the execution worker (`SAMPLE_TENANT_EXECUTION_ENABLED=false`); a `@TestPropertySource` is an equal alternative

## Tasks and steps

1. `ApplicationContextTest`: the full context starts against the Testcontainers PostgreSQL migrated by Flyway (proves every bean is wired after the phase 2 and 3 moves).
2. `HttpWorld`: starts the app on a random port (or MockMvc with the real filter chain), registers a user through `POST /api/auth/register`, logs in, and returns an authenticated client for a customer and one for an administrator; the credential is passed the way `SessionAuthenticationFilter` reads it.
3. `FakeBilling` implements the `Billing*` api interfaces with deterministic data and records calls, so order tests can assert what was reserved, captured and released.
4. One test class per controller covering every endpoint in the README table: success, the main failure (401, 403, 404, 409, 402, 502 where the README or code defines it), and the **full response body** (field names and values), because views in later phases must reproduce it. Webhook tests send a correctly signed body, a forged one, a stale one and a duplicate.
5. Cover the SQL-heavy paths with real data: duplicate usage (`on conflict do nothing`), duplicate webhook delivery, session expiry, wallet setting upsert, order listing per customer and for an administrator.
6. Fix a test only when it is wrong about the intended behaviour; if a test exposes a real bug, stop and report it instead of changing behaviour inside this plan.

## Verification

- `cd sample-tenant && ./gradlew :apps:app-boot:test` passes with Docker running; every controller class has at least one test class.
- The tests pass against the current Spring Data JDBC persistence, and are not edited to fit it: any assertion that fails is checked against the README and the code before deciding which one is wrong.
- `./gradlew checkNoBillingDependency build` green.

## Risks

- Test runtime grows; the container is started once per JVM (static container, as Billing does).
- Time-dependent fields (`createdAt`) cannot be asserted exactly; assert presence and format, and assert exact values for everything else.

## Todo

- [x] Context test
- [x] `HttpWorld` and `FakeBilling`
- [x] One HTTP test class per controller
- [x] SQL-heavy paths covered
