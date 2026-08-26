# DPC-AIO 0.9.0 — Enrollment Engine

DPC-AIO 0.9.0 turns the Android Enterprise provisioning entry points into a resumable enrollment engine while keeping the monolithic APK and existing work-profile/device-owner QR paths.

## Enrollment core

- `EnrollmentConfigParser` normalizes QR, documented KME admin extras, zero-touch-tagged DPC extras, NFC, manual token, BYOD work-profile, and generic Android Enterprise sources.
- `EnrollmentSession` tracks explicit stages from `RECEIVED` through `COMPLETE`, including retry and pending-server states.
- `EnrollmentSessionStore` uses device-protected storage and persists only non-secret state plus a SHA-256 token fingerprint.
- `EnrollmentSecretStore` encrypts the runtime enrollment token/KME password with Android Keystore AES-GCM when process/reboot recovery requires persistence.

## Integrated provisioning

`ProvisioningModeActivity` still selects only a mode allowed by Android and now creates/recovers an enrollment session. It returns the standard `EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE` with DPC-AIO session metadata so `ACTION_ADMIN_POLICY_COMPLIANCE` can continue the same session.

`PolicyComplianceActivity` no longer returns success immediately. It runs `EnrollmentCoordinator` on a worker thread and reports success only for `Complete` outcomes. Retryable and terminal failures stay visible with diagnostics; there is no ignore-and-finish action.

## Enrollment server v2

The provisioning service adds:

- `POST /v2/enrollments/reserve`
- `POST /v2/enrollments/validate`
- `POST /v2/enrollments/bootstrap`
- `POST /v2/enrollments/commit`
- `POST /v2/enrollments/release`
- `GET /v2/enrollments/{sessionId}/status`

Token state is `ISSUED → RESERVED → COMMITTED`, with reservation expiry returning a still-valid token to `ISSUED`. Tokens are stored by SHA-256 digest. When `DPC_AIO_TOKEN_STORE_PATH` is configured, state and idempotency records are written atomically to disk.

## Trust

Bootstrap responses are signed with Ed25519. Production server startup requires `DPC_AIO_ENROLLMENT_SIGNING_PRIVATE_KEY_FILE` unless an operator explicitly opts into ephemeral development signing with `DPC_AIO_ALLOW_EPHEMERAL_SIGNING_KEY=1`.

The DPC build receives the matching base64 DER public key through `DPC_AIO_ENROLLMENT_SIGNING_PUBLIC_KEY`. `EnrollmentTrustVerifier` validates session ID, reservation ID, nonce, time window, and signature before policy bootstrap is accepted.

`DPC_AIO_ENROLLMENT_ENDPOINT` and QR `--enrollment-endpoint` must use HTTPS. The client does not install a permissive hostname verifier or trust-all TLS policy.

## Recovery and UI

- `EnrollmentRecoveryReceiver` reacts to boot/locked-boot/package replacement by scheduling work, not by doing networking in the receiver.
- `EnrollmentRecoveryJobService` uses `JobScheduler` and the bounded retry schedule.
- `EnrollmentStatusActivity` shows source, stage, mode, profile, redacted fingerprint, retry count, and error.
- `EnrollmentManualActivity` allows a user/admin to create a manual-token enrollment session and immediately clears the visible token field after secure storage.
- `enrollment-diagnostics.json` contains no enrollment token, password, KPE key, or auth header.

## Verification levels

- `SOURCE_VERIFIED`: host/core/contracts/server/plugin/source scans pass.
- `APK_BUILD_VERIFIED`: requires an actual Gradle Android assemble exit 0.
- `ANDROID_PROVISIONING_VERIFIED`: requires real emulator/device integrated provisioning.
- `KME_VERIFIED`: requires an actual Samsung KME deployment.
- `ZERO_TOUCH_VERIFIED`: requires an actual registered zero-touch deployment.
