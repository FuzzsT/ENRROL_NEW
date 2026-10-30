# Self Update + Install Source Implementation Plan

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a verified DPC-AIO Self Update module with official silent-install attempts where Android policy allows them, plus shared install-source metadata and focused ModernUi improvements.

**Architecture:** Add `:self-update-core` as pure Kotlin for manifest parsing, update eligibility and install-policy decisions, and `:self-update-android` as the Android adapter for HTTPS download, APK/package/signing verification and PackageInstaller submission. Reuse `AndroidPackageInstallerAdapter` and the existing installer trust boundary. Put install-source inspection in `:installer-android` so Activity Launcher and App Package Manager consume the same API.

**Tech Stack:** Kotlin/JVM 21, Android classic Views, PackageManager/InstallSourceInfo, PackageInstaller, DevicePolicyManager, `HttpURLConnection`, Python host contract tests, GitHub Actions Android CI.

## Global Constraints

- No root, Shizuku shell install, ADB install, accessibility confirmation clicking, hidden-API bypass or PackageInstaller-policy bypass.
- `SILENT_IF_ALLOWED` is the default manual Self Update policy; Android callback remains authoritative.
- `AUTO_SILENT` is opt-in and never bypasses `STATUS_PENDING_USER_ACTION`.
- Every candidate must pass HTTPS-manifest, package name, higher versionCode, size if supplied, SHA-256, parsed APK version/package and signing-identity checks before install controls are enabled.
- Self Update uses app-private storage and keeps at most one pending verified candidate.
- Install-source fields are shown only when Android provides them; null values render as unavailable rather than being guessed.
- Self Update is internal (`exported=false`) and reachable from Dashboard, Control Center and Module Center.
- UI changes are limited to reusable components needed by Self Update and installer/source surfaces.
- Exact-head Source contracts, full host suite, Enterprise debug APK, APK-exists check and aggregate `verify` must all pass before completion.

---

### Task 1: Pure Self Update policy and manifest model

**Files:**
- Modify: `settings.gradle.kts`
- Create: `apps/dpc/modules/self-update/core/build.gradle.kts`
- Create: `apps/dpc/modules/self-update/core/src/main/kotlin/io/dpcaio/selfupdate/SelfUpdateModels.kt`
- Create: `apps/dpc/modules/self-update/core/src/main/kotlin/io/dpcaio/selfupdate/SelfUpdatePlanner.kt`
- Create: `tools/tests/test_self_update_core_contract.py`
- Modify: `tools/run_host_tests.sh`

**Interfaces:**
- Produces: `UpdateChannel`, `SelfUpdateManifest`, `SelfUpdateState`, `SelfUpdateInstallPolicy`, `SelfUpdateDecision`, `SelfUpdatePlanner.evaluate(...)`.
- `SelfUpdateManifest` fields: `schemaVersion`, `channel`, `packageName`, `versionName`, `versionCode`, `apkUrl`, `apkSha256`, `apkSize`, `publishedAt`, `releaseNotesUrl`.

- [ ] **Step 1: Add the focused failing test**

Require `:self-update-core` in settings and assert the model/planner tokens above, including states `WAITING_USER`/`VERIFIED`, policies `ASK_ALWAYS`/`SILENT_IF_ALLOWED`/`AUTO_SILENT`, supported schema `1`, package equality, `candidateVersionCode > installedVersionCode`, HTTPS APK URL and nonblank SHA-256.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_self_update_core_contract.py`
Expected: failure on missing `:self-update-core` or missing model/planner source.

- [ ] **Step 3: Implement the minimum behavior**

Create the JVM module. `SelfUpdatePlanner.evaluate(installedPackage, installedVersionCode, selectedChannel, manifest)` returns a fail-closed decision with explicit reason. Reject unsupported schema, channel mismatch, package mismatch, non-HTTPS APK URL, blank/invalid 64-hex SHA-256 and versionCode not greater than installed. Keep parsing separate from Android networking; JSON-to-model parsing may live in Android/app code if adding a JSON library would otherwise be required.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_self_update_core_contract.py`
Expected: PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `bash tools/run_host_tests.sh`
Expected: PASS after registering the new host test.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add settings.gradle.kts apps/dpc/modules/self-update/core tools/tests/test_self_update_core_contract.py tools/run_host_tests.sh
git commit -m "feat(self-update): add core policy model"
```

### Task 2: Shared Android install-source reader

**Files:**
- Create: `apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/InstallSourceDetails.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt`
- Create: `tools/tests/test_install_source_details_contract.py`
- Modify: `tools/run_host_tests.sh`

**Interfaces:**
- Produces: `data class InstallSourceDetails(installingPackageName, installingPackageLabel, initiatingPackageName, originatingPackageName, packageSource, updateOwnerPackageName)`.
- Produces: `class AndroidInstallSourceReader(context) { fun read(packageName: String): InstallSourceDetails }`.

- [ ] **Step 1: Add the focused failing test**

Assert API 30+ uses `getInstallSourceInfo`, pre-30 uses `getInstallerPackageName`, API guards exist for `packageSource` and `updateOwnerPackageName`, labels come from PackageManager, `ActivityLauncherHubActivity` uses `AndroidInstallSourceReader`, and updater mode renders both label and raw package name.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_install_source_details_contract.py`
Expected: failure because the shared reader is absent and Activity Launcher still has a local helper.

- [ ] **Step 3: Implement the minimum behavior**

Use `getInstallSourceInfo()` on API 30+, legacy installer package fallback below API 30, API 33+ for `packageSource`, API 34+ for `updateOwnerPackageName`. Resolve the installing package label best-effort. Preserve null fields. Replace the launcher local `installerPackage()` implementation with the shared reader. Extend `InstalledApp` in updater mode with source details and render a selected-app/source card that updates with spinner selection.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_install_source_details_contract.py`
Expected: PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `python3 tools/tests/test_activity_launcher_app_manager_contract.py && bash tools/run_host_tests.sh`
Expected: PASS.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/InstallSourceDetails.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt tools/tests/test_install_source_details_contract.py tools/run_host_tests.sh
git commit -m "feat(installer): show package install source"
```

### Task 3: Android Self Update verification, storage and PackageInstaller coordinator

**Files:**
- Create: `apps/dpc/modules/self-update/android/build.gradle.kts`
- Create: `apps/dpc/modules/self-update/android/src/main/AndroidManifest.xml`
- Create: `apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateClient.kt`
- Create: `apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateStore.kt`
- Create: `apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateVerifier.kt`
- Create: `apps/dpc/modules/self-update/android/src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateInstaller.kt`
- Modify: `settings.gradle.kts`
- Modify: `apps/dpc/app/build.gradle.kts`
- Create: `tools/tests/test_self_update_android_contract.py`
- Modify: `tools/run_host_tests.sh`

**Interfaces:**
- Consumes: `SelfUpdateManifest`, `SelfUpdatePlanner`, `SelfUpdateInstallPolicy`, `AndroidPackageInstallerAdapter`, `InstallSessionSpec`.
- Produces: `SelfUpdateClient.fetchManifest()/downloadApk()`, `SelfUpdateVerifier.verifyCandidate(...)`, `SelfUpdateInstaller.submit(...)`, and persisted one-candidate state.

- [ ] **Step 1: Add the focused failing test**

Assert `:self-update-android` exists, declares INTERNET, depends on `:self-update-core` and `:installer-android`, uses HTTPS-capable `HttpURLConnection`, computes SHA-256, reads APK archive package/version/signing info, compares installed/candidate signing identities, uses `PackageSource.DOWNLOADED_FILE`, and sets `requireUserAction=false` only for a silent-attempt policy. Assert no Shizuku/root/shell install tokens are present in self-update sources.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_self_update_android_contract.py`
Expected: failure on missing Android self-update module/classes.

- [ ] **Step 3: Implement the minimum behavior**

`SelfUpdateClient` fetches the fixed manifest URL and candidate APK with finite connect/read timeouts and rejects redirects to non-HTTPS destinations. `SelfUpdateStore` uses an app-private `filesDir/self-update` directory plus SharedPreferences/JSON metadata and maintains only one candidate. `SelfUpdateVerifier` checks byte count, SHA-256, PackageManager archive parse, package, version and signer compatibility; failed candidates are deleted. `SelfUpdateInstaller` creates/stages/commits via `AndroidPackageInstallerAdapter`; map `ASK_ALWAYS` to `requireUserAction=true`, silent attempts to `false` on API 31+, and leave older APIs to managed-device PackageInstaller semantics. Callback result, not the preflight capability probe, determines whether user action is required.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_self_update_android_contract.py`
Expected: PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `bash tools/run_host_tests.sh`
Expected: PASS.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add settings.gradle.kts apps/dpc/app/build.gradle.kts apps/dpc/modules/self-update tools/tests/test_self_update_android_contract.py tools/run_host_tests.sh
git commit -m "feat(self-update): add verified Android update pipeline"
```

### Task 4: Self Update UI, silent capability display and ModernUi components

**Files:**
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt`
- Create: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateStatusReceiver.kt`
- Modify: `apps/dpc/app/src/main/AndroidManifest.xml`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ModernUi.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/ControlCenterActivity.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt`
- Modify: `apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt`
- Create: `tools/tests/test_self_update_ui_contract.py`
- Modify: `tools/run_host_tests.sh`

**Interfaces:**
- Consumes: Android self-update client/store/verifier/installer and `ManagementContextFactory`/DevicePolicyManager capability information.
- Produces: internal `SelfUpdateActivity` and callback receiver; reusable `ModernUi.statusCard`, `progressCard`, and key/value details helper.

- [ ] **Step 1: Add the focused failing test**

Require internal manifest registration, Dashboard/Control Center/Module Center navigation, status states, Stable/Continuous channel controls, `Check now`, `Download & verify`, manual install, silent install, clear candidate, release notes, opt-in auto-silent setting, and `STATUS_PENDING_USER_ACTION` -> `WAITING_USER` handling. Require the new ModernUi helpers and their use by Self Update and installer source details.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_self_update_ui_contract.py`
Expected: failure on missing `SelfUpdateActivity` or navigation/UI components.

- [ ] **Step 3: Implement the minimum behavior**

Build one classic-View screen using `ModernUi`. Default manual policy is `SILENT_IF_ALLOWED`; auto-silent setting defaults OFF. Capability display is informational/best-effort; installation always follows PackageInstaller callback. On `STATUS_PENDING_USER_ACTION`, start the platform-provided confirmation Intent for manual/SILENT_IF_ALLOWED operation and set persisted/UI state `WAITING_USER`; AUTO_SILENT also becomes `WAITING_USER` and never auto-clicks/auto-starts hidden confirmation. On package replacement or success, clear candidate and show `UPDATED`.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_self_update_ui_contract.py`
Expected: PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `python3 tools/tests/test_aio_dashboard_contract.py && python3 tools/tests/test_module_center_contract.py && bash tools/run_host_tests.sh`
Expected: PASS.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add apps/dpc/app/src/main/AndroidManifest.xml apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateStatusReceiver.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/ModernUi.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/ControlCenterActivity.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt tools/tests/test_self_update_ui_contract.py tools/run_host_tests.sh
git commit -m "feat(ui): add self update center"
```

### Task 5: Release-side self-update manifest generation

**Files:**
- Create: `tools/release/build_self_update_manifest.py`
- Modify: `.github/workflows/build-aio-enrollment.yml`
- Create: `tools/tests/test_self_update_release_manifest.py`
- Modify: `tools/run_host_tests.sh`

**Interfaces:**
- Produces stable schema-v1 `self-update.json` from the APK/release inputs actually being published.

- [ ] **Step 1: Add the focused failing test**

Create a temporary APK-like file and invoke the generator with package/version/channel/apk URL/release notes URL. Assert schemaVersion=1, normalized channel, exact package/version, HTTPS URLs, byte size and computed lowercase SHA-256. Assert invalid non-HTTPS APK URL exits nonzero.

- [ ] **Step 2: Verify the relevant failure**

Run: `python3 tools/tests/test_self_update_release_manifest.py`
Expected: failure because generator is absent.

- [ ] **Step 3: Implement the minimum behavior**

Generator writes deterministic UTF-8 JSON with no secrets. Update release workflow so the manifest is generated only after the target APK exists and uploaded alongside that exact APK for continuous/stable release paths. Do not query GitHub API from the app to discover assets.

- [ ] **Step 4: Verify the focused pass**

Run: `python3 tools/tests/test_self_update_release_manifest.py`
Expected: PASS.

- [ ] **Step 5: Run the affected integration check**

Run: `bash tools/run_host_tests.sh`
Expected: PASS.

- [ ] **Step 6: Commit the passing deliverable**

```bash
git add tools/release/build_self_update_manifest.py .github/workflows/build-aio-enrollment.yml tools/tests/test_self_update_release_manifest.py tools/run_host_tests.sh
git commit -m "ci: publish self update manifest"
```

### Task 6: Exact-head integration verification and PR

**Files:**
- No production behavior beyond fixes required by failing verification.
- Update PR description with RED/GREEN evidence.

**Interfaces:**
- Consumes all previous tasks.
- Produces a reviewable PR with exact-head evidence.

- [ ] **Step 1: Run full host verification**

Run: `bash tools/run_host_tests.sh`
Expected: PASS.

- [ ] **Step 2: Open/update PR from `feat/self-update-install-source` to `main`**

Include scope, explicit no-bypass boundary, RED commit(s), GREEN head and expected CI gates.

- [ ] **Step 3: Verify Android CI on the exact PR head**

Expected: Source contracts PASS; Enterprise debug APK PASS; `Verify APK exists` PASS; aggregate `verify` PASS.

- [ ] **Step 4: Review changed files and review threads**

Ensure no unrelated refactor, no unresolved correctness thread, and branch head still equals the verified SHA.

- [ ] **Step 5: Stop before merge**

Do not merge unless the user explicitly requests it.

## Externally observable decisions

All externally observable decisions required for this scope are resolved by the approved design: default manual policy is `SILENT_IF_ALLOWED`; auto-silent is OFF by default; pending user action never gets bypassed; null install-source fields display unavailable; Stable and Continuous are the two channels; Self Update keeps one candidate and does not poll periodically in the background.
