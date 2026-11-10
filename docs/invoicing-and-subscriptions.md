# Invoicing and Subscriptions

## Invoice

- Manages Invoices and Invoice Items.
- Supports Invoices generated from Charges or from Subscriptions.
- Manages payment status.
- Tracks debt and payment history.

For Postpaid customers, Invoices come from the aggregation cycle described in [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md).

## Subscription

- Manages recurring service subscriptions.
- Supports Billing Cycles.
- Supports renewal and recurring charges.
- Records Subscription Events.
- Does not provision or stop Resources. The Tenant System decides what to do when it receives `subscription.renewed` or `subscription.payment_failed`, see [webhooks-and-events.md](webhooks-and-events.md).
