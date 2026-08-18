# DPC-AIO Development Checkpoint 0.2.0

## Implemented in this checkpoint
- Pure Kotlin `PolicyResult` and `DevicePolicyGateway` contracts.
- Android `DevicePolicyManager` adapter for hide/unhide, suspend/unsuspend, and managed runtime permission state.
- Permission inspection/action planning with raw permission vs AppOp vs effective-route separation.
- Permission policy coordinator with post-mutation readback verification.
- App inventory model/filters and Android installed-app inventory adapter.
- App policy coordinator with verified hide/suspend readback.
- Activity/component access planner that preserves `exported=false` boundaries.
- Activity launch coordinator with runtime fallback and Android framework/LauncherApps/deep-link executor.
- Installer planner preserving genuine Play vs installer-of-record distinction.
- Android PackageInstaller session creation, APK staging, fsync, commit, and abandon operations.
- Pure/Android module split: policy, permission, app, activity, and installer logic are separated from framework adapters.
- Android contract verifier and release safety gate.

## Platform envelope
- minSdk 29 / Android 10.
- compileSdk 37 / targetSdk 37.
- ABI policy remains arm64-v8a, armeabi-v7a, x86_64, x86.
- 4 KiB and 16 KiB page-size compatibility gate remains enabled.

## Verification model
Host tests compile pure Kotlin modules with local `kotlinc`, then run structural Android API contract checks and the release gate.

## Android build limitation in this sandbox
A full Gradle Android build was attempted with:

`./gradlew :app-dpc:assembleEnterpriseDebug --offline`

The wrapper is not cached locally and attempts to fetch Gradle 9.7.0 from `services.gradle.org`. This sandbox cannot resolve that host, so the Android APK build cannot be claimed as verified here. No APK is included in this checkpoint.
