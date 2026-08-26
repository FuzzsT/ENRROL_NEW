# Enrollment Trust and Bootstrap 0.9.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add HTTPS-only endpoint validation, signed bootstrap verification, bounded schema validation, policy apply/readback, and coordinator stage transitions.

**Architecture:** Pure trust/schema models are unit-testable; Android networking/policy adapters are thin. Remote bootstrap maps to a fixed local `BootstrapPolicy` model.

**Tech Stack:** Kotlin/JVM, Android URLConnection/HTTPS APIs, Java Security Ed25519, org.json.

**Spec:** `docs/superpowers/specs/2026-08-22-enrollment-engine-0.9.0-design.md`

## Global Constraints
- HTTPS only; no trust-all or permissive hostname verifier.
- Public key only in APK.
- No arbitrary server-provided method names.

---

### Task 1: Bootstrap Schema and Signature Verifier

**Files:**
- Create: `apps/dpc/modules/core/model/src/main/kotlin/io/dpcaio/core/model/EnrollmentBootstrap.kt`
- Create: `apps/dpc/modules/core/model/src/test/kotlin/io/dpcaio/core/model/EnrollmentBootstrapTest.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentTrustVerifier.kt`
- Test: `tools/tests/test_enrollment_trust_contract.py`

**Interfaces:**
- Produces `BootstrapPolicy`, `SignedBootstrapEnvelope`, `BootstrapValidationResult`.
- Produces `EnrollmentTrustVerifier.verify(envelope, expectedSessionId, expectedReservationId, nowMillis)`.

- [ ] **Step 1: RED pure schema test**

```kotlin
check(BootstrapPolicy(schemaVersion = 1, profileId = "default", allowedModes = setOf("work-profile")).validate("work-profile").ok)
```

- [ ] **Step 2: Implement bounded schema and reject unsupported schema/mode**

```kotlin
if (schemaVersion != 1) return Validation(false, "UNSUPPORTED_SCHEMA")
if (requestedMode !in allowedModes) return Validation(false, "MODE_NOT_ALLOWED")
```

- [ ] **Step 3: RED static trust contract**

```python
assert 'HttpsURLConnection' in source
assert 'HostnameVerifier' not in source or 'return true' not in source
assert 'Ed25519' in source
```

- [ ] **Step 4: Implement HTTPS endpoint validator and Ed25519 public-key verification**

```kotlin
require(uri.scheme.equals("https", ignoreCase = true))
val verifier = Signature.getInstance("Ed25519")
```

- [ ] **Step 5: Run tests and expect PASS**

```bash
python3 tools/tests/test_enrollment_trust_contract.py
```

### Task 2: Enrollment Coordinator and Policy Compliance

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/EnrollmentCoordinator.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/PolicyComplianceActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProvisioningModeActivity.kt`
- Test: `tools/tests/test_enrollment_coordinator_contract.py`

**Interfaces:**
- Produces `EnrollmentCoordinator.resumeOrCreate(Intent): EnrollmentOutcome`.

- [ ] **Step 1: RED contract requiring PolicyComplianceActivity not to immediately return OK**

```python
assert 'EnrollmentCoordinator' in policy_activity
assert 'setResult(RESULT_OK)' in policy_activity
assert policy_activity.index('EnrollmentCoordinator') < policy_activity.index('setResult(RESULT_OK)')
```

- [ ] **Step 2: Implement stage sequencing with retryable/terminal outcomes**

```kotlin
when (outcome) {
    is EnrollmentOutcome.Complete -> setResult(RESULT_OK)
    is EnrollmentOutcome.Retryable -> showRetry(outcome)
    is EnrollmentOutcome.Failed -> showFailure(outcome)
}
```

- [ ] **Step 3: Ensure ProvisioningModeActivity forwards normalized admin extras/session metadata**

Use result intent `EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE` only for documented bundle propagation.

- [ ] **Step 4: Run contract and existing provisioning tests**

```bash
python3 tools/tests/test_enrollment_coordinator_contract.py
python3 tools/tests/test_provisioning_android_contract.py
python3 tools/tests/test_dual_provisioning_qr.py
```
