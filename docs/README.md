# Core Billing Documentation

Documentation for two independently deployable systems: the **Core Billing System** and the **Sample Tenant System** (a Cloud Resource Management reference tenant). It is written for people and AI collaborators who need to know what the system must do, why it is shaped this way, and where each rule is owned.

## Authority

- [system-specification.md](system-specification.md) is the original specification, translated to English and kept unchanged. It is the baseline snapshot and is not edited further.
- The topic documents below carry the same requirements split by area, plus the decisions made after the specification. They are the maintained contract. If a topic document differs from the baseline, the topic document wins and the difference is a decision recorded in the ledger in [architecture.md](architecture.md).
- Once code exists, executable owners (API schemas, event schemas, database migrations, tests) take over the details they define. A topic document then keeps only the rationale and a link to that owner, and tables here that list events, fields or endpoints stop being the source of truth.

## Reading order

| Document | Owns |
|---|---|
| [overview-and-scope.md](overview-and-scope.md) | Purpose, vocabulary, who is responsible for what, non-goals |
| [architecture.md](architecture.md) | System diagram, decision ledger, module boundaries, technical principles |
| [multi-tenancy-and-data-isolation.md](multi-tenancy-and-data-isolation.md) | Tenant model, isolation strategy, tenant resolution |
| [authentication-and-authorization.md](authentication-and-authorization.md) | Keycloak usage, Hybrid RBAC, Billing Users |
| [customers-and-tenant-mapping.md](customers-and-tenant-mapping.md) | Billing Customers and the Tenant User mapping |
| [pricing-and-usage.md](pricing-and-usage.md) | Pricing Engine, Usage Metering, Cost Estimation |
| [wallet-ledger-and-charging.md](wallet-ledger-and-charging.md) | Wallet, Ledger, Reservation, Charge, Prepaid and Postpaid |
| [invoicing-and-subscriptions.md](invoicing-and-subscriptions.md) | Invoice and Subscription |
| [payments-and-stripe.md](payments-and-stripe.md) | Payment Provider model, Stripe Direct and Connect, Payment, Refund, Stripe webhooks |
| [billing-session-and-portal.md](billing-session-and-portal.md) | Billing Session and the Customer Portal |
| [webhooks-and-events.md](webhooks-and-events.md) | Tenant System to Billing and Billing to Tenant System integration |
| [sample-tenant-system.md](sample-tenant-system.md) | The reference tenant and its integration scenarios |
| [security-and-reliability.md](security-and-reliability.md) | Security and operational requirements |

## Stateful records

These are dated records, not evergreen guidance:

- `plans/reports/brainstorm-261109-2031-core-billing-contract.md` captured the scope and the decisions that [architecture.md](architecture.md) now carries.
- `plans/reports/advise-261109-2227-tenant-system-auth.md` holds the confirmed machine-to-machine authentication decision and its rejected alternatives.
- `plans/reports/advise-261109-2248-ddd-clean-architecture.md` holds the confirmed DDD and Clean Architecture decisions, their rejected alternatives, and the enforcement plan.
- `plans/reports/advise-261109-2309-ledger-append-only.md` holds the confirmed append-only Ledger model, its correction and balance-projection rules, the wallet currency decision, and their rejected alternatives.
