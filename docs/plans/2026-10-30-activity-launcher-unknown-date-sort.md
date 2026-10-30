# Activity Launcher Unknown Date Sort Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep applications with unknown install/update timestamps at the end of Activity Launcher date sorts in both ascending and descending directions.

**Architecture:** Add one focused date-sort helper in `ActivityLauncherHubActivity` that partitions valid timestamps (`> 0L`) from unknown timestamps (`<= 0L`), sorts only the valid partition by the selected direction, and appends the unknown partition unchanged. Reuse it for INSTALL_DATE and UPDATE_DATE while preserving NAME, PACKAGE, RECENT, filtering, UI state, paging, selection, and actions.

**Tech Stack:** Kotlin, Android framework, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- Unknown timestamp means `timestamp <= 0L`.
- Unknown entries must always appear after all valid timestamps for both ASCENDING and DESCENDING date sorts.
- Valid timestamps keep normal ascending/descending chronological order.
- `AppSort.NAME`, `AppSort.PACKAGE`, and `AppTab.RECENT` behavior stays unchanged.
- Do not change age filters, search, UI labels, paging, selection, permissions, actions, Shizuku/root/hidden API paths, or package mutation behavior.
- Establish genuine RED before production changes and require full GREEN Android CI before merge.

---

### Task 1: Sort unknown dates last

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`

**Interfaces:**
- Consumes: `AppEntry`, `SortDirection`, `AppSort`, existing `filteredApps()` output.
- Produces: `private fun sortByTimestampUnknownLast(apps: List<AppEntry>, timestamp: (AppEntry) -> Long): List<AppEntry>` using `currentSortDirection` to order only valid timestamps and append unknown timestamps.

- [ ] **Step 1: Add the focused failing contract**

Require the shared helper, require partitioning into `valid` and `unknown`, require `timestamp(it) > 0L` for valid entries and `timestamp(it) <= 0L` for unknown entries, require direction application only to the valid sorted list, require appending `unknown` after valid entries, and require INSTALL_DATE/UPDATE_DATE to call the helper with `firstInstallTime` / `lastUpdateTime`. Reject the old direct `filtered.sortedBy { it.firstInstallTime }` and `filtered.sortedBy { it.lastUpdateTime }` tokens.

- [ ] **Step 2: Verify the relevant RED**

Run through the active host/source suite in PR Android CI.
Expected: `Source contracts` fails specifically because `ActivityLauncherHubActivity.kt` lacks `sortByTimestampUnknownLast(...)`; APK build may still pass because production has not changed.

- [ ] **Step 3: Implement the minimum behavior**

Add:

```kotlin
private fun sortByTimestampUnknownLast(
    apps: List<AppEntry>,
    timestamp: (AppEntry) -> Long,
): List<AppEntry> {
    val (valid, unknown) = apps.partition { timestamp(it) > 0L }
    val sortedValid = valid.sortedBy(timestamp)
    val directed = if (currentSortDirection == SortDirection.DESCENDING) sortedValid.reversed() else sortedValid
    return directed + unknown
}
```

Use it only for `AppSort.INSTALL_DATE` and `AppSort.UPDATE_DATE`. Keep NAME/PACKAGE sorting and the RECENT early return unchanged. Because date direction is handled inside the helper, ensure the existing final whole-list reversal is not applied to date-sort results; otherwise `unknown` would move to the front in DESCENDING mode. Preserve the existing final direction behavior for NAME/PACKAGE.

- [ ] **Step 4: Verify the focused GREEN**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: `ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS`.

- [ ] **Step 5: Run the affected integration checks**

Run the repository host/source suite and Android enterprise debug APK build in PR CI.
Expected: Source contracts success, Enterprise debug APK success, `Verify APK exists` success, and aggregate `verify` success.

- [ ] **Step 6: Commit the passing deliverable**

Commit the contract and minimal Kotlin change together only after the RED has been observed and GREEN checks pass.

## Unresolved Product Decisions

None. The approved design fixes unknown-date placement without changing the visible result set or non-date sorting semantics.
