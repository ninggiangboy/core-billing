---
phase: 1
title: "Shared replay and constraint helpers"
status: completed
priority: P1
effort: "1h"
dependencies: []
---

# Phase 1: Shared replay and constraint helpers

## Goal

Give every keyed flow one way to decide "replay or conflict" and one way to recognise a violation of a
named unique constraint, so later phases do not repeat either rule.

## Context

Nine use cases repeat `if (!existing.requestHash().equals(hash)) throw new IdempotencyConflictException(scope, key)`.
Repositories catch every `DuplicateKeyException` the same way, so `JooqLedgerRepository.append` cannot tell
a sequence clash from a key clash. `Causes` (`libs/shared-kernel`, JDK-only) already walks cause chains.

## Files to Create / Modify

- Modify: `billing/contexts/platform/src/api/java/dev/ngb/core_billing/platform/api/idempotency/Fingerprint.java`
- Modify: `billing/libs/shared-kernel/src/main/java/dev/ngb/core_billing/sharedkernel/util/Causes.java`
- Create or extend: unit tests next to each class

## Tasks & Steps

- [x] 1. Add `Fingerprint.requireSameRequest(String scope, String key, String storedHash, String requestHash)`.
      It returns normally when the hashes match and throws `IdempotencyConflictException(scope, key)` otherwise.
- [x] 2. Add `Causes.violates(Throwable, String constraint)`. It is true when a `SQLException` in the chain has
      SQLState `23505` and its message names the constraint in double quotes, as PostgreSQL reports it.
      It stays JDK-only, with no driver type.
- [x] 3. Replace the existing hash comparisons in the use cases with `requireSameRequest`: charge, reservation,
      `LedgerPosting`, usage, top-up, invoice payment, refund and subscription. Behaviour must not change in
      this phase.
- [x] 4. Unit tests: `requireSameRequest` for match and mismatch; `violates` for a matching constraint, another
      constraint, another SQLState and a wrapped cause.

## Verification

- `./gradlew :contexts:platform:test :libs:shared-kernel:test`
- `./gradlew :apps:app-boot:test --tests '*MoneyIntegrationTest' --tests '*UsageIntegrationTest*'` stays green.

## Risks and rollback

Pure refactor. Revert the commit.
