---
phase: 2
title: "Layout: small modules"
status: completed
priority: P1
effort: "1d"
dependencies: [1]
---

# Phase 2: Layout: small modules

## Goal

Move `usage`, `catalog`, `execution` and `webhook-receiver` into the feature-then-role package layout, and rename `webhook-receiver` to `webhook`. Packages only: no behaviour, no persistence change.

## Context

- Target layout, as in Billing (`billing/contexts/usage`, `billing/contexts/webhook`): `dev.ngb.sampletenant.<module>.<layer>.<feature>.<role>`; adapters `adapter.<feature>.<technology>`; tests mirror production packages.
- Decision D2: keep module `billing-client`, rename only `webhook-receiver` (its package root is already `webhook`).
- Deep mode: start this phase with a scouting pass over the four modules (files, imports, wiring in `boot/config`).

## Proposed mapping (confirm while scouting)

| Module | Feature | Moves |
|---|---|---|
| usage | `usage` | `UsageRecord` to `domain.usage`; `RecordUsageUseCase` to `application.usage.command`; `UsageRepository` to `.port`; `UsageQueries` to `.query`; adapters to `adapter.usage.persistence` and `adapter.usage.web`; api `UsageRecorder` to `api.recording` |
| catalog | `plan` | `CatalogPlan`, `PricingModel`, `ResourceType` to `domain.plan`; `AttachBillingPlanUseCase` to `.command`; `CatalogPlanRepository` to `.port`; `CatalogQueries` to `.query`; adapters to `adapter.plan.{persistence,web}`; api `CatalogLookup` to `api.lookup` |
| execution | `queue` | `ExecuteOrdersUseCase` to `application.queue.command`; `ExecutionQueue` to `.port`; `JdbcExecutionQueue` to `adapter.queue.persistence` (no domain or api layer exists); both move to `resource` in phase 8 (decision D1), so here they only change package |
| webhook | `event` | `WebhookEvent`, `WebhookSignature` to `domain.event`; `ReceiveWebhookUseCase` to `.command`; `WebhookRepository`, `CustomerPeriods`, `DebtBook` to `.port`; `WebhookPolicy` and `WebhookEnvelope` to `.service` (an envelope used only by a port moves to `.port`); adapters to `adapter.event.{persistence,web}` |

Spring Data interfaces and Rows (`*JdbcRepository`, `*Row`) move with their `Jdbc*Repository` and stay as they are until phase 6 and later.

## Files

- Modify/rename (git mv): all `.java` under `sample-tenant/modules/{usage,catalog,execution,webhook-receiver}/src/**` and their tests
- Rename directory: `sample-tenant/modules/webhook-receiver` to `sample-tenant/modules/webhook`; edit `sample-tenant/settings.gradle.kts`, `sample-tenant/apps/app-boot/build.gradle.kts`, and every module `build.gradle.kts` that references `:modules:webhook-receiver`
- Modify: `sample-tenant/apps/app-boot/src/main/java/dev/ngb/sampletenant/boot/config/{Usage,Catalog,Execution,Webhook}Configuration.java` (imports only)
- Modify: other modules' imports of moved `api` types (`resource`, `customer`, `execution`)

## Tasks and steps

1. Scout the four modules; confirm or adjust the mapping table; write down any class whose role is unclear (decide: data carrier for a port goes to `port`, otherwise `service`).
2. One module per commit, in order usage, catalog, execution, webhook: `git mv` files, rewrite `package` and `import` lines, move tests to mirror, fix `boot/config` imports. Commit message `refactor(sample-tenant-<module>): group packages by feature and role`.
3. Rename `webhook-receiver` to `webhook` in its own commit (`git mv`, Gradle paths, `README.md` and `docs/sample-tenant-system.md` references found by grep).
4. Keep `*Queries` returning what they return today; they only change package.

## Verification

- After each commit: `cd sample-tenant && ./gradlew checkNoBillingDependency build` green, test count unchanged from the phase 1 baseline.
- `git diff --stat -M` of each move commit shows renames with import-only edits, no logic changes.
- `grep -rn 'webhook-receiver' sample-tenant --include='*.kts' --include='*.md'` returns nothing (docs under `docs/` checked in phase 9).

## Risks

- A bean wired by type in `boot/config` fails at startup if a class moves and an import is missed; the build compiles it, and phase 5 adds a context test.
- `JdbcExecutionQueue` has no domain or api layer: the layout rules must not require one (the rules only constrain classes that exist).

## Todo

- [x] usage, catalog, execution, webhook moved
- [x] `webhook-receiver` renamed
- [x] Build green after each commit
