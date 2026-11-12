---
phase: 1
title: "Baseline, cross-module fix and guard rules"
status: completed
priority: P1
effort: "1d"
dependencies: []
---

# Phase 1: Baseline, cross-module fix and guard rules

## Goal

Record the green baseline, remove the one live cross-module import, and put the boundary rules that the tree already
satisfies under test with a fixtures meta-test, so every later phase is guarded from its first commit.

## Context

- Review evidence: `sample-tenant/modules/customer/src/adapter/java/dev/ngb/sampletenant/customer/adapter/identity/CustomerAccountListener.java`
  imports `identity.application.port.AccountListener` and `identity.domain.User`. Every module jar carries all layers
  (`tasks.jar { from(layers...) }` in `build-logic/.../layered-module-conventions.gradle.kts`), so the classpath does not stop it.
- Reference: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/architecture/{ArchitectureRules,ArchitectureTest,ArchitectureMetaTest}.java`
  and `billing/testing/arch-fixtures/`. Decision D3: copy the generic rules, drop the Billing-specific ones.

## Files

- Create: `sample-tenant/testing/arch-fixtures/build.gradle.kts` and fixture classes under
  `sample-tenant/testing/arch-fixtures/src/main/java/archfixtures/...` (one violating class per rule, a `sharedcore` package for the core rules)
- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/architecture/ArchitectureRules.java`, `ArchitectureMetaTest.java`
- Modify: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/architecture/ArchitectureTest.java` (run the named rules)
- Modify: `sample-tenant/settings.gradle.kts` (include `testing:arch-fixtures`), `sample-tenant/apps/app-boot/build.gradle.kts` (test dependency on the fixtures)
- Create: `sample-tenant/modules/identity/src/api/java/dev/ngb/sampletenant/identity/api/AccountListener.java` and `RegisteredAccount.java`
- Delete: `sample-tenant/modules/identity/src/application/java/dev/ngb/sampletenant/identity/application/port/AccountListener.java`
- Modify: `RegisterUserUseCase.java`, `ManageUsersUseCase.java`, `CustomerAccountListener.java`, `IdentityConfiguration.java`, `CustomerConfiguration.java`, and the identity and customer tests that use `Fakes`

## Tasks and steps

1. Baseline: `cd sample-tenant && ./gradlew checkNoBillingDependency build`; record the passing test count in the commit message of step 2 and the current `git rev-parse HEAD` in the plan's Validation Log (phase 9 uses it to prove `contracts/`, `billing/` and `core/` are unchanged).
2. Move the listener contract into `identity.api`: `AccountListener` keeps both callbacks, `registered(RegisteredAccount)` and
   `default void deactivated(RegisteredAccount)`, with `record RegisteredAccount(UUID userId, String displayName, String email)`.
   `RegisterUserUseCase` (calls `registered`) and `ManageUsersUseCase` (calls `deactivated`) build the record from the `User`.
   `CustomerAccountListener` implements the api interface and no longer imports `identity.application` or `identity.domain`.
   Behaviour is identical: it still calls `RegisterCustomerUseCase.register` then `ManageWalletSettingUseCase.ensureDefaultWallet`.
3. Create the sample's `ArchitectureRules` (root-parameterised, `all(root)` and `core(core)`), starting from Billing's and keeping only the rules the
   tree already satisfies: `layer-direction`, `inner-layer-purity` (JDK, project, `dev.ngb.core`), `no-cross-module-import` (a module may use another only through `..api..`;
   context = first package under the root except `shared` and `boot`), `api-exposes-no-internals`, `no-jpa`,
   `shared-core-is-independent`, `datasource-and-transactions-only-in-core-spring` (permits `dev.ngb.core.spring..` and `dev.ngb.sampletenant.boot..`),
   plus `core-is-self-contained` and `core-spring-is-self-contained`. Keep the existing three rules' intent.
4. Create the fixtures module and `ArchitectureMetaTest`: for each rule a violating fixture class must appear in the failure report, and the rule set must equal the expected id list
   (a rule added without a fixture fails the test).
5. If a rule fails on production code for a reason other than step 2, fix the code when it is a real leak (for example an `api` type naming a domain type); if the fix would change behaviour or a contract, stop and ask.
6. Commit separately: the listener move; then the rules and fixtures.

## Verification

- `cd sample-tenant && ./gradlew checkNoBillingDependency build` is green; test count is at least the baseline.
- `grep -rn 'identity\.\(application\|domain\)' sample-tenant/modules/customer/src` returns nothing.
- `./gradlew :apps:app-boot:test --tests '*architecture*'` passes, and temporarily reverting step 2 makes `no-cross-module-import` fail naming `CustomerAccountListener`.

## Risks

- The new `api` types change `Fakes` in identity and customer tests; update them in the same commit.
- `api-exposes-no-internals` may flag `billing.api.model` or identity api types; handle per step 5.

## Todo

- [x] Baseline recorded
- [x] Listener contract in `identity.api`, customer adapter uses it
- [x] `ArchitectureRules`, fixtures, meta-test
- [x] Verification passes
