---
name: dpc-aio-ci-repair
description: Use when DPC-AIO GitHub Actions or Gradle CI fails and concrete logs are available, especially around SDK setup, Gradle wrapper execution, Kotlin compilation, provisioning, or artifact collection.
---

# DPC-AIO CI Repair

## Core rule

Diagnose the first blocking error from the current log and current source. Treat warnings as non-blocking unless the job explicitly fails on them. Prefer one bounded root-cause fix over broad workflow rewrites.

## Procedure

1. Record the failing workflow step, process exit code, Gradle task, and first compiler/error line.
2. Compare that evidence with the checked-in workflow/source at the commit being built. Confirm that the expected fix exists in that SHA before recommending a rerun.
3. Separate environment/setup failures from repository compilation failures and from post-build artifact failures.
4. Apply the smallest source or workflow change that addresses the demonstrated root cause. Preserve current Java/AGP/Gradle/SDK/NDK versions unless the failure establishes incompatibility.
5. Add or run a regression check that fails on the pre-fix state and passes after the change.
6. Trigger a **new commit/run** when the source changed. A rerun of an old SHA cannot validate code that was never committed.

## Reference

Read `references/known-failures.md` for patterns already observed in this repository. They are diagnostic patterns, not automatic fixes; verify the current log/source first.
