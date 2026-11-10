---
phase: 5
title: "Write and read models: usage, webhook, pricing"
status: completed
priority: P1
effort: "3d"
dependencies: [4]
---

# Phase 5: Write and read models: usage, webhook, pricing

## Goal

Split each of the three smaller modules into a write side (use cases, aggregates, write ports, `@Table` Row persistence) and a read side (read models, read ports, direct SQL projections), and fix the conventions every later module follows.

## Context

Today `JdbcUsageEventRepository`, `JdbcWebhookEndpointRepository`, `JdbcWebhookDeliveryRepository`, `JdbcPlanRepository`, `JdbcRatingRepository` and `JdbcMetricRegistry` map result sets straight into domain types, and the `*Queries` classes return domain objects through the same repositories the use cases write with.

**Write side.** Each aggregate has a package-private `XxxRow` `@Table` record next to its repository, with `static XxxRow from(Domain)` and `Domain toDomain()`. Plain insert and update go through `JdbcAggregateOperations`. Special SQL (locking reads, guarded or partial updates, inserts into append-only tables) stays an explicit `JdbcClient` statement and reads into a Row, then maps to domain. A guarded or partial `update` is never replaced by a whole-row update. Never use `save()` or `@MappedCollection` on append-only data. `customer` (`CustomerRow`, `JdbcCustomerRepository`) is the reference for the Row shape.

**Read side.** For each query operation (`*Queries`, `*LookupService`, every read-only method a GET endpoint or another module's `api` lookup calls):

- an immutable `XxxView` record in `application/<feature>/query/`, plain Java, shaped for what the caller needs (joins and flattened fields are fine), not for the aggregate;
- a read port `XxxReadRepository` in the same package;
- `JdbcXxxReadRepository` in `adapter/persistence/<feature>/`, selecting directly into the view with `JdbcClient`; it builds neither a domain object nor a Row;
- the query class depends only on the read port and `UnitOfWork` (reads still run inside the tenant-scoped transaction so row-level security applies), never on a write repository;
- the controller's GET handlers call query classes and map views to responses; POST and PUT handlers call use cases.

Write repositories lose their list, page and search methods; they keep add, update, find by id or key, and lock. A use case that needs to read in order to decide keeps using the write repository. Reads stay immediately consistent: the same database, no projections.

## Files to Create / Modify

- Create: one `*Row` per aggregate and per persisted child table, one `*View` per query result, one `*ReadRepository` port and one `Jdbc*ReadRepository` per feature that has queries.
- Modify: the write repositories and `*Queries` / lookup classes of these modules; the controllers' GET handlers; `billing/app-boot/.../config/` wiring for the new read repositories.

## Tasks & Steps

- [x] 1. Pilot `usage`. Add the Row for the write side and a view and read repository for its read side. Check that `.query(XxxRow.class)` and `.query(XxxView.class)` map snake_case columns, `timestamptz` to `Instant`, enums and `UUID`. Record the verified conventions (null handling, enum as `String`, `Money` as `long` plus currency code, naming of views) in the phase notes. If `query(Class)` cannot map a type, use a small explicit `RowMapper` that returns the Row or the view, never a domain object.
- [x] 2. Read the SQL of every repository in this phase. List each guarded or partial `update` and keep it as an explicit statement.
- [x] 3. Convert `webhook`, then `pricing`, one commit per repository for the write side (`refactor(<module>): map <aggregate> through a row record`) and one commit per feature for the read side (`refactor(<module>): read <feature> through read models`).
- [x] 4. Move the read methods out of the write repositories and delete what is no longer used.
- [x] 5. After each commit, run that module's tests.
- [x] 6. Add `usage`, `webhook` and `pricing` to the persistence set and to the read and write set of the architecture rules.

## Phase notes

### Verified `JdbcClient.query(Class)` conventions (usage pilot)

- `query(Class)` uses `SimplePropertyRowMapper`; it binds records through their canonical constructor and converts a property name like `occurredAt` to the column `occurred_at`. `UUID`, `BigDecimal`, `long`/`Long`/`Integer` and `Instant` (from `timestamptz`) all map.
- Enum columns (`status`) map from their `String` through the default conversion service. This was verified with webhook's `DeliveryStatus`/`EndpointStatus` and pricing's plan/rating enums.
- `null` columns map to wrapper types (`Integer`, `UUID`); primitives must be `NOT NULL` in the query.
- A PostgreSQL array cannot map to a collection. Select `array_to_string(event_types, ',') as event_types_csv` into a `String` view field and split it in an accessor (webhook endpoints). An empty array becomes `""` and must yield `List.of()`.
- A `jsonb` column cannot map to a record. Select `rule::text` / `breakdown::text` into a `String` and parse it in the adapter (`RuleDto` for plans, the line JSON for a rating).
- Consequence: the explicit `RowMapper` fallback was not needed. No persistence class in these modules calls `query(RowMapper)`; the architecture rule passes without a grep fallback.

### usage

- Write: `UsageEventRow` (`@Table("usage_events")`) next to `JdbcUsageEventRepository`; the append-only `insert ... on conflict do nothing` and the idempotency probe stay explicit `JdbcClient` statements with the exact SQL. `UsageEventRepository` keeps only `addIfAbsent` and `findByIdempotencyKey`.
- Read: `UsageEventView` (history), `UsageRecordView` and `UsageTotalView`; port `UsageEventReadRepository` in `query/`; `JdbcUsageEventReadRepository` in `adapter/persistence/usage/` selects directly into the views. `UsageQueries` depends only on that port and `UnitOfWork` and maps views to the api `UsageRecord`/`UsageTotal`; the controller's GET handlers map views.
- `UsageQueries` no longer names `UsageEvent`; the controller keeps one `UsageEvent` mapping for the POST response.

### webhook

- Write: `WebhookEndpointRow` and `WebhookDeliveryRow` (`@Table`) and `ClaimedDeliveryRow` (join projection); `Rows` is reduced to the `clip` helper used by the attempt log. The locking claim (`for update ... skip locked`), the guarded attempt update (`where attempts = ? and status = 'IN_PROGRESS'`) and the append-only attempt insert keep their exact SQL.
- Read: `WebhookEndpointView`, `WebhookDeliveryView`, `DeliveryAttemptView`; ports `EndpointReadRepository`, `DeliveryReadRepository`; adapters in `adapter/persistence/{endpoint,delivery}`. New `EndpointQueries` and `DeliveryQueries` own list/get; `EndpointAdministration` and `DeliveryAdministration` keep only update/rotate-secret and resend.
- `WebhookEndpointRepository` loses `list`; `WebhookDeliveryRepository` loses `list` and `attempts`. Query methods take `UUID`s, not domain ids, so the read side names no domain id record.
- The integration test now calls the query classes for reads; its assertions are unchanged, and the resend/pending checks construct the domain id they need.

### pricing

- Write: `PricingPlanRow`, `PlanVersionRow` and `UsageRatingRow` (`@Table`) plus `RatedUsageRow` (link projection). `JdbcPlanRepository` and `JdbcRatingRepository` map with `query(Class)`; the JSON of a rule/breakdown is decoded in the Row (`RuleDto`). `PlanRepository` loses `list`; `RatingRepository` keeps the reads that decide writes (`findById`, `ratedAmong`, `failedAttempts`).
- Read: `PricingPlanView`/`PlanVersionView` (the rule stays as stored JSON text), `UsageRatingView`/`PriceLineView`, and `MetricReadRepository`; ports `PlanReadRepository`, `RatingReadRepository`; adapters in `adapter/persistence/{plan,rating,metric}`. `PlanQueries`, `RatingQueries` and `MetricCatalogService` depend only on read ports.
- `MetricRegistry` loses `unitOf` (it now only registers a metric); `JdbcMetricRegistry` keeps a private lookup for the conflict check. `MetricCatalogService` reads through `MetricReadRepository`.
- The controller parses `VersionView.rule` back into `RuleDto` for the response, so `PlanResponse`/`VersionResponse` and the committed OpenAPI contract are unchanged.
- The unused pricing `Rows` helper was deleted.

### Result

- `./gradlew build` green; 551 tests, 0 failures (same as phase 1). `OpenApiContractTest` passes.
- `ArchitectureTest` passes with `usage`, `webhook` and `pricing` in the persistence and read/write sets.

## Verification

- `cd billing && ./gradlew :usage:test :webhook:test :pricing:test :app-boot:test --tests '*Architecture*' --tests '*Webhook*' --tests '*Pricing*'` passes.
- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `git grep -n 'RowMapper<' -- 'billing/usage/src/adapter' 'billing/webhook/src/adapter' 'billing/pricing/src/adapter'` returns no mapper that yields a domain type.
- No class under `..query..` imports a write repository; no write repository declares a list, page or search method.
- `billing/app-boot/src/main/resources/db/migration/` is unchanged and the OpenAPI contract test passes.

## Risks and rollback

- A mapping difference (rounding, null versus zero, time zone) changes returned values; the integration tests are the signal and none may be weakened.
- A view that selects fewer columns than the old domain object can drop a field a response still needs. Compare each GET response before and after through the existing HTTP tests.
- Rollback is reverting the repository or feature commit; the Row or view file goes with it.
