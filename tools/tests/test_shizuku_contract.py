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


def main():
    runtime = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AndroidShizukuRuntime.kt"
    bootstrap = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuEarlyBootstrap.kt"
    application = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcAioApplication.kt"
    service = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/AioShizukuUserService.kt"
    client = ROOT / "apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt"
    aidl = ROOT / "apps/dpc/integrations/shizuku/src/main/aidl/io/dpcaio/shizuku/IAioShizukuUserService.aidl"
    editor = ROOT / "apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppOpsVerifiedEditorActivity.kt"
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
    if "Sui.init(" in runtime_text:
        raise AssertionError("runtime probe must not repeat the one-shot Sui initialization")

    bootstrap_text = must(bootstrap, "object ShizukuEarlyBootstrap")
    for token in [
        "ShizukuProvider.disableAutomaticSuiInitialization()",
        "Sui.init(packageName)",
        "@Synchronized",
        "private var initialized = false",
        "if (initialized) return suiAvailable",
        "initialized = true",
    ]:
        if token not in bootstrap_text:
            raise AssertionError(f"early Sui bootstrap missing {token}")
    if bootstrap_text.index("ShizukuProvider.disableAutomaticSuiInitialization()") > bootstrap_text.index("Sui.init(packageName)"):
        raise AssertionError("automatic provider Sui initialization must be disabled before the explicit one-shot init")

    application_text = must(application, "class DpcAioApplication : Application()")
    for token in [
        "import io.dpcaio.shizuku.ShizukuEarlyBootstrap",
        "companion object",
        "ShizukuEarlyBootstrap.initialize(BuildConfig.APPLICATION_ID)",
    ]:
        if token not in application_text:
            raise AssertionError(f"application early Shizuku bootstrap missing {token}")
    if application_text.index("ShizukuEarlyBootstrap.initialize(BuildConfig.APPLICATION_ID)") > application_text.index("override fun onCreate()"):
        raise AssertionError("Sui bootstrap must run from class initialization before Application.onCreate")

    aidl_text = must(aidl, 'String getAppOp(String packageName, String op, int userId);')
    for token in [
        'String getAppOps(String packageName, int userId);',
        'String resolveAppOp(String op);',
        'String queryBroadcastReceivers(String action, int userId);',
    ]:
        if token not in aidl_text:
            raise AssertionError(f"typed AppOps/inventory contract missing {token}")

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
        'override fun queryBroadcastReceivers(action: String, userId: Int): String',
        '"/system/bin/pm", "query-receivers", "--brief", "--components", "--user"',
        '"--query-flags", "0x00000200"',
    ]:
        if token not in service_text:
            raise AssertionError(f"checked AppOps/cross-user inventory missing {token}")
    for forbidden in ["Runtime.getRuntime().exec", "newProcess(", "HiddenApiBypass", "setHiddenApiExemptions"]:
        if forbidden in service_text:
            raise AssertionError(f"unsafe Shizuku service token: {forbidden}")

    client_text = must(client, "Shizuku.UserServiceArgs")
    for token in [
        "Shizuku.bindUserService",
        ".processNameSuffix(",
        ".version(3)",
        "IBinder.DeathRecipient",
        "linkToDeath",
        "unlinkToDeath",
        "isBinderAlive",
        "fun isConnected(): Boolean",
        "clearRemote(",
        "fun connectedRemote(): IAioShizukuUserService?",
        "Shizuku.OnBinderReceivedListener",
        "Shizuku.OnRequestPermissionResultListener",
        "addBinderReceivedListenerSticky",
        "addRequestPermissionResultListener",
        "removeBinderReceivedListener",
        "removeRequestPermissionResultListener",
        "retryBindIfReady",
        "PackageManager.PERMISSION_GRANTED",
        'fun getAppOp(packageName: String, op: String, userId: Int): String?',
        'connectedRemote()?.getAppOp(packageName, op, userId)',
        'fun resolveAppOp(op: String): String?',
        'connectedRemote()?.resolveAppOp(op)',
        'fun queryBroadcastReceivers(action: String, userId: Int): String?',
        'connectedRemote()?.queryBroadcastReceivers(action, userId)',
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
