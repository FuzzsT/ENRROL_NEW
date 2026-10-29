# Dual App Multi Clone Manager Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Dual Apps with a bounded Preview → Execute multi-clone workflow that creates up to 20 Android profile workspaces and installs one selected existing package into each workspace without repackaging APKs or escalating privileges.

**Architecture:** Keep all device mutation behind the existing typed Shizuku user-service boundary. Add deterministic planning/parsing helpers to the app layer, expose per-user package-presence/removal operations through typed AIDL/service/client methods, and make the UI execute one workspace at a time while recording SUCCESS/PARTIAL/FAILED/SKIPPED outcomes. Existing single-workspace controls remain available.

**Tech Stack:** Kotlin/Android, AIDL, Shizuku user service, Python host/source contract tests, GitHub Actions Android CI.

## Global Constraints

- Preview must occur before Execute and must show package, profile type, base name, requested count, and generated profile names.
- Batch size is limited to 1..20 copies.
- Profile creation is sequential; each successful profile is started before package installation.
- The package is reused with `install-existing`; package name/signature must not be changed and APKs must not be repackaged.
- Android/OEM profile limits remain authoritative; the UI must report partial success rather than claiming unlimited copies.
- Retry applies only to FAILED/PARTIAL entries and must not duplicate SUCCESS entries.
- No automatic destructive rollback. Cleanup is an explicit user action and only targets workspaces created by the active batch.
- UID 1000 remains read-only diagnostics. No obtain/spoof/escalation path and no arbitrary shell executor.
- Existing single-workspace create/start/stop/remove/install behavior must continue to work.

---

### Task 1: Add RED contract for bounded multi-clone behavior

**Files:**
- Create: `tools/tests/test_dual_app_multi_clone_contract.py`
- Modify: `tools/run_host_tests.sh`

**Interfaces:**
- Consumes: existing `DualAppWorkspaceActivity`, `IAioShizukuUserService`, `AioShizukuUserService`, `ShizukuUserServiceClient`.
- Produces: source-contract requirements for Preview/Execute, max batch 20, typed package listing/removal, batch outcome states, retry and explicit cleanup.

- [ ] **Step 1: Add the focused failing test**

Assert the source contains these observable contracts:
- UI text/tokens: `Multi Clone Manager`, `Preview batch`, `Execute batch`, `Retry failed`, `Cleanup batch workspaces`, `MAX_BATCH_COPIES = 20`, and outcome labels `SUCCESS`, `PARTIAL`, `FAILED`, `SKIPPED`.
- AIDL/client/service typed methods: `listPackagesForUser(int userId)` and `uninstallPackageForUser(String packageName, int userId)`.
- Service commands remain typed: `pm list packages --user <id>` and `pm uninstall --user <id> <package>`; no generic command string parameter.
- Explicit statement that UID 1000 diagnostics remain read-only.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_dual_app_multi_clone_contract.py`
Expected: assertion failure because the new multi-clone tokens/interfaces do not yet exist.

- [ ] **Step 3: Wire the test into host tests**

Add the new test to `tools/run_host_tests.sh` beside the existing Dual Apps contract.

- [ ] **Step 4: Verify host suite fails for the same missing behavior**

Run: `./tools/run_host_tests.sh`
Expected: existing tests pass until `test_dual_app_multi_clone_contract.py`, which fails on a missing multi-clone contract token.

- [ ] **Step 5: Commit the RED contract**

Commit message: `test: define dual app multi-clone contract`.

### Task 2: Add typed per-profile package inspection and cleanup primitives

**Files:**
- Modify: `apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl`
- Modify: `apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt`
- Modify: `apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt`

**Interfaces:**
- Produces: `String listPackagesForUser(int userId)` and `int uninstallPackageForUser(String packageName, int userId)`.
- Validation: `userId > 0`; package must pass existing `validPackage` validation.

- [ ] **Step 1: Extend the contract test assertions for exact typed commands**

Require service source to contain explicit argument lists equivalent to:
`/system/bin/pm list packages --user <id>` and `/system/bin/pm uninstall --user <id> <package>`.

- [ ] **Step 2: Verify RED remains relevant**

Run: `python3 tools/tests/test_dual_app_multi_clone_contract.py`
Expected: failure on the missing AIDL/service/client methods.

- [ ] **Step 3: Implement the minimum typed methods**

`listPackagesForUser` returns `INVALID_ARGUMENT` for `userId <= 0`; otherwise captures `pm list packages --user` output.
`uninstallPackageForUser` returns `RESULT_INVALID_ARGUMENT` for invalid package or `userId <= 0`; otherwise executes typed `pm uninstall --user`.
No generic shell command input is introduced.

- [ ] **Step 4: Verify focused and existing contracts**

Run: `python3 tools/tests/test_dual_app_multi_clone_contract.py` and `python3 tools/tests/test_dual_app_workspace_contract.py`.
Expected: package primitive assertions pass; remaining UI assertions may still fail until Task 3; existing Dual Apps contract stays PASS.

- [ ] **Step 5: Commit the passing integration primitive**

Commit message: `feat: add typed dual-app package workspace operations`.

### Task 3: Implement Preview → Execute batch state machine in Dual Apps UI

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DualAppWorkspaceActivity.kt`

**Interfaces:**
- Consumes: `createProfile(name, cloneProfile)`, `startProfile(userId)`, `installExistingPackage(packageName, userId)`, `listPackagesForUser(userId)`, `uninstallPackageForUser(packageName, userId)`, `removeProfile(userId)`.
- Produces internal models: batch plan entries with planned name/index and execution entries with `SUCCESS`, `PARTIAL`, `FAILED`, or `SKIPPED` plus optional created user ID and error/result detail.

- [ ] **Step 1: Add focused source assertions for deterministic planning**

Require count validation `1..MAX_BATCH_COPIES`, deterministic names `<base> 1` through `<base> N`, and an execution guard that rejects Execute when there is no current preview.

- [ ] **Step 2: Verify RED on UI behavior**

Run: `python3 tools/tests/test_dual_app_multi_clone_contract.py`
Expected: failure on `Multi Clone Manager`/preview/execute/batch-state tokens.

- [ ] **Step 3: Implement preview**

Add selected app + profile type + base name + copy-count inputs. `Preview batch` validates Shizuku permission, selected package, profile name syntax compatible with service validation, and count 1..20. It renders generated workspace names and stores an immutable current plan. Any input change invalidates the previous plan before Execute.

- [ ] **Step 4: Implement sequential execute**

Run work on a background thread. For each planned entry:
1. create profile;
2. parse the created user ID from the success output;
3. start profile;
4. call `installExistingPackage`;
5. verify presence using `listPackagesForUser`.
Record `SUCCESS` only when create/start/install/presence verification succeed. Record `PARTIAL` when a profile was created but a later step fails. Record `FAILED` when profile creation fails. `SKIPPED` is used for entries intentionally not retried because they already succeeded. Stop only on cancellation/activity destruction; ordinary per-entry failures do not abort later entries.

- [ ] **Step 5: Implement retry and explicit cleanup**

`Retry failed` reruns only FAILED/PARTIAL entries. If PARTIAL has a created user ID, reuse that profile when it still exists instead of creating a duplicate; otherwise recreate it. `Cleanup batch workspaces` operates only on user IDs recorded as created by the active batch, first attempts package uninstall for diagnostic cleanliness, then stops/removes those profiles. It never targets pre-existing profiles.

- [ ] **Step 6: Verify focused and full host tests**

Run: `python3 tools/tests/test_dual_app_multi_clone_contract.py` then `./tools/run_host_tests.sh`.
Expected: both PASS.

- [ ] **Step 7: Commit the UI/state-machine deliverable**

Commit message: `feat: add previewed multi-clone batches to Dual Apps`.

### Task 4: Validate Android build and PR CI

**Files:**
- Modify only if tests reveal a necessary compatibility fix; no unrelated refactor.

**Interfaces:**
- Consumes the existing PR workflow jobs `Source contracts`, `Enterprise debug APK`, and aggregate `verify`.
- Produces a reviewable PR from `feature/dual-app-multi-clone-manager` to `main`.

- [ ] **Step 1: Run source contracts**

Run: `./tools/run_host_tests.sh`
Expected: PASS including both Dual Apps contracts.

- [ ] **Step 2: Run Android enterprise debug build**

Run: `./gradlew :app-dpc:assembleEnterpriseDebug` with the repository's existing Android/provisioning environment.
Expected: successful APK assembly and provisioning QR generation.

- [ ] **Step 3: Open PR and require CI**

PR title: `Add previewed multi-clone batches to Dual Apps`.
Expected GitHub Actions: `Source contracts` success, `Enterprise debug APK` success, `verify` success.

- [ ] **Step 4: Merge only on exact-head GREEN**

Use squash merge with the fresh expected head SHA. After merge, require `Enrollment build + release` to pass build, signer verification, exact QR/APK binding, Device Owner runtime smoke, artifact upload, publish, and published APK byte-identity verification before declaring completion.

## Unresolved externally observable decisions

None. The approved design fixes preview-before-execute, 1..20 batch count, sequential execution, retry-only-failures, explicit cleanup, and non-escalating UID diagnostics.
