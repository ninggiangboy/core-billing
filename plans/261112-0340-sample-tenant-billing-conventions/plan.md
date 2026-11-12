---
title: "Sample tenant on Billing's conventions"
description: "Give sample-tenant Billing's package layout, jOOQ persistence, command/query split and ArchUnit enforcement, fix its one cross-module import, and add the persistence and HTTP tests it lacks."
status: completed
priority: P1
effort: 11d
branch: dev
tags: [refactor, backend, architecture, persistence, jooq, cqrs, testing]
blockedBy: []
blocks: []
created: 2026-11-12
---

# Sample tenant on Billing's conventions

## Overview

A review of `sample-tenant/` against `billing/` (2026-11-12) found the same four source sets and the same
layered `build-logic`, but a flat package tree, Spring Data JDBC Rows mixed with `JdbcTemplate`, reads that return
domain aggregates through write ports, 3 ArchUnit rules against Billing's 33, one real cross-module import
(`customer.adapter.identity.CustomerAccountListener` imports `identity.application` and `identity.domain`), and no
test that runs SQL or a controller. The user confirmed two scope decisions: the sample moves to **jOOQ**, and it adopts
Billing's **command/query split**. This plan applies the same shape that
[the Billing restructure](../261110-1538-billing-package-and-persistence-restructure/plan.md) and
[the jOOQ migration](../261111-1317-jooq-persistence-migration/plan.md) applied to Billing, in one pass: the
sample never gets the hand-written Row stage that Billing built and then threw away.

## Outcome, constraints, non-goals, acceptance

- **Outcome.** `sample-tenant` reads like `billing`: one role per package, a write side and a read side, one
  persistence stack, boundaries enforced by the build. A renamed column in a Flyway migration breaks the build.
- **Constraints.**
  - Layout in every layer, `api` included: `dev.ngb.sampletenant.<module>.<layer>.<feature>.<role>` with roles
    `command`, `query`, `port`, `service`, `exception`. Adapters: `adapter.<feature>.<technology>`. Use cases are
    `*UseCase` in `command`, query classes `*Queries` in `query`, a class serving another module's `api` interface is
    `<Interface>Service` in `service`.
  - Write side: aggregates, write ports, `Jooq*Repository`. Read side: immutable `*View` records,
    `*ReadRepository` ports in `query`, `Jooq*ReadRepository` selecting straight into views. Never a Row, never an
    aggregate on the read side. Same database, no bus, no event sourcing.
  - jOOQ replaces Spring Data JDBC, the 6 `@Table` Rows and every `JdbcTemplate` call. Generated code lives in a new
    sample-owned module `libs:jooq-schema` (package `dev.ngb.sampletenant_jooq`), is committed, and is regenerated from
    the Flyway migrations by a Testcontainers test with a drift check, exactly as in Billing. A normal compile needs no
    Docker. Special SQL (`for update skip locked ... returning`, `on conflict do nothing|update`, guarded updates) stays
    explicit statements, never `store()`.
  - The sample stays an independent build: no Gradle dependency on `billing/`, `checkNoBillingDependency` stays green.
    The shared `core/` build is used as is and is not edited.
  - One module per commit, `cd sample-tenant && ./gradlew checkNoBillingDependency build` green at every commit.
    Conventional commits, no plan or phase identifiers in code, test names or messages. Branch `dev`.
- **Non-goals.** Behaviour, DB schema and migrations, HTTP and webhook contracts, `core/`, `billing/`, new use-case
  transactions beyond what exists, typed ids (the sample keeps `UUID`), domain events, a separate read store.
- **Acceptance.** All of: `grep -rE 'JdbcTemplate|JdbcClient|JdbcAggregateOperations|@Table|starter-data-jdbc'
  sample-tenant --include='*.java' --include='*.kts'` is empty; the ported ArchUnit rules and their fixtures meta-test
  pass; every repository and every controller has an integration test; `./gradlew checkNoBillingDependency build`
  is green; `contracts/` unchanged; docs updated; the scenario-to-test matrix in `sample-tenant/README.md` still holds.

## Phases

| # | Phase | Status | Depends on |
|---|-------|--------|------------|
| 1 | [Baseline, cross-module fix and guard rules](./phase-01-baseline-cross-module-fix-and-guard-rules.md) | Completed | none |
| 2 | [Layout: small modules](./phase-02-layout-small-modules.md) | Completed | 1 |
| 3 | [Layout: remaining modules and layout rules](./phase-03-layout-remaining-modules-and-layout-rules.md) | Completed | 2 |
| 4 | [jOOQ foundation and persistence rules](./phase-04-jooq-foundation-and-persistence-rules.md) | Completed | 3 |
| 5 | [HTTP integration tests](./phase-05-http-integration-tests.md) | Completed | 4 |
| 6 | [Write and read sides: usage, catalog](./phase-06-write-and-read-sides-usage-catalog.md) | Completed | 5 |
| 7 | [Write and read sides: customer, identity](./phase-07-write-and-read-sides-customer-identity.md) | Completed | 6 |
| 8 | [Write and read sides: resource, webhook](./phase-08-write-and-read-sides-resource-webhook.md) | Completed | 7 |
| 9 | [Docs and closure](./phase-09-docs-and-closure.md) | Completed | 8 |

Phases run in sequence: every module imports `identity.api` and `app-boot` wires them all, so parallel edits would
conflict. Deep mode: phases 2 onward get a scouting pass of their own modules at the start of execution. The 11-day
effort is an estimate, not measured.

## Decisions (confirmed 2026-11-12)

| # | Decision | Outcome | Phase |
|---|----------|---------|-------|
| D1 | `JdbcOrderSubmission` (module `resource`) writes `orders` and `execution_queue` (owned by `execution`) in one transaction, which "a module uses only its own tables" forbids; a new `execution.api` would create a Gradle project cycle. | **`resource` owns `execution_queue`.** `execution` reaches it through a new `resource.api.execution.ExecutionQueue` (the dependency direction `execution` to `resource` already exists), loses its persistence, and `CreateOrderUseCase` opens core's `UnitOfWork` around the two writes. | 4, 8 |
| D2 | Renaming `billing-client` to `billing` would make `checkNoBillingDependency` flag the sample's own project path. | Keep `billing-client`; rename only `webhook-receiver` to `webhook`. | 2, 3 |
| D3 | The sample needs an ArchUnit rule set; Billing's is 693 lines, partly Billing-specific. | Copy the generic rules (about 25) with the sample's own fixtures module; no shared rules library, `core/` untouched. | 1, 3, 4 |
| D4 | Spring Modulith verification as in Billing. | Included: build-logic lines, `package-info` named interfaces, `ModulithVerificationTest`. | 3 |
| D5 | `.github/workflows/core.yml` so `core/` tests run in CI (review finding 1). | Still open and outside this plan; the user has not decided. | none |

## Key risks

| Risk | Mitigation | Phase |
|---|---|---|
| A layout move changes a bean's package and Spring wiring breaks silently. | Wiring is in `boot/config/*Configuration`; compile plus the app context test catch it; one module per commit. | 2, 3 |
| jOOQ maps `timestamptz` to `OffsetDateTime`, the domain uses `Instant`. | Same forced type as Billing (`INSTANT`), proven by the pilot repository test. | 4, 5 |
| `execution_queue` claim loses `for update skip locked` semantics in the DSL, or the queue move (D1) changes worker behaviour. | Keep `claim` a single explicit `update ... returning` statement; a two-claimer concurrency test asserts disjoint batches; the phase 5 worker path test must pass unedited. | 8 |
| A view drops a field a controller returns, or a jOOQ statement behaves differently. | Phase 5 writes HTTP tests that assert full response bodies against the current persistence, before phases 6 to 8 change it; they must pass unedited after each module. | 5-8 |
| `CustomerBillingViews` mixes commands (`openSession`, `topUp`) with reads, so splitting it changes constructors and wiring. | Split mechanically into two use cases and one query class; behaviour is asserted by the existing `CustomerBillingViewsTest` moved with it. | 3 |
| Rules land red before the tree complies. | Layout rules are enabled at the end of phase 3, persistence rules in phase 4 (no subjects until phase 6) and command/query rules at the end of phase 8; the meta-test proves each rule on fixtures when it lands. No exemption list. | 3, 4, 8 |

## Success criteria

- [x] Acceptance conditions above hold on `dev`.
- [x] Each phase's Verification passes and its commits are on `dev`.
- [x] Decisions D1-D4 are recorded in the plan and in `docs/sample-tenant-system.md`.

## Validation Log

### Session 1 (2026-11-12)

- Verification: about a dozen claims spot-checked against the code (file paths, module dependencies, port signatures, the `@Table` Row count, Billing reference files). All held except one, corrected in phase 1 (`AccountListener` has a second callback, `deactivated`, used by `ManageUsersUseCase`). The Gradle cycle behind D1 was verified in `modules/execution/build.gradle.kts` (depends on `:modules:resource`).
- Interview: D1 to D4 answered as in the table above; D1 is the only answer that differs from the recommendation (queue ownership moves to `resource`), and phases 2, 4, 6 and 8 were updated.
- Baseline commit sha for the "billing, core and contracts unchanged" check: `84adc219b8cd6dab56761ef88dbbcc44f5ac7bab` (recorded during cook, 2026-11-12); baseline build green with 82 tests.

### Session 2 (2026-11-12, execution)

- Baseline: `84adc219b8cd6dab56761ef88dbbcc44f5ac7bab`, 82 tests green. Final: `checkNoBillingDependency build` green with 190 tests; Billing's `*architecture*` tests green; `git diff 84adc21..HEAD -- contracts billing core` empty.
- Test counts by phase: 98 after phase 1, 125 after phase 4, 159 after phase 5, 164 after phase 6, 171 after phase 7, 190 after phase 8.
- Acceptance grep `JdbcTemplate|JdbcClient|JdbcAggregateOperations|@Table|starter-data-jdbc|@Transactional` is empty in `sample-tenant`.
- Deviations, each consistent with the plan's stated conventions:
  - `inner-layer-purity` allows `javax..` as part of the JDK, because the webhook domain verifies HMAC with `javax.crypto`.
  - `OrderExecutionService` became `ExecutionOrdersService` so `api-implementations-are-services` holds (`<Interface>Service`).
  - `role-package-not-layer-root` uses `allowEmptyShould(true)` because the cleaned production tree has no class in a layer root; the fixture meta-test still proves it fails.
  - `CustomerQueries` and `CatalogQueries` return views and no longer implement their `api` interfaces; `CustomerDirectoryService` and `CatalogLookupService` serve the api through the read repositories.
  - HTTP tests run with the `test` profile, which excludes `BillingConfiguration`; `FakeBilling` provides the named beans. The qualifiers on the Billing injections were kept because one HTTP client implements every interface.
  - `AccountListener` was placed directly in `identity.api.account`, so phase 3 did not need to move it again.
- One review note carried over and still open: `.github/workflows/core.yml` (decision D5) remains outside this plan.

### Whole-Plan Consistency Sweep

- Searched all plan files for the superseded terms "co-owned", "JdbcOrderSubmission", "usage-catalog-execution" and "execution migrated". Remaining mentions are historical only (D1 row, phase 8 deletions).
