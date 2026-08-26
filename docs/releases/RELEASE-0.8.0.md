# DPC-AIO 0.8.0 — Enterprise Operations Center

## Highlights

- Keeps the single monolithic DPC APK plus persistent **Show hidden** and **Developer / Lab** visibility.
- Adds **Compliance & Logs** with public Android Security Logging and Network Logging APIs, callback state, bounded local storage and manual redacted export.
- Adds **System Update Policy** for automatic, windowed, postpone and system-default modes plus validated recurring freeze periods.
- Adds **Certificate & Credential Center** with Storage Access Framework import for PEM/DER CA certificates and PKCS#12 key pairs, key-pair app grants and certificate delegation. Private-key export is intentionally absent.
- Adds **Device Lifecycle Center** for Lock Task packages/features, password/device security policy, application restrictions/control and FRP policy. It does not expose a wipe/factory-reset trigger.
- Adds **Work Profile / COPE** controls for cross-profile packages, Android 14+ managed-profile contacts/caller-ID `PackagePolicy`, maximum time off, personal-app suspension, organization identity and affiliation IDs.
- Adds **Knox Enterprise Center** as a public-capability surface. Deprecated Knox AuditLog is shown only as `DEPRECATED_PLATFORM_API`; private KLMS/KnoxGuard/HDM protocols are not reproduced.
- Extends DPC diagnostics with logging, system-update, certificate and COPE state.
- Preserves explicit Work Profile and Device Owner provisioning QR as release gates.

## Safety and platform boundary

All operations remain subject to Android ownership, delegation, API-level and OEM/KPE requirements. `Show hidden` exposes unavailable capabilities for diagnosis; it does not bypass platform enforcement. High-impact state changes use preview/confirmation paths and readback where the platform exposes it.

## Verification status

Repository/core/contract/pre-push/provisioning/plugin gates were executed in the release workspace. The aggregate host wrapper exceeded the chat sandbox execution limit after already passing its Kotlin suite and early contracts; its remaining constituent commands were run separately and passed. A real Android Gradle assemble could not start because the sandbox cannot resolve/download `https://services.gradle.org/distributions/gradle-9.7.0-bin.zip`; therefore no APK compilation claim is made from this workspace.
