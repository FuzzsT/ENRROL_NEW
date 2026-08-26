# DPC-AIO 0.9.0 — Enrollment Engine

## Release status

Source-level Enrollment Engine verification is performed independently from Android APK compilation and live enrollment verification.

### Added
- normalized QR/KME/zero-touch/NFC/manual enrollment configuration;
- resumable enrollment state machine and device-protected session store;
- Android Keystore-encrypted enrollment secret store;
- server v2 reservation/validation/bootstrap/commit/release/status endpoints;
- durable hashed token store with reservation/idempotency persistence;
- Ed25519 signed bootstrap verification and HTTPS-only enrollment client;
- real `ACTION_ADMIN_POLICY_COMPLIANCE` coordinator flow;
- boot/process/profile-complete recovery scheduling;
- Enrollment Status and Manual Enrollment UI;
- redacted enrollment diagnostics;
- QR/build configuration for enrollment endpoint and public signing key.

### Preserved
- DPC-AIO 0.8.0 Enterprise Operations Center;
- 34 app-owned modules / 35 reachable Gradle projects;
- explicit work-profile and device-owner QR artifacts;
- hidden/Developer-Lab capability model;
- public Android Enterprise API boundaries.

### Not claimed by source verification
A source ZIP does not prove that the Android APK compiled, that a real work-profile/device-owner provisioning completed, or that KME/zero-touch service enrollment completed. Those statuses require their own executable tests.
