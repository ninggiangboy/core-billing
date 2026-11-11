---
title: Idempotency unified on the created row
date: 2026-11-11
summary: "One keyed-create pattern everywhere, the generic store and table removed, racing duplicates replay, and 201/200/409 documented"
---

# Idempotency unified on the created row

## What changed

- `Fingerprint.requireSameRequest` and `Causes.violates` centralise the replay decision and the named-constraint check.
- The customer key and request hash moved onto `customers` (V18 backfill, V19 drops the table). `CreateCustomerUseCase`
  inserts first and replays 200; `IdempotencyStore`, `StoredResponse` and `JooqIdempotencyStore` are gone.
- Top-up, invoice payment and subscription create are insert-first with `on conflict do nothing`; refund reads its key
  after the payment lock. Racing duplicates replay the winner instead of answering a generic 409.
- Charge, reservation and ledger repositories map only the idempotency-key constraint to
  `IdempotencyConflictException`; a ledger sequence clash now names the skipped wallet lock.
- Charge, capture, reservation, pricing reservation and customer create answer 200 on replay; a failed charge replays
  as 402. The OpenAPI contract was regenerated.
- `docs/concurrency-and-locking.md` records the lock-first / insert-first pattern; the architecture and payment docs
  no longer mention the removed store.

## Verification

- `./gradlew test` is green, including ArchUnit, `JooqSchemaTest` and `OpenApiContractTest`.
- A migration upgrade test migrates to V17, seeds an old key, migrates on, and proves the same customer still replays.
- Eight-way same-key tests cover customer, top-up, invoice payment, subscription, refund and reservation. Cross-wallet
  concurrent charge and reservation tests get one success and one idempotency conflict.
