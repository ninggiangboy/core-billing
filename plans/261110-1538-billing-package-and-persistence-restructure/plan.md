---
title: "Billing package, persistence and CQRS restructure"
description: "Group every billing module's packages by feature then role, split each module into a command (write) side with Row-based persistence and a query (read) side with read models, and enforce it with ArchUnit."
status: in-progress
priority: P1
effort: 15d
branch: dev
tags: [refactor, backend, architecture, cqrs]
blockedBy: []
blocks: []
created: 2026-11-10
---

# Billing package, persistence and CQRS restructure

## Overview

The 8 billing modules already separate `api`, `domain`, `application` and `adapter` into Gradle source sets with different classpaths. Inside each layer, however, the packages are flat (20 to 30 files in `platform.application`, `pricing.domain`, `payment.application`, `money.api`). Persistence is split: `platform` and `customer` use `@Table` Row records, while `money`, `payment`, `pricing`, `usage` and `webhook` map SQL straight into domain entities. Reads and writes share the same repositories and the same domain objects. This plan fixes all three and makes the build refuse a return to the old shape.

Source of the decisions: the advisory interview of 2026-11-10, the later request for real CQRS the same day (both recorded in the project memory), and the current code. Do this plan before the invoicing module of [the implementation plan](../261109-1625-core-billing-implementation/plan.md) (its phase 9) so the new module starts in the new structure.

## Outcome, constraints, non-goals, acceptance

- **Outcome.** A reader opening any package sees one role. Every module has a write side and a read side with separate models. Every aggregate is persisted the same way in every module. The architecture tests fail when a class is placed outside the structure.
- **Constraints.**
  - Package layout in every source set, `api` included: feature first, then role (`command`, `query`, `port`, ...). `customer` and `usage` are small and use `command`, `query`, `port` without a feature level.
  - **Write side:** use cases in `command/`, aggregates in `domain`, write ports in `port/`, and a package-private `@Table` Row record next to each write repository, with `from` and `toDomain`. Plain insert and update use `JdbcAggregateOperations`.
  - **Read side:** immutable `*View` records and `*ReadRepository` ports in `query/`, `Jdbc*ReadRepository` in the adapter selecting directly into views with `JdbcClient`. A read model is never a domain aggregate or a Row. Query classes never depend on write ports, and the write side never depends on `query/`.
  - Special SQL (`for update`, `skip locked`, guarded status updates, append-only ledger insert, outbox and inbox) stays `JdbcClient` on the write side. A guarded or partial `update` is never replaced by a whole-row update. Ledger rows are only inserted.
  - Reads and writes use the same database and still run inside the tenant-scoped `UnitOfWork` so row-level security applies. Reads stay immediately consistent.
  - The money path stays one transaction. Modulith verification must pass with `api` sub-packages.
  - The package move, the write-side change and the read-side change are separate commits, one module (or repository, or feature) per commit. Conventional commit messages, with no plan or phase identifiers in code comments, test names or commits.
  - Execute on branch `dev` or a branch cut from it; no force-push or history rewrite.
- **Non-goals.** Behaviour, DB schema, HTTP and event contracts; Gradle source-set and classpath design; classes under `billing/app-boot/.../infrastructure`; event sourcing; a command or query bus (the `*UseCase` classes stay the command handlers); a separate read store or asynchronous projections; JPA; Spring Data repository interfaces.
- **Acceptance.** All Success Criteria below are checked and each phase's Verification passes.

## Phases

| # | Phase | Status | Depends on |
|---|-------|--------|------------|
| 1 | [Baseline and layout rules](./phase-01-baseline-and-layout-rules.md) | Pending | none |
| 2 | [Layout: customer, usage, webhook](./phase-02-layout-customer-usage-webhook.md) | Pending | 1 |
| 3 | [Layout: pricing, payment, money](./phase-03-layout-pricing-payment-money.md) | Pending | 2 |
| 4 | [Layout: platform and app-boot imports](./phase-04-layout-platform-and-app-boot.md) | Pending | 3 |
| 5 | [Write and read models: usage, webhook, pricing](./phase-05-write-and-read-models-usage-webhook-pricing.md) | Pending | 4 |
| 6 | [Write and read models: payment and money](./phase-06-write-and-read-models-payment-money.md) | Pending | 5 |
| 7 | [Write and read models: platform and customer](./phase-07-write-and-read-models-platform-customer.md) | Pending | 6 |
| 8 | [Docs and closure](./phase-08-docs-and-closure.md) | Pending | 7 |

Phases run in sequence: all modules share `platform.api` and `app-boot`, so parallel edits would conflict on imports. The effort of 15 days is an estimate, not measured.

## Key risks

| Risk | Mitigation | Phase |
|---|---|---|
| Modulith treats an `api` sub-package as internal and fails verification. | Spike on `customer.api` first; one `@NamedInterface` per used sub-package in the `adapter` source set. If it cannot work, stop and ask. | 2 |
| `JdbcClient.query(Class)` mis-maps a column type for Rows or views. | Pilot on `usage`; fall back to an explicit `RowMapper` that returns the Row or view. | 5 |
| A whole-row update drops a guard or rewrites immutable columns. | Read every repository's SQL first; keep guarded and partial updates as explicit statements. | 5, 6 |
| A view selects fewer columns than the old domain object and a response loses a field. | Existing HTTP tests assert GET bodies; compare before and after. | 5, 6, 7 |
| Moving authentication lookups to the query side changes the hot request path. | They stay on their current ports and are listed in the phase notes. | 7 |
| A read model leaks a domain type, or a query reaches a write port. | Read and write split rules in ArchUnit with fixtures; the domain allowlist is explicit and reviewed. | 1 |
| Huge diffs hide mistakes. | Move, write side and read side in separate commits, per module. | all |

## Success Criteria

- [ ] `cd billing && ./gradlew build` is green, including Testcontainers tests, `ModulithVerificationTest` and the OpenAPI contract test, and the test count is not below the baseline recorded in phase 1.
- [ ] No Java file sits directly in a layer root package in any module (other than `package-info`).
- [ ] No class in `adapter.persistence` maps a result set straight into a domain type; every aggregate has a `@Table` Row.
- [ ] Every module has `*View` read models and `*ReadRepository` ports; no query class depends on a write port; the write side does not depend on `query/`; no write port declares a list, page or search method.
- [ ] `billing/app-boot/src/main/resources/db/migration/` and the HTTP and event contracts are unchanged.
- [ ] The new ArchUnit rules pass with no exemption, and the meta-test fails when any one is removed.
- [ ] `docs/architecture.md` describes the new layout, persistence and read and write split, and no longer says the project has no CQRS.

## Validation notes

The outcome, constraints, non-goals and acceptance were confirmed with the user during the interview. The CQRS scope was added afterwards from a one-line request; the in-process, same-database form is an assumption and is marked in the non-goals so it can be widened. Facts read in code: the file counts per package, the split between Row-based and direct-mapping repositories, the source-set convention plugin, the current ArchUnit rule names, the `api` `package-info` for Modulith, and the `docs/architecture.md` text that this plan supersedes. Still unverified: column mapping with `JdbcClient.query(Class)`, Modulith behaviour with `api` sub-packages, guarded updates in repositories not yet read, whether ArchUnit can forbid `query(RowMapper)`, and how much of the query side each GET endpoint really needs.

<!-- slug: billing-package-and-persistence-restructure -->
