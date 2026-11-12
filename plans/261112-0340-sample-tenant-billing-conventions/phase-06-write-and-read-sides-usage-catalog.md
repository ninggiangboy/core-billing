---
phase: 6
title: "Write and read sides: usage, catalog"
status: completed
priority: P1
effort: "1d"
dependencies: [5]
---

# Phase 6: Write and read sides: usage, catalog

## Goal

For the two simplest persistence-owning modules (the execution queue moves with `resource` in phase 8), split command and query sides and replace Spring Data JDBC, Rows and `JdbcTemplate` with jOOQ. `usage` is the pilot.

## Pattern applied in every module of phases 6 to 8

1. Write port keeps only what a use case needs to decide (`save`, `findById`, guarded lookups). Anything that lists or filters for a caller moves to a `*ReadRepository` interface in `query/`.
2. `*View` records (immutable, primitives and enums, never a domain type other than an enum) in `query/`; `*Queries` return views and call the read repository.
3. `Jooq*Repository` (write) and `Jooq*ReadRepository` (read) in the same `adapter.<feature>.persistence` package, using `DSLContext` and the generated tables, mapping straight to the aggregate or view. No Row. Guarded updates, upserts and `for update skip locked` are explicit statements.
4. Controllers map from views. The class implementing a module's `api` interface is `<Interface>Service` in `service` and reads through the read repository.
5. Delete the `*JdbcRepository` (Spring Data), `*Row` and `Jdbc*Repository` classes and the old `JdbcTemplate` wiring in `boot/config`.
6. Add `Jooq*RepositoryIntegrationTest` for each repository against the Testcontainers database: round trip, each guarded or conflict statement, and RLS-free reads; add or extend domain tests for the aggregate's state transitions.
7. One module per commit; after each: `./gradlew checkNoBillingDependency build` green and the phase 5 HTTP tests unchanged and passing.

## Modules

| Module | Write side | Read side | Special |
|---|---|---|---|
| usage (pilot) | `UsageRepository`: `save` (`on conflict (order_id, metric) do nothing`), `findByOrderIdAndMetric` | `UsageReadRepository.forBillingCustomer`, `UsageRecordView`, `UsageQueries` | proves the `INSTANT` forced type and the pipeline |
| catalog | `CatalogPlanRepository`: `save` (upsert), `findById`, `findByCode` (used by `AttachBillingPlanUseCase`) | `CatalogPlanReadRepository`: `listActive`, `all`, `byId`, `byCode`; `PlanView`; `CatalogLookupService implements CatalogLookup` | `CatalogQueries.get` returns the api `PlanSnapshot` today; it moves to the service |

## Files

- Modify/create/delete: `sample-tenant/modules/{usage,catalog}/src/{application,adapter,test}/**` (see mapping in phase 2 for packages)
- Modify: `sample-tenant/apps/app-boot/src/main/java/dev/ngb/sampletenant/boot/config/{Usage,Catalog}Configuration.java`
- Modify: `UsageController`, `CatalogController` (map from views)

## Verification

- `cd sample-tenant && ./gradlew checkNoBillingDependency build` green, with Docker.
- Phase 5 HTTP tests for usage and catalog pass without edits to their assertions.
- `grep -rln 'JdbcTemplate\|@Table' sample-tenant/modules/{usage,catalog}` returns nothing.

## Risks

- Views that omit a field a controller returns: caught by the phase 5 full-body assertions.

## Todo

- [x] usage (pilot) migrated and committed
- [x] catalog migrated and committed
