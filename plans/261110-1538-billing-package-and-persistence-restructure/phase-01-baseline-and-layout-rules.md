---
phase: 1
title: "Baseline and layout rules"
status: pending
priority: P1
effort: "0.5d"
dependencies: []
---

# Phase 1: Baseline and layout rules

## Goal

Record a green baseline and add the architecture rules that enforce the new package layout, Row-based persistence and the read and write split, applied only to modules that have been converted so far, so each later phase turns the rules on for its modules and the final phase leaves no exemption.

## Context

Confirmed decisions: [plan.md](./plan.md). Current rules live in `billing/app-boot/src/test/java/io/corebilling/architecture/` (`ArchitectureRules`, `ArchitectureTest`, `ArchitectureMetaTest`) and run the same rule set against production code and against `billing/arch-fixtures`.

## Files to Create / Modify

- Modify: `billing/app-boot/src/test/java/io/corebilling/architecture/ArchitectureRules.java`, `ArchitectureTest.java`, `ArchitectureMetaTest.java`
- Create or modify: classes under `billing/arch-fixtures/src/main/java/archfixtures/` that violate each new rule

## Tasks & Steps

- [ ] 1. Read `ArchitectureRules`, `ArchitectureTest`, `ArchitectureMetaTest` and one fixture; note how the root package is passed and how the meta-test asserts each rule fails on the fixtures.
- [ ] 2. On `dev`, run `cd billing && ./gradlew build`. Record the result and the number of executed tests in the phase notes. Stop if it is not green.
- [ ] 3. Make the rule set take three context sets: contexts whose layout is converted, contexts whose persistence is converted, and contexts whose read and write sides are split. Production starts with all three empty. The fixtures run with every fixture context in all three sets.
- [ ] 4. Add layout rules, each applying only to the layout set:
  - no class sits directly in `<root>.<ctx>.domain`, `.application`, `.adapter` or `.api` (exact package, `package-info` excepted);
  - a class named `*UseCase` or `*Queries` in `application` resides in a `..command..` or `..query..` package;
  - a write-side repository or other outbound port interface in `application` resides in a `..port..` package; a read port named `*ReadRepository` resides in a `..query..` package;
  - a class annotated `@Table` resides in `..adapter.persistence..`.
- [ ] 5. Add the persistence rule, applying only to the persistence set: no class in `..adapter.persistence..` calls `JdbcClient` `query` with a `RowMapper` argument. Calls taking a `Class` argument stay allowed. If ArchUnit cannot express this, record the limit and replace it with a rule on classes implementing `RowMapper` plus a grep check in the phase 8 verification.
- [ ] 6. Add read and write split rules, each applying only to the read and write set:
  - classes in `..command..` or `..port..` do not depend on classes in `..query..`;
  - classes in `..query..`, and the adapter classes named `Jdbc*ReadRepository`, do not depend on classes in `..command..` or `..port..` (write ports);
  - the same classes do not depend on domain types except an explicit, reviewable allowlist of value objects and enums, so a read model can never be a domain aggregate;
  - interfaces in `..port..` declare no method named `list*`, `page*` or `search*` (the write side never serves reads; this is a name rule and cannot catch a differently named method).
- [ ] 7. Add one violating fixture per new rule and extend the meta-test so removing any rule makes it fail.
- [ ] 8. Run `./gradlew :app-boot:test --tests '*Architecture*'`; production still passes because all three sets are empty.

## Verification

- `cd billing && ./gradlew :app-boot:test --tests '*Architecture*'` passes.
- Deleting any new rule from the rule list makes `ArchitectureMetaTest` fail.
- `cd billing && ./gradlew build` is green with the same test count as the baseline.

## Risks and rollback

- ArchUnit may not see a lambda `RowMapper`, which is what the current repositories use. Step 5 covers this by checking the `query` call, not the mapper type. Rollback is reverting the test and fixture files only; no production code changes in this phase.
