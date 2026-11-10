# Webhooks and Events

## Tenant System to Billing

Billing offers a REST API and a mechanism for receiving Events from the Tenant System. Integrated operations:

- Customer Mapping
- Pricing and Cost Estimation
- Reservation
- Usage Metering
- Charge
- Refund Request, if authorized
- Related business Events

## Billing to Tenant System

Billing proactively sends Webhooks when business state changes.

| Event | Meaning |
|---|---|
| `reservation.created` | A Reservation was created |
| `reservation.expired` | A Reservation expired |
| `reservation.released` | A Reservation was released |
| `charge.succeeded` | A Charge succeeded |
| `charge.failed` | A Charge failed |
| `payment.succeeded` | A Payment succeeded |
| `payment.failed` | A Payment failed |
| `refund.succeeded` | A Refund succeeded |
| `invoice.created` | An Invoice was created |
| `invoice.paid` | An Invoice was paid |
| `subscription.renewed` | A Subscription was renewed |
| `subscription.payment_failed` | A Subscription payment failed |

These are examples from the specification. Once an event schema exists in code, that schema owns the event list.

## Principles

- A Webhook only reflects a business state or result.
- Events are never used to impose an action on the Tenant System.
- The Tenant System defines its own policy for handling Events. For example, on `payment.failed` it may record a debt, extend the payment period, suspend the Resource, or keep providing the service. Billing does not decide for it.
- Supports Retry, Signature Verification and Idempotency.
- Outbound Webhooks are signed with HMAC-SHA256 over the timestamp and the raw request body, carry the timestamp and a key id, reject a stale timestamp, and process an event id once. The signing secret is separate from the API credential a Tenant System uses to call Billing.
- Keeps delivery history and processing status, and supports resending a Webhook.
- A Tenant configures its Webhook Endpoints and the Event types it wants to receive.

Reliability mechanics (outbox, backoff, dead-letter queue, idempotent consumers) are in [security-and-reliability.md](security-and-reliability.md). Inbound Stripe webhooks are in [payments-and-stripe.md](payments-and-stripe.md).
