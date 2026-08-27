# DPC-AIO final validation

Final audit scope: DPC UI/Activity Manager 3.0, app PIN, Android Enterprise dual provisioning, zero-settings signing workflow, APK path gates and release publication.

## UI / navigation

- `DpcUiShell` applies status/navigation/display-cutout/IME insets and preserves base padding.
- Activities owning a content root are covered by the safe-insets contract.
- Dashboard is grouped into Enrollment, Apps & Components, Device & Policy, Security & Credentials, Network, Work Profile / COPE, OEM / Knox, Diagnostics and developer-only Advanced / Lab.
- Apps & Components exposes Activity Manager 3.0, Favorites and Permission Manager.

## Activity Manager 3.0

- Displays all applications visible to the current PackageManager/user context as expandable rows.
- Activity lists are loaded lazily per expanded package and cached thread-safely.
- Filters cover query, User/System, Enabled/Disabled, Exported/Not exported, Launcher/Hidden, permission presence, favorites and favorite group.
- Sort modes: label, package, activity count.
- Favorites exist at app and activity level.
- Favorite groups support create, rename, delete and multi-membership.
- Favorites/groups use device-protected `SharedPreferences` and do not grant mutation/launch privileges.
- Enable/Disable/Restore/Launch and batch flows continue through the existing component-control/protection routes; critical/protected targets remain blocked.

## Additional public DevicePolicyManager controls

Device Lifecycle Center now also exposes public DPM operations for status bar, keyguard, lock-now, hide/unhide application, suspend/unsuspend packages, enable system app and delegated scopes. Calls remain wrapped in `PolicyResult` handling.

## Workflow UX and signing

- Production workflow: exactly one manual input, `release_signing_password`.
- Emergency workflow: zero manual inputs.
- Configuration precedence: GitHub Variables/Secrets -> `.github/dpc-aio-defaults.env` -> safe built-ins.
- Production sequence remains: validate keystore -> Gradle creates/signs APK -> validate exact APK path -> `apksigner verify` -> copy same APK to `dist` -> bind QR checksum -> publish.
- Password-backed rolling signer uses encrypted PKCS12 persistence.

## Android Enterprise QR

Every production build generates and validates both explicit modes independently:

- `work-profile-qr.png` -> `--expected-mode work-profile`;
- `device-owner-qr.png` -> `--expected-mode fully-managed`.

The compatibility `provisioning-qr.png` may use the configured default mode but does not replace either explicit QR.

A real APK from the uploaded GitHub Actions artifact was re-bound with the current QR generator and passed both work-profile and fully-managed verification with exact component, URL, QR payload and APK SHA-256 match. See `UI-ACTIVITY-MANAGER-3-VALIDATION.md` for the evidence and APK hash.

## Fresh validation results

PASS with exit 0 where applicable:

- core/host Kotlin test mains: 63/63;
- tests 115, 116, 117, 118, 119 and 120;
- Component Manager UI contract;
- provisioning build integration;
- QR readiness 102;
- Android runtime smoke 112 contract;
- GitHub upload-ready 113 contract;
- manual signing bootstrap 114;
- QR release bundle 114;
- release secret scan;
- non-SDK API scan;
- Python AST parse: 163 files;
- Bash syntax: 9 `.sh` scripts + `gradlew` (10 Bash files total);
- workflow YAML parse: 2 workflows;
- provisioning Node tests: 12/12;
- repository Python contracts invoked by `tools/run_host_tests.sh`: 120/120 PASS;
- real APK QR verification: work-profile + fully-managed.

The Python contracts were rerun individually from a fresh extraction with process-group timeout control. The two slower QR tests were rerun with an extended timeout and both completed with exit 0.

## Compile / release boundary

The latest UI/Activity Manager 3.0 Android source changes have not been compiled into a new APK inside this sandbox: Gradle 9.7.0 is not locally cached and `services.gradle.org` is unavailable from this environment. A new GitHub Actions run is therefore required for the authoritative Android compile of this exact final tree.

At the fresh Autopilot revalidation on 2026-08-27, the public `dpc-aio-continuous` release-by-tag endpoint still returned 404. A successful final production workflow run must reach `publish` to create/update that rolling release and verify the public APK asset.
