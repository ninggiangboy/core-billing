---
title: "One persistence stack: jOOQ replaces JdbcClient and Spring Data JDBC"
date: 2026-11-11
summary: "Nine modules and the boot wiring moved to generated jOOQ tables; Spring Data JDBC is gone, schema drift is a build failure, and explicit statements stayed explicit."
---

# One persistence stack: jOOQ replaces JdbcClient and Spring Data JDBC

## What happened

Every persistence adapter in `billing/` now injects `DSLContext` and maps generated records straight to its
aggregate or view. The package-private `@Table` Row types and their `from`/`toDomain` helpers are gone, and so is
the `JdbcClient` string SQL in adapters. Spring Data JDBC no longer appears on any classpath, including the boot
application.

`jooq-schema` holds the generated tables and records, committed to git and regenerated from the Flyway migrations
by `JooqSchemaTest`; a plain test run fails when the committed code is out of date, and `-Djooq.update=true`
rewrites it. The module sits outside `dev.ngb.core_billing` on purpose, so Modulith does not treat generated code
as a module.

The architecture rules changed with the stack: generated jOOQ types and records must live in
`adapter.persistence` (or the boot wiring), the read-repository naming rule follows `Jooq*ReadRepository`, and a new
rule keeps each context's adapter on the tables it owns. The outbox, inbox, idempotency keys and audit log are
owned by boot; the meta-test's fixtures were updated to keep every rule failing where it should.

Order of work: foundation and the customer pilot were already committed; this change finished usage, webhook,
platform, pricing, payment, invoicing, money, then the boot infrastructure and the rules/docs. Each module is one
commit, tests green at each step. All 655 tests pass, `grep -rE 'JdbcClient|JdbcAggregateOperations|@Table|
starter-data-jdbc' billing` is empty, and the OpenAPI contract did not change.

## Problems worth remembering

- Plain SQL bindings in jOOQ do not pick up jOOQ's `Instant` converter: `dsl.fetch(sql, instant)` sent the value
  as `varchar`, and PostgreSQL rejected `timestamptz <= character varying`. The one raw statement (the webhook
  claim CTE) binds `java.sql.Timestamp` instead. DSL-built queries bind `Instant` correctly.
- A partial unique index is not a regular conflict target. `on conflict (endpoint_id, event_id) where resend_of is
  null` cannot be expressed by naming the columns alone; `onDuplicateKeyIgnore()` emits `on conflict do nothing`,
  which lets PostgreSQL infer the partial index. The incident upsert does need the target, and jOOQ's
  `onConflict(...).where(...)` produces the partial `do update` correctly.
- `JSONB` columns are typed `org.jooq.JSONB`: write `JSONB.valueOf(json)`, read `field.data()`. The previous
  `rule::text` and `breakdown::text` casts disappeared because they existed only to satisfy the string row mapping.
- `sum()` over a `bigint` returns `BigDecimal` in jOOQ, so the ledger balance and verification sums cast to
  `Long` before coalescing with `0L`.
- An insert that takes its tenant from `app_tenant_id()` (usage links, metric registration) is a
  `select ... from unnest(array[...])` or a `DSL.field("app_tenant_id()", UUID.class)` value, not a bound literal.
- `.set(field, null)` is ambiguous without a cast; the unbilled-line insert needed `(UUID) null`.

## Decisions

- One generated schema module, committed, with a drift test. A normal compile needs no Docker.
- Explicit statements stay explicit: claims, `for update`/`skip locked` reads, guarded or partial updates,
  conflict-ignoring inserts, upserts and the append-only ledger insert. `UpdatableRecord.store()` is not used.
- Aggregates with child collections (customer mappings, role permissions, user assignments, invoice lines and
  payments) rewrite their children explicitly, matching what Spring Data JDBC did implicitly but visibly.
- Per-context table ownership is an ArchUnit rule keyed by table constant, with the four shared infrastructure
  tables assigned to boot.

## Next steps

- The generated schema must be regenerated when a migration adds or retypes a column; the drift test names the
  command when it fails.
- If a query shape outgrows the generated tables, prefer adding a small explicit statement over a hand-written
  Row; the rules still allow it in `adapter.persistence`.
- The read check was the existing HTTP integration tests over the customer, invoice, payment and webhook list
  endpoints; if list latency becomes a concern, measure before changing the read model.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
