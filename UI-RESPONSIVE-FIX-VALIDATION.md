# DPC-AIO 1.2.0 Responsive UI Fix Validation

## Scope

This source revision keeps the DPC-AIO 1.2.0 functional scope and fixes the programmatic UI layout layer.

Changes:
- programmatic spacing now uses density-independent dp instead of raw pixels;
- explicit application theme added to avoid OEM-dependent implicit Activity chrome;
- `DpcUiShell` remains the single system-bar / display-cutout / IME inset owner;
- `DpcUiShell.scroll()` keeps content scrollable and uses `clipToPadding=false`;
- Activity Manager 3.0 filter/action controls use horizontal scroll strips rather than fixed 50/50 pairs;
- Activity Manager app rows and dashboard buttons use readable non-all-caps labels and dp padding;
- all existing PIN, QR provisioning, component-protection and DPM behavior is retained.

## Fresh verification

- TDD regression `test_121_responsive_ui_contract.py`: RED before implementation, PASS after implementation.
- Kotlin core host test mains: 63/63 PASS, exit 0.
- UI contracts 115-121: PASS.
- `test_non_sdk_api_scan.py`: PASS.
- `test_release_secret_scan.py`: PASS.
- Python AST parse: 164 files PASS.
- Android XML parse for the app manifest/resources: PASS.
- Raw numeric `setPadding(...)` scan in app Activity sources: PASS (none remain outside `DpcUiShell`, where inset values are intentionally px).
- `bash -n tools/run_host_tests.sh`: PASS.

## Build limitation

A full Android Gradle compile was attempted with:

`./gradlew :app-dpc:compileEnterpriseDebugKotlin --no-daemon --console=plain`

The Gradle wrapper could not download Gradle 9.7.0 because the sandbox cannot resolve `services.gradle.org` (`UnknownHostException`). This is an environment/network blocker. This report does not claim a compiler-verified APK for this exact UI-fix source tree.
