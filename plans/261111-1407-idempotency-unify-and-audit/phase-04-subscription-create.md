---
phase: 4
title: "Subscription create"
status: completed
priority: P2
effort: "1.5h"
dependencies: [1]
---

# Phase 4: Subscription create

## Goal

Racing duplicates of a subscription create replay the first one instead of answering 409 "already used".

## Context

`CreateSubscriptionUseCase` reads the key at line 70 and inserts at line 87, with no lock in between.
`JooqSubscriptionRepository.add` maps every `DuplicateKeyException` to "this idempotency key was already used
for a subscription". The first-period renewal runs in its own transaction after the create commits (lines 93-98).
`SubscriptionController` already answers 201 for new and 200 for replay.

## Files to Create / Modify

- Modify: `billing/contexts/invoicing/src/application/.../subscription/port/SubscriptionRepository.java`
- Modify: `billing/contexts/invoicing/src/adapter/.../subscription/persistence/JooqSubscriptionRepository.java`
- Modify: `billing/contexts/invoicing/src/application/.../subscription/command/CreateSubscriptionUseCase.java`
- Modify: `billing/contexts/invoicing/src/test/.../subscription/command/RenewSubscriptionUseCaseTest.java` (its in-memory repository implements the port)
- Modify test: `billing/apps/app-boot/src/test/java/dev/ngb/core_billing/invoicing/SubscriptionIdempotencyIntegrationTest.java`

## Tasks & Steps

- [x] 1. `addIfAbsent` with `on conflict (tenant_id, idempotency_key) do nothing`. Keep the `DuplicateKeyException`
      mapping for any other unique constraint on the table, with a message that no longer mentions the key.
- [x] 2. Use case: insert-first. On `false`, read by key, call `requireSameRequest` and take the existing replay
      path, which skips the first-period renewal. Write the audit entry only on a real insert.
- [x] 3. Tests: 8 parallel creates with one key give one subscription, one first-period charge, one 201 and seven 200s.
      The same key with a different plan gives 409.

## Verification

- `./gradlew :contexts:invoicing:test`
- `./gradlew :apps:app-boot:test --tests '*Subscription*'`

## Risks and rollback

A replay that arrives while the winner's first-period renewal is still running returns the subscription before
its first charge. That is the same state a sequential replay sees today, so it is accepted. Code-only: revert the commit.
