---
phase: 8
title: "Docs and closure"
status: completed
priority: P1
effort: "0.5d"
dependencies: [7]
---

# Phase 8: Docs and closure

## Goal

Make the documentation match the new structure and leave the architecture rules with no exemption.

## Context

`docs/architecture.md` line 92 says "Tactical DDD only: ... no CQRS, event sourcing or layer-per-module splitting" with the reason "The confirmed needs are consistency and boundaries, not read/write separation". The project owner has since chosen in-process CQRS, so that row and its trade-off text are now wrong. The earlier implementation plan lists CQRS as a non-goal; that plan is a stateful record and stays as it is, but its constraint is superseded for the structure of `billing/`.

## Files to Create / Modify

- Modify: `docs/architecture.md` (rows "Pattern scope", "Persistence", "Backend layering", the "Trade-offs" list and the paragraph "Persistence row types live in the adapter...").
- Modify: `billing/app-boot/src/test/java/io/corebilling/architecture/ArchitectureRules.java` and the test that passes the sets, so every context is checked unconditionally.
- Modify: `README.md` only if it describes the package layout (read it first).

## Tasks & Steps

- [x] 1. Read `docs/architecture.md` and any other doc that mentions package layout, persistence or CQRS (`git grep -n -iE 'cqrs|application package|adapter package|Spring Data JDBC|row type|read/write' -- docs README.md`).
- [x] 2. Rewrite the listed rows, trade-offs and paragraph in plain complete sentences: the command side (use cases, aggregates, write ports, Row persistence), the query side (views, read ports, direct SQL), the same database and the same transaction scope, immediate consistency, the Row placement, the rule for guarded SQL, and that event sourcing, a bus and a separate read store are not used. Add the condition under which this stops being enough (a read workload that needs its own store) and what switching costs.
- [x] 3. Remove the three context sets from the rules so every context is checked; keep only the non-context packages (`sharedkernel`, `boot`) excluded.
- [x] 4. Run the whole build and the final checks below.
- [x] 5. Compare the result with the confirmed outcome in [plan.md](./plan.md) and report anything not met.

## Phase notes

### Documentation

- `docs/architecture.md`: rewrote "Backend layering" (feature-then-role packages, no class in a layer root), "Persistence" (Row per aggregate, `JdbcAggregateOperations` for plain writes, explicit SQL only where the statement is the rule, views through `Jdbc*ReadRepository`) and "Pattern scope" (in-process CQRS over one database and one transaction; no event sourcing, bus, async projections or separate read store).
- Added the in-process CQRS trade-off: it fails first when reporting or search needs its own index or independent scaling; then a projection and a separate read store are added behind the existing read ports, while the command side and ledger stay. The cost today is two models (Row and View) and keeping list/page/search SQL out of the write side.
- Rewrote the "Persistence row types live in the adapter" paragraph with the Row placement and the guarded-SQL rule.
- `README.md` does not describe the package layout, so it was not changed. No remaining sentence in `docs/` says the project has no CQRS.

### Rules

- `ArchitectureRules.ContextSets` is gone. Every rule applies to every context discovered by its package pattern; `sharedkernel` and `boot` remain the only non-contexts for the cross-context rule. `ArchitectureTest` and `ArchitectureMetaTest` call `ArchitectureRules.all(root)`.
- The meta-test still pins the exact rule-id list, so removing a rule fails `ruleSetIsExactlyTheExpectedOne`, and each rule still names its culprit fixture.

### Package-size refinement

- The final size check found three packages above the "about 10" guide. `pricing.domain.plan` (19) was split so the pricing model strategies live in `pricing.domain.model` (11) and the plan aggregate/value types stay in `plan` (8).
- `platform.adapter.persistence` and `payment.adapter.persistence` (14 each) were grouped by feature (`tenant`, `role`, `user`, `credential`, `tenantsystem`; `payment`, `refund`, `stripe`, `incident`). The small shared technical helpers `Causes` and `Rows` stay at the persistence root and are now public so the feature packages can use them; they hold no domain rule.
- Remaining largest source packages: `shared-kernel` 13 (non-context), `pricing.domain.model` 11, `boot.infrastructure` 11 (wiring), `platform.api.actor` 10, `money.adapter.persistence` 10. The corrected size check uses `git ls-files 'billing/**/*.java'`; the plan's `billing/*.java` pattern matches no nested file.

### Verification

- `cd billing && ./gradlew build` green; 551 tests, 0 failures (same as phase 1).
- `ArchitectureTest`, `ArchitectureMetaTest` and `ModulithVerificationTest` pass with every context checked and no exemption.
- `git diff -- billing/app-boot/src/main/resources/db/migration contracts` is empty; `OpenApiContractTest` passes.
- All Success Criteria in [plan.md](./plan.md) are met. The only recorded limitations are the size exceptions above and the fact that the test count is the phase-1 baseline (551) rather than a higher number, because no test was added by the refactor.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `ArchitectureMetaTest` still fails when any layout, persistence or read and write rule is removed.
- `git ls-files 'billing/*.java' | grep -E 'src/(api|domain|application|adapter)/' | xargs -n1 dirname | sort | uniq -c | sort -rn | head -1` shows no package above about 10 classes.
- `git diff dev -- billing/app-boot/src/main/resources/db/migration` is empty; the OpenAPI contract test passes.
- Links in the edited docs resolve, and no remaining sentence in `docs/` says the project has no CQRS.

## Risks and rollback

- Documentation could claim more than the code does. Read each claim against the final package tree before committing.
- Rollback is reverting the docs and rule-set commit; the earlier phases stand on their own.
