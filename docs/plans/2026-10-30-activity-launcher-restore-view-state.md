# Activity Launcher View-State Restoration Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Activity Launcher transient view state across Android Activity recreation without persisting that state across fresh launches.

**Architecture:** Save all transient catalog controls into `Bundle` in `onSaveInstanceState()`. Restore enum-backed state defensively before rendering, restore pagination depth with a lower bound of `INITIAL_RENDER_LIMIT`, then restore search text after `EditText` creation while suppressing the intermediate render callback. Existing durable favorites/recents remain in `SharedPreferences` unchanged.

**Tech Stack:** Kotlin, Android Activity/Bundle lifecycle APIs, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- Preserve `currentTab`, `currentSort`, `currentSortDirection`, state/source/install-age/update-age/target-SDK/debuggable filters, search text, `visibleAppLimit`, and existing `selectedPackages` across Activity recreation.
- Restore enum values defensively with fallback to the current defaults; unknown/missing saved enum names must not crash.
- Clamp restored `visibleAppLimit` to at least `INITIAL_RENDER_LIMIT`.
- Restore search only after the field exists and guard `search.setText(...)` with `suppressSearchRender`.
- Do not persist transient view state in `SharedPreferences` or another durable store.
- Fresh Activity launches retain the existing defaults.
- Do not change filtering, sorting, selection, package discovery, permissions, Shizuku/OEM/Knox behavior, or package mutation behavior.
- Establish genuine RED before production changes and require full GREEN Android CI before merge.

---

### Task 1: Restore Activity Launcher transient view state

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`

**Interfaces:**
- Consumes: Android `Bundle`, existing Activity Launcher enums/state fields, `INITIAL_RENDER_LIMIT`, `suppressSearchRender`, existing `STATE_SELECTED_PACKAGES` restoration.
- Produces: safe enum restore helper (or equivalent), stable saved-state key constants, complete `onSaveInstanceState()` persistence and `onCreate()` restoration for transient view state.

- [ ] **Step 1: Add the focused failing contract**

Require stable saved-state keys for tab, sort, direction, every filter, search query, visible limit, and existing selected packages. Require defensive enum restoration with fallback, `visibleAppLimit` clamping via `coerceAtLeast(INITIAL_RENDER_LIMIT)` or equivalent, guarded search restoration using `suppressSearchRender`, and `outState.put*` calls for every field. Reject transient-state writes through `preferences.edit()`/SharedPreferences.

- [ ] **Step 2: Verify the relevant RED**

Run through the active host/source suite in PR Android CI.
Expected: `Source contracts` fails specifically because production lacks the new view-state restoration contract while APK compilation may still pass.

- [ ] **Step 3: Implement the minimum behavior**

Restore enum-backed fields before `renderShell()` using safe name lookup with existing field values as fallback. Restore `visibleAppLimit` from the bundle and clamp it to `INITIAL_RENDER_LIMIT` or greater. Preserve existing selected-package restoration. After `renderShell()`, restore the saved search text inside a `suppressSearchRender = true` / `finally { suppressSearchRender = false }` guard, then continue with `discoverInstalledApps()`.

In `onSaveInstanceState()`, write each enum `.name`, search text, visible limit, and selected packages using stable key constants. Do not write these values to `SharedPreferences`.

- [ ] **Step 4: Verify the focused GREEN**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: `ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS`.

- [ ] **Step 5: Run the affected integration checks**

Run the repository host/source suite and Android enterprise debug APK build in PR CI.
Expected: Source contracts success, Enterprise debug APK success, `Verify APK exists` success, and aggregate `verify` success.

- [ ] **Step 6: Commit the passing deliverable**

Commit only the focused contract and minimal Kotlin lifecycle change after RED has been observed and GREEN verification succeeds.

## Unresolved Product Decisions

None.