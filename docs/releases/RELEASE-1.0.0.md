# DPC-AIO 1.0.0 — Offline & Application Control

## Added
- Full Offline execution mode with no backend/DNS/HTTPS dependency.
- Ed25519-signed offline bundle verification and SHA-256 validation for base/split APK payloads.
- Offline Package Vault / PackageInstaller staging and multi-package support on compatible Android versions.
- Offline policy application, device-protected recovery state and bounded redacted sync receipt.
- Permission Manager 2.0 with per-app actual grant, DPC grant, AppOps, route, capability and target-user state.
- Permission batch preview/readback/restore and global future-request policy.
- Activity / Component Manager 2.0 with Enable, Disable, Restore Default, Enable & Launch, multi-user routing and snapshots.
- Protected DPC critical-component guard and explicit non-atomic batch status.

## Preserved
- Enrollment Engine 0.9.0 reserve/validate/signed-bootstrap/commit contract.
- Enterprise Operations Center, Work Profile/COPE and public Knox capability model.
- Explicit work-profile and device-owner provisioning QR flows.

## Verification model
Source verification, APK build verification and live-device verification are distinct. `APK_BUILD_VERIFIED` is not claimed unless a real Gradle assemble command exits 0. Real FULL_OFFLINE, Permission Manager and Component Manager behavior require emulator/device evidence in the relevant ownership context.

## Source verification result
- Kotlin host test mains: 50/50 PASS.
- Provisioning/enrollment server: 12/12 PASS.
- Project, Android contract, module-center, secret and non-SDK gates: PASS.
- Companion plugin 0.1.7 deterministic packaging: PASS.
- Real APK assemble remains unverified because the build environment cannot resolve `services.gradle.org` to download Gradle 9.7.0; the failure occurs before project source compilation.
