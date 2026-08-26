# DPC-AIO 1.1.0 — Enterprise + Samsung OEM AIO

## Source release scope

DPC-AIO 1.1.0 preserves the 1.0.2 QR Production Readiness baseline and adds the shared Protected Targets/Operations guard, Enterprise Transaction Engine, deterministic Android/Knox/SEM/OEM capability routing, Package Trust 2.0, hardened APK+ import, Work Profile Lifecycle 2.0, Credential Recovery 2.0, application-restrictions readback, whole-app state control and Device Owner location policy.

## Verification model

Source/static verification is independent from runtime verification. A source PASS does not imply Device Owner, Work Profile, Knox, SEM, OEM Internals, Package Trust or protected-operation runtime verification. In this environment APK build remains BLOCKED because the exact Gradle/Android toolchain and dependency cache are unavailable; hardware-dependent states remain NOT_RUN/PARTIAL.

## Fresh-extract findings fixed before final release

- Historical 1.0.1 companion regression now preserves `0.1.8 or newer` instead of incorrectly rejecting the 1.1.0 companion `0.2.0`.
- The production enrollment workflow now propagates `DPC_AIO_ENROLLMENT_OFFLINE_MODE` and `DPC_AIO_OFFLINE_BUNDLE_ID`.
- The production artifact collector now reads `enterprise/release` APK bytes rather than `enterprise/debug` bytes renamed as release.
- Module Center now represents the 1.1.0 enterprise-protection, Knox Official and OEM Internals modules.
- Samsung Enterprise Center again exposes an explicit `Knox KPE / License` evidence row while retaining capability-first semantics.

## Security boundaries

The release does not add signature-permission bypass, hidden-API exemptions, guessed Binder transaction codes, root/su policy fallback, forged Knox/license/attestation state, plaintext credential reset-token export or dynamic loading of code extracted from analyzed APKs. OEM Internals remains catalog-driven and Lab-only.

## Companion plugin

DPC-AIO Companion 0.2.0 is skills-only. Plugin Autopilot contract/script validation and deterministic packaging pass locally. The `plugin-eval` executable is unavailable in this runtime, so no synthetic Plugin Eval score is reported.
