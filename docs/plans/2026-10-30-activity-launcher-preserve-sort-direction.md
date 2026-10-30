# Activity Launcher Preserve Sort Direction Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the current sort direction when the user clicks the already-active Activity Launcher sort pill, while still assigning the existing default direction when switching to a different sort key.

**Architecture:** Keep all behavior inside `ActivityLauncherHubActivity.renderSortBar()`. Guard the default-direction assignment with a comparison against the previous `currentSort`; do not change the dedicated direction toggle, RECENT behavior, date comparator semantics, filters, lifecycle restore, or persistence.

**Tech Stack:** Kotlin / Android Activity UI, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- Re-clicking the already-active sort must preserve `currentSortDirection`.
- Switching to a different sort key must keep the current defaults: NAME/PACKAGE -> ASCENDING, INSTALL_DATE/UPDATE_DATE -> DESCENDING.
- The dedicated `Direction` action remains the only explicit ASC/DESC toggle.
- RECENT behavior remains unchanged and sort controls remain disabled there.
- No changes to filters, search, selection, pagination, lifecycle restoration, favorites, timestamp validity, date tie-breakers, permissions, Shizuku, OEM/Knox, package mutation, or enrollment/setup behavior.

---

### Task 1: Preserve direction on active-sort re-click

**Files:**
- Create: `tools/tests/test_activity_launcher_preserve_sort_direction_contract.py`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`
- Modify: `tools/run_host_tests.sh`
- Modify only if required by an overlapping existing source contract: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Modify only if required by an overlapping existing source contract: `tools/tests/test_component_manager_ui_contract.py`

**Interfaces:**
- Consumes: `currentSort: AppSort`, `currentSortDirection: SortDirection`, `renderSortBar()` sort-pill click callback.
- Produces: active sort re-click leaves direction unchanged; changing sort key assigns its existing default direction.

- [ ] **Step 1: Add the focused failing test**

Create a source contract that requires the sort-pill callback to compare the clicked sort with the current sort before assigning the default direction. Require a shape equivalent to:

```kotlin
if (sort != currentSort) {
    currentSort = sort
    currentSortDirection = when (sort) {
        AppSort.NAME, AppSort.PACKAGE -> SortDirection.ASCENDING
        AppSort.INSTALL_DATE, AppSort.UPDATE_DATE -> SortDirection.DESCENDING
    }
}
```

The contract must also reject the existing unconditional sequence where `currentSort = sort` is immediately followed by an unconditional `currentSortDirection = when (sort)` assignment.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_activity_launcher_preserve_sort_direction_contract.py`

Expected: non-zero exit with an assertion identifying the missing guard such as `ActivityLauncherHubActivity.kt missing active-sort direction-preservation guard`.

- [ ] **Step 3: Implement the minimum behavior**

In the sort pill callback inside `renderSortBar()`, assign `currentSort` and its default direction only when `sort != currentSort`. Keep `renderSortBar()` and `renderApps()` calls after the conditional so the existing rendering behavior remains intact. Do not make an active pill click toggle direction.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_activity_launcher_preserve_sort_direction_contract.py`

Expected: exit 0 with `ACTIVITY_LAUNCHER_PRESERVE_SORT_DIRECTION_CONTRACT: PASS`.

Run the overlapping focused contracts:

```bash
python3 tools/tests/test_activity_launcher_app_manager_contract.py
python3 tools/tests/test_component_manager_ui_contract.py
```

Expected: both exit 0. If either fails only because it asserts the old unconditional assignment shape, update that assertion to the accepted guarded behavior without weakening unrelated checks.

- [ ] **Step 5: Run the affected integration check**

Add `test_activity_launcher_preserve_sort_direction_contract.py` to `tools/run_host_tests.sh` adjacent to the Activity Launcher contracts.

Run the repository host/source suite via the existing CI path and require full Android CI GREEN:

- Source contracts: success
- Enterprise debug APK: success
- Assemble enterprise debug APK: success
- Verify APK exists: success
- aggregate `verify`: success

Also require `git diff --check` with no whitespace errors.

- [ ] **Step 6: Commit the passing deliverable**

Commit only the production file, focused contract, host-suite registration, and any strictly necessary overlapping-contract updates. Temporary transport workflows must be deleted before final PR verification.

## Unresolved Product Decisions

None.
