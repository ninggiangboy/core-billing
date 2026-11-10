---
phase: 1
title: "Baseline and layout rules"
status: completed
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

- [x] 1. Read `ArchitectureRules`, `ArchitectureTest`, `ArchitectureMetaTest` and one fixture; note how the root package is passed and how the meta-test asserts each rule fails on the fixtures.
- [x] 2. On `dev`, run `cd billing && ./gradlew build`. Record the result and the number of executed tests in the phase notes. Stop if it is not green.
- [x] 3. Make the rule set take three context sets: contexts whose layout is converted, contexts whose persistence is converted, and contexts whose read and write sides are split. Production starts with all three empty. The fixtures run with every fixture context in all three sets.
- [x] 4. Add layout rules, each applying only to the layout set:
  - no class sits directly in `<root>.<ctx>.domain`, `.application`, `.adapter` or `.api` (exact package, `package-info` excepted);
  - a class named `*UseCase` or `*Queries` in `application` resides in a `..command..` or `..query..` package;
  - a write-side repository or other outbound port interface in `application` resides in a `..port..` package; a read port named `*ReadRepository` resides in a `..query..` package;
  - a class annotated `@Table` resides in `..adapter.persistence..`.
- [x] 5. Add the persistence rule, applying only to the persistence set: no class in `..adapter.persistence..` calls `JdbcClient` `query` with a `RowMapper` argument. Calls taking a `Class` argument stay allowed. If ArchUnit cannot express this, record the limit and replace it with a rule on classes implementing `RowMapper` plus a grep check in the phase 8 verification.
- [x] 6. Add read and write split rules, each applying only to the read and write set:
  - classes in `..command..` or `..port..` do not depend on classes in `..query..`;
  - classes in `..query..`, and the adapter classes named `Jdbc*ReadRepository`, do not depend on classes in `..command..` or `..port..` (write ports);
  - the same classes do not depend on domain types except an explicit, reviewable allowlist of value objects and enums, so a read model can never be a domain aggregate;
  - interfaces in `..port..` declare no method named `list*`, `page*` or `search*` (the write side never serves reads; this is a name rule and cannot catch a differently named method).
- [x] 7. Add one violating fixture per new rule and extend the meta-test so removing any rule makes it fail.
- [x] 8. Run `./gradlew :app-boot:test --tests '*Architecture*'`; production still passes because all three sets are empty.

## Phase notes

- Baseline on `dev`: `cd billing && ./gradlew clean build` = BUILD SUCCESSFUL in 1m 4s, 533 tests in 50 test classes, 0 failures, 0 skipped. This is the test count every later phase must match.
- Three context sets are `ArchitectureRules.ContextSets(layout, persistence, readWrite)`. `ArchitectureTest` holds the three production sets (empty in this phase); `ArchitectureMetaTest` treats every fixture context (`alpha`, `beta`, `money`, `payment`, `webhook`, `layout`, `split`) as converted in all three.
- ArchUnit can express the `RowMapper` rule: the `query` method is declared on `JdbcClient.StatementSpec`, so the predicate checks the call target's owner against `JdbcClient.StatementSpec` and its raw parameter types against `RowMapper`. No grep fallback is needed.
- The outbound-port rule is name based (interfaces in `application` whose name ends in `Repository`, `Port`, `Provider`, `Access`, `Sender`, `Signer`, `Cipher`, `Writer`, `Store`, `Keys`, `Limiter`, `Units` or `Registry`); `*ReadRepository` is checked first and must live in `..query..`.
- The domain-type allowlist for read models (`ALLOWED_QUERY_DOMAIN_TYPES`) starts empty; enums are always allowed.
- `ArchitectureMetaTest.ruleSetIsExactlyTheExpectedOne` pins the rule IDs, so deleting a rule fails the meta-test; each rule also names its culprit fixture.

## Verification

- `cd billing && ./gradlew :app-boot:test --tests '*Architecture*'` passes.
- Deleting any new rule from the rule list makes `ArchitectureMetaTest` fail.
- `cd billing && ./gradlew build` is green with the same test count as the baseline.

## Risks and rollback

- ArchUnit may not see a lambda `RowMapper`, which is what the current repositories use. Step 5 covers this by checking the `query` call, not the mapper type. Rollback is reverting the test and fixture files only; no production code changes in this phase.
