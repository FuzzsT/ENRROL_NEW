# Activity Launcher Install Age Filter Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an install-age filter to Activity Launcher with `ALL / 24H / 7D / 30D` options based on each app's existing `firstInstallTime` metadata.

**Architecture:** Extend `ActivityLauncherHubActivity` with one new filter enum/state and one compact filter bar. Apply the age predicate in the existing `filteredApps()` pipeline before sorting, include it in active-filter count/summary/reset, and keep all behavior read-only.

**Tech Stack:** Kotlin/Android, existing `ModernUi` helpers, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- No new Android permissions.
- No Shizuku/root/hidden-API path.
- No package/system mutations.
- `ALL` is the default and reset state.
- Thresholds are rolling windows from current system time: 24 hours, 7 days, 30 days.
- `firstInstallTime <= 0` is excluded from `24H`, `7D`, and `30D`, but remains visible in `ALL`.
- Existing tabs, state/source/target/debug filters, search, sort direction, paging, selection, favorites, recents, and app actions remain unchanged.

---

### Task 1: Contract the install-age behavior

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Test: `tools/tests/test_activity_launcher_app_manager_contract.py`

**Interfaces:**
- Consumes: `ActivityLauncherHubActivity.kt` source contract.
- Produces: assertions for enum/state/UI/filter/reset/summary semantics.

- [ ] Add assertions for `InstallAgeFilter { ALL, HOURS_24, DAYS_7, DAYS_30 }`, default state, `renderInstallAgeBar()`, `AGE ${filter.name}`, active-filter count, reset, summary, and rolling-window predicates.
- [ ] Run `python3 tools/tests/test_activity_launcher_app_manager_contract.py` and require a relevant missing-install-age assertion.

### Task 2: Implement the minimum read-only filter

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`
- Test: `tools/tests/test_activity_launcher_app_manager_contract.py`

**Interfaces:**
- Consumes: existing `AppEntry.firstInstallTime`, `filteredApps()`, filter render/reset helpers.
- Produces: `InstallAgeFilter`, `currentInstallAgeFilter`, `renderInstallAgeBar()` and age-filtered app list.

- [ ] Add the enum/state and UI bar using existing pill conventions.
- [ ] Add `currentInstallAgeFilter != InstallAgeFilter.ALL` to `activeFilterCount()`.
- [ ] Add `age=${currentInstallAgeFilter.name}` to filter summary.
- [ ] Reset to `InstallAgeFilter.ALL` in `resetFilters()`.
- [ ] In `filteredApps()`, compute `now = System.currentTimeMillis()` and apply rolling thresholds: `24L*60*60*1000`, `7L*24*60*60*1000`, `30L*24*60*60*1000`; require `firstInstallTime > 0` for non-ALL options.
- [ ] Run the focused contract and require PASS.
- [ ] Run `./tools/run_host_tests.sh` and require PASS.
- [ ] Require full Android CI GREEN before merge.

## Unresolved Product Decisions

None. The approved UX is `ALL / 24H / 7D / 30D`, rolling from the current device time, with unknown/zero install timestamps excluded from age-limited views.
