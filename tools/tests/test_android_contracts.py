#!/usr/bin/env python3
from pathlib import Path
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_android_contracts import verify_android_contracts


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def write(root: Path, rel: str, text: str):
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def main():
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        policy = write(
            root,
            "apps/dpc/modules/policy/android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt",
            "setApplicationHidden(\nsetPackagesSuspended(\nsetPermissionGrantState(\ngetPermissionGrantState(\n",
        )
        write(
            root,
            "apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionInspector.kt",
            "checkPermission(\ncheckOpNoThrow(\n",
        )
        write(
            root,
            "apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt",
            "getPackageInfo(\ngetActivityList(\ngetComponentEnabledSetting(\n",
        )
        write(
            root,
            "apps/dpc/modules/account/android/src/main/kotlin/io/dpcaio/account/android/AndroidGoogleAccountRepository.kt",
            "AccountManager.get(\ngetAccountsByType(\ncom.google\n",
        )
        write(
            root,
            "apps/dpc/modules/account/android/src/main/kotlin/io/dpcaio/account/android/AndroidAccountReorderGateway.kt",
            "removeAccount(\nsetAccountManagementDisabled(\ngetAccountTypesWithManagementDisabled(\n",
        )
        write(
            root,
            "apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidPackageInstallerAdapter.kt",
            "SessionParams(\ncreateSession(\nsetPackageSource(\nsetInstallerPackageName(\nsetPermissionState(\nopenSession(\nopenWrite(\nfsync(\ncommit(\n",
        )
        app_inventory = write(
            root,
            "apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidAppInventory.kt",
            "class AndroidAppInventory",
        )
        findings = verify_android_contracts(root)
        assert_true(any(f.detail == "getInstalledPackages(" for f in findings), "app inventory must require getInstalledPackages")
        app_inventory.write_text("getInstalledPackages(\nisApplicationHidden(\nisPackageSuspended(\n", encoding="utf-8")
        findings = verify_android_contracts(root)
        assert_true(not findings, f"public API fixture should pass: {findings}")

        policy.write_text(policy.read_text() + "\nHiddenApiBypass\n", encoding="utf-8")
        findings = verify_android_contracts(root)
        assert_true(not findings, "technology names must not be rejected by Android contract verifier")

        permission = root / "apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionInspector.kt"
        permission.write_text(permission.read_text() + "\nsetUidMode(\nsetMode(\n", encoding="utf-8")
        findings = verify_android_contracts(root)
        assert_true(not findings, "AppOps mutation helpers must not be policy-blocked by verifier")

    findings = verify_android_contracts(ROOT)
    assert_true(not findings, f"project Android contracts should pass: {findings}")

    source_reader_path = ROOT / "apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/InstallSourceDetails.kt"
    assert_true(source_reader_path.is_file(), "shared InstallSourceDetails.kt missing")
    source_reader = source_reader_path.read_text(encoding="utf-8")
    for token in [
        "data class InstallSourceDetails",
        "class AndroidInstallSourceReader",
        "getInstallSourceInfo",
        "getInstallerPackageName",
        "installingPackageName",
        "installingPackageLabel",
        "initiatingPackageName",
        "originatingPackageName",
        "packageSource",
        "updateOwnerPackageName",
    ]:
        assert_true(token in source_reader, f"install source reader missing: {token}")

    hub = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityLauncherHubActivity.kt").read_text(encoding="utf-8")
    package_manager = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt").read_text(encoding="utf-8")
    assert_true("AndroidInstallSourceReader" in hub, "Activity Launcher must use shared install source reader")
    for token in ["AndroidInstallSourceReader", "Installed by", "installingPackageName", "installingPackageLabel"]:
        assert_true(token in package_manager, f"App Package Manager source UI missing: {token}")

    self_update_root = ROOT / "apps/dpc/modules/self-update/android"
    build_file = self_update_root / "build.gradle.kts"
    manifest_file = self_update_root / "src/main/AndroidManifest.xml"
    client_file = self_update_root / "src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateClient.kt"
    store_file = self_update_root / "src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateStore.kt"
    verifier_file = self_update_root / "src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateVerifier.kt"
    installer_file = self_update_root / "src/main/kotlin/io/dpcaio/selfupdate/android/SelfUpdateInstaller.kt"
    for path in [build_file, manifest_file, client_file, store_file, verifier_file, installer_file]:
        assert_true(path.is_file(), f"Self Update Android file missing: {path.relative_to(ROOT)}")

    build_text = build_file.read_text(encoding="utf-8")
    assert_true('project(":self-update-core")' in build_text, "self-update Android must depend on core")
    assert_true('project(":installer-android")' in build_text, "self-update Android must reuse installer Android adapter")
    assert_true('project(":installer-core")' in build_text, "self-update Android must reuse installer core model")
    assert_true('project(":policy-core")' in build_text, "self-update Android must expose PolicyResult dependency")
    assert_true("android.permission.INTERNET" in manifest_file.read_text(encoding="utf-8"), "Self Update Android must declare INTERNET")

    client = client_file.read_text(encoding="utf-8")
    for token in ["class SelfUpdateClient", "HttpURLConnection", "connectTimeout", "readTimeout", "JSONObject", "https://"]:
        assert_true(token in client, f"Self Update client missing: {token}")

    store = store_file.read_text(encoding="utf-8")
    for token in ["class SelfUpdateStore", "filesDir", "self-update", "clearCandidate", "saveCandidate"]:
        assert_true(token in store, f"Self Update store missing: {token}")

    verifier = verifier_file.read_text(encoding="utf-8")
    for token in ["class SelfUpdateVerifier", "SHA-256", "getPackageArchiveInfo", "GET_SIGNING_CERTIFICATES", "apkContentsSigners", "signingCertificateHistory", "versionCode"]:
        assert_true(token in verifier, f"Self Update verifier missing: {token}")

    installer = installer_file.read_text(encoding="utf-8")
    for token in ["class SelfUpdateInstaller", "AndroidPackageInstallerAdapter", "InstallSessionSpec", "PackageSource.DOWNLOADED_FILE", "requireUserAction", "SILENT_IF_ALLOWED", "AUTO_SILENT"]:
        assert_true(token in installer, f"Self Update installer missing: {token}")

    combined = "\n".join(path.read_text(encoding="utf-8") for path in [client_file, store_file, verifier_file, installer_file])
    for forbidden in ["ProcessBuilder", "Runtime.getRuntime", "Shizuku", "su -c", "pm install"]:
        assert_true(forbidden not in combined, f"Self Update must not use install bypass path: {forbidden}")

    self_update_activity_path = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateActivity.kt"
    assert_true(self_update_activity_path.is_file(), "SelfUpdateActivity.kt missing")
    self_update_activity = self_update_activity_path.read_text(encoding="utf-8")
    for token in [
        "UpdateChannel.STABLE",
        "UpdateChannel.CONTINUOUS",
        "Check now",
        "Download & verify",
        "Install update",
        "Install silently",
        "Clear downloaded update",
        "Release notes",
        "SILENT_IF_ALLOWED",
        "AUTO_SILENT",
    ]:
        assert_true(token in self_update_activity, f"Self Update UI missing: {token}")

    status_receiver_path = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/SelfUpdateStatusReceiver.kt"
    assert_true(status_receiver_path.is_file(), "SelfUpdateStatusReceiver.kt missing")
    status_receiver = status_receiver_path.read_text(encoding="utf-8")
    for token in ["STATUS_PENDING_USER_ACTION", "WAITING_USER", "EXTRA_INTENT"]:
        assert_true(token in status_receiver, f"Self Update status receiver missing: {token}")

    app_manifest = (ROOT / "apps/dpc/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    assert_true("android.permission.INTERNET" in app_manifest, "app manifest must explicitly declare INTERNET for Self Update")
    assert_true('.SelfUpdateActivity" android:exported="false"' in app_manifest, "Self Update activity must be internal")
    assert_true('.SelfUpdateStatusReceiver" android:exported="false"' in app_manifest, "Self Update status receiver must be internal")

    dashboard = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt").read_text(encoding="utf-8")
    control = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ControlCenterActivity.kt").read_text(encoding="utf-8")
    registry = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt").read_text(encoding="utf-8")
    for text, surface in [(dashboard, "Dashboard"), (control, "Control Center"), (registry, "Module registry")]:
        assert_true("SelfUpdateActivity::class.java" in text, f"{surface} must navigate to Self Update")

    modern_ui = (ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ModernUi.kt").read_text(encoding="utf-8")
    for token in ["fun statusCard(", "fun progressCard(", "fun detailRow("]:
        assert_true(token in modern_ui, f"ModernUi Self Update helper missing: {token}")

    print("test_android_contracts: PASS")


if __name__ == "__main__":
    main()
