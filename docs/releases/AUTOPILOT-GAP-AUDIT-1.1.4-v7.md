# DPC-AIO 1.1.4 — Plugin Autopilot / Source Completeness Audit v7

Date: 2026-08-25
Target: `local-localhost-app-system/dpc_android`
Baseline: DPC-AIO 1.1.4 v6 module-classpath fix

## Executive result

The repository is not missing Gradle project source. All 43 declared subprojects have a project directory, `build.gradle.kts`, and real `src/main` content. The recurring Gradle `NO-SOURCE` messages are expected when a task has no inputs of that specific type, for example `compileJava` in Kotlin-only modules, `processResources` in JVM modules without `src/main/resources`, or Android native-lib merge tasks in modules without JNI.

v7 adds a fail-closed source-completeness audit so a genuinely empty/missing module is now a CI blocker while legitimate `NO-SOURCE` remains informational.

## Fixed in v7

- Added `tools/gradle_source_audit.py` with JSON evidence.
- Added `tools/tests/test_gradle_source_completeness.py`.
- Removed duplicate `:offline-core` and `:offline-android` entries from `settings.gradle.kts`.
- Added source-completeness audit to `tools/run_host_tests.sh`.
- GitHub Actions now runs `Audit Gradle module source completeness` before Android toolchain setup and writes `gradle-source-audit.json`.
- Release artifact collection includes `gradle-source-audit.json`.
- Updated the Work Profile Lifecycle contract test to require public `UserManager.userName` readback and forbid the removed/non-public `getProfileName` expectation.

## Current implementation coverage

### Implemented / source contract present

- Device Owner + Work Profile provisioning and dual QR tooling.
- Explicit ONLINE / ONLINE_PREFERRED / FULL_OFFLINE / OFFLINE_THEN_SYNC routing.
- Full Offline coordinator, bundle verification, local package install path, recovery state.
- Permission Manager core + Android gateway + Shizuku mutation route.
- Activity Explorer / Component Control including enable/disable state routing and protected-target checks.
- Security logging and network logging Android Enterprise gateways.
- System Update Policy with freeze-window model/readback contract.
- Certificate / Credential Center (CA certs, managed key pairs and grants where public API permits).
- Lock Task / device-security policy models and Android gateway.
- COPE / cross-profile packages / maximum-time-off / personal-app suspension.
- FRP policy, organization identity, affiliation IDs, application restrictions/readback, clear-app-data and user-control-disabled package policy.
- Protected Targets / Protected Operations guard and transaction/rollback model.
- Package Trust 2.0 and hardened APK+ planning/staging contracts.
- Credential Recovery lifecycle and encrypted vault abstraction.
- Samsung capability center, SEM discovery, OEM Internals Lab isolation/circuit-breaker structure.

### Partial — present but not yet operationally complete

1. **Knox Official execution layer**
   - `KnoxSdkInventory` currently proves class presence only.
   - `KnoxCapabilityReducer` validates prerequisites but explicitly reports `CALL_NOT_YET_VERIFIED`.
   - UI lists ApplicationPolicy, CertificatePolicy, Kiosk, Firewall/VPN and Enhanced Attestation, but there is no corresponding bounded official-Knox execution gateway with call/readback evidence yet.
   - Until such adapters exist, these capabilities must not be presented as operational solely from license/class presence.

2. **Samsung SEM execution**
   - `SemRuntimeProbe` currently proves class/method presence and leaves call/readback as a later bounded invocation step.
   - This is correct probe-first behavior, but not full operational SEM support.

3. **OEM Internals Lab**
   - Current probe is bounded/read-only and isolated; cataloged write operations remain intentionally limited/unimplemented unless naturally authorized and recoverable.

4. **Runtime evidence**
   - Source/static checks do not establish Device Owner, Work Profile, Knox, SEM or OEM hardware runtime verification.

### Missing / deferred features from earlier gap plans

- Managed subscriptions policy surface.
- Preferential network service configuration.
- Nearby-streaming enterprise policy surface where supported.
- Knox geofencing adapter.
- DeX execution adapter.
- Real official Knox execution/readback adapters for the currently advertised public capability families.
- Measured Plugin Eval benchmark/token-usage run in a host that provides the Plugin Eval CLI.

## Release / signing status

The emergency workflow can generate a PKCS12 signing key when stable repository secrets are absent. This is useful for producing a signed emergency APK, but the resulting certificate is run-specific and therefore cannot provide an update-stable production enrollment chain.

True production QR readiness still requires a stable secret-managed release key, expected signing-certificate SHA-256, exact APK-to-QR checksum binding, public unauthenticated download, and byte-for-byte public artifact verification.

## ChatGPT/Codex companion plugin audit

Current repository-native plugin checks pass for `dpc-aio-companion` 0.2.1 with deterministic packaging and four Skills. Publication remains blocked by publisher/public-URL/credential facts outside the package.

Current Autopilot contract also expects a public brand pack with light/dark logo variants. The existing plugin currently contains `assets/logo.svg` and `assets/icon.svg`; `assets/logo-light.svg` and `assets/logo-dark.svg` remain a packaging/listing improvement to do before public publication work.

## Priority order after v7

1. Run GitHub Actions with v7 and get `assembleEnterpriseRelease` to completion.
2. Treat any next compiler error as the next build blocker; do not broaden changes speculatively.
3. Once APK build/release is green, configure stable signing for production/updateability.
4. Add official Knox bounded execution adapters with real CALL_SUCCEEDED / READBACK_VERIFIED evidence.
5. Add remaining P2 enterprise policies (managed subscriptions, preferential network, geofencing/DeX where supported).
6. Complete plugin public brand/listing/publisher gates separately from Android APK readiness.
