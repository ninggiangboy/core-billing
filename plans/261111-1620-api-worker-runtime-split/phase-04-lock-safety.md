---
phase: 4
title: "Lock safety net and documentation"
status: pending
---

# Lock safety net and documentation

## Goal

A lock wait fails predictably instead of becoming an opaque 500, and the lock hierarchy is written
down and guarded.

## Work

- Map `40P01` (deadlock) and `55P03` (lock timeout) on the API to 503 with `Retry-After`; jobs treat
  them as a skipped round and retry on the next tick.
- Set `lock_timeout` and `idle_in_transaction_session_timeout` per profile (api long, worker short,
  migrate short with retry).
- `docs/concurrency-and-locking.md`: the observed lock order, the lease rules, the rate-limit rule,
  the invoice counter as the last lock, and the safety net. Link it from `docs/README.md` and the
  reliability doc.
- A test fails if a blocking advisory lock (`pg_advisory_lock` / `pg_advisory_xact_lock`) appears in
  sources or migrations; only the `try` variants are allowed.

## Verification

- Exception-handler test returns 503 and `Retry-After` for both SQLSTATEs.
- Lock hierarchy test passes and fails on an injected blocking advisory lock string.
- Docs build has no broken relative link.
