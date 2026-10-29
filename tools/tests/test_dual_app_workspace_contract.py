#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def need(path: str, tokens: list[str]) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    for token in tokens:
        assert token in text, f"{path} missing {token}"


def main() -> None:
    need(
        "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DualAppWorkspaceActivity.kt",
        [
            "Dual Apps",
            "Managed profile",
            "Clone profile",
            "installExistingPackage",
            "AndroidShizukuRuntime",
            "UID 1000 diagnostics",
            "serverUid == 1000",
        ],
    )
    need(
        "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt",
        [
            '"Dual app workspaces"',
            "DualAppWorkspaceActivity::class.java",
            'tags = setOf("advanced", "multi_instance", "diagnostics")',
        ],
    )
    need(
        "apps/dpc/app/src/main/AndroidManifest.xml",
        [".DualAppWorkspaceActivity"],
    )
    need(
        "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AndroidShizukuRuntime.kt",
        ["val isSystemUid: Boolean", "serverUid == 1000"],
    )
    need(
        "apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl",
        ["listUsers()", "createProfile", "removeProfile", "startProfile", "stopProfile"],
    )
    need(
        "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt",
        [
            '"create-user"',
            '"android.os.usertype.profile.CLONE"',
            '"--managed"',
            '"install-existing"',
            "validProfileName",
        ],
    )
    print("test_dual_app_workspace_contract: PASS")


if __name__ == "__main__":
    main()
