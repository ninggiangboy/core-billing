---
phase: 5
title: "Concurrency and multi-instance tests"
status: pending
---

# Concurrency and multi-instance tests

## Goal

Prove the deployment shape and the deadlock claim with tests, not reasoning.

## Work

- Two-context test: 2 api + 2 worker contexts on one Testcontainers PostgreSQL, kill one worker
  mid-run, and show outbox, webhook and renewal work completes with no duplicate charge or invoice.
- Stress test: charge, renewal, reservation expiry, cycle close and Stripe settlement concurrently
  on one wallet; assert `pg_stat_database.deadlocks = 0` afterwards.
- Measure and record a starting `lock_timeout` for each profile in the docs, based on the tests.

## Verification

- The failover test observes exactly one charge and one invoice for the affected period.
- The stress test ends with zero deadlocks and no failed financial operations.
