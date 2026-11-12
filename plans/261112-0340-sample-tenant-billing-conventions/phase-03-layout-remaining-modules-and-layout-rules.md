---
phase: 3
title: "Layout: remaining modules and layout rules"
status: completed
priority: P1
effort: "1.5d"
dependencies: [2]
---

# Phase 3: Layout: remaining modules and layout rules

## Goal

Move `customer`, `identity`, `resource` and `billing-client` into the layout, split the one class that mixes commands and reads, add Spring Modulith verification, and turn on the layout rules so the build refuses a return to the flat tree.

## Proposed mapping (confirm while scouting)

| Module | Features | Notes |
|---|---|---|
| customer | `customer`, `wallet` | `Customer`, `CustomerStatus` to `domain.customer`; `CustomerWalletSetting`, `WalletMode` to `domain.wallet`. `RegisterCustomerUseCase` to `customer.command`; `ManageWalletSettingUseCase` to `wallet.command`. `CustomerMapping` to `customer.service`. `CustomerQueries` to `customer.query`. `CustomerDirectory` (api) to `api.lookup`; its implementer becomes `CustomerDirectoryService` in `customer.service` (phase 7 moves its reads to a read repository). |
| customer split | | `CustomerBillingViews` mixes reads (`wallets`, `invoices`, `payments`) with commands (`openSession`, `topUp`). Split into `OpenBillingSessionUseCase` and `TopUpUseCase` in `billing.command`, and `CustomerBillingQueries` in `billing.query`. Controllers and `CustomerConfiguration` change accordingly; `CustomerBillingViewsTest` is split with it, assertions unchanged. |
| identity | `user`, `session`, `account` | `User`, `UserStatus` to `domain.user`; `UserSession` to `domain.session`. Use cases to `*.command`. `SessionService` and `Tokens` to `session.service`. Ports to `.port`. `BCryptPasswordHasher` to `adapter.user.security`. api: `AuthenticatedUser`, `Permission`, `RequiresPermission`, `Role` to `api.security`; `AccountListener`, `RegisteredAccount` to `api.account`. |
| resource | `order` | `Order`, `OrderStatus` to `domain.order`; `CreateOrderUseCase`, `ManageOrderUseCase` to `order.command`; `OrderExecutionService` to `order.service` (implements `ExecutionOrders`, which moves to `api.execution`); `OrderQueries` to `order.query`; ports `OrderRepository`, `OrderSubmission` to `.port`. |
| billing-client | `client` | api interfaces (`BillingAdmin` ... `BillingWebhooks`) to `billing.api.client`, models stay in `billing.api.model`; `HttpBillingClient`, `HttpBillingSettings` to `billing.adapter.client.http`. The module keeps its name (decision D2). |

## Files

- Modify/rename: all `.java` under `sample-tenant/modules/{customer,identity,resource,billing-client}/src/**` and their tests (`Fakes.java` mirrors packages)
- Modify: `sample-tenant/apps/app-boot/src/main/java/dev/ngb/sampletenant/boot/**` (imports in `config`, `security`, `web`, `bootstrap`, `jobs`)
- Create: `package-info.java` with `@NamedInterface` for each `api` sub-package, in the module's `adapter` source set (as Billing does under `src/adapter/java/.../api/`)
- Modify: `sample-tenant/build-logic/src/main/kotlin/layered-module-conventions.gradle.kts` (add the Modulith BOM and `spring-modulith-api` lines Billing has)
- Create: `sample-tenant/apps/app-boot/src/test/java/dev/ngb/sampletenant/architecture/ModulithVerificationTest.java`
- Modify: `ArchitectureRules.java`, `ArchitectureTest.java`, `ArchitectureMetaTest.java`, fixtures (add the layout rules)

## Tasks and steps

1. Scout the four modules; confirm the mapping; settle unclear roles with the same rule as phase 2.
2. Moves, one module per commit, order: customer (including the `CustomerBillingViews` split in its own commit before the move), billing-client, identity, resource. Fix `app-boot` imports in the same commit as each module.
3. Add Modulith: build-logic lines, `ModulithVerificationTest` (module well-formedness; documentation output as Billing does), `package-info` named interfaces. If Modulith cannot accept an `api` sub-package layout, stop and ask (Billing hit the same risk and resolved it with one named interface per used sub-package).
4. Enable the layout rules and their fixtures: `role-package-not-layer-root`, `use-cases-and-queries-in-command-or-query`, `ports-in-port-or-query`, `application-classes-in-a-role-package`, `command-package-holds-only-use-cases`, `port-package-holds-no-implementations`, `adapter-classes-in-feature-then-technology`, `api-implementations-are-services`, `web-does-not-depend-on-persistence`. Domain classes may not sit in the layer root either (`role-package-not-layer-root` covers domain and api).
5. Update the meta-test's expected rule list and add one fixture per new rule.

## Verification

- `cd sample-tenant && ./gradlew checkNoBillingDependency build` green; test count at least the phase 1 baseline (the `CustomerBillingViewsTest` split keeps its assertions).
- The nine layout rules pass on production code and each fails on its fixture (meta-test).
- `ModulithVerificationTest` passes.
- `find sample-tenant/modules -path '*/src/*/java/*' -name '*.java'` shows no class directly under a layer root package.

## Risks

- Splitting `CustomerBillingViews` is the only non-mechanical change; keep it behind the existing test and a separate commit.
- `api-implementations-are-services` forces `CustomerQueries implements CustomerDirectory` and `CatalogQueries implements CatalogLookup` to become `*Service` classes; they still read through the write port until phases 6 and 7, which is a temporary state, not an exemption in the rule.

## Todo

- [x] customer (with the split), billing-client, identity, resource moved
- [x] Modulith verification added
- [x] Layout rules and fixtures enabled
- [x] Build green
