# Wallet, Ledger and Charging

## Wallet and Ledger

- Wallets are managed per Tenant and Customer.
- A wallet holds exactly one currency. The currency is part of the wallet's identity and never changes, so a Customer that needs several currencies has several wallets. Cross-currency and inter-wallet transfers are not supported.
- Every balance change is recorded in the Ledger.
- Supported operations: Credit, Debit, Hold and Release.
- Consistency and auditability are guaranteed. The Ledger is the audit source for all financial transactions.
- Ledger history is never edited directly.

## Ledger model

- The Ledger is append-only: entries are only inserted. No use case, API or adapter updates or deletes an entry. The application's database role holds INSERT and SELECT only, and a trigger rejects UPDATE and DELETE.
- Entries are single-entry per wallet: each entry belongs to exactly one wallet, and the model is not double-entry bookkeeping. Every entry carries its currency, and a composite foreign key on the wallet and its currency makes a cross-currency entry impossible to insert.
- A balance is derived from entries. The stored balance is a projection updated in the same transaction and checked by a rebuild job; the projection is never the source of truth, and available balance is the balance minus held amounts.
- Corrections are reversal entries linked to the original entry, followed by a replacement entry when the correct amount differs. A reversal records the actor and the reason and targets the same wallet and currency.
- Entries are ordered by a monotonic sequence, not by timestamp, so audit replay is deterministic.
- Idempotency is enforced by a database unique constraint on the entry's idempotency key; an application-level check is only an optimization.

Rejected alternatives: mutating entries in place, a mutable balance row as the source of truth, soft-delete or void flags instead of reversal, and double-entry bookkeeping at this stage. The first three break the audit trail; double-entry has no two-sided account requirement yet and remains a seam to open later.

## Reservation

Lets the Tenant System hold a budget before executing a Resource.

- Checks the balance, or the Postpaid limit.
- Holds budget before the Resource is executed.
- A hold is not a ledger posting: the reserved budget lives on the Reservation, and the Ledger is written only when the Reservation is captured or charged.
- Supports Capture or Charge from a Reservation.
- Releases any remaining budget.
- Supports Expiration and the handling of expired Reservations.
- Guarantees Idempotency and avoids duplicate holds.

## Charge

Calculates and records the actual cost incurred.

- Charge from Usage.
- Charge from a Reservation.
- Standalone Charge.
- Validates the transaction.
- Updates Wallet and Ledger.
- Records Charge status and history.

## Prepaid and Postpaid

**Prepaid**
- The Customer tops up the Wallet.
- Billing checks the balance before a Reservation or Charge.
- Cost is deducted from the available balance.

**Postpaid**
- The Customer uses the service first and pays later.
- Billing manages the Credit Limit and the Outstanding Balance.
- Incurred debt is recorded.
- Supports aggregation cycles and Invoice generation, see [invoicing-and-subscriptions.md](invoicing-and-subscriptions.md).
- Supports paying off the debt.

## Cross-cutting guarantees

- Idempotency: nothing is recorded twice on retry. This covers Usage, Reservation, Charge, Payment and Refund.
- Reservation holds and releases stay consistent with the Wallet and Ledger in the same transaction.
- Events that report these changes are published through the transactional outbox, see [security-and-reliability.md](security-and-reliability.md).

## Related

Top-ups and refunds change the Wallet through verified payment results only: [payments-and-stripe.md](payments-and-stripe.md).
