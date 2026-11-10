# Core Billing — Brainstorm Contract

Date: 2026-11-09 · Source: "Core Billing System – System Specification" (Vietnamese, 17 sections, pasted by the user) · Repo state: empty (branch `dev`, no commits, no `docs/` or `plans/`).

## Outcome

Two independently deployable systems that talk only through REST API, events and webhooks:

1. **Core Billing System**: a multi-tenant billing core covering Pricing, Usage Metering, Cost Estimation, Wallet and Ledger, Reservation, Charge, Invoice, Payment, Refund, Subscription, Billing Session and Customer Portal, Webhooks, per-Tenant Stripe configuration (Direct and Connect), Keycloak authentication and Billing-owned Hybrid RBAC. It serves Platform Administration, Tenant Administration and the Customer Billing Portal.
2. **Sample Tenant System**: a Cloud Resource Management reference tenant (Compute, Storage, Bandwidth, Workload, API Operation, Managed Service, Cloud Package) that exercises all 15 integration scenarios in spec §11.3.

No code is written in this step. The next workflow is to be chosen.

## Decisions (user-confirmed unless marked)

| Area | Decision |
|---|---|
| Backend (Billing and Sample Tenant) | Java + Spring Boot, DDD, Clean Architecture, Gradle multi-module |
| Backend shape | Modular monolith per system (Gradle modules enforce bounded contexts). *Assumption from the answer "modular or gradle multi module".* |
| Frontend | React + Vite + TanStack (Router/Query), in a single monorepo of apps plus shared packages. *Assumption: separate apps for Platform Admin, Tenant Admin, Customer Portal and Sample Tenant UI, sharing packages.* |
| Stripe Connect charge model | Direct charges: the charge is created on the Tenant's connected account, so the Tenant bears fees, refunds and disputes |
| Data isolation | Shared DB, shared schema, `tenant_id` everywhere, composite FKs, PostgreSQL RLS as an extra layer (spec §2.2) |
| Identity | One Keycloak realm; tenant-scoped login identifier because Keycloak usernames are unique per realm |

## Constraints

- Spec principles are binding: Keycloak does authentication only, Billing owns authorization, and Billing never acts on Tenant resources.
- Ledger is append-only and the audit source.
- Idempotency on usage, reservation, charge, payment and refund.
- Never record payment success from a browser redirect; only from verified Stripe events.
- Transactional outbox, bounded retry with backoff, dead-letter queue.
- Stripe secrets are encrypted or held in a secret manager and are never returned by read APIs.
- Tenant resolved from the hostname, never from client-supplied `tenant_id`.
- The Sample Tenant System must deploy without the Billing codebase.

## Non-goals

- Implementation code, infrastructure and deploy pipelines in this step.
- Payment providers other than Stripe. Only the extension seam is designed.
- Custom domains. Only the domain-mapping seam is designed.
- Billing managing Tenant users, roles or resources.

## Acceptance criteria

- The design covers every spec section: domain model, invariants and state machines (wallet and ledger, reservation, charge, payment and refund, invoice, subscription), tenant resolution, auth and RBAC, Stripe Direct and Connect flows, webhook in and out, outbox and retry, API surface, data model, UI map, fault tolerance and risks.
- Each of the 15 scenarios in §11.3 maps to concrete flows and to modules or APIs.
- Every gap in the spec appears as a decision with a proposed option.

## Trade-offs

- **Modular monolith (recommended) vs microservices inside Billing.**
  - Depends most on: one team, one PostgreSQL, and a ledger that needs ACID across wallet, ledger and outbox in a single transaction.
  - Fails first when: a module (probably usage ingestion) needs independent scaling or its own release cadence. The Gradle module boundaries and the outbox make that extraction cheap later.
- **Direct charges (chosen) vs destination charges or separate transfers.**
  - Depends most on: Tenants being the merchant of record, owning their disputes, and each having a Stripe account.
  - Fails first when: the Platform wants an application fee or a revenue share, or has to carry dispute liability. Direct charges can still take an `application_fee_amount`, so the switch is localized to the Payment domain.

## Better approaches

None. The requested direction is sound. The repo is empty, so there is no contrary evidence, and the stack choices are consistent with the modular-monolith and ACID-ledger requirements. One design detail still to resolve: using Spring Modulith, or ArchUnit rules, to enforce module boundaries on top of Gradle modules.

## Unresolved questions

1. "react + vite + tanstack as 1 but use mono repo" is read as separate apps in one pnpm/Turborepo-style workspace. Confirm this, or say if you meant a single SPA with route groups.
2. "modular or gradle multi module" is read as a Gradle multi-module modular monolith. Confirm this, or say if you want separate deployable services.
3. Keycloak tenant-scoped username scheme (for example `{tenant-code}:{email}`, or one realm with a custom authenticator) is still open.
4. Single repo with `billing/`, `sample-tenant/` and `web/` directories is assumed. Confirm this, or say if you want separate repos.
5. Which workflow follows this brainstorm (for example `ak-plan`) is not decided.
