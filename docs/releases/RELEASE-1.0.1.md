# DPC-AIO 1.0.1 — Hardening & Verification

## Added
- Exact Gradle/JDK/Android SDK Build Resolver and `tools/verify-aio`.
- Offline Bundle Builder/Signer/Inspector and `tools/bundle-tool`.
- Ed25519 trust-store verification with keyId rotation and tamper rejection.
- Optional APK metadata inspection via local `apkanalyzer`/`apksigner`; no filename guessing.
- Verification-only `io.dpcaio.testtarget` application.
- `VerificationCommandReceiver` protected by `android.permission.DUMP` for shell/system-only readback tests.
- SAFE Device Harness for DPC permission/component Grant/Deny/Default and Enable/Disable/Default verification.
- Layered `RELEASE-VERIFICATION.json` statuses: PASS/FAIL/SKIP/BLOCKED/NOT_RUN.

## Source evidence
- 50/50 Kotlin test mains PASS.
- 12/12 provisioning-server tests PASS.
- 1.0.1 Build Resolver/Bundle Builder/Device Harness/release contracts PASS.
- Android contracts, project verification, non-SDK and secret scans PASS.
- Companion plugin 0.1.8 deterministic packaging PASS.

## Environment-blocked verification
This environment has Java 21 but no Android SDK, no exact cached Gradle 9.7.0 distribution, no complete Gradle dependency cache, and no ADB binary/device. Therefore APK build/install and live DO/PO/offline/permission/component verification remain BLOCKED, not PASS.
