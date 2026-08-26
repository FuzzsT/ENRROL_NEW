# DPC-AIO 0.9.0 Enrollment Engine Design

## Goal
Turn the existing Android Enterprise provisioning entry points into a resumable, stateful enrollment engine that supports QR, KME-compatible admin extras, zero-touch-compatible DPC extras, NFC/manual sources, server reservation/commit, signed bootstrap policies, redacted diagnostics, and explicit verification states.

## Constraints
- Keep one DPC-AIO APK.
- Use public Android Enterprise APIs and documented Samsung KME/KPE integration only.
- Never depend on private Knox Manage, KLMS Agent, KnoxGuard, HDM, or Samsung signature-only protocols discovered in `3.zip`.
- Keep existing `ACTION_GET_PROVISIONING_MODE` and `ACTION_ADMIN_POLICY_COMPLIANCE` contracts.
- Never log/export plaintext enrollment tokens, passwords, KPE keys, auth headers, full IMEI, or full serial.
- No arbitrary remote method invocation or downloaded scripts. Server bootstrap maps only to locally implemented policy operations.
- `RESULT_OK` from policy compliance is emitted only after the selected enrollment mode reaches its required completion state.
- Offline profiles may reach `LOCAL_PROVISIONED` but not `COMPLETE` until required server registration is committed.
- Source verification, APK build verification, Android provisioning verification, KME verification, and zero-touch verification remain separate release claims.

## Architecture
### Enrollment core
`EnrollmentConfigParser` normalizes provisioning extras into `NormalizedEnrollmentConfig`. `EnrollmentSession` persists state transitions through `EnrollmentSessionStore` in device-protected storage. `EnrollmentCoordinator` advances idempotent stages and classifies failures as terminal or retryable.

### Sources
Supported source labels are `QR`, `KME`, `ZERO_TOUCH`, `NFC`, `MANUAL_TOKEN`, `BYOD_WORK_PROFILE`, and `GENERIC_ANDROID_ENTERPRISE`. Source detection is metadata-driven; manufacturer name alone never selects KME.

### Stages
`RECEIVED`, `VALIDATING`, `NETWORK_CHECK`, `PROVISIONING_MODE`, `POLICY_COMPLIANCE`, `RESERVING`, `REGISTERING`, `BOOTSTRAP_VERIFY`, `APPLYING_PROFILE`, `POLICY_READBACK`, `COMMITTING`, `POST_PROVISION`, `LOCAL_PROVISIONED`, `SERVER_REGISTRATION_PENDING`, `COMPLETE`, `WAITING_FOR_RETRY`, `FAILED`.

### Server v2
The provisioning service exposes `reserve`, `validate`, `bootstrap`, `commit`, `release`, and `status`. Tokens are stored only as SHA-256 digests. States are `ISSUED`, `RESERVED`, `COMMITTED`, `EXPIRED`, `REVOKED`. Reservations have TTL and are idempotent by `requestId`.

### Trust model
Enrollment endpoints must be HTTPS. Bootstrap responses are versioned and signed over canonical payload metadata including session/reservation identifiers, nonce, issue time, and expiry. The DPC verifies a pinned public enrollment signing key; private signing material never ships in the APK.

### Policy bootstrap
The bootstrap schema describes a bounded policy profile and capability requirements. It does not contain arbitrary Android method names. The DPC validates schema/version, applies only known local operations, reads back critical state, and commits enrollment only after verification.

### Recovery
Session state is device-protected and written atomically. Resume triggers include policy-compliance re-entry, profile provisioning completion, locked boot/boot, package replacement, process restart, and manual Retry. Heavy work is not performed directly in broadcast receiver callbacks.

### UX and diagnostics
`EnrollmentStatusActivity` displays source, stage, mode, policy, retry count, network/server state, and redacted token fingerprint. `EnrollmentDiagnosticsSnapshot` exports redacted JSON. There is no "ignore and finish" path for required compliance failures.

## Release matrix
- `SOURCE_VERIFIED`: host Kotlin tests, Python contracts, Node server tests, QR/provisioning contracts, secret/non-SDK scans all pass.
- `APK_BUILD_VERIFIED`: Gradle Android assemble completes successfully.
- `ANDROID_PROVISIONING_VERIFIED`: real emulator/device integrated provisioning passes.
- `KME_VERIFIED`: actual Samsung KME enrollment passes.
- `ZERO_TOUCH_VERIFIED`: actual registered zero-touch configuration passes.
