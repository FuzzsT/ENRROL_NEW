# Install Queue + History Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a durable FIFO APK/APKS install/update queue with callback-driven sequencing, retry, restart recovery, and bounded local history while preserving Android PackageInstaller security semantics.

**Architecture:** Introduce a focused queue model/store/coordinator in the app layer. `AppPackageManagerActivity` imports files and renders queue/history state, while `AppInstallStatusReceiver` correlates PackageInstaller callbacks to a stable queue item ID. Existing APK/APKS parsing, signer continuity, downgrade protection, and PackageInstaller adapter logic remain the execution boundary and are reused rather than bypassed.

**Tech Stack:** Kotlin/Android, Android `PackageInstaller`, `SharedPreferences`/JSON-compatible app-private persistence, Python source-contract tests, Gradle/GitHub Actions.

## Global Constraints

- Strict FIFO execution.
- At most one queue item may be in `PREFLIGHT`, `WAITING_USER`, or `INSTALLING` at once.
- The next item starts only after the previous item reaches a terminal state.
- Preserve existing APK/APKS path/size/split checks, package matching, downgrade blocking, and signer continuity.
- Preserve Android-required user confirmation.
- No shell/root install path, `pm install`, `su`, APK repackaging, package rewriting, signer bypass, or silent-install bypass.
- Retry only `FAILED` items.
- `Clear completed` removes terminal queue rows without deleting history.
- Persist queue state and recover interrupted in-flight items without blindly recommitting them.
- Keep the newest 100 metadata-only history records.
- Do not persist signing secrets or original external filesystem paths.

---

### Task 1: Add queue models, durable store, and state-machine tests

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueModels.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueStore.kt`
- Create: `apps/dpc/app/src/test/kotlin/io/dpcaio/app/InstallQueueStoreTest.kt` if app JVM tests are wired; otherwise add equivalent focused Python source contract under `tools/tests/`.
- Modify: `tools/tests/test_apk_plus_110_contract.py`

**Interfaces:**
- Produces `enum class InstallQueueState { QUEUED, PREFLIGHT, WAITING_USER, INSTALLING, SUCCESS, FAILED, SKIPPED }`.
- Produces `data class InstallQueueItem(...)` containing stable item ID, enqueue time, mode, private imported-file reference, display name, file kind, optional updater target package, optional discovered package/version, state, optional session ID, result code, summary.
- Produces `data class InstallHistoryEntry(...)` containing completion time, queue item ID, package/version when known, APK/APKS type, mode, terminal result, short message.
- Produces `InstallQueueStore.loadQueue()`, `replaceQueue(...)`, `updateItem(...)`, `loadHistory()`, `appendHistory(...)`, `clearCompleted()`.

- [ ] **Step 1: Add the focused failing test**

Extend `tools/tests/test_apk_plus_110_contract.py` to require `InstallQueueModels.kt` and `InstallQueueStore.kt`, state tokens `QUEUED`, `WAITING_USER`, `INSTALLING`, `FAILED`, and a history cap constant/value of 100. Add focused logic tests where the repository test harness supports them for: history trimming to newest 100, terminal-row clearing preserving history, and persisted queue round-trip.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_apk_plus_110_contract.py`
Expected: failure because `InstallQueueModels.kt` or `InstallQueueStore.kt` is missing.

- [ ] **Step 3: Implement the minimum behavior**

Use app-private persistence. Serialize only queue/history metadata and private imported-file references. `appendHistory` must trim oldest records beyond 100. `clearCompleted` removes only `SUCCESS`, `FAILED`, and `SKIPPED` queue rows. No store method starts installs.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_apk_plus_110_contract.py`
Expected: PASS for the new source contract.

- [ ] **Step 5: Run the affected integration check**

Run: `./tools/run_host_tests.sh`
Expected: full host/source suite PASS.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueModels.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueStore.kt tools/tests/test_apk_plus_110_contract.py
git commit -m "feat: add durable install queue state"
```

### Task 2: Add callback-driven InstallQueueCoordinator

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueCoordinator.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppInstallStatusReceiver.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt`
- Test: `tools/tests/test_apk_plus_110_contract.py`

**Interfaces:**
- Produces `InstallQueueCoordinator.enqueue(...)`, `startOrResume()`, `onPackageInstallerResult(itemId, sessionId, status, packageName, message)`, `retryFailed()`, `clearCompleted()`, and `recoverInterrupted()`.
- `statusIntentSender(sessionId, itemId)` includes stable queue item ID and session ID extras.
- `AppInstallStatusReceiver` routes final/pending callbacks to the coordinator/store and still launches Android's pending confirmation intent when required.

- [ ] **Step 1: Add the focused failing test**

Require `InstallQueueCoordinator`, queue item correlation extra names, and callback handling tokens in the source contract. Add focused state-machine tests for FIFO selection, one-active-item invariant, no advancement before final callback, callback correlation by item ID, retry only failed, and interrupted-state recovery without duplicate commit.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_apk_plus_110_contract.py`
Expected: failure because `InstallQueueCoordinator` and queue correlation are missing.

- [ ] **Step 3: Implement the minimum behavior**

Move queue orchestration out of the activity. The coordinator selects the oldest `QUEUED` item only when no item is active. It sets `PREFLIGHT`, runs the existing validation/install path, stores the allocated session ID, and transitions to `WAITING_USER`/`INSTALLING` after commit. A non-pending final callback writes `SUCCESS` or `FAILED`, appends history, releases the active slot, cleans private package bytes when no retry/reference requires them, then starts the next item. Pending-user-action callbacks must not advance the queue. `recoverInterrupted()` converts unreconciled `PREFLIGHT`, `WAITING_USER`, or `INSTALLING` items to `FAILED` with `INTERRUPTED_RECHECK_REQUIRED` rather than recommitting.

- [ ] **Step 4: Verify the focused pass**

Run: focused queue tests plus `python3 tools/tests/test_apk_plus_110_contract.py`
Expected: FIFO/correlation/recovery assertions PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `./tools/run_host_tests.sh`
Expected: all existing package trust/APKS/signer contracts remain PASS.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add apps/dpc/app/src/main/kotlin/io/dpcaio/app/InstallQueueCoordinator.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppInstallStatusReceiver.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt tools/tests/test_apk_plus_110_contract.py
git commit -m "feat: sequence package installs through queue callbacks"
```

### Task 3: Add multi-select import, queue controls, and history UI

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt`
- Test: `tools/tests/test_apk_plus_110_contract.py`

**Interfaces:**
- Picker normalizer consumes `Intent.data` and `Intent.clipData` and produces an ordered de-duplicated list of URIs.
- UI actions call coordinator methods only; they do not create competing PackageInstaller sessions directly.

- [ ] **Step 1: Add the focused failing test**

Require `Intent.EXTRA_ALLOW_MULTIPLE`, `clipData`, `Add to queue`, `Add multiple`, `Start / Resume queue`, `Retry failed`, `Clear completed`, and `History`. Add normalization tests for single URI, multiple ordered URIs, and duplicate URI de-duplication.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_apk_plus_110_contract.py`
Expected: failure on the first missing queue UI/multi-select token.

- [ ] **Step 3: Implement the minimum behavior**

Keep existing picker MIME types. Single add may use `data`; multiple import sets `EXTRA_ALLOW_MULTIPLE=true` and normalizes both sources. Copy selected files to app-private storage before enqueue. Render queue state/result rows and newest history entries. `Retry failed` calls the coordinator; `Clear completed` preserves history. Clearly state that Android may show confirmation for each item and the queue pauses until that result arrives.

- [ ] **Step 4: Verify the focused pass**

Run: focused normalization tests and `python3 tools/tests/test_apk_plus_110_contract.py`
Expected: PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `./gradlew --no-daemon --stacktrace :app-dpc:assembleEnterpriseDebug`
Expected: enterprise debug APK assembles successfully.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt tools/tests/test_apk_plus_110_contract.py
git commit -m "feat: add install queue controls and history UI"
```

### Task 4: Full regression gate and PR validation

**Files:**
- Modify only files required by concrete regression failures found by the full checks.

**Interfaces:**
- No new product interfaces; this task validates the completed queue against repository release gates.

- [ ] **Step 1: Run the full host/source suite**

Run: `./tools/run_host_tests.sh`
Expected: PASS, including existing APK/APKS, signer, downgrade, PackageInstaller, dashboard, and release contracts.

- [ ] **Step 2: Build the enterprise debug APK**

Run: `./gradlew --no-daemon --stacktrace :app-dpc:assembleEnterpriseDebug`
Expected: BUILD SUCCESSFUL and APK exists.

- [ ] **Step 3: Open/update PR and verify Android CI**

Expected GitHub checks: `Source contracts=success`, `Enterprise debug APK=success`, `verify=success`.

- [ ] **Step 4: Fix only demonstrated regressions**

For each failure, capture the exact failing assertion/compiler diagnostic, add or preserve a focused regression test, make the minimum correction, and rerun the affected job before rerunning the aggregate gate.

- [ ] **Step 5: Merge only with exact head SHA guard after full GREEN**

Use squash merge with the current PR head SHA. After merge, verify the post-merge `Enrollment build + release` workflow through signed build, signer/QR verification, Device Owner runtime smoke, artifact upload, publish, and published APK byte identity.

## Unresolved product decisions

None. The approved design fixes sequencing, retry, history retention, restart recovery, multi-select behavior, confirmation semantics, and security boundaries.
