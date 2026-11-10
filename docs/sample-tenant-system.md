# Sample Tenant System

A complete reference system that verifies integration with the Core Billing System. It follows a Cloud Resource Management model and simulates a platform that rents resources. It deploys independently of Billing but needs Billing for every financial function.

## Business functions

- User Registration and Authentication.
- User account management.
- Business Role and Permission management.
- Customer management.
- Customer Mapping with Billing.
- Resource Catalog management.
- Resource Order management.
- Resource Lifecycle management.
- Execution and Background Workload management.
- Resource Usage tracking.
- Management of Billing-related transactions.
- Billing Webhook handling.
- A Customer Portal and Resource Management UI.

## Resource types

| Resource | Illustrated pricing model |
|---|---|
| Compute Instance | Time-based |
| Storage | Usage-based |
| Bandwidth | Usage-based |
| Background Workload | Workload-based |
| API Operation | Event-based |
| Managed Service | Subscription |
| Cloud Package | Hybrid |

## Integration scenarios it must demonstrate

1. User registration and creating a Customer Mapping.
2. Fetching a Pricing Plan and performing Cost Estimation.
3. Checking the Wallet and performing a Reservation.
4. Creating a Resource Order and putting it into the Execution Queue.
5. Recording Usage during use.
6. Performing a Charge when cost is incurred.
7. Releasing the Reservation and updating the remaining budget.
8. Topping up through Stripe.
9. Supporting Stripe Direct and Stripe Connect.
10. Viewing Invoices and transaction history.
11. Handling Webhooks when Billing state changes.
12. Using Subscriptions and combined pricing models.
13. Supporting both Prepaid and Postpaid.
14. Testing data isolation between multiple Tenants.
15. Testing the same email with independent accounts in several Tenants.

## Responsibility

It owns Resources, Orders, Execution and its own users; it decides what to do with Billing's state changes. See the division rules in [overview-and-scope.md](overview-and-scope.md) and the event contract in [webhooks-and-events.md](webhooks-and-events.md).

## Stack

Same backend and frontend toolchain as Billing, see [architecture.md](architecture.md).
