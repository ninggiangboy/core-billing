# Authentication and Authorization

Keycloak authenticates. Billing authorizes.

## Authentication

Keycloak is the shared Identity Provider for the whole Platform.

- A single shared Keycloak Realm.
- Integration through OAuth 2.0 and OpenID Connect.
- Keycloak owns authentication, credentials and login session management.
- Billing uses the OIDC Subject (`sub`) to link to its internal User.
- A User's identity is distinguished per Tenant. Email is never the sole identifier across the Platform, and the same email may exist in several Tenants under independent identities.

Keycloak must be configured to allow duplicate emails. Usernames are unique within a Realm by default, so the login identifier is the Tenant-scoped username `{tenant-code}:{email}`, built by the Billing UI from the hostname. Platform Users use the reserved code `platform`. The Realm is configured by `ops/keycloak/realm-export.json`.

Every token carries a `tenant_code` claim, set from a user attribute that only an administrator can edit. Billing accepts a token only when that claim equals the Tenant the request hostname resolved to; a mismatch is a 403, so a session for one Tenant cannot be used in another. Billing creates the Keycloak identity when it creates a user, and removes it again if storing the user fails.

## Billing Users

A Billing User is an account used to access and administer Billing.

- **Platform User** administers the Platform.
- **Tenant User** administers and operates Billing within one Tenant. Not to be confused with a Tenant System's business user, see [customers-and-tenant-mapping.md](customers-and-tenant-mapping.md).
- The same email in several Tenants gives independent accounts.
- Each User is linked to a Keycloak identity through the OIDC Subject.
- Roles and Permissions are managed by Billing.

## Authorization: Hybrid RBAC

| Part | Meaning |
|---|---|
| User | Account of a person administering Billing |
| Role | Customizable group of Permissions |
| Permission | The right to perform one specific action |
| Role Assignment | Assigns a Role to a User within a Scope |
| Role Permission | Which Permissions belong to a Role |
| User Permission | A Permission granted directly to a User |
| Authorization Policy | Checks access from Permission, Scope and extra conditions when needed |

Two scopes exist:

- **Platform Scope** administers the whole Platform and its Tenants.
- **Tenant Scope** administers data and operations inside one Tenant.

A User can hold several Roles and Permissions in different Scopes.

Roles are managed dynamically through the database and the UI and are never hardcoded. The system may ship some default Roles when a Platform or Tenant is initialized.

## Tenant Systems (machine to machine)

A Tenant System is a third party that self-serves and does not share the Platform's identity provider, so it does not authenticate through Keycloak.

- Billing issues the credential, one per Tenant System, bound to `tenant_system_id`. A Tenant User creates, rotates and revokes it in Tenant Administration; a Tenant System never creates its own credential.
- Tenant System → Billing uses a bearer secret over TLS plus an `Idempotency-Key`. Scopes and a rate limit attach to the credential.
- Billing derives the Tenant and the Tenant System from the credential, never from the hostname or a client-supplied `tenant_id`.
- The credential's scopes bound what it may call; ownership bounds what it may see. A Tenant System reaches only the Customers it mapped, and the wallets, reservations, charges, payments, usage and ratings that belong to those Customers. A webhook endpoint belongs to the Tenant System that registered it. A resource owned by another Tenant System in the same Tenant answers 404, exactly like a resource that does not exist. A Tenant User keeps the tenant-wide view.
- The operations a Tenant System may call are published in [contracts/openapi/billing-tenant-system.yaml](../contracts/openapi/billing-tenant-system.yaml), generated from the same code as the full contract. Each operation carries its required permission and its allowed callers.
- The secret is hashed at rest, shown once, never logged and never returned by a read API. Rotation supports an overlap window; revocation is immediate. Create, rotate and revoke are audited.
- Billing → Tenant System Webhooks are signed with a secret separate from the API credential, see [webhooks-and-events.md](webhooks-and-events.md).
- Rejected: a Keycloak client per Tenant System, inbound HMAC on every request, and reuse of the API credential as the Webhook signing secret.

## Token and session checks

The token checks (issuer, audience, expiry, subject) and the rule that a session from one Tenant cannot be used in another are listed in [security-and-reliability.md](security-and-reliability.md).
