# Customers and Tenant Mapping

## Billing Customer

A Billing Customer represents a customer who uses a Tenant's services.

- Each Customer belongs to exactly one Tenant.
- A Customer is managed independently of the Tenant System's User account.
- A Customer does not need a Billing login account.
- A Customer can be given access to Billing through a Billing Session, see [billing-session-and-portal.md](billing-session-and-portal.md).
- A Customer owns the Wallet, Usage, Charge, Invoice, Payment and Subscription information within the supported business scope.

## Tenant User Mapping

User accounts of the Tenant System are outside Billing's management scope. The Tenant System manages its own accounts and maps them to Billing Customers. Billing does not manage passwords, Roles or Permissions of a Tenant User.

Relationship: one Tenant User maps to one Billing Customer within the same Tenant.

The mapping is a record in Billing with at least:

- `tenant_id`
- `tenant_system_id`
- `external_user_id`
- `billing_customer_id`

Two Tenants may use the same `external_user_id` or email, but their Billing Customers are completely independent.

| Tenant | Tenant User | Billing Customer |
|---|---|---|
| Tenant A | `user_001` | `cus_001` |
| Tenant B | `user_001` | `cus_002` |

Because a Tenant can integrate more than one Tenant System, `tenant_system_id` keeps `external_user_id` unambiguous.

### Creating and finding Customers

- Creating a Customer is idempotent by the `Idempotency-Key` header, which is required. Repeating the request returns the same Customer; reusing the key for a different request is a conflict, and so is mapping an external user that already has a Customer under a new key.
- A Tenant System creates and finds Customers for itself only; it cannot name another Tenant System. A Tenant User with the right permission administers Customers and names the Tenant System explicitly. A Customer may exist without a mapping.
- The `customer.created` event carries the Customer id, display name and mapped external ids, and no email or other personal data.
- Other contexts that act on a Customer (money, payment) ask the `customer` module's public lookup, which answers within the current Tenant only.

## Related

Tenant scoping of these records: [multi-tenancy-and-data-isolation.md](multi-tenancy-and-data-isolation.md).
