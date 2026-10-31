from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app"


def text(name: str) -> str:
    path = APP / name
    assert path.is_file(), f"missing {path.relative_to(ROOT)}"
    return path.read_text(encoding="utf-8")


def require(source: str, needles: list[str], label: str) -> None:
    for needle in needles:
        assert needle in source, f"{label} missing {needle!r}"


appops = text("AppOpsVerifiedEditorActivity.kt")
permission = text("PermissionManagerActivity.kt")
developer = text("DeveloperActivitiesActivity.kt")

shared = [
    '"Choose package"',
    '"Choose user"',
    "RuntimeTargetPicker.showPackagePicker",
    "RuntimeTargetPicker.showUserPicker",
]

require(appops, shared + [
    "readCurrent()",
    "targetUserIdOrNull()",
], "Verified AppOps")

require(permission, shared + [
    "scanAppPermissions(false)",
    "openVerifiedAppOps()",
], "Permission Manager")

require(developer, shared + [
    "scan()",
    "ActivityExplorerActivity::class.java",
    'putExtra("userId", userId)',
], "Developer Activities")

print("RUNTIME_TARGET_PICKER_FOLLOWUP_CONTRACT: PASS")
