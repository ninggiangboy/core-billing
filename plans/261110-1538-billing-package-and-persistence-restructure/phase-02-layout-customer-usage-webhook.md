---
phase: 2
title: "Layout: customer, usage, webhook"
status: pending
priority: P1
effort: "1d"
dependencies: [1]
---

# Phase 2: Layout: customer, usage, webhook

## Goal

Move the three smaller modules to the feature-then-role package layout in all four source sets, and settle how `api` sub-packages stay visible to Spring Modulith and to other modules.

## Context

Target shape (illustrated with `money` in [plan.md](./plan.md)): inside each source set, one package per feature (usually one per aggregate), and inside it `command`, `query`, `port` as needed. `customer` and `usage` are small, so they use `command/`, `query/`, `port/` without a feature level. `webhook` has no `api` source set. This phase is a pure move: no logic edit, no signature change, no rename of a class.

## Files to Create / Modify

- Move with `git mv`, then fix `package` lines and imports, for every `.java` file under `billing/customer`, `billing/usage` and `billing/webhook` in `src/api`, `src/domain`, `src/application`, `src/adapter` and `src/test`.
- Modify imports in other modules and `billing/app-boot` that reference moved types (`customer.api`, `usage.api`).
- Create or modify each module's `adapter/.../api/package-info.java` so every `api` sub-package that other modules use is a Modulith named interface.

## Tasks & Steps

- [ ] 1. For `usage`, `webhook` and `customer`, list the files per source set (`git ls-files <module>/src`) and write the file-to-package mapping in the phase notes before moving anything. Group by aggregate: one feature package per aggregate root, shared helpers in the feature that owns them.
- [ ] 2. Spike on `customer.api` (two types): put the types in a sub-package, add a `package-info.java` with `@NamedInterface` in the `adapter` source set, and run `ModulithVerificationTest`. Record whether the root `api` package keeps its own `package-info` and how several named interfaces are declared. The rest of this plan reuses the recorded pattern.
- [ ] 3. Move `customer`, then compile (`./gradlew :customer:compileAdapterJava`, then `build`), then fix imports in dependants.
- [ ] 4. Repeat for `usage` and `webhook`.
- [ ] 5. Add `customer`, `usage` and `webhook` to the layout set of the architecture rules.
- [ ] 6. Commit each module separately as `refactor(<module>): group packages by feature and role`.

## Verification

- `cd billing && ./gradlew build` is green; test count equals the baseline.
- `git diff --stat -M dev -- 'billing/*/src'` shows renames, with no content change other than `package` and `import` lines for the moved files.
- The new layout rules pass for the three modules.

## Risks and rollback

- Modulith may report a sub-package as internal. The spike in step 2 finds this on the smallest module. If a named interface per sub-package cannot be made to work, stop and ask before flattening `api` again, because the user chose to split it.
- Rollback is reverting the module's move commit.
