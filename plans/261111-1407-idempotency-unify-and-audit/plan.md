---
title: "Unify idempotency on the created row and make every keyed create race-free"
description: "One idempotency pattern (key and request hash on the row a request creates), the generic IdempotencyStore removed, every Idempotency-Key flow race-free, 201 for new and 200 for replay, and 409 IdempotencyConflict for a reused key."
status: pending
priority: P1
effort: 14h
branch: dev
tags: [backend, database, api, refactor, reliability]
blockedBy: []
blocks: []
created: 2026-11-11
---

# Unify idempotency on the created row and make every keyed create race-free

## Outcome, constraints, non-goals, acceptance

- **Outcome.** Every `Idempotency-Key` flow uses one pattern. The key and the request hash live on the
  row the request creates, guarded by `unique (tenant_id, idempotency_key)`. Two requests with the same
  key, sent at the same time or one after another, produce one row. Every caller gets the same resource
  and the right status.
- **The pattern** (written into `docs/concurrency-and-locking.md` in phase 6):
  1. **Lock-first.** Lock the parent row that every request with this key must lock, then read the key.
     This is valid only when the parent is part of the request hash, so a same-key request with another
     parent is by definition a different request. Used by charge, reservation, ledger postings and refund.
  2. **Insert-first.** `addIfAbsent` with `on conflict (tenant_id, idempotency_key) do nothing`. When it
     inserts nothing, read the row and decide. Used by usage, customer, top-up, invoice payment and
     subscription.
  3. **Decide.** Same hash: return the row's current state, `replayed = true`. Different hash:
     `IdempotencyConflictException` (409). A violation of the key constraint that escapes a lock-first
     flow is also `IdempotencyConflictException`, never a generic `ConflictException` or
     `DataIntegrityViolationException`.
  4. **HTTP.** 201 for a new resource, 200 for a replay. A recorded outcome status (402 for a failed
     charge) is replayed unchanged.
- **Constraints.**
  - READ COMMITTED, as today. Lock order payment, then refund, then wallet. jOOQ for all persistence.
  - Schema changes only through Flyway. Regenerate `libs/jooq-schema` with `-Djooq.update=true`.
  - Back up the database before running the new migrations on any environment that holds data.
  - No plan or phase identifiers in code, tests, migrations or commits.
- **Non-goals.** Changing the isolation level. Advisory locks. A generic idempotency framework or
  middleware. Data-keyed idempotency (invoice per charge, usage ratings, subscription renewal, Stripe
  events, webhook deliveries), which already handles races. Adding `replayed` fields to response bodies
  that do not have one.
- **Acceptance.**
  - `IdempotencyStore`, `StoredResponse`, `JooqIdempotencyStore` and the `idempotency_keys` table are gone.
    `grep -rn "IdempotencyStore\|idempotency_keys" billing contracts docs` finds only migration history.
  - Existing customers keep their keys: replaying a key stored before the migration returns the same
    customer (migration upgrade test).
  - Each keyed create has a test that fires 8 parallel same-key requests and asserts one row, one id,
    one 201 and seven 200s at the HTTP level, or `replayed = false` once and `true` seven times at the
    use-case level.
  - A same-key request with a different body returns 409 with the idempotency-conflict message on every
    keyed endpoint, including the concurrent cross-parent case for charge and reservation.
  - `./gradlew test` is green, including ArchUnit, `JooqSchemaTest` and `OpenApiContractTest`.
    `contracts/openapi/billing.yaml` documents a 200 replay response on the five endpoints that gain one.

## Inventory of keyed flows

| Flow | Today | Action | Phase |
|---|---|---|---|
| `CreateCustomerUseCase` | Generic store, insert-first, race-safe | Move the key to `customers`, remove the store, 200 on replay | 2 |
| `CreateTopUpUseCase` | Check, then insert, no lock: a racing duplicate gets a generic 409 | Insert-first | 3 |
| `CreateInvoicePaymentUseCase` | Same as top-up | Insert-first | 3 |
| `RequestRefundUseCase` | Checks before the payment lock: 409 or a misleading "at most 0" | Check after the payment lock | 3 |
| `CreateSubscriptionUseCase` | Check, then insert: 409 "already used" | Insert-first | 4 |
| `ChargeUseCase` (charge, capture) | Lock-first, correct; cross-wallet race gives a generic conflict; 201 on replay | Map the key constraint, 200 on replay | 5 |
| `ReserveBudgetUseCase` (+ pricing reserve) | Lock-first, correct; same gaps | Map the key constraint, 200 on replay | 5 |
| `LedgerPosting` (credit, debit, reverse) | Lock-first, correct; `append` maps both seq and key duplicates to one conflict | Map the key constraint | 5 |
| `RecordUsageUseCase` | Insert-first, correct | None; the reference implementation | — |
| Internal keys (`PaymentSettlement`, `ApplyPaymentUseCase`) | Go through `CreditWallet` / `DebitWallet` under the wallet lock | None; verified | — |

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 1 | [Shared replay and constraint helpers](./phase-01-shared-helpers.md) | 1h | completed |
| 2 | [Customer key on the row, store removed](./phase-02-customer-row-key.md) | 4h | pending |
| 3 | [Payment: top-up, invoice payment, refund](./phase-03-payment-flows.md) | 3h | pending |
| 4 | [Subscription create](./phase-04-subscription-create.md) | 1.5h | pending |
| 5 | [Money: conflict mapping and replay status](./phase-05-money-and-http-status.md) | 3h | pending |
| 6 | [Docs and full verification](./phase-06-docs-and-verification.md) | 1.5h | pending |

Phases run in order. Phase 1 blocks all others. Phases 3, 4 and 5 touch disjoint contexts, but all
three regenerate `contracts/openapi/billing.yaml`, so they stay sequential. Each phase runs its
narrowest tests first, then `./gradlew :apps:app-boot:test`, and is committed on its own.
