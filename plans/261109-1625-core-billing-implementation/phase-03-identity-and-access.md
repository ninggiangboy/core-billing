---
phase: 3
title: "Identity and access"
status: completed
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

- [x] 1. Configure the Keycloak realm: duplicate emails allowed, login by username only, a protocol mapper adding a `tenant_code` claim from a user attribute, a public PKCE client for the web apps.
- [x] 2. Validate tokens: issuer, audience, expiry, subject. The `tenant_code` claim must equal the tenant resolved from the hostname; a mismatch is a 403, so a session for tenant A fails on tenant B.
- [x] 3. Link `sub` to `BillingUser` per tenant. Platform Users have a Platform scope; Tenant Users have a Tenant scope. The same email in two tenants yields two Users and two Keycloak identities.
- [x] 4. Hybrid RBAC: Permission catalog defined in code and seeded; Roles, Role Permissions, Role Assignments (with scope) and direct User Permissions stored in the database; an `AuthorizationPolicy` answers "may actor do action in scope". Seed default roles when a Platform or Tenant is initialized.
- [x] 5. User provisioning use case calls `IdentityProviderPort` (Keycloak admin API adapter) to create the Keycloak identity with the tenant-scoped username, then stores the link.
- [x] 6. Tenant System credentials: key format `cbk_<key-id>_<secret>`, 32 random bytes of secret, store key id and SHA-256 of the secret, constant-time comparison, scopes per credential, tenant and tenant system derived from the credential only. The secret is shown once on create and rotate, never logged, never returned by read APIs.
- [x] 7. Rotation with an overlap window (two active credentials per tenant system for a bounded time), immediate revocation, and audit entries for create, rotate and revoke.
- [x] 8. Rate limit per credential behind a `RateLimiter` port, in-memory token bucket for now, documented as approximate across instances.
- [x] 9. One security filter chain resolving the principal from one of three sources: OIDC bearer (admin), API key (Tenant System) and, in phase 10, Billing Session. The Tenant Context is built from the principal plus hostname checks, once.
- [x] 10. REST: tenants and domains (Platform), users and roles (Platform or Tenant), tenant systems and credentials (Tenant). Every endpoint declares its required Permission, enforced by the policy, with a test per endpoint.
- [x] 11. Export the OpenAPI document to `contracts/openapi/billing.yaml` and add a CI check that the committed file matches the generated one.

## Notes

- Migration numbers shifted: `V3__platform_tenant.sql` (the Platform as a reserved tenant) and `V4__identity.sql`; later phases continue from V5.
- Modules now have a fourth, lowest source set `api` (kernel only), because the domain needs `Permission`, `PermissionScope` and `TenantSystemId`, which also belong to the published contract. `docs/architecture.md` records it.
- The Platform owns its data as a reserved tenant (fixed id, code `platform`), so identity tables use the standard RLS template and composite foreign keys. A Platform Administrator cannot read tenant data; acting inside a tenant's explicit, audited context is not built yet.
- Keycloak 26.8 requires the `kc-admin-api` client scope for its Admin API; the `billing-admin` client in `ops/keycloak/realm-export.json` carries it.
- A Tenant System request must arrive on its tenant's hostname; the credential is looked up inside that tenant, so a key from another tenant is indistinguishable from an unknown key.
- `ProvisionTenantUseCase` creates the first Tenant administrator and default roles from inside the new tenant's scope; `BILLING_BOOTSTRAP_ADMIN_EMAIL` creates the first Platform administrator.
- Open for phase 12: the `billing-web` client lists localhost redirect URIs only; each new tenant hostname needs its redirect URI registered (by Billing or by a wildcard policy).
- Committed default passwords were removed; `.env.example` lists the variables and `docker-compose.yml` requires them.

## Verification

- Integration tests (Testcontainers PostgreSQL and Keycloak): login works for the same email in two tenants; a token from tenant A is rejected on tenant B's hostname; a user without a Permission gets 403 on its endpoint; a revoked credential fails on the next call; a rotated credential works during overlap and fails after it.
- A test greps responses and log output captured during credential tests for the secret and finds nothing.
- Timing test is not required; the code review checklist includes constant-time comparison.
- `./gradlew build` stays green with all architecture rules.

## Risks and rollback

- The `{tenant-code}:{email}` scheme leaks the tenant code in usernames; acceptable because the code is already in the hostname. Switching to a custom authenticator later changes only the Keycloak adapter and realm export.
- If Keycloak admin calls fail during user creation, the use case must not leave a half-created user: create the Keycloak identity first, store the link second, and compensate by deleting the identity on a storage failure.
