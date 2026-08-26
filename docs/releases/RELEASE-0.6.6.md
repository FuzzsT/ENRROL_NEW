# DPC-AIO 0.6.6

## App-owned module integration

- Added `ModuleCenterActivity` to the DPC launcher.
- Added `DpcModuleRegistry` with compile-time representative class references for every app-owned Gradle module.
- All 34 non-app Gradle modules are now direct `:app-dpc` dependencies; `:knox-mock-core` is no longer only transitively reachable.
- Module Center distinguishes Core, Android, Integration, and Lab modules and marks modules with a user-facing screen as `UI`.
- The dashboard now reads the installed package version dynamically instead of embedding a stale version string.

## Verification hardening

- Added `tools/tests/test_module_center_contract.py`.
- The contract verifies that every Gradle module is represented in Module Center, directly owned by `:app-dpc`, and reachable in the project graph.
- The companion verifier now runs project-layout and module-center checks before project/android/release audits.

## Versions

- DPC-AIO: `0.6.6` (`versionCode 13`)
- DPC-AIO Companion: `0.1.3`
