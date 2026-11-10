---
phase: 8
title: "Docs and closure"
status: pending
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

- [ ] 1. Read `docs/architecture.md` and any other doc that mentions package layout, persistence or CQRS (`git grep -n -iE 'cqrs|application package|adapter package|Spring Data JDBC|row type|read/write' -- docs README.md`).
- [ ] 2. Rewrite the listed rows, trade-offs and paragraph in plain complete sentences: the command side (use cases, aggregates, write ports, Row persistence), the query side (views, read ports, direct SQL), the same database and the same transaction scope, immediate consistency, the Row placement, the rule for guarded SQL, and that event sourcing, a bus and a separate read store are not used. Add the condition under which this stops being enough (a read workload that needs its own store) and what switching costs.
- [ ] 3. Remove the three context sets from the rules so every context is checked; keep only the non-context packages (`sharedkernel`, `boot`) excluded.
- [ ] 4. Run the whole build and the final checks below.
- [ ] 5. Compare the result with the confirmed outcome in [plan.md](./plan.md) and report anything not met.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `ArchitectureMetaTest` still fails when any layout, persistence or read and write rule is removed.
- `git ls-files 'billing/*.java' | grep -E 'src/(api|domain|application|adapter)/' | xargs -n1 dirname | sort | uniq -c | sort -rn | head -1` shows no package above about 10 classes.
- `git diff dev -- billing/app-boot/src/main/resources/db/migration` is empty; the OpenAPI contract test passes.
- Links in the edited docs resolve, and no remaining sentence in `docs/` says the project has no CQRS.

## Risks and rollback

- Documentation could claim more than the code does. Read each claim against the final package tree before committing.
- Rollback is reverting the docs and rule-set commit; the earlier phases stand on their own.
