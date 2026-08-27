# DPC-AIO UI + Activity Manager 3.0 validation

Initial validation date: 2026-08-26. Fresh Autopilot revalidation: 2026-08-27.

## Scope

This upgrade starts from the validated PIN + explicit work-profile / fully-managed source tree and adds:

- a shared `DpcUiShell` for status bar, display cutout, navigation bar and IME insets;
- safe-insets migration for every activity that owns a `setContentView` root;
- categorized DPC dashboard sections and direct Favorites entry;
- Activity Manager 3.0 all-app browser;
- expandable application rows with lazy activity loading;
- filtering by app scope, enabled state, exported state, launcher visibility, required permission, favorites and favorite group;
- sorting by label, package or activity count;
- application and per-activity favorites;
- persistent favorite groups in device-protected storage;
- batch operations limited to expanded/loaded/filtered activities and still routed through `ComponentControlRouter`;
- additional public `DevicePolicyManager` controls in Device Lifecycle Center: status bar, keyguard, lock-now, hide/unhide, suspend/unsuspend, enable system app and delegated scopes.

No protected-component bypass was added. Favorites/groups are navigation metadata only.

## Source delta from previous validated ZIP

- 11 files added.
- 26 existing files changed.
- 0 source files removed.

The added files include `DpcUiShell.kt`, `ActivityFavoriteStore.kt`, `ActivityBrowserModel.kt`, its pure Kotlin test, Superpowers spec/plan and tests 116-120.

## Fresh verification evidence

- Host pure/core Kotlin suite: 63/63 test mains PASS, exit 0.
- `test_115_app_pin_and_provisioning_modes_contract.py`: PASS.
- `test_116_safe_insets_ui_contract.py`: PASS.
- `test_117_activity_manager_persistence_contract.py`: PASS.
- `test_118_activity_manager_3_contract.py`: PASS.
- `test_119_dashboard_menu_contract.py`: PASS.
- `test_120_device_lifecycle_expanded_controls.py`: PASS.
- Component Manager UI contract: PASS.
- Provisioning build integration: PASS.
- QR production-readiness 102: PASS.
- Android runtime smoke 112 contract: PASS.
- GitHub upload-ready 113 contract: PASS.
- Manual signing bootstrap 114: PASS.
- QR release bundle 114: PASS.
- release secret scan: PASS.
- non-SDK API scan: PASS.
- Python AST parse: 163 files PASS.
- Bash syntax: 9 `.sh` scripts + `gradlew` PASS (10 Bash files total).
- GitHub Actions YAML parse: 2 workflows PASS.
- provisioning server Node tests: 12/12 PASS, exit 0.
- all 120 Python contracts invoked by `tools/run_host_tests.sh`: PASS under per-test process-group timeouts; slower dual-QR/work-profile tests also PASS with extended timeout.

## Real APK dual-QR check

The APK from uploaded GitHub Actions artifact `DPC-AIO-enterpriseRelease-enrollment-32960742242-1(2).zip` was bound to newly generated QR payloads using the current generator:

- APK SHA-256: `76aab13fa043144581a047d26e3a5254afbc48b8af294540d72d2051a10035ef`.
- work-profile: `ok=true`, exact component, exact APK URL, QR payload match and APK checksum match.
- fully-managed: `ok=true`, exact component, exact APK URL, QR payload match and APK checksum match.

The workflow itself validates `work-profile-qr.png` with `--expected-mode work-profile` and `device-owner-qr.png` with `--expected-mode fully-managed`, regardless of the compatibility/default `provisioning-qr.png` mode.

## Build limitation in this sandbox

A new Android/Gradle APK containing the UI/Activity Manager 3.0 source changes cannot be compiled in this sandbox because the Gradle 9.7.0 distribution is not installed locally and outbound resolution/download of `services.gradle.org` is unavailable. Therefore the uploaded Actions APK used for the real QR test proves the provisioning pipeline and checksum binding, but it predates the new UI source changes.

The authoritative compile check for this final source tree must be a new GitHub Actions run after the source is pushed.

## Public rolling release

At fresh Autopilot revalidation on 2026-08-27, GitHub's release-by-tag API for `dpc-aio-continuous` still returned 404. The final workflow is responsible for creating/updating the rolling release during a successful publish job and then verifying the public asset byte-for-byte.
