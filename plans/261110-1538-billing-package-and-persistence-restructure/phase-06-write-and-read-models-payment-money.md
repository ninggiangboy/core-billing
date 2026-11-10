---
phase: 6
title: "Write and read models: payment and money"
status: pending
priority: P1
effort: "4d"
dependencies: [5]
---

# Phase 6: Write and read models: payment and money

## Goal

Apply the write and read conventions from phase 5 to the modules that hold Stripe state and the ledger, without weakening any transaction, lock, guard or append-only guarantee.

## Context

Highest-risk SQL in the project:

- `money`: `JdbcWalletRepository` (`for update`, `for update skip locked`, partial `update` of balance, held and last sequence), `JdbcLedgerRepository` (insert only), `JdbcReservationRepository`, `JdbcChargeRepository` (idempotency key lookups), `JdbcLedgerVerifier` (verification SQL).
- `payment`: `JdbcPaymentRepository`, `JdbcRefundRepository`, `JdbcIncidentRepository`, `JdbcStripeConfigurationRepository` (encrypted secrets), `JdbcStripeEventRepository` (inbox with monotonic status transitions), `JdbcStripeRoutes`.

**Write side.** Rows and conventions as in phase 5. Wallet `update` keeps its partial column list, because the wallet row is locked before it is written and a whole-row update would also rewrite immutable columns. Ledger entries are only inserted.

**Read side.** `WalletQueries` becomes the query class over `WalletReadRepository`, `LedgerReadRepository` and so on, returning views such as a wallet view and a ledger entry page (`afterSequence`, `limit` and the `MAX_PAGE` cap stay as they are). `WalletLookupService`, which implements the read-only `WalletLookup` that `pricing` uses through `money.api`, moves to the query side and reads through the read port, not through the wallet repository. Payment status and list queries move the same way. A read that decides a write (idempotency replays, wallet lock) stays on the write port.

`JdbcLedgerVerifier` is a verification job used by `VerifyLedgerUseCase`, not a read model; it stays on the command side unless it maps into a domain type.

## Files to Create / Modify

- Create: `*Row` records, `*View` records, `*ReadRepository` ports and `Jdbc*ReadRepository` classes in `billing/money` and `billing/payment`, in the feature packages created in phase 3.
- Modify: the repositories above, `WalletQueries`, `WalletLookupService`, `PaymentQueries`, the money and payment controllers' GET handlers, and `MoneyConfiguration` and `PaymentConfiguration` in `billing/app-boot`.

## Tasks & Steps

- [ ] 1. Read every repository in both modules. List each guarded or partial `update` and each locking read; each keeps its exact SQL.
- [ ] 2. Convert the `payment` write side one repository per commit, running `./gradlew :payment:test` and the payment HTTP and Stripe webhook integration tests after each.
- [ ] 3. Convert the `money` write side in this order: `charge`, `reservation`, `wallet`, `ledger`, running `./gradlew :money:test` and the money integration tests (concurrency, atomicity, idempotency, append-only) after each.
- [ ] 4. Add the read side of `payment`, then of `money`, one commit per feature. Keep the response bodies of GET endpoints identical.
- [ ] 5. Confirm that the ledger repository only calls `insert` for entries and that `ledger-repository-is-append-only` still passes.
- [ ] 6. Add `payment` and `money` to the persistence set and to the read and write set of the architecture rules.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `MoneyIntegrationTest`, `MoneyHttpIntegrationTest`, `PaymentIntegrationTest`, `PaymentHttpIntegrationTest`, `StripeWebhookHttpIntegrationTest` and `PricingUsageHttpIntegrationTest` (it uses `WalletLookup`) pass with no test edited to make them pass.
- `git grep -n 'RowMapper<' -- 'billing/money/src/adapter' 'billing/payment/src/adapter'` returns no mapper that yields a domain type.
- No class under `..query..` depends on a write repository, and `WalletLookupService` reads only through a read port.
- `billing/app-boot/src/main/resources/db/migration/` is unchanged.

## Risks and rollback

- Replacing a guarded update with a whole-row update silently drops a compare-and-set; replacing a lock read with a plain read loses serialisation of concurrent wallet writes. Review both explicitly.
- A read served from a view must never be used to decide a write; the architecture rules forbid the dependency, and review checks the use cases that call `WalletLookup`.
- Rollback is reverting the repository or feature commit. If a conversion cannot keep a guard without a behaviour change, keep the guarded statement and stop to report it.
