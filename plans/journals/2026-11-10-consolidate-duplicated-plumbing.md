---
title: Consolidate duplicated plumbing and align the drifting rules
date: 2026-11-10
---

# Consolidate duplicated plumbing and align the drifting rules

## Outcome

- One audit type: `platform.api.audit.Audit(String actor, String requestId)` with
  `Audit.of(actor, suppliedRequestId)` as the only request-id sanitiser. It
  replaces three byte-identical `Audit` records (pricing, payment, webhook),
  `customer`'s `AuditContext` and `platform`'s `Caller`, and six copies of the
  64-character printable-ASCII check.
- `CurrentActor.caller()` now owns the tenant-system narrowing that four
  controllers each wrote privately.
- `platform.api.idempotency.Fingerprint` is the one SHA-256 digest and the one
  `Idempotency-Key` rule. Payment's 200-character limit is gone: every context
  now accepts 1-255 printable characters, so a 220-character key that used to be
  rejected at `/payments/top-ups` is accepted there too.
- `sharedkernel.Causes`, `sharedkernel.Text` and `sharedkernel.PageSize` replace
  three cause-chain walks, five `clip` helpers (one of which threw on null) and
  four page-size constants with three different clamp shapes.
- `sharedkernel.MoneyDto` is the single `{amount, currency}` wire shape;
  `AmountDto` is gone from the pricing responses and from both OpenAPI files.
- `money`'s `Rows.duplicate` was dead code and is removed.

## Verification

- `./gradlew build` green: 318 tests, including ArchUnit (47 rules across two
  suites), Spring Modulith, the OpenAPI drift test and every HTTP integration
  test.
- New `PaymentHttpIntegrationTest.aKeyOfUpTo255CharactersIsAccepted` proves the
  key limit is now uniform: 220 characters returns 201, 256 returns 400.
- `MoneyHttpIntegrationTest` now asserts `limit=0` is raised to 1 instead of
  rejected with 400, matching the single clamp everywhere else.
- Both OpenAPI files regenerated: the only change is `AmountDto` ->
  `MoneyDto` on three pricing responses, so the wire shape is unchanged.

## Notes

- Deliberately not merged: the 19 UUID identifier records (nominal typing is the
  point), the `of`/`ofView` twins (ArchUnit forbids a read model from naming a
  domain aggregate, so they map different types), `Rows.from`/`toDomain`
  (a `RowMapper` is banned) and the `@Bean` wiring in `app-boot`.
- `Causes` moved from `platform.adapter.persistence` to `shared-kernel`: the
  cross-context rule only allows reaching another context through its `api`
  package, so `money` and `customer` could not have used the adapter copy.
- Fingerprints already stored in `payment`, `refund`, `usage_event` and
  `idempotency_keys` keep their exact bytes: `Fingerprint.sha256` preserves the
  old canonical form where a caller's field order or NUL placement differed.
