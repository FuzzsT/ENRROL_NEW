#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        raise AssertionError(f"missing {path}")
    return p.read_text(encoding="utf-8", errors="ignore")


def require(text: str, tokens: list[str], label: str) -> None:
    for token in tokens:
        if token not in text:
            raise AssertionError(f"{label} missing {token}")


def main() -> None:
    settings = read("settings.gradle.kts")
    app_gradle = read("apps/dpc/app/build.gradle.kts")
    registry = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/DpcModuleRegistry.kt")
    manifest = read("apps/dpc/app/src/main/AndroidManifest.xml")
    dashboard = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt")
    permissions = read("apps/dpc/modules/permissions/android/src/main/kotlin/io/dpcaio/permission/android/AndroidPermissionGrantCoordinator.kt")
    shizuku = read("apps/dpc/integrations/shizuku/src/main/kotlin/io/dpcaio/shizuku/ShizukuUserServiceClient.kt")

    require(settings, [
        '":file-manager-android"',
        'project(":file-manager-android").projectDir = file("apps/dpc/modules/file-manager/android")',
    ], "settings")
    require(app_gradle, ['implementation(project(":file-manager-android"))'], "app gradle")

    file_engine = read("apps/dpc/modules/file-manager/android/src/main/kotlin/io/dpcaio/filemanager/android/SafFileManager.kt")
    require(file_engine, [
        "DocumentsContract.buildChildDocumentsUriUsingTree",
        "DocumentsContract.getDocumentId",
        "DocumentsContract.createDocument",
        "DocumentsContract.deleteDocument",
        "DocumentsContract.renameDocument",
        "contentResolver.query(",
    ], "SAF file manager")
    file_ui = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/FileManagerActivity.kt")
    require(file_ui, [
        "Intent.ACTION_OPEN_DOCUMENT_TREE",
        "Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION",
        "takePersistableUriPermission",
        "SafFileManager",
        '"Create folder"',
        '"Delete selected"',
        '"Rename selected"',
    ], "FileManager UI")
    require(registry, [
        'DpcModuleDescriptor(":file-manager-android"',
        'FileManagerActivity::class.java',
    ], "module registry")
    require(manifest, ['<activity android:name=".FileManagerActivity"'], "manifest FileManager")
    require(dashboard, ['"File Manager"', 'FileManagerActivity::class.java'], "dashboard FileManager")

    autostart = read("apps/dpc/app/src/main/kotlin/io/dpcaio/app/AutostartManagerActivity.kt")
    require(autostart, [
        "Intent.ACTION_BOOT_COMPLETED",
        "Intent.ACTION_LOCKED_BOOT_COMPLETED",
        "PackageManager.MATCH_DISABLED_COMPONENTS",
        "queryBroadcastReceivers",
        "AndroidComponentStateGateway",
        "ShizukuComponentStateExecutor",
        "ComponentOverrideState.ENABLED",
        "ComponentOverrideState.DISABLED",
        '"Autostart Manager"',
    ], "Autostart Manager")
    require(manifest, ['<activity android:name=".AutostartManagerActivity"'], "manifest Autostart")
    require(dashboard, ['"Autostart Manager"', 'AutostartManagerActivity::class.java'], "dashboard Autostart")

    require(shizuku, [
        "fun awaitConnected(timeoutMs: Long",
        "SystemClock.elapsedRealtime()",
        "Thread.sleep(",
        "bind()",
        "isConnected()",
    ], "Shizuku readiness wait")
    require(permissions, [
        "shizuku?.awaitConnected(",
        "shizukuAvailable = shizukuReady",
    ], "AUTO permissions readiness")
    if "shizuku?.identity() != null" in permissions:
        raise AssertionError("AUTO permissions must not use identity() as the Shizuku readiness gate")

    print("test_filemanager_autostart_auto_permissions_contract: PASS")


if __name__ == "__main__":
    main()
