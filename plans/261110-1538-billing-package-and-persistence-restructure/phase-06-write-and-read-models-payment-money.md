---
phase: 6
title: "Write and read models: payment and money"
status: completed
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

- [x] 1. Read every repository in both modules. List each guarded or partial `update` and each locking read; each keeps its exact SQL.
- [x] 2. Convert the `payment` write side one repository per commit, running `./gradlew :payment:test` and the payment HTTP and Stripe webhook integration tests after each.
- [x] 3. Convert the `money` write side in this order: `charge`, `reservation`, `wallet`, `ledger`, running `./gradlew :money:test` and the money integration tests (concurrency, atomicity, idempotency, append-only) after each.
- [x] 4. Add the read side of `payment`, then of `money`, one commit per feature. Keep the response bodies of GET endpoints identical.
- [x] 5. Confirm that the ledger repository only calls `insert` for entries and that `ledger-repository-is-append-only` still passes.
- [x] 6. Add `payment` and `money` to the persistence set and to the read and write set of the architecture rules.

## Phase notes

### Persistence convention applied to both modules

- Plain inserts and whole-row updates go through `JdbcAggregateOperations` (`aggregates.insert(row)` / `aggregates.update(row)`). A simple by-id read goes through `aggregates.findById(id, Row.class)`.
- Only these stay as explicit `JdbcClient` SQL, because the database statement *is* the rule:
  - locking reads (`for update`, `for update skip locked`);
  - guarded updates (`recordAttempt` compares the attempt number and status; `resolve` requires `resolved_at is null`);
  - append-only inserts (ledger) and conflict-ignoring inserts (`usage_events`, `pricing_metrics`, `usage_ratings`, `stripe_events`);
  - the upsert that keeps one open incident;
  - a subselect insert (`payment_attempts`, `stripe_event_attempts` copying the tenant);
  - columns Spring Data cannot map directly (Postgres arrays, `jsonb`, partial selects with aliases).
- The domain owns the immutability rule: fields that never change are `final` and have no mutator. The adapters no longer carry a "partial on purpose / never change" rationale; a whole-row update writes the aggregate the domain exposes. Locking/guarded statements remain explicit so concurrency rules are not lost.

### money

- Write rows: `WalletRow`, `LedgerEntryRow`, `ReservationRow`, `ChargeRow` (`@Table`).
- Write side: wallet/reservation/charge use `aggregates.insert` and `aggregates.update`; ledger stays append-only with its explicit insert. The wallet `lockById`/`tryLockById`, `findByCustomer`, reservation idempotency lookup and charge idempotency lookup stay explicit; `findById` uses `aggregates.findById`.
- Read side: `WalletView` (available/outstanding derived), `LedgerEntryView`, `ReservationView`, `ChargeView`; ports in `wallet.query`, `ledger.query`, `reservation.query`, `charge.query`; adapters in `adapter/persistence/{wallet,ledger,reservation,charge}`.
- `WalletQueries` and `WalletLookupService` depend only on read ports. `WalletRepository` loses `listByCustomer`; `LedgerRepository` loses `page`.
- `JdbcLedgerVerifier` reads its mismatch projection through `query(Class)`; it is not a read model and stays on the command side.
- `Rows` is reduced to the duplicate-key classifier.

### payment

- Write rows: `PaymentRow`, `RefundRow`, `StripeConfigurationRow`, `StripeEventRow`, `PaymentAttemptRow`, `EventAttemptRow`, `StripeRouteRow` (`@Table`); `PaymentRow` carries the constant `purpose = 'TOP_UP'`.
- Write side: payments/refunds/configurations/events use `aggregates.insert`/`update`; the locks, the inbox `on conflict do nothing`, the incident upsert, the guarded attempt update and the attempt subselect stay explicit; `findById` uses `aggregates.findById`.
- Read side: `PaymentView`/`PaymentAttemptView`, `RefundView`, `StripeConfigurationView`, `StripeEventView`/`EventAttemptView`, `IncidentView`; ports and adapters for payment, refund, stripe and incident. The configuration view exposes `webhookSecretSet`, never the ciphertext.
- `PaymentQueries` composes payment/refund read ports; `RefundQueries`, `StripeConfigurationQueries`, `StripeEventQueries` and `IncidentQueries` own the GET paths. `StripeEventAdministration` keeps only retry/resolve; the retry endpoint reads the detail back through the query.
- Write ports lose `list`/`listByPayment`; `PaymentRepository` keeps `attempts` because a close decides from the last failed attempt (a read that decides a write).
- `PaymentView` also carries `checkoutSessionId` and `paymentIntentId` for internal reads; the HTTP response mapping still exposes neither.

### Result

- `./gradlew build` green; 551 tests, 0 failures (same as phase 1).
- No `RowMapper` remains in any converted adapter; no query class imports a write port; `git diff` over `db/migration` and `contracts/` is empty.
- `MoneyIntegrationTest`, `MoneyHttpIntegrationTest`, `PaymentIntegrationTest`, `PaymentHttpIntegrationTest`, `StripeWebhookHttpIntegrationTest` and `PricingUsageHttpIntegrationTest` pass.

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
