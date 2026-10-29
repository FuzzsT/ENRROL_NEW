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
            "Multi Clone Manager",
            "Preview batch",
            "Execute batch",
            "Retry failed",
            "Cleanup batch workspaces",
            "MAX_BATCH_COPIES = 20",
            "SUCCESS",
            "PARTIAL",
            "FAILED",
            "SKIPPED",
            "1..MAX_BATCH_COPIES",
            "UID 1000 diagnostics are read-only",
        ],
    )
    need(
        "apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl",
        [
            "listPackagesForUser(int userId)",
            "uninstallPackageForUser(String packageName, int userId)",
        ],
    )
    need(
        "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt",
        [
            "fun listPackagesForUser(userId: Int)",
            "fun uninstallPackageForUser(packageName: String, userId: Int)",
        ],
    )
    need(
        "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt",
        [
            "override fun listPackagesForUser(userId: Int)",
            "override fun uninstallPackageForUser(packageName: String, userId: Int)",
            'listOf("/system/bin/pm", "list", "packages", "--user", userId.toString())',
            'listOf("/system/bin/pm", "uninstall", "--user", userId.toString(), packageName)',
        ],
    )
    print("test_dual_app_multi_clone_contract: PASS")


if __name__ == "__main__":
    main()
