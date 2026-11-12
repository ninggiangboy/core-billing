---
phase: 9
title: "Docs and closure"
status: completed
priority: P2
effort: "0.5d"
dependencies: [8]
---

# Phase 9: Docs and closure

## Goal

Make the docs describe the sample as it now is, verify the acceptance conditions against the final tree, and review the result.

## Files

- Modify: `docs/sample-tenant-system.md` (module layout, persistence, decisions D1 to D4, test pyramid)
- Modify: `sample-tenant/README.md` (build convention paragraph, scenario-to-test matrix test names that moved or split, module name `webhook`, how to regenerate the jOOQ schema, Docker requirement for integration tests)
- Modify: `docs/architecture.md` only where it states how the sample persists or is structured (it currently names the backend stack for both systems)
- Read, change only if stale: `docs/README.md`, `contracts/` (must stay unchanged)

## Tasks and steps

1. Read each document before editing; update only statements that are now false or missing.
2. Update the scenario-to-test matrix: every row names a test that exists (for example `CustomerBillingViewsTest` becomes `CustomerBillingQueriesTest` plus the two use case tests).
3. Document how to regenerate the jOOQ schema (`-Djooq.update=true`) and that integration tests need Docker.
4. Run the full gate: `cd sample-tenant && ./gradlew checkNoBillingDependency build`, then `cd ../billing && ./gradlew :apps:app-boot:test --tests '*architecture*'` to confirm Billing is untouched.
5. `git diff --stat <phase-1-baseline-sha>..HEAD -- contracts billing core` shows no change (the baseline sha is recorded in phase 1 step 1).
6. Run `/ak-code-review` on the branch diff; resolve findings or record them.
7. Journal entry via `/ak-journal`.

## Verification

- Acceptance conditions of `plan.md` all hold; every link in the edited docs resolves; every test named in the README matrix exists (`grep` each name under `sample-tenant`).
- `contracts/`, `billing/` and `core/` have no diff from this plan.

## Todo

- [x] Docs updated and links checked
- [x] Full build green, Billing architecture tests green
- [x] Code review done
- [x] Journal written
