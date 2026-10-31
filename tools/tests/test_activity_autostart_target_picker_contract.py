from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    p = ROOT / path
    assert p.is_file(), f"missing {path}"
    return p.read_text(encoding="utf-8")


def require(text: str, tokens: list[str], label: str) -> None:
    for token in tokens:
        assert token in text, f"{label} missing {token!r}"


picker = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/RuntimeTargetPicker.kt")
explorer = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt")
autostart = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/AutostartManagerActivity.kt")
signature_lab = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/SignatureCodeLabActivity.kt")
service = read("apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt")
shizuku_executor = read("apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuActivityRouteExecutor.kt")
planner = read("apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ActivityAccessPlanner.kt")
planner_test = read("apps/dpc/modules/activity/core/src/test/kotlin/io/dpcaio/activity/ActivityAccessPlannerTest.kt")

require(
    picker,
    [
        "object RuntimeTargetPicker",
        "fun showUserPicker(",
        "fun showPackagePicker(",
        "AlertDialog.Builder",
        "listUsers()",
        "listPackagesForUser(userId)",
        "package:",
        r"UserInfo\\{",
        "currentUserId",
        "onSelected",
    ],
    "RuntimeTargetPicker",
)

require(
    explorer,
    [
        '"Choose package"',
        '"Choose user"',
        "RuntimeTargetPicker.showPackagePicker",
        "RuntimeTargetPicker.showUserPicker",
        "launch(activity",
        "result.attempts",
        "Thread {",
    ],
    "ActivityExplorerActivity",
)

require(
    autostart,
    [
        '"Choose user"',
        "RuntimeTargetPicker.showUserPicker",
        "target user",
        "ShizukuComponentStateExecutor",
        "CURRENT_USER_READBACK",
        "TARGET_USER_SUBMITTED",
        '"Restore default"',
    ],
    "AutostartManagerActivity",
)

require(
    signature_lab,
    [
        '"Choose package"',
        "RuntimeTargetPicker.showPackagePicker",
    ],
    "SignatureCodeLabActivity",
)

require(
    service,
    [
        '"/system/bin/am", "start", "-W"',
        '"/system/bin/cmd"',
        '"activity"',
        '"start-activity"',
        '"-W"',
        "if (primary == 0) return primary",
        "override fun listPackagesForUser(userId: Int): String",
        "if (userId < 0)",
    ],
    "AioShizukuUserService",
)

require(
    shizuku_executor,
    [
        "awaitConnected(1800L)",
        "Shizuku shell launch exit=",
        "success = result == 0",
    ],
    "ShizukuActivityRouteExecutor",
)

require(
    planner,
    [
        "val packagePrepared =",
        "val frameworkPrepared =",
        "packagePrepared && input.shizukuAccessible",
    ],
    "ActivityAccessPlanner",
)
require(
    planner_test,
    [
        "crossUserShizuku",
        "userAccessible = false",
        "shizukuAccessible = true",
        "ActivityRoute.SHIZUKU",
    ],
    "ActivityAccessPlannerTest",
)

print("ACTIVITY_AUTOSTART_TARGET_PICKER_CONTRACT: PASS")
