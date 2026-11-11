---
phase: 6
title: "Docs and full verification"
status: pending
priority: P2
effort: "1.5h"
dependencies: [2, 3, 4, 5]
---

# Phase 6: Docs and full verification

## Goal

The idempotency pattern is written down where a new flow's author will look, no doc mentions the removed store,
and the whole suite is green.

## Files to Create / Modify

- Modify: `docs/concurrency-and-locking.md` (rule in "Adding a flow"; refund row in the lock-order table)
- Modify: `docs/architecture.md` (lines 123, 142, 145 and 147 mention idempotency in `platform` and the idempotency keys table)
- Modify: `docs/customers-and-tenant-mapping.md` (replay answers 200)
- Modify: `docs/wallet-ledger-and-charging.md` and `docs/payments-and-stripe.md` only where they state a replay status or conflict behaviour

## Tasks & Steps

- [ ] 1. Add the four-point pattern from `plan.md` to `docs/concurrency-and-locking.md`, with the lock-first condition
      (the parent is in the hash) and `RecordUsageUseCase` named as the insert-first reference.
- [ ] 2. Update the lock-order table row for refunds: the payment lock is taken before the key is read.
- [ ] 3. Remove the store and the `idempotency_keys` table from `docs/architecture.md`; keep `Fingerprint` as what
      `platform.api.idempotency` holds.
- [ ] 4. Check every doc claim against the code: `grep -rn -i "idempoten" docs`.
- [ ] 5. Run the full suite and confirm the acceptance list in `plan.md`.

## Verification

- `./gradlew test`
- `grep -rn "IdempotencyStore\|idempotency_keys" billing contracts docs` finds only `V2__platform_tables.sql` and `V19__drop_idempotency_keys.sql`.
- `git diff --stat contracts/` shows only the five endpoints' new 200 responses and the customer change.
