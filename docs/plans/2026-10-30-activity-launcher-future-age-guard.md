# Activity Launcher Future Age Guard Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent install/update age filters from accepting timestamps that lie in the future while preserving all existing filter UI and behavior.

**Architecture:** Add one shared private helper in `ActivityLauncherHubActivity` that validates a timestamp against `now` and a maximum age. Reuse it for all install/update age-specific predicates while leaving `ALL` branches unchanged.

**Tech Stack:** Kotlin, Android framework, Python source-contract tests, GitHub Actions Android CI.

## Global Constraints

- Use `timestamp > 0L && timestamp <= now && now - timestamp <= maxAgeMs` as the shared rule.
- Apply only to install/update `24H`, `7D`, and `30D` filters.
- Keep `ALL` behavior unchanged.
- No GUI changes, permissions, Shizuku/root/hidden API additions, package mutations, or unrelated refactors.
- Establish genuine RED before production changes, then verify full GREEN CI before merge.

---

### Task 1: Reject future timestamps in install/update age filters

**Files:**
- Modify: `tools/tests/test_activity_launcher_app_manager_contract.py`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`
- Test: `tools/tests/test_activity_launcher_app_manager_contract.py`

**Interfaces:**
- Consumes: existing `InstallAgeFilter`, `UpdateAgeFilter`, age constants, and `filteredApps()` pipeline.
- Produces: `private fun isTimestampWithinAge(timestamp: Long, now: Long, maxAgeMs: Long): Boolean`.

- [ ] **Step 1: Add the focused failing test**

Extend the existing Activity Launcher app-manager source contract to require:
- `private fun isTimestampWithinAge(timestamp: Long, now: Long, maxAgeMs: Long): Boolean`
- `timestamp > 0L && timestamp <= now && now - timestamp <= maxAgeMs`
- install-age predicates calling `isTimestampWithinAge(it.firstInstallTime, now, INSTALL_AGE_24H_MS/7D_MS/30D_MS)`
- update-age predicates calling `isTimestampWithinAge(it.lastUpdateTime, now, UPDATE_AGE_24H_MS/7D_MS/30D_MS)`
- stale direct predicates without `timestamp <= now` must no longer appear.

- [ ] **Step 2: Verify the relevant failure**

Run: `./tools/run_host_tests.sh`
Expected: non-zero exit from `test_activity_launcher_app_manager_contract.py`, specifically reporting the missing `isTimestampWithinAge` helper or guarded predicate; unrelated host/source tests remain passing up to that assertion.

- [ ] **Step 3: Implement the minimum behavior**

Add:

`private fun isTimestampWithinAge(timestamp: Long, now: Long, maxAgeMs: Long): Boolean = timestamp > 0L && timestamp <= now && now - timestamp <= maxAgeMs`

Replace only the six age-specific inline predicates in `filteredApps()` with calls to the helper. Keep `InstallAgeFilter.ALL -> sourceFiltered` and `UpdateAgeFilter.ALL -> ageFiltered` unchanged.

Edge cases:
- `timestamp <= 0L` => rejected.
- `timestamp > now` => rejected.
- `timestamp == now` => accepted for every positive max age.
- `now - timestamp == maxAgeMs` => accepted.
- `now - timestamp > maxAgeMs` => rejected.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py`
Expected: `ACTIVITY_LAUNCHER_APP_MANAGER_CONTRACT: PASS` and exit 0.

- [ ] **Step 5: Run the affected integration check**

Run: `./tools/run_host_tests.sh`
Expected: full host/source suite exit 0. Then use the repository PR Android CI and require `Source contracts`, `Enterprise debug APK`, `Verify APK exists`, and final `verify` to complete successfully on the exact PR head.

- [ ] **Step 6: Commit the passing deliverable**

Commit only the test and production change after RED was observed. Merge only with an exact-head SHA guard after fresh full GREEN CI.

## Unresolved product decisions

None.
