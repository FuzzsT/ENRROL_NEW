# Account Manager 0.4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Google account inventory, DPC-local account selection, and a verified guided system-order reorder workflow without claiming Android has a public global primary-account setter.

**Architecture:** Pure Kotlin `account-manager` owns account order models and reorder planning. Android `account-android` owns AccountManager/DevicePolicyManager integration, account chooser/remove/add flows, and account-management policy inspection. `app-dpc` depends on both modules; system-wide reorder is an explicit guided workflow because Google authenticator removal/re-add may require user interaction.

**Tech Stack:** Kotlin/JVM core, Android AccountManager, DevicePolicyManager, Settings intents, existing DPC-AIO policy/result conventions.

**Spec:** `docs/specs/dpc-aio-v1.md`

## Global Constraints
- minSdk = 29
- compileSdk = 37
- targetSdk = 37
- Account type for Google inventory is `com.google`.
- Never store Google credentials or auth tokens.
- `PRIMARY_FOR_AIO` must not be represented as a global Android primary-account change.
- `SYSTEM_ORDER_REORDER` requires explicit user confirmation and read-back verification.
- Existing enterprise release gates remain unchanged.

---

### Task 1: Pure account order planner
**Files:**
- Create: `modules/account/core/src/main/kotlin/io/dpcaio/account/AccountModel.kt`
- Create: `modules/account/core/src/main/kotlin/io/dpcaio/account/AccountPriorityPlanner.kt`
- Create: `modules/account/core/src/test/kotlin/io/dpcaio/account/AccountPriorityPlannerTest.kt`

**Interfaces:**
- Produces: `AccountRecord`, `AccountPriorityPlan`, `AccountPriorityPlanner.plan()`.

- [ ] Write failing test for target already first, target later, and target absent.
- [ ] Run test and observe RED.
- [ ] Implement minimal planner.
- [ ] Run test and observe GREEN.

### Task 2: Android account inventory and chooser
**Files:**
- Create: `modules/account/android/build.gradle.kts`
- Create: `modules/account/android/src/main/kotlin/io/dpcaio/account/android/AndroidGoogleAccountRepository.kt`
- Create: `modules/account/android/src/main/kotlin/io/dpcaio/account/android/GoogleAccountIntentFactory.kt`
- Modify: `settings.gradle.kts`
- Modify: `apps/dpc/build.gradle.kts`
- Modify: `apps/dpc/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: account inventory from `AccountManager.getAccountsByType("com.google")` and chooser/add-account intents.

- [ ] Add Android contract test first.
- [ ] Run contract test and observe RED.
- [ ] Implement repository and intent factory.
- [ ] Run contract test and observe GREEN.

### Task 3: Guided system-order reorder coordinator
**Files:**
- Create: `modules/account/core/src/main/kotlin/io/dpcaio/account/AccountReorderCoordinator.kt`
- Create: `modules/account/core/src/test/kotlin/io/dpcaio/account/AccountReorderCoordinatorTest.kt`
- Create: `modules/account/android/src/main/kotlin/io/dpcaio/account/android/AndroidAccountReorderGateway.kt`

**Interfaces:**
- Produces typed steps `REMOVE_BEFORE_TARGET`, `RE_ADD_AFTER_TARGET`, `VERIFY_ORDER` and Android removal/policy capabilities.

- [ ] Write failing state-machine test.
- [ ] Run RED.
- [ ] Implement coordinator.
- [ ] Run GREEN.
- [ ] Add Android gateway using public APIs only.

### Task 4: Verification and checkpoint
**Files:**
- Modify: `tools/run_host_tests.sh`
- Modify: `tools/verify_project.py`
- Modify: `tools/verify_android_contracts.py`
- Modify: `tools/tests/test_android_contracts.py`
- Create: `CHECKPOINT-0.4.0.md`

- [ ] Run full host suite.
- [ ] Run release gate.
- [ ] Attempt Gradle Android build and record environmental blocker if still offline.
- [ ] Create password-protected checkpoint ZIP and verify archive.
