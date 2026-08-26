# DPC-AIO Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first buildable DPC-AIO workspace with a tested capability execution core and Android module boundaries ready for DevicePolicyManager, Permission Manager, Activity Launcher, Installer, and delegation adapters.

**Architecture:** Keep decision logic in pure Kotlin modules so API/ABI routing is testable without Android. Android-facing modules implement narrow adapters and feed verified outcomes back into the execution engine. Production and lab modules remain dependency-separated.

**Tech Stack:** Kotlin/JVM for core, Gradle Kotlin DSL project metadata, Android/Kotlin module skeletons for API 29–37, shell/Kotlin host verification for this checkpoint.

**Spec:** `docs/specs/dpc-aio-v1.md`

## Global Constraints
- minSdk 29; compileSdk 37; targetSdk 37.
- Supported native ABIs: arm64-v8a, armeabi-v7a, x86_64, x86.
- Runtime page-size support: 4 KiB and 16 KiB.
- `enterpriseRelease` must not depend on lab/Xposed/hook modules.
- Capability GREEN requires verification; simulation is never represented as a real permission grant.
- Installer source metadata and genuine store initiation are represented separately.

---

### Task 1: Capability model and execution planner

**Files:**
- Create: `modules/core/model/src/main/kotlin/io/dpcaio/core/model/Capability.kt`
- Create: `modules/core/model/src/main/kotlin/io/dpcaio/core/model/ExecutionRoute.kt`
- Create: `modules/core/execution/src/main/kotlin/io/dpcaio/execution/ExecutionPlanner.kt`
- Test: `modules/core/execution/src/test/kotlin/io/dpcaio/execution/ExecutionPlannerTest.kt`

**Interfaces:**
- Consumes: `CapabilityRequest`, `ExecutionRoute`.
- Produces: `ExecutionPlan plan(CapabilityRequest, List<ExecutionRoute>)` selecting only available, release-eligible routes and sorting by score.

- [x] **Step 1: Write failing planner tests** for availability, release eligibility, score ordering, and lab route separation.
- [x] **Step 2: Compile tests and verify RED** because production types do not exist.
- [x] **Step 3: Implement minimal model/planner code** matching the tests.
- [x] **Step 4: Compile/run tests and verify GREEN**.
- [x] **Step 5: Commit** `feat(core): add capability execution planner`.

### Task 2: Effective permission/capability state

**Files:**
- Create: `modules/permissions/core/src/main/kotlin/io/dpcaio/permission/PermissionState.kt`
- Create: `modules/permissions/core/src/main/kotlin/io/dpcaio/permission/EffectiveCapabilityResolver.kt`
- Test: `modules/permissions/core/src/test/kotlin/io/dpcaio/permission/EffectiveCapabilityResolverTest.kt`

**Interfaces:**
- Consumes: direct permission state plus verified route result.
- Produces: one of `GREEN_PERMISSION`, `GREEN_COMPAT`, `GREEN_SHIZUKU`, `GREEN_SYSTEM`, `LAB`, or `BLOCKED` while preserving raw grant state.

- [x] **Step 1: Write failing tests** proving raw denial can coexist with verified Shizuku/compat GREEN and lab never becomes real GREEN.
- [x] **Step 2: Verify RED**.
- [x] **Step 3: Implement minimal resolver**.
- [x] **Step 4: Verify GREEN**.
- [x] **Step 5: Commit** `feat(permission): model effective capabilities`.

### Task 3: Platform and CPU compatibility model

**Files:**
- Create: `modules/platform/compat/src/main/kotlin/io/dpcaio/platform/PlatformProfile.kt`
- Create: `modules/platform/compat/src/main/kotlin/io/dpcaio/platform/CompatibilityGate.kt`
- Test: `modules/platform/compat/src/test/kotlin/io/dpcaio/platform/CompatibilityGateTest.kt`

**Interfaces:**
- Consumes: API level, ABI, process bitness, page size.
- Produces: supported/unsupported result plus explicit compatibility findings.

- [x] **Step 1: Write failing tests** for API 29/37 acceptance, API 28 rejection, four supported ABIs, and 4096/16384 page sizes.
- [x] **Step 2: Verify RED**.
- [x] **Step 3: Implement gate**.
- [x] **Step 4: Verify GREEN**.
- [x] **Step 5: Commit** `feat(platform): add Android and ABI compatibility gate`.

### Task 4: Android module boundaries

**Files:**
- Create: `settings.gradle.kts`
- Create: root `build.gradle.kts`, `gradle.properties`
- Create module build descriptors and manifests for `app-dpc`, `policy-core`, `app-manager`, `activity-launcher`, `installer-core`, `delegation-core`, `dhizuku-compat`, `shizuku-adapter`, `native-diagnostics`, `lab-tools`.
- Create: `apps/dpc/src/main/AndroidManifest.xml`

**Interfaces:**
- `app-dpc` may depend on production modules only.
- `lab-tools` may depend on core modules, but no production module may depend on `lab-tools`.

- [x] **Step 1: Write structural verifier** `tools/verify_project.py` that fails if required modules or dependency boundaries are missing.
- [x] **Step 2: Verify RED** on the pre-module workspace.
- [x] **Step 3: Add Gradle/module skeletons** with API 29/37 constraints and one DeviceAdminReceiver entry point.
- [x] **Step 4: Verify GREEN** using the structural verifier.
- [x] **Step 5: Commit** `build: scaffold DPC-AIO Android modules`.

### Task 5: Reference provenance and release safety gate

**Files:**
- Create: `docs/provenance/SOURCES.md`
- Create: `tools/release_gate.py`
- Test: `tools/tests/test_release_gate.py`

**Interfaces:**
- Produces a release-gate report failing on forbidden production dependencies/strings such as Xposed entry points, lab module dependency, stealth `/proc` filtering, or release hook artifacts.

- [x] **Step 1: Write failing release-gate tests** against a deliberately unsafe fixture.
- [x] **Step 2: Verify RED** because gate does not exist.
- [x] **Step 3: Implement release gate and source provenance**.
- [x] **Step 4: Verify GREEN** on safe workspace and expected failure on unsafe fixture.
- [x] **Step 5: Commit** `chore: add provenance and release safety gate`.
