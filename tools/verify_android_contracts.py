#!/usr/bin/env python3
from dataclasses import dataclass
from pathlib import Path
import sys


@dataclass(frozen=True)
class Finding:
    code: str
    path: str
    detail: str


ADAPTERS = {
    "permission-android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionCatalog.kt": {
        "required": ("getAllPermissionGroups(", "queryPermissionsByGroup(", "getInstalledPackages("),
    },
    "permission-android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionGrantCoordinator.kt": {
        "required": ("setPermissionGrantState(", "checkPermission("),
    },
    "samsung-settings-android/src/main/kotlin/io/dpcaio/samsung/settings/android/AndroidSamsungSettingGateway.kt": {
        "required": ("Settings.System.getString", "Settings.Secure.getString", "Settings.Global.getString", "WRITE_SECURE_SETTINGS"),
    },
    "policy-android/src/main/kotlin/io/dpcaio/policy/android/AndroidDevicePolicyGateway.kt": {
        "required": (
            "setApplicationHidden(",
            "setPackagesSuspended(",
            "setPermissionGrantState(",
            "getPermissionGrantState(",
        ),
    },
    "permission-android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionInspector.kt": {
        "required": ("checkPermission(", "checkOpNoThrow("),
    },
    "app-android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidAppInventory.kt": {
        "required": ("getInstalledPackages(", "isApplicationHidden(", "isPackageSuspended("),
    },
    "activity-android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt": {
        "required": ("getPackageInfo(", "getActivityList(", "isActivityEnabled("),
    },
    "account-android/src/main/kotlin/io/dpcaio/account/android/AndroidGoogleAccountRepository.kt": {
        "required": ("AccountManager.get(", "getAccountsByType(", "com.google"),
    },
    "account-android/src/main/kotlin/io/dpcaio/account/android/AndroidAccountReorderGateway.kt": {
        "required": ("removeAccount(", "setAccountManagementDisabled(", "getAccountTypesWithManagementDisabled("),
    },
    "installer-android/src/main/kotlin/io/dpcaio/installer/android/AndroidPackageInstallerAdapter.kt": {
        "required": (
            "SessionParams(",
            "createSession(",
            "setPackageSource(",
            "setInstallerPackageName(",
            "setPermissionState(",
            "openSession(",
            "openWrite(",
            "fsync(",
            "commit(",
        ),
    },
}


def verify_android_contracts(root: Path):
    root = Path(root)
    findings = []
    for rel, contract in ADAPTERS.items():
        path = root / rel
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        lowered = text.lower()
        for call in contract["required"]:
            if call not in text:
                findings.append(Finding("MISSING_PUBLIC_ANDROID_CALL", str(path), call))
    return findings


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
    findings = verify_android_contracts(root)
    if findings:
        print("ANDROID_CONTRACTS: FAIL")
        for finding in findings:
            print(f" - {finding.code}: {finding.path}: {finding.detail}")
        return 1
    print("ANDROID_CONTRACTS: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
