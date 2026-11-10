# Multi-tenancy and Data Isolation

## Tenant model

One Platform manages many Tenants. Each Tenant is an independent business space.

- Each Tenant has a unique `tenant-code`.
- Each Tenant has its own data, configuration and Billing operations.
- Data is never accessible across Tenants.
- Wallet, Ledger, Invoice, Payment, Refund, Subscription and Customer all belong to a Tenant scope.
- The same email can exist in several Tenants as independent accounts.
- A User of one Tenant has no implicit access to another Tenant.
- A Tenant can integrate one or more Tenant Systems.

## Isolation strategy

Shared Database, Shared Schema, Tenant ID.

- Tenants share one database and schema.
- Tenant-scoped business tables carry a `tenant_id` column.
- Every data query is restricted by the Tenant Context.
- Authorization checks the Tenant Scope before allowing access.
- Relationships must make cross-Tenant references impossible, using composite foreign keys for important relations.
- Unique constraints include `tenant_id` whenever the identifier is scoped to a Tenant.
- PostgreSQL Row-Level Security is a further layer: the tenant is set once per transaction inside the transaction boundary, so no query can run outside a tenant scope.
- The Tenant Context reaches the domain as a value object; a raw `tenant_id` is never threaded through business code.

## Tenant resolution and domains

Each Tenant is reached through a subdomain.

| Domain | Scope |
|---|---|
| `platform.base-domain.com` | Platform Administration |
| `tenant-a.base-domain.com` | Tenant A |
| `tenant-b.base-domain.com` | Tenant B |

- The Tenant is resolved automatically from the request hostname.
- A Tenant Context is built once and carried through every processing layer.
- Background Jobs and Event Consumers carry the Tenant Context too.
- A `tenant_id` supplied by a client is never trusted to determine access rights.
- Domain Mapping is designed in so Custom Domains can be added later.

## Related

- Authentication and tenant-scoped identity: [authentication-and-authorization.md](authentication-and-authorization.md).
- Session isolation rules: [security-and-reliability.md](security-and-reliability.md).
