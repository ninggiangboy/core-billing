---
phase: 3
title: "Layout: pricing, payment, money"
status: pending
priority: P1
effort: "1.5d"
dependencies: [2]
---

# Phase 3: Layout: pricing, payment, money

## Goal

Apply the layout recorded in phase 2 to the three modules that carry the money path and the most files.

## Context

Pure move, as in phase 2. Suggested feature groups (confirm against the files before moving; record the final mapping in the phase notes):

- `money`: `wallet`, `ledger`, `reservation`, `charge`, plus `event` for `MoneyEvents` in `domain`. Application split: `wallet/command` (`OpenWalletUseCase`, `CreditWalletUseCase`, `DebitWalletUseCase`), `wallet/query` (`WalletQueries`, `WalletLookupService`), `ledger/command` (`ReverseEntryUseCase`, `VerifyLedgerUseCase`), `reservation/command` (reserve, release, expire), `charge/command` (`ChargeUseCase`), each feature's `port/` for its repository. `LedgerPosting` and `RequestFingerprint` go to the feature that uses them most.
- `payment`: `payment` (Payment, attempt, id, status), `refund`, `stripe` (configuration, environment, events, routes, webhook receive and reconcile use cases), `incident`. `PaymentProvider`, `ProviderAccess`, `SecretCipher` are ports; `ProviderRejectedException`, `ProviderUnavailableException`, `InvalidSignatureException` live with the feature that throws them.
- `pricing`: group by aggregate (plan, metric, rating); list the 25 domain files first.

`money.api` (23 types) splits into `wallet`, `reservation`, `charge`, `ledger`; keep each command, result and id next to its feature, and keep `InsufficientFundsException` with the wallet operations that throw it.

## Files to Create / Modify

- Move with `git mv`, then fix `package` and `import` lines, for every file under `billing/pricing`, `billing/payment` and `billing/money`, all source sets including `src/test`.
- Modify imports in `billing/app-boot` (`PricingConfiguration`, `PaymentConfiguration`, `MoneyConfiguration`, jobs, tests) and in other modules that use `money.api`, `pricing.api` or `payment.api`.
- Modify each module's `adapter/.../api/package-info.java` following the pattern recorded in phase 2.

## Tasks & Steps

- [ ] 1. Write the file-to-package mapping for `pricing` in the phase notes.
- [ ] 2. Move `pricing` (smallest), compile, fix imports, run `./gradlew :pricing:test`.
- [ ] 3. Move `payment`, compile, fix imports, run `./gradlew :payment:test`.
- [ ] 4. Move `money`, compile, fix the roughly 180 import lines that reference `money.api`, run `./gradlew :money:test`.
- [ ] 5. Add the three modules to the layout set, run the architecture tests.
- [ ] 6. One commit per module: `refactor(<module>): group packages by feature and role`.

## Verification

- `cd billing && ./gradlew build` is green; the Testcontainers money, payment and pricing integration tests pass; test count equals the baseline.
- `ModulithVerificationTest` passes with the split `money.api`.
- Rename-only diff, as in phase 2.

## Risks and rollback

- Package-private helpers (`Rows`, row records) become unreachable if a repository and its helper land in different packages. Keep a helper with the repository that uses it, or duplicate nothing and make the shared helper a package-private class in each feature that needs it only if it is tiny. Do not widen visibility to `public` just to compile.
- Rollback is reverting the module's move commit.
