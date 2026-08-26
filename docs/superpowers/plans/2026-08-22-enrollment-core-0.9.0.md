# Enrollment Core 0.9.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add normalized enrollment configuration, state machine, retry classification, and device-protected session persistence.

**Architecture:** Pure Kotlin models live in `core-model`; Android persistence/adapters live in the app module. Provisioning activities become thin adapters around the enrollment core.

**Tech Stack:** Kotlin/JVM 21, Android API 29-37, PersistableBundle, SharedPreferences/device-protected storage, org.json.

**Spec:** `docs/superpowers/specs/2026-08-22-enrollment-engine-0.9.0-design.md`

## Global Constraints
- One DPC-AIO APK.
- No private Samsung/Knox contracts.
- Plaintext tokens/passwords never appear in diagnostic/export models.
- Stage transitions are idempotent.

---

### Task 1: Enrollment Models and Parser

**Files:**
- Create: `apps/dpc/modules/core/model/src/main/kotlin/io/dpcaio/core/model/EnrollmentModel.kt`
- Create: `apps/dpc/modules/core/model/src/test/kotlin/io/dpcaio/core/model/EnrollmentModelTest.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentConfigParser.kt`
- Test: `tools/tests/test_enrollment_config_contract.py`

**Interfaces:**
- Produces `EnrollmentSource`, `EnrollmentStage`, `EnrollmentErrorCode`, `NormalizedEnrollmentConfig`, `EnrollmentSession`, `EnrollmentRetryPolicy`.
- Produces `EnrollmentConfigParser.parse(PersistableBundle?, explicitSource: String?): ParsedEnrollmentConfig`.

- [ ] **Step 1: Write failing pure-Kotlin model tests**

```kotlin
check(EnrollmentRetryPolicy.delayMillis(1) == 5_000L)
check(EnrollmentRetryPolicy.delayMillis(5) == null)
check(EnrollmentSession.new(NormalizedEnrollmentConfig(source = EnrollmentSource.QR, requestedMode = "work-profile")).stage == EnrollmentStage.RECEIVED)
```

- [ ] **Step 2: Run the model test and verify RED**

```bash
kotlinc EnrollmentModelTest.kt EnrollmentModel.kt -include-runtime -d /tmp/enrollment-model-test.jar
```

Expected: compilation fails because `EnrollmentModel.kt` does not yet define the required types.

- [ ] **Step 3: Implement minimal model types and retry schedule**

```kotlin
enum class EnrollmentSource { QR, KME, ZERO_TOUCH, NFC, MANUAL_TOKEN, BYOD_WORK_PROFILE, GENERIC_ANDROID_ENTERPRISE }
enum class EnrollmentStage { RECEIVED, VALIDATING, NETWORK_CHECK, PROVISIONING_MODE, POLICY_COMPLIANCE, RESERVING, REGISTERING, BOOTSTRAP_VERIFY, APPLYING_PROFILE, POLICY_READBACK, COMMITTING, POST_PROVISION, LOCAL_PROVISIONED, SERVER_REGISTRATION_PENDING, COMPLETE, WAITING_FOR_RETRY, FAILED }
object EnrollmentRetryPolicy { fun delayMillis(attempt: Int): Long? = listOf(5_000L, 15_000L, 30_000L, 120_000L).getOrNull(attempt - 1) }
```

- [ ] **Step 4: Add parser contract tests for QR/KME/zero-touch/manual inputs**

```python
assert 'PROVISIONING_ADMIN_EXTRAS_BUNDLE' in source
assert 'enrollmentToken' in source
assert 'policyProfile' in source
assert 'kmeUri' in source
assert 'GENERIC_ANDROID_ENTERPRISE' in model_source
```

- [ ] **Step 5: Implement parser without manufacturer-based source detection**

```kotlin
val source = explicitSource?.let(EnrollmentSource::valueOf)
    ?: when {
        extras?.containsKey("kmeUri") == true -> EnrollmentSource.KME
        extras?.containsKey("zeroTouch") == true -> EnrollmentSource.ZERO_TOUCH
        extras?.containsKey("enrollmentToken") == true -> EnrollmentSource.QR
        else -> EnrollmentSource.GENERIC_ANDROID_ENTERPRISE
    }
```

- [ ] **Step 6: Re-run Kotlin and Python tests; expect PASS**

```bash
python3 tools/tests/test_enrollment_config_contract.py
```

### Task 2: Device-Protected Session Store

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentSessionStore.kt`
- Test: `tools/tests/test_enrollment_session_store_contract.py`

**Interfaces:**
- Produces `EnrollmentSessionStore.read()`, `write(session)`, `clear()`, `tokenFingerprint(token)`.

- [ ] **Step 1: Write RED contract requiring device-protected storage and redacted JSON fields**

```python
assert 'createDeviceProtectedStorageContext' in source
assert 'enrollment_token' not in exported_field_names
assert 'tokenFingerprint' in source
```

- [ ] **Step 2: Run and observe RED**

```bash
python3 tools/tests/test_enrollment_session_store_contract.py
```

- [ ] **Step 3: Implement atomic SharedPreferences session persistence**

```kotlin
private val prefs = context.createDeviceProtectedStorageContext()
    .getSharedPreferences("dpc_enrollment_session", Context.MODE_PRIVATE)
```

Persist only normalized non-secret fields plus token fingerprint and encrypted-token alias reference.

- [ ] **Step 4: Re-run contract and expect PASS**

```bash
python3 tools/tests/test_enrollment_session_store_contract.py
```
