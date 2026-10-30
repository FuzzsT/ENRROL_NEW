#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def must(path: Path, token: str):
    if not path.exists():
        raise AssertionError(f"missing {path.relative_to(ROOT)}")
    text = path.read_text(encoding="utf-8", errors="ignore")
    if token not in text:
        raise AssertionError(f"{path.relative_to(ROOT)} missing {token}")
    return text


def require_resume_rebind(path: Path):
    text = path.read_text(encoding="utf-8", errors="ignore")
    marker = "override fun onResume()"
    if marker not in text:
        raise AssertionError(f"{path.relative_to(ROOT)} must rebind Shizuku from onResume")
    block = text[text.index(marker):text.index(marker) + 500]
    if "shizuku.bind()" not in block:
        raise AssertionError(f"{path.relative_to(ROOT)} onResume must retry shizuku.bind()")


def main():
    runtime = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AndroidShizukuRuntime.kt"
    service = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt"
    client = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt"
    aidl = ROOT / "apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl"
    editor = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppOpsVerifiedEditorActivity.kt"
    permission_manager = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/PermissionManagerActivity.kt"
    activity_explorer = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ActivityExplorerActivity.kt"
    samsung_settings = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/SamsungSettingsEditorActivity.kt"
    activity_executor = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuActivityRouteExecutor.kt"
    control_center = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/ControlCenterActivity.kt"
    manifest = ROOT / "apps/dpc/integrations/shizuku/src/main/AndroidManifest.xml"
    catalog = ROOT / "gradle/libs.versions.toml"

    runtime_text = must(runtime, "Shizuku.checkSelfPermission()")
    for token in [
        "Shizuku.getUid()",
        "Shizuku.getVersion()",
        "Shizuku.requestPermission(",
        "Sui.isSui()",
        "onChanged(probe())",
        "RETRY_DELAYS_MS",
        "handler.postDelayed",
        "handler.removeCallbacks",
    ]:
        if token not in runtime_text:
            raise AssertionError(f"runtime missing {token}")
    for delay in ["250L", "1000L", "2500L"]:
        if delay not in runtime_text:
            raise AssertionError(f"runtime retry schedule missing {delay}")
    if 'val backend:' not in runtime_text:
        raise AssertionError("runtime state must expose detected Shizuku/Sui backend")

    aidl_text = must(aidl, 'String getAppOp(String packageName, String op, int userId);')
    for token in [
        'String getAppOps(String packageName, int userId);',
        'String resolveAppOp(String op);',
    ]:
        if token not in aidl_text:
            raise AssertionError(f"typed AppOps contract missing {token}")

    service_text = must(service, 'ProcessBuilder(args)')
    for token in ['"/system/bin/am", "start"', '"/system/bin/am", "broadcast"']:
        if token not in service_text:
            raise AssertionError(f"typed am route missing {token}")
    for token in [
        'runTypedCaptureChecked(',
        'return runTypedCaptureChecked(listOf("/system/bin/cmd", "appops", "get", "--user", userId.toString(), packageName))',
        'override fun getAppOp(packageName: String, op: String, userId: Int): String',
        'return runTypedCaptureChecked(listOf("/system/bin/cmd", "appops", "get", "--user", userId.toString(), packageName, op))',
        'override fun resolveAppOp(op: String): String',
        'getDeclaredMethod("opToSwitch"',
        'getDeclaredMethod("opToName"',
        'getDeclaredMethod("strOpToOp"',
        'getDeclaredMethod("strDebugOpToOp"',
        'if (process.exitValue() == 0) output else "EXECUTION_ERROR"',
    ]:
        if token not in service_text:
            raise AssertionError(f"checked AppOps read/resolve missing {token}")
    for forbidden in ["Runtime.getRuntime().exec", "newProcess(", "HiddenApiBypass", "setHiddenApiExemptions"]:
        if forbidden in service_text:
            raise AssertionError(f"unsafe Shizuku service token: {forbidden}")

    client_text = must(client, "Shizuku.UserServiceArgs")
    for token in [
        "Shizuku.bindUserService",
        ".processNameSuffix(",
        ".version(",
        "IBinder.DeathRecipient",
        "linkToDeath",
        "unlinkToDeath",
        "isBinderAlive",
        "fun isConnected(): Boolean",
        "clearRemote(",
        "fun connectedRemote(): IAioShizukuUserService?",
        'fun getAppOp(packageName: String, op: String, userId: Int): String?',
        'connectedRemote()?.getAppOp(packageName, op, userId)',
        'fun resolveAppOp(op: String): String?',
        'connectedRemote()?.resolveAppOp(op)',
    ]:
        if token not in client_text:
            raise AssertionError(f"client missing {token}")

    editor_text = must(editor, 'val resolved = shizuku.resolveAppOp(op)')
    for token in [
        'val switchOp = resolvedSwitchOp(resolved)',
        'val before = shizuku.getAppOp(pkg, switchOp, userId)',
        'shizuku.setAppOp(pkg, switchOp, mode, userId)',
        'if (!snapshotReadable(before))',
        'Initial AppOps snapshot unreadable; mutation aborted',
        'Uid mode:',
        'Default mode:',
        'val rollbackMode = beforeState.packageMode ?: beforeState.defaultMode',
        'read.packageMode == "default"',
    ]:
        if token not in editor_text:
            raise AssertionError(f"AppOps switch-aware fail-closed verification missing {token}")

    for screen in [editor, permission_manager, activity_explorer, samsung_settings]:
        require_resume_rebind(screen)

    activity_text = must(activity_executor, "ActivityRoute.SHIZUKU")
    if "client.startActivity(" not in activity_text:
        raise AssertionError("Shizuku activity executor must invoke typed user-service startActivity")

    control_text = control_center.read_text(encoding="utf-8", errors="ignore")
    for token in ["state.backend", '"BACKEND"', "shizuku.isConnected()", '"SERVICE"']:
        if token not in control_text:
            raise AssertionError(f"Control Center must show detected backend/service state: {token}")

    must(manifest, "rikka.shizuku.ShizukuProvider")
    must(catalog, 'shizuku = "13.1.5"')
    print("test_shizuku_contract: PASS")


if __name__ == "__main__":
    main()
