#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REPOSITORY = ROOT / "apps/dpc/modules/account/android/src/main/kotlin/io/dpcaio/account/android/AndroidGoogleAccountRepository.kt"
INTENTS = ROOT / "apps/dpc/modules/account/android/src/main/kotlin/io/dpcaio/account/android/GoogleAccountIntentFactory.kt"
GATEWAY = ROOT / "apps/dpc/modules/account/android/src/main/kotlin/io/dpcaio/account/android/AndroidAccountReorderGateway.kt"
MANIFEST = ROOT / "apps/dpc/app/src/main/AndroidManifest.xml"
SYSTEM_MANIFEST = ROOT / "apps/dpc/app/src/systemPrivileged/AndroidManifest.xml"


def require(path: Path, tokens: list[str]):
    if not path.exists():
        raise AssertionError(f"missing {path}")
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise AssertionError(f"{path}: missing {token}")
    return text


def main():
    repository = require(REPOSITORY, ["AccountManager.get(", "getAccountsByType(", '"com.google"'])
    intents = require(INTENTS, ["newChooseAccountIntent(", "Settings.ACTION_ADD_ACCOUNT"])
    gateway = require(GATEWAY, ["removeAccount(", "setAccountManagementDisabled(", "isAccountManagementDisabled("])
    manifest = require(MANIFEST, ["android.permission.GET_ACCOUNTS"])
    system_manifest = require(SYSTEM_MANIFEST, ["android.permission.GET_ACCOUNTS_PRIVILEGED", "android.permission.REMOVE_ACCOUNTS"])

    combined = "\n".join([repository, intents, gateway, manifest, system_manifest]).lower()
    for forbidden in ["getpassword(", "peekauthtoken(", "setpassword(", "setauthtoken("]:
        if forbidden in combined:
            raise AssertionError(f"account manager must not access/store credentials or auth tokens: {forbidden}")
    print("test_account_android_contract: PASS")


if __name__ == "__main__":
    main()
