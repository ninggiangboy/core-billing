---
title: Renamed the billing and tenant identifiers
date: 2026-11-12
summary: "Mechanical rename: dev.ngb.core_billing to dev.ngb.billing, dev.ngb.sampletenant to dev.ngb.tenant, sample-tenant/ to tenant/, and the runtime DB, role, realm, audience and schema ids that follow. Five green commits."
---

# Renamed the billing and tenant identifiers

## What happened
- Renamed the packages and every identifier the compiler cannot see, in five commits on `refactor/rename-identifiers`: billing, tenant, runtime config, CI, docs.
- `dev.ngb.core_billing` became `dev.ngb.billing`, `dev.ngb.core_billing_jooq` became `dev.ngb.billing_jooq`, `dev.ngb.sampletenant` became `dev.ngb.tenant` and `dev.ngb.sampletenant_jooq` became `dev.ngb.tenant_jooq`.
- `sample-tenant/` became `tenant/`; both build root project names, the boot main classes, the tenant config prefix, the compose services and volumes, the CI workflow file, `docs/sample-tenant-system.md` and its links followed.
- Databases and roles became `billing`, `billing_app` and `tenant`; the Keycloak realm and OIDC audience became `billing`; the 19 event schema `$id` hosts became `billing.io`.
- Migrations V1, V2, V6-V9 and V16 were edited in place; jOOQ code was regenerated with `JooqSchemaTest -Djooq.update=true`, and the diff is only the package rename.

## Verification
- `billing`: `./gradlew clean build` green at HEAD.
- `tenant`: `./gradlew clean checkNoBillingDependency build` green at HEAD, 217 tests.
- `core`: `./gradlew build` green.
- Done-means grep for `core_billing|corebilling|sampletenant|sample-tenant|core-billing` outside `plans/` returns only the protected `plans/reports/...-core-billing-contract.md` link in `docs/README.md`.
- Fresh `docker compose down -v && up`, `migrate` profile: database `billing` with role `billing_app`, database `tenant`, realm `billing` imported; a password-grant token carried `aud: ["billing", ...]` with issuer `/realms/billing`.
- Compose volumes and the throwaway `.env` were removed after the smoke test.

## Deviations
- `ops/keycloak/realm-export.json` moved in the billing commit, not the runtime commit: `KeycloakFixture` reads it, so billing's integration tests need the realm renamed to stay green.
- Generated jOOQ sources were token-renamed before the regeneration run only so the module would compile; the committed files are the generator's output, verified by the non-update `JooqSchemaTest`.
- Empty `sampletenant` package directories had no tracked files, so they were removed instead of moved.
- `SAMPLE_TENANT_*` variable names keep their old spelling, per the plan.

## Next steps
- `tenant` now names both the reference app and the platform concept, so docs and review must stay explicit about which is meant.
- Renaming `SAMPLE_TENANT_*` is separate work touching `.env.example`, compose and `application.yml`.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
