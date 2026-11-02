#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'apps/dpc/modules/app-management/core/src/main/kotlin/io/dpcaio/appmanager/PackageExportPlan.kt'
READER = ROOT / 'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidInstalledPackageReader.kt'
EXPORTER = ROOT / 'apps/dpc/modules/app-management/android/src/main/kotlin/io/dpcaio/appmanager/android/AndroidPackageExporter.kt'
PROCESS_MONITOR = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProcessMonitorActivity.kt'
PROCESS_SNAPSHOT = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/ProcessSnapshotCollector.kt'
DASHBOARD = ROOT / 'apps/dpc/app/src/main/kotlin/io/dpcaio/app/AioDashboardActivity.kt'
MANIFEST = ROOT / 'apps/dpc/app/src/main/AndroidManifest.xml'
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
        'AIO_BUNDLE',
        'data class InstalledPackageArtifacts',
        'baseApk',
        'splitApks',
        'obbFiles',
        'data class PackageExportPlan',
        'generatedTextEntries',
        'process-snapshot.json',
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
        'generatedTextEntries',
        'manifest.json',
        'SHA256SUMS.txt',
        'copyTo(',
    ])
    assert 'readBytes(' not in exporter, 'APK export must stream files instead of loading them fully into memory'
    assert 'FileOutputStream(' not in exporter, 'destination must be written through SAF/ContentResolver Uri'
    assert 'ZipEntry(' in exporter, 'APKS/XAPK/AIO bundle export must create ZIP entries'
    assert 'obbIncluded' in exporter, 'archive metadata must record whether OBB files were included'
    assert exporter.count('openOutputStream(destinationUri, "wt")') == 2, 'APK and archive exports must request write+truncate SAF mode'
    assert 'openOutputStream(destinationUri, "w")' not in exporter, 'plain write mode may leave stale trailing bytes on some providers'

    snapshot = need(PROCESS_SNAPSHOT, [
        'class ProcessSnapshotCollector',
        'ActivityManager',
        'runningAppProcesses',
        'pid',
        'uid',
        'importance',
        'pkgList',
        'process-snapshot.json',
        'privateDataIncluded',
        'false',
    ])
    assert '/data/data/' not in snapshot and '/data/user/' not in snapshot, 'process snapshot must not read another app private data'

    monitor = need(PROCESS_MONITOR, [
        'class ProcessMonitorActivity',
        'ProcessSnapshotCollector',
        'Refresh processes',
        'Export AIO Bundle',
        'PackageExportFormat.AIO_BUNDLE',
        'Intent.ACTION_CREATE_DOCUMENT',
        'AndroidInstalledPackageReader',
        'AndroidPackageExporter',
    ])
    for forbidden in ['Runtime.getRuntime().exec', 'ProcessBuilder', 'su -c', 'run-as']:
        assert forbidden not in monitor, f'process monitor must not expose shell dump path: {forbidden}'

    dashboard = need(DASHBOARD, ['Process Monitor', 'ProcessMonitorActivity'])
    manifest = need(MANIFEST, ['.ProcessMonitorActivity', 'android:exported="false"'])

    workflow = need(WORKFLOW, [
        'Run package export contracts',
        'python3 tools/tests/test_package_export_contract.py',
    ])
    assert workflow.count('test_package_export_contract.py') == 1

    print('test_package_export_contract: PASS')


if __name__ == '__main__':
    main()
