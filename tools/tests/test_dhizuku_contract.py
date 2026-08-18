#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def require(path: Path, token: str):
    if not path.exists():
        raise AssertionError(f"missing {path.relative_to(ROOT)}")
    text = path.read_text(encoding="utf-8", errors="ignore")
    if token not in text:
        raise AssertionError(f"{path.relative_to(ROOT)} missing token: {token}")
    return text


def main():
    aidl = ROOT / "dhizuku-compat/src/main/aidl/com/rosan/dhizuku/aidl/IDhizuku.aidl"
    provider = ROOT / "dhizuku-compat/src/main/kotlin/io/dpcaio/delegation/dhizuku/SafeDhizukuProvider.kt"
    service = ROOT / "dhizuku-compat/src/main/kotlin/io/dpcaio/delegation/dhizuku/SafeDhizukuService.kt"
    manifest = ROOT / "dhizuku-compat/src/main/AndroidManifest.xml"
    license_file = ROOT / "dhizuku-compat/LICENSE-DHIZUKU-API-MIT.txt"

    require(aidl, "interface IDhizuku")
    require(provider, 'METHOD_CLIENT = "client"')
    require(provider, 'PARAM_DHIZUKU_BINDER = "dhizuku_binder"')
    service_text = require(service, "class SafeDhizukuService")
    for token in [
        "getDelegatedScopes(", "setDelegatedScopes(",
        "RAW_BINDER_DISABLED", "REMOTE_PROCESS_DISABLED", "USER_SERVICE_DISABLED"
    ]:
        if token not in service_text:
            raise AssertionError(f"SafeDhizukuService missing {token}")
    for forbidden in ["DhizukuProcess.get()", ".remoteTransact(", "HiddenApiBypass"]:
        if forbidden in service_text:
            raise AssertionError(f"unsafe Dhizuku compatibility token present: {forbidden}")
    require(manifest, '${applicationId}.dhizuku_server.provider')
    require(license_file, "MIT License")
    print("test_dhizuku_contract: PASS")


if __name__ == "__main__":
    main()
