# Self Update + Install Source + GUI design

Date: 2026-10-29
Repository: `FuzzsT/ENRROL_NEW`
Base: `main` at `4e13c374944b2adb055c4be94aa843025c3ab8b9`

## Goal

Add a first-class Self Update module to DPC-AIO, improve package install-source visibility in the existing APK/APKS installer/updater, and extend the shared `ModernUi` components used by those surfaces.

The update path must preserve Android's PackageInstaller trust boundary. Silent installation is supported only when Android grants it through official device-management/package-installer policy. No root, Shizuku shell install, hidden-API bypass, accessibility automation, or confirmation-dialog bypass is introduced.

## Existing architecture to reuse

- `AppPackageManagerActivity` already owns APK/APKS install/update UX, queueing, preflight and PackageInstaller submission.
- `AndroidPackageInstallerAdapter` remains the install authority adapter.
- `InstallQueueCoordinator` / `InstallQueueStore` remain the durable install sequencing path for ordinary package installs.
- `AppInstallStatusReceiver` already receives PackageInstaller outcomes.
- `ActivityLauncherHubActivity` already reads the current installer package using `getInstallSourceInfo(...).installingPackageName` on API 30+ and `getInstallerPackageName(...)` on older Android.
- `ModernUi` is the shared classic-View design system and should be extended instead of creating a second UI toolkit.
- The repository already publishes predictable GitHub Release APK assets, including the mutable `dpc-aio-continuous` release channel.

## 1. Self Update module

### 1.1 User surface

Add internal `SelfUpdateActivity` (`android:exported="false"`) reachable from:

- `AioDashboardActivity > Tools`
- `ControlCenterActivity > Functions`
- `DpcModuleRegistry` / Module Center

The screen shows:

- installed version name and version code
- selected channel: `Stable` or `Continuous`
- latest available version name and version code
- update state chip
- silent-install capability state
- APK size
- download progress
- APK SHA-256 verification state
- signer verification state
- release notes summary/link
- last successful update check
- last install result

Primary actions:

- `Check now`
- `Download & verify`
- `Install update`
- `Install silently` when supported
- `Clear downloaded update`
- `Release notes`

Optional setting:

- `Automatically install a downloaded verified update when silent install is supported`

This setting defaults to OFF. It does not create periodic background update checks in this scope; it only controls what happens after an update has been explicitly checked/downloaded by the user.

### 1.2 Update states

Use a small explicit state model:

- `IDLE`
- `CHECKING`
- `UP_TO_DATE`
- `UPDATE_AVAILABLE`
- `DOWNLOADING`
- `VERIFYING`
- `VERIFIED`
- `INSTALLING`
- `WAITING_USER`
- `UPDATED`
- `ERROR`

Persist only state required to recover a downloaded verified update after Activity recreation/process restart. Transient progress may be reconstructed.

### 1.3 Update manifest

Publish a small JSON manifest per channel. Schema v1:

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "packageName": "io.dpcaio.app",
  "versionName": "1.2.0",
  "versionCode": 26,
  "apkUrl": "https://github.com/FuzzsT/ENRROL_NEW/releases/download/.../DPC-AIO-enterprise-release.apk",
  "apkSha256": "...",
  "apkSize": 12345678,
  "publishedAt": "2026-10-29T00:00:00Z",
  "releaseNotesUrl": "https://github.com/FuzzsT/ENRROL_NEW/releases/tag/..."
}
```

The app uses fixed default manifest endpoints through build configuration so production code does not depend on GitHub API discovery:

- stable: `https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-stable/self-update.json`
- continuous: `https://github.com/FuzzsT/ENRROL_NEW/releases/download/dpc-aio-continuous/self-update.json`

The release workflow must maintain the `dpc-aio-stable` mutable alias for the latest approved stable APK and keep the existing `dpc-aio-continuous` alias for continuous builds.

CI/release automation generates the manifest from the APK actually being published, computes SHA-256 and size, and uploads manifest + APK together.

### 1.4 Download and verification boundary

Before installation, all of the following must pass:

1. manifest fetched over HTTPS
2. `schemaVersion` supported
3. channel matches selected channel
4. `packageName == io.dpcaio.app`
5. `versionCode > installedVersionCode`
6. APK download completes
7. downloaded byte count matches `apkSize` when supplied
8. SHA-256 matches `apkSha256`
9. APK parses successfully through PackageManager archive inspection
10. parsed APK package name matches the manifest and current app package
11. parsed APK version code matches the manifest
12. candidate signing identity is compatible with the currently installed application's signing identity/signing lineage

The signing identity/lineage check is the primary app-authenticity trust anchor. Manifest SHA-256 protects the downloaded candidate against corruption/substitution relative to that manifest but does not replace signer verification.

If any verification fails, installation controls remain disabled and the cached candidate is rejected/deleted.

Android PackageInstaller still performs its own platform signature/update validation; the preflight is an additional fail-closed UX guard.

### 1.5 Storage

Store only one pending Self Update candidate.

- downloaded APK: `filesDir/self-update/pending.apk`
- persisted metadata/state: SharedPreferences file `self_update`
- delete candidate after successful package replacement, explicit clear, failed cryptographic/package verification, or when superseded by a newer download

Never store signing secrets in the app.

## 2. Silent installation

### 2.1 Modes

Expose three user-facing install policies:

- `ASK_ALWAYS`
- `SILENT_IF_ALLOWED` (default for manual update install)
- `AUTO_SILENT` (only when user explicitly enables auto-install-after-download)

### 2.2 Capability and runtime behavior

Silent install is attempted only through official Android APIs.

Supported cases include Android device-management configurations where PackageInstaller permits installation without user interaction, such as a Device Owner on a fully managed device and supported affiliated managed contexts.

Implementation rules:

- use DevicePolicyManager/management context to report a best-effort capability state
- on API 31+, request `PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED` only when the selected policy allows a silent attempt
- on older supported managed-device configurations, commit normally and let PackageInstaller apply admin semantics
- always treat the actual PackageInstaller callback as authoritative
- if Android returns `STATUS_PENDING_USER_ACTION`, `SILENT_IF_ALLOWED` falls back to the provided system confirmation intent
- `AUTO_SILENT` must not bypass that confirmation; it transitions to `WAITING_USER` and requires the user to continue manually
- other failures transition to `ERROR`

No Shizuku/root/ADB install fallback is added.

### 2.3 Self-update install session

Self Update reuses `AndroidPackageInstallerAdapter` but has a focused `SelfUpdateCoordinator` so update-specific verification/state is not mixed into the general user-selected APK queue.

The session must set:

- expected package name `io.dpcaio.app`
- installer package name `io.dpcaio.app`
- package source consistent with downloaded-file/self-update semantics
- requested user-action mode derived from the selected Self Update policy

The install result receiver must distinguish:

- success
- pending user action
- aborted/cancelled
- incompatible signer/version/package
- storage/session failures

## 3. Install source metadata in APK/APKS Installer / Updater

### 3.1 Shared model

Add a small Android-facing source model, for example:

```kotlin
data class InstallSourceDetails(
    val installingPackageName: String?,
    val installingPackageLabel: String?,
    val initiatingPackageName: String?,
    val originatingPackageName: String?,
    val packageSource: Int?,
    val updateOwnerPackageName: String?,
)
```

Read fields with explicit API guards:

- API 30+: `PackageManager.getInstallSourceInfo(packageName)` for installing, initiating and originating package names
- pre-30: deprecated `getInstallerPackageName(packageName)` fallback for the installing package only
- API 33+: `InstallSourceInfo.packageSource`
- API 34+: `InstallSourceInfo.updateOwnerPackageName`

`originatingPackageName` may legitimately be null when the caller does not hold `INSTALL_PACKAGES`; null is preserved as unavailable rather than inferred.

Do not infer a package source when Android returns null/unavailable.

### 3.2 Display

Enhance the selected installed-app card in `AppPackageManagerActivity` updater mode.

Example:

```text
Chrome
com.android.chrome
151.x

Installed by
Google Play Store
com.android.vending

Initiated by
com.android.vending

Source
STORE

Update owner
com.android.vending
```

Rules:

- resolve a human-readable label with PackageManager when the package is installed/visible
- always retain the raw package name underneath the label
- null fields display `Unknown / unavailable`, not guessed values
- known package-name hardcoding is not required; use the installed application's label when available

Also include the installer/source package in package-install history where that information can be read after a successful install/update.

### 3.3 Reuse in Activity Launcher

Replace the local `installerPackage(...)` helper in `ActivityLauncherHubActivity` with the shared install-source reader. Activity Launcher and App Package Manager must use the same reader and therefore the same API-gating/null behavior.

The launcher continues displaying only its compact installer field while the updater uses the full details card.

## 4. ModernUi improvements

Extend `ModernUi` only with reusable components needed by the new and existing installer surfaces:

- `statusCard(title, detail, tone)`
- `progressCard(title, progress, detail)`
- `metricRow(...)` or an equivalent compact version/value row
- consistent primary / secondary / destructive action spacing
- a compact key/value details component suitable for package metadata

Apply these improvements to:

- `SelfUpdateActivity`
- the installed-app/source card in `AppPackageManagerActivity`
- installer status/verification presentation where the change is small and directly reusable

Do not redesign every DPC-AIO screen in this change.

## 5. Navigation and manifest

Add:

- `SelfUpdateActivity` to app manifest with `exported=false`
- Dashboard Tools entry
- Control Center Functions entry
- Module Center registry entry

Add explicit app-level `android.permission.INTERNET` if the app currently receives it only indirectly through a dependent module, so Self Update networking does not depend on manifest-merger side effects.

## 6. Error handling

User-visible errors must distinguish at least:

- network unavailable / timeout
- HTTP failure
- malformed/unsupported manifest
- no newer version
- APK download failure
- size mismatch
- SHA-256 mismatch
- APK parse failure
- package-name mismatch
- version mismatch/downgrade
- signer mismatch
- PackageInstaller session create/stage/commit failure
- Android requires user confirmation
- installation cancelled

Messages should expose actionable status without dumping stack traces into the main UI.

## 7. Testing / TDD

Implementation follows RED -> GREEN.

### RED contracts first

Add focused host tests that initially fail because implementation is absent:

1. Self Update policy/model contract
   - manifest parser fields
   - version comparison
   - package/version/hash/signer fail-closed rules
   - install policy enum and pending-user-action fallback semantics

2. Self Update Android/UI source contract
   - `SelfUpdateActivity`
   - manifest entry is internal/exported false
   - Dashboard, Control Center and Module Center entries
   - PackageInstaller adapter use
   - no Shizuku/root install path

3. Install-source contract
   - API 30+ `getInstallSourceInfo`
   - pre-30 `getInstallerPackageName` fallback
   - API 33 package-source handling
   - API 34 update-owner handling
   - AppPackageManager updater renders label + raw package
   - Activity Launcher uses the shared reader

4. ModernUi contract
   - reusable status/progress/details components used by Self Update

### GREEN

Implement the smallest production changes required to satisfy those contracts, then run full `tools/run_host_tests.sh` and Android CI.

### Final verification

Require exact-head evidence for:

- Source contracts PASS
- full host/source suite PASS
- Enterprise debug APK PASS
- APK existence verification PASS
- aggregate `verify` PASS

## 8. Non-goals

Not included in this scope:

- root installation
- Shizuku shell installation
- accessibility-driven confirmation clicking
- bypassing PackageInstaller policy
- downloading APK payloads out of Google Play
- unattended periodic background update polling
- multi-version rollback manager
- delta/patch APK updates
- redesigning unrelated application screens

## 9. Completion criteria

The change is complete when:

1. DPC-AIO can check a trusted update channel, download a newer APK and verify package/version/hash/signing identity before install.
2. A manual Self Update can install silently when official Android policy permits it, and safely falls back to system confirmation otherwise.
3. Optional auto-silent-after-download remains opt-in and never bypasses `STATUS_PENDING_USER_ACTION`.
4. The updater clearly shows which package installed the selected app and exposes richer `InstallSourceInfo` fields when Android provides them.
5. Activity Launcher and App Package Manager use the same shared source reader instead of divergent installer-package logic.
6. Self Update and installer source details use the improved shared ModernUi components.
7. Exact-head CI is fully green before the PR is considered implementation-complete.
