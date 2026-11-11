---
phase: 11
title: "Sample Tenant System backend"
status: pending
priority: P1
effort: "8d"
dependencies: [7, 8, 9, 10]
---

# Phase 11: Sample Tenant System backend

## Goal

Build a Cloud Resource Management reference tenant as its own Java build and database that integrates with Billing only over REST and webhooks and can be deployed without the Billing codebase.

## Context

Source: [sample-tenant-system.md](../../docs/sample-tenant-system.md), [webhooks-and-events.md](../../docs/webhooks-and-events.md). It uses the same stack conventions (Java, Spring Boot, DDD and Clean Architecture, Gradle multi-module, Spring Data JDBC) but shares no code with `billing/`. The Billing HTTP client is hand-written over `contracts/openapi/billing.yaml`; webhook payload records are generated from `contracts/events/` schemas.

## Files to Create / Modify

- Create: `sample-tenant/` Gradle build with modules: `identity` (own users, registration and login, business roles), `customer` (Customer and Billing mapping), `catalog` (Resource Catalog), `resource` (orders and lifecycle), `execution` (queue and background workers), `usage`, `billing-client`, `webhook-receiver`, `app-boot`
- Create: a bootstrap command that creates the reference tenant's pricing plans and webhook endpoint in Billing through the admin API
- Create: its own Flyway migrations and PostgreSQL database; its own ArchUnit rules and CI workflow

## Tasks & Steps

- [x] 1. Reuse the layered-module convention from phase 1 by copying the build-logic (not depending on `billing/`). Add a CI check that no `sample-tenant` Gradle project depends on `billing`.
- [x] 2. `identity`: registration, authentication with its own credentials, account management, business roles and permissions. It does not use Billing's Keycloak realm.
- [x] 3. `billing-client`: typed calls for pricing plans, cost estimation, reservation, usage, charge, mapping, billing session, with Idempotency-Key on every write and retry with backoff on transport errors.
- [x] 4. `customer`: on registration create the Billing Customer and the mapping (scenario 1) and store the returned customer id.
- [x] 5. `catalog`, `resource`: the seven Resource types and their pricing models (Compute time-based, Storage and Bandwidth usage-based, Background Workload workload-based, API Operation event-based, Managed Service subscription, Cloud Package hybrid).
- [x] 6. `resource` order flow: fetch plan, estimate cost (scenario 2), check wallet and reserve (scenario 3), create the order and enqueue execution only when the reservation succeeds (scenario 4).
- [x] 7. `execution`: database-backed queue with `skip locked` workers that run orders, emit usage during execution (scenario 5), charge when cost is incurred (scenario 6), and release the remaining reservation (scenario 7).
- [x] 8. Top-up and billing views: create a Billing Session and redirect to the portal for top-up and invoice views (scenarios 8 and 10); handle both Direct and Connect tenants unchanged (scenario 9).
- [x] 9. `webhook-receiver`: verify the HMAC signature and timestamp, dedupe by event id, and apply the tenant's own policy to each event (record a debt, extend a period, suspend, or continue) (scenario 11).
- [x] 10. Subscriptions and combined pricing: Managed Service and Cloud Package orders create Billing subscriptions and react to renewal and failure events (scenario 12).
- [x] 11. Prepaid and Postpaid customers: a customer setting selects the wallet mode on the Billing side (scenario 13).
- [x] 12. Scenario-to-test matrix in `sample-tenant/README.md` mapping scenarios 1 to 13 to a module, an API and a test; scenarios 14 and 15 are exercised by the phase 14 suite with a second tenant.

## Verification

- `cd sample-tenant && ./gradlew build` passes and the dependency check proves no Gradle dependency on `billing`.
- Component tests run each of scenarios 1 to 13 against a running Billing instance started from docker-compose (Billing's Docker image or a locally built jar used only as a black box).
- The service starts and its non-billing functions work with Billing stopped; billing-dependent actions fail with a clear error and the order is not queued.
- Webhook receiver tests cover a duplicate delivery, a stale timestamp and a forged signature.

## Risks and rollback

- Drift between hand-written client and the OpenAPI file: a contract test calls every client method against a Billing instance on each CI run.
- This phase is large; the order in the steps allows stopping after step 7 with a usable partial sample.
