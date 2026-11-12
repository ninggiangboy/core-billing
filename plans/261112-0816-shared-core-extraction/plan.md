---
title: Shared core for billing and sample-tenant
status: completed
created: 2026-11-12
completed: 2026-11-12
---

# Shared core extraction

## Outcome

A top-level `core/` Gradle build consumed by `billing/` and `sample-tenant/` through `includeBuild("../core")`.
`core-kernel` is plain Java; `core-spring` holds the Spring implementations. Both systems still deploy alone.

## Decisions (confirmed with the user, 2026-11-12)

- The rule "contracts hold documents, never code" is loosened: a shared code core is allowed.
- Core holds technical ports and implementations, the event ports (`DomainEvent`, `EventEnvelope`, `EventInbox`, `EventPublisher`), `TenantId`, and new minimal DDD bases (`AggregateRoot`, `Entity`, `ValueObject`).
- `Money`, `Currency`, `MoneyDto`, `CustomerId`, `WalletId` stay in billing. Sample keeps its own `Money` and exceptions.
- `SpringUnitOfWork` lives in core with a `TransactionScopeHook`; billing plugs in the RLS `set_config('app.tenant_id')` hook, sample plugs in nothing.
- Outbox and inbox implementations (`JooqOutboxAppender`, `JooqEventInbox`, `OutboxRelay`) stay in billing.
- Sample moves fully to core: `Ids` deleted, `java.time.Clock` replaced by core `Clock`, loose `@Transactional` replaced by `UnitOfWork`.
- Assumed (not yet confirmed): directory `core/`, package `dev.ngb.core`, `includeBuild` rather than a published artifact, sample emits domain events only when a use case needs it.

## Non-goals

Behaviour, DB schema, HTTP and event contracts, Gradle source-set layout, Maven publishing, moving `Money` or other business ids.

## Phases

| Phase | Owner | Content | Status |
|---|---|---|---|
| 1 | cmd-worker, then review | Create `core/`; move ports, impls, event ports, `TenantId`; add `TransactionScopeHook`; migrate billing; update ArchUnit rules and CI path filters | done |
| 2 | cmd-worker, then review | Migrate sample-tenant to core; delete `Ids`; add minimal DDD bases and adopt them in one aggregate per system | done |
| 3 | Claude | Rewrite `docs/architecture.md` and `docs/sample-tenant-system.md`; `contracts/README.md` wording | done |

## Outcome notes

- Verified on the merged tree: `core` build green; `sample-tenant` 82 tests and `checkNoBillingDependency` green; `billing` 83 suites, 752 tests, 0 failures, including the architecture, row-level security and unit-of-work tests.
- Adopted base types: `TenantSystem` (billing) and `WebhookEvent` (sample) extend `AggregateRoot`; `Money` implements `ValueObject`. Every other sample domain type is a record, so adopting `AggregateRoot` anywhere else in the sample costs the same record-to-class conversion.
- `AggregateRoot.register` and `pullEvents` have no caller yet.
- Still open: whether the sample should emit domain events; today it has no implementation of the event ports.

## Acceptance

- `./gradlew build` passes in `billing/` and `sample-tenant/` (tests needing Docker are reported if they cannot run).
- `grep -rn "sharedkernel.runtime" billing --include='*.java'` and `grep -rn "shared.id.Ids" sample-tenant --include='*.java'` return nothing.
- ArchUnit proves `core/` imports nothing from `dev.ngb.core_billing` or `dev.ngb.sampletenant`.
- `RowLevelSecurityIntegrationTest` still passes with the RLS hook plugged in.
- Both CI workflows trigger on `core/**`.
