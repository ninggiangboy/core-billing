---
phase: 2
title: "Customer key on the row, store removed"
status: pending
priority: P1
effort: "4h"
dependencies: [1]
---

# Phase 2: Customer key on the row, store removed

## Goal

`customers` carries `idempotency_key` and `request_hash`, creation is insert-first, a replay returns 200,
and the generic `IdempotencyStore` with its `idempotency_keys` table no longer exists.

## Context

- `CreateCustomerUseCase` is the only consumer of `IdempotencyStore`. It uses scope `customer.create`,
  stores the new customer id as `response_body`, and hashes the request with its private `fingerprint`
  (`Fingerprint.sha256` of the NUL-joined fields).
- `Customer.create` is called only by this use case, so every existing customer row should have a store entry.
- Every tenant table uses `force row level security`, which applies to the migration owner too. A backfill
  `update` run without lifting it sees no rows.
- The latest migration is `V17__job_runs_finished_at.sql`.

## Files to Create / Modify

- Create: `billing/apps/app-boot/src/main/resources/db/migration/V18__customer_idempotency_key.sql`
- Create: `billing/apps/app-boot/src/main/resources/db/migration/V19__drop_idempotency_keys.sql`
- Modify: `billing/libs/jooq-schema/**` (regenerated)
- Modify: `billing/contexts/customer/src/domain/.../Customer.java` (key and hash on create and restore)
- Modify: `billing/contexts/customer/src/application/.../port/CustomerRepository.java` (`addIfAbsent`, `findByIdempotencyKey`)
- Modify: `billing/contexts/customer/src/adapter/.../persistence/JooqCustomerRepository.java`
- Modify: `billing/contexts/customer/src/application/.../command/CreateCustomerUseCase.java`
- Modify: `billing/contexts/customer/src/adapter/.../web/CustomerController.java`
- Modify: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/config/CustomerConfiguration.java`
- Modify: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/infrastructure/InfrastructureConfiguration.java`
- Delete: `billing/contexts/platform/src/api/java/dev/ngb/core_billing/platform/api/idempotency/IdempotencyStore.java`
- Delete: `billing/contexts/platform/src/api/java/dev/ngb/core_billing/platform/api/idempotency/StoredResponse.java`
- Delete: `billing/apps/app-boot/src/main/java/dev/ngb/core_billing/boot/infrastructure/persistence/JooqIdempotencyStore.java`
- Modify tests: `UnitOfWorkIntegrationTest` (drop the store tests and keep its transaction and RLS tests),
  `CustomerIntegrationTest:143`, `PricingUsageIntegrationTest:311`, `JooqSchemaTest:74`, `ArchitectureRules:305`,
  `CustomerDomainTest`
- Create test: a migration upgrade test in `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/customer/`

## Tasks & Steps

- [ ] 1. Back up any database that holds data before running V18 and V19 on it.
- [ ] 2. `V18`: add nullable `idempotency_key text` and `request_hash text` to `customers`. Inside the migration,
      lift the forced RLS on `customers` and `idempotency_keys` (`alter table … no force row level security`).
      Backfill from `idempotency_keys` where `scope = 'customer.create'`, joining `response_body::uuid = customers.id`
      and matching `tenant_id`. Give any customer without an entry the key `legacy:<id>` and the hash `legacy`,
      so the next step cannot fail. Restore `force row level security`. Set both columns `not null` and add
      `unique (tenant_id, idempotency_key)`.
- [ ] 3. `V19`: `drop table idempotency_keys`.
- [ ] 4. Regenerate jOOQ: `./gradlew :apps:app-boot:test --tests '*JooqSchemaTest' -Djooq.update=true`.
      Remove `IdempotencyKeys` from the shared-table list in `JooqSchemaTest`.
- [ ] 5. `Customer`: carry `idempotencyKey` and `requestHash` the way `Charge` does. Keep the hash function
      byte-for-byte identical (move `fingerprint` unchanged), so backfilled hashes still match.
- [ ] 6. `JooqCustomerRepository.addIfAbsent`: `on conflict (tenant_id, idempotency_key) do nothing`, returning
      whether a row was inserted. Keep the existing `DuplicateKeyException` mapping for the external-user
      mapping constraint.
- [ ] 7. `CreateCustomerUseCase`: insert-first. On `false`, read by key, call `Fingerprint.requireSameRequest`
      and return `replayed = true`. Publish `CustomerCreated`, write the audit entry and insert the external
      mapping only when the row was inserted. Remove the store dependency.
- [ ] 8. `CustomerController.create`: return `ResponseEntity` with 201 for new and 200 for replay; update the javadoc.
- [ ] 9. Delete the store types, the bean and its wiring. Update the ArchUnit table-ownership rule so
      `idempotency_keys` is no longer listed.
- [ ] 10. Tests:
       - Migration upgrade: migrate a fresh database to V17, seed a tenant, a customer and its `idempotency_keys`
         row with `app.tenant_id` set, migrate to latest, then assert the customer has that key and hash and that
         replaying the original request returns the same customer with 200.
       - 8 parallel same-key creates give one customer, one 201 and seven 200s.
       - Same key with a different body gives 409.
       - Replace the `idempotency_keys` row-count assertion in `CustomerIntegrationTest` with a check on `customers`.
- [ ] 11. Regenerate the API contract: `./gradlew :apps:app-boot:test --tests '*OpenApiContractTest' -Dopenapi.update=true`.

## Verification

- `./gradlew :contexts:customer:test`
- `./gradlew :apps:app-boot:test --tests '*Customer*' --tests '*UnitOfWorkIntegrationTest' --tests '*JooqSchemaTest' --tests '*ArchitectureRules*' --tests '*OpenApiContractTest'`
- `grep -rn "IdempotencyStore\|StoredResponse\|IDEMPOTENCY_KEYS" billing --include='*.java'` returns nothing.

## Risks and rollback

- If the backfill matches nothing because RLS was not lifted, every customer gets a `legacy:` key and old keys
  stop replaying. The upgrade test catches this; do not skip it.
- V19 is destructive. Restore from the backup taken in step 1 if the backfill must be redone.
