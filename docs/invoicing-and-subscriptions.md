# Invoicing and Subscriptions

## Invoice

- Manages Invoices and Invoice Items.
- Supports Invoices generated from Charges or from Subscriptions.
- Manages payment status.
- Tracks debt and payment history.

For Postpaid customers, Invoices come from the aggregation cycle described in [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md).

### What an invoice is

An invoice is a request for payment of what a wallet **already owes**. Every line is backed by a succeeded Charge, which is where the amount was taken from the wallet (for a Postpaid wallet that is the debt). So an invoice never creates debt and never changes a Charge: paying it credits the wallet by the invoice total, and voiding it leaves the wallet alone. A Charge is billed on at most one line.

A line is either a plain Charge (from usage, a reservation or on its own) or the renewal Charge of one subscription period, which names the subscription and the period it covers. Totals are in the wallet's currency.

| Status | Meaning |
|---|---|
| `DRAFT` | Being assembled; has no number. Invoices are issued in the same step they are built, so a draft is never stored. |
| `OPEN` | Issued and unpaid. |
| `PAID` | Paid by a verified payment, or documenting a Charge the wallet had already paid. |
| `VOID` | The request for payment was cancelled by an administrator, with a reason. |
| `UNCOLLECTIBLE` | Written off as unlikely to be paid by an administrator. It can still be paid later. |

Invoice numbers (`INV-000042`) are a gap-free sequence per Tenant, taken from a counter row that is locked in the issuing transaction, so an invoice that rolls back gives its number back.

### How invoices are created

- **Postpaid aggregation cycle.** A succeeded Charge on a Postpaid wallet becomes an *unbilled line* of the calendar period (UTC) it was incurred in, through the outbox, deduplicated by the inbox and by the Charge. The cycle job closes every period that has ended: the unbilled lines of one wallet and period become one invoice, announced as `invoice.created`. Running the job twice, or on two instances, bills a period once. A period that cannot be closed is logged, counted as a failure and left untouched for the next run, so it never holds back the periods behind it. A Charge that reaches Billing late, for a period that was already invoiced, waits for the period that is open. A Charge on a Prepaid wallet is never collected, because the wallet already paid it. The period is set by `billing.invoicing.postpaid-cycle` (`MONTHLY` by default).
- **On request.** A Tenant asks for an invoice for one succeeded Charge. Asking again returns the invoice that exists. A Postpaid Charge is still owed, so its invoice opens for payment (and a line already waiting for its cycle moves onto it); a Prepaid Charge was paid when it was made, so its invoice is a `PAID` record with the settlement `WALLET`.
- **Subscription renewals** reach the cycle invoice through the first path, as subscription-period lines.

### Paying an invoice

A Tenant System or administrator starts a payment for an open invoice with an `Idempotency-Key`. Billing creates a Stripe-hosted Checkout Session through the Tenant's Stripe configuration (see [payments-and-stripe.md](payments-and-stripe.md)) and returns its URL. Nothing is paid yet.

When Stripe's verified result arrives, the payment context announces `payment.succeeded` carrying the invoice id. Invoicing consumes it from the outbox: it credits the wallet by the invoice total (the debt is paid off), marks the invoice `PAID` and announces `invoice.paid`, all in one transaction with the inbox mark. The credit key is derived from the payment, so a repeated event credits once. **Nothing marks an invoice paid from a client request or a browser redirect.** A declined payment is recorded in the invoice's payment history and leaves it open, to be paid again.

Money that arrives for an invoice that can no longer be paid (it was already paid by another payment, or voided) is still credited to the wallet, because the customer really paid it, and the payment is recorded in the history. A payment of an invoice cannot be refunded through Billing: the refund would bring the paid-off debt back while the invoice stayed paid.

### Void and uncollectible

An administrator can void an open invoice with a reason, or write it off as uncollectible. Voiding never touches the wallet: what was charged stays charged, and correcting the ledger is a reversal (see [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md)). A paid invoice cannot be voided.

## Subscription

- Manages recurring service subscriptions.
- Supports Billing Cycles.
- Supports renewal and recurring charges.
- Records Subscription Events.
- Does not provision or stop Resources. The Tenant System decides what to do when it receives `subscription.renewed` or `subscription.payment_failed`, see [webhooks-and-events.md](webhooks-and-events.md).

### The schedule

A subscription fixes the plan, the wallet, the billing cycle and the currency when it is created, and is anchored at that moment. Period `n` runs from `anchor + n cycles` to `anchor + (n+1) cycles`, counted from the anchor (never chained), so a subscription anchored on the 31st renews on the last day of a short month and returns to the 31st afterwards. All periods are in UTC. A period is charged **in advance**, when it starts: creating a subscription charges period 0 at once.

The price of a period is the plan's recurring amount under the plan version in force when the period starts: the fixed price of a Subscription plan, or the base amount of a Hybrid plan. A later plan version never changes a period already charged.

### Renewal

The renewal job takes one due subscription at a time (`for update skip locked`, so two workers never renew the same one), charges the wallet through the money context, and moves the schedule forward, writes the history record and the `subscription.renewed` notice in the same transaction as that charge. Period 0 is reported as `subscription.renewed` too, with `periodNumber` 0. A Prepaid wallet pays from its balance; a Postpaid wallet is charged the same way, which records the debt, and the Charge reaches the cycle invoice as a subscription line.

A period is charged **once**, however the job is repeated: the subscription row is locked, the charge key names the subscription, the period and the attempt, the schedule moves in the charge's transaction, and a unique index allows one renewed record per period. A catch-up after downtime charges each missed period once, oldest first.

### Combined models

A Hybrid plan bills its base amount through the subscription, once per cycle, and its usage through charges from usage. Charging usage under a Hybrid plan prices only the usage components (with the same single rounding and the plan's limits); an estimate of a whole cycle still includes the base. Both kinds of Charge reach the same period invoice as lines.

### When a period cannot be paid

If the wallet refuses the Charge (for example `insufficient_funds`), or the price cannot be determined, the subscription becomes `PAST_DUE`, the failed attempt is recorded in its history, and `subscription.payment_failed` is announced with the reason, the attempt number and when Billing tries again. Retries wait longer each time (capped) and are bounded: after `billing.invoicing.renewal-max-attempts` Billing stops trying on its own, the last notice says `willRetry: false`, and the subscription stays past due for the Tenant System to decide. A success at any attempt returns it to `ACTIVE`. Billing never stops any Resource; past due is a fact the Tenant System reacts to by its own policy.

### Cancellation

Cancelling keeps the period that is already paid: the subscription ends where that period ends, and the renewal job ends it instead of charging the next one. When no paid period is running (nothing was charged yet, or the last period could not be paid) it ends at once. Cancelling again changes nothing. Every change is recorded in the subscription's history.

## API and permissions

The shapes are in [contracts/openapi/billing.yaml](../contracts/openapi/billing.yaml) (and `billing-tenant-system.yaml` for what a Tenant System may call) and the event shapes in [contracts/events](../contracts/events/README.md).

| Operation | Permission | Who |
|---|---|---|
| List and read invoices and subscriptions | `invoice:read`, `subscription:read` | Administrators and Tenant Systems (mapped customers only) |
| Invoice a Charge | `invoice:create` | Administrators and Tenant Systems |
| Pay an invoice | `payment:create` | Administrators and Tenant Systems |
| Create and cancel a subscription | `subscription:manage` | Administrators and Tenant Systems |
| Void an invoice, write it off | `invoice:manage` | Administrators only |

A Tenant System sees and acts on the customers mapped to it only; another system's invoice or subscription answers 404.

## Events

| Event | Sent when |
|---|---|
| `invoice.created` | An invoice is issued, for a closed period or for a Charge. |
| `invoice.paid` | An invoice is paid by a verified payment, or documents a Charge the wallet already paid. |
| `subscription.renewed` | A period was paid. |
| `subscription.payment_failed` | A period could not be paid. |

`payment.succeeded` and `payment.failed` also carry an `invoiceId` when the payment was for an invoice.

## Settings

`billing.invoicing.*` in `application.yml`: `postpaid-cycle`, `cycle-batch`, `cycle-delay`, `renewal-batch`, `renewal-delay`, `renewal-max-attempts`, `renewal-backoff-base`, `renewal-backoff-cap` and `jobs-enabled`. Switching `jobs-enabled` off is the rollback: charges stay collected and subscriptions stay due, and both catch up when it is switched back on.
