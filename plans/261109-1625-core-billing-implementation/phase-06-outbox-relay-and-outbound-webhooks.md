---
phase: 6
title: "Outbox relay and outbound webhooks"
status: pending
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
- Create: Flyway `V6__webhooks.sql`; `AesGcmSecretCipher` in `app-boot` implementing the webhook module's own secret port

## Tasks & Steps

- [ ] 1. Outbox poller: select due rows with `for update skip locked`, mark in progress, dispatch, mark done; bounded attempts with exponential backoff and jitter; after the limit set status `dead` and keep the row.
- [ ] 2. In-process dispatch publishes a Spring application event per outbox row, with consumers deduplicating by event id through an inbox table (`unique (consumer, event_id)`).
- [ ] 3. `WebhookEndpoint`: URL, subscribed event types, status, signing secret with key id, encrypted at rest with envelope AES-GCM using a master key from the environment or a secret manager; never returned after creation, with rotate support.
- [ ] 4. Delivery: for every outbox event and every subscribed endpoint of the event's tenant, create a `WebhookDelivery` row; a sender worker posts the JSON body with headers carrying timestamp, key id and `HMAC-SHA256(timestamp + "." + rawBody)`; success is any 2xx.
- [ ] 5. SSRF guard: HTTPS only outside the dev profile, resolve the host and reject loopback, link-local and private ranges, do not follow redirects, set connect and read timeouts and a response size limit.
- [ ] 6. Retry schedule, dead state, delivery attempt log (status code, latency, truncated response), and a resend API that creates a new attempt for a stored event.
- [ ] 7. Event payload builders produce JSON that validates against the schemas in `contracts/events/`; the schema list includes every event in [webhooks-and-events.md](../../docs/webhooks-and-events.md) that exists so far, and each later phase adds its own.
- [ ] 8. Admin REST: configure endpoints and event types, list deliveries with status filter, resend.
- [ ] 9. Published verification recipe for receivers (header format, tolerance window, example in Java and TypeScript) kept in `contracts/events/README.md`.

## Verification

- A killed worker mid-delivery leaves the row retryable; two workers never deliver the same row twice (concurrency test).
- A receiver that returns 500 is retried on schedule, then marked dead; resend produces a new attempt.
- A request to an endpoint resolving to `127.0.0.1` or a private address is refused in a test; redirects are not followed.
- Signature test: the documented verification recipe validates a delivery and rejects a stale timestamp and a tampered body.
- Schema validation test fails when a payload builder drifts from its JSON Schema.

## Risks and rollback

- Delivery at-least-once means receivers must dedupe by event id; the contract documents this. Rollback is disabling the sender worker by configuration; events accumulate in the outbox and are delivered when re-enabled.
