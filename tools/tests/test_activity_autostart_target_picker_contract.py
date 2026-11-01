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
verification = read("apps/dpc/modules/activity/core/src/main/kotlin/io/dpcaio/activity/ComponentMutationVerification.kt")
inventory = read("apps/dpc/modules/activity/android/src/main/kotlin/io/dpcaio/activity/android/AndroidActivityInventory.kt")

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
        "ComponentMutationVerification.statusLine",
        "ComponentMutationVerification.canLaunchAfterEnable",
        "result.observedState",
        "result.verified",
    ],
    "ActivityExplorerActivity",
)
assert "SUBMITTED, cross-user readback not claimed" not in explorer
assert "readback via rescan" not in explorer

require(
    autostart,
    [
        '"Choose user"',
        "RuntimeTargetPicker.showUserPicker",
        "target user",
        "ShizukuComponentStateExecutor",
        "CURRENT_USER_READBACK",
        "TARGET_USER_VERIFIED",
        "ComponentMutationVerification.statusLine",
        "result.observedState",
        "result.verified",
        '"Restore default"',
        "private var scanGeneration = 0L",
        "val generation = ++scanGeneration",
        "if (generation != scanGeneration) return@runOnUiThread",
    ],
    "AutostartManagerActivity",
)

require(
    inventory,
    [
        "private fun launcherFallback(",
        "launcherApps.getActivityList(packageName, user)",
        "PackageManager.NameNotFoundException",
        "launcherFallback(packageName, user)",
        "launcherVisible = true",
        "userAccessible = launcherApps.profiles.contains(user)",
    ],
    "AndroidActivityInventory cross-user fallback",
)

require(
    verification,
    [
        "object ComponentMutationVerification",
        "fun statusLine(",
        "observed?.name ?: \"UNKNOWN\"",
        "fun canLaunchAfterEnable(",
        "observed == ComponentOverrideState.ENABLED",
        "verified",
    ],
    "ComponentMutationVerification",
)

require(
    signature_lab,
    [
        '"Choose package"',
        "RuntimeTargetPicker.showPackagePicker",
        'EXTRA_TARGET_PACKAGE',
        'intent.getStringExtra(EXTRA_TARGET_PACKAGE)',
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
