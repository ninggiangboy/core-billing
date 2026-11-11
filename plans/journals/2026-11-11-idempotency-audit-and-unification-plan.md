---
title: Idempotency audit and unification plan
date: 2026-11-11
summary: "Audited every Idempotency-Key flow; planned one row-level pattern, store removal and 201/200 replay status"
---

# Idempotency audit and unification plan

## What happened

The question was whether `findByIdempotencyKey` followed by an insert is safe under concurrency. Answer: on its own it is not, but money never moves twice, because every table has `unique (tenant_id, idempotency_key)` and the transaction rolls back. The gaps are in the responses.

- `ChargeUseCase`, `ReserveBudgetUseCase` and `LedgerPosting` lock the wallet before reading the key. Under READ COMMITTED that is race-free, and `MoneyIntegrationTest.concurrentDuplicatesProduceOneEntry` proves it.
- `RecordUsageUseCase` inserts first (`on conflict do nothing`) and is correct.
- `CreateTopUpUseCase`, `CreateInvoicePaymentUseCase` and `CreateSubscriptionUseCase` read and then insert with no lock. A racing duplicate gets a generic 409 instead of a replay.
- `RequestRefundUseCase` reads the key before it locks the payment. A racing duplicate gets a 409 or a misleading "at most 0 can still be refunded".
- `IdempotencyStore` / `idempotency_keys` was built in the tenancy phase before any business table existed. Only `CreateCustomerUseCase` uses it. It is insert-first and race-safe. An earlier claim in this session that it was unsafe was wrong and was corrected.
- Replay status is inconsistent: charge, capture, reservation, pricing reservation and customer always answer 201, while the other flows answer 200.

## Decision

- One pattern: the key and request hash live on the created row, and the flow is either lock-first (the parent is in the hash) or insert-first.
- 201 for new, 200 for replay, everywhere.
- Move the customer key onto `customers` with a backfill, then drop `idempotency_keys` in the same plan. The backfill must lift `force row level security` inside the migration, or it updates nothing.

Plan: `plans/261111-1407-idempotency-unify-and-audit/plan.md` (6 phases, about 14h).

## Next steps

- `/ak-cook plans/261111-1407-idempotency-unify-and-audit/plan.md`
- Back up any database that holds data before V18/V19 run.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
