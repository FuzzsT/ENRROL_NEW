#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'apps/dpc/modules/app-management/core/src/main/kotlin/io/dpcaio/appmanager/PackageExportPlan.kt'
READER = ROOT / 'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidInstalledPackageReader.kt'
EXPORTER = ROOT / 'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidPackageExporter.kt'
WORKFLOW = ROOT / '.github/workflows/pr-android-ci.yml'


def need(path: Path, tokens):
    assert path.exists(), f'{path.relative_to(ROOT)} missing'
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        assert token in text, f'{path.relative_to(ROOT)} missing {token}'
    return text


def main():
    core = need(CORE, [
        'enum class PackageExportFormat',
        'APK',
        'APKS',
        'XAPK',
        'data class InstalledPackageArtifacts',
        'baseApk',
        'splitApks',
        'obbFiles',
        'data class PackageExportPlan',
        'manifest.json',
        'SHA256SUMS.txt',
        'fun forFormat(',
    ])
    assert '..' not in '\n'.join(line for line in core.splitlines() if 'entryName' in line and 'require' not in line), 'archive entry naming must not embed traversal paths'
    assert 'requireSafeEntryName' in core or 'validateEntryName' in core, 'export plan must reject unsafe archive entry names'

    reader = need(READER, [
        'class AndroidInstalledPackageReader',
        'PackageManager',
        'sourceDir',
        'splitSourceDirs',
        'versionName',
        'longVersionCode',
        'InstalledPackageArtifacts',
    ])
    assert 'dataDir' not in reader, 'private application data must not be exported'

    exporter = need(EXPORTER, [
        'class AndroidPackageExporter',
        'ContentResolver',
        'Uri',
        'openOutputStream(',
        'ZipOutputStream',
        'MessageDigest.getInstance("SHA-256")',
        'manifest.json',
        'SHA256SUMS.txt',
        'copyTo(',
    ])
    assert 'readBytes(' not in exporter, 'APK export must stream files instead of loading them fully into memory'
    assert 'FileOutputStream(' not in exporter, 'destination must be written through SAF/ContentResolver Uri'
    assert 'ZipEntry(' in exporter, 'APKS/XAPK export must create ZIP entries'
    assert 'obbIncluded' in exporter, 'XAPK metadata must record whether OBB files were included'
    assert exporter.count('openOutputStream(destinationUri, "wt")') == 2, 'APK and archive exports must request write+truncate SAF mode'
    assert 'openOutputStream(destinationUri, "w")' not in exporter, 'plain write mode may leave stale trailing bytes on some providers'

    workflow = need(WORKFLOW, [
        'Run package export contracts',
        'python3 tools/tests/test_package_export_contract.py',
    ])
    assert workflow.count('test_package_export_contract.py') == 1

    print('test_package_export_contract: PASS')


if __name__ == '__main__':
    main()
