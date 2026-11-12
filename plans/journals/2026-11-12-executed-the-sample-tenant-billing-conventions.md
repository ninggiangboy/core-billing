---
title: "Executed the sample-tenant move to Billing's conventions"
date: 2026-11-12
summary: "Ran all 9 phases: feature-role layout, jOOQ, command/query split, ArchUnit and Modulith rules, HTTP and repository integration tests. 82 to 190 tests, contracts/billing/core untouched."
---

# Executed the sample-tenant move to Billing's conventions

## What happened
- Executed `plans/261112-0340-sample-tenant-billing-conventions` phase by phase, committing per task on `dev`.
- Phase 1 moved `AccountListener` into `identity.api.account`, removed the one cross-module import, and added the sample's ArchUnit rules with a fixtures meta-test.
- Phase 2 moved `usage`, `catalog`, `execution` and `webhook` into the feature-then-role layout and renamed `webhook-receiver` to `webhook`.
- Phase 3 moved `customer`, `identity`, `resource` and `billing-client`, split `CustomerBillingViews` into two use cases and a query class, added Spring Modulith named interfaces and verification, and enabled the nine layout rules.
- Phase 4 added `libs:jooq-schema` with committed generated code, the `JooqSchemaTest` drift check, the jOOQ starter in every persistence-owning module, and the persistence placement and table-ownership rules.
- Phase 5 pinned every controller with HTTP integration tests on the then-current Spring Data persistence, with a `FakeBilling` and one shared PostgreSQL container.
- Phases 6 to 8 replaced Spring Data JDBC and `JdbcTemplate` with jOOQ write repositories and read projections for usage, catalog, customer, identity, resource and webhook; moved `execution_queue` to `resource` behind `resource.api.execution.ExecutionQueue`; wrapped the order and queue writes in `UnitOfWork`; and enabled the CQRS and repository-pair rules.
- Phase 9 updated `sample-tenant/README.md`, `docs/sample-tenant-system.md` and `docs/architecture.md`, ran the full gate, and finalized the plan files.

## Verification
- `cd sample-tenant && ./gradlew checkNoBillingDependency build`: green, 190 tests (baseline 82).
- `cd billing && ./gradlew :apps:app-boot:test --tests '*architecture*'`: green.
- `git diff 84adc21..HEAD -- contracts billing core`: empty.
- Acceptance grep for `JdbcTemplate|JdbcClient|JdbcAggregateOperations|@Table|starter-data-jdbc|@Transactional`: empty.

## Deviations, all consistent with the plan's stated conventions
- `inner-layer-purity` allows `javax..` as JDK: the webhook domain verifies HMAC with `javax.crypto`.
- `OrderExecutionService` became `ExecutionOrdersService`, so `api-implementations-are-services` holds.
- `role-package-not-layer-root` allows an empty subject; the cleaned tree has no layer-root class and the fixture meta-test still proves the rule bites.
- `CustomerQueries` and `CatalogQueries` return views; `CustomerDirectoryService` and `CatalogLookupService` serve the api through the read repositories.
- HTTP tests use the `test` profile, which excludes `BillingConfiguration`; the fake provides the named beans, and the Billing qualifiers stay because one HTTP client implements every interface.

## Next steps
- Still open and outside this plan: add `.github/workflows/core.yml` so the shared core's tests run in CI (decision D5).
- The two queue claimers are covered by a concurrency test; a future move to more than one worker process does not need a new design.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
