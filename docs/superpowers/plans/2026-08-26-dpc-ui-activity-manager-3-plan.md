# DPC UI + Activity Manager 3.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every primary DPC screen safe around status/navigation/IME insets, reorganize the dashboard into discoverable categories, and replace the single-package Activity Explorer with an all-app Activity Manager 3.0 supporting expansion, filters, favorites, and favorite groups without weakening component-control protections.

**Architecture:** Add a small `DpcUiShell` utility in the app module that applies system/IME/cutout insets to a content root and wraps scrollable screens consistently. Put Activity Manager browsing/filtering data models in the pure `activity-launcher` module so they can be tested with `kotlinc`; Android inventory remains in `activity-android`, while favorites/group persistence stays in the app module using device-protected `SharedPreferences`. `ActivityExplorerActivity` becomes the orchestration/rendering layer and continues using the existing `ComponentControlRouter`, snapshot store, Shizuku routes, and protected-target checks for mutations.

**Tech Stack:** Kotlin/JVM 21, Android framework APIs, DevicePolicyManager project modules, device-protected SharedPreferences, existing Shizuku adapter, Python host contract tests, `kotlinc` for pure Kotlin unit checks.

**Spec:** `docs/superpowers/specs/2026-08-26-dpc-ui-policy-expansion-design.md`

## Global Constraints

- Preserve current Android Enterprise provisioning flows and manifest components for `work-profile` and `fully-managed`.
- Preserve app PIN behavior; provisioning/compliance activities must remain callable without the dashboard PIN gate.
- Do not add hidden/non-SDK API dependencies.
- Do not bypass `ComponentControlRouter`, `ProtectionPlanner`, `PROTECTED_DPC_COMPONENT`, or critical-system-component guards.
- UI must not place actionable content beneath status bar, display cutout, navigation bar, or on-screen keyboard.
- Activity Manager must work with all installed packages visible to PackageManager under the app's current package visibility and user context; inaccessible packages are skipped with diagnostic counts instead of crashing.
- Favorites and groups affect navigation only; they never grant additional component mutation/launch capability.

---

### Task 1: Safe-insets UI shell

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcUiShell.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt`
- Modify: primary scroll-based activities under `apps/dpc/app/src/main/kotlin/io/dpcaio/app/` that currently call `setContentView(ScrollView(...))`
- Test: `tools/tests/test_116_safe_insets_ui_contract.py`

**Interfaces:**
- Produces: `object DpcUiShell` with `fun install(activity: Activity, content: View, baseHorizontalDp: Int = 16, baseVerticalDp: Int = 16)` and `fun scroll(activity: Activity, child: View, baseHorizontalDp: Int = 16, baseVerticalDp: Int = 16): ScrollView`.

- [ ] **Step 1: Write the failing host contract test** checking `DpcUiShell` exists, listens for `WindowInsets` including system bars, display cutout, and IME, uses `setDecorFitsSystemWindows(window, false)` or equivalent framework behavior, and that dashboard/activity-manager use the shell rather than fixed-only root padding.
- [ ] **Step 2: Run** `python3 tools/tests/test_116_safe_insets_ui_contract.py` and verify failure because `DpcUiShell.kt` is absent.
- [ ] **Step 3: Implement `DpcUiShell`** using framework `WindowInsets` APIs available at project minSdk, preserving base padding and adding max(systemBars/cutout, IME bottom) safely.
- [ ] **Step 4: Migrate dashboard, Activity Manager, and primary long-form scroll screens** to the shell without changing policy behavior.
- [ ] **Step 5: Re-run** `python3 tools/tests/test_116_safe_insets_ui_contract.py` and existing provisioning/PIN contracts; expect PASS.

### Task 2: Activity browser pure model and filtering

**Files:**
- Create: `apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ActivityBrowserModel.kt`
- Create: `apps/dpc/modules/activity/core/src/test/kotlin/io/dpcaio/activity/ActivityBrowserModelTest.kt`
- Test helper: `/mnt/data/dpc_ui_upgrade/activity_browser_model_test_runner.kt` (temporary, excluded from ZIP)

**Interfaces:**
- Produces: `InstalledAppDescriptor(packageName, label, systemApp, enabled, activityCount)`; `ActivityBrowserFilter(query, appScope, enabledState, exportedState, launcherState, permissionState, favoritesOnly, favoriteGroup, sortMode)`; enums for each filter; `ActivityBrowserMatcher.matchesApp(...)`, `matchesActivity(...)`, and `sortApps(...)`.

- [ ] **Step 1: Write failing pure Kotlin tests** for query matching label/package/class, user/system scope, enabled/disabled, exported, launcher/hidden, permission/no-permission, favorites-only/group membership, and label/package/activity-count sorting.
- [ ] **Step 2: Compile/run with `kotlinc`** against existing `ComponentModel.kt`; verify RED due missing model.
- [ ] **Step 3: Implement minimal pure model/matcher** with deterministic case-insensitive filtering and stable sorting.
- [ ] **Step 4: Compile/run same test harness**; expect PASS.

### Task 3: Android all-app inventory and durable favorites/groups

**Files:**
- Modify: `apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityFavoriteStore.kt`
- Test: `tools/tests/test_117_activity_manager_persistence_contract.py`

**Interfaces:**
- `AndroidActivityInventory.listApps(user: UserHandle): List<InstalledAppDescriptor>`
- `ActivityFavoriteStore.isAppFavorite(packageName: String): Boolean`
- `ActivityFavoriteStore.isActivityFavorite(packageName: String, className: String): Boolean`
- `ActivityFavoriteStore.toggleAppFavorite(packageName: String): Boolean`
- `ActivityFavoriteStore.toggleActivityFavorite(packageName: String, className: String): Boolean`
- `ActivityFavoriteStore.groups(): Set<String>`
- `ActivityFavoriteStore.createGroup(name: String): Boolean`
- `ActivityFavoriteStore.renameGroup(oldName: String, newName: String): Boolean`
- `ActivityFavoriteStore.deleteGroup(name: String): Boolean`
- `ActivityFavoriteStore.setMembership(group: String, itemKey: String, member: Boolean)`
- `ActivityFavoriteStore.members(group: String): Set<String>`

- [ ] **Step 1: Write failing host contract test** for device-protected storage, namespaced keys, normalized group names, and no mutation/launch capability in the favorite store.
- [ ] **Step 2: Run** `python3 tools/tests/test_117_activity_manager_persistence_contract.py`; verify RED because store/listApps are absent.
- [ ] **Step 3: Implement `listApps`** using PackageManager installed-app/package info APIs with activity counts and resilient per-package error handling.
- [ ] **Step 4: Implement favorite/group persistence** using `createDeviceProtectedStorageContext().getSharedPreferences(...)`, string sets, stable item keys `app:<pkg>` and `activity:<pkg>/<class>`, plus sanitized non-empty group names.
- [ ] **Step 5: Re-run persistence contract**; expect PASS.

### Task 4: Activity Manager 3.0 UI and behavior

**Files:**
- Replace/refactor: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt`
- Test: `tools/tests/test_118_activity_manager_3_contract.py`

**Interfaces:**
- Consumes Task 2 filter/matcher types, Task 3 inventory/store, existing `ComponentControlRouter`, `ComponentStateSnapshotStore`, `AndroidComponentStateGateway`, Shizuku executors, and launch coordinator.
- Produces an expandable all-app UI: collapsed app rows; lazy activity load on expand; filters; favorites; groups; per-activity mutation/launch actions; batch actions scoped to currently filtered activities of the selected/expanded package.

- [ ] **Step 1: Write failing host contract** requiring title `Activity Manager 3.0`, all-app scan, expandable package state, lazy `inventory.list(package, user)`, filter controls, app/activity favorite controls, favorite group management, and reuse of existing mutation/launch routes.
- [ ] **Step 2: Run** `python3 tools/tests/test_118_activity_manager_3_contract.py`; verify RED against Activity Manager 2.0.
- [ ] **Step 3: Implement top filter bar** with query plus compact selector buttons/dialogs for app scope, enabled, exported, launcher, permission, favorites/group, sort, and target user ID.
- [ ] **Step 4: Implement all-app list** with collapsed rows `★ label / package / activity count`; load activities only on expansion; cache loaded activities per package/user and invalidate on rescan/mutation.
- [ ] **Step 5: Implement favorites/groups UX**: star app/activity, create/rename/delete group dialogs, assign item to group, filter by group.
- [ ] **Step 6: Preserve state controls**: Enable/Disable/Restore/Enable&Launch/Launch and snapshot restore still route through existing decision methods and protections.
- [ ] **Step 7: Re-run host contract and pure matcher tests**; expect PASS.

### Task 5: Dashboard/menu information architecture

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt`
- Test: `tools/tests/test_119_dashboard_menu_contract.py`

**Interfaces:**
- Produces categorized sections: `Enrollment`, `Apps & Components`, `Device & Policy`, `Security & Credentials`, `Network`, `Work Profile / COPE`, `OEM / Knox`, `Diagnostics`, and `Advanced / Lab` when developer mode is enabled.
- `Apps & Components` must include `Activity Manager 3.0`, `Permission Manager`, and an Activity Manager entry point that can open directly with favorites-only intent extras.

- [ ] **Step 1: Write failing dashboard contract** for required categories, Activity Manager 3.0, Favorites entry, and shell usage.
- [ ] **Step 2: Run** `python3 tools/tests/test_119_dashboard_menu_contract.py`; verify RED against flat dashboard.
- [ ] **Step 3: Implement categorized dashboard** using reusable header/button helpers and existing activities; Favorites launches `ActivityExplorerActivity` with `favoritesOnly=true`.
- [ ] **Step 4: Re-run dashboard contract and PIN contract**; expect PASS.

### Task 6: Regression gates, documentation, packaging

**Files:**
- Modify: `tools/run_host_tests.sh`
- Create: `ACTIVITY-MANAGER-3-VALIDATION.md`
- Modify: `REPO-SHA256SUMS.txt`

**Interfaces:** none.

- [ ] **Step 1: Add tests 116-119 to host test runner**.
- [ ] **Step 2: Run focused tests** 115-119 plus provisioning QR, signing-path, release gates, secret scan, Python AST, Bash syntax, YAML parse.
- [ ] **Step 3: Attempt Kotlin/Gradle verification**. Use `kotlinc` for pure module regardless; run Gradle if local distribution/dependencies are available. If network prevents wrapper bootstrap, record that exact limitation rather than claiming compile success.
- [ ] **Step 4: Write `ACTIVITY-MANAGER-3-VALIDATION.md`** with behavior and verification evidence.
- [ ] **Step 5: Refresh `REPO-SHA256SUMS.txt`**, excluding itself and transient/generated ignored directories.
- [ ] **Step 6: Create final ZIP** with top-level `ENRROL_NEW-main/`, no `.git`, no build outputs, no `__pycache__`/`.pyc`; run ZIP `testzip()` and verify manifest hashes.
