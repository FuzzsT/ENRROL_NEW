#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def text(rel: str) -> str:
    return (ROOT / rel).read_text("utf-8")


def main() -> None:
    activity = text("apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt")
    assert "UserHandle.of(" not in activity
    assert "LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)" in activity

    knox = text("apps/dpc/app/src/main/kotlin/io/dpcaio/app/KnoxEnterpriseCenterActivity.kt")
    assert "management.deviceOwner" not in knox
    assert "management.profileOwner" not in knox
    assert "OwnershipMode.DEVICE_OWNER" in knox
    assert "OwnershipMode.PROFILE_OWNER" in knox

    offline = text("apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflinePolicyApplier.kt")
    assert "UserHandle.of(" not in offline
    assert "val manifest = inspected.manifest" in offline
    assert "read(bundleFile, manifest.policyPath)" in offline

    setup = text("apps/dpc/app/src/main/kotlin/io/dpcaio/app/OfflineSetupActivity.kt")
    assert "val manifest = inspected?.manifest" in setup
    assert "OfflineReadinessPlanner().evaluate(\n            manifest," in setup

    xml = text("apps/dpc/modules/nfc-lab/android/src/main/res/xml/dpcaio_host_apdu_service.xml")
    strings = text("apps/dpc/modules/nfc-lab/android/src/main/res/values/strings.xml")
    assert 'android:description="@string/dpcaio_nfc_aid_group_description"' in xml
    assert 'name="dpcaio_nfc_aid_group_description"' in strings

    print("ENTERPRISE_RELEASE_COMPILE_HOTFIX_CONTRACT: PASS")


if __name__ == "__main__":
    main()
