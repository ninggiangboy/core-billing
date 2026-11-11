---
title: "Replace JdbcClient and Spring Data JDBC with jOOQ"
description: "One type-safe persistence stack: generated jOOQ tables, DSLContext in every adapter, no hand-written Rows."
status: completed
priority: P1
branch: dev
tags: [refactor, backend, persistence, jooq]
created: 2026-11-11
---

# Replace JdbcClient and Spring Data JDBC with jOOQ

## Outcome, constraints, non-goals, acceptance

- **Outcome.** A column renamed or retyped in a Flyway migration fails `./gradlew build` instead of failing at runtime. One persistence stack (jOOQ) in all 9 modules and in `app-boot`.
- **Constraints.**
  - `DSLContext` shares the Spring transaction and connection, so `set_config('app.tenant_id', ...)` in `SpringUnitOfWork` keeps row-level security correct. `SpringUnitOfWork` itself is unchanged.
  - Generated code lives in one Gradle module, `jooq-schema`, package `dev.ngb.core_billing_jooq` (outside the Modulith root on purpose). It is committed to git and regenerated from the Flyway migrations by a Testcontainers test: `-Djooq.update=true` rewrites it, a plain run fails on drift. A normal compile needs no Docker.
  - jOOQ records and the DSL map straight to domain aggregates and views. No intermediate Row. Adapters are named `Jooq*Repository`.
  - Guarded and partial updates, `for update skip locked`, conflict-ignoring inserts, upserts and the append-only ledger insert stay explicit statements. No `UpdatableRecord.store()` for them.
  - Each context's adapter uses only its own tables (ArchUnit). One module per commit, tests green at each step. Conventional commits, no plan or phase identifiers in code, test names or messages.
- **Non-goals.** MyBatis, JPA, DB schema or migration changes, HTTP and event contracts, rewriting RLS or `SpringUnitOfWork`, a separate read store, read-performance tuning.
- **Acceptance.** `grep -rE 'JdbcClient|JdbcAggregateOperations|@Table|starter-data-jdbc' billing` is empty; no MyBatis; `./gradlew test` green (654 tests at baseline); `contracts/openapi/billing.yaml` unchanged; ArchUnit and its meta-test updated; docs rewritten; no-regression read check on 2-3 list endpoints.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | Foundation: `jooq-schema` module, generation and drift test, starter, ArchUnit rules | done |
| 2 | Pilot: customer | done |
| 3 | usage, webhook | done |
| 4 | platform | done |
| 5 | pricing, payment | done |
| 6 | invoicing, money | done |
| 7 | app-boot infrastructure and test support, remove Spring Data JDBC | done |
| 8 | Read check, docs, journal | done |

Each phase: migrate, `./gradlew test` for the touched module and `:app-boot:test`, commit.
