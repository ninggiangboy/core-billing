# Concurrency and locking

Every flow has a lock order. This document owns that order, the rules that keep coordination state out of business
transactions, and the safety net that turns a lock wait into a retryable answer instead of a hang or an opaque 500.
A new flow that changes a row another flow already locks must fit the order below.

## Observed order

The order is the order rows are locked, not the order endpoints are called.

| Flow | Locks in order |
|---|---|
| Subscription renewal | subscription, then wallet (through `money.charge`) |
| Charge, credit, debit, reserve, reverse | wallet first, then everything else. A charge's idempotency check runs after the wallet lock |
| Reservation expiry | wallet with `skip locked`, then reservation (the comment on the statement says why) |
| Billing cycle close and `InvoiceChargeUseCase` | unbilled lines, then the invoice counter |
| Stripe event | event, then payment, then refund. A wallet credit goes through `PaymentSettlement`, so the wallet is after the payment |
| Outbox relay | `skip locked`, so it never waits. Consumers share the relay's transaction but lock an invoice or unbilled line, never a wallet |
| Job lease, run record, rate-limit bucket | one coordination row in its own short transaction, never inside a business transaction |

Two consequences:

- No flow in the code today can form a wait cycle. The risk is a new flow that locks the wallet after, say, a
  reservation, or that holds a coordination row while calling a use case.
- The invoice counter is always the last lock. Invoice numbers are gap-free per Tenant, so issuing an invoice for a
  Tenant is serialised until commit. That is serialization, not deadlock, and it is only acceptable while the
  transaction is short: nothing runs after `InvoiceIssuer.issue`.

## Rules

1. **A lease never holds a lock while the job runs.** Acquiring, renewing and releasing a lease is a short
   transaction on one `job_leases` row. Ownership is a deadline, not a held row lock, so there is no hold-and-wait.
   This is why a lease table is used instead of an advisory lock. If an advisory lock is ever unavoidable, only
   `pg_try_advisory_lock` is allowed; the blocking forms are forbidden.
2. **One statement touches one lease row.** A multi-row update has no defined lock order and can deadlock.
3. **A run record is claimed before the work.** `job_runs` is inserted with `on conflict do nothing` in its own
   short transaction. A second run for the same key and Tenant inserts nothing and does no work. The claim is not
   inserted at the end of a long transaction.
4. **The rate limiter runs in the authentication step, alone.** It is one function call that locks one bucket row in
   its own transaction. It never runs inside a business transaction: that would put a rate-limit row in front of the
   wallet and add a new lock edge to every money path. A request whose key does not authenticate spends one of a
   fixed 1024 buckets chosen by key id, so a caller inventing key ids cannot create unbounded rows or exhaust a real
   key's budget. A bucket is expired only once it has been idle long enough that an empty one would have refilled to
   the full burst; expired buckets are removed only by a worker, in small `skip locked` batches, until a batch comes
   back short.
5. **Bootstrap runs under the lease and holds nothing while calling Keycloak.** The seed steps commit on their own;
   the lease is ownership by deadline, not a row lock.
6. **The invoice counter is the last lock in every flow.** Do not add work after `InvoiceIssuer.issue`.
7. **Migrations use a short `lock_timeout` and retry.** `ALTER TABLE` asks for an exclusive lock, queues behind a
   long transaction, and then blocks every new query behind it. The `migrate` profile sets `lock_timeout` to 3
   seconds and retries a bounded number of times.
8. **Set a timeout per profile, not one number for everything.** The api may wait on a wallet row on purpose, because
   that is how two charges on the same wallet serialise. The worker should give up sooner and retry on its next tick.
   The `migrate` step should give up soonest. The running values are in `application-api.yml`,
   `application-worker.yml` and `application-migrate.yml`; they are starting points to measure under load.
9. **Treat `40P01` and `55P03` as retryable.** The api answers 503 with `Retry-After` and expects the caller to
   repeat the request with the same `Idempotency-Key`; every money operation has one. A job logs the round and
   retries on its next tick.

## Adding a flow

Before merging a flow that writes money, payments, invoices or coordination state:

1. Write down which rows it locks and in what order.
2. Check that the order is a subsequence of the table above; do not lock an earlier row after a later one.
3. Keep every coordination statement in its own short transaction and to one row.
4. Run the deadlock stress test (charge, renewal, reservation expiry, cycle close and Stripe settlement on one
   wallet) and confirm `pg_stat_database.deadlocks` stays at zero.

## Related

Money path and wallet ordering: [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md). Invoice counter:
[invoicing-and-subscriptions.md](invoicing-and-subscriptions.md). Reliability requirements:
[security-and-reliability.md](security-and-reliability.md).
