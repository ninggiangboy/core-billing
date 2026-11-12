---
phase: 4
title: "jOOQ foundation and persistence rules"
status: completed
priority: P1
effort: "1d"
dependencies: [3]
---

# Phase 4: jOOQ foundation and persistence rules

## Goal

Add the sample's generated-schema module, the drift test, the Testcontainers support and the jOOQ starter, without migrating any repository yet. After this phase a Flyway migration change that is not regenerated fails the build.

## Context

- Mirror `billing/libs/jooq-schema/build.gradle.kts`, `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/boot/infrastructure/persistence/JooqSchemaTest.java`, and the support classes `PostgresContainerConfig` and `RepositoryPaths` under `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/support/`.
- The sample's schema: 11 tables in `sample-tenant/apps/app-boot/src/main/resources/db/migration/V1..V9`, `timestamptz` and `bigint` columns, image `postgres:18` (see `docker-compose.yml`).
- Spring Boot 4.1 manages jOOQ and Testcontainers versions; `sample-tenant/gradle/libs.versions.toml` already lists what Billing's does.

## Files

- Create: `sample-tenant/libs/jooq-schema/build.gradle.kts` and generated `sample-tenant/libs/jooq-schema/src/main/java/dev/ngb/sampletenant_jooq/**` (committed)
- Modify: `sample-tenant/settings.gradle.kts` (include `libs:jooq-schema`)
- Modify: each persistence-owning module's `build.gradle.kts` (`adapterImplementation` of `spring-boot-starter-jooq` and `project(":libs:jooq-schema")`): usage, catalog, customer, identity, resource, webhook (execution owns no table)
- Modify: `sample-tenant/apps/app-boot/build.gradle.kts` (starter-jooq, Testcontainers PostgreSQL, `spring-boot-testcontainers`; keep `starter-data-jdbc` until phase 8)
- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/support/{PostgresContainerConfig,RepositoryPaths}.java`
- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/boot/persistence/JooqSchemaTest.java`
- Modify: `ArchitectureRules.java`, `ArchitectureTest.java`, `ArchitectureMetaTest.java`, fixtures

## Tasks and steps

1. Create `libs:jooq-schema` as in Billing (`compileOnly("org.jooq:jooq")`, lint arguments cleared for generated code).
2. `JooqSchemaTest`: migrate a throwaway PostgreSQL with Flyway, generate with `GenerationTool`, compare with the committed package; `-Djooq.update=true` rewrites it. Package `dev.ngb.sampletenant_jooq`, forced type `INSTANT` for `timestamptz`, records on, POJOs, DAOs and routines off, `flyway_schema_history` excluded. Run once with the update flag and commit the generated code.
3. Add the jOOQ starter to the modules and `app-boot` and a `DSLContext` smoke: the application context test (phase 5) will prove the bean exists; here only the build must pass.
4. Add the persistence rules and fixtures: `persistence-types-in-adapter` (only `..adapter..persistence..` and `boot` may name `dev.ngb.sampletenant_jooq..`), `generated-records-in-adapter-persistence`, and `module-uses-only-own-tables` with a table-owner map (identity: `users`, `user_sessions`; customer: `customers`, `customer_wallet_settings`; catalog: `catalog_plans`; usage: `usage_records`; resource: `orders`, `execution_queue`; webhook: `webhook_events`, `customer_debts`, `customer_periods`; execution owns no table). Decision D1: `resource` owns `execution_queue`; `execution` reaches it only through a `resource.api` port (phase 8). The rules have no subjects until phase 6, so they use `allowEmptyShould(true)` exactly as Billing does for empty rule subjects, and the fixtures keep the meta-test honest.

## Verification

- `cd sample-tenant && ./gradlew checkNoBillingDependency build` green with no Docker (generated code is committed).
- With Docker: `./gradlew :apps:app-boot:test --tests '*JooqSchemaTest'` passes; changing a column in a scratch migration makes it fail, and `-Djooq.update=true` makes it pass again (revert the scratch migration).
- The three new rules fail on their fixtures (meta-test).

## Risks

- A generated table name collides with a domain term; jOOQ names are only referenced inside adapters, which the new rule enforces.
- The Docker requirement for `JooqSchemaTest` also applies in CI; the sample CI job runs on `ubuntu-latest`, which has Docker (same as Billing's job).

## Todo

- [x] `libs:jooq-schema` and committed generated code
- [x] `JooqSchemaTest` with drift check
- [x] Starter and Testcontainers dependencies
- [x] Persistence rules and fixtures
