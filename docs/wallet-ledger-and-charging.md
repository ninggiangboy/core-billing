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

- Entry amounts are signed minor units: a credit is positive, a debit negative, and a reversal carries the negation of the entry it corrects, so a balance is the sum of its entries and an entry plus its reversal sum to zero. An entry can be reversed once, and a reversal cannot itself be reversed.
- Every flow locks the wallet row first and assigns the entry's sequence number while holding it, so sequences are gap-free and one wallet's operations are naturally serial. Idempotency is checked under that lock and backed by the unique constraints, so a concurrent duplicate waits and then finds the first result.
- A verify job recomputes each wallet's balance and sequence from its entries. It reports and audits a mismatch and never rewrites the projection; deciding which side is right is a person's job.

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

A capture charges the actual cost, which cannot exceed the held amount, and returns the unused remainder in the same step; capturing after the hold has expired is refused. Release and expiry return the whole hold. Expiry runs per Tenant and skips a wallet that is busy, to be picked up on the next run.

## Charge

Calculates and records the actual cost incurred.

- Charge from Usage.
- Charge from a Reservation.
- Standalone Charge.
- Validates the transaction.
- Updates Wallet and Ledger.
- Records Charge status and history.
- A Charge the wallet cannot pay is recorded as failed with the reason `insufficient_funds` and announced as `charge.failed`; it is an outcome, not an error. Repeating the request returns the same recorded failure. The REST API answers 402 for it, and for a reservation or debit that the wallet cannot cover.

## Prepaid and Postpaid

**Prepaid**
- The Customer tops up the Wallet.
- Billing checks the balance before a Reservation or Charge.
- Cost is deducted from the available balance.

**Postpaid**
- The Customer uses the service first and pays later; the debt is how far the balance is below zero, and a payment is a credit that reduces it.
- Billing manages the Credit Limit and the Outstanding Balance.
- Incurred debt is recorded.
- Supports aggregation cycles and Invoice generation, see [invoicing-and-subscriptions.md](invoicing-and-subscriptions.md).
- Supports paying off the debt.

## Refunds and top-ups

Refunding a top-up debits the wallet, so it is limited to the unspent balance: a refund must not drive a Prepaid wallet negative or into money that is held for a Reservation.

## Cross-cutting guarantees

- Idempotency: nothing is recorded twice on retry. This covers Usage, Reservation, Charge, Payment and Refund.
- An idempotency key identifies one operation within a Tenant. Repeating a request with the same key returns the original result; reusing the key for a different request, or for a different kind of operation, is a conflict.
- Reservation holds and releases stay consistent with the Wallet and Ledger in the same transaction.
- Events that report these changes are published through the transactional outbox, see [security-and-reliability.md](security-and-reliability.md).

## Related

Top-ups and refunds change the Wallet through verified payment results only: [payments-and-stripe.md](payments-and-stripe.md).
