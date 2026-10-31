# Component Verification UI + Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show verified target-user component mutation readback consistently in Autostart and Activity Explorer, and gate Enable & Launch on verified ENABLED state.

**Architecture:** Keep Shizuku execution semantics in `ShizukuComponentStateExecutor`. Put the reusable verification status/launch decision in the pure `activity/core` module so the host suite can execute VERIFIED / MISMATCH / UNKNOWN behavior directly. Autostart and Activity Explorer map `ShizukuComponentMutationResult` fields into that helper, while source contracts pin the Android call sites.

**Tech Stack:** Kotlin, Android, Shizuku UserService v4, Kotlin host tests, Python source-contract tests, GitHub Actions Android CI.

**Spec:** `docs/superpowers/specs/2026-11-01-component-verification-ui.md`

## Global Constraints

- No new Android permissions.
- No hidden API, ADB bypass or privilege-boundary expansion.
- Preserve protected DPC/recovery component guards.
- Unknown or mismatched target-user readback remains fail-closed.
- `Enable & Launch` may proceed through Shizuku only after verified observed state `ENABLED`.

## Review Focus

- Verified result with requested=ENABLED and observed=ENABLED permits launch-after-enable.
- Mismatch result never reports success and never triggers launch.
- Unknown readback remains explicit rather than being rewritten as submitted/success.
- Batch status includes per-component requested and observed state.
- Snapshot restore reports the verified Shizuku result instead of silently discarding it.

---

### Task 1: Shared component mutation verification

**Files:**
- Create: `apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ComponentMutationVerification.kt`
- Test: `apps/dpc/modules/activity/core/src/test/kotlin/io/dpcaio/activity/ComponentControlTest.kt`

**Interfaces:**
- Consumes: `ComponentOverrideState`, plus observed/verified/detail values supplied by the Android integration layer.
- Produces: `ComponentMutationVerification.statusLine(...) : String` and `ComponentMutationVerification.canLaunchAfterEnable(...) : Boolean`.

- [x] **Step 1: Write the failing host runtime smoke** for VERIFIED, MISMATCH and UNKNOWN plus the launch gate.
- [x] **Step 2: Run via PR CI and confirm RED before implementation.** Android CI #850 failed on unresolved `ComponentMutationVerification`.
- [x] **Step 3: Implement the shared core helper and launch gate.**
- [ ] **Step 4: Re-run CI and confirm the host smoke passes.**

### Task 2: Autostart verified status

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AutostartManagerActivity.kt`
- Test: `tools/tests/test_component_manager_ui_contract.py`
- Test: `tools/tests/test_activity_autostart_target_picker_contract.py`

**Interfaces:**
- Consumes: `ComponentMutationVerification.statusLine(...)`.
- Produces: target-user status lines containing requested, observed, verified and detail.

- [x] **Step 1: Extend source contracts for verified/observed target-user output.**
- [x] **Step 2: Replace `TARGET_USER_SUBMITTED` success wording with `TARGET_USER_VERIFIED` and formatter-backed output.**
- [x] **Step 3: Keep cached requested state only after a verified result.**

### Task 3: Activity Explorer verified single/batch/restore flows

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt`
- Test: `tools/tests/test_component_manager_ui_contract.py`
- Test: `tools/tests/test_activity_autostart_target_picker_contract.py`

**Interfaces:**
- Consumes: shared verification status and `canLaunchAfterEnable(...)`.
- Produces: verified Shizuku status in single mutation, batch, and snapshot restore flows.

- [x] **Step 1: Make single-component Shizuku status display requested/observed/verified.**
- [x] **Step 2: Gate `Enable & Launch` on verified ENABLED readback, not `SUBMITTED` text.**
- [x] **Step 3: Make batch lines display the same verified result.**
- [x] **Step 4: Capture and display snapshot-restore Shizuku mutation results.**

### Task 4: Full verification and merge

**Files:**
- Verify all changed files through Android CI.

- [ ] **Step 1: Run full Source contracts job.**
- [ ] **Step 2: Run Enterprise debug APK build and APK existence check.**
- [ ] **Step 3: Require aggregate `verify` PASS on the final frozen head.**
- [ ] **Step 4: Review PR comments/diff and squash merge only with the expected final head SHA.**
