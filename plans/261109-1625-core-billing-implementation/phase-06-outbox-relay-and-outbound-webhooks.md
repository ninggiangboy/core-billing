---
phase: 6
title: "Outbox relay and outbound webhooks"
status: completed
priority: P1
effort: "4d"
dependencies: [5]
---

# Phase 6: Outbox relay and outbound webhooks

## Goal

Move outbox events to in-process consumers and to Tenant System webhook endpoints with signing, bounded retry, a dead-letter state, delivery history and resend.

## Context

Source: [webhooks-and-events.md](../../docs/webhooks-and-events.md), [security-and-reliability.md](../../docs/security-and-reliability.md). Webhooks only report state; Billing never imposes an action. The signing secret is separate from the API credential.

## Files to Create / Modify

- Create: `billing/webhook/` (`WebhookEndpoint`, `WebhookDelivery`, signer, sender, SSRF guard, REST for endpoint config, delivery history and resend)
- Create: `billing/app-boot/src/main/java/.../infrastructure/outbox/` (poller and in-process dispatcher)
- Create: `contracts/events/*.schema.json` (one schema per event, source of truth for the payload) and a JSON Schema validation test
- Create: Flyway `V7__webhooks.sql`; `AesGcmSecretCipher` in `app-boot` implementing the webhook module's own secret port

## Tasks & Steps

- [x] 1. Outbox poller: select due rows with `for update skip locked`, mark in progress, dispatch, mark done; bounded attempts with exponential backoff and jitter; after the limit set status `dead` and keep the row.
- [x] 2. In-process dispatch publishes a Spring application event per outbox row, with consumers deduplicating by event id through an inbox table (`unique (consumer, event_id)`).
- [x] 3. `WebhookEndpoint`: URL, subscribed event types, status, signing secret with key id, encrypted at rest with envelope AES-GCM using a master key from the environment or a secret manager; never returned after creation, with rotate support.
- [x] 4. Delivery: for every outbox event and every subscribed endpoint of the event's tenant, create a `WebhookDelivery` row; a sender worker posts the JSON body with headers carrying timestamp, key id and `HMAC-SHA256(timestamp + "." + rawBody)`; success is any 2xx.
- [x] 5. SSRF guard: HTTPS only outside the dev profile, resolve the host and reject loopback, link-local and private ranges, do not follow redirects, set connect and read timeouts and a response size limit.
- [x] 6. Retry schedule, dead state, delivery attempt log (status code, latency, truncated response), and a resend API that creates a new attempt for a stored event.
- [x] 7. Event payload builders produce JSON that validates against the schemas in `contracts/events/`; the schema list includes every event in [webhooks-and-events.md](../../docs/webhooks-and-events.md) that exists so far, and each later phase adds its own.
- [x] 8. Admin REST: configure endpoints and event types, list deliveries with status filter, resend.
- [x] 9. Published verification recipe for receivers (header format, tolerance window, example in Java and TypeScript) kept in `contracts/events/README.md`.

## Verification

- A killed worker mid-delivery leaves the row retryable; two workers never deliver the same row twice (concurrency test).
- A receiver that returns 500 is retried on schedule, then marked dead; resend produces a new attempt.
- A request to an endpoint resolving to `127.0.0.1` or a private address is refused in a test; redirects are not followed.
- Signature test: the documented verification recipe validates a delivery and rejects a stale timestamp and a tampered body.
- Schema validation test fails when a payload builder drifts from its JSON Schema.

## Risks and rollback

- Delivery at-least-once means receivers must dedupe by event id; the contract documents this. Rollback is disabling the sender worker by configuration; events accumulate in the outbox and are delivered when re-enabled.

## Deviations and results

- **Migration number.** `V7__webhooks.sql`, because `V6` was taken by the money schema. It also adds `outbox.last_error` and the `event_inbox` table.
- **Cross-tenant outbox.** Row-level security stays untouched: the relay and the sender run once per active Tenant through `TenantSweep`, each inside that Tenant's scope, like the money jobs. The cost is one short transaction per Tenant per tick, which is fine for a single instance and the first thing to revisit at large Tenant counts.
- **Outbox claim.** The relay claims a row, publishes it to the consumers and marks it delivered in one transaction, so a crash leaves it pending and a consumer failure rolls back the consumer's own writes; a second transaction records the failure and schedules the retry. The "in progress" state lives on webhook deliveries (a lease: status `IN_PROGRESS` with `next_attempt_at` at the end of the lease), where the HTTP call happens outside any transaction.
- **Secret rotation** replaces the secret at once; the `Billing-Key-Id` header lets a receiver hold both during its own cut-over. No overlap window on the Billing side.
- **Resend** inserts a new delivery row that points at the original, with a fresh attempt budget and a byte-identical body; the original keeps its history. Only a finished delivery can be resent.
- **Permissions.** `webhook:read` and `webhook:manage`, both grantable to a Tenant System credential so an integrator can register its own endpoint.
- **Schemas** describe the `data` member; the envelope and headers are documented in `contracts/events/README.md`. There is also a `customer.created` schema because that event already existed. The schemas are packaged on the classpath and are the event catalog, so a subscription to a type without a schema is rejected.
- **SSRF guard** runs in the HTTP client's own DNS resolver (no gap between check and connect); an architecture rule keeps every other HTTP client out of the webhook module, with a fixture proving the rule fires.
- **Master key.** `BILLING_SECRETS_MASTER_KEY` (base64 of 32 bytes) is required to start; `.env.example` documents it.
- **Verification.** Concurrency test (five workers, sixty deliveries, each received once), lease-expiry and lost-lease tests, retry-to-dead-to-resend test, SSRF and redirect tests against a real local server, signature recipe test written independently of the production signer, and the schema contract test with a drift meta-test.
