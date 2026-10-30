# Activity Launcher Future-Date Consistency Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Activity Launcher use one timestamp-validity rule across age filtering, date sorting, and displayed dates.

**Architecture:** Add one small validity helper that defines valid timestamps as `timestamp > 0L && timestamp <= now`. Reuse it in the existing age helper, date sort partitioning, and date formatter so future timestamps behave as unknown everywhere without changing normal date ordering or age thresholds.

**Tech Stack:** Kotlin, Android Activity code, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- `timestamp <= 0L` is invalid everywhere.
- `timestamp > now` is invalid everywhere.
- `timestamp == now` remains valid.
- Existing 24H / 7D / 30D thresholds remain unchanged.
- Invalid timestamps remain at the end of INSTALL_DATE/UPDATE_DATE sorts in both directions.
- Invalid timestamps display as `unknown`.
- Existing valid timestamps keep `yyyy-MM-dd` display.
- NAME, PACKAGE and RECENT behavior remains unchanged.
- Do not alter filters unrelated to timestamps, saved Activity state, inventory discovery, permissions, Shizuku, OEM/Knox, package mutation or setup behavior.
- Establish genuine RED before production changes and require full GREEN Android CI before merge.

---

### Task 1: Unify timestamp validity

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`

**Interfaces:**
- Consumes: `System.currentTimeMillis()`, existing `isTimestampWithinAge(...)`, `sortByTimestampUnknownLast(...)`, `formatInstallDate(...)`.
- Produces: `private fun isValidTimestamp(timestamp: Long, now: Long): Boolean` or equivalent shared validity logic used by all three paths.

- [ ] **Step 1: Add the focused failing contract**

Require a shared validity helper equivalent to `timestamp > 0L && timestamp <= now`. Require `isTimestampWithinAge(...)` to reuse that helper, date sorting to partition via the shared validity rule with one captured `now`, and `formatInstallDate(...)` to return `unknown` for future timestamps. Reject the stale direct sort predicate `apps.partition { timestamp(it) > 0L }` and the stale formatter guard `if (time <= 0L) return "unknown"` when it does not also reject future timestamps.

- [ ] **Step 2: Verify the relevant RED**

Run through the active host/source suite in PR Android CI.
Expected: `Source contracts` fails specifically because production lacks the shared timestamp-validity helper/use sites.

- [ ] **Step 3: Implement the minimum behavior**

Add `isValidTimestamp(timestamp, now)` with `timestamp > 0L && timestamp <= now`.

Change `isTimestampWithinAge(...)` to `isValidTimestamp(timestamp, now) && now - timestamp <= maxAgeMs`.

In `sortByTimestampUnknownLast(...)`, capture `val now = System.currentTimeMillis()` once and partition with `isValidTimestamp(timestamp(it), now)`. Keep current direction handling and append invalid/unknown entries after sorted valid entries.

In `formatInstallDate(...)`, capture current time once and return `unknown` unless `isValidTimestamp(time, now)`; otherwise keep the current `SimpleDateFormat("yyyy-MM-dd", ...)` formatting.

- [ ] **Step 4: Verify the focused GREEN**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: `ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS`.

- [ ] **Step 5: Run the affected integration checks**

Run the repository host/source suite and Android enterprise debug APK build in PR CI.
Expected: Source contracts success, Enterprise debug APK success, `Verify APK exists` success, aggregate `verify` success.

- [ ] **Step 6: Commit the passing deliverable**

Commit only the focused contract and minimal Kotlin timestamp-consistency change after RED has been observed and GREEN verification succeeds.

## Unresolved Product Decisions

None.
