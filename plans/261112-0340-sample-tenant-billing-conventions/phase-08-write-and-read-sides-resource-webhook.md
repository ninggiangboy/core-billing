---
phase: 8
title: "Write and read sides: resource, webhook"
status: completed
priority: P1
effort: "2d"
dependencies: [7]
---

# Phase 8: Write and read sides: resource, webhook

## Goal

Finish the migration with the two modules that have the most SQL, give `resource` ownership of the execution queue (decision D1), remove Spring Data JDBC from the build, and turn on the command/query and repository rules so the new shape is enforced.

## Modules

| Module | Write side | Read side | Special |
|---|---|---|---|
| resource / order | `OrderRepository`: `save` (upsert with `set status = excluded.status ...`), `findById` | `OrderReadRepository`: `forCustomer`, `forBillingCustomer`, `all`, `byId`; `OrderView`; `OrderQueries`; `OrderExecutionService implements ExecutionOrders` decides with the write port | `OrderController` maps from `OrderView`, keeping every field of today's `OrderResponse` |
| resource / queue (moved from `execution`) | `OrderQueueRepository` port (`enqueue`, `claim`, `finish`, `requeue`) with `JooqOrderQueueRepository`; `ExecutionQueueService implements ExecutionQueue` (new api interface `resource.api.execution.ExecutionQueue` with `claim(int)`, `finish`, `requeue` and a `Claimed(orderId, attempts)` record) | none | `claim` stays one statement: `update execution_queue set status = 'RUNNING', claimed_at = ?, attempts = attempts + 1 where id in (select ... where status = 'PENDING' ... for update skip locked) returning order_id, attempts` |
| resource / submission | `CreateOrderUseCase` opens core's `UnitOfWork` around `orders.save(order)` and `queue.enqueue(order.id())`, as Billing's use cases do; `OrderSubmission`, `JdbcOrderSubmission` and every `@Transactional` are deleted | none | the order and its queue row still commit together or not at all |
| execution | none: `ExecuteOrdersUseCase` uses `resource.api.execution.ExecutionQueue` instead of its own port and adapter | none | `execution` keeps only its application layer; its `build.gradle.kts` drops the persistence dependencies; the direction `execution` to `resource` is unchanged, so no Gradle cycle |
| webhook / event | `WebhookRepository`: `saveIfAbsent` (`on conflict (billing_event_id) do nothing`, returns whether inserted), `findByBillingEventId`, `save` (guarded update of `status`, `decision`, `detail`); `DebtBook` (insert, clear open debts); `CustomerPeriods.extend` (`on conflict (billing_customer_id) do update`) | none (no list endpoint) | `WebhookEvent` stays an `AggregateRoot`; `restore(...)` builds it from a record; the `query` package is absent and the rules allow that |

## Files

- Modify/create/delete: `sample-tenant/modules/{resource,execution,webhook}/src/{api,application,adapter,test}/**`
- Modify: `sample-tenant/modules/execution/build.gradle.kts`, `sample-tenant/modules/resource/build.gradle.kts`
- Modify: `sample-tenant/apps/app-boot/src/main/java/dev/ngb/sampletenant/boot/config/{Resource,Webhook,Execution}Configuration.java`, `boot/jobs/ExecutionJob.java` (only if its types change)
- Modify: `sample-tenant/apps/app-boot/build.gradle.kts` (remove `spring-boot-starter-data-jdbc`; keep what the jOOQ starter and Flyway need)
- Modify: `ArchitectureRules.java`, `ArchitectureTest.java`, `ArchitectureMetaTest.java`, fixtures

## Tasks and steps

1. Scout the three modules; confirm which `OrderRepository` methods only list or filter for a caller, and every caller of `ExecutionQueue` and `OrderSubmission`.
2. Resource order (one commit), then the queue move: add the api interface and `Claimed`, port, `ExecutionQueueService`, `JooqOrderQueueRepository`; switch `ExecuteOrdersUseCase` and its fakes to the api interface; delete `execution`'s port and adapter. Then `CreateOrderUseCase` with `UnitOfWork` and the removal of `OrderSubmission` (own commit). Then webhook (own commit). Pattern steps 1 to 7 of phase 6.
3. Tests: upserts, the conflict-ignoring insert (returns false the second time), the guarded update; a two-claimer concurrency test on `claim` asserting disjoint batches and `attempts` incremented; a `CreateOrderUseCase` integration test that forces `enqueue` to fail and asserts the `orders` row is absent (the property the old `@Transactional` gave).
4. Remove `starter-data-jdbc` and any leftover `JdbcTemplate` bean wiring; run the acceptance grep.
5. Enable the CQRS and repository rules with fixtures: `write-side-does-not-depend-on-query`, `read-side-does-not-depend-on-write`, `query-side-uses-no-domain-aggregates`, `write-ports-serve-no-reads`, `repository-pair-shares-a-package`. Update the meta-test's expected list. Remove `allowEmptyShould` from the phase 4 persistence rules if every rule now has subjects.

## Verification

- `cd sample-tenant && ./gradlew checkNoBillingDependency build` green with Docker.
- Acceptance grep: `grep -rE 'JdbcTemplate|JdbcClient|JdbcAggregateOperations|@Table|starter-data-jdbc|@Transactional' sample-tenant --include='*.java' --include='*.kts'` is empty.
- All phase 5 HTTP tests pass without assertion edits (orders, usage, webhooks, and the execution worker path if phase 5 covered it).
- `module-uses-only-own-tables` passes with `execution_queue` owned by `resource` alone; every rule in `ArchitectureRules.all` passes on production code and fails on its fixture.

## Risks

- The queue move is the largest non-mechanical change of the plan: it changes which module owns a table and adds one `api` interface. Behaviour must not change; the concurrency test and the phase 5 worker test are the guard.
- `ExecuteOrdersUseCase`'s test `Fakes` implement the old `ExecutionQueue` port; update them to the api interface in the same commit.
- `query-side-uses-no-domain-aggregates` forbids views naming domain types other than enums; use primitives and strings (as `OrderResponse` already does).

## Todo

- [x] resource order migrated and committed
- [x] queue moved to resource, execution has no persistence
- [x] `CreateOrderUseCase` opens the unit of work, `OrderSubmission` gone
- [x] webhook migrated and committed
- [x] Spring Data JDBC removed, grep clean
- [x] CQRS and repository rules enabled
