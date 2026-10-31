#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AppPackageManagerActivity.kt'
WORKFLOW = ROOT / '.github/workflows/pr-android-ci.yml'


def need(path: Path, tokens):
    assert path.exists(), f'{path.relative_to(ROOT)} missing'
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path.relative_to(ROOT)} missing {token}'
    return text


def main():
    activity = need(ACTIVITY, [
        'AndroidInstalledPackageReader',
        'AndroidPackageExporter',
        'PackageExportFormat',
        'PackageExportPlan',
        'pendingExportFormat',
        'pendingExportPackage',
        'Export APK',
        'Export APKS',
        'Export XAPK',
        'openExportDestination(',
        'Intent.ACTION_CREATE_DOCUMENT',
        'Intent.CATEGORY_OPENABLE',
        'REQ_EXPORT',
        'exportSelectedPackage(',
        'PackageExportPlan.forFormat(',
        'packageExporter.export(',
    ])
    assert activity.count('ModernUi.action(this@AppPackageManagerActivity, "Export APK"') == 1
    assert activity.count('ModernUi.action(this@AppPackageManagerActivity, "Export APKS"') == 1
    assert activity.count('ModernUi.action(this@AppPackageManagerActivity, "Export XAPK"') == 1
    assert 'MANAGE_EXTERNAL_STORAGE' not in activity
    assert 'FileOutputStream(' not in activity
    assert 'ACTION_OPEN_DOCUMENT_TREE' not in activity
    assert 'application/vnd.android.package-archive' in activity
    assert 'application/zip' in activity
    assert 'if (requestCode == REQ_EXPORT)' in activity or 'requestCode == REQ_EXPORT' in activity

    workflow = need(WORKFLOW, [
        'Run package export contracts',
        'python3 tools/tests/test_package_export_ui_contract.py',
    ])
    assert workflow.count('test_package_export_ui_contract.py') == 1

    print('test_package_export_ui_contract: PASS')


if __name__ == '__main__':
    main()
