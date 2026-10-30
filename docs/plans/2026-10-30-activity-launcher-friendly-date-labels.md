# Activity Launcher Friendly Date Labels Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace technical enum-style date filter labels with compact human-readable `ALL / 24H / 7D / 30D` labels while preserving all existing Activity Launcher filter behavior.

**Architecture:** Keep `InstallAgeFilter` and `UpdateAgeFilter` enums unchanged and add small display-label helpers inside `ActivityLauncherHubActivity`. `renderDateAgeBar()` consumes those helpers so only UI text changes; filtering, thresholds, state, reset, summary, sorting, search, paging, selection, and actions remain untouched.

**Tech Stack:** Kotlin, Android Views, existing `ModernUi` helpers, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- Preserve `InstallAgeFilter { ALL, HOURS_24, DAYS_7, DAYS_30 }` and `UpdateAgeFilter { ALL, HOURS_24, DAYS_7, DAYS_30 }` exactly.
- Display labels must be `INST ALL`, `INST 24H`, `INST 7D`, `INST 30D`, `UPD ALL`, `UPD 24H`, `UPD 7D`, `UPD 30D`.
- Do not change millisecond thresholds or filter predicates.
- Do not change reset behavior, active-filter count, summary state values, sort/search/paging/selection/actions, permissions, Shizuku/root/hidden API usage, or package/system mutation behavior.
- Preserve the compact shared `dateAgeBar` and `renderDateAgeBar()` structure from PR #150.

---

### Task 1: Contract the friendly date labels

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Test: `tools/tests/test_activity_launcher_app_manager_contract.py`

**Interfaces:**
- Consumes: `ActivityLauncherHubActivity.kt` source text and existing date-filter enums/render method.
- Produces: a source contract requiring explicit display-label helpers and all eight friendly rendered labels while rejecting direct `${filter.name}` rendering for date pills.

- [ ] **Step 1: Add the focused failing test**

Update the contract to require:
- `private fun installAgeLabel(filter: InstallAgeFilter): String`
- `private fun updateAgeLabel(filter: UpdateAgeFilter): String`
- exact mappings `ALL -> "ALL"`, `HOURS_24 -> "24H"`, `DAYS_7 -> "7D"`, `DAYS_30 -> "30D"` for both helpers
- `"INST ${installAgeLabel(filter)}"`
- `"UPD ${updateAgeLabel(filter)}"`

Also assert stale date pill render tokens are absent:
- `"INST ${filter.name}"`
- `"UPD ${filter.name}"`

Keep all existing enum, predicate, threshold, reset, summary, compact-bar, and safety contract assertions.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: failure proving `ActivityLauncherHubActivity.kt` is missing `private fun installAgeLabel(filter: InstallAgeFilter): String` (or the first newly-required friendly-label token), with all unrelated earlier assertions passing.

- [ ] **Step 3: Commit the RED contract**

Commit only the contract change with message `test(activity-launcher): require friendly date labels`.

### Task 2: Implement friendly labels without behavior changes

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`
- Test: `tools/tests/test_activity_launcher_app_manager_contract.py`

**Interfaces:**
- Consumes: `InstallAgeFilter`, `UpdateAgeFilter`, and `renderDateAgeBar()`.
- Produces: `installAgeLabel(filter: InstallAgeFilter): String` and `updateAgeLabel(filter: UpdateAgeFilter): String`, each returning only `ALL`, `24H`, `7D`, or `30D`.

- [ ] **Step 1: Implement the minimum behavior**

Add two private exhaustive `when` helpers:
- install: `ALL -> "ALL"`, `HOURS_24 -> "24H"`, `DAYS_7 -> "7D"`, `DAYS_30 -> "30D"`
- update: same mapping

Change only the two date pill strings in `renderDateAgeBar()`:
- from `INST ${filter.name}` to `INST ${installAgeLabel(filter)}`
- from `UPD ${filter.name}` to `UPD ${updateAgeLabel(filter)}`

Do not touch filter predicates, timestamps, thresholds, state, summary, reset, ordering, or actions.

- [ ] **Step 2: Verify the focused pass**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: `ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS`.

- [ ] **Step 3: Run the affected integration check**

Run: `./tools/run_host_tests.sh`
Expected: host/source suite passes, including the Activity Launcher app-manager contract.

- [ ] **Step 4: Commit the passing deliverable**

Commit production change with message `feat(activity-launcher): use friendly date labels`.

### Task 3: Full CI and guarded merge

**Files:**
- No source changes expected.

**Interfaces:**
- Consumes: final PR head SHA.
- Produces: verified merged `main` commit containing the friendly date labels.

- [ ] **Step 1: Open/update the PR and verify exact head**

Open a PR from `feature/activity-launcher-friendly-date-labels` to `main`. Confirm fresh PR metadata shows the expected final head SHA and `mergeable=true` before merging.

- [ ] **Step 2: Verify full CI**

Require the Android CI jobs to complete successfully:
- `Source contracts`
- `Enterprise debug APK`
- `Assemble enterprise debug APK`
- `Verify APK exists`
- final `verify`

Expected: all required jobs conclude `success` on the exact final head.

- [ ] **Step 3: Exact-head squash merge**

Merge with squash and `expected_head_sha` equal to the final GREEN head so GitHub rejects any moved-head race.

- [ ] **Step 4: Verify merged state**

Freshly verify:
- PR is `closed` and `merged=true`
- `main` points to the merge/squash commit
- commit verification reports `verified=true` and `reason=valid`

## Unresolved Product Decisions

None. The approved specification fully fixes the externally visible labels and explicitly preserves all other behavior.
