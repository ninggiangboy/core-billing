# Security and Reliability

Requirements beyond business functions.

## Authentication and session

- OIDC authentication through Keycloak.
- Validate token Issuer, Audience, Expiration and Subject.
- Distinguish Platform Context from Tenant Context.
- A session from one Tenant must not be usable in another Tenant.

## Data security

- Tenant-scoped data access everywhere, see [multi-tenancy-and-data-isolation.md](multi-tenancy-and-data-isolation.md).
- Composite foreign keys for important relationships.
- PostgreSQL Row-Level Security where appropriate.
- Sensitive data is never exposed through logs, APIs or Webhooks.

## Payment security

- Encrypt the Stripe Secret Key and Webhook Secret.
- Verify the Stripe Webhook signature.
- Never store payment card information directly.
- Use Stripe-hosted Checkout or the Payment Element where appropriate.
- Reconcile transactions, and handle Webhooks that arrive late or out of order.

## Reliability

- Transactional Outbox for important Events.
- Bounded Retry with Backoff.
- Dead Letter Queue.
- Idempotent Consumer.
- Background jobs run once per Tenant, each inside that Tenant's scope, because Row-Level Security lets a job see one Tenant at a time. A failure in one Tenant does not stop the others.
- Audit Log of administrative actions and transactions.
- Monitoring and Distributed Tracing.

## Related

Stripe handling: [payments-and-stripe.md](payments-and-stripe.md). Outbound delivery: [webhooks-and-events.md](webhooks-and-events.md).
