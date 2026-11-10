---
phase: 5
title: "Money: wallet, ledger, reservation, charge"
status: completed
priority: P1
effort: "7d"
dependencies: [4]
---

# Phase 5: Money: wallet, ledger, reservation, charge

## Goal

Build the `money` module so that Wallet, append-only Ledger, Reservation, Charge, Prepaid and Postpaid rules commit with their outbox rows in one transaction and are idempotent.

## Context

Source: [wallet-ledger-and-charging.md](../../docs/wallet-ledger-and-charging.md), `plans/reports/advise-261109-2309-ledger-append-only.md`. This is the phase that proves the architecture. It must not call any other module except `customer.api` for existence checks.

## Files to Create / Modify

- Create: `billing/money/` (domain: `Wallet`, `LedgerEntry`, `Reservation`, `Charge`, `ChargeService`; application: use cases and ports; adapter: persistence, REST, expiry scheduler, rebuild job; public `api` package: `ReserveBudget`, `ChargeCommand`, `CreditWallet`, `DebitWallet`)
- Create: Flyway `V5__money.sql` (wallets, ledger_entries, reservations, charges, triggers, grants)
- Create: `billing/money/src/test/` pure JUnit domain tests and `billing/app-boot/src/test/.../money/` Testcontainers tests

## Tasks & Steps

- [x] 1. Wallet: id, tenant, customer, immutable currency, mode (Prepaid or Postpaid), balance, held amount, credit limit and outstanding balance for Postpaid. Available balance is balance minus held. One wallet per (customer, currency).
- [x] 2. Ledger entry: id, tenant, wallet, currency, per-wallet `seq`, type (credit, debit, reversal), amount, link to reservation, charge, payment or original entry, actor, reason, idempotency key, created_at. Constraints: `unique (wallet_id, seq)`, `unique (tenant_id, idempotency_key)`, composite foreign key `(tenant_id, wallet_id, currency)` to wallets, amounts positive.
- [x] 3. Append-only enforcement: the application role gets INSERT and SELECT only on `ledger_entries`; a trigger raises on UPDATE and DELETE. A test attempts both as the application role and as the owner.
- [x] 4. `seq` is assigned while holding the wallet row lock (`select ... for update`), so replay order is deterministic. The balance projection updates in the same transaction.
- [x] 5. Reservation aggregate: check available balance (or Postpaid headroom), hold budget on the Reservation and the wallet's held amount, no ledger posting at hold time. Capture or charge from a reservation posts the ledger and releases any remainder; release and expiry restore availability. Idempotent create avoids duplicate holds.
- [x] 6. Charge: from usage input, from a reservation, standalone. Validates currency, amount and wallet mode, writes the ledger, records status and history. Idempotent by key.
- [x] 7. Prepaid: credits come from top-ups (phase 8) through `money.api`; charges fail with `insufficient_funds` when available balance is too low. Postpaid: debit is allowed within credit limit; outstanding balance is recorded; a debt-payment credit reduces it.
- [x] 8. Reversal use case: requires a Permission, actor and reason; writes a reversal entry linked to the original on the same wallet and currency, then an optional replacement entry; never touches the original.
- [x] 9. Reservation expiry job: scan with `for update skip locked`, release expired holds, publish `reservation.expired`. Safe to run on several instances.
- [x] 10. Ledger rebuild job: recompute each wallet's balance from entries, compare to the projection, publish a metric and an audit record on mismatch; it never rewrites the projection by itself.
- [x] 11. Events through the outbox in the same transaction: `reservation.created`, `reservation.released`, `reservation.expired`, `charge.succeeded`, `charge.failed`, wallet credit and debit events.
- [x] 12. REST for Tenant Systems: reserve, release, capture, charge, get wallet and ledger. Admin REST: view wallet, ledger, reverse an entry. All Idempotency-Key aware.
- [x] 13. Domain unit tests run with plain JUnit and no Spring context; add property-style tests for the Money invariants (no negative available balance for Prepaid, held never exceeds balance, currency mismatch rejected).

## Notes

- The migration is `V6__money.sql`. Ledger amounts are signed (credit positive, debit negative, reversal the negation) so a balance is `SUM(amount)`, as the ledger advisory report defines; the constraint "amounts positive" in task 2 was read as "non-zero and consistent with the entry type", enforced by checks.
- Every flow locks the wallet row first. The expiry job takes the lock with `skip locked` on the wallet (not on the reservation) so it can never deadlock with a capture and several instances can run together; a busy wallet is retried on the next run.
- A charge the wallet cannot pay is recorded as a FAILED charge (reason `insufficient_funds`, event `charge.failed`) rather than thrown; a reservation or debit that cannot be covered throws `InsufficientFundsException` (HTTP 402).
- Added permissions: `wallet:read`, `wallet:manage`, `reservation:manage`, `charge:create` (grantable to Tenant Systems) and `ledger:reverse` (administrators only). Default roles now sync to the catalog on start, so existing tenants get new permissions.
- The ledger repository port has no update or delete method, enforced by an ArchUnit rule (the rule set now has 10 rules; each has a fixture in the meta-test). A guard test fails if the money use-case rule ever matches no class.
- Scheduled jobs (`billing.jobs.enabled`) run per tenant scope; the ledger verify job counts `billing.ledger.mismatch` and audits, never repairs. `/actuator/health` is reachable without a tenant hostname.
- The outbox relay (phase 6) must read across tenants; it needs its own policy or role, not provided here.

## Verification

- Testcontainers: wallet, ledger entry and outbox row commit together and, with a failure injected after the outbox write, all three roll back (counts equal zero).
- Same idempotency key twice produces one ledger entry and equal responses; concurrent duplicates (parallel test) still produce one entry.
- Concurrent charges against one wallet never overdraw a Prepaid wallet and keep `seq` gap-free per wallet in the test sample.
- UPDATE and DELETE on `ledger_entries` fail for the application role and the owner.
- Rebuild job reports zero mismatches after a randomized sequence of operations, and reports a mismatch when the projection is tampered with in the test.
- Architecture rule: every use case under `money..application..` depends on `UnitOfWork`.

## Risks and rollback

- Wallet row locking serializes operations on a hot wallet. Accepted: one wallet's operations are naturally serial; measure in phase 14.
- A reversal of a charge that already produced an invoice is a cross-context concern; it publishes an event and invoicing reacts in phase 9, it is never edited here.
