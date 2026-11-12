# Overview and Scope

## Purpose

A standalone Core Billing System is the central place for financial operations and charging across many kinds of resource-rental services. Many Tenants share one Platform while keeping independent data, accounts, integration configuration and billing operations.

The goal is that many different Tenant Systems can use Billing regardless of service type, deployment technology or internal business structure.

## Two systems

1. **Core Billing System.** Owns Multi-tenancy, Pricing, Wallet, Ledger, Reservation, Charge, Invoice, Payment, Refund and Subscription. It serves Platform Administration, Tenant Administration and the Customer Billing Portal.
2. **Sample Tenant System.** A complete Cloud Resource Management reference system that proves the integration. See [tenant-system.md](tenant-system.md).

They are deployed independently and communicate only through REST API, Events and Webhooks. The Sample Tenant System needs Billing for financial functions but must deploy without it.

## Vocabulary

| Term | Meaning |
|---|---|
| Platform | The platform that provides and manages Billing |
| Tenant | A unit that uses Billing, with its own data, configuration and operations; identified by a unique `tenant-code` |
| Tenant System | The Tenant's business system; provides and operates the service and its Resources |
| Tenant User | A business user that belongs to the Tenant System; not managed by Billing |
| Billing User | A user who administers the Platform or a Tenant in Billing |
| Billing Customer | The entity that represents a Tenant's customer inside Billing; independent of any Tenant User |
| Tenant Customer Mapping | The record that links a Tenant User to a Billing Customer |
| Billing Session | A time-limited, scoped grant that lets one Customer reach the Billing Portal |
| Keycloak | The Identity Provider; authentication only |
| Stripe / Stripe Connect | The Payment Provider, and the mechanism that links a Tenant's Stripe account to the Platform |

## Functions

Pricing Engine, Usage Metering, Cost Estimation, Wallet and Ledger, Reservation, Charge, Invoice, Payment, Refund, Subscription, Billing Session, Webhook, and per-Tenant Payment Provider Configuration. Details live in the area documents listed in the [index](README.md).

Supported pricing models: Time-based, Usage-based, Workload-based, Event-based, Subscription and Hybrid. Both Prepaid and Postpaid billing are supported.

## Who is responsible for what

**Billing System**
- Manages Platform, Tenants, Billing Customers, Users, Roles, Permissions and Authorization.
- Integrates Keycloak for authentication.
- Manages Tenant Context, Data Isolation, Tenant Domains and Integration Credentials.
- Manages the Tenant User Mapping.
- Owns pricing, cost calculation and estimation, balance checks, budget holds and deductions.
- Records Usage and owns every financial transaction: Wallet, Ledger, Reservation, Charge, Invoice, Payment, Refund, Subscription.
- Manages Payment Provider Configuration independently per Tenant, including Stripe Direct and Stripe Connect.
- Provides the REST API for Tenant Systems, including the API that creates Billing Sessions.
- Receives Events and Webhooks from Tenant Systems and Stripe, and sends Webhooks about Billing state changes.
- Guarantees financial consistency, Idempotency and auditability.
- Provides the Platform Administration, Tenant Administration and Customer Billing Portal interfaces.

**Tenant System**
- Manages its own User accounts, business Roles and Permissions.
- Manages Customers and their mapping to Billing Customers.
- Owns Resources, Orders, Execution, the Resource Lifecycle, the Execution Queue and Background Workloads.
- Supplies Usage, performs Cost Estimation before deploying, checks the Reservation result before queuing work, and sends Charge requests when real cost is incurred.
- Decides whether to execute, renew, suspend or stop the service, and defines its own policy for Billing state changes.
- Creates Billing Sessions when a Customer needs the Billing UI.
- Does not manage Billing's Ledger, Wallet or internal financial operations.

**Division rules**
- Billing manages only financial operations and never operates on Resources.
- The Tenant System controls the Resource lifecycle and execution.
- Keycloak does authentication; Billing does authorization.
- A Billing Customer is an independent entity, not the same as a Tenant User.
- Billing publishes state and results through API, Events and Webhooks. It never imposes actions on the Tenant System, which decides its own next step.
- Stripe processes payment transactions; Billing synchronizes status and updates the financial ledger.
- Each Tenant has an independent Stripe Account Configuration.
- A Customer can act only within the scope of the Billing Session issued to them.
- Tenants are fully isolated in data and access rights.

## Non-goals

- Payment providers other than Stripe. Only the extension seam is designed.
- Custom domains. Only the domain-mapping seam is designed.
- Billing managing Tenant User accounts, passwords, Roles or Permissions.
- Billing operating on Resources or deciding on a Tenant System's behalf.
