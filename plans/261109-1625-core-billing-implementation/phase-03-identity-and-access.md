---
phase: 3
title: "Identity and access"
status: pending
priority: P1
effort: "6d"
dependencies: [2]
---

# Phase 3: Identity and access

## Goal

Authenticate Billing Users through Keycloak, authorize them with Billing-owned Hybrid RBAC, and authenticate Tenant Systems with Billing-issued API keys, all bound to the Tenant Context.

## Context

Source: [authentication-and-authorization.md](../../docs/authentication-and-authorization.md), `plans/reports/advise-261109-2227-tenant-system-auth.md`. Keycloak does authentication only; Billing links the OIDC `sub` to its own User. Resolve open decision 1 here (Keycloak login identifier); the default is the username `{tenant-code}:{email}`.

## Files to Create / Modify

- Create: in `billing/platform/`: `BillingUser`, `Role`, `Permission` catalog, `RoleAssignment`, `UserPermission`, `AuthorizationPolicy`, `TenantSystem`, `ApiCredential`
- Create: adapters: OIDC resource-server config, Keycloak admin client (`IdentityProviderPort`), API key filter, rate limiter behind a port
- Create: Flyway `V3__identity.sql` (users, roles, role_permissions, role_assignments, user_permissions, tenant_systems, api_credentials)
- Create: `ops/keycloak/realm-export.json` and Keycloak service in `docker-compose.yml`
- Create: REST controllers for tenants, domains, users, roles, tenant systems and credentials; OpenAPI export into `contracts/openapi/billing.yaml`

## Tasks & Steps

- [ ] 1. Configure the Keycloak realm: duplicate emails allowed, login by username only, a protocol mapper adding a `tenant_code` claim from a user attribute, a public PKCE client for the web apps.
- [ ] 2. Validate tokens: issuer, audience, expiry, subject. The `tenant_code` claim must equal the tenant resolved from the hostname; a mismatch is a 403, so a session for tenant A fails on tenant B.
- [ ] 3. Link `sub` to `BillingUser` per tenant. Platform Users have a Platform scope; Tenant Users have a Tenant scope. The same email in two tenants yields two Users and two Keycloak identities.
- [ ] 4. Hybrid RBAC: Permission catalog defined in code and seeded; Roles, Role Permissions, Role Assignments (with scope) and direct User Permissions stored in the database; an `AuthorizationPolicy` answers "may actor do action in scope". Seed default roles when a Platform or Tenant is initialized.
- [ ] 5. User provisioning use case calls `IdentityProviderPort` (Keycloak admin API adapter) to create the Keycloak identity with the tenant-scoped username, then stores the link.
- [ ] 6. Tenant System credentials: key format `cbk_<key-id>_<secret>`, 32 random bytes of secret, store key id and SHA-256 of the secret, constant-time comparison, scopes per credential, tenant and tenant system derived from the credential only. The secret is shown once on create and rotate, never logged, never returned by read APIs.
- [ ] 7. Rotation with an overlap window (two active credentials per tenant system for a bounded time), immediate revocation, and audit entries for create, rotate and revoke.
- [ ] 8. Rate limit per credential behind a `RateLimiter` port, in-memory token bucket for now, documented as approximate across instances.
- [ ] 9. One security filter chain resolving the principal from one of three sources: OIDC bearer (admin), API key (Tenant System) and, in phase 10, Billing Session. The Tenant Context is built from the principal plus hostname checks, once.
- [ ] 10. REST: tenants and domains (Platform), users and roles (Platform or Tenant), tenant systems and credentials (Tenant). Every endpoint declares its required Permission, enforced by the policy, with a test per endpoint.
- [ ] 11. Export the OpenAPI document to `contracts/openapi/billing.yaml` and add a CI check that the committed file matches the generated one.

## Verification

- Integration tests (Testcontainers PostgreSQL and Keycloak): login works for the same email in two tenants; a token from tenant A is rejected on tenant B's hostname; a user without a Permission gets 403 on its endpoint; a revoked credential fails on the next call; a rotated credential works during overlap and fails after it.
- A test greps responses and log output captured during credential tests for the secret and finds nothing.
- Timing test is not required; the code review checklist includes constant-time comparison.
- `./gradlew build` stays green with all architecture rules.

## Risks and rollback

- The `{tenant-code}:{email}` scheme leaks the tenant code in usernames; acceptable because the code is already in the hostname. Switching to a custom authenticator later changes only the Keycloak adapter and realm export.
- If Keycloak admin calls fail during user creation, the use case must not leave a half-created user: create the Keycloak identity first, store the link second, and compensate by deleting the identity on a storage failure.
