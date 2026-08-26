# Enrollment UX and Release 0.9.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add progress/retry diagnostics UI, resume triggers, redacted export, version 0.9.0, and release verification gates.

**Architecture:** UI renders persisted state; boot/profile callbacks schedule resume rather than doing network work in receivers. Release scripts clearly separate source verification from APK/KME/zero-touch verification.

**Tech Stack:** Android Activity/Receiver, SharedPreferences, JSON, Python contract tests, shell release gates.

**Spec:** `docs/superpowers/specs/2026-08-22-enrollment-engine-0.9.0-design.md`

## Global Constraints
- No secret values in UI/export.
- No "ignore and finish" action.
- Receivers do not perform long-running network calls directly.

---

### Task 1: Enrollment Status and Diagnostics

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentStatusActivity.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentDiagnosticsSnapshot.kt`
- Modify: `apps/dpc/app/src/main/AndroidManifest.xml`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcDiagnosticsActivity.kt`
- Test: `tools/tests/test_enrollment_ui_contract.py`

**Interfaces:**
- Produces redacted `EnrollmentDiagnosticsSnapshot.toJson()`.

- [ ] **Step 1: RED UI contract**

```python
assert 'EnrollmentStatusActivity' in manifest
assert 'Retry' in activity
assert 'tokenFingerprint' in snapshot
assert 'enrollmentToken' not in snapshot_json_keys
```

- [ ] **Step 2: Implement status screen and redacted export**

```kotlin
appendLine("Stage: ${session.stage}")
appendLine("Token fingerprint: ${session.tokenFingerprint ?: "none"}")
```

- [ ] **Step 3: Add diagnostics entry and run contract**

```bash
python3 tools/tests/test_enrollment_ui_contract.py
```

### Task 2: Resume Receiver and Post-Provision Hook

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentRecoveryReceiver.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDeviceAdminReceiver.kt`
- Modify: `apps/dpc/app/src/main/AndroidManifest.xml`
- Test: `tools/tests/test_enrollment_recovery_contract.py`

**Interfaces:**
- Receiver calls `EnrollmentCoordinator.scheduleResume(context, trigger)` only.

- [ ] **Step 1: RED contract requiring LOCKED_BOOT_COMPLETED/BOOT_COMPLETED and no HTTP calls in receiver**

```python
assert 'LOCKED_BOOT_COMPLETED' in manifest
assert 'scheduleResume' in receiver
assert 'HttpURLConnection' not in receiver
```

- [ ] **Step 2: Implement minimal receiver and profile-provisioning hook**

```kotlin
EnrollmentCoordinator.scheduleResume(context, "PROFILE_PROVISIONING_COMPLETE")
```

- [ ] **Step 3: Run contract and Android provisioning contract**

```bash
python3 tools/tests/test_enrollment_recovery_contract.py
python3 tools/tests/test_provisioning_android_contract.py
```

### Task 3: Version and Release Gate

**Files:**
- Modify: `apps/dpc/app/build.gradle.kts`
- Create: `tools/tests/test_090_release_gate_contract.py`
- Modify: `tools/run_host_tests.sh`
- Modify: `tools/verify-before-push.sh`
- Modify: `plugins/chatgpt-companion/.codex-plugin/plugin.json`
- Modify: `plugins/chatgpt-companion/skills/dpc-aio-enrollment/SKILL.md`
- Create: `DPC-AIO-0.9.0-RELEASE-NOTES.md`

**Interfaces:**
- Version `0.9.0`, versionCode `17`, companion `0.1.6`.

- [ ] **Step 1: RED version/release test**

```python
assert 'versionCode = 17' in gradle
assert 'versionName = "0.9.0"' in gradle
for name in required_tests: assert name in host_script
```

- [ ] **Step 2: Bump version and wire all enrollment tests**

```kotlin
versionCode = 17
versionName = "0.9.0"
```

- [ ] **Step 3: Run source verification gates**

```bash
bash tools/verify-before-push.sh
bash tools/run_host_tests.sh
cd services/provisioning && node --test test/*.test.mjs
```

- [ ] **Step 4: Attempt Android assemble and classify separately**

```bash
DPC_AIO_PROVISIONING_APK_URL=https://example.invalid/DPC-AIO.apk ./gradlew :app-dpc:assembleEnterpriseDebug
```

If Gradle distribution/network is unavailable, report `APK_BUILD_VERIFIED = BLOCKED_BY_ENVIRONMENT`, never PASS.
