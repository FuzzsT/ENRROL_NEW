# DPC-AIO v1 Architecture Specification

## Product goal
Build one Android enterprise DPC for Android 10 (API 29) through Android 17 (API 37), with a single policy engine shared by app management, permission management, activity/component launching, installation, provisioning, delegation, OEM/Knox adapters, and diagnostics.

## Build variants
- `enterpriseRelease`: production DPC. Public Android Enterprise APIs, typed delegation, optional Shizuku integration, native diagnostics. No runtime hook/stealth modules.
- `enterpriseDebug`: production feature set plus verbose diagnostics and developer screens.
- `systemPrivileged`: for owned/custom system images where the APK is legitimately provisioned as a system/priv-app and receives explicitly allowlisted privileges.
- `labDebug`: own-app/emulator compatibility experiments such as JVMTI/self-process hooks. Never a dependency of `enterpriseRelease`.

## Platform baseline
- minSdk 29.
- compileSdk 37.
- targetSdk 37.
- Java/Kotlin bytecode target 21 for host/core tooling; Android module toolchain may lower bytecode if AGP requires it.
- Native ABIs: arm64-v8a, armeabi-v7a, x86_64, x86.
- Native page sizes must be runtime-detected and 4 KiB/16 KiB safe.

## Core rules
1. `raw permission state`, `effective capability`, and `execution route` are distinct values.
2. A capability becomes GREEN only after a verifier confirms the requested operation or an equivalent supported result.
3. The engine may use official DPC authority, Android delegation, same-UID/signature routes, explicit intents, Binder/AIDL, providers, companion relays, Shizuku/shell, native companion routes, or legitimate system privileges.
4. Lab simulation/hooking is marked `LAB`/`SIMULATED`, never as a real grant.
5. Device Owner is singular. TestDPC policy behavior is adapted behind one AIO admin receiver/gateway.
6. Dhizuku compatibility is typed and scope-based; arbitrary remote binder/shell is disabled by default outside privileged/lab diagnostics.
7. Installer records initiating installer separately from installer-of-record and package source. `com.android.vending` attribution is never reported as genuine Play install unless source verification confirms it.
8. Package and component operations are user/profile aware.
9. Every modifying operation is audited.
10. Release build must have no dependency on Xposed/LSPosed, stealth `/proc` filtering, signature spoofing, hidden-API enforcement bypass, or foreign-process hook engines.

## Major subsystems
- core-model
- core-execution
- platform-compat
- policy-core
- permission-manager
- account-manager + account-android
- app-manager
- activity-launcher
- installer-core
- delegation-core + dhizuku-compat
- shizuku-adapter
- provisioning
- oem-aosp + samsung-knox
- native-diagnostics
- app-dpc
- lab-tools

## Sources used as references
- Google TestDPC 9.0.12: canonical DPM policy behavior/reference.
- Dhizuku API: MIT compatibility API reference.
- Dhizuku app: behavioral reference; avoid wholesale GPL code merge into core.
- InstallerX Revived/MIO: installer workflow/reference; implement AIO interfaces cleanly.
- enroll_android: provisioning server concepts.
- Xposed/native hook material: lab/reference only.


## Google account priority
- Google account inventory uses `AccountManager.getAccountsByType("com.google")` and therefore represents accounts visible to the DPC, not a guaranteed global Google-primary ordering.
- `PRIMARY_FOR_AIO` stores only the selected account name/type locally and does not claim to change Android-wide primary state.
- `SYSTEM_ORDER_REORDER` is a guided, explicit-confirmation workflow: temporarily remove accounts observed before the target, preserve the target, re-add removed accounts through the system authenticator, then verify the observed order.
- No Google passwords or auth tokens are stored, read, or replayed.
- If Google account management was disabled by policy, the workflow records and restores that policy after completion.
- Direct account removal is attempted only when the caller is a profile owner or actually holds the platform removal permission; otherwise the workflow opens system account settings and resumes verification when the user returns.
- `systemPrivileged` may request privileged account visibility/removal permissions, but capabilities remain runtime-verified.
