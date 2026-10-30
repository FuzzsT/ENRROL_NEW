# Activity Launcher Date Sort Tie-Breaker Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make INSTALL_DATE and UPDATE_DATE sorting deterministic for applications that share the same valid timestamp.

**Architecture:** Keep `sortByTimestampUnknownLast(...)` as the only date-sort helper. Partition valid and unknown timestamps exactly as today, then sort valid entries with timestamp as the primary key and case-insensitive label/package tie-breakers; for descending date order reverse only the timestamp comparison while keeping tie-breakers ascending.

**Tech Stack:** Kotlin, Android Activity code, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- Valid timestamp rules from PR #155 stay unchanged.
- Invalid/unknown timestamps stay after all valid timestamps.
- Equal valid timestamps sort by app label A-to-Z, case-insensitively, then package name A-to-Z, case-insensitively.
- ASCENDING applies oldest-to-newest on the timestamp key only.
- DESCENDING applies newest-to-oldest on the timestamp key only; tie-breakers remain A-to-Z.
- Unknown entries keep their current stable input order.
- NAME, PACKAGE, RECENT, filters, lifecycle state, selection, pagination and UI controls remain unchanged.
- No permissions, Shizuku, OEM/Knox, package mutation or enrollment/setup behavior changes.
- Establish genuine RED before production changes and require full GREEN Android CI before merge.

---

### Task 1: Add deterministic tie-breaking for date sorts

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Modify: `tools/tests/test_component_manager_ui_contract.py` only if the existing overlapping date-sort contract requires alignment
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`

**Interfaces:**
- Consumes: `sortByTimestampUnknownLast(apps: List<AppEntry>, timestamp: (AppEntry) -> Long)`, `currentSortDirection`, `AppEntry.label`, `AppEntry.packageName`, `isValidTimestamp(timestamp, now)`.
- Produces: deterministic valid-date ordering with alphabetical tie-breakers while preserving unknown-last behavior.

- [ ] **Step 1: Add the focused failing contract**

Require the date-sort helper to construct deterministic ordering equivalent to:

- ASCENDING: timestamp ascending, then `label` case-insensitive ascending, then `packageName` case-insensitive ascending;
- DESCENDING: timestamp descending, then the same two alphabetical tie-breakers ascending.

Require that unknown entries are still appended after sorted valid entries. Reject an implementation that sorts valid entries only with `sortedBy(timestamp)` followed by whole-list `reversed()`, because that would reverse tie-breakers in DESCENDING mode.

- [ ] **Step 2: Verify the relevant RED**

Run through the active host/source suite in PR Android CI.
Expected: `Source contracts` fails specifically because production lacks deterministic label/package tie-breakers for equal date timestamps.

- [ ] **Step 3: Implement the minimum behavior**

Inside `sortByTimestampUnknownLast(...)`:

1. keep the existing one-time `now` capture and valid/unknown partition;
2. create a comparator whose timestamp comparison depends on `currentSortDirection`;
3. append `.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }`;
4. append `.thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }`;
5. sort only the `valid` subset with that comparator;
6. return `sortedValid + unknown`.

Do not call `reversed()` on the fully tie-broken valid list for DESCENDING mode.

- [ ] **Step 4: Verify the focused GREEN**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: `ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS`.

If `test_component_manager_ui_contract.py` contains overlapping date-sort assertions, run it too and align only stale assertions to the same comparator contract.

- [ ] **Step 5: Run the affected integration checks**

Run the repository host/source suite and Android enterprise debug APK build in PR CI.
Expected: Source contracts success, Enterprise debug APK success, `Verify APK exists` success, aggregate `verify` success.

- [ ] **Step 6: Commit the passing deliverable**

Commit only the focused contract updates and minimal Kotlin comparator change after RED has been observed and GREEN verification succeeds.

## Unresolved Product Decisions

None.
