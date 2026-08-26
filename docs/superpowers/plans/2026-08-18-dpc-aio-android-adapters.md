# DPC-AIO Android Adapters 0.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tested policy, permission, activity/component, and installer planning layers plus concrete public-API Android adapters for the 0.2 development checkpoint.

**Architecture:** Keep route selection and policy results in pure Kotlin so they can be verified without Android SDK. Android-facing modules are narrow adapters around DevicePolicyManager, PackageManager/LauncherApps/AppOpsManager, and PackageInstaller; they never redefine raw permission state and never depend on lab modules. Android adapter source is additionally checked by a static contract verifier because this sandbox cannot download the Android/Gradle toolchain.

**Tech Stack:** Kotlin/JVM host tests, Android API 29-37 adapters, Gradle Kotlin DSL metadata, Python structural/release verification.

**Spec:** `docs/specs/dpc-aio-v1.md`

## Global Constraints
- minSdk 29; compileSdk 37; targetSdk 37.
- Supported native ABIs: arm64-v8a, armeabi-v7a, x86_64, x86.
- Production adapters use public Android APIs only.
- `enterpriseRelease` has no dependency on `lab-tools`.
- Permission grants and effective capabilities are distinct.
- `com.android.vending` attribution is not classified as genuine Play unless initiating and installing package names both verify as Play.
- Component launch planning never treats `exported=false` as bypassable by Device Owner alone.

---

### Task 1: Typed policy gateway contract

**Files:**
- Create: `modules/policy/core/src/main/kotlin/io/dpcaio/policy/PolicyResult.kt`
- Create: `modules/policy/core/src/main/kotlin/io/dpcaio/policy/DevicePolicyGateway.kt`
- Test: `modules/policy/core/src/test/kotlin/io/dpcaio/policy/PolicyResultTest.kt`

**Interfaces:**
- Produces: `PolicyResult<T>`, `PackagePolicyGateway`, `PermissionPolicyGateway`.
- Result statuses distinguish success, unsupported authority, platform rejection, package not found, and failure.

- [x] Step 1: Write test proving result status preserves success/error semantics.
- [x] Step 2: Compile and verify RED because policy result classes do not exist.
- [x] Step 3: Implement the minimal policy result/gateway contracts.
- [x] Step 4: Compile/run and verify GREEN.
- [x] Step 5: Commit `feat(policy): add typed device policy gateway contract`.

### Task 2: Android DevicePolicyManager adapter

**Files:**
- Create: `modules/policy/core/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt`
- Create: `tools/verify_android_contracts.py`
- Test: extend `tools/verify_android_contracts.py` self-check fixture behavior through `tools/tests/test_android_contracts.py`.

**Interfaces:**
- Consumes: `Context`, admin `ComponentName`, public `DevicePolicyManager` APIs.
- Produces: hide/unhide, suspend/unsuspend, runtime permission grant state operations with typed `PolicyResult`.

- [x] Step 1: Write static contract test requiring the three public DPM calls and forbidding hidden API/reflection patterns.
- [x] Step 2: Verify RED.
- [x] Step 3: Implement Android adapter and contract verifier.
- [x] Step 4: Verify GREEN using Python static test.
- [x] Step 5: Commit `feat(policy): add Android DevicePolicyManager adapter`.

### Task 3: Permission inspection and action planning

**Files:**
- Create: `modules/permissions/core/src/main/kotlin/io/dpcaio/permission/PermissionInspection.kt`
- Create: `modules/permissions/core/src/main/kotlin/io/dpcaio/permission/PermissionActionPlanner.kt`
- Create: `modules/permissions/core/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionInspector.kt`
- Test: `modules/permissions/core/src/test/kotlin/io/dpcaio/permission/PermissionActionPlannerTest.kt`

**Interfaces:**
- Produces: raw runtime state, AppOp state, DPC-manageability and ordered recommended actions.
- Android inspector reads `PackageManager.checkPermission` and `AppOpsManager.checkOpNoThrow`; it does not modify AppOps.

- [x] Step 1: Write planner tests for DPC grant, user-action fallback, verified alternative route, and blocked cases.
- [x] Step 2: Verify RED.
- [x] Step 3: Implement model/planner and Android inspector.
- [x] Step 4: Verify GREEN plus Android contract verifier.
- [x] Step 5: Commit `feat(permission): add inspection and action planner`.

### Task 4: Activity/component access planning

**Files:**
- Create: `modules/activity/core/src/main/kotlin/io/dpcaio/activity/ComponentModel.kt`
- Create: `modules/activity/core/src/main/kotlin/io/dpcaio/activity/ActivityAccessPlanner.kt`
- Create: `modules/activity/core/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt`
- Test: `modules/activity/core/src/test/kotlin/io/dpcaio/activity/ActivityAccessPlannerTest.kt`

**Interfaces:**
- Produces: explicit/launcher/deep-link/cross-profile/companion/Shizuku route candidates based on exported/enabled/user/profile state.
- A non-exported activity is never marked directly launchable unless same UID is already true or a companion route exists.

- [x] Step 1: Write tests for launcher-visible, exported-hidden, non-exported same-UID, and companion fallback cases.
- [x] Step 2: Verify RED.
- [x] Step 3: Implement model/planner and public API inventory adapter.
- [x] Step 4: Verify GREEN plus static Android contracts.
- [x] Step 5: Commit `feat(activity): add component access planner`.

### Task 5: Installer plan and public PackageInstaller adapter

**Files:**
- Create: `modules/installer/core/src/main/kotlin/io/dpcaio/installer/InstallPlan.kt`
- Create: `modules/installer/core/src/main/kotlin/io/dpcaio/installer/InstallPlanner.kt`
- Create: `modules/installer/core/src/main/kotlin/io/dpcaio/installer/android/AndroidPackageInstallerAdapter.kt`
- Test: `modules/installer/core/src/test/kotlin/io/dpcaio/installer/InstallPlannerTest.kt`

**Interfaces:**
- Produces: route choice among managed Play, DPC PackageInstaller, system privileged, Shizuku, user confirmation.
- `PLAY_COMPAT` requires real Play when configured; installer-of-record compatibility remains a distinct fallback.
- Android adapter configures `SessionParams` with API-gated public methods only.

- [x] Step 1: Write tests for genuine Play preference, DPC local install, installer-record fallback, and unavailable-route failure.
- [x] Step 2: Verify RED.
- [x] Step 3: Implement plan/planner and Android PackageInstaller adapter.
- [x] Step 4: Verify GREEN plus static contracts.
- [x] Step 5: Commit `feat(installer): add install planner and PackageInstaller adapter`.

### Task 6: Integrated verification and checkpoint

**Files:**
- Modify: `tools/run_host_tests.sh`
- Modify: `tools/verify_project.py`
- Create: `CHECKPOINT-0.2.md`

**Interfaces:**
- Runs all host Kotlin tests, Android contract checks, project verifier, and release gate.

- [x] Step 1: Add all new host tests to the runner and required source checks to project verifier.
- [x] Step 2: Run complete suite and fix only concrete failures.
- [x] Step 3: Attempt Gradle Android build; if toolchain/network is unavailable, capture exact blocker without claiming APK success.
- [x] Step 4: Run fresh full verification and release gate.
- [x] Step 5: Package development checkpoint and record SHA-256.
