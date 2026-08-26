# DPC-AIO Clean Build / Gradle Noise Fix

Validated: 2026-08-26

## What `NO-SOURCE` means

Gradle prints `NO-SOURCE` when a task has no files of the type it is designed to process. In this repository many modules are intentionally Kotlin-only, have no Java resources, or have no JNI libraries. Those states are not build failures and no empty placeholder files are added just to change the task label.

CI now filters only cosmetic terminal task-state lines (`NO-SOURCE`, `SKIPPED`, `UP-TO-DATE`, `FROM-CACHE`) while preserving warnings, errors, normal task execution, provisioning output and the final Gradle result. The complete unfiltered Gradle output is retained as `dist/gradle-build.log` in the GitHub Actions build artifact and is removed before publishing GitHub Release assets.

## Real warnings fixed

- Android Gradle Plugin source-set API: deprecated `srcDir(...)` calls replaced with `directories.add(...)`.
- `PermissionInfo.protectionLevel` and protection masks replaced with `protection` / `protectionFlags` (project minSdk is 29).
- Deprecated generic `Bundle.get(...)` replaced with string-typed `Bundle.getString(...)` for the string-only application restrictions coordinator.
- Removed unused `Unit` expression from the `clearApplicationUserData` callback.
- Password-backed generated signing keystore now uses PKCS12 format, removing the JKS proprietary-format warning while preserving the existing path/env contract.
- The intentional API-37 minor-platform alias used by native builds suppresses only AGP's `EXTERNAL_NATIVE_BUILD_CONFIGURATION` package-path warning.

## Checks

- Workflow YAML parse: PASS
- Gradle CI output filter unit test: PASS
- DPC Android migration contract: PASS
- Permission catalog Android contract: PASS
- Enterprise policy Android contract: PASS
- Device lifecycle Android contract: PASS
- Core Android contracts: PASS
- Project layout / module integration: PASS
- Provisioning integration: PASS
- QR production readiness: PASS
- Runtime smoke contract: PASS
- GitHub upload readiness: PASS
- Manual password-backed signing contract: PASS
- Release gates 112 / 113 / 114: PASS
- GitHub publish kit: PASS
- Dense QR version 20 / 655 chars: PASS
- Release secret scan: PASS
- Python AST parse: 156 files PASS
- Bash syntax: 9 files PASS
- Real PKCS12 signing smoke: PASS

The Android APK build itself is still the authoritative compiler check and remains in GitHub Actions. The CI filter does not alter the Gradle task graph or convert failures to success because the shell pipeline uses `pipefail`.
