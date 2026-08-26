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
    print("test_android_contracts: PASS")


if __name__ == "__main__":
    main()
