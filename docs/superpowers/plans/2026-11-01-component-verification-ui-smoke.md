# Component Verification UI + Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show verified target-user component mutation readback consistently in Autostart and Activity Explorer, and gate Enable & Launch on verified ENABLED state.

**Architecture:** Keep Shizuku execution semantics in `ShizukuComponentStateExecutor`. Add a small app-level formatter that converts `ShizukuComponentMutationResult` plus requested/user/component context into stable UI text and a launch eligibility boolean. Both Activities consume that formatter. A source-contract smoke test pins the full UserService v4 -> executor -> formatter -> UI path.

**Tech Stack:** Kotlin, Android, Shizuku UserService v4, Python source-contract tests, GitHub Actions Android CI.

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

### Task 1: Shared component mutation presentation

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ComponentMutationPresentation.kt`
- Test: `tools/tests/test_component_verification_ui_smoke_contract.py`

**Interfaces:**
- Consumes: `ShizukuComponentMutationResult`, `ComponentOverrideState`.
- Produces: `ComponentMutationPresentation.format(...) : String` and `ComponentMutationPresentation.canLaunchAfterEnable(...) : Boolean`.

- [ ] **Step 1: Write the failing source-contract smoke test** requiring stable tokens for verified, mismatch, observed state, UserService v4, and both UI consumers.
- [ ] **Step 2: Run via PR CI and confirm RED before implementation.**
- [ ] **Step 3: Implement the shared formatter and launch gate.**
- [ ] **Step 4: Re-run CI and confirm the focused contract passes.**

### Task 2: Autostart verified status

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AutostartManagerActivity.kt`
- Test: `tools/tests/test_component_manager_ui_contract.py`

**Interfaces:**
- Consumes: `ComponentMutationPresentation.format(...)`.
- Produces: target-user status lines containing requested, observed, verified and detail.

- [ ] **Step 1: Extend the UI contract with RED requirements for verified/observed target-user output.**
- [ ] **Step 2: Replace `TARGET_USER_SUBMITTED` success wording with formatter-backed verified output while preserving failure wording.**
- [ ] **Step 3: Keep cached requested state only after a verified result.**

### Task 3: Activity Explorer verified single/batch/restore flows

**Files:**
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt`
- Test: `tools/tests/test_component_manager_ui_contract.py`

**Interfaces:**
- Consumes: shared formatter and `canLaunchAfterEnable(...)`.
- Produces: verified Shizuku status in single mutation, batch, and snapshot restore flows.

- [ ] **Step 1: Make single-component Shizuku status display requested/observed/verified.**
- [ ] **Step 2: Gate `Enable & Launch` on verified ENABLED readback, not `SUBMITTED` text.**
- [ ] **Step 3: Make batch lines display the same verified result.**
- [ ] **Step 4: Capture and display snapshot-restore Shizuku mutation results.**

### Task 4: Full verification and merge

**Files:**
- Verify all changed files through Android CI.

- [ ] **Step 1: Run full Source contracts job.**
- [ ] **Step 2: Run Enterprise debug APK build and APK existence check.**
- [ ] **Step 3: Require aggregate `verify` PASS on the final frozen head.**
- [ ] **Step 4: Review PR comments/diff and squash merge only with the expected final head SHA.**
