---
phase: 5
title: "Money: conflict mapping and replay status"
status: pending
priority: P2
effort: "3h"
dependencies: [1]
---

# Phase 5: Money: conflict mapping and replay status

## Goal

The lock-first money flows keep their behaviour, report a reused key as an idempotency conflict even across
wallets, and answer 200 on replay.

## Context

- `ChargeUseCase` and `ReserveBudgetUseCase` lock the wallet before reading the key, which is correct under READ
  COMMITTED (`MoneyIntegrationTest.concurrentDuplicatesProduceOneEntry`). The wallet is part of both hashes.
- A same key on two different wallets, sent concurrently, takes two different locks. The loser hits the key
  constraint in `add`, which today throws a generic `ConflictException`.
- `JooqLedgerRepository.append` maps both `unique (wallet_id, seq)` and the key constraint to one `ConflictException`.
- `ChargeController` (charge and capture), `ReservationController.reserve` and `RatingController.reserveEstimate`
  always answer 201. `ChargeResult`, `ReservationResult` and `ReserveFromEstimateUseCase.Result.reservation()`
  already carry `replayed`.

## Files to Create / Modify

- Modify: `billing/contexts/money/src/adapter/.../charge/persistence/JooqChargeRepository.java`
- Modify: `billing/contexts/money/src/adapter/.../reservation/persistence/JooqReservationRepository.java`
- Modify: `billing/contexts/money/src/adapter/.../ledger/persistence/JooqLedgerRepository.java`
- Modify: `billing/contexts/money/src/application/.../charge/command/ChargeUseCase.java` (javadoc only)
- Modify: `billing/contexts/money/src/application/.../reservation/command/ReserveBudgetUseCase.java` (javadoc only)
- Modify: `billing/contexts/money/src/adapter/.../charge/web/ChargeController.java`
- Modify: `billing/contexts/money/src/adapter/.../reservation/web/ReservationController.java`
- Modify: `billing/contexts/pricing/src/adapter/.../rating/web/RatingController.java`
- Modify tests: `MoneyIntegrationTest`, `MoneyHttpIntegrationTest`, `PricingUsageIntegrationTest` or the pricing HTTP test that covers `/pricing/reservations`
- Modify: `contracts/openapi/billing.yaml` (regenerated)

## Tasks & Steps

- [ ] 1. In the charge and reservation repositories, map only `Causes.violates(e, <TABLE>_TENANT_ID_IDEMPOTENCY_KEY_KEY.getName())`
      to `IdempotencyConflictException(scope, key)`. Leave every other duplicate as it is.
- [ ] 2. In `JooqLedgerRepository.append`, map the key constraint to `IdempotencyConflictException("ledger", key)`.
      A `(wallet_id, seq)` clash means the wallet lock was skipped, so it becomes an `IllegalStateException` that names it.
- [ ] 3. Javadoc on `ChargeUseCase` and `ReserveBudgetUseCase`: the key is read after the wallet lock, which is what makes
      the read race-free under READ COMMITTED; do not move it above the lock.
- [ ] 4. HTTP: charge and capture answer 200 when `replayed` and the charge succeeded, 402 for a failed charge
      whether replayed or not, and 201 otherwise. Reservation and pricing reservation answer 200 when replayed and 201
      otherwise. Update each javadoc.
- [ ] 5. Tests:
       - 8 parallel reservations with one key give one reservation and one hold.
       - The same key on two wallets, concurrently, for a charge and for a reservation: one success and one 409 with the
         idempotency-conflict message.
       - An HTTP replay of a charge, a capture, a reservation and a pricing reservation answers 200 with the same id.
         A failed charge replays as 402.
- [ ] 6. Regenerate the API contract: `./gradlew :apps:app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true`.

## Verification

- `./gradlew :contexts:money:test :contexts:pricing:test`
- `./gradlew :apps:app-boot:test --tests '*Money*' --tests '*Pricing*' --tests '*OpenApiContractTest' --tests '*DeadlockStressIntegrationTest'`

## Risks and rollback

A client that treats only 201 as success on charge or reservation must also accept 200. `sample-tenant/` and `web/`
do not check these statuses (verified with grep). Revert the commit and the regenerated contract to roll back.
