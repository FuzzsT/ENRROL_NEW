#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def need(path, tokens):
    text = (ROOT / path).read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path} missing {token}'

def main():
    need('apps/dpc/modules/installer/android/src/main/kotlin/io/dpcaio/installer/android/AndroidPackageInstallerAdapter.kt', [
        'fun stageApkSet(', 'session.openWrite', 'session.fsync',
    ])
    need('apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt', [
        'ACTION_OPEN_DOCUMENT', 'AndroidApkPlusStager', 'AndroidPackageInstallerAdapter',
        'APK/APKS Installer', 'App Updater', 'loadInstalledPackages()',
        'getPackageArchiveInfo', 'PackageInstaller.SessionParams.USER_ACTION',
    ])
    need('apps/dpc/app/src/main/AndroidManifest.xml', [
        '.AppPackageManagerActivity', '.AppInstallStatusReceiver',
    ])
    need('apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt', [
        'APK / APKS Installer', 'App Updater', 'AppPackageManagerActivity',
    ])
    print('test_app_installer_updater_contract: PASS')

if __name__ == '__main__':
    main()
