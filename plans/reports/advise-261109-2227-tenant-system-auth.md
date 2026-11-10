# Tenant System → Billing: M2M Authentication Decision

Date: 2026-11-09 · Scope: how a third-party, self-service Tenant System authenticates to the Core Billing API · Status: advisory, no code written.

## Verdict

Choose **Billing-issued per-Tenant-System API keys** as bearer credentials over TLS, and use **HMAC-SHA256 only for Billing → Tenant webhooks**. Do not route machine-to-machine traffic through Keycloak Client Credentials for this onboarding model.

The deciding evidence is the confirmed onboarding model: Tenant Systems are third parties that self-serve and do not share your IdP. Keycloak Client Credentials would force every credential creation into a cross-system transaction (Billing + Keycloak Admin API), require a privileged realm-admin credential, and couple rotation and revocation to Keycloak availability. That is real operational cost for little benefit. A Billing-issued key keeps provisioning local, matches the specification line that Billing manages Integration Credentials, and gives integrators the Stripe-like flow they already know.

The price is a long-lived secret instead of a short-lived token. That is acceptable if scopes, rotation, revocation, rate limits, audit, and leak response are designed now, not later.

## Reframing

**Problem.** Tenant Systems are third-party, self-service, and do not use the Platform's identity provider. Billing needs a machine-to-machine credential that a Tenant User can create, rotate, and revoke inside Billing, without a Keycloak Admin API dependency, while Billing → Tenant webhooks remain verifiable.

**Exact requirements**

1. Each Tenant System has its own credential bound to `tenant_system_id`; credentials are never shared across Tenants or Tenant Systems.
2. A Tenant User (a Billing User with Tenant Scope) creates, rotates, and revokes the credential in Tenant Administration; the Tenant System never creates its own credential.
3. Billing derives Tenant and Tenant System from the authenticated credential, never from the hostname or a client-supplied `tenant_id`.
4. The secret is stored as a hash and shown only once; it never appears in logs or read responses.
5. Tenant → Billing uses a bearer API key over TLS plus an `Idempotency-Key`; per-request HMAC is not required.
6. Billing → Tenant webhooks are signed with HMAC-SHA256 over `timestamp + raw body`, carry a key id, reject stale timestamps, and deduplicate event ids.
7. Credentials carry scopes and a rate limit; create, rotate, and revoke are audited with `created_by`.

**Goals.** Tenant Users manage credentials for their own Tenant Systems; no cross-system provisioning dependency; low integration effort for third parties; security adequate for financial data.

**Done means**

- The decision is recorded in `docs/architecture.md` and `docs/authentication-and-authorization.md`.
- The credential lifecycle is documented: create, rotate with overlap, revoke, prefix/last4 display, hash at rest, `created_by`.
- A Tenant User creates a credential in Tenant Administration; the Sample Tenant uses it to complete reservation → charge → webhook end to end.
- Tests prove: invalid key → 401; revoked key → 401; missing idempotency key → 400; bad webhook signature or stale timestamp → rejected; two Tenants cannot see each other's data.
- No tenant or credential provisioning path calls the Keycloak Admin API.

**Non-goals.** Implementation in this step; building an OAuth2 authorization server; mTLS; authenticating a Tenant System's own end users; replacing Keycloak for human Billing Users.

**Constraints.** Keycloak remains the IdP for Billing Users; one Tenant may integrate several Tenant Systems; external Tenants do not control your infrastructure; the specification requires encrypted credentials and secret-management support; idempotency is mandatory.

## What you should do

1. Define an `integration_credential` record: `id`, `tenant_id`, `tenant_system_id`, `key_prefix`, `key_last4`, `secret_hash`, `scopes`, `status`, `created_by`, `created_at`, `last_used_at`, `rotated_at`, `revoked_at`, optional `expires_at`.
2. Generate keys as `cb_live_<random>` / `cb_test_<random>` with at least 32 bytes of entropy. Keep the prefix for indexed lookup and the last four characters for display; show the full secret once.
3. Put credential management in Tenant Administration behind a permission such as `integration_credential:manage`. Create, rotate with an overlap window, and revoke immediately.
4. Authenticate with a filter that parses the bearer token, looks up by prefix, compares the hash in constant time, checks status, expiry, scope, and rate limit, then builds Tenant Context from `tenant_system_id`.
5. Audit create, rotate, revoke, and authentication failures; update `last_used_at` asynchronously.
6. Sign outbound webhooks per endpoint with a **separate** signing secret: `X-Signature: HMAC-SHA256(timestamp + "." + raw_body)`, plus `X-Timestamp` and `X-Key-Id`. Reject timestamps older than five minutes and deduplicate by event id.
7. Record the decision in the architecture ledger, replace the M2M gap in the auth document, and close the related open decision.
8. Wire the Sample Tenant: configure the key, add the bearer header and idempotency key to its client, and verify webhook signatures in its endpoint.

## What you shouldn't do

- Do not create one Keycloak client per Tenant System for M2M. It reintroduces the cross-system transaction and the realm-admin credential this decision avoids.
- Do not require HMAC on every inbound request. TLS plus a bearer key plus idempotency is sufficient, and inbound canonical signing creates integrator friction and canonicalization bugs.
- Do not store raw keys or use reversible encryption when a hash is enough. Do not log `Authorization` headers.
- Do not reuse the API key as the webhook signing secret. Separate secrets keep one leak from compromising both directions.
- Do not expose credential creation to the Tenant System. It is a Tenant User action in Billing.
- Do not delete an old key the moment a new one is issued. Support an overlap window so tenants can deploy without downtime.
- Do not build an OAuth2 authorization server inside Billing.

## What could be better / more efficient

Ranked by effort-to-impact:

1. **Test-mode key prefix.** `cb_test_` lets tenants integrate safely without touching live money. Low effort, high support savings.
2. **Credential introspection.** Return `tenant_system_id`, scopes, and environment on a `/whoami`-style call so integrators debug themselves.
3. **Scoped permissions.** Issue narrow scopes (for example usage write, charge read) instead of full tenant scope. Low effort, real containment.
4. **Shared webhook-verification helper.** Ship one small library or code sample so the Sample Tenant and real tenants verify signatures identically.
5. **Async `last_used_at`.** Avoid a write on every request; batch or sample it.

## My take and how to get there

Record the decision now, while the repo has no code, then build the credential seam before the first integration endpoint. The route from current state to goal:

1. Write the decision and a short threat model into the docs.
2. Add the credential schema and migration.
3. Build issuance, rotation, and revocation in Tenant Administration with audit.
4. Add the bearer filter, Tenant Context, scope checks, and rate limit.
5. Add webhook signing on the Billing side and a verification helper shared with the Sample Tenant.
6. Prove it end to end with the Sample Tenant and the negative tests listed under Done means.
7. Write the rotation, revocation, and key-leak runbook, plus metrics and alerts.

## Benefits

- Tenant onboarding stays a local Billing transaction; no Keycloak Admin API and no saga.
- External Tenant Systems need no membership in your IdP and can integrate with a bearer header.
- Revocation is immediate and entirely inside Billing.
- The model matches the specification's "Billing manages Integration Credentials" and credential-encryption principles.
- Integrators recognize the Stripe-style split: secret key for the API, signing secret for webhooks.
- Blast radius stays per Tenant System, which supports the one-Tenant-many-systems model.

## Trade-offs

- **Long-lived secrets.** A leaked key stays valid until revoked. Mitigate with scopes, rotation overlap, rate limits, audit, anomaly alerts, and a tested leak-response runbook.
- **No short-lived tokens.** You lose automatic expiry and delegated, per-request authorization. If a tenant needs that later, you add a token layer and migrate credentials.
- **Custom auth code.** You own key generation, hashing, comparison, rotation, and revocation instead of relying on OAuth libraries.
- **Inbound relies on TLS.** HMAC is not applied to Tenant → Billing requests by the confirmed decision. If TLS termination is weak or requests cross untrusted intermediaries, revisit this.
- **Integrator secret storage.** Tenants must store the key securely; this is their operational burden, not yours.
- **When this stops being right:** many tenants demand OIDC, or compliance requires short-lived credentials, or you need per-request delegated authorization. Switching then means adding Keycloak client provisioning automation, a `client_id → tenant_system_id` mapping, and a credential migration; the API auth filter and Tenant Context stay largely unchanged.

## Unverified

- Keycloak version and features are not specified in the repository; the docs mention OIDC only. Confirm the version before relying on Organizations or dynamic client registration.
- No application code exists, so the schema, Spring Security filter, and hashing choices are proposals, not verified implementation.
- TLS termination and any ingress mTLS policy are not documented; the recommendation assumes TLS is enforced end to end to the Billing API.
- Rate-limiting infrastructure is not specified; the per-credential limit assumes an existing gateway or filter mechanism.

## Work checklist

- [ ] Record the decision in `docs/architecture.md` and update `docs/authentication-and-authorization.md`; close the M2M open decision.
- [ ] Define the `integration_credential` schema and migration.
- [ ] Implement key generation, prefix/last4 display, hash at rest, and one-time secret reveal.
- [ ] Add the `integration_credential:manage` permission and Tenant Administration endpoints for create, rotate, and revoke.
- [ ] Implement the bearer auth filter with constant-time comparison, Tenant Context, scopes, and rate limit.
- [ ] Implement outbound webhook signing with timestamp, key id, and replay rejection.
- [ ] Ship a webhook-verification helper and use it in the Sample Tenant.
- [ ] Wire the Sample Tenant HTTP client with the bearer key and `Idempotency-Key`.
- [ ] Add negative tests: invalid key, revoked key, missing idempotency key, stale or bad webhook signature, duplicate event, cross-tenant access.
- [ ] Write the rotation/revocation/leak runbook and add metrics and alerts.

## Success metrics

- A Tenant User creates a credential and the Sample Tenant completes reservation → charge → webhook in under 10 minutes of manual setup.
- Zero Keycloak Admin API calls during tenant or credential creation, verified in logs.
- 100% of protected endpoints return 401 for missing, invalid, or revoked keys in the test suite.
- 100% of cross-tenant isolation tests return 403 or 404.
- 100% of webhook tests reject a bad signature and a stale timestamp; duplicate event ids are processed once.
- Authentication lookup adds less than 5 ms at p95 in a local benchmark.

## Handoff brief

```text
Objective: Adopt Billing-issued per-Tenant-System API keys (bearer over TLS) for Tenant → Billing, with HMAC-SHA256 only for Billing → Tenant webhooks, and record the decision in the docs.
Done means: a Tenant User can create, rotate, and revoke a credential in Tenant Administration; the Sample Tenant completes reservation → charge → webhook with that key; invalid, revoked, and cross-tenant requests are rejected; bad or stale webhook signatures are rejected and duplicate events are processed once; no tenant or credential provisioning path calls the Keycloak Admin API; the decision is recorded in docs/architecture.md and docs/authentication-and-authorization.md.
Out of scope: implementing an OAuth2 authorization server, mTLS, authenticating Tenant System end users, replacing Keycloak for human Billing Users, and any payment-provider work.
Constraints: Keycloak remains the IdP for Billing Users; one Tenant may integrate several Tenant Systems; credentials are hashed at rest and shown once; idempotency is mandatory; webhook signing uses a secret separate from the API key.
Stop and ask only if: a failure cannot be explained, an action would delete data or rewrite shared history, work would leave the confirmed scope, or a Keycloak version limitation makes the chosen model impossible.
```
